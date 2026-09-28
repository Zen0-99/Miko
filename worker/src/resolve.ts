import type { Env } from './env';
import type { MbSeries } from './mangabaka';
import { searchSeries } from './mangabaka';
import type { OlEdition, OlSearchDoc } from './openlibrary';
import {
    isValidIsbn,
    isbnToEdition,
    normalizeIsbn,
    searchWorks,
    workIdFromKey,
} from './openlibrary';
import { normalize } from './normalize';
import { err, MEDIUM_ALIASES, now, ok } from './util';

// Keep in sync with WorkIdResolver.allowedTypes + WorkIdentityApi
// ResolveRequest/WorkDto in the app (miko_work_id snake_case JSON).
// Phase 15 taxonomy: medium ∈ {shows, manga, books} is the carrier; the
// sets are the worker-assigned KINDS that satisfy each carrier ('oel'
// is a manga kind — original-English-language comics — never books).
const MEDIUM_TYPES: Record<string, Set<string>> = {
    shows: new Set(['anime', 'movie', 'series']),
    manga: new Set(['manga', 'manhwa', 'manhua', 'comic', 'webtoon', 'oel', 'other']),
    books: new Set(['book', 'novel']),
};

// Tie-break kind per carrier — the kind matching the request's original
// intent. Books requests historically asked for web novels, so 'novel'
// still wins ties over 'book' rows under the books carrier.
const PREFERRED_KIND: Record<string, string> = {
    shows: 'anime',
    manga: 'manga',
    books: 'novel',
};

interface ResolveRequest {
    provider?: string;
    external_id?: string;
    title: string;
    medium: string;
    author?: string;
    year?: number;
    /** Fetch disambiguator — advisory only, never stamped as the
     *  resolved kind (worker stays kind authority). 'book' gates the
     *  OpenLibrary fallthrough; 'movie'/'series' are inert until 15-02's
     *  Cinemeta path. Out-of-vocab values are ignored, not rejected. */
    kind_hint?: 'book' | 'movie' | 'series';
    /** DEPRECATED: pre-Phase-15 name of kind_hint — accepted for one
     *  deploy cycle, then dropped by the cleanup plan. */
    kind?: 'book';
    /** Strong external-ID signal for books (ISBN-10/13). */
    isbn?: string;
}

interface WorkRow {
    miko_work_id: string;
    type: string;
    medium: string | null;
    nsfw: number;
    content_rating: string | null;
    title: string;
    romanized_title: string | null;
    native_title: string | null;
    year: number | null;
    total_chapters: string | null;
    author: string | null;
    provider_links: string;
}

function toDto(row: WorkRow, externalIds: Record<string, string>) {
    return {
        miko_work_id: row.miko_work_id,
        // DEPRECATED: `type` still emitted during the one-cycle alias
        // window so old app builds parse; `kind` is the taxonomy name.
        type: row.type,
        kind: row.type,
        medium: row.medium,
        nsfw: row.nsfw === 1,
        content_rating: row.content_rating,
        title: row.title,
        romanized_title: row.romanized_title,
        native_title: row.native_title,
        year: row.year,
        total_chapters: row.total_chapters,
        author: row.author,
        provider_links: row.provider_links,
        external_ids: externalIds,
    };
}

async function externalIdsFor(env: Env, workId: string): Promise<Record<string, string>> {
    const { results } = await env.COMMENTS_DB.prepare(
        'SELECT provider, external_id FROM external_ids WHERE miko_work_id = ?',
    ).bind(workId).all<{ provider: string; external_id: string }>();
    return Object.fromEntries(results.map((r) => [r.provider, r.external_id]));
}

async function review(env: Env, kind: string, payload: unknown) {
    await env.COMMENTS_DB.prepare(
        'INSERT INTO identity_review (kind, payload, created_at) VALUES (?,?,?)',
    ).bind(kind, JSON.stringify(payload), now()).run();
}

const LINK_ID: Array<[RegExp, string]> = [
    [/title_no=(\d+)/i, 'webtoons'],
    [/titleId=(\d+)/i, 'naver'],
    [/royalroad\.com\/fiction\/(\d+)/i, 'royalroad'],
    [/novelupdates\.com\/series\/([\w-]+)/i, 'novelupdates'],
    [/mangadex\.org\/(?:title|manga)\/([0-9a-f-]{36})/i, 'mangadex'],
];

