/**
 * ReadNovelFull (yokai.extension.novel.en.readnovelfull) — port of
 * ReadNovelFull.kt. Chapters: ajax/chapter-archive keyed by the entry
 * page's div#rating[data-novel-id].
 */
import { getDoc, fixUrl } from './_kit.mjs';

export const pkg = 'yokai.extension.novel.en.readnovelfull';
export const name = 'ReadNovelFull';
export const baseUrl = 'https://readnovelfull.com';

export async function search(title) {
    const doc = await getDoc(`${baseUrl}/novel-list/search?keyword=${encodeURIComponent(title)}`);
    return doc('div.col-novel-main > div.list-novel > div.row').map((_, row) => {
        const a = doc(row).find('h3.novel-title > a, h3 > a').first();
        return { title: a.text().trim(), url: fixUrl(a.attr('href'), baseUrl) };
    }).get().filter((r) => r.title && r.url);
}

export async function chapterList(entryUrl) {
    const doc = await getDoc(entryUrl);
    const novelId = doc('div#rating').attr('data-novel-id');
    if (!novelId) return [];
    const d = await getDoc(`${baseUrl}/ajax/chapter-archive?novelId=${novelId}`);
    return d('div.panel-body div.row div ul.list-chapter li > a, ul.list-chapter li > a')
        .map((i, a) => {
            const el = d(a);
            const nm = (el.find('span').text() || el.text()).trim();
            return {
                name: nm || `Chapter ${i + 1}`,
                url: fixUrl(el.attr('href'), baseUrl),
                date: null,
                number: i + 1,
            };
        }).get().filter((c) => c.url);
}
