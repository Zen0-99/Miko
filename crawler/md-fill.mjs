/**
 * md-fill.mjs — fill top-500 misses via the MangaDex API.
 *
 * Ports the worker's mangadex.ts path (resolveMangaId + fetchEnglishFeed)
 * and lands results through POST /v1/chapters/canonize — the same write
 * path ingestMangadexFeed uses (canonical_feeds upsert + external_ids
 * binding + remerge), so no worker deploy is needed.
 *
 * Input: crawler/misses.json — wrangler `--json` output whose [0].results
 * rows carry {miko_work_id, title, romanized_title, native_title, year,
 * author} for works that have no canonical_feeds row.
 *
 *   node md-fill.mjs [--limit N]
 *
 * Low-confidence resolves and empty feeds are collected into
 * misses-review.json — never canonized (a wrong id is worse than none).
 * Works with no MangaDex match get a '__none__' tombstone statement
 * printed for wrangler d1 (keeps the seed cron from retrying them).
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));

const envFile = {};
if (existsSync(path.join(HERE, '.env'))) {
    for (const l of readFileSync(path.join(HERE, '.env'), 'utf8').split(/\r?\n/)) {
        const m = l.match(/^(\w+)=(.*)$/);
        if (m) envFile[m[1]] = m[2].trim();
    }
}
const WORKER = process.env.WORKER_URL ?? envFile.WORKER_URL ?? 'https://miko-comments.keypop3750.workers.dev';
const CRAWLER_KEY = process.env.CRAWLER_KEY ?? envFile.CRAWLER_KEY ?? '';
const MD_BASE = 'https://api.mangadex.org';
const UA = 'miko-comments/1.0 (+https://github.com/Zen0-99/Miko)';
const LIMIT = process.argv.includes('--limit')
    ? Number(process.argv[process.argv.indexOf('--limit') + 1])
    : 0;
const REVIEW = process.argv.includes('--review');
// --pair mk:mb:NNN=<md-uuid> — human-verified mapping, skips resolve.
const PAIRS = process.argv.filter((a) => a.startsWith('--pair='))
    .map((a) => a.slice('--pair='.length).split('='));

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const NONWORD = /[^\p{L}\p{N}\s]/gu;
const normalize = (t) =>
    (t ?? '').normalize('NFKC').toLowerCase().replace(NONWORD, ' ').replace(/\s+/g, ' ').trim();

async function md(path) {
    const res = await fetch(`${MD_BASE}${path}`, {
        headers: { 'user-agent': UA, accept: 'application/json' },
    });
    if (!res.ok) throw new Error(`mangadex ${res.status} ${path}`);
    return res.json();
}

/** resolveMangaId port — normalized equality on title/altTitles (any
 *  locale), corroborated by year ±1 or author/artist containment. */
async function resolveMangaId(work) {
    const titles = [work.title, work.romanized_title, work.native_title]
        .map(normalize).filter((t) => t.length > 0);
    if (!titles.length) return null;
    // The misses skew obscure — a subtitled title ("Magi: The Labyrinth
    // of Magic") can crowd the whole top-10 with spin-offs, so every
    // variant gets a query and the union is scored.
    const queries = [work.title, work.romanized_title, work.native_title]
        .filter((t, i, a) => t && a.indexOf(t) === i);
    const seen = new Set();
    const candidates = [];
    for (const q of queries) {
        const search = await md(
            `/manga?title=${encodeURIComponent(q)}&limit=25` +
            '&order[followedCount]=desc' +
            '&contentRating[]=safe&contentRating[]=suggestive' +
            '&contentRating[]=erotica&contentRating[]=pornographic' +
            '&includes[]=author&includes[]=artist',
        );
        await sleep(400);
        for (const m of search.data ?? []) {
            if (!seen.has(m.id)) { seen.add(m.id); candidates.push(m); }
        }
    }
    for (const m of candidates) {
        const a = m.attributes;
        const cands = [
            ...Object.values(a.title ?? {}),
            ...(a.altTitles ?? []).flatMap((t) => Object.values(t)),
        ].map(normalize);
        if (!cands.some((c) => titles.includes(c))) continue;
        const author = normalize(work.author ?? '');
        const names = (m.relationships ?? [])
            .filter((r) => r.type === 'author' || r.type === 'artist')
            .map((r) => normalize(r.attributes?.name))
            .filter((n) => n.length > 0);
        // Word-set compare — MD stores JP names surname-first ("Abe
        // Yoshitoshi") where works_cache has "Yoshitoshi ABe".
        const authorWords = new Set(author.split(' ').filter(Boolean));
        const authorHit = authorWords.size > 0 &&
            names.some((n) => {
                const nw = new Set(n.split(' ').filter(Boolean));
                return nw.size > 0 &&
                    ([...nw].every((w) => authorWords.has(w)) ||
                     [...authorWords].every((w) => nw.has(w)));
            });
        const yearHit = !!a.year && !!work.year && Math.abs(a.year - work.year) <= 1;
        return { id: m.id, confidence: authorHit || yearHit ? 'high' : 'low' };
    }
    return null;
}

