// Cinemeta client — IMDB-keyed shows identity (mk:imdb:).
// Stremio meta protocol: GET {base}/meta/{series|movie}/{ttid}.json.
// The worker is the kind authority: Cinemeta `type` + the Japan+Animation
// rule (same as REQ-001 / app AnimeDetection.kt) decide the stored kind.

export interface CinemetaMeta {
    id?: string;
    /** 'movie' | 'series' (Cinemeta vocab). */
    type?: string;
    name?: string;
    genres?: string[];
    /** e.g. "Japan" or "United States" — may be a list on some metas. */
    country?: string | string[];
    releaseInfo?: string;
    year?: string | number;
    description?: string;
    poster?: string;
}

export const CINEMETA_BASE = 'https://v3-cinemeta.strem.io';

const UA = 'Miko/2.0 (github.com/Zen0-99/Miko)';

/** /meta/{type}/{ttid}.json — null on 404/network failure. */
export async function fetchMeta(
    type: 'series' | 'movie',
    ttid: string,
    base = CINEMETA_BASE,
): Promise<CinemetaMeta | null> {
    try {
        const res = await fetch(`${base}/meta/${type}/${encodeURIComponent(ttid)}.json`, {
            headers: { 'User-Agent': UA, Accept: 'application/json' },
            signal: AbortSignal.timeout(5_000),
        });
        if (!res.ok) return null;
        const body = (await res.json()) as { meta?: CinemetaMeta };
        return body?.meta ?? null;
    } catch {
        return null;
    }
}

/** Cinemeta releaseInfo ("2005-2013", "2019", …) / year → 4-digit year. */
export function metaYear(meta: CinemetaMeta | null): number | null {
    const raw = meta?.year ?? meta?.releaseInfo;
    const m = /(\d{4})/.exec(String(raw ?? ''));
    return m ? parseInt(m[1], 10) : null;
}

/**
 * Japan + Animation rule — Cinemeta has no "anime" genre; western
 * animation also carries "Animation", so the country field is the
 * distinguisher (mirrors app `AnimeDetection.isAnime`).
 */
export function isAnimeMeta(meta: CinemetaMeta): boolean {
    const country = Array.isArray(meta.country)
        ? meta.country.join(' ')
        : (meta.country ?? '');
    if (!country.toLowerCase().includes('japan')) return false;
    return (meta.genres ?? []).some((g) => g.toLowerCase() === 'animation');
}

/** Worker-assigned kind for a Cinemeta meta. Never trusts kind_hint. */
export function deriveShowKind(meta: CinemetaMeta): 'anime' | 'movie' | 'series' {
    if (isAnimeMeta(meta)) return 'anime';
    return meta.type === 'movie' ? 'movie' : 'series';
}
