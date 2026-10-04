/**
 * crawler.mjs — MikoDB server-side chapter crawler.
 *
 * Scheduler: walks the canonical catalog in importance order
 * (top-500 by popularity → works from the last 3 years → the rest),
 * resolves each work on a rotating pool of extensions running inside
 * Suwayomi-Server, fetches chapter lists, and POSTs feeds to
 * /v1/chapters/canonize — the same contract devices use.
 *
 * Runs on the host (not in Docker): Suwayomi rides the NordVPN tunnel
 * via gluetun; the scheduler only talks to localhost + the worker.
 *
 *   node crawler.mjs [--once] [--limit N] [--phase top500|recent|rest]
 *
 * State lives in crawler/state.json — restarts are idempotent.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const WORKER_DIR = path.join(HERE, '..', 'worker');
const WRANGLER = path.join(WORKER_DIR, 'node_modules', 'wrangler', 'bin', 'wrangler.js');

// ---------- env / config ----------
const envFile = {};
if (existsSync(path.join(HERE, '.env'))) {
    for (const l of readFileSync(path.join(HERE, '.env'), 'utf8').split(/\r?\n/)) {
        const m = l.match(/^(\w+)=(.*)$/);
        if (m) envFile[m[1]] = m[2].trim();
    }
}
const SUWAYOMI = process.env.SUWAYOMI ?? envFile.SUWAYOMI ?? 'http://127.0.0.1:4567';
const WORKER = process.env.WORKER_URL ?? envFile.WORKER_URL ?? 'https://miko-comments.keypop3750.workers.dev';
const CRAWLER_KEY = process.env.CRAWLER_KEY ?? envFile.CRAWLER_KEY ?? '';
const KEIYOUSHI_INDEX = 'https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json';
const POOL = JSON.parse(readFileSync(path.join(HERE, 'sources.json'), 'utf8'));
const LIM = POOL.limits;

const argVal = (k, d) => {
    const i = process.argv.indexOf(k);
    return i >= 0 ? Number(process.argv[i + 1]) : d;
};
const argStr = (k) => {
    const i = process.argv.indexOf(k);
    return i >= 0 ? process.argv[i + 1] : null;
};
const LIMIT = argVal('--limit', 0);
const ONCE = process.argv.includes('--once');
const ONLY = argStr('--work');
const PHASE = argStr('--phase');

// ---------- pacing ----------
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const jitter = (ms) => ms * (0.8 + Math.random() * 0.4);
const CHAPTER_FETCH_GAP = Number(process.env.CHAPTER_FETCH_GAP_MS ?? 30_000);   // ≥30s between chapter fetches
const WORK_GAP = Number(process.env.WORK_GAP_MS ?? 90_000);                    // ≥90s between works
const BROWSE_GAP = Number(process.env.BROWSE_GAP_MS ?? 4_000);                 // lighter hits between browse pages

// ---------- state ----------
const STATE_PATH = path.join(HERE, 'state.json');
const state = existsSync(STATE_PATH)
    ? JSON.parse(readFileSync(STATE_PATH, 'utf8'))
    : { done: [], unresolved: [], sources: {} };
const save = () => writeFileSync(STATE_PATH, JSON.stringify(state, null, 2));
const day = () => new Date().toISOString().slice(0, 10);

function srcState(key) {
    const s = state.sources[key] ??= {
        fetchesToday: 0, dayStamp: day(), consecFail: 0, cooldownUntil: 0,
    };
    if (s.dayStamp !== day()) {
        s.fetchesToday = 0; s.dayStamp = day(); s.consecFail = 0;
    }
    return s;
}
const cooled = (key) => Date.now() >= srcState(key).cooldownUntil;
const budgeted = (key) => srcState(key).fetchesToday < LIM.dailyFetchBudget;

// ---------- GraphQL ----------
async function gql(query) {
    const res = await fetch(`${SUWAYOMI}/api/graphql`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ query }),
    });
    if (!res.ok) throw new Error(`suwayomi ${res.status}`);
    const j = await res.json();
    if (j.errors?.length) throw new Error(j.errors[0].message ?? 'gql error');
    return j.data;
}

// ---------- catalog ----------
function d1(sql) {
    const out = execFileSync(
        process.execPath,
        [WRANGLER, 'd1', 'execute', 'miko-comments', '--remote', '--json', '--command', sql.replace(/\s+/g, ' ')],
        { cwd: WORKER_DIR, maxBuffer: 512 * 1024 * 1024 },
    ).toString();
    return JSON.parse(out)[0]?.results ?? [];
}

function buildQueue() {
    const year = new Date().getFullYear();
    const done = new Set(state.done);
    const take = (rows) => rows.filter((r) => !done.has(r.miko_work_id));
    const want = (p) => !PHASE || PHASE === p;
    const top500 = want('top500') ? take(d1(
        `SELECT miko_work_id, title FROM works_cache WHERE medium = 'manga'
         ORDER BY popularity IS NULL, popularity ASC LIMIT 500`,
    )) : [];
    const recent = want('recent') ? take(d1(
        `SELECT miko_work_id, title FROM works_cache
         WHERE medium = 'manga' AND year >= ${year - 3}
         ORDER BY popularity IS NULL, popularity ASC LIMIT 5000`,
    )) : [];
    const rest = want('rest') ? take(d1(
        `SELECT miko_work_id, title FROM works_cache WHERE medium = 'manga'
         ORDER BY popularity IS NULL, popularity ASC LIMIT 50000`,
    )) : [];
    return { top500, recent, rest };
}

// ---------- worker ----------
async function workerGet(p) {
    const res = await fetch(`${WORKER}${p}`);
    if (!res.ok) throw new Error(`worker ${res.status} ${p}`);
    return res.json();
}

async function canonize(workId, sourceKey, providerRef, chapters, score) {
    const res = await fetch(`${WORKER}/v1/chapters/canonize`, {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            'x-crawler-key': CRAWLER_KEY,
        },
        body: JSON.stringify({
            work_id: workId,
            feeds: [{
                source_key: sourceKey,
                provider_ref: providerRef,
                provenance: 'crawler',
                verified: true,
                score: score ?? null,
                chapters,
            }],
        }),
    });
    if (!res.ok) throw new Error(`canonize ${res.status}`);
    return res.json();
}

// ---------- suwayomi ----------
let indexCache = null;
async function keiyoushiIndex() {
    if (!indexCache) {
        const idx = await (await fetch(KEIYOUSHI_INDEX)).json();
        // extensionList = { <key>: <entries array> } (one key today;
        // tolerate a JSON-string form too).
        const v = Object.values(idx.extensionList)[0];
        indexCache = typeof v === 'string' ? JSON.parse(v) : v;
    }
    return indexCache;
}

async function installedSources() {
    const d = await gql(
        `{ sources { nodes { id name lang homeUrl extension { pkgName } } } }`,
    );
    return d.sources.nodes;
}

/** Install the extension matching pkgSuffix from keiyoushi index. */
async function ensureExtension(pkgSuffix) {
    const idx = await keiyoushiIndex();
    const ext = idx.find((e) => e.packageName?.endsWith(pkgSuffix));
    if (!ext) throw new Error(`no keiyoushi entry for ${pkgSuffix}`);
    const jar = await fetch(ext.resources.jarUrl);
    if (!jar.ok) throw new Error(`jar ${jar.status} ${ext.resources.jarUrl}`);
    const buf = Buffer.from(await jar.arrayBuffer());
    const form = new FormData();
    form.append('operations', JSON.stringify({
        query: `mutation ($file: Upload!) {
            installExternalExtension(input: {extensionFile: $file}) { extension { pkgName } }
        }`,
        variables: { file: null },
    }));
    form.append('map', JSON.stringify({ '0': ['variables.file'] }));
    // Filename must be unique per extension — Suwayomi stages it at
    // extensions/<name> and a fixed name collides on the second install.
    form.append('0', new Blob([buf]), `${ext.packageName}.jar`);
    const res = await fetch(`${SUWAYOMI}/api/graphql`, { method: 'POST', body: form });
    const j = await res.json();
    if (j.errors?.length) throw new Error(j.errors[0].message);
    // Give the dex loader a beat before the first fetchSourceManga.
    await sleep(4000);
    return ext.packageName;
}