/** fetchEnglishFeed port — EN chapters asc, 500/page, 4000 ceiling. */
async function fetchEnglishFeed(mdId) {
    const out = [];
    let offset = 0;
    for (let page = 0; page < 8; page++) {
        const d = await md(
            `/manga/${mdId}/feed?translatedLanguage[]=en&order[chapter]=asc` +
            `&limit=500&offset=${offset}&includeEmptyPages=0` +
            '&includeFutureUpdates=0&includeUnavailable=0',
        );
        await sleep(400);
        const data = d.data ?? [];
        for (const ch of data) {
            const a = ch.attributes;
            const num = a.chapter != null ? parseFloat(a.chapter) : NaN;
            const number = Number.isFinite(num) ? num : null;
            const vol = a.volume != null ? parseFloat(a.volume) : NaN;
            const volume = Number.isFinite(vol) ? vol : null;
            const title = (a.title ?? '').trim();
            out.push({
                name: number !== null
                    ? `Chapter ${a.chapter}${title ? ` — ${title}` : ''}`
                    : (title || 'Extra'),
                url: a.externalUrl ?? `https://mangadex.org/chapter/${ch.id}`,
                date_upload: a.publishAt ? Date.parse(a.publishAt) || null : null,
                number,
                volume,
                part: null,
                kind: number === null ? 'extra' : 'chapter',
            });
        }
        offset += data.length;
        if (offset >= (d.total ?? offset) || data.length === 0) break;
    }
    return out;
}

async function canonize(workId, mdId, chapters) {
    const res = await fetch(`${WORKER}/v1/chapters/canonize`, {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            'x-crawler-key': CRAWLER_KEY,
        },
        body: JSON.stringify({
            work_id: workId,
            feeds: [{
                source_key: 'mangadex',
                provider_ref: mdId,
                provenance: 'crawler',
                verified: true,
                chapters,
            }],
        }),
    });
    if (!res.ok) throw new Error(`canonize ${res.status}`);
    return res.json();
}

async function main() {
    if (!CRAWLER_KEY) {
        console.error('CRAWLER_KEY missing — set it in crawler/.env');
        process.exit(1);
    }
    const raw = JSON.parse(readFileSync(path.join(HERE, 'misses.json'), 'utf8'));
    let works = (Array.isArray(raw) ? raw[0]?.results : raw.results) ?? [];
    if (PAIRS.length) {
        // Forced pairs resolve straight to their md id.
        for (const [id, mdId] of PAIRS) {
            const w = works.find((x) => x.miko_work_id === id)
                ?? { miko_work_id: id, title: id };
            try {
                const chapters = await fetchEnglishFeed(mdId);
                if (!chapters.length) { console.log(`  ${id}: ${mdId} empty EN feed`); continue; }
                const out = await canonize(id, mdId, chapters);
                console.log(`  ${id} "${w.title}" <- mangadex:${mdId.slice(0, 8)}: ${out.count} ch (forced)`);
            } catch (e) {
                console.error(`  ${id}: ${e.message}`);
            }
        }
        return;
    }
    // --review reprocesses the low-confidence candidates from the last
    // run (misses-review.json entries without a `reason` field).
    if (REVIEW) {
        const rev = JSON.parse(readFileSync(path.join(HERE, 'misses-review.json'), 'utf8'));
        const ids = new Set(rev.filter((r) => !r.reason).map((r) => r.work_id));
        works = works.filter((w) => ids.has(w.miko_work_id));
    }
    const queue = LIMIT ? works.slice(0, LIMIT) : works;
    console.log(`md-fill: ${queue.length} works to resolve`);

    const review = [];
    const tombstones = [];
    let filled = 0, empty = 0, low = 0, miss = 0, failed = 0;

    for (const w of queue) {
        const id = w.miko_work_id;
        try {
            const hit = await resolveMangaId(w);
            if (!hit) {
                miss++;
                tombstones.push(id);
                console.log(`  ${id} "${w.title}": no md match`);
                continue;
            }
            if (hit.confidence !== 'high') {
                low++;
                review.push({ work_id: id, title: w.title, candidate: hit.id });
                console.log(`  ${id} "${w.title}": low-confidence md ${hit.id} → review`);
                continue;
            }
            const chapters = await fetchEnglishFeed(hit.id);
            if (!chapters.length) {
                empty++;
                review.push({ work_id: id, title: w.title, candidate: hit.id, reason: 'empty_feed' });
                console.log(`  ${id} "${w.title}": md ${hit.id} empty EN feed`);
                continue;
            }
            const out = await canonize(id, hit.id, chapters);
            filled++;
            console.log(`  ${id} "${w.title}" <- mangadex:${hit.id.slice(0, 8)}: ${out.count} ch`);
        } catch (e) {
            failed++;
            console.error(`  ${id} "${w.title}": ${e.message}`);
        }
    }

    writeFileSync(
        path.join(HERE, 'misses-review.json'),
        JSON.stringify(review, null, 2),
    );
    if (tombstones.length) {
        const stmts = tombstones.map((id) =>
            `INSERT OR IGNORE INTO external_ids (provider, external_id, miko_work_id, provenance, last_seen_at) VALUES ('mangadex', '__none__', '${id}', 'mangadex_fill', ${Date.now()});`,
        );
        writeFileSync(path.join(HERE, 'misses-tombstones.sql'), stmts.join('\n'));
    }
    console.log(`\nfilled=${filled} low-confidence=${low} empty-feed=${empty} no-match=${miss} failed=${failed}`);
    if (tombstones.length) {
        console.log(`tombstones → misses-tombstones.sql (${tombstones.length} rows, wrangler d1 --file)`);
    }
}

main().catch((e) => { console.error(e); process.exit(1); });
