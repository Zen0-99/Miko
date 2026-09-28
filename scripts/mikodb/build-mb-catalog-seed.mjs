#!/usr/bin/env node
// build-mb-catalog-seed.mjs — MangaBaka series.sqlite dump → chunked D1 seed SQL
//
// Run:  node build-mb-catalog-seed.mjs [series.sqlite] [outDir]
//       (defaults: .planning/tmp/mangabaka-coverage/series.sqlite, seed-mb/)
//
// Requires Node ≥22.13 (node:sqlite, experimental warning is harmless).
// Emits <outDir>/seed-mb-001.sql … (~1000 works per file, sized for
// `npx wrangler d1 execute miko-comments --remote --file=`):
//   INSERT OR IGNORE works_cache  — mk:mb:<id> rows, medium='books' iff
//                                   type='novel' else 'manga', nsfw from
//                                   content_rating (erotica|pornographic),
//                                   rating + popularity_type_current +
//                                   cover + genres for /v1/works/browse
//   INSERT works_titles           — canonical title pick (guarded
//                                   WHERE NOT EXISTS — the table has no
//                                   unique constraint, so re-seeding must
//                                   dedupe on (miko_work_id, normalized))
//   INSERT OR IGNORE external_ids — ('mangabaka', <id>) → mk:mb:<id>
//
// Selected rows: type in the MangaBaka vocab AND (rating OR
// popularity_type_current present) — unranked rows can't be ordered by
// the browse sorts so they're skipped. state='deleted'/'merged' rows
// are NOT excluded (locked spec: filter is rank-presence + type only).

import { DatabaseSync } from 'node:sqlite';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const DEFAULT_SRC = '.planning/tmp/mangabaka-coverage/series.sqlite';
const ROWS_PER_FILE = 5000;
// MangaBaka type vocab — comic/webtoon are allowed if a future dump
// grows them (absent from the current dump).
const TYPES = new Set([
    'manga', 'manhwa', 'manhua', 'novel', 'oel', 'other', 'comic', 'webtoon',
]);

const isMain = process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
const [,, src = DEFAULT_SRC, outDir = 'seed-mb'] = process.argv;

// ---- TitleNormalizer twin (keep in sync with TitleNormalizer.kt /
// worker/src/normalize.ts) ----
const NONWORD = /[^\p{L}\p{N}\s]/gu;
const SPACES = /\s+/g;
export function normalize(t) {
    if (!t) return '';
    return t.normalize('NFKC').toLowerCase().replace(NONWORD, ' ').replace(SPACES, ' ').trim();
}

const sql = (s) => `'${String(s).replace(/'/g, "''")}'`;
const sqlOrNull = (s) => (s == null || s === '' ? 'NULL' : sql(s));
const numOrNull = (n) => (n == null || !Number.isFinite(Number(n)) ? 'NULL' : String(Number(n)));

// dump stores authors as a JSON array TEXT col (strings; tolerate
// {name} objects like the API shape). First entry wins per spec.
function firstAuthor(authorsJson) {
    if (!authorsJson) return null;
    try {
        const arr = JSON.parse(authorsJson);
        if (!Array.isArray(arr)) return null;
        for (const a of arr) {
            const name = typeof a === 'string' ? a : a?.name;
            if (name) return String(name);
        }
    } catch {
        // malformed JSON — treat as no author
    }
    return null;
}

// genres is already a JSON array string — pass through verbatim when
// it parses as an array, NULL it otherwise (don't seed garbage).
function genresPassthrough(g) {
    if (!g) return null;
    try {
        if (Array.isArray(JSON.parse(g))) return g;
    } catch {
        // malformed JSON
    }
    return null;
}