/** keiyoushi index entry for a pkg — carries the Suwayomi source id +
 *  homeUrl for absolutizing chapter refs. */
async function indexEntry(pkgName) {
    const idx = await keiyoushiIndex();
    return idx.find((e) => e.packageName === pkgName) ?? null;
}

const NONWORD = /[^\p{L}\p{N}\s]/gu;
const normalize = (t) =>
    (t ?? '').normalize('NFKC').toLowerCase().replace(NONWORD, ' ').replace(/\s+/g, ' ').trim();

async function fetchPage(srcId, type, page, query) {
    const q = query ? `, query: "${query.replace(/"/g, '\\"')}"` : '';
    const d = await gql(
        `mutation { fetchSourceManga(input: {
            source: "${srcId}", type: ${type}, page: ${page}${q}
        }) { mangas { id title url } hasNextPage } }`,
    );
    return d.fetchSourceManga;
}

/** Find the manga entry for (title | bindingRef) on a source. */
async function resolveManga(srcId, workTitle, bindingRef) {
    const want = normalize(workTitle);
    const match = (m) =>
        m.url === bindingRef || normalize(m.title) === want ||
        normalize(m.title).includes(want) && want.length > 6;
    // SEARCH first — cheap when the extension's filters survive.
    try {
        let page = 1;
        for (; page <= 2; page++) {
            const r = await fetchPage(srcId, 'SEARCH', page, workTitle);
            const hit = (r.mangas ?? []).find(match);
            if (hit) return hit;
            if (!r.hasNextPage) break;
            await sleep(jitter(BROWSE_GAP));
        }
    } catch { /* SEARCH broken on some exts (spike) — fall through */ }
    // Browse walk: POPULAR then LATEST, bounded pages.
    for (const type of ['POPULAR', 'LATEST']) {
        for (let page = 1; page <= LIM.browsePageCap; page++) {
            try {
                const r = await fetchPage(srcId, type, page);
                const hit = (r.mangas ?? []).find(match);
                if (hit) return hit;
                if (!r.hasNextPage) break;
            } catch (e) {
                break; // source threw mid-walk — move on
            }
            await sleep(jitter(BROWSE_GAP));
        }
    }
    return null;
}

