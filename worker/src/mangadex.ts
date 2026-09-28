const DEFAULT_BASE = 'https://api.mangadex.org';
const UA = 'miko-comments/1.0 (+https://github.com/Zen0-99/Miko)';

interface MdSearchResponse {
    data?: Array<{ id: string }>;
}

interface MdAggregate {
    volumes?: Record<string, { chapters?: Record<string, { chapter?: string }> }>;
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
