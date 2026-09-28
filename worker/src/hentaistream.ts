// Hentaistream catalog client — NSFW shows identity (mk:hs:).
//
// The addon (github.com/Zen0-99/hentaistream-addon) speaks the Stremio
// meta protocol — GET {addon}/meta/{series|hentai}/{id}.json for
// hmm-/hse-/htv-/hs- ids — but the only public deployment
// (hentaistream-addon.onrender.com) is suspended, so the GitHub-hosted
// catalog artifact is the identity source of truth:
//   catalog[]      — canonical rows keyed on `id` (prov-slug),
//                    `providerSlugs` = {prov: bare-slug} alias map
//   slugRegistry   — normalized-slug → {prov: {id, …}} alias→id index
// Bulk identity ships via scripts/mikodb/build-hs-seed.mjs; this client
// is the lazy-fill path for items newer than the last seed.
// Keep the slug rules in sync with that script AND the addon's
// src/cache/slugRegistry.js makeKey normalization.

export interface HsCatalogItem {
    id: string;
    type?: string;
    name?: string;
    year?: number | string;
    releaseInfo?: string;
    genres?: string[];
    studio?: string;
    poster?: string;
    description?: string;
    providers?: string[];
    providerSlugs?: Record<string, string>;
}

export interface HsCatalog {
    catalog?: HsCatalogItem[];
    slugRegistry?: Record<string, Record<string, { id?: string } | undefined> | undefined>;
}

export interface HsIndex {
    byId: Map<string, HsCatalogItem>;
    /** normalized alias slug (or prefixed variant) → canonical item id. */
    aliasToId: Map<string, string>;
    /** `${provider}:${bareSlug}` → canonical item id (registry keeps the
     *  provenance of each alias — provider keys beat the bare map). */
    provSlugToId: Map<string, string>;
}

export const HENTAISTREAM_CATALOG_URL =
    'https://raw.githubusercontent.com/Zen0-99/hentaistream-addon/master/data/catalog.json.gz';

const UA = 'Miko/2.0 (github.com/Zen0-99/Miko)';

// ~4.3k rows / ~11 MB JSON — fetched and indexed once per isolate.
const cache = new Map<string, Promise<HsIndex | null>>();

const PROVIDER_PREFIX = /^(hmm|hse|htv|hs|os|hentaimama|hentaisea|hentaitv)-/;
const PROVIDER_CANON: Record<string, string> = {
    hentaimama: 'hmm',
    hentaisea: 'hse',
    hentaitv: 'htv',
};
// Registry provenance order for the (rare, 3 in the 2025-12 catalog)
// slugs that map to different ids across providers.
const PROVIDER_RANK = ['hmm', 'hse', 'htv', 'hs', 'os'];

/** Strip the provider prefix only — lossless normalization. */
export function stripHsPrefix(raw: string): { provider: string | null; slug: string } {
    const s = raw.toLowerCase().trim();
    const m = PROVIDER_PREFIX.exec(s);
    if (!m) return { provider: null, slug: s };
    return { provider: PROVIDER_CANON[m[1]] ?? m[1], slug: s.slice(m[1].length + 1) };
}

/** Lossy normalizations from the addon's slugRegistry.makeKey — applied
 *  in order AFTER exact lookups (a `-the-animation` slug can be a real
 *  registry key and must hit before it is truncated). */
function slugVariants(bare: string): string[] {
    const out = [bare];
    const noEp = bare.replace(/-episode-\d+$/, '');
    if (noEp !== bare) out.push(noEp);
    for (const s of [...out]) {
        const noAnim = s.replace(/-the-animation$/, '');
        if (noAnim !== s) out.push(noAnim);
    }
    return out;
}

async function fetchCatalog(url: string): Promise<HsIndex | null> {
    try {
        const res = await fetch(url, {
            headers: { 'User-Agent': UA, Accept: 'application/json' },
            signal: AbortSignal.timeout(15_000),
        });
        if (!res.ok) throw new Error(`hentaistream catalog ${res.status}`);
        const buf = await res.arrayBuffer();
        const bytes = new Uint8Array(buf);
        // .gz is served as octet-stream (no Content-Encoding) — sniff the
        // gzip magic; a plain .json body (tests, mirrors) parses as-is.
        const text =
            bytes[0] === 0x1f && bytes[1] === 0x8b
                ? await new Response(
                      new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip')),
                  ).text()
                : new TextDecoder().decode(bytes);
        const cat = JSON.parse(text) as HsCatalog;
        return buildIndex(cat);
    } catch {
        return null;
    }
}