async function build() {
    if (!fs.existsSync(src)) throw new Error(`dump not found: ${src}`);
    const db = new DatabaseSync(src, { readOnly: true });

    const total = db.prepare('SELECT COUNT(*) AS n FROM series').get().n;
    const rows = db.prepare(
        `SELECT id, type, title, romanized_title, native_title, year,
                total_chapters, authors, cover_x250_x1, cover_raw_url,
                rating, popularity_type_current, genres, content_rating
         FROM series
         WHERE (rating IS NOT NULL OR popularity_type_current IS NOT NULL)`,
    );

    fs.mkdirSync(outDir, { recursive: true });
    const fetchedAt = Date.now();
    const hash = createHash('sha256');
    let works = 0, titles = 0, extIds = 0, badType = 0, fileNo = 0, inFile = 0;
    let fh = null;
    const openFile = () => {
        fileNo += 1;
        fh = fs.createWriteStream(path.join(outDir, `seed-mb-${String(fileNo).padStart(3, '0')}.sql`));
        // No BEGIN/COMMIT — `wrangler d1 execute --remote --file=` rejects
        // raw transaction statements (D1 wants its JS transaction API);
        // every statement below is idempotent so re-runs are safe.
    };
    const closeFile = async () => {
        await new Promise((res, rej) => { fh.once('error', rej); fh.end(res); });
    };
    const w = (line) => { hash.update(line + '\n'); fh.write(line + '\n'); };

    for (const r of rows.iterate()) {
        if (!TYPES.has(r.type)) { badType++; continue; }
        if (inFile === 0) openFile();
        const workId = `mk:mb:${r.id}`;
        // Canonical title pick — same chain as resolve.ts persistSeries.
        const title = r.title || r.romanized_title || r.native_title || `untitled-${r.id}`;
        const norm = normalize(title);
        const author = firstAuthor(r.authors);
        const cover = r.cover_x250_x1 || r.cover_raw_url || null;
        const genres = genresPassthrough(r.genres);
        const medium = r.type === 'novel' ? 'books' : 'manga';
        const rating = r.content_rating;
        const nsfw = rating === 'erotica' || rating === 'pornographic' ? 1 : 0;

        w(`INSERT OR IGNORE INTO works_cache (miko_work_id, type, title, romanized_title, native_title, normalized_title, year, total_chapters, author, provider_links, medium, nsfw, content_rating, rating, popularity, cover_url, genres, fetched_at) VALUES (${sql(workId)}, ${sql(r.type)}, ${sql(title)}, ${sqlOrNull(r.romanized_title)}, ${sqlOrNull(r.native_title)}, ${sql(norm)}, ${numOrNull(r.year)}, ${sqlOrNull(r.total_chapters)}, ${sqlOrNull(author)}, '[]', '${medium}', ${nsfw}, ${sqlOrNull(rating)}, ${numOrNull(r.rating)}, ${numOrNull(r.popularity_type_current)}, ${sqlOrNull(cover)}, ${sqlOrNull(genres)}, ${fetchedAt});`);
        works++;
        if (norm) {
            // works_titles has no unique constraint — WHERE NOT EXISTS
            // makes the re-seed idempotent (OR IGNORE can't help here).
            w(`INSERT INTO works_titles (miko_work_id, title, lang, normalized) SELECT ${sql(workId)}, ${sql(title)}, NULL, ${sql(norm)} WHERE NOT EXISTS (SELECT 1 FROM works_titles t WHERE t.miko_work_id = ${sql(workId)} AND t.normalized = ${sql(norm)});`);
            titles++;
        }
        w(`INSERT OR IGNORE INTO external_ids (provider, external_id, miko_work_id) VALUES ('mangabaka', ${sql(String(r.id))}, ${sql(workId)});`);
        extIds++;
        inFile++;
        if (inFile >= ROWS_PER_FILE) {
            await closeFile();
            inFile = 0;
        }
    }
    if (inFile > 0) await closeFile();
    db.close();
    console.log(JSON.stringify({
        files: fileNo, works, titles, external_ids: extIds,
        skipped: total - works - badType, bad_type: badType,
        source_rows: total, source_sha256: hash.digest('hex'),
    }, null, 2));
}

if (isMain) build().catch((e) => { console.error(e); process.exit(1); });