async function fetchChapters(mangaId) {
    const d = await gql(
        `mutation { fetchMangaAndChapters(input: {
            id: ${mangaId}, fetchManga: true, fetchChapters: true
        }) { chapters { name chapterNumber uploadDate url } manga { url } } }`,
    );
    return d.fetchMangaAndChapters;
}

// ---------- per-work ----------
async function processWork(work) {
    const { miko_work_id: id, title } = work;

    const ch = await workerGet(`/v1/works/${encodeURIComponent(id)}/chapters`);
    if (ch.chapters?.length > 0) return 'fed';

    const binds = await workerGet(`/v1/works/${encodeURIComponent(id)}/bindings`);
    const bound = new Map(
        (binds.bindings ?? [])
            .filter((b) => b.source_key.startsWith('eu.kanade.'))
            .map((b) => [b.source_key, b.provider_ref]),
    );

    // Candidate order: bound source first, then tier rotation
    // (S→A→B) skipping cooled/over-budget sources.
    const pool = [...POOL.tiers.S, ...POOL.tiers.A, ...POOL.tiers.B]
        .map((s) => s.pkgSuffix);
    const ordered = [...bound.keys(), ...pool.filter((p) => ![...bound.keys()].some((k) => k.endsWith(p)))];

    for (const pkgSuffix of ordered) {
        let installed = await installedSources();
        let src = installed.find((s) => s.extension?.pkgName?.endsWith(pkgSuffix));
        if (!src) {
            await ensureExtension(pkgSuffix);
            installed = await installedSources();
            src = installed.find((s) => s.extension?.pkgName?.endsWith(pkgSuffix));
            if (!src) { console.error(`  ${pkgSuffix}: install didn't register`); continue; }
        }
        const key = src.extension.pkgName;
        if (!cooled(key) || !budgeted(key)) continue;

        try {
            const manga = await resolveManga(src.id, title, bound.get(key) ?? null);
            if (!manga) { srcState(key).consecFail++; continue; }

            await sleep(jitter(CHAPTER_FETCH_GAP));
            const r = await fetchChapters(manga.id);
            srcState(key).fetchesToday++;
            srcState(key).consecFail = 0;
            const home = src.homeUrl ?? '';
            const abs = (u) => {
                if (!u) return null;
                try { return new URL(u, home).href; } catch { return u; }
            };
            const chapters = (r.chapters ?? [])
                .filter((c) => c.chapterNumber != null || c.name)
                .map((c) => ({
                    name: c.name ?? `Chapter ${c.chapterNumber}`,
                    url: abs(c.url),
                    date_upload: Number(c.uploadDate) || null,
                    number: c.chapterNumber ?? null,
                    kind: 'chapter', volume: null, part: null,
                }));
            if (!chapters.length) { srcState(key).consecFail++; continue; }

            const out = await canonize(id, key, r.manga?.url ?? manga.url, chapters, null);
            console.log(`  ${id} "${title}" <- ${key}: ${out.count} ch`);
            return out.count > 0 ? 'crawled' : 'fed';
        } catch (e) {
            const s = srcState(key);
            s.consecFail++;
            if (/429|403|challenge|rate/i.test(String(e))) {
                s.cooldownUntil = Date.now() + LIM.cooldownMs;
                console.log(`  ${key} cooling 1h: ${e.message}`);
            }
            if (s.consecFail >= LIM.cooldownAfterConsecutiveFailures) {
                s.cooldownUntil = Date.now() + LIM.cooldownMs;
                s.consecFail = 0;
            }
        }
    }
    return 'unresolved';
}

