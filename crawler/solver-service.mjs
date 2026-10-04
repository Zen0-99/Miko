/**
 * Solver service — patchright (undetected Playwright) serving HTTP fetches
 * through a persistent browser profile.
 *
 * Why this exists alongside Byparr: Byparr's FlareSolverr API only does
 * page.goto (GET, full render, 5–20s, no POST). This service keeps one
 * browser profile (cookies/clearance persist across restarts), navigates to
 * each origin ONCE, then serves every request — GET or POST — via
 * page.evaluate(fetch()): ~200–500ms, no rendering, inherits clearance.
 *
 * POST /fetch {url, method?, headers?, body?, timeoutMs?}
 *   → {status, body}  (body as text — all crawler uses are HTML/JSON)
 * GET /health → {ok, origins, uptimeSec}
 *
 * Egress: gluetun's HTTP proxy so clearance binds to the Nord IP the rest
 * of the stack uses.
 */
import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { chromium } from 'patchright';

const PORT = Number(process.env.SOLVER_PORT ?? 8192);
const PROXY_URL = process.env.GLUETUN_PROXY ?? 'http://127.0.0.1:8888';
const PROFILE_DIR = process.env.SOLVER_PROFILE ?? fileURLToPath(new URL('./solver-profile/', import.meta.url));
const IDLE_CLOSE_MS = 30 * 60_000;
const CHALLENGE_WAIT_MS = 45_000;

let context = null;
const pages = new Map(); // origin -> { page, lastUsed, queue: Promise }

const CHALLENGE_RE = /just a moment|verify you are human|attention required|cf-chl|checking your browser|challenge-platform/i;

async function ensureBrowser() {
    if (context) return context;
    context = await chromium.launchPersistentContext(PROFILE_DIR, {
        // Chrome-headless-shell (patchright-patched) — flip via env if a site
        // insists on headed.
        headless: (process.env.SOLVER_HEADED ?? '') !== '1',
        proxy: { server: PROXY_URL },
        viewport: { width: 1280, height: 800 },
        locale: 'en-US',
        ignoreHTTPSErrors: true,
        args: [
            '--disable-blink-features=AutomationControlled',
            '--no-first-run',
            '--no-default-browser-check',
        ],
    });
    context.on('close', () => { context = null; pages.clear(); });
    return context;
}

/** Some sites (novelnice) render an interactive Turnstile that only clears
 *  after a real checkbox click — passive waiting isn't enough. Try the
 *  documented clicks each poll: the checkbox input inside the CF iframe, and
 *  a positional click on the widget's left area. Both no-op harmlessly when
 *  no challenge is up. */
async function tryTurnstileClicks(page) {
    try {
        const frame = page.frameLocator('iframe[src*="challenges.cloudflare.com"]');
        await frame.locator('input[type="checkbox"]').click({ timeout: 800 });
    } catch { /* no checkbox frame — try positional */ }
    try {
        for (const el of await page.locator('iframe[src*="challenges.cloudflare.com"]').all()) {
            const box = await el.boundingBox();
            if (box) await el.click({ position: { x: 30, y: box.height / 2 }, timeout: 800 });
        }
    } catch { /* best-effort */ }
}

/** Detect a Cloudflare challenge page; wait for it to clear (patchright
 * auto-solves most managed challenges incl. Turnstile checkbox). */
async function waitForChallengeClear(page) {
    const deadline = Date.now() + CHALLENGE_WAIT_MS;
    let clickAttempted = false;
    while (Date.now() < deadline) {
        try {
            const probe = await page.evaluate(() => ({
                title: document.title,
                body: document.body?.innerText?.slice(0, 600) ?? '',
            }));
            if (!CHALLENGE_RE.test(probe.title) && !CHALLENGE_RE.test(probe.body)) {
                // Turnstile iframe may still be running — give it one extra beat
                await page.waitForTimeout(1200);
                return true;
            }
            if (clickAttempted) {
                // Turnstile doesn't re-arm a consumed widget — reload for a fresh one.
                clickAttempted = false;
                await page.reload({ waitUntil: 'domcontentloaded', timeout: 30_000 }).catch(() => {});
                await page.waitForTimeout(1000);
                continue;
            }
            clickAttempted = true;
            await tryTurnstileClicks(page);
        } catch { /* navigations can make evaluate race — retry */ }
        await page.waitForTimeout(1500);
    }
    return false;
}