function buildIndex(cat: HsCatalog): HsIndex {
    const byId = new Map<string, HsCatalogItem>();
    const aliasToId = new Map<string, string>();
    const provSlugToId = new Map<string, string>();
    for (const item of cat.catalog ?? []) {
        if (!item?.id) continue;
        byId.set(item.id, item);
        for (const [prov, slug] of Object.entries(item.providerSlugs ?? {})) {
            if (!slug) continue;
            const s = slug.toLowerCase();
            aliasToId.set(s, item.id);
            aliasToId.set(`${prov}-${s}`, item.id);
            provSlugToId.set(`${prov}:${s}`, item.id);
        }
    }
    for (const [key, provs] of Object.entries(cat.slugRegistry ?? {})) {
        if (!provs) continue;
        const slug = key.toLowerCase();
        for (const [prov, e] of Object.entries(provs)) {
            if (e?.id && byId.has(e.id)) {
                provSlugToId.set(`${prov}:${slug}`, e.id);
                // Prefixed alias variant ('htv-21') — providerSlugs take
                // precedence, so only fill when absent.
                if (!aliasToId.has(`${prov}-${slug}`)) {
                    aliasToId.set(`${prov}-${slug}`, e.id);
                }
            }
        }
        // Bare-slug alias: prefer the provider-ranked canonical id; only
        // ids that still exist in the catalog count (1 stale entry in
        // the 2025-12 snapshot).
        const ids = PROVIDER_RANK.map((p) => provs[p]?.id)
            .concat(Object.values(provs).map((e) => e?.id))
            .filter((id): id is string => !!id && byId.has(id));
        if (ids.length && !aliasToId.has(slug)) aliasToId.set(slug, ids[0]);
    }
    return { byId, aliasToId, provSlugToId };
}

/** Fetch + index once per isolate; null when upstream is unreachable. */
export function loadCatalog(url = HENTAISTREAM_CATALOG_URL): Promise<HsIndex | null> {
    let p = cache.get(url);
    if (!p) {
        p = fetchCatalog(url);
        cache.set(url, p);
    }
    return p;
}

/** Test hook — drop the isolate-scoped catalog cache. */
export function resetCatalogCache(): void {
    cache.clear();
}

/**
 * Canonicalize any provider slug/alias to its catalog row.
 * Returns the item plus every known alias (registry keys, providerSlugs,
 * prefixed variants) so the caller can seed `external_ids` in one shot.
 */
export function findHsEntry(
    idx: HsIndex,
    raw: string,
): { item: HsCatalogItem; aliases: string[] } | null {
    const rawLc = raw.toLowerCase().trim();
    let item = idx.byId.get(rawLc);

    const { provider, slug } = stripHsPrefix(rawLc);
    if (!item && provider) {
        for (const v of slugVariants(slug)) {
            const id = idx.provSlugToId.get(`${provider}:${v}`);
            if (id) {
                item = idx.byId.get(id);
                break;
            }
        }
    }
    if (!item) {
        for (const v of slugVariants(slug)) {
            const id = idx.aliasToId.get(v) ?? idx.byId.get(`${provider ?? 'hs'}-${v}`)?.id;
            if (id) {
                item = idx.byId.get(id);
                break;
            }
        }
    }
    if (!item) return null;

    const aliases = new Set<string>([item.id]);
    for (const [alias, id] of idx.aliasToId) {
        if (id === item.id) aliases.add(alias);
    }
    return { item, aliases: [...aliases] };
}

/** catalog `year` ("2025" | 2025) / releaseInfo → 4-digit year. */
export function hsYear(item: HsCatalogItem): number | null {
    const m = /(\d{4})/.exec(String(item.year ?? item.releaseInfo ?? ''));
    return m ? parseInt(m[1], 10) : null;
}
