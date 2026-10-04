/**
 * NovelsOnline (yokai.extension.novel.en.novelsonline) — port of
 * NovelsOnline.kt. Search is a POST to /search-ajax returning an HTML
 * fragment of <li> items; chapters render server-side on the entry page.
 */
import { getDoc, postDoc, fixUrl, firstNum, probeBySlug, searchViaDdg } from './_kit.mjs';

export const pkg = 'yokai.extension.novel.en.novelsonline';
export const name = 'NovelsOnline';
export const baseUrl = 'https://novelsonline.org';

export async function search(title) {
    // /search-ajax is POST-only — unreachable through Byparr (GET-only).
    // Probe /{slug} detail pages first (Kotlin: /{slug}/chapter-N).
    const probed = await probeBySlug(title, '', baseUrl, 'div.block-title h1');
    if (probed.length) return probed;
    return searchViaDdg(title, 'novelsonline', (u) => u.includes('novelsonline'), 'div.block-title h1');
}

export async function chapterList(entryUrl) {
    const doc = await getDoc(entryUrl);
    return doc('div.panel.panel-default ul li a, div.panel ul li a')
        .map((i, a) => {
            const el = doc(a);
            const nm = el.text().trim();
            return {
                name: nm || `Chapter ${i + 1}`,
                url: fixUrl(el.attr('href'), baseUrl),
                date: null,
                number: firstNum(nm),
            };
        }).get().filter((c) => c.url);
}
