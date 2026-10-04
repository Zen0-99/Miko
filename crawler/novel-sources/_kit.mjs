/**
 * Shared kit for novel source adapters — ports of the Kotlin scrapers in
 * Zen0-99/MikoNovelSources (yokai.extension.novel.*). Cheerio stands in for
 * Jsoup; fetches ride gluetun's HTTP proxy (:8888) so egress matches the
 * Suwayomi tunnel's Nord exit.
 */
import { ProxyAgent, fetch as undiciFetch } from 'undici';
import * as cheerio from 'cheerio';

export const PROXY_URL = process.env.GLUETUN_PROXY ?? 'http://127.0.0.1:8888';
export const SOLVER_URL = process.env.SOLVER_URL ?? 'http://127.0.0.1:8192';
const dispatcher = new ProxyAgent(PROXY_URL);

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

/**
 * Origins that challenged us — every call goes through the solver browser
 * (cookies/clearance live in its persistent profile). Marked on first
 * 403/503 so we skip the doomed undici attempt.
 */
const solverOnly = new Set();

/**
 * Fetch through solver-service.mjs — patchright browser, persistent profile.
 * In-page fetch() supports POST (unlike Byparr's goto-only API) and returns
 * in ~200–500ms once the origin's challenge is cleared.
 */
async function fetchViaSolver(url, { method = 'GET', data, headers = {} } = {}) {
    const body = data
        ? (data instanceof URLSearchParams ? data.toString() : new URLSearchParams(data).toString())
        : undefined;
    const res = await fetch(SOLVER_URL + '/fetch', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
            url,
            method,
            headers: {
                'user-agent': UA,
                accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'accept-language': 'en-US,en;q=0.9',
                ...(body ? { 'content-type': 'application/x-www-form-urlencoded' } : {}),
                ...headers,
            },
            body,
            timeoutMs: 100_000,
        }),
        signal: AbortSignal.timeout(120_000),
    });
    const j = await res.json().catch(() => null);
    if (!j || j.status === 0 || j.error) throw new Error(`solver failed ${url}: ${j?.error ?? res.status}`);
    if (j.status >= 400) throw new Error(`solver http ${j.status} ${url}`);
    return j.body ?? '';
}

export async function fetchText(url, { method = 'GET', data, headers = {}, timeoutMs = 30_000 } = {}) {
    const origin = new URL(url).origin;
    if (solverOnly.has(origin)) return fetchViaSolver(url, { method, data, headers });

    const body = data
        ? (data instanceof URLSearchParams ? data.toString() : new URLSearchParams(data).toString())
        : undefined;
    const res = await undiciFetch(url, {
        method,
        dispatcher,
        signal: AbortSignal.timeout(timeoutMs),
        headers: {
            'user-agent': UA,
            accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'accept-language': 'en-US,en;q=0.9',
            ...(body ? { 'content-type': 'application/x-www-form-urlencoded' } : {}),
            ...headers,
        },
        body,
    });
    if (res.ok) return res.text();
    if (res.status === 403 || res.status === 503) {
        solverOnly.add(origin);
        console.log(`  [solver] ${new URL(url).host}`);
        return fetchViaSolver(url, { method, data, headers });
    }
    throw new Error(`http ${res.status} ${url}`);
}

export const getDoc = async (url, opts) => cheerio.load(await fetchText(url, opts));
export const postForm = (url, data, headers = {}) =>
    fetchText(url, { method: 'POST', data, headers });
export const postDoc = async (url, data, headers = {}) =>
    cheerio.load(await postForm(url, data, headers));

/** Resolve possibly-relative href against base; tolerates missing scheme. */
export function fixUrl(href, base) {
    if (!href) return null;
    try { return new URL(href, base).href; } catch { return href; }
}

const NONWORD = /[^\p{L}\p{N}\s]/gu;
export const norm = (t) =>
    (t ?? '').normalize('NFKC').toLowerCase().replace(NONWORD, ' ').replace(/\s+/g, ' ').trim();

/** Same match contract as crawler.mjs resolveManga: exact or containment. */
export function titleMatch(found, want) {
    const w = norm(want);
    const f = norm(found);
    return f === w || (f.includes(w) && w.length > 6);
}

export const pick = (results, want) => (results ?? []).find((r) => titleMatch(r.title, want)) ?? null;

const slugify = (t) => norm(t).replace(/\s+/g, '-');

async function verifyEntry(url, title, titleSelector) {
    try {
        const doc = await getDoc(url);
        const pageTitle = doc(titleSelector).first().text().trim();
        return pageTitle && titleMatch(pageTitle, title)
            ? { title: pageTitle, url }
            : null;
    } catch { return null; }
}

/**
 * Search fallback for sites whose search is POST-only (Byparr navigates GETs
 * only). Probes detail pages at `{base}/{slug}` candidates and verifies the
 * landing title with titleMatch. Caps at 3 probes — each may be a browser
 * solve on solver-only origins.
 */
export async function probeBySlug(title, pathPrefix, base, titleSelector) {
    const cands = [];
    const full = slugify(title);
    if (full) cands.push(full);
    const head = slugify(title.split(/[:;–—]/)[0]);
    if (head && head !== full) cands.push(head);
    for (const slug of cands.slice(0, 3)) {
        const hit = await verifyEntry(`${base}/${pathPrefix}${slug}`, title, titleSelector);
        if (hit) return [hit];
    }
    return [];
}

/**
 * Last resort: DuckDuckGo html endpoint (GET-only, bot-tolerant). Finds real
 * entry URLs on `domain`, verifies each candidate's detail title. urlFilter
 * narrows to the site's entry-path shape (e.g. u => u.includes('/novel/')).
 */
export async function searchViaDdg(title, domain, urlFilter, titleSelector, maxCandidates = 4) {
    let doc;
    try {
        doc = await getDoc(`https://html.duckduckgo.com/html/?q=${encodeURIComponent(`${title} ${domain}`)}`);
    } catch { return []; }
    const seen = new Set();
    const cands = [];
    for (const a of doc('.result__a').toArray()) {
        const href = doc(a).attr('href') ?? '';
        const m = /uddg=([^&]+)/.exec(href);
        const real = m ? decodeURIComponent(m[1]) : href;
        if (!real.includes(domain) || (urlFilter && !urlFilter(real)) || seen.has(real)) continue;
        seen.add(real);
        cands.push(real);
        if (cands.length >= maxCandidates) break;
    }
    const hits = [];
    for (const url of cands) {
        const hit = await verifyEntry(url, title, titleSelector);
        if (hit) hits.push(hit);
        if (hits.length) break; // one verified entry is enough
    }
    return hits;
}

export const firstNum = (text) => {
    const m = /(\d+(?:\.\d+)?)/.exec(text ?? '');
    return m ? parseFloat(m[1]) : null;
};
