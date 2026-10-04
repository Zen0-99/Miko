/**
 * novel-crawler.mjs — MikoDB book carrier crawler.
 *
 * Mirrors crawler.mjs but drives Miko's own novel sources
 * (yokai.extension.novel.* — Zen0-99/MikoNovelSources) directly: those are
 * Kotlin scrapers compiled to APKs implementing the NovelSource API, which
 * Suwayomi cannot run — so the scraping logic is ported host-side into
 * novel-sources/*.mjs adapters and executed by Node + cheerio.
 *
 * Egress rides gluetun's HTTP proxy (:8888) so requests leave through the
 * same Nord exit as Suwayomi — no extra ban surface on the home IP.
 *
 *   node novel-crawler.mjs [--once] [--limit N] [--work mk:mb:123]
 *
 * State lives in crawler/novel-state.json — separate from the manga
 * crawler's state.json and fully resumable.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { SOURCES } from './novel-sources/index.mjs';
import { pick } from './novel-sources/_kit.mjs';

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
const WORKER = process.env.WORKER_URL ?? envFile.WORKER_URL ?? 'https://miko-comments.keypop3750.workers.dev';
const CRAWLER_KEY = process.env.CRAWLER_KEY ?? envFile.CRAWLER_KEY ?? '';
const LIM = { dailyFetchBudget: 400, cooldownAfterConsecutiveFailures: 3, cooldownMs: 3_600_000 };

const argVal = (k, d) => {
    const i = process.argv.indexOf(k);
    return i >= 0 ? Number(process.argv[i + 1]) : d;
};
const LIMIT = argVal('--limit', 0);
const ONCE = process.argv.includes('--once');
const ONLY = process.argv.indexOf('--work') >= 0
    ? process.argv[process.argv.indexOf('--work') + 1]
    : null;

// ---------- pacing ----------
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const jitter = (ms) => ms * (0.8 + Math.random() * 0.4);
const SOURCE_GAP = 30_000;   // min spacing between ANY two requests to one source
const WORK_GAP = 90_000;     // per-worker pause between its own works
const WORKERS = Math.max(1, argVal('--workers', Number(process.env.CRAWL_WORKERS ?? 3)));

/**
 * Per-source pacing: slot acquisition is serialized per source, and the slot
 * is held through the wait+stamp — so a source never sees two requests closer
 * than SOURCE_GAP regardless of how many workers are running. Site-facing
 * cadence is identical to the single-threaded crawler.
 */
const sourceLast = new Map();
const sourceSlots = new Map();
async function pace(pkg) {
    const prev = sourceSlots.get(pkg) ?? Promise.resolve();
    let release;
    const next = new Promise((r) => { release = r; });
    sourceSlots.set(pkg, prev.then(() => next));
    await prev;
    try {
        const wait = (sourceLast.get(pkg) ?? 0) + jitter(SOURCE_GAP) - Date.now();
        if (wait > 0) await sleep(wait);
        sourceLast.set(pkg, Date.now());
    } finally {
        release();
    }
}

// ---------- state ----------
const STATE_PATH = path.join(HERE, 'novel-state.json');
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
    const sel = `SELECT miko_work_id, title FROM works_cache WHERE medium = 'books'`;
    const top500 = take(d1(
        `${sel} ORDER BY popularity IS NULL, popularity ASC LIMIT 500`,
    ));
    const recent = take(d1(
        `${sel} AND year >= ${year - 3} ORDER BY popularity IS NULL, popularity ASC LIMIT 5000`,
    ));
    const rest = take(d1(
        `${sel} ORDER BY popularity IS NULL, popularity ASC LIMIT 40000`,
    ));
    return { top500, recent, rest };
}

// ---------- worker ----------
async function workerGet(p) {
    const res = await fetch(`${WORKER}${p}`);
    if (!res.ok) throw new Error(`worker ${res.status} ${p}`);
    return res.json();
}

async function canonize(workId, sourceKey, providerRef, chapters) {
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
                score: null,
                chapters,
            }],
        }),
    });
    if (!res.ok) throw new Error(`canonize ${res.status}`);
    return res.json();
}

