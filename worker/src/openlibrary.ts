// OpenLibrary API client — book identity for Western published works.
// Free, no auth. https://openlibrary.org/developers/api

export interface OlEdition {
    key: string; // /books/OL…M
    title?: string;
    isbn_10?: string[];
    isbn_13?: string[];
    authors?: Array<{ key: string }>;
    publish_date?: string;
    /** Work rollup — the canonical identity we mint from. */
    works?: Array<{ key: string }>; // /works/OL…W
    /** Cover record ids — renders as covers.openlibrary.org/b/id/{n}-L.jpg */
    covers?: number[];
}

export interface OlSearchDoc {
    key: string; // /works/OL…W
    title?: string;
    author_name?: string[];
    first_publish_year?: number;
    isbn?: string[];
    /** Cover record id — renders as covers.openlibrary.org/b/id/{n}-L.jpg */
    cover_i?: number;
}

const UA = 'Miko/2.0 (github.com/Zen0-99/Miko)';

export const OPENLIBRARY_BASE = 'https://openlibrary.org';

async function getJson(base: string, path: string): Promise<unknown> {
    const res = await fetch(`${base}${path}`, {
        headers: { 'User-Agent': UA, Accept: 'application/json' },
        signal: AbortSignal.timeout(5_000),
    });
    if (!res.ok) throw new Error(`openlibrary ${res.status}`);
    return res.json();
}

/** "ISBN-13 canonical" — strip hyphens/spaces; 10→13 not converted (OL accepts both). */
export function normalizeIsbn(raw: string): string {
    return raw.replace(/[-\s]/g, '').toUpperCase();
}

export function isValidIsbn(isbn: string): boolean {
    return /^(\d{10}|\d{13}|\d{9}X)$/.test(isbn);
}

/** /isbn/{isbn}.json — edition record with work rollup. */
export async function isbnToEdition(
    isbn: string,
    base = OPENLIBRARY_BASE,
): Promise<OlEdition | null> {
    try {
        const body = (await getJson(base, `/isbn/${isbn}.json`)) as OlEdition;
        return body && typeof body === 'object' ? body : null;
    } catch {
        return null;
    }
}

/** /search.json — work-level docs. */
export async function searchWorks(
    title: string,
    author?: string,
    limit = 10,
    base = OPENLIBRARY_BASE,
): Promise<OlSearchDoc[]> {
    const fields = 'key,title,author_name,first_publish_year,isbn,cover_i';
    let q = `title=${encodeURIComponent(title)}`;
    if (author) q += `&author=${encodeURIComponent(author)}`;
    const body = (await getJson(base, `/search.json?${q}&limit=${limit}&fields=${fields}`)) as {
        docs?: OlSearchDoc[];
    };
    return Array.isArray(body?.docs) ? body.docs : [];
}

/** "/works/OL45883W" → "OL45883W" (null for non-work keys). */
export function workIdFromKey(key: string | undefined): string | null {
    const m = /^\/works\/(OL\d+W)$/.exec(key ?? '');
    return m?.[1] ?? null;
}

/** OL cover record id → image URL (large size). */
export function olCoverUrl(coverId: number | undefined | null): string | null {
    return coverId ? `https://covers.openlibrary.org/b/id/${coverId}-L.jpg` : null;
}
