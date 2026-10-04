/**
 * NovelFull (yokai.extension.novel.en.novelfull) — port of NovelFull.kt.
 * Chapter list paginates on the entry page: ?page=N&per-page=50.
 */
import { getDoc, fixUrl, firstNum } from './_kit.mjs';

export const pkg = 'yokai.extension.novel.en.novelfull';
export const name = 'NovelFull';
export const baseUrl = 'https://novelfull.com';

export async function search(title) {
    const doc = await getDoc(`${baseUrl}/search?keyword=${encodeURIComponent(title)}&page=1`);
    return doc('#list-page > div.col-truyen-main.archive > div > div.row, #list-page div.row')
        .map((_, el) => {
            const a = doc(el).find('h3.truyen-title > a, h3 > a').first();
            return { title: a.text().trim(), url: fixUrl(a.attr('href'), baseUrl) };
        })
        .get().filter((r) => r.title && r.url);
}

function parsePage(doc) {
    return doc('#list-chapter .list-chapter li a, .list-chapter li a')
        .map((_, a) => {
            const el = doc(a);
            const nm = el.text().trim();
            return {
                name: nm,
                url: fixUrl(el.attr('href'), baseUrl),
                date: null,
                number: firstNum(nm),
            };
        }).get().filter((c) => c.name && c.url);
}

export async function chapterList(entryUrl) {
    const first = await getDoc(`${entryUrl}?page=1&per-page=50`);
    const lastHref = first('li.last a').attr('href');
    const total = (lastHref && Number(/page=(\d+)/.exec(lastHref)?.[1])) ||
        Math.max(1, ...first('.pagination li a').map((_, a) => parseInt(first(a).text(), 10)).get().filter(Number.isFinite));

    const out = parsePage(first);
    for (let p = 2; p <= Math.min(total, 60); p++) {
        const doc = await getDoc(`${entryUrl}?page=${p}&per-page=50`);
        const rows = parsePage(doc);
        if (!rows.length) break;
        out.push(...rows);
    }
    const seen = new Set();
    return out.filter((c) => !seen.has(c.url) && seen.add(c.url));
}
