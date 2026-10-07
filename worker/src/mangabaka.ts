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
    /** Phase 21 — tankōbon count + gallery size (per-volume cover lane). */
    final_volume?: number | string | null;
    covers_count?: number | string | null;
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

/* ---------- Phase 21: volume covers + edition records ---------- */

export interface MbImage {
    id: number;
    index_numeric: number | null;
    type: string;                    // 'volume' | 'volume_back' | ...
    language: string | null;
    image?: {
        raw?: { url?: string; blurhash?: string };
        x350?: { x1?: string };
    } | null;
}

export interface MbWork {
    id: string;
    sequence_numeric: number | null;
    count_type: string | null;
    release_date: string | null;
    pages: number | null;
    inc_chapters: unknown | null;
    identifiers?: Array<{ id?: string; name?: string }> | null;
    images?: MbImage[] | null;
}

export async function getSeriesImages(
    id: number,
    base = MANGABAKA_BASE,
): Promise<MbImage[]> {
    const out: MbImage[] = [];
    for (let page = 1; page <= 40; page++) {
        const body = (await getJson(
            base, `/v1/series/${id}/images?page=${page}`,
        )) as { data?: MbImage[]; pagination?: { next?: string | null } };
        const rows = Array.isArray(body?.data) ? body.data : [];
        out.push(...rows);
        if (!body?.pagination?.next || rows.length === 0) break;
    }
    return out;
}

export async function getSeriesWorks(
    id: number,
    base = MANGABAKA_BASE,
): Promise<MbWork[]> {
    const out: MbWork[] = [];
    for (let page = 1; page <= 40; page++) {
        const body = (await getJson(
            base, `/v1/series/${id}/works?page=${page}`,
        )) as { data?: MbWork[]; pagination?: { next?: string | null } };
        const rows = Array.isArray(body?.data) ? body.data : [];
        out.push(...rows);
        if (!body?.pagination?.next || rows.length === 0) break;
    }
    return out;
}
