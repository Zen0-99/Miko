import type { Env } from './env';
import {
    getSeries,
    getSeriesImages,
    getSeriesWorks,
    type MbImage,
    type MbWork,
} from './mangabaka';
import { now } from './util';

/**
 * Volume covers/meta seeder (Phase 21-01 / NCM-016).
 *
 * Plain-HTTPS MangaBaka lane — no browser needed (ComicK boundaries are
 * a separate, local-only harvester that pushes via the seed endpoint).
 * Iterates the popularity-ranked mk:mb:* works_cache that haven't been
 * volume-seeded, fetches series + images + works, upserts work_volumes
 * (front covers only — `type='volume'`; backs and other image kinds
 * share the same (volume, language) key and don't serve the UI), and
 * backfills works_cache.final_volume / covers_count.
 *
 * Trigger: POST /v1/volumes/seed-run behind the crawler-key gate —
 * same manual-lane auth as /v1/charts/webtoon/refresh. Rate-gentle:
 * MB's 180/min shared bucket tolerates ~3 calls per work at a modest
 * pace; a small sleep keeps bursts honest.
 */

const MB_ID = /^mk:mb:(\d+)$/;

interface WorkEdition {
    isbn: string | null;
    releaseDate: string | null;
    pages: number | null;
}

/** First main-type edition per volume — en-language work rows win so
 *  the ISBN/date reflect the English release a reader is likeliest to
 *  recognize; any edition beats none. */
function editionsByVolume(works: MbWork[]): Map<number, WorkEdition> {
    const map = new Map<number, WorkEdition>();
    const ranked = works
        .filter((w) => w.count_type === 'main' && w.sequence_numeric != null)
        .sort((a, b) => {
            const aEn = (a.images ?? []).some((i) => i.language === 'en') ? 0 : 1;
            const bEn = (b.images ?? []).some((i) => i.language === 'en') ? 0 : 1;
            return aEn - bEn;
        });
    for (const w of ranked) {
        const vol = w.sequence_numeric!;
        if (map.has(vol)) continue;
        map.set(vol, {
            isbn: w.identifiers?.find((i) => i.name === 'isbn')?.id ?? null,
            releaseDate: w.release_date,
            pages: w.pages,
        });
    }
    return map;
}

export async function runVolumeSeed(env: Env, limit = 500): Promise<{
    scanned: number;
    works_seeded: number;
    volumes_written: number;
    skipped: string[];
}> {
    // Scan set = volume_seed_queue ∪ top-N unseeded by popularity.
    // "Unseeded" = neither stamp set: final_volume is legitimately null
    // forever on ongoing series (One Piece has no last tankōbon), so
    // covers_count is the convergence marker — a fetched work always
    // gets covers_count = gallery size (≥1 for any real series).
    const { results: works } = await env.COMMENTS_DB.prepare(
        `SELECT DISTINCT miko_work_id FROM works_cache
         WHERE miko_work_id LIKE 'mk:mb:%' AND (
             miko_work_id IN (SELECT work_id FROM volume_seed_queue)
             OR miko_work_id IN (
                 SELECT miko_work_id FROM works_cache
                 WHERE miko_work_id LIKE 'mk:mb:%'
                   AND final_volume IS NULL AND covers_count IS NULL
                 ORDER BY popularity ASC LIMIT ?
             )
         )`,
    ).bind(limit).all<{ miko_work_id: string }>();

    let seeded = 0;
    let written = 0;
    const skipped: string[] = [];
    const t = now();

    for (const { miko_work_id: workId } of works ?? []) {
        const mbId = workId.match(MB_ID)?.[1];
        if (!mbId) continue;
        try {
            const series = await getSeries(Number(mbId));
            const finalVolume = Number(series?.final_volume ?? 0) || null;

            const images = await getSeriesImages(Number(mbId));
            // MB has no covers_count field — the site count is just the
            // image gallery size, which we already fetched. Zero is a
            // real stamp (fetched, gallery empty) — null stays reserved
            // for never-attempted so the scan set converges.
            const coversCount = images.length;
            const fronts = images.filter((i) => i.type === 'volume' && i.index_numeric != null);

            const editions = editionsByVolume(await getSeriesWorks(Number(mbId)));

            const stmts: D1PreparedStatement[] = [];
            for (const img of fronts) {
                const vol = img.index_numeric!;
                const lang = img.language ?? 'unknown';
                const ed = editions.get(vol);
                stmts.push(env.COMMENTS_DB.prepare(
                    `INSERT OR REPLACE INTO work_volumes
                     (work_id, volume_no, language, cover_url, cover_thumb_url,
                      cover_blurhash, isbn, release_date, pages, source, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'mangabaka', ?)`,
                ).bind(
                    workId, vol, lang,
                    img.image?.raw?.url ?? null,
                    img.image?.x350?.x1 ?? img.image?.raw?.url ?? null,
                    img.image?.raw?.blurhash ?? null,
                    ed?.isbn ?? null, ed?.releaseDate ?? null, ed?.pages ?? null,
                    t,
                ));
            }
            stmts.push(env.COMMENTS_DB.prepare(
                `UPDATE works_cache
                 SET final_volume = COALESCE(?, final_volume),
                     covers_count = COALESCE(?, covers_count)
                 WHERE miko_work_id = ?`,
            ).bind(finalVolume, coversCount, workId));
            // Seeded works leave the queue.
            stmts.push(env.COMMENTS_DB.prepare(
                'DELETE FROM volume_seed_queue WHERE work_id = ?',
            ).bind(workId));
            await env.COMMENTS_DB.batch(stmts);

            seeded += 1;
            written += stmts.length - 2;
            // per-request pacing lives in mangabaka.getJson — no extra
            // sleep needed here.
        } catch (e) {
            skipped.push(`${workId}: ${e instanceof Error ? e.message : String(e)}`);
        }
    }
    return { scanned: (works ?? []).length, works_seeded: seeded, volumes_written: written, skipped };
}

export const __testables = { editionsByVolume };
