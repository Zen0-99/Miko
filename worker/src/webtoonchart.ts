import type { Env } from './env';
import { normalize } from './normalize';
import { now } from './util';

// Webtoon platform chart ingestion — the ranking boards behind
// m.webtoons.com/en/ranking (TRENDING / POPULAR / per-genre), which is
// the same API the site's own tab UI drives
// (`/{lang}/ranking/titles/{tab}`). POPULAR is the all-time chart; each
// genre tab is that genre's popularity board. The union — POPULAR order
// first, then genre-only entries by likeitCount — is stored as
// works_charts 'webtoon_popular' so browse can present the platform's
// own ranking rather than MangaBaka's aggregate.
//
// Chart rows also mint `external_ids ('webtoons', title_no)` and a
// webtoons.com entry in works_cache.provider_links — the match is then
// durable across renames and the app's canonize pipeline can direct-
// fetch the platform carrier (`all.webtoons` pkg ↔ 'webtoons' provider).
const DEFAULT_BASE = 'https://m.webtoons.com';
const UA = 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36';
export const WEBTOON_POPULAR_CHART = 'webtoon_popular';

// TRENDING is a biggest-movers board, not a popularity chart — a title
// only earns a popular-chart slot through POPULAR or a genre board.
const SKIP_TABS = new Set(['TRENDING']);
const BIND_CHUNK = 80;

interface ChartEntry {
    titleNo: number;
    title: string;
    slug: string;
    genreSeo: string;
    likeitCount: number;
    nsfw: boolean;
    thumb: string | null;
}

interface TitleListRow {
    titleNo: number;
    title: string;
    titleGroupName: string;
    representGenreSeoCode: string;
    likeitCount: number | null;
    unsuitableForChildren: boolean;
    posterThumbnail: string | null;
}

export interface ChartRefreshStats {
    tabs: number;
    fetched: number;
    matched: number;
    ambiguous: number;
    chartWritten: number;
}

async function fetchBoard(base: string, tab: string): Promise<ChartEntry[]> {
    const res = await fetch(`${base}/en/ranking/titles/${tab.toLowerCase()}`, {
        headers: { 'User-Agent': UA },
    });
    if (!res.ok) throw new Error(`${tab}: HTTP ${res.status}`);
    const body = await res.json<{ titleList?: TitleListRow[] }>();
    return (body.titleList ?? []).map((t) => ({
        titleNo: t.titleNo,
        title: t.title,
        slug: t.titleGroupName,
        genreSeo: t.representGenreSeoCode,
        likeitCount: t.likeitCount ?? 0,
        nsfw: !!t.unsuitableForChildren,
        thumb: t.posterThumbnail
            ? `https://webtoon-phinf.pstatic.net${t.posterThumbnail}`
            : null,
    }));
}

/** Ranking-tab slugs advertised by the ranking page's own markup. */
function tabsOf(html: string): string[] {
    const seen = new Set<string>();
    for (const m of html.matchAll(/data-sub-tab="([A-Z_]+)"/g)) {
        seen.add(m[1]);
    }
    return [...seen].filter((t) => !SKIP_TABS.has(t));
}

/**
 * POPULAR order first (the platform's own ranking, curated — likes do
 * not fully determine it), then union-remaining entries by likeitCount.
 */
function rankUnion(boards: Map<string, ChartEntry[]>): ChartEntry[] {
    const byNo = new Map<number, ChartEntry>();
    const popular = boards.get('POPULAR') ?? [];
    const order: number[] = [];
    for (const e of popular) {
        byNo.set(e.titleNo, e);
        order.push(e.titleNo);
    }
    const rest: ChartEntry[] = [];
    for (const [tab, entries] of boards) {
        if (tab === 'POPULAR') continue;
        for (const e of entries) {
            const existing = byNo.get(e.titleNo);
            if (existing) {
                // Genre boards can carry fresher like counts — keep the max.
                existing.likeitCount = Math.max(existing.likeitCount, e.likeitCount);
            } else {
                byNo.set(e.titleNo, e);
                rest.push(e);
            }
        }
    }
    rest.sort((a, b) => b.likeitCount - a.likeitCount);
    order.push(...rest.map((e) => e.titleNo));
    return order.map((no) => byNo.get(no)!);
}

