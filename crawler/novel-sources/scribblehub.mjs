/**
 * ScribbleHub (yokai.extension.novel.en.scribblehub) — port of Scribblehub.kt.
 * Chapter list = WordPress admin-ajax `sf_get_fic_chapters` keyed by the
 * entry page's hidden `input#mypostid`.
 */
import { getDoc, postDoc, fixUrl } from './_kit.mjs';

export const pkg = 'yokai.extension.novel.en.scribblehub';
export const name = 'ScribbleHub';
export const baseUrl = 'https://www.scribblehub.com';

export async function search(title) {
    // ?s=&post_type=fictionposts returns 410 — dead endpoint (Kotlin stale).
    // The Series Finder page runs the same search server-side via ?sh=.
    const doc = await getDoc(
        `${baseUrl}/series-finder/?sf=1&sh=${encodeURIComponent(title)}`,
    );
    return doc('div.search_main_box').map((_, el) => {
        const a = doc(el).find('div.search_title > a').first();
        return { title: a.text().trim(), url: fixUrl(a.attr('href'), baseUrl) };
    }).get().filter((r) => r.title && r.url);
}

export async function chapterList(entryUrl) {
    // TOC page 1 is server-rendered on the series page: ol.toc_ol > li.toc_w
    // (newest-first). Longer TOCs paginate via admin-ajax action
    // wi_getreleases_pagination {pagenum, mypostid} — the Kotlin's actions
    // (sf_get_fic_chapters / wi_gettocchp) are stale and 400.
    const doc = await getDoc(entryUrl);
    const parseRows = (d) => d('ol.toc_ol li.toc_w, li.toc_w').map((i, li) => {
        const a = d(li).find('a.toc_a').first();
        const num = /^\d+/.exec(a.text().trim())?.[0];
        return {
            name: a.text().trim() || `Chapter ${i + 1}`,
            url: fixUrl(a.attr('href'), baseUrl),
            date: d(li).find('span.fic_date_pub').first().attr('title')
                ?? d(li).find('span.fic_date_pub').first().text().trim() ?? null,
            number: num ? Number(num) : null,
        };
    }).get().filter((c) => c.url);

    const out = parseRows(doc);
    if (!out.length) return out;

    const storyId =
        doc('input#mypostid').attr('value') ??
        doc('input[name=mypostid]').attr('value') ??
        doc('[data-story-id]').attr('data-story-id');
    const total = Number(doc('input#chpcounter').attr('value')) || 0;
    const pages = storyId && out.length ? Math.min(60, Math.ceil(total / out.length) - 1) : 0;

    for (let p = 2; p <= pages + 1; p++) {
        const d = await postDoc(
            `${baseUrl}/wp-admin/admin-ajax.php`,
            { action: 'wi_getreleases_pagination', pagenum: String(p), mypostid: storyId },
            { Referer: entryUrl, 'X-Requested-With': 'XMLHttpRequest' },
        );
        const rows = parseRows(d);
        if (!rows.length) break;
        out.push(...rows);
    }

    const seen = new Set();
    return out.reverse() // newest-first → chronological
        .filter((c) => !seen.has(c.url) && seen.add(c.url));
}
