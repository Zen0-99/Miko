#!/usr/bin/env node
// Manual webtoon platform-chart refresh — the local equivalent of
// worker/src/webtoonchart.ts `refreshWebtoonChart` for when the cron or
// the CRAWLER_KEY-gated route can't be invoked. Fetches the platform's
// own ranking boards, matches titles onto works_cache rows (durable
// external_ids link first, then exact normalized title; ambiguous
// matches are skipped, never guessed), emits a SQL file, and applies it
// via `wrangler d1 execute --remote`.
//
// Usage:
//   node refresh-webtoon-chart.mjs <out.sql>          # emit SQL only
//   node refresh-webtoon-chart.mjs <out.sql> --apply  # emit + apply remote
//   node refresh-webtoon-chart.mjs <out.sql> --local  # emit + apply local dev DB
import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';

const DB = 'miko-comments';
const BASE = 'https://m.webtoons.com';
const UA = 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36';
const CHART = 'webtoon_popular';
const SKIP_TABS = new Set(['TRENDING']);
const BIND_CHUNK = 80;

const NONWORD = /[^\p{L}\p{N}\s]/gu;
const SPACES = /\s+/g;
const normalize = (t) =>
    (t ?? '').normalize('NFKC').toLowerCase().replace(NONWORD, ' ').replace(SPACES, ' ').trim();
const q = (v) => `'${String(v).replaceAll("'", "''")}'`;

async function fetchJson(url) {
    const res = await fetch(url, { headers: { 'User-Agent': UA } });
    if (!res.ok) throw new Error(`${url}: HTTP ${res.status}`);
    return res.json();
}

function tabsOf(html) {
    const seen = new Set();
    for (const m of html.matchAll(/data-sub-tab="([A-Z_]+)"/g)) seen.add(m[1]);
    return [...seen].filter((t) => !SKIP_TABS.has(t));
}

function rankUnion(boards) {
    const byNo = new Map();
    const order = [];
    for (const e of boards.get('POPULAR') ?? []) {
        byNo.set(e.titleNo, e);
        order.push(e.titleNo);
    }
    const rest = [];
    for (const [tab, entries] of boards) {
        if (tab === 'POPULAR') continue;
        for (const e of entries) {
            const existing = byNo.get(e.titleNo);
            if (existing) existing.likeitCount = Math.max(existing.likeitCount, e.likeitCount);
            else {
                byNo.set(e.titleNo, e);
                rest.push(e);
            }
        }
    }
    rest.sort((a, b) => b.likeitCount - a.likeitCount);
    order.push(...rest.map((e) => e.titleNo));
    return order.map((no) => byNo.get(no));
}

// Spawn `node <wrangler.js>` directly — on Windows the npx/wrangler .cmd
// shims cannot be execFile'd (Node ≥20.12 blocks shell-script spawns).
const WRANGLER = new URL('../../worker/node_modules/wrangler/bin/wrangler.js', import.meta.url)
    .pathname.replace(/^\/([A-Za-z]:)/, '$1');

function d1(args, opts = {}) {
    return execFileSync(process.execPath, [WRANGLER, ...args], {
        encoding: 'utf8',
        maxBuffer: 64 * 1024 * 1024,
        ...opts,
    });
}

function d1Query(sql, local) {
    const out = d1(['d1', 'execute', DB, local ? '--local' : '--remote', '--json',
        '--command', sql]);
    const parsed = JSON.parse(out);
    return parsed[0]?.results ?? [];
}

async function matchWorks(entries, local) {
    const matched = new Map();
    let ambiguous = 0;
    const byNo = new Map(entries.map((e) => [String(e.titleNo), e]));

    for (let i = 0; i < entries.length; i += BIND_CHUNK) {
        const nos = entries.slice(i, i + BIND_CHUNK).map((e) => String(e.titleNo));
        const rows = d1Query(
            `SELECT external_id, miko_work_id FROM external_ids
             WHERE provider = 'webtoons' AND external_id IN (${nos.map(q).join(',')})`,
            local,
        );
        for (const r of rows) {
            const e = byNo.get(r.external_id);
            if (e) matched.set(e.titleNo, r.miko_work_id);
        }
    }

    const unmatched = entries.filter((e) => !matched.has(e.titleNo));
    const titleRows = new Map();
    for (let i = 0; i < unmatched.length; i += BIND_CHUNK) {
        const norms = unmatched.slice(i, i + BIND_CHUNK).map((e) => normalize(e.title));
        const rows = d1Query(
            `SELECT t.normalized, t.miko_work_id, w.type, w.popularity FROM works_titles t
             JOIN works_cache w ON w.miko_work_id = t.miko_work_id
             WHERE w.medium = 'manga'
               AND t.normalized IN (${norms.map(q).join(',')})`,
            local,
        );
        for (const r of rows) {
            let list = titleRows.get(r.normalized);
            if (!list) titleRows.set(r.normalized, list = []);
            list.push({ id: r.miko_work_id, type: r.type, popularity: r.popularity });
        }
    }
    for (const e of unmatched) {
        const rows = titleRows.get(normalize(e.title));
        const id = rows ? disambiguate(rows) : null;
        if (id) matched.set(e.titleNo, id);
        else if (rows && rows.length > 1) {
            ambiguous++;
            console.log(`ambiguous '${e.title}' (${rows.length} works)`);
        }
    }
    return { matched, ambiguous };
}

