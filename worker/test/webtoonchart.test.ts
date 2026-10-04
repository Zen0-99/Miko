import { env, SELF } from 'cloudflare:test';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Env } from '../src/env';
import { refreshWebtoonChart } from '../src/webtoonchart';

// Webtoon platform-chart ingestion: POPULAR + genre boards → ranked
// works_charts rows + external_ids/provider_links provenance, surfaced
// by /v1/works/browse sort=popularity ahead of the MangaBaka tail.
async function seedWork(
    id: string,
    w: { title: string; type?: string; medium?: string; popularity?: number; nsfw?: number },
) {
    await env.COMMENTS_DB.prepare(
        `INSERT INTO works_cache
         (miko_work_id, type, title, normalized_title, fetched_at, medium, popularity, nsfw, provider_links)
         VALUES (?, ?, ?, ?, 1, ?, ?, ?, '[]')`,
    ).bind(id, w.type ?? 'webtoon', w.title, w.title.toLowerCase(),
        w.medium ?? 'manga', w.popularity ?? null, w.nsfw ?? 0).run();
    await env.COMMENTS_DB.prepare(
        'INSERT INTO works_titles (miko_work_id, title, normalized) VALUES (?, ?, ?)',
    ).bind(id, w.title, w.title.toLowerCase()).run();
}

function board(entries: Array<Partial<{
    titleNo: number; title: string; titleGroupName: string;
    representGenreSeoCode: string; likeitCount: number;
    unsuitableForChildren: boolean; posterThumbnail: string;
}>>) {
    return {
        titleList: entries.map((e) => ({
            titleNo: e.titleNo ?? 1,
            title: e.title ?? 'X',
            titleGroupName: e.titleGroupName ?? 'x',
            representGenreSeoCode: e.representGenreSeoCode ?? 'fantasy',
            likeitCount: e.likeitCount ?? 0,
            unsuitableForChildren: e.unsuitableForChildren ?? false,
            posterThumbnail: e.posterThumbnail ?? null,
        })),
    };
}

const realFetch = globalThis.fetch;
let boards: Record<string, unknown> = {};

function stubWebtoon(tabs: string[] = ['TRENDING', 'POPULAR', 'DRAMA', 'FANTASY']) {
    boards = {};
    const tabsHtml = tabs
        .map((t) => `<li class="item"><a href="#" data-sub-tab="${t}">${t}</a></li>`)
        .join('');
    vi.stubGlobal('fetch', async (url: RequestInfo | URL, init?: RequestInit) => {
        const u = String(url);
        const boardMatch = u.match(/\/en\/ranking\/titles\/([a-z_]+)$/);
        if (boardMatch) {
            const tab = boardMatch[1].toUpperCase();
            return new Response(JSON.stringify(boards[tab] ?? { titleList: [] }), {
                headers: { 'content-type': 'application/json' },
            });
        }
        if (u.endsWith('/en/ranking')) {
            return new Response(`<html><body><ul>${tabsHtml}</ul></body></html>`);
        }
        return realFetch(url, init);
    });
    (env as Env).WEBTOON_BASE = 'http://wt.test';
}

