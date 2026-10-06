import { remergeChapters } from './chaptermap';
import type { Env } from './env';
import { fetchEnglishFeed, resolveMangaId } from './mangadex';
import { now } from './util';

const TOMBSTONE = '__none__';
const STALE_AFTER_MS = 12 * 3600_000;
const STALE_SLICE = 40;
const BACKLOG_SLICE = 40;

async function flagAmbiguous(env: Env, workId: string, mdId: string) {
    const payload = JSON.stringify({ work_id: workId, candidate: mdId });
    const { results } = await env.COMMENTS_DB.prepare(
        'SELECT 1 AS x FROM identity_review WHERE kind = ? AND payload = ? LIMIT 1',
    ).bind('mangadex_resolve_ambiguous', payload).all();
    if (results.length === 0) {
        await env.COMMENTS_DB.prepare(
            'INSERT INTO identity_review (kind, payload, created_at) VALUES (?,?,?)',
        ).bind('mangadex_resolve_ambiguous', payload, now()).run();
    }
}

/**
 * Ensure a work has a stored MangaDex feed: reuse the stored
 * external_ids binding or resolve a fresh id (title + year/author
 * corroboration), fetch the EN feed, upsert canonical_feeds, remerge.
 *
 * - resolve miss → `__none__` tombstone so the cron doesn't retry
 *   the same title every tick.
 * - low-confidence match → identity_review, nothing written (a wrong
 *   id is worse than no id).
 * - empty EN feed (licensed titles — OPM) → the id is still stored;
 *   coverage is honest about the gap.
 */
export async function ingestMangadexFeed(
    env: Env,
    workId: string,
): Promise<{ mdId: string | null; chapters: number; skipped: string | null }> {
    const { results: existing } = await env.COMMENTS_DB.prepare(
        // Only a CONFIRMED binding steers the managed crawler — a
        // provisional device claim (Phase 20) must not pick which
        // MangaDex feed becomes verified canon. Unbound → title search.
        `SELECT external_id FROM external_ids
         WHERE provider = 'mangadex' AND miko_work_id = ?
           AND status = 'confirmed' LIMIT 1`,
    ).bind(workId).all<{ external_id: string }>();
    let mdId = existing[0]?.external_id ?? null;
    if (mdId === TOMBSTONE) return { mdId: null, chapters: 0, skipped: 'tombstoned' };

    if (!mdId) {
        const { results: rows } = await env.COMMENTS_DB.prepare(
            `SELECT title, romanized_title, native_title, year, author
             FROM works_cache WHERE miko_work_id = ? LIMIT 1`,
        ).bind(workId).all<{
            title: string; romanized_title: string | null;
            native_title: string | null; year: number | null;
            author: string | null;
        }>();
        const w = rows[0];
        if (!w) return { mdId: null, chapters: 0, skipped: 'not_in_catalog' };

        const hit = await resolveMangaId({
            title: w.title,
            romanizedTitle: w.romanized_title,
            nativeTitle: w.native_title,
            year: w.year,
            author: w.author,
        }, env.MANGADEX_BASE);
        if (!hit) {
            await env.COMMENTS_DB.prepare(
                `INSERT OR IGNORE INTO external_ids (provider, external_id, miko_work_id, provenance, last_seen_at)
                 VALUES ('mangadex', ?, ?, 'mangadex_seed', ?)`,
            ).bind(TOMBSTONE, workId, now()).run();
            return { mdId: null, chapters: 0, skipped: 'no_md_match' };
        }
        if (hit.confidence !== 'high') {
            await flagAmbiguous(env, workId, hit.id);
            return { mdId: null, chapters: 0, skipped: 'ambiguous' };
        }
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids
             (provider, external_id, miko_work_id, provenance, last_seen_at)
             VALUES ('mangadex', ?, ?, 'mangadex_seed', ?)
             ON CONFLICT(provider, external_id) DO UPDATE SET
                last_seen_at = excluded.last_seen_at`,
        ).bind(hit.id, workId, now()).run();
        mdId = hit.id;
    }

    const chapters = await fetchEnglishFeed(mdId, env.MANGADEX_BASE);
    if (chapters.length === 0) {
        return { mdId, chapters: 0, skipped: 'empty_feed' };
    }
    await env.COMMENTS_DB.prepare(
        `REPLACE INTO canonical_feeds
         (work_id, source_key, feed_json, verified, score, received_at)
         VALUES (?, 'mangadex', ?, 1, NULL, ?)`,
    ).bind(workId, JSON.stringify(chapters), now()).run();
    const summary = await remergeChapters(env, workId);
    return { mdId, chapters: summary.count, skipped: null };
}

/**
 * Cron body ([triggers] in wrangler.toml): refresh stale mangadex feeds
 * first, then spend the remainder resolving coverage backlog — manga
 * works with no mangadex external_id at all (tombstones count as
 * resolved). Slices stay small: MangaDex rate-limits per IP and Worker
 * egress shares CF's pool.
 */
export async function runMangadexCron(env: Env): Promise<void> {
    const cutoff = now() - STALE_AFTER_MS;
    const { results: stale } = await env.COMMENTS_DB.prepare(
        `SELECT work_id FROM canonical_feeds
         WHERE source_key = 'mangadex' AND received_at < ?
         ORDER BY received_at ASC LIMIT ?`,
    ).bind(cutoff, STALE_SLICE).all<{ work_id: string }>();
    for (const r of stale) {
        try {
            await ingestMangadexFeed(env, r.work_id);
        } catch (e) {
            console.error('mangadex refresh failed', r.work_id, e);
        }
    }

    const { results: backlog } = await env.COMMENTS_DB.prepare(
        `SELECT miko_work_id FROM works_cache
         WHERE medium = 'manga'
           AND miko_work_id NOT IN (
               SELECT miko_work_id FROM external_ids WHERE provider = 'mangadex'
           )
         ORDER BY popularity IS NULL, popularity ASC LIMIT ?`,
    ).bind(BACKLOG_SLICE).all<{ miko_work_id: string }>();
    for (const r of backlog) {
        try {
            await ingestMangadexFeed(env, r.miko_work_id);
        } catch (e) {
            console.error('mangadex resolve failed', r.miko_work_id, e);
        }
    }
}
