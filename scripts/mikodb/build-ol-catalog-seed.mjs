#!/usr/bin/env node
// build-ol-catalog-seed.mjs — OpenLibrary trending → chunked D1 seed SQL
//
// Run:  node build-ol-catalog-seed.mjs [outDir]        (default seed-ol/)
//
// Harvests OpenLibrary's public trending lists (no auth, same upstream
// the app's OpenLibraryTrending client reads) and emits
// <outDir>/seed-ol-001.sql … sized for
// `npx wrangler d1 execute miko-comments --remote --file=`:
//   INSERT OR IGNORE works_cache  — mk:ol:<workKey> rows, medium='books',
//                                   type='book', popularity = trending
//                                   rank (lower = more popular)
//   INSERT works_titles           — canonical title pick (guarded WHERE
//                                   NOT EXISTS — no unique constraint)
//   INSERT OR IGNORE external_ids — ('openlibrary', workKey) +
//                                   ('isbn', <normalized>) per doc
//
// Why: the books pill's Top-N chart must live in MikoDB so Discovery
// renders it whether or not any book extension is installed — the
// app's live OL path is a fallback, not the catalog.
//
// Order = trending rank across periods (yearly → monthly → weekly →
// daily), deduped on work key, capped at TOP_N. `yearly` is the
// broadest period OL serves — there is no all-time endpoint.

import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const ROWS_PER_FILE = 500;
const TOP_N = 500;
const FETCH_LIMIT = 1000;
const PERIODS = ['yearly', 'monthly', 'weekly', 'daily'];
const BASE = 'https://openlibrary.org';
const COVER_BASE = 'https://covers.openlibrary.org/b/id';
const USER_AGENT = 'MikoDB-Seed/1.0 (catalog seed; contact: github)';

const isMain = process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
const [,, outDir = 'seed-ol'] = process.argv;

// ---- TitleNormalizer twin (keep in sync with TitleNormalizer.kt /
// worker/src/normalize.ts + build-mb-catalog-seed.mjs) ----
const NONWORD = /[^\p{L}\p{N}\s]/gu;
const SPACES = /\s+/g;
export function normalize(t) {
    if (!t) return '';
    return t.normalize('NFKC').toLowerCase().replace(NONWORD, ' ').replace(SPACES, ' ').trim();
}

const sql = (s) => `'${String(s).replace(/'/g, "''")}'`;
const sqlOrNull = (s) => (s == null || s === '' ? 'NULL' : sql(s));
const numOrNull = (n) => (n == null || !Number.isFinite(Number(n)) ? 'NULL' : String(Number(n)));

const WORK_KEY = /^\/works\/(OL\d+W)$/;
const normalizeIsbn = (s) => (s || '').replace(/[^0-9Xx]/g, '').toUpperCase();
const isValidIsbn = (s) => /^\d{10}$|^\d{13}$/.test(s) || /^[0-9]{9}[0-9X]$/.test(s);

async function trending(period) {
    const url = `${BASE}/trending/${period}.json?limit=${FETCH_LIMIT}`;
    const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
    if (!res.ok) throw new Error(`trending ${period}: HTTP ${res.status}`);
    const body = await res.json();
    return Array.isArray(body.works) ? body.works : [];
}

async function build() {
    // Rank-ordered dedupe: earlier periods win (forever = all-time).
    const seen = new Map(); // workKey -> doc
    for (const period of PERIODS) {
        const docs = await trending(period);
        for (const d of docs) {
            const key = typeof d.key === 'string' ? WORK_KEY.exec(d.key)?.[1] : null;
            if (key && d.title && !seen.has(key)) seen.set(key, d);
            if (seen.size >= TOP_N) break;
        }
        if (seen.size >= TOP_N) break;
    }

    fs.mkdirSync(outDir, { recursive: true });
    const fetchedAt = Date.now();
    let works = 0, titles = 0, extIds = 0, fileNo = 0, inFile = 0;
    let fh = null;
    const openFile = () => {
        fileNo += 1;
        fh = fs.createWriteStream(path.join(outDir, `seed-ol-${String(fileNo).padStart(3, '0')}.sql`));
        // No BEGIN/COMMIT — `wrangler d1 execute --remote --file=` rejects
        // raw transaction statements; every statement below is idempotent.
    };
    const closeFile = async () => {
        await new Promise((res, rej) => { fh.once('error', rej); fh.end(res); });
    };
    const w = (line) => { fh.write(line + '\n'); };

    let rank = 0;
    for (const [workKey, d] of seen) {
        if (inFile === 0) openFile();
        rank += 1;
        const workId = `mk:ol:${workKey}`;
        const title = String(d.title);
        const norm = normalize(title);
        const author = Array.isArray(d.author_name) && d.author_name.length ? String(d.author_name[0]) : null;
        const cover = Number.isFinite(d.cover_i) ? `${COVER_BASE}/${d.cover_i}-M.jpg` : null;
        const isbns = Array.isArray(d.isbn) ? d.isbn.map(normalizeIsbn).filter(isValidIsbn) : [];

        // popularity = trending rank (lower = more popular) so the
        // worker's `popularity IS NULL, popularity ASC` sort reproduces
        // chart order. rating stays NULL — trending docs don't carry it.
        w(`INSERT OR IGNORE INTO works_cache (miko_work_id, type, title, romanized_title, native_title, normalized_title, year, total_chapters, author, provider_links, medium, nsfw, content_rating, rating, popularity, cover_url, genres, fetched_at) VALUES (${sql(workId)}, 'book', ${sql(title)}, NULL, NULL, ${sql(norm)}, ${numOrNull(d.first_publish_year)}, NULL, ${sqlOrNull(author)}, '[]', 'books', 0, NULL, NULL, ${rank}, ${sqlOrNull(cover)}, NULL, ${fetchedAt});`);
        works++;
        if (norm) {
            w(`INSERT INTO works_titles (miko_work_id, title, lang, normalized) SELECT ${sql(workId)}, ${sql(title)}, NULL, ${sql(norm)} WHERE NOT EXISTS (SELECT 1 FROM works_titles t WHERE t.miko_work_id = ${sql(workId)} AND t.normalized = ${sql(norm)});`);
            titles++;
        }
        w(`INSERT OR IGNORE INTO external_ids (provider, external_id, miko_work_id) VALUES ('openlibrary', ${sql(workKey)}, ${sql(workId)});`);
        extIds++;
        // Same last-one-wins shape as resolve.ts persistBookWork — every
        // ISBN on the doc maps to this work.
        for (const isbn of isbns) {
            w(`INSERT OR IGNORE INTO external_ids (provider, external_id, miko_work_id) VALUES ('isbn', ${sql(isbn)}, ${sql(workId)});`);
            extIds++;
        }
        inFile++;
        if (inFile >= ROWS_PER_FILE) {
            await closeFile();
            inFile = 0;
        }
    }
    if (inFile > 0) await closeFile();
    console.log(JSON.stringify({ files: fileNo, works, titles, external_ids: extIds, source_rows: seen.size }, null, 2));
}

if (isMain) build().catch((e) => { console.error(e); process.exit(1); });
