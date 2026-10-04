import type { Env } from './env';
import { err, ok } from './util';

// Phase 15 catalog browse — ranked slices of works_cache for the app's
// Discovery tab. Same carrier vocab as resolve.ts (canonical only, no
// aliases); `type` is the kind column internally.
const MEDIUMS = new Set(['shows', 'manga', 'books']);

// SQLite orders NULLs FIRST on DESC and FIRST on ASC — the `x IS NULL`
// leading key (0 for valued rows, 1 for NULLs) forces NULLs last either
// way. miko_work_id is the stable tiebreaker so pagination is
// deterministic when rating/popularity/year tie.
//
// `popularity` leads with the platform chart (works_charts — e.g. the
// webtoons.com Popular board) when the row carries one: chart members
// surface in platform order, the rest of the carrier keeps its
// MangaBaka-aggregate order below. Join is a no-op for carriers/kinds
// with no chart rows.
const SORTS: Record<string, string> = {
    rating: 'rating IS NULL, rating DESC',
    popularity: 'chart_rank IS NULL, chart_rank ASC, popularity IS NULL, popularity ASC',
    newest: 'year IS NULL, year DESC',
};

const DEFAULT_SORT = 'popularity';
const DEFAULT_LIMIT = 40;
const MAX_LIMIT = 100;
// Kinds that can carry a webtoon_popular chart slot — the platform's
// own vocabulary. Anything outside (manga, comic) never legitimately
// appears on webtoons.com.
const WEBTOON_FORMAT_KINDS = new Set(['manhwa', 'manhua', 'webtoon', 'oel', 'other']);
// Generous cap — the full kind vocab is ~12, so this only guards
// against pathological query strings hitting the bind limit.
const MAX_KINDS = 50;

// works_cache.genres is a JSON array string ('["Action","Romance"]') —
// pass it through as a parsed array; malformed rows yield [].
function parseGenres(raw: string | null): string[] {
    if (!raw) return [];
    try {
        const v: unknown = JSON.parse(raw);
        return Array.isArray(v) ? v.filter((g): g is string => typeof g === 'string') : [];
    } catch {
        return [];
    }
}

interface BrowseRow {
    miko_work_id: string;
    type: string | null;
    title: string;
    author: string | null;
    cover_url: string | null;
    rating: number | null;
    popularity: number | null;
    year: number | null;
    nsfw: number;
    genres: string | null;
}