// ---------- main ----------
async function main() {
    if (!CRAWLER_KEY) {
        console.error('CRAWLER_KEY missing — set it in crawler/.env');
        process.exit(1);
    }
    await installedSources(); // connectivity probe — fails fast if suwayomi is down

    // Targeted mode: crawl exactly one work (tracer/debug runs).
    if (ONLY) {
        const rows = d1(
            `SELECT miko_work_id, title FROM works_cache WHERE miko_work_id = '${ONLY}'`,
        );
        const w = rows[0] ?? { miko_work_id: ONLY, title: ONLY };
        const verdict = await processWork(w);
        save();
        console.log(`verdict: ${verdict}`);
        return;
    }

    const q = buildQueue();
    // Dedupe across phases (a work can sit in top500 AND recent AND rest);
    // queue entries carry {miko_work_id, title} straight from works_cache.
    const seen = new Set();
    const queue = [...q.top500, ...q.recent, ...q.rest]
        .filter((w) => !seen.has(w.miko_work_id) && seen.add(w.miko_work_id));
    console.log(`queue: top500=${q.top500.length} recent=${q.recent.length} rest=${q.rest.length} unique=${queue.length}`);

    let done = 0, crawled = 0, skipped = 0, unresolved = 0;
    for (const w of queue) {
        if (LIMIT && done >= LIMIT) break;
        if (state.done.includes(w.miko_work_id)) continue;
        const title = w.title ?? w.miko_work_id;
        try {
            const verdict = await processWork({ miko_work_id: w.miko_work_id, title });
            done++;
            if (verdict === 'crawled') crawled++;
            else if (verdict === 'fed') skipped++;
            else unresolved++;
            state.done.push(w.miko_work_id);
            save();
        } catch (e) {
            console.error(`  ${w.miko_work_id} "${title}": ${e.message}`);
            unresolved++;
        }
        await sleep(jitter(WORK_GAP));
        if (ONCE) break;
    }
    console.log(`\ndone=${done} crawled=${crawled} already-fed=${skipped} unresolved=${unresolved}`);
}

main().catch((e) => { console.error(e); save(); process.exit(1); });