function linkEntries(series: MbSeries): Array<{ provider: string; external_id: string | null; url: string }> {
    return (series.links_v2 ?? []).map((l) => {
        let provider: string | null = null;
        let ext: string | null = null;
        for (const [re, name] of LINK_ID) {
            const m = re.exec(l.url);
            if (m) {
                provider = name;
                ext = m[1];
                break;
            }
        }
        return { provider: provider ?? l.name, external_id: ext, url: l.url };
    });
}

function candidateScore(
    s: MbSeries,
    req: ResolveRequest,
    normTitle: string,
): number {
    let score = 0;
    const titles = [
        s.title,
        s.romanized_title,
        s.native_title,
        ...Object.values(s.secondary_titles ?? {}).flat().map((t) => t.title),
        ...(s.titles ?? []).map((t) => t.title),
    ];
    if (titles.some((t) => normalize(t) === normTitle)) score += 2;
    if (req.provider && req.external_id) {
        const src = s.source?.[req.provider]?.id;
        if (src != null && String(src) === req.external_id) score += 3;
    }
    if (req.year != null && s.year === req.year) score += 1;
    return score;
}

async function persistSeries(env: Env, s: MbSeries, medium: string): Promise<WorkRow> {
    const workId = `mk:mb:${s.id}`;
    const title =
        s.title ?? s.romanized_title ?? s.native_title ?? `untitled-${s.id}`;
    const author = (s.authors ?? [])
        .map((a) => (typeof a === 'string' ? a : a.name))
        .filter(Boolean)
        .join(', ') || null;
    const links = linkEntries(s);
    const norm = normalize(title);

    // nsfw: only the explicit ratings flag. 'suggestive' stays SFW per the
    // locked default — the raw rating is persisted so it can be surfaced
    // as its own tag later without re-fetching (15-01 waiver).
    const contentRating = s.content_rating ?? null;
    const nsfw = contentRating === 'erotica' || contentRating === 'pornographic' ? 1 : 0;

    await env.COMMENTS_DB.prepare(
        `INSERT INTO works_cache
         (miko_work_id, type, title, romanized_title, native_title,
          normalized_title, year, total_chapters, author, provider_links,
          medium, nsfw, content_rating, fetched_at)
         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
         ON CONFLICT(miko_work_id) DO UPDATE SET
           fetched_at = excluded.fetched_at,
           medium = excluded.medium,
           nsfw = excluded.nsfw,
           content_rating = excluded.content_rating`,
    ).bind(
        workId, s.type, title, s.romanized_title, s.native_title,
        norm, s.year, s.total_chapters, author, JSON.stringify(links),
        medium, nsfw, contentRating, now(),
    ).run();

    const titleRows = new Map<string, string | null>();
    titleRows.set(title, null);
    for (const t of s.titles ?? []) if (t.title) titleRows.set(t.title, t.language ?? null);
    for (const [lang, entries] of Object.entries(s.secondary_titles ?? {})) {
        for (const e of entries) if (e.title) titleRows.set(e.title, lang);
    }
    for (const [t, lang] of titleRows) {
        await env.COMMENTS_DB.prepare(
            'INSERT INTO works_titles (miko_work_id, title, lang, normalized) VALUES (?,?,?,?)',
        ).bind(workId, t, lang, normalize(t)).run();
    }

    const ext = new Map<string, string>();
    for (const [provider, info] of Object.entries(s.source ?? {})) {
        if (info?.id != null) ext.set(provider, String(info.id));
    }
    for (const l of links) if (l.external_id) ext.set(l.provider, l.external_id);
    for (const [provider, id] of ext) {
        await env.COMMENTS_DB.prepare(
            'INSERT OR IGNORE INTO external_ids (provider, external_id, miko_work_id) VALUES (?,?,?)',
        ).bind(provider, id, workId).run();
    }

    return {
        miko_work_id: workId, type: s.type, title,
        romanized_title: s.romanized_title, native_title: s.native_title,
        year: s.year, total_chapters: s.total_chapters, author,
        provider_links: JSON.stringify(links),
        medium, nsfw, content_rating: contentRating,
    };
}