// Same ladder as worker/src/webtoonchart.ts disambiguate: platform-
// linked row first, then the ranked main entry; still-tied = skip.
function disambiguate(rows) {
    if (rows.length === 1) return rows[0].id;
    const wt = rows.filter((r) => r.type === 'webtoon');
    const pool = wt.length > 0 ? wt : rows;
    if (pool.length === 1) return pool[0].id;
    const ranked = pool.filter((r) => r.popularity != null);
    if (ranked.length === 0) return null;
    const best = Math.min(...ranked.map((r) => r.popularity));
    const tops = ranked.filter((r) => r.popularity === best);
    return tops.length === 1 ? tops[0].id : null;
}

const out = process.argv[2];
const apply = process.argv.includes('--apply');
const local = process.argv.includes('--local');
if (!out) {
    console.error('usage: node refresh-webtoon-chart.mjs <out.sql> [--apply|--local]');
    process.exit(1);
}

const page = await fetch(`${BASE}/en/ranking`, { headers: { 'User-Agent': UA } }).then((r) => r.text());
const tabs = tabsOf(page);
console.log(`tabs: ${tabs.join(', ')}`);

const boards = new Map();
for (const tab of tabs) {
    try {
        const body = await fetchJson(`${BASE}/en/ranking/titles/${tab.toLowerCase()}`);
        boards.set(tab, (body.titleList ?? []).map((t) => ({
            titleNo: t.titleNo,
            title: t.title,
            slug: t.titleGroupName,
            genreSeo: t.representGenreSeoCode,
            likeitCount: t.likeitCount ?? 0,
            nsfw: !!t.unsuitableForChildren,
            thumb: t.posterThumbnail ? `https://webtoon-phinf.pstatic.net${t.posterThumbnail}` : null,
        })));
    } catch (e) {
        console.error(`${tab} fetch failed: ${e.message}`);
    }
}
const union = rankUnion(boards);
if (union.length === 0) {
    console.error('no board rows fetched — refusing to emit an empty chart');
    process.exit(1);
}
console.log(`union: ${union.length} titles`);

const { matched, ambiguous } = await matchWorks(union, local);
const ts = Math.floor(Date.now() / 1000);
const stmts = [`DELETE FROM works_charts WHERE chart = ${q(CHART)};`];
let written = 0;
for (let i = 0; i < union.length; i++) {
    const e = union[i];
    const workId = matched.get(e.titleNo);
    if (!workId) continue;
    const url = `https://www.webtoons.com/en/${e.genreSeo}/${e.slug}/list?title_no=${e.titleNo}`;
    stmts.push(
        `REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
         VALUES (${q(CHART)}, ${q(workId)}, ${i + 1}, ${e.likeitCount}, ${ts});`,
        `INSERT OR REPLACE INTO external_ids
         (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
         VALUES ('webtoons', ${q(e.titleNo)}, ${q(workId)}, 1.0, 'webtoon_chart', ${ts});`,
        `UPDATE works_cache SET provider_links = json_insert(
             provider_links, '$[#]',
             json_object('provider', 'webtoons', 'external_id', ${q(e.titleNo)}, 'url', ${q(url)}))
         WHERE miko_work_id = ${q(workId)} AND provider_links NOT LIKE '%"webtoons"%';`,
    );
    if (e.nsfw) {
        stmts.push(`UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = ${q(workId)} AND nsfw = 0;`);
    }
    if (e.thumb) {
        stmts.push(
            `UPDATE works_cache SET cover_url = ${q(e.thumb)}
             WHERE miko_work_id = ${q(workId)} AND cover_url IS NULL;`,
        );
    }
    // Platform presence ⇒ scroll format — MangaBaka's type field
    // misclassifies a few as manga/comic; the chart slot wins.
    stmts.push(
        `UPDATE works_cache SET type = 'webtoon'
         WHERE miko_work_id = ${q(workId)} AND type IN ('manga', 'comic');`,
    );
    written++;
}
writeFileSync(out, stmts.join('\n') + '\n');
console.log(`written: ${written}/${union.length} chart rows (${matched.size} matched, ${ambiguous} ambiguous) → ${out}`);

if (apply || local) {
    d1(['d1', 'execute', DB, local ? '--local' : '--remote', '--file', out], { stdio: 'inherit' });
}
