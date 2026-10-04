const DEFAULT_BASE = 'https://api.mangadex.org';
const UA = 'miko-comments/1.0 (+https://github.com/Zen0-99/Miko)';

interface MdSearchResponse {
    data?: Array<{ id: string }>;
}

interface MdAggregate {
    volumes?: Record<string, { chapters?: Record<string, { chapter?: string }> }>;
}

interface MdManga {
    id: string;
    attributes: {
        title?: Record<string, string>;
        altTitles?: Array<Record<string, string>>;
        year?: number | null;
    };
    relationships?: Array<{
        type?: string;
        attributes?: { name?: string };
    }>;
}

interface MdMangaSearch {
    data?: MdManga[];
}

interface MdFeedChapter {
    id: string;
    attributes: {
        chapter?: string | null;
        title?: string | null;
        volume?: string | null;
        publishAt?: string | null;
        externalUrl?: string | null;
    };
}

interface MdFeedResponse {
    result?: string;
    total?: number;
    data?: MdFeedChapter[];
}

async function mdFetch<T>(path: string, base?: string): Promise<T> {
    const res = await fetch(`${base ?? DEFAULT_BASE}${path}`, {
        headers: { 'user-agent': UA, accept: 'application/json' },
    });
    if (!res.ok) throw new Error(`mangadex ${res.status}`);
    return res.json<T>();
}

/**
 * Reference chapter-number set for a manga title via MangaDex:
 * series search (first hit — the caller already resolved the work, this
 * is only a numbering reference) → chapter aggregate across scanlation
 * groups, which dedupes per chapter number. Returns null when MangaDex
 * has no match or errors.
 */
export async function referenceChapters(
    title: string,
    base?: string,
): Promise<Set<number> | null> {
    try {
        const search = await mdFetch<MdSearchResponse>(
            `/manga?title=${encodeURIComponent(title)}&limit=1&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica`,
            base,
        );
        const id = search.data?.[0]?.id;
        if (!id) return null;

        const agg = await mdFetch<MdAggregate>(
            `/manga/${id}/aggregate?translatedLanguage[]=en`,
            base,
        );
        const numbers = new Set<number>();
        for (const vol of Object.values(agg.volumes ?? {})) {
            for (const ch of Object.values(vol.chapters ?? {})) {
                const n = parseFloat(ch.chapter ?? '');
                if (Number.isFinite(n)) numbers.add(n);
            }
        }
        return numbers.size > 0 ? numbers : null;
    } catch {
        return null;
    }
}

import { normalize } from './normalize';

export interface MdResolveInput {
    title: string;
    romanizedTitle?: string | null;
    nativeTitle?: string | null;
    year?: number | null;
    author?: string | null;
}

export type MdConfidence = 'high' | 'low';

/**
 * Resolve a work to its MangaDex manga id. Normalized-title equality
 * against title + altTitles (any locale), corroborated by year ±1 or
 * author/artist containment → 'high'. A normalized match without
 * corroboration returns 'low' — the caller decides whether to trust it
 * (seed writes only 'high'; low goes to identity_review).
 */
export async function resolveMangaId(
    work: MdResolveInput,
    base?: string,
): Promise<{ id: string; confidence: MdConfidence } | null> {
    const titles = [work.title, work.romanizedTitle, work.nativeTitle]
        .map(normalize)
        .filter((t) => t.length > 0);
    if (!titles.length) return null;
    try {
        const search = await mdFetch<MdMangaSearch>(
            `/manga?title=${encodeURIComponent(work.title)}&limit=10` +
            '&contentRating[]=safe&contentRating[]=suggestive' +
            '&contentRating[]=erotica&contentRating[]=pornographic' +
            '&includes[]=author&includes[]=artist',
            base,
        );
        for (const m of search.data ?? []) {
            const a = m.attributes;
            const cands = [
                ...Object.values(a.title ?? {}),
                ...(a.altTitles ?? []).flatMap((t) => Object.values(t)),
            ].map(normalize);
            if (!cands.some((c) => titles.includes(c))) continue;

            const author = normalize(work.author ?? '');
            const names = (m.relationships ?? [])
                .filter((r) => r.type === 'author' || r.type === 'artist')
                .map((r) => normalize(r.attributes?.name))
                .filter((n) => n.length > 0);
            const authorHit = !!author &&
                names.some((n) => n === author || n.includes(author) || author.includes(n));
            const yearHit = !!a.year && !!work.year && Math.abs(a.year - work.year) <= 1;
            return { id: m.id, confidence: authorHit || yearHit ? 'high' : 'low' };
        }
        return null;
    } catch {
        return null;
    }
}

/**
 * Full English chapter feed for a MangaDex manga — structured
 * number/volume fields plus name/url/date_upload, matching the
 * canonical_feeds storage shape. Unnumbered entries carry kind='extra'.
 */
export async function fetchEnglishFeed(
    mdId: string,
    base?: string,
): Promise<Array<Record<string, unknown>>> {
    const out: Array<Record<string, unknown>> = [];
    let offset = 0;
    for (let page = 0; page < 8; page++) { // 8×500 = 4000 chapter ceiling
        const d = await mdFetch<MdFeedResponse>(
            `/manga/${mdId}/feed?translatedLanguage[]=en&order[chapter]=asc` +
            `&limit=500&offset=${offset}&includeEmptyPages=0` +
            '&includeFutureUpdates=0&includeUnavailable=0',
            base,
        );
        const data = d.data ?? [];
        for (const ch of data) {
            const a = ch.attributes;
            const num = a.chapter != null ? parseFloat(a.chapter) : NaN;
            const number = Number.isFinite(num) ? num : null;
            const vol = a.volume != null ? parseFloat(a.volume) : NaN;
            const volume = Number.isFinite(vol) ? vol : null;
            const title = (a.title ?? '').trim();
            out.push({
                name: number !== null
                    ? `Chapter ${a.chapter}${title ? ` — ${title}` : ''}`
                    : (title || 'Extra'),
                url: a.externalUrl ?? `https://mangadex.org/chapter/${ch.id}`,
                date_upload: a.publishAt ? Date.parse(a.publishAt) || null : null,
                number,
                volume,
                part: null,
                kind: number === null ? 'extra' : 'chapter',
            });
        }
        offset += data.length;
        if (offset >= (d.total ?? offset) || data.length === 0) break;
    }
    return out;
}
