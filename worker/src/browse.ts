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
const SORTS: Record<string, string> = {
    rating: 'rating IS NULL, rating DESC',
    popularity: 'popularity IS NULL, popularity ASC',
    newest: 'year IS NULL, year DESC',
};

const DEFAULT_SORT = 'popularity';
const DEFAULT_LIMIT = 40;
const MAX_LIMIT = 100;
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
    if (!includeNsfw) where += ' AND nsfw = 0';
    if (kinds.length) {
        where += ` AND type IN (${kinds.map(() => '?').join(',')})`;
        binds.push(...kinds);
    }

    // LIMIT+1 probe — one extra row answers has_more without a COUNT(*).
    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT miko_work_id, type, title, author, cover_url, rating, popularity, year, nsfw, genres
         FROM works_cache
         WHERE ${where}
         ORDER BY ${orderBy}, miko_work_id
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