/**
 * title_no → work: durable `external_ids` link first, then an exact
 * normalized-title match against manga-medium works (works_titles
 * covers alternate titles). A normalized title resolving to several
 * works is ambiguous — skipped rather than bound on a coin flip.
 */
async function matchWorks(
    env: Env,
    entries: ChartEntry[],
): Promise<{ matched: Map<number, string>; ambiguous: number }> {
    const matched = new Map<number, string>();
    let ambiguous = 0;

    const byNo = new Map(entries.map((e) => [String(e.titleNo), e]));
    for (let i = 0; i < entries.length; i += BIND_CHUNK) {
        const nos = entries.slice(i, i + BIND_CHUNK).map((e) => String(e.titleNo));
        const { results } = await env.COMMENTS_DB.prepare(
            `SELECT external_id, miko_work_id FROM external_ids
             WHERE provider = 'webtoons' AND status = 'confirmed'
               AND external_id IN (${nos.map(() => '?').join(',')})`,
        ).bind(...nos).all<{ external_id: string; miko_work_id: string }>();
        for (const r of results) {
            const e = byNo.get(r.external_id);
            if (e) matched.set(e.titleNo, r.miko_work_id);
        }
    }

    const unmatched = entries.filter((e) => !matched.has(e.titleNo));
    const titleRows = new Map<string, Array<{ id: string; type: string | null; popularity: number | null }>>();
    for (let i = 0; i < unmatched.length; i += BIND_CHUNK) {
        const slice = unmatched.slice(i, i + BIND_CHUNK);
        const norms = slice.map((e) => normalize(e.title));
        const { results } = await env.COMMENTS_DB.prepare(
            `SELECT t.normalized, t.miko_work_id, w.type, w.popularity FROM works_titles t
             JOIN works_cache w ON w.miko_work_id = t.miko_work_id
             WHERE w.medium = 'manga'
               AND t.normalized IN (${norms.map(() => '?').join(',')})`,
        ).bind(...norms).all<{
            normalized: string; miko_work_id: string; type: string | null; popularity: number | null;
        }>();
        for (const r of results) {
            let list = titleRows.get(r.normalized);
            if (!list) titleRows.set(r.normalized, list = []);
            list.push({ id: r.miko_work_id, type: r.type, popularity: r.popularity });
        }
    }
    for (const e of unmatched) {
        const rows = titleRows.get(normalize(e.title));
        const id = rows ? disambiguate(rows) : null;
        if (id) {
            matched.set(e.titleNo, id);
        } else if (rows && rows.length > 1) {
            ambiguous++;
            console.log(`webtoon chart: ambiguous '${e.title}' (${rows.length} works)`);
        }
    }
    return { matched, ambiguous };
}

/**
 * title_no → one work when the evidence points to a single row. The
 * catalog carries duplicate rows per title (original + variants), so an
 * exact normalized-title hit on several rows disambiguates by:
 *   1. `type='webtoon'` — the platform-linked row is the chart's subject.
 *   2. Ranked MangaBaka row (lowest popularity) — the main entry; dupe
 *      variants are typically unranked.
 * Anything still tied is ambiguous and skipped, never guessed.
 */
function disambiguate(
    rows: Array<{ id: string; type: string | null; popularity: number | null }>,
): string | null {
    if (rows.length === 1) return rows[0].id;
    const wt = rows.filter((r) => r.type === 'webtoon');
    const pool = wt.length > 0 ? wt : rows;
    if (pool.length === 1) return pool[0].id;
    const ranked = pool.filter((r) => r.popularity != null);
    if (ranked.length === 0) return null;
    const best = Math.min(...ranked.map((r) => r.popularity!));
    const tops = ranked.filter((r) => r.popularity === best);
    return tops.length === 1 ? tops[0].id : null;
}