export async function handleResolve(req: Request, env: Env): Promise<Response> {
    let body: ResolveRequest;
    try {
        body = await req.json<ResolveRequest>();
    } catch {
        return err(400, 'bad_json');
    }
    // Alias window (one deploy cycle): legacy anime/novel normalize to
    // the canonical carrier; anything outside the vocab is rejected —
    // the old collapse-to-manga is gone (locked 15-CONTEXT ruling).
    const medium = MEDIUM_ALIASES[body.medium] ?? body.medium;
    const allowed = MEDIUM_TYPES[medium];
    if (!allowed) return err(400, 'bad_medium');
    // kind_hint (or its legacy `kind` name) is advisory — it picks the
    // upstream path, never the resolved kind.
    const kindHint = body.kind_hint ?? body.kind;
    const normTitle = normalize(body.title);
    if (!normTitle) return err(400, 'missing_title');

    // 1. External ID hit.
    if (body.provider && body.external_id) {
        const row = await env.COMMENTS_DB.prepare(
            `SELECT w.* FROM works_cache w
             JOIN external_ids e ON e.miko_work_id = w.miko_work_id
             WHERE e.provider = ? AND e.external_id = ?`,
        ).bind(body.provider, body.external_id).first<WorkRow>();
        if (row && allowed.has(row.type)) {
            return ok({ work: toDto(row, await externalIdsFor(env, row.miko_work_id)) });
        }
    }

    // 2. Normalized-title hit.
    const { results: titleHits } = await env.COMMENTS_DB.prepare(
        `SELECT w.* FROM works_cache w
         JOIN works_titles t ON t.miko_work_id = w.miko_work_id
         WHERE t.normalized = ? GROUP BY w.miko_work_id`,
    ).bind(normTitle).all<WorkRow>();
    const typed = titleHits.filter((r) => allowed.has(r.type));
    if (typed.length === 1) {
        return ok({ work: toDto(typed[0], await externalIdsFor(env, typed[0].miko_work_id)) });
    }
    if (typed.length > 1) {
        await review(env, 'divergence', { normTitle, medium, hits: typed.map((t) => t.miko_work_id) });
        return ok({ work: null });
    }

    // 3. MangaBaka fetch once → persist.
    let series: MbSeries[] = [];
    try {
        series = await searchSeries(body.title, 10, env.MANGABAKA_BASE);
    } catch {
        // MangaBaka down — books still have their own path.
        if (medium === 'books' && (kindHint === 'book' || body.isbn)) {
            return resolveBook(body, normTitle, env);
        }
        return ok({ work: null });
    }
    const candidates = series
        .filter((s) => s.state !== 'deleted' && s.merged_with == null)
        .map((s) => ({ s, score: candidateScore(s, body, normTitle) }))
        .filter((c) => c.score >= 2 && allowed.has(c.s.type))
        .sort((a, b) => b.score - a.score);

    if (candidates.length === 0) {
        // Books only — gated so webnovels that miss MangaBaka never hit OL.
        if (medium === 'books' && (kindHint === 'book' || body.isbn)) {
            return resolveBook(body, normTitle, env);
        }
        return ok({ work: null });
    }
    // Exact-type match wins a tie before ambiguity is declared — e.g.
    // novel "Shadow Slave" beats the OEL webcomic for a books request.
    let best = candidates[0];
    const tied = candidates.filter((c) => c.score === best.score);
    if (tied.length > 1) {
        const exact = tied.filter((c) => c.s.type === PREFERRED_KIND[medium]);
        if (exact.length === 1) {
            best = exact[0];
        } else {
            await review(env, 'collision', {
                normTitle, medium,
                candidates: candidates.slice(0, 5).map((c) => ({ id: c.s.id, score: c.score })),
            });
            return ok({ work: null });
        }
    }
    if (body.provider && body.external_id) {
        const verified = String(best.s.source?.[body.provider]?.id ?? '') === body.external_id;
        if (!verified) {
            await review(env, 'provider_mismatch', {
                provider: body.provider, external_id: body.external_id,
                matched: best.s.id, title: body.title,
            });
            // Still return the title-matched work — the external_id just
            // isn't trusted as a binding for it.
        }
    }
    const row = await persistSeries(env, best.s, medium);
    return ok({ work: toDto(row, await externalIdsFor(env, row.miko_work_id)) });
}

// ---------- books (OpenLibrary) ----------