async function pageFor(origin) {
    const entry = pages.get(origin);
    if (entry && !entry.page.isClosed()) {
        entry.lastUsed = Date.now();
        return entry;
    }
    const ctx = await ensureBrowser();
    const page = await ctx.newPage();
    const rec = { page, lastUsed: Date.now(), queue: Promise.resolve() };
    pages.set(origin, rec);
    return rec;
}

/** Serialize work per origin — keeps the page's fetch context stable and
 *  paces requests gently per site. */
function enqueue(rec, fn) {
    const run = rec.queue.then(fn, fn);
    rec.queue = run.catch(() => {});
    return run;
}

async function navigateAndSolve(rec, url) {
    const page = rec.page;
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 }).catch(() => {});
    const cleared = await waitForChallengeClear(page);
    if (!cleared) throw new Error('challenge not cleared');
}

async function solverFetch(origin, { url, method, headers, body }) {
    const rec = await pageFor(origin);
    return enqueue(rec, async () => {
        // Page must sit on the same origin for fetch() to be same-origin.
        const currentOrigin = rec.page.url().startsWith('http') ? new URL(rec.page.url()).origin : null;
        if (currentOrigin !== origin) await navigateAndSolve(rec, origin + '/');

        const exec = () => rec.page.evaluate(async ({ url, method, headers, body }) => {
            try {
                const r = await fetch(url, {
                    method,
                    headers,
                    body: body ?? undefined,
                    credentials: 'include',
                    redirect: 'follow',
                });
                return { status: r.status, body: await r.text() };
            } catch (e) {
                return { status: 0, body: '', error: String(e) };
            }
        }, { url, method, headers, body });

        let out = await exec();
        // Blocked/challenged → re-navigate (lets CF re-eval + patchright solve), retry once.
        if ((out.status === 403 || out.status === 503) || CHALLENGE_RE.test((out.body ?? '').slice(0, 4000))) {
            await navigateAndSolve(rec, url);
            out = await exec();
        }
        if (out.status === 0) {
            // Page died — rebuild it once and retry on the fresh page.
            await rec.page.close().catch(() => {});
            pages.delete(origin);
            const fresh = await pageFor(origin);
            await navigateAndSolve(fresh, url);
            rec.page = fresh.page;
            out = await exec();
        }
        return out;
    });
}

// Idle page GC
setInterval(() => {
    const now = Date.now();
    for (const [origin, rec] of pages) {
        if (now - rec.lastUsed > IDLE_CLOSE_MS) {
            rec.page.close().catch(() => {});
            pages.delete(origin);
        }
    }
}, 60_000).unref();

const server = http.createServer(async (req, res) => {
    try {
        if (req.method === 'GET' && req.url === '/health') {
            res.writeHead(200, { 'content-type': 'application/json' });
            return void res.end(JSON.stringify({ ok: true, origins: [...pages.keys()], uptimeSec: Math.round(process.uptime()) }));
        }
        if (req.method === 'POST' && req.url === '/fetch') {
            const chunks = [];
            for await (const c of req) chunks.push(c);
            const payload = JSON.parse(Buffer.concat(chunks).toString('utf8'));
            const { url } = payload;
            if (!url || !/^https?:\/\//.test(url)) {
                res.writeHead(400, { 'content-type': 'application/json' });
                return void res.end(JSON.stringify({ error: 'bad url' }));
            }
            const origin = new URL(url).origin;
            const timeoutMs = Math.min(Math.max(payload.timeoutMs ?? 45_000, 5_000), 180_000);
            const out = await Promise.race([
                solverFetch(origin, {
                    url,
                    method: payload.method ?? 'GET',
                    headers: payload.headers ?? {},
                    body: payload.body ?? null,
                }),
                new Promise((_, rej) => setTimeout(() => rej(new Error('solver timeout')), timeoutMs)),
            ]);
            res.writeHead(200, { 'content-type': 'application/json' });
            return void res.end(JSON.stringify({ status: out.status, body: out.body ?? '', error: out.error }));
        }
        res.writeHead(404).end();
    } catch (e) {
        res.writeHead(500, { 'content-type': 'application/json' });
        res.end(JSON.stringify({ error: String(e?.message ?? e) }));
    }
});

server.listen(PORT, '127.0.0.1', () => {
    console.log(`solver-service on http://127.0.0.1:${PORT} (proxy ${PROXY_URL}, profile ${PROFILE_DIR})`);
});