/** Full board refresh — fetch → match → atomic chart replace. */
export async function refreshWebtoonChart(env: Env): Promise<ChartRefreshStats> {
    const base = env.WEBTOON_BASE ?? DEFAULT_BASE;
    const page = await fetch(`${base}/en/ranking`, { headers: { 'User-Agent': UA } })
        .then((r) => r.text());
    const tabs = tabsOf(page);

    const boards = new Map<string, ChartEntry[]>();
    for (const tab of tabs) {
        try {
            boards.set(tab, await fetchBoard(base, tab));
        } catch (e) {
            console.error(`webtoon chart: ${tab} fetch failed`, e);
        }
    }
    const union = rankUnion(boards);
    if (union.length === 0) {
        // Never blow the chart away on a total fetch outage.
        return { tabs: tabs.length, fetched: 0, matched: 0, ambiguous: 0, chartWritten: 0 };
    }

    const { matched, ambiguous } = await matchWorks(env, union);
    const ts = now();
    const stmts: D1PreparedStatement[] = [
        env.COMMENTS_DB.prepare('DELETE FROM works_charts WHERE chart = ?').bind(WEBTOON_POPULAR_CHART),
    ];
    let written = 0;
    for (let i = 0; i < union.length; i++) {
        const e = union[i];
        const workId = matched.get(e.titleNo);
        if (!workId) continue;
        const url = `https://www.webtoons.com/en/${e.genreSeo}/${e.slug}/list?title_no=${e.titleNo}`;
        stmts.push(
            env.COMMENTS_DB.prepare(
                `REPLACE INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
                 VALUES (?, ?, ?, ?, ?)`,
            ).bind(WEBTOON_POPULAR_CHART, workId, i + 1, e.likeitCount, ts),
            env.COMMENTS_DB.prepare(
                // Never OR REPLACE — that would wipe a conflicting row's
                // lifecycle (status/minter/points, Phase 20). The chart is
                // authoritative for the webtoons→work link itself, so
                // rebind on conflict, but a provisional claim keeps its
                // lifecycle until it solidifies on its own evidence.
                `INSERT INTO external_ids
                 (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
                 VALUES ('webtoons', ?, ?, 1.0, 'webtoon_chart', ?)
                 ON CONFLICT(provider, external_id) DO UPDATE SET
                    miko_work_id = excluded.miko_work_id,
                    confidence = excluded.confidence,
                    provenance = excluded.provenance,
                    last_seen_at = excluded.last_seen_at`,
            ).bind(String(e.titleNo), workId, ts),
            env.COMMENTS_DB.prepare(
                `UPDATE works_cache SET provider_links = json_insert(
                     provider_links, '$[#]',
                     json_object('provider', 'webtoons', 'external_id', ?, 'url', ?))
                 WHERE miko_work_id = ? AND provider_links NOT LIKE '%"webtoons"%'`,
            ).bind(String(e.titleNo), url, workId),
        );
        if (e.nsfw) {
            stmts.push(
                env.COMMENTS_DB.prepare(
                    'UPDATE works_cache SET nsfw = 1 WHERE miko_work_id = ? AND nsfw = 0',
                ).bind(workId),
            );
        }
        if (e.thumb) {
            stmts.push(
                env.COMMENTS_DB.prepare(
                    'UPDATE works_cache SET cover_url = ? WHERE miko_work_id = ? AND cover_url IS NULL',
                ).bind(e.thumb, workId),
            );
        }
        // A chart slot is proof the work is published on the webtoons
        // platform — scroll format, never manga/comic. MangaBaka's own
        // type field misclassifies a few of these (they surface in the
        // manga pill instead of webtoons); presence on the platform
        // wins.
        stmts.push(
            env.COMMENTS_DB.prepare(
                `UPDATE works_cache SET type = 'webtoon'
                 WHERE miko_work_id = ? AND type IN ('manga', 'comic')`,
            ).bind(workId),
        );
        written++;
    }
    // batch() is all-or-nothing — the delete+inserts land atomically.
    await env.COMMENTS_DB.batch(stmts);
    console.log(`webtoon chart: ${written}/${union.length} written (${ambiguous} ambiguous)`);
    return { tabs: tabs.length, fetched: union.length, matched: matched.size, ambiguous, chartWritten: written };
}