describe('webtoon chart — refresh', () => {
    beforeEach(() => stubWebtoon());
    afterEach(() => vi.unstubAllGlobals());

    it('POPULAR order leads, genre-only entries follow by likeitCount', async () => {
        await seedWork('mk:mb:osora', { title: 'Osora' });
        await seedWork('mk:mb:sf', { title: 'Superfish' });
        await seedWork('mk:mb:genre1', { title: 'Genre Big', popularity: 5 });
        await seedWork('mk:mb:genre2', { title: 'Genre Small', popularity: 3 });
        boards['POPULAR'] = board([
            { titleNo: 6202, title: 'Osora', likeitCount: 100 },
            { titleNo: 7777, title: 'Superfish', likeitCount: 90 },
        ]);
        boards['DRAMA'] = board([
            { titleNo: 1, title: 'Genre Big', likeitCount: 500 },
            { titleNo: 2, title: 'Genre Small', likeitCount: 50 },
        ]);
        boards['FANTASY'] = board([
            { titleNo: 7777, title: 'Superfish', likeitCount: 95 },
        ]);

        const stats = await refreshWebtoonChart(env);
        expect(stats.chartWritten).toBe(4);

        const { results } = await env.COMMENTS_DB.prepare(
            `SELECT miko_work_id, rank, metric FROM works_charts
             WHERE chart='webtoon_popular' ORDER BY rank`,
        ).all<{ miko_work_id: string; rank: number; metric: number }>();
        // POPULAR 1-2 keep platform order even though Genre Big's like
        // count is higher; the genre tail sorts by likeitCount.
        expect(results.map((r) => r.miko_work_id)).toEqual([
            'mk:mb:osora', 'mk:mb:sf', 'mk:mb:genre1', 'mk:mb:genre2',
        ]);
        // genre-board fresher likeitCount wins the max-merge
        expect(results[1].metric).toBe(95);
    });

    it('writes webtoons external_ids and provider_links as provenance', async () => {
        await seedWork('mk:mb:osora', { title: 'Osora' });
        boards['POPULAR'] = board([{ titleNo: 6202, title: 'Osora', titleGroupName: 'osora' }]);
        await refreshWebtoonChart(env);

        const ext = await env.COMMENTS_DB.prepare(
            `SELECT external_id, miko_work_id, provenance FROM external_ids WHERE provider='webtoons'`,
        ).all<{ external_id: string; miko_work_id: string; provenance: string }>();
        expect(ext.results).toEqual([
            { external_id: '6202', miko_work_id: 'mk:mb:osora', provenance: 'webtoon_chart' },
        ]);

        const links = await env.COMMENTS_DB.prepare(
            `SELECT provider_links FROM works_cache WHERE miko_work_id='mk:mb:osora'`,
        ).first<{ provider_links: string }>();
        expect(links!.provider_links).toContain('"webtoons"');
        expect(links!.provider_links).toContain('title_no=6202');
    });

    it('ambiguous normalized titles are skipped, never guessed', async () => {
        await seedWork('mk:mb:a1', { title: 'Shared' });
        await seedWork('mk:mb:a2', { title: 'Shared' });
        boards['POPULAR'] = board([{ titleNo: 9, title: 'Shared' }]);
        const stats = await refreshWebtoonChart(env);
        expect(stats.ambiguous).toBe(1);
        expect(stats.chartWritten).toBe(0);
        const { results } = await env.COMMENTS_DB.prepare(
            `SELECT * FROM works_charts WHERE chart='webtoon_popular'`,
        ).all();
        expect(results).toHaveLength(0);
    });

    it('dupe titles resolve via webtoon type, then ranked row', async () => {
        // webtoon-typed row beats same-title rows of other types
        await seedWork('mk:mb:wt', { title: 'Duped', type: 'webtoon' });
        await seedWork('mk:mb:mh', { title: 'Duped', type: 'manhwa', popularity: 5 });
        // two webtoon dupes: the ranked main entry beats the sparse one
        await seedWork('mk:mb:main', { title: 'Duped2', type: 'webtoon', popularity: 3 });
        await seedWork('mk:mb:dupe', { title: 'Duped2', type: 'webtoon' });
        // nothing distinguishes these — still skipped
        await seedWork('mk:mb:x1', { title: 'Duped3', type: 'manhwa' });
        await seedWork('mk:mb:x2', { title: 'Duped3', type: 'oel' });
        boards['POPULAR'] = board([
            { titleNo: 1, title: 'Duped' },
            { titleNo: 2, title: 'Duped2' },
            { titleNo: 3, title: 'Duped3' },
        ]);
        const stats = await refreshWebtoonChart(env);
        expect(stats.ambiguous).toBe(1);
        const { results } = await env.COMMENTS_DB.prepare(
            `SELECT miko_work_id, rank FROM works_charts
             WHERE chart='webtoon_popular' ORDER BY rank`,
        ).all<{ miko_work_id: string; rank: number }>();
        expect(results).toEqual([
            { miko_work_id: 'mk:mb:wt', rank: 1 },
            { miko_work_id: 'mk:mb:main', rank: 2 },
        ]);
    });

    it('chart-bound manga/comic rows are retyped webtoon — the platform is scroll-only', async () => {
        await seedWork('mk:mb:gravity', { title: 'Hidden Class Gravity User', type: 'manga' });
        await seedWork('mk:mb:cyber', { title: 'Cyberpunk 2077', type: 'comic' });
        await seedWork('mk:mb:keep', { title: 'Actual Manhwa', type: 'manhwa' });
        boards['POPULAR'] = board([
            { titleNo: 1, title: 'Hidden Class Gravity User' },
            { titleNo: 2, title: 'Cyberpunk 2077' },
            { titleNo: 3, title: 'Actual Manhwa' },
        ]);
        await refreshWebtoonChart(env);

        const { results } = await env.COMMENTS_DB.prepare(
            `SELECT miko_work_id, type FROM works_cache WHERE miko_work_id IN
             ('mk:mb:gravity', 'mk:mb:cyber', 'mk:mb:keep')`,
        ).all<{ miko_work_id: string; type: string }>();
        expect(results.find((r) => r.miko_work_id === 'mk:mb:gravity')!.type).toBe('webtoon');
        expect(results.find((r) => r.miko_work_id === 'mk:mb:cyber')!.type).toBe('webtoon');
        // webtoon-format kinds are left alone
        expect(results.find((r) => r.miko_work_id === 'mk:mb:keep')!.type).toBe('manhwa');
    });

    it('unmatched chart titles just do not land', async () => {
        boards['POPULAR'] = board([{ titleNo: 1, title: 'No Such Row' }]);
        const stats = await refreshWebtoonChart(env);
        expect(stats.fetched).toBe(1);
        expect(stats.chartWritten).toBe(0);
    });

    it('TRENDING board is not folded into the popular chart', async () => {
        await seedWork('mk:mb:mover', { title: 'Big Mover' });
        boards['TRENDING'] = board([{ titleNo: 5, title: 'Big Mover' }]);
        boards['POPULAR'] = board([]);
        const stats = await refreshWebtoonChart(env);
        expect(stats.fetched).toBe(0);
        expect(stats.chartWritten).toBe(0);
    });

    it('a total board outage keeps the existing chart', async () => {
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
             VALUES ('webtoon_popular', 'mk:mb:keep', 1, 0, 1)`,
        ).run();
        stubWebtoon([]); // no tabs → nothing fetched
        const stats = await refreshWebtoonChart(env);
        expect(stats.chartWritten).toBe(0);
        const { results } = await env.COMMENTS_DB.prepare(
            `SELECT * FROM works_charts WHERE chart='webtoon_popular'`,
        ).all();
        expect(results).toHaveLength(1);
    });
});

describe('works/browse — platform chart ordering', () => {
    beforeEach(() => stubWebtoon());
    afterEach(() => vi.unstubAllGlobals());

    it('sort=popularity leads chart rows, MangaBaka rank orders the tail', async () => {
        await seedWork('mk:mb:mb1', { title: 'MB Top', popularity: 1 });
        await seedWork('mk:mb:chart2', { title: 'Chart Two', popularity: 900 });
        await seedWork('mk:mb:chart1', { title: 'Chart One', popularity: 950 });
        boards['POPULAR'] = board([
            { titleNo: 1, title: 'Chart One' },
            { titleNo: 2, title: 'Chart Two' },
        ]);
        await refreshWebtoonChart(env);

        const res = await SELF.fetch(
            'http://test/v1/works/browse?medium=manga&sort=popularity&kind=webtoon',
        );
        const body = await res.json<{ works: Array<{ miko_work_id: string }> }>();
        expect(body.works.map((w) => w.miko_work_id)).toEqual([
            'mk:mb:chart1', 'mk:mb:chart2', 'mk:mb:mb1',
        ]);
    });

    it('chart rows do not leak into a kind filter that excludes webtoon', async () => {
        await seedWork('mk:mb:wt', { title: 'Webtoon A', type: 'webtoon', popularity: 50 });
        await seedWork('mk:mb:mg', { title: 'Manga B', type: 'manga', popularity: 999 });
        boards['POPULAR'] = board([{ titleNo: 1, title: 'Webtoon A' }]);
        await refreshWebtoonChart(env);

        const res = await SELF.fetch(
            'http://test/v1/works/browse?medium=manga&sort=popularity&kind=manga',
        );
        const body = await res.json<{ works: Array<{ miko_work_id: string }> }>();
        expect(body.works.map((w) => w.miko_work_id)).toEqual(['mk:mb:mg']);
    });

    it('a stray non-format chart row cannot order the manga slice', async () => {
        // Regression for chart rows bound to a 'manga'-typed work —
        // retype normally fixes these at refresh time, but ordering must
        // never trust the chart for kinds the platform cannot carry.
        await seedWork('mk:mb:bad', { title: 'Stray', type: 'manga', popularity: 500 });
        await seedWork('mk:mb:real', { title: 'Real Manga', type: 'manga', popularity: 1 });
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_charts (chart, miko_work_id, rank, metric, fetched_at)
             VALUES ('webtoon_popular', 'mk:mb:bad', 1, 0, 1)`,
        ).run();

        const res = await SELF.fetch(
            'http://test/v1/works/browse?medium=manga&sort=popularity&kind=manga',
        );
        const body = await res.json<{ works: Array<{ miko_work_id: string }> }>();
        // MangaBaka rank order preserved — 'real' (popularity 1) leads.
        expect(body.works.map((w) => w.miko_work_id)).toEqual(['mk:mb:real', 'mk:mb:bad']);
    });

    it('chart ordering still applies to a single webtoon-format kind', async () => {
        await seedWork('mk:mb:mh', { title: 'Chart Manhwa', type: 'manhwa', popularity: 900 });
        await seedWork('mk:mb:mh2', { title: 'Tail Manhwa', type: 'manhwa', popularity: 1 });
        boards['POPULAR'] = board([{ titleNo: 1, title: 'Chart Manhwa' }]);
        await refreshWebtoonChart(env);

        const res = await SELF.fetch(
            'http://test/v1/works/browse?medium=manga&sort=popularity&kind=manhwa',
        );
        const body = await res.json<{ works: Array<{ miko_work_id: string }> }>();
        expect(body.works.map((w) => w.miko_work_id)).toEqual(['mk:mb:mh', 'mk:mb:mh2']);
    });

    it('sort=rating ignores chart order entirely', async () => {
        await seedWork('mk:mb:wt', { title: 'Webtoon A', popularity: 50 });
        await seedWork('mk:mb:hi', { title: 'Rated', popularity: 900 });
        boards['POPULAR'] = board([{ titleNo: 1, title: 'Webtoon A' }]);
        await refreshWebtoonChart(env);
        await env.COMMENTS_DB.prepare(
            `UPDATE works_cache SET rating = 99 WHERE miko_work_id = 'mk:mb:hi'`,
        ).run();

        const res = await SELF.fetch(
            'http://test/v1/works/browse?medium=manga&sort=rating',
        );
        const body = await res.json<{ works: Array<{ miko_work_id: string }> }>();
        expect(body.works.map((w) => w.miko_work_id)).toEqual(['mk:mb:hi', 'mk:mb:wt']);
    });

    it('refresh route is crawler-key gated', async () => {
        const unauth = await SELF.fetch('http://test/v1/charts/webtoon/refresh', { method: 'POST' });
        expect(unauth.status).toBe(403);
        const bad = await SELF.fetch('http://test/v1/charts/webtoon/refresh', {
            method: 'POST', headers: { 'x-crawler-key': 'wrong' },
        });
        expect(bad.status).toBe(403);
        await seedWork('mk:mb:osora', { title: 'Osora' });
        boards['POPULAR'] = board([{ titleNo: 1, title: 'Osora' }]);
        const good = await SELF.fetch('http://test/v1/charts/webtoon/refresh', {
            method: 'POST', headers: { 'x-crawler-key': 'test-crawler-key' },
        });
        expect(good.status).toBe(200);
        const body = await good.json<{ chartWritten: number }>();
        expect(body.chartWritten).toBe(1);
    });
});
