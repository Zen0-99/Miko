/**
 * BoxNovel (yokai.extension.novel.en.boxnovel) — port of BoxNovel.kt.
 * Madara-theme site at novelnice.com: WP search (?s=&post_type=wp-manga)
 * and POSTed ajax chapter pages ({entry}/ajax/chapters/?t=N, newest-first).
 */
import { getDoc, postDoc, postForm, fixUrl, firstNum, probeBySlug, searchViaDdg } from './_kit.mjs';

export const pkg = 'yokai.extension.novel.en.boxnovel';
export const name = 'BoxNovel';
export const baseUrl = 'https://novelnice.com';

export async function search(title) {
    // wp-manga-search-manga (autocomplete ajax) is NOT CF-challenged and
    // returns JSON — far better than the ?s= page which sits behind an
    // interactive Turnstile this IP can't clear.
    try {
        const text = await postForm(`${baseUrl}/wp-admin/admin-ajax.php`, {
            action: 'wp-manga-search-manga',
            title,
        }, { Referer: baseUrl, 'X-Requested-With': 'XMLHttpRequest' });
        const j = JSON.parse(text);
        if (j?.success && Array.isArray(j.data)) {
            const rows = j.data
                .filter((r) => r?.url?.includes('/read/') && r.title)
                .map((r) => ({ title: r.title.trim(), url: r.url }));
            if (rows.length) return rows;
        }
    } catch { /* fall through to probes */ }

    const probed = await probeBySlug(title, 'read/', baseUrl, 'div.post-title h1, h1');
    if (probed.length) return probed;
    return searchViaDdg(title, 'novelnice.com', (u) => u.includes('/read/'), 'div.post-title h1, h1');
}

export async function chapterList(entryUrl) {
    // The Kotlin's ?t={page} variant 404s on this site — bare POST to
    // /ajax/chapters/ returns the full single-page list (no server
    // pagination; "Show more" is client-side).
    const out = [];
    const d = await postDoc(
        `${entryUrl.replace(/\/$/, '')}/ajax/chapters/`,
        {},
        { Referer: entryUrl, 'X-Requested-With': 'XMLHttpRequest' },
    );
    d('li.wp-manga-chapter > a[href]').each((_, a) => {
        const url = fixUrl(d(a).attr('href'), baseUrl);
        out.push({
            name: d(a).text().trim(),
            url,
            date: null,
            number: Number(/chapter-(\d+(?:\.\d+)?)/.exec(url ?? '')?.[1]) || null,
        });
    });
    const seen = new Set();
    return out.reverse()
        .filter((c) => c.url && !seen.has(c.url) && seen.add(c.url))
        .map((c, i) => ({ ...c, name: c.name || `Chapter ${i + 1}`, number: c.number ?? firstNum(c.name) }));
}