// ---------- per-work ----------
async function processWork(work) {
    const { miko_work_id: id, title } = work;

    const ch = await workerGet(`/v1/works/${encodeURIComponent(id)}/chapters`);
    if (ch.chapters?.length > 0) return 'fed';

    const binds = await workerGet(`/v1/works/${encodeURIComponent(id)}/bindings`);
    const bound = new Map(
        (binds.bindings ?? [])
            .filter((b) => b.source_key.startsWith('yokai.extension.novel.'))
            .map((b) => [b.source_key, b.provider_ref]),
    );

    // Candidate order: bound carriers first, then tier rotation S→A→B.
    const pool = [...SOURCES.S, ...SOURCES.A, ...SOURCES.B];
    const ordered = [
        ...pool.filter((s) => [...bound.keys()].includes(s.pkg)),
        ...pool.filter((s) => ![...bound.keys()].includes(s.pkg)),
    ];

    for (const src of ordered) {
        if (!cooled(src.pkg) || !budgeted(src.pkg)) continue;
        try {
            const boundRef = bound.get(src.pkg) ?? null;
            // Resolve the work on the source: bound provider_ref wins
            // outright (it IS the entry), else title-search the catalog.
            await pace(src.pkg);
            const entry = boundRef
                ? { url: boundRef, title }
                : pick(await src.search(title), title);
            srcState(src.pkg).fetchesToday++;
            if (!entry?.url) { srcState(src.pkg).consecFail++; continue; }

            await pace(src.pkg);
            const raw = await src.chapterList(entry.url);
            srcState(src.pkg).fetchesToday++;
            srcState(src.pkg).consecFail = 0;

            const chapters = (raw ?? [])
                .filter((c) => c.url)
                .map((c, i) => ({
                    name: c.name?.trim() || `Chapter ${c.number ?? i + 1}`,
                    url: c.url,
                    date_upload: c.date ?? null,
                    number: c.number ?? null,
                    kind: 'chapter', volume: null, part: null,
                }));
            if (!chapters.length) { srcState(src.pkg).consecFail++; continue; }

            const out = await canonize(id, src.pkg, entry.url, chapters);
            console.log(`  ${id} "${title}" <- ${src.pkg}: ${out.count} ch`);
            return out.count > 0 ? 'crawled' : 'fed';
        } catch (e) {
            const s = srcState(src.pkg);
            s.consecFail++;
            if (/429|403|challenge|rate|ETIMEDOUT|fetch failed/i.test(String(e))) {
                s.cooldownUntil = Date.now() + LIM.cooldownMs;
                console.log(`  ${src.pkg} cooling 1h: ${e.message}`);
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
    const seen = new Set();
    const queue = [...q.top500, ...q.recent, ...q.rest]
        .filter((w) => !seen.has(w.miko_work_id) && seen.add(w.miko_work_id));
    console.log(`queue: top500=${q.top500.length} recent=${q.recent.length} rest=${q.rest.length} unique=${queue.length}`);

    let done = 0, crawled = 0, skipped = 0, unresolved = 0;
    let nextIdx = 0;
    const doneSet = new Set(state.done);
    const workerLoop = async (workerId) => {
        while (true) {
            if (LIMIT && done >= LIMIT) return;
            const w = queue[nextIdx++];
            if (!w) return;
            if (doneSet.has(w.miko_work_id)) continue;
            try {
                const verdict = await processWork(w);
                done++;
                if (verdict === 'crawled') crawled++;
                else if (verdict === 'fed') skipped++;
                else unresolved++;
                doneSet.add(w.miko_work_id);
                state.done.push(w.miko_work_id);
                save();
            } catch (e) {
                console.error(`  [w${workerId}] ${w.miko_work_id} "${w.title}": ${e.message}`);
                unresolved++;
            }
            await sleep(jitter(WORK_GAP));
            if (ONCE) return;
        }
    };
    console.log(`workers: ${WORKERS}`);
    await Promise.all(Array.from({ length: WORKERS }, (_, i) => workerLoop(i + 1)));
    console.log(`\ndone=${done} crawled=${crawled} already-fed=${skipped} unresolved=${unresolved}`);
}

main().catch((e) => { console.error(e); save(); process.exit(1); });