export async function handleBrowse(url: URL, env: Env): Promise<Response> {
    const medium = url.searchParams.get('medium') ?? '';
    if (!MEDIUMS.has(medium)) return err(400, 'bad_medium');

    const sort = url.searchParams.get('sort') ?? DEFAULT_SORT;
    const orderBy = SORTS[sort];
    if (!orderBy) return err(400, 'bad_sort');

    // Empty/absent kind → every kind under the carrier.
    const kinds = (url.searchParams.get('kind') ?? '')
        .split(',')
        .map((k) => k.trim())
        .filter(Boolean)
        .slice(0, MAX_KINDS);

    const page = Math.max(1, Math.floor(Number(url.searchParams.get('page'))) || 1);
    const limit = Math.min(
        MAX_LIMIT,
        Math.max(1, Math.floor(Number(url.searchParams.get('limit'))) || DEFAULT_LIMIT),
    );

    // NSFW rows are excluded unless the caller opts in — Discovery is a
    // ranked surface, so adult rows must be seeked out via the app's
    // NSFW-sources preference, not surfaced by default.
    const includeNsfw = url.searchParams.get('nsfw') === '1';

    const binds: Array<string | number> = [medium];
    let where = 'medium = ?';
    // Merged catalog rows stay as aliases (resolve/chapters still
    // redirect) but Discovery shows only the live work.
    where += ' AND works_cache.miko_work_id NOT IN (SELECT dead_id FROM work_merges)';
    if (!includeNsfw) where += ' AND nsfw = 0';
    if (kinds.length) {
        where += ` AND type IN (${kinds.map(() => '?').join(',')})`;
        binds.push(...kinds);
    }

    // The webtoon_popular chart only orders slices that can contain
    // platform-format rows — a pure `kind=manga` query (the Manga pill)
    // would otherwise let a stray mis-typed chart row outrank the whole
    // MangaBaka listing. Unfiltered manga keeps the chart: it's the
    // only platform chart this carrier has.
    const chartApplies = medium === 'manga' &&
        (kinds.length === 0 || kinds.some((k) => WEBTOON_FORMAT_KINDS.has(k)));
    const chartJoin = chartApplies
        ? `LEFT JOIN works_charts wc
                ON wc.miko_work_id = works_cache.miko_work_id
               AND wc.chart = 'webtoon_popular'`
        : '';

    // LIMIT+1 probe — one extra row answers has_more without a COUNT(*).
    // The chart join only matters under sort=popularity (see SORTS) but
    // is harmless elsewhere: LEFT JOIN yields NULL rank, ordering the
    // other sorts exactly as before.
    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT works_cache.miko_work_id, type, title, author, cover_url, rating, popularity,
                year, nsfw, genres, ${chartApplies ? 'wc.rank' : 'NULL'} AS chart_rank
         FROM works_cache
         ${chartJoin}
         WHERE ${where}
         ORDER BY ${orderBy}, works_cache.miko_work_id
         LIMIT ? OFFSET ?`,
    )
        .bind(...binds, limit + 1, (page - 1) * limit)
        .all<BrowseRow>();

    const works = results.slice(0, limit).map((r) => ({
        miko_work_id: r.miko_work_id,
        kind: r.type,
        title: r.title,
        author: r.author,
        cover_url: r.cover_url,
        rating: r.rating,
        popularity: r.popularity,
        year: r.year,
        nsfw: r.nsfw === 1,
        genres: parseGenres(r.genres),
    }));
    return ok({ works, page, has_more: results.length > limit });
}

interface HydrateRow {
    miko_work_id: string;
    normalized_title: string | null;
    author: string | null;
    cover_url: string | null;
    year: number | null;
    total_chapters: string | null;
    rating: number | null;
    genres: string | null;
}

// Batch display-field hydration for catalog search results. The
// offline map artifact carries no covers/authors at all, so the app
// dedupes locally then asks for these fields per work id. A row
// missing its own cover borrows the best-ranked same-title sibling's —
// MangaBaka keeps several sparse rows per title and the art rarely
// sits on the search winner.
export async function handleHydrate(url: URL, env: Env): Promise<Response> {
    const ids = (url.searchParams.get('ids') ?? '')
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)
        .slice(0, 100);
    if (!ids.length) return ok({ works: [] });

    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT miko_work_id, normalized_title, author, cover_url, year,
                total_chapters, rating, genres
         FROM works_cache
         WHERE miko_work_id IN (${ids.map(() => '?').join(',')})`,
    )
        .bind(...ids)
        .all<HydrateRow>();

    const missingTitles = [
        ...new Set(
            results
                .filter((r) => !r.cover_url && r.normalized_title)
                .map((r) => r.normalized_title as string),
        ),
    ].slice(0, 100);
    const siblingCover = new Map<string, string>();
    if (missingTitles.length) {
        const { results: sibs } = await env.COMMENTS_DB.prepare(
            `SELECT normalized_title, cover_url FROM works_cache
             WHERE normalized_title IN (${missingTitles.map(() => '?').join(',')})
               AND cover_url IS NOT NULL AND cover_url != ''
             ORDER BY popularity IS NULL, popularity ASC`,
        )
            .bind(...missingTitles)
            .all<{ normalized_title: string; cover_url: string }>();
        for (const s of sibs) {
            if (!siblingCover.has(s.normalized_title)) {
                siblingCover.set(s.normalized_title, s.cover_url);
            }
        }
    }

    const works = results.map((r) => ({
        miko_work_id: r.miko_work_id,
        author: r.author,
        cover_url: r.cover_url ??
            (r.normalized_title ? siblingCover.get(r.normalized_title) ?? null : null),
        year: r.year,
        total_chapters: r.total_chapters,
        rating: r.rating,
        genres: parseGenres(r.genres),
    }));
    return ok({ works });
}
