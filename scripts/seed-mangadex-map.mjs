/**
 * seed-mangadex-map.mjs — bulk-seed MangaDex chapter feeds into
 * canonical_chapters through the public /v1/chapters/canonize contract.
 *
 * The script is a "well-fed device": it resolves work→MangaDex id by
 * normalized-title + year/author corroboration (same rules as the
 * worker's mangadexseed.ts), fetches the EN feed, and POSTs it as
 * source_key 'mangadex' with provider_ref=mdId — which also persists
 * the external_ids binding the cron refresh walks.
 *
 *   node scripts/seed-mangadex-map.mjs [--limit 500] [--offset 0] [--dry]
 *
 * Env: WORKER_URL (default miko-comments workers.dev), D1 access via
 * `npx wrangler d1 execute` (must be run from worker/ context or have
 * wrangler on PATH).
 */
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const WORKER = process.env.WORKER_URL ?? 'https://miko-comments.keypop3750.workers.dev';
const MD = 'https://api.mangadex.org';
const UA = { 'user-agent': 'miko-db-seed/1.0 (+github.com/Zen0-99/Miko)' };

const args = new Set(process.argv.slice(2));
const argVal = (k, d) => {
    const i = process.argv.indexOf(k);
    return i >= 0 ? Number(process.argv[i + 1]) : d;
};
const LIMIT = argVal('--limit', 500);
const OFFSET = argVal('--offset', 0);
const DRY = args.has('--dry');

// --- twin of worker/src/normalize.ts + app TitleNormalizer.kt ---
const NONWORD = /[^\p{L}\p{N}\s]/gu;
const SPACES = /\s+/g;
const normalize = (t) =>
    (t ?? '').normalize('NFKC').toLowerCase().replace(NONWORD, ' ').replace(SPACES, ' ').trim();

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let lastReq = 0;

async function md(pathname) {
    const dt = Date.now() - lastReq;
    if (dt < 340) await sleep(340 - dt); // ~3 req/s — MD limits per-IP
    lastReq = Date.now();
    const res = await fetch(`${MD}${pathname}`, { headers: { ...UA, accept: 'application/json' } });
    if (res.status === 429) { await sleep(5000); return md(pathname); }
    if (!res.ok) throw new Error(`mangadex ${res.status} ${pathname}`);
    return res.json();
}

const WRANGLER = path.join(HERE, '..', 'worker', 'node_modules', 'wrangler', 'bin', 'wrangler.js');

function d1(sql) {
    // Invoke wrangler's JS bin via the current node — Windows blocks
    // .cmd shims on execFileSync, and npx adds resolution noise.
    // SQL is flattened to a single line: multi-line argv breaks the
    // Cloudflare API call on Windows.
    const flat = sql.replace(/\s+/g, ' ');
    const out = execFileSync(
        process.execPath,
        [WRANGLER, 'd1', 'execute', 'miko-comments', '--remote', '--json', '--command', flat],
        { cwd: path.join(HERE, '..', 'worker'), maxBuffer: 64 * 1024 * 1024 },
    ).toString();
    // wrangler prints a JSON array of result objects
    return JSON.parse(out)[0]?.results ?? [];
}

async function resolveMdId(w) {
    const titles = [w.title, w.romanized_title, w.native_title].map(normalize).filter(Boolean);
    if (!titles.length) return null;
    const s = await md(
        `/manga?title=${encodeURIComponent(w.title)}&limit=10` +
        '&contentRating[]=safe&contentRating[]=suggestive' +
        '&contentRating[]=erotica&contentRating[]=pornographic' +
        '&includes[]=author&includes[]=artist',
    );
    for (const m of s.data ?? []) {
        const a = m.attributes;
        const cands = [
            ...Object.values(a.title ?? {}),
            ...(a.altTitles ?? []).flatMap((t) => Object.values(t)),
        ].map(normalize);
        if (!cands.some((c) => titles.includes(c))) continue;
        const author = normalize(w.author);
        const names = (m.relationships ?? [])
            .filter((r) => r.type === 'author' || r.type === 'artist')
            .map((r) => normalize(r.attributes?.name))
            .filter(Boolean);
        const authorHit = !!author &&
            names.some((n) => n === author || n.includes(author) || author.includes(n));
        const yearHit = !!a.year && !!w.year && Math.abs(a.year - w.year) <= 1;
        return { id: m.id, confidence: authorHit || yearHit ? 'high' : 'low' };
    }
    return null;
}

