import { env } from 'cloudflare:test';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Env } from '../src/env';
import { ingestMangadexFeed, runMangadexCron } from '../src/mangadexseed';

const W = 'mk:mb:hxh_test';

async function seedWork(
    id: string,
    w: { title: string; romanized?: string; year?: number; author?: string; medium?: string; popularity?: number },
) {
    await env.COMMENTS_DB.prepare(
        `INSERT INTO works_cache
         (miko_work_id, type, title, romanized_title, normalized_title,
          year, author, fetched_at, medium, popularity)
         VALUES (?, 'manga', ?, ?, ?, ?, ?, 1, ?, ?)`,
    ).bind(
        id, w.title, w.romanized ?? null, w.title.toLowerCase(),
        w.year ?? null, w.author ?? null, w.medium ?? 'manga', w.popularity ?? null,
    ).run();
}

function mdSearch(items: unknown[]) {
    return { data: items };
}

function mdManga(id: string, opts: {
    title?: Record<string, string>;
    altTitles?: Array<Record<string, string>>;
    year?: number;
    author?: string;
}) {
    return {
        id,
        attributes: {
            title: opts.title ?? { en: 'Hunter x Hunter' },
            altTitles: opts.altTitles ?? [],
            year: opts.year ?? 1998,
        },
        relationships: opts.author
            ? [{ type: 'author', attributes: { name: opts.author } }]
            : [],
    };
}

function mdFeed(chapters: Array<{ id: string; chapter?: string; title?: string; publishAt?: string; externalUrl?: string }>, total?: number) {
    return {
        result: 'ok',
        total: total ?? chapters.length,
        data: chapters.map((c) => ({
            id: c.id,
            attributes: {
                chapter: c.chapter ?? null,
                title: c.title ?? null,
                volume: '1',
                publishAt: c.publishAt ?? '2024-01-01T00:00:00+00:00',
                externalUrl: c.externalUrl ?? null,
            },
        })),
    };
}

const realFetch = globalThis.fetch;
let routes: Array<{ match: RegExp; body: unknown }> = [];

function stubMd() {
    routes = [];
    vi.stubGlobal('fetch', async (url: RequestInfo | URL, init?: RequestInit) => {
        const u = String(url);
        for (const r of routes) {
            if (r.match.test(u)) {
                return new Response(JSON.stringify(r.body), {
                    headers: { 'content-type': 'application/json' },
                });
            }
        }
        return realFetch(url, init);
    });
    (env as Env).MANGADEX_BASE = 'http://md.test';
}

beforeEach(stubMd);
afterEach(() => vi.unstubAllGlobals());

