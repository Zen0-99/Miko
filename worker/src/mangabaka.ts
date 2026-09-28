// MangaBaka API client — single fetch for the lazy mirror.
// API docs: https://mangabaka.org/data/api

export interface MbTitle {
    language?: string;
    title: string;
    is_primary?: boolean;
}

export interface MbSeries {
    id: number;
    title: string | null;
    romanized_title: string | null;
    native_title: string | null;
    secondary_titles: Record<string, Array<{ title: string }>> | null;
    titles?: MbTitle[];
    type: string;
    year: number | null;
    total_chapters: string | null;
    authors: Array<{ name: string } | string> | null;
    links_v2: Array<{ url: string; name: string; type?: string }> | null;
    source: Record<string, { id: string | number | null } | undefined> | null;
    /** Upstream content rating — 'erotica'/'pornographic' flag nsfw;
     *  'suggestive' stays SFW per the locked Phase 15 default. */
    content_rating?: string | null;
    state?: string;
    merged_with?: number | null;
}

const UA = 'Miko/2.0 (github.com/Zen0-99/Miko)';

export const MANGABAKA_BASE = 'https://api.mangabaka.org';

async function getJson(base: string, path: string): Promise<unknown> {
    const res = await fetch(`${base}${path}`, {
        headers: { 'User-Agent': UA, Accept: 'application/json' },
        signal: AbortSignal.timeout(5_000),
    });
    if (!res.ok) throw new Error(`mangabaka ${res.status}`);
    return res.json();
}

export async function searchSeries(
    title: string,
    limit = 10,
    base = MANGABAKA_BASE,
): Promise<MbSeries[]> {
    const q = encodeURIComponent(title);
    const body = (await getJson(base, `/v1/series/search?q=${q}&limit=${limit}`)) as {
        data?: MbSeries[];
    };
    return Array.isArray(body?.data) ? body.data : [];
}

export async function getSeries(id: number, base = MANGABAKA_BASE): Promise<MbSeries | null> {
    const body = (await getJson(base, `/v1/series/${id}`)) as MbSeries | { data?: MbSeries };
    if (body && typeof body === 'object' && 'data' in body && body.data) return body.data;
    return body as MbSeries;
}