async function fetchFeed(mdId) {
    const out = [];
    let offset = 0;
    for (let page = 0; page < 8; page++) {
        const d = await md(
            `/manga/${mdId}/feed?translatedLanguage[]=en&order[chapter]=asc` +
            `&limit=500&offset=${offset}&includeEmptyPages=0` +
            '&includeFutureUpdates=0&includeUnavailable=0',
        );
        for (const ch of d.data ?? []) {
            const a = ch.attributes;
            const num = a.chapter != null ? parseFloat(a.chapter) : NaN;
            const number = Number.isFinite(num) ? num : null;
            const vol = a.volume != null ? parseFloat(a.volume) : NaN;
            const title = (a.title ?? '').trim();
            out.push({
                name: number !== null
                    ? `Chapter ${a.chapter}${title ? ` — ${title}` : ''}`
                    : (title || 'Extra'),
                url: a.externalUrl ?? `https://mangadex.org/chapter/${ch.id}`,
                date_upload: a.publishAt ? Date.parse(a.publishAt) || null : null,
                number,
                volume: Number.isFinite(vol) ? vol : null,
                part: null,
                kind: number === null ? 'extra' : 'chapter',
            });
        }
        offset += (d.data ?? []).length;
        if (offset >= (d.total ?? offset) || !(d.data ?? []).length) break;
    }
    return out;
}

async function canonize(workId, mdId, chapters, confidence) {
    const res = await fetch(`${WORKER}/v1/chapters/canonize`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
            work_id: workId,
            feeds: [{
                source_key: 'mangadex',
                verified: confidence === 'high',
                provider_ref: mdId,
                provenance: 'mangadex_seed',
                chapters,
            }],
        }),
    });
    if (!res.ok) throw new Error(`canonize ${res.status}`);
    return res.json();
}

async function main() {
    const rows = d1(
        `SELECT miko_work_id, title, romanized_title, native_title, year, author
         FROM works_cache
         WHERE medium = 'manga'
           AND miko_work_id NOT IN (
               SELECT miko_work_id FROM external_ids WHERE provider = 'mangadex'
           )
         ORDER BY popularity IS NULL, popularity ASC LIMIT ${LIMIT} OFFSET ${OFFSET}`,
    );
    console.log(`works to resolve: ${rows.length}${DRY ? ' (dry)' : ''}`);

    let ok = 0, fed = 0, miss = 0, amb = 0, empty = 0, fail = 0;
    for (let i = 0; i < rows.length; i++) {
        const w = rows[i];
        try {
            const hit = await resolveMdId(w);
            if (!hit) { miss++; continue; }
            if (hit.confidence !== 'high') { amb++; continue; }
            const chapters = await fetchFeed(hit.id);
            ok++;
            if (!chapters.length) empty++;
            else fed++;
            if (DRY) continue;
            // POST even when empty — provider_ref still lands the
            // external_ids binding, and an empty feed row marks the
            // work as resolved for the cron backlog.
            const r = await canonize(w.miko_work_id, hit.id, chapters, hit.confidence);
            if (i % 10 === 0) {
                console.log(`${i + 1}/${rows.length} ${w.title} -> ${r.count} ch`);
            }
        } catch (e) {
            fail++;
            console.error(`  ${w.miko_work_id} ${w.title}: ${e.message}`);
        }
    }
    console.log(`\nresolved=${ok} seeded=${fed} licensed/empty=${empty} ` +
        `no-match=${miss} ambiguous=${amb} failed=${fail}`);
}

main().catch((e) => { console.error(e); process.exit(1); });
