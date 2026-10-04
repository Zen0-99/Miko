/**
 * NovelFire (yokai.extension.novel.en.novelfire) — port of NovelFire.kt.
 * Chapters paginate on {entry}/chapters?page=N until `a[rel=next]` vanishes.
 * Entry urls may be relative — resolve against baseUrl.
 */
import { getDoc, fixUrl } from './_kit.mjs';

export const pkg = 'yokai.extension.novel.en.novelfire';
export const name = 'NovelFire';
export const baseUrl = 'https://novelfire.net';

export async function search(title) {
    const doc = await getDoc(`${baseUrl}/search?title=${encodeURIComponent(title)}&page=1`);
    return doc('li.novel-item').map((_, el) => {
        const a = doc(el).find('a').first();
        return {
            title: (a.attr('title') || a.text()).trim(),
            url: fixUrl(a.attr('href'), baseUrl),
        };
    }).get().filter((r) => r.title && r.url);
}

export async function chapterList(entryUrl) {
    const out = [];
    for (let page = 1; page <= 100; page++) {
        const doc = await getDoc(`${entryUrl.replace(/\/$/, '')}/chapters?page=${page}`);
        doc('ul.chapter-list li a[href*=chapter]').each((_, a) => {
            const el = doc(a);
            const nm = (el.find('strong.chapter-title').text() || el.attr('title') || el.text()).trim();
            if (!nm || nm.includes('Chapters')) return;
            const no = parseFloat(el.find('span.chapter-no').text().trim());
            const dt = el.find('time.chapter-update').attr('datetime');
            out.push({
                name: nm,
                url: fixUrl(el.attr('href'), baseUrl),
                date: dt ? Date.parse(dt) || null : null,
                number: Number.isFinite(no) ? no : null,
            });
        });
        if (!doc('a[rel=next]').length) break;
    }
    const seen = new Set();
    return out.filter((c) => c.url && !seen.has(c.url) && seen.add(c.url));
}
