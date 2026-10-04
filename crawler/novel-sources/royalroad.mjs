/**
 * Royal Road (yokai.extension.novel.en.royalroad) — port of RoyalRoad.kt.
 * Chapters ride the fiction page's embedded `window.chapters` JSON;
 * `table#chapters` is the server-rendered fallback.
 */
import { getDoc, fixUrl, firstNum } from './_kit.mjs';

export const pkg = 'yokai.extension.novel.en.royalroad';
export const name = 'Royal Road';
export const baseUrl = 'https://www.royalroad.com';

export async function search(title) {
    const doc = await getDoc(`${baseUrl}/fictions/search?title=${encodeURIComponent(title)}&page=1`);
    return doc('div.fiction-list-item').map((_, el) => {
        const a = doc(el).find('h2.fiction-title > a').first();
        return {
            title: a.text().trim(),
            url: fixUrl(a.attr('href'), baseUrl),
        };
    }).get().filter((r) => r.title && r.url);
}

export async function chapterList(entryUrl) {
    const doc = await getDoc(entryUrl);
    const script = doc('script')
        .map((_, s) => doc(s).text() || s.children?.[0]?.data || '')
        .get()
        .find((t) => t.includes('window.chapters'));

    if (script) {
        const start = script.indexOf('window.chapters = ') + 'window.chapters = '.length;
        const end = script.indexOf(';', start);
        try {
            const arr = JSON.parse(script.slice(start, end));
            const out = arr.map((c, i) => ({
                name: c.title ?? `Chapter ${i + 1}`,
                url: fixUrl(c.url, baseUrl),
                date: c.date ? Date.parse(c.date) || null : null,
                number: c.chapter != null ? Number(c.chapter) : i + 1,
            })).filter((c) => c.url);
            if (out.length) return out;
        } catch { /* fall through to table parse */ }
    }

    return doc('table#chapters tbody tr').map((i, row) => {
        const a = doc(row).find('td:first-child a').first();
        const dt = doc(row).find('time').attr('datetime');
        return {
            name: a.text().trim() || `Chapter ${i + 1}`,
            url: fixUrl(a.attr('href'), baseUrl),
            date: dt ? Date.parse(dt) || null : null,
            number: i + 1,
        };
    }).get().filter((c) => c.url).reverse();
}

export { firstNum };