/** Persist an OL work as a `book` row + title + external IDs. */
async function persistBook(
    env: Env,
    olWorkId: string,
    title: string,
    author: string | null,
    year: number | null,
    isbns: string[],
): Promise<WorkRow> {
    const workId = `mk:ol:${olWorkId}`;
    const norm = normalize(title);

    await env.COMMENTS_DB.prepare(
        `INSERT INTO works_cache
         (miko_work_id, type, title, romanized_title, native_title,
          normalized_title, year, total_chapters, author, provider_links,
          medium, nsfw, content_rating, fetched_at)
         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
         ON CONFLICT(miko_work_id) DO UPDATE SET fetched_at = excluded.fetched_at`,
    ).bind(
        workId, 'book', title, null, null, norm, year, null, author, '[]',
        'books', 0, null, now(),
    ).run();

    await env.COMMENTS_DB.prepare(
        'INSERT OR IGNORE INTO works_titles (miko_work_id, title, lang, normalized) VALUES (?,?,?,?)',
    ).bind(workId, title, null, norm).run();

    const ext = new Map<string, string>();
    ext.set('openlibrary', olWorkId);
    for (const isbn of isbns.map(normalizeIsbn).filter(isValidIsbn)) {
        ext.set('isbn', isbn); // last one wins — all resolve to the same work
        await env.COMMENTS_DB.prepare(
            'INSERT OR IGNORE INTO external_ids (provider, external_id, miko_work_id) VALUES (?,?,?)',
        ).bind('isbn', isbn, workId).run();
    }
    await env.COMMENTS_DB.prepare(
        'INSERT OR IGNORE INTO external_ids (provider, external_id, miko_work_id) VALUES (?,?,?)',
    ).bind('openlibrary', olWorkId, workId).run();

    return {
        miko_work_id: workId, type: 'book', title,
        romanized_title: null, native_title: null,
        year, total_chapters: null, author, provider_links: '[]',
        medium: 'books', nsfw: 0, content_rating: null,
    };
}

/** Score an OL search doc: title match dominates, author/year refine. */
function bookCandidateScore(d: OlSearchDoc, req: ResolveRequest, normTitle: string): number {
    let score = 0;
    if (d.title && normalize(d.title) === normTitle) score += 2;
    if (req.author && d.author_name?.some((a) => normalize(a) === normalize(req.author!))) score += 1;
    if (req.year != null && d.first_publish_year === req.year) score += 1;
    return score;
}

/**
 * OpenLibrary fallthrough for book requests (`kind='book'` or ISBN present).
 * ISBN path is exact; title+author search is fuzzy. Edition-only results
 * (no works[] rollup) don't mint — logged to identity_review instead.
 */
async function resolveBook(req: ResolveRequest, normTitle: string, env: Env): Promise<Response> {
    const base = env.OPENLIBRARY_BASE;

    // 4a. ISBN path — edition → work rollup.
    if (req.isbn) {
        const isbn = normalizeIsbn(req.isbn);
        const edition = isValidIsbn(isbn) ? await isbnToEdition(isbn, base) : null;
        if (edition) {
            const workKey = workIdFromKey(edition.works?.[0]?.key);
            if (workKey) {
                const row = await persistBook(
                    env, workKey,
                    edition.title ?? req.title, req.author ?? null,
                    null, [isbn, ...(edition.isbn_13 ?? []), ...(edition.isbn_10 ?? [])],
                );
                return ok({ work: toDto(row, await externalIdsFor(env, row.miko_work_id)) });
            }
            await review(env, 'edition_no_work', { isbn, editionKey: edition.key });
        }
    }

    // 4b. Title+author search.
    let docs: OlSearchDoc[] = [];
    try {
        docs = await searchWorks(req.title, req.author, 10, base);
    } catch {
        return ok({ work: null });
    }
    const candidates = docs
        .map((d) => ({ d, score: bookCandidateScore(d, req, normTitle) }))
        .filter((c) => c.score >= 2)
        .sort((a, b) => b.score - a.score);
    if (candidates.length === 0) return ok({ work: null });

    const best = candidates[0];
    const tied = candidates.filter((c) => c.score === best.score);
    if (tied.length > 1) {
        await review(env, 'collision', {
            normTitle, medium: 'books',
            candidates: candidates.slice(0, 5).map((c) => ({ key: c.d.key, score: c.score })),
        });
        return ok({ work: null });
    }
    const workKey = workIdFromKey(best.d.key);
    if (!workKey) {
        await review(env, 'edition_no_work', { key: best.d.key, title: req.title });
        return ok({ work: null });
    }
    const row = await persistBook(
        env, workKey, best.d.title ?? req.title,
        best.d.author_name?.[0] ?? req.author ?? null,
        best.d.first_publish_year ?? req.year ?? null,
        best.d.isbn ?? [],
    );
    return ok({ work: toDto(row, await externalIdsFor(env, row.miko_work_id)) });
}