describe('mangadexseed', () => {
    it('resolves a work, persists the binding, seeds chapters', async () => {
        await seedWork(W, {
            title: 'HUNTER×HUNTER',
            romanized: 'Hunter x Hunter',
            year: 1998,
            author: 'Yoshihiro Togashi',
            popularity: 5,
        });
        routes.push(
            { match: /\/manga\?title=/, body: mdSearch([mdManga('md-hxh', { author: 'Yoshihiro Togashi' })]) },
            {
                match: /\/manga\/md-hxh\/feed/, body: mdFeed([
                    { id: 'c1', chapter: '1', title: 'The Day of Departure', publishAt: '2020-01-01T00:00:00+00:00' },
                    { id: 'c2', chapter: '2', publishAt: '2020-01-08T00:00:00+00:00' },
                ]),
            },
        );

        const r = await ingestMangadexFeed(env as Env, W);
        expect(r.mdId).toBe('md-hxh');
        expect(r.chapters).toBe(2);

        const { results: ids } = await env.COMMENTS_DB.prepare(
            `SELECT external_id FROM external_ids
             WHERE provider = 'mangadex' AND miko_work_id = ?`,
        ).bind(W).all<{ external_id: string }>();
        expect(ids[0]?.external_id).toBe('md-hxh');

        const { results: feeds } = await env.COMMENTS_DB.prepare(
            `SELECT verified FROM canonical_feeds
             WHERE work_id = ? AND source_key = 'mangadex'`,
        ).bind(W).all<{ verified: number }>();
        expect(feeds).toHaveLength(1);
        expect(feeds[0].verified).toBe(1);

        const { results: chs } = await env.COMMENTS_DB.prepare(
            `SELECT number, label, date_upload FROM canonical_chapters
             WHERE work_id = ? ORDER BY seq`,
        ).bind(W).all<{ number: number; label: string; date_upload: number }>();
        expect(chs.map((c) => c.number)).toEqual([1, 2]);
        expect(chs[0].label).toContain('Day of Departure');
        expect(chs[0].date_upload).toBe(Date.parse('2020-01-01T00:00:00+00:00'));
    });

    it('reuses a stored binding without re-resolving', async () => {
        const W2 = 'mk:mb:reuse_test';
        await seedWork(W2, { title: 'Anything', year: 2000 });
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id)
             VALUES ('mangadex', 'md-stored', ?)`,
        ).bind(W2).run();
        routes.push({
            match: /\/manga\/md-stored\/feed/, body: mdFeed([{ id: 'x', chapter: '1' }]),
        });
        // Deliberately no /manga?title route — a resolve attempt would
        // hit realFetch and fail the assertion via error, not silently.
        const r = await ingestMangadexFeed(env as Env, W2);
        expect(r.mdId).toBe('md-stored');
        expect(r.chapters).toBe(1);
    });

    it('low-confidence matches flag review, never write ids', async () => {
        const W3 = 'mk:mb:amb_test';
        await seedWork(W3, { title: 'One Punch Man', year: 2099 });
        routes.push({
            match: /\/manga\?title=/,
            body: mdSearch([mdManga('md-wrong-year', { title: { en: 'One Punch Man' }, year: 1970 })]),
        });
        const r = await ingestMangadexFeed(env as Env, W3);
        expect(r.skipped).toBe('ambiguous');
        const { results: ids } = await env.COMMENTS_DB.prepare(
            `SELECT external_id FROM external_ids
             WHERE provider = 'mangadex' AND miko_work_id = ?`,
        ).bind(W3).all();
        expect(ids).toHaveLength(0);
        const { results: rev } = await env.COMMENTS_DB.prepare(
            `SELECT kind FROM identity_review
             WHERE kind = 'mangadex_resolve_ambiguous' AND payload LIKE ?`,
        ).bind(`%${W3}%`).all();
        expect(rev).toHaveLength(1);
    });

    it('no match tombstones the work', async () => {
        const W4 = 'mk:mb:nomd_test';
        await seedWork(W4, { title: 'Nonexistent Work ZZZ' });
        routes.push({ match: /\/manga\?title=/, body: mdSearch([]) });
        const r = await ingestMangadexFeed(env as Env, W4);
        expect(r.skipped).toBe('no_md_match');
        const { results: ids } = await env.COMMENTS_DB.prepare(
            `SELECT external_id FROM external_ids
             WHERE provider = 'mangadex' AND miko_work_id = ?`,
        ).bind(W4).all<{ external_id: string }>();
        expect(ids[0]?.external_id).toBe('__none__');
        // Second call short-circuits on the tombstone.
        const r2 = await ingestMangadexFeed(env as Env, W4);
        expect(r2.skipped).toBe('tombstoned');
    });

    it('empty EN feed keeps the binding, writes no chapters (licensed)', async () => {
        const W5 = 'mk:mb:opm_test';
        await seedWork(W5, {
            title: 'One-Punch Man', romanized: 'One Punch-Man',
            year: 2012, author: 'ONE',
        });
        routes.push(
            {
                match: /\/manga\?title=/,
                body: mdSearch([mdManga('md-opm', { title: { en: 'One Punch-Man' }, year: 2012 })]),
            },
            { match: /\/manga\/md-opm\/feed/, body: mdFeed([], 0) },
        );
        const r = await ingestMangadexFeed(env as Env, W5);
        expect(r.mdId).toBe('md-opm');
        expect(r.chapters).toBe(0);
        const { results: chs } = await env.COMMENTS_DB.prepare(
            'SELECT seq FROM canonical_chapters WHERE work_id = ?',
        ).bind(W5).all();
        expect(chs).toHaveLength(0);
    });

    it('cron fills backlog and refreshes stale feeds', async () => {
        const W6 = 'mk:mb:cron_stale';
        const W7 = 'mk:mb:cron_new';
        await seedWork(W6, { title: 'Stale', year: 2001 });
        await seedWork(W7, { title: 'Fresh Work', year: 2020, popularity: 1 });
        // Stale mangadex feed for W6.
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id)
             VALUES ('mangadex', 'md-stale', ?)`,
        ).bind(W6).run();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO canonical_feeds (work_id, source_key, feed_json, verified, received_at)
             VALUES (?, 'mangadex', '[]', 1, 1)`,
        ).bind(W6).run();

        routes.push(
            { match: /\/manga\/md-stale\/feed/, body: mdFeed([{ id: 's1', chapter: '7' }]) },
            {
                match: /\/manga\?title=/,
                body: mdSearch([mdManga('md-fresh', { title: { en: 'Fresh Work' }, year: 2020 })]),
            },
            { match: /\/manga\/md-fresh\/feed/, body: mdFeed([{ id: 'f1', chapter: '1' }]) },
        );

        await runMangadexCron(env as Env);

        const { results: a } = await env.COMMENTS_DB.prepare(
            'SELECT number FROM canonical_chapters WHERE work_id = ?',
        ).bind(W6).all<{ number: number }>();
        expect(a.map((x) => x.number)).toEqual([7]);
        const { results: b } = await env.COMMENTS_DB.prepare(
            'SELECT number FROM canonical_chapters WHERE work_id = ?',
        ).bind(W7).all<{ number: number }>();
        expect(b.map((x) => x.number)).toEqual([1]);
    });
});
