/**
 * FreeWebNovel (yokai.extension.novel.en.freewebnovel) — port of
 * FreeWebNovel.kt. Chapters come from the entry's `?ajax=chapters` JSON
 * endpoint (html fragment per page, newest-first).
 */
import { getDoc, postDoc, fetchText, fixUrl, firstNum, probeBySlug, searchViaDdg } from './_kit.mjs';
import * as cheerio from 'cheerio';

export const pkg = 'yokai.extension.novel.en.freewebnovel';
export const name = 'FreeWebNovel';
export const baseUrl = 'https://freewebnovel.com';

function parseResults(doc) {
    const items = doc('.col-content .con');
    const rows = (items.length ? items : doc('div.li-row')).map((_, el) => {
        const a = doc(el).find('.txt h3 a, h3.tit > a, h3 a').first();
        return {
            title: (a.attr('title') || a.text()).trim(),
            url: fixUrl(a.attr('href'), baseUrl),
        };
    }).get().filter((r) => r.title && r.url);
    const seen = new Set();
    return rows.filter((r) => !seen.has(r.url) && seen.add(r.url));
}

export async function search(title) {
    // Site renamed the form field to "keyword" (Kotlin's searchkey is stale
    // and returns an empty result page). GET ?searchkey= is still tried first.
    const get = parseResults(await getDoc(`${baseUrl}/search/?searchkey=${encodeURIComponent(title)}`));
    if (get.length) return get;
    const post = parseResults(await postDoc(`${baseUrl}/search`, { keyword: title }, {
        Referer: baseUrl,
        Origin: baseUrl,
    }));
    if (post.length) return post;
    // POST is dead on solver-only origins — probe /novel/{slug}, then DDG.
    const probed = await probeBySlug(title, 'novel/', baseUrl, 'h1.tit');
    if (probed.length) return probed;
    // .io mirror has a different page shape the extension can't parse — .com only.
    return searchViaDdg(title, 'freewebnovel.com', (u) => u.includes('freewebnovel.com/novel/'), 'h1.tit');
}

export async function chapterList(entryUrl) {
    const pageSize = 40;
    const out = [];
    for (let page = 1; page <= 250; page++) {
        const res = await fetchText(
            `${entryUrl}?ajax=chapters&page=${page}&pageSize=${pageSize}`,
            { headers: { 'X-Requested-With': 'XMLHttpRequest', Referer: entryUrl } },
        );
        let j;
        try { j = JSON.parse(res); } catch { break; }
        if (!j.html?.trim()) break;
        const d = cheerio.load(j.html);
        const links = d('li > a[href]');
        if (!links.length) break;
        links.each((_, a) => {
            const el = d(a);
            const nm = (el.find('.title, span.title').text() || el.attr('title') || el.text()).trim();
            out.push({
                name: nm || `Chapter ${out.length + 1}`,
                url: fixUrl(el.attr('href'), baseUrl),
                date: null,
                number: firstNum(nm),
            });
        });
        if (j.totalChapters > 0 && out.length >= j.totalChapters) break;
        if (links.length < pageSize) break;
    }
    const byUrl = new Map();
    for (const c of out.reverse()) if (c.url && !byUrl.has(c.url)) byUrl.set(c.url, c);
    return [...byUrl.values()];
}
