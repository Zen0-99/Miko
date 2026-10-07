"""comick lane — Patchright headed-Chrome seeder (seed-time only).

Verified flow (Phase 21 research): api.comick.dev serves JSON through a
real Chrome context, but /comic/*/chapters is endpoint-hardened — fetch()
inside the page 403s even after clearance, while top-level page.goto
navigations clear. So this lane navigates per API call: one comick.dev
landing for CF clearance, then page.goto per endpoint, JSON pulled from
the rendered body text.

Rate discipline is the feature: ≥1.5s between navigations, bounded
pagination. A flagged IP kills the run.
"""
from __future__ import annotations

import json
import time
import urllib.parse

from common import title_sim

API = 'https://api.comick.dev'
SITE = 'https://comick.dev'
NAV_GAP = 1.6
PAGE_LIMIT = 100
MAX_PAGES = 40

_SHARED = {'browser': None, 'page': None, 'pw': None}


def _browser(ctx: dict):
    """Lazy singleton — one headed Chrome for the whole run."""
    if _SHARED['page'] is not None:
        return _SHARED['page']
    from patchright.sync_api import sync_playwright
    pw = sync_playwright().start()
    browser = pw.chromium.launch(channel='chrome', headless=False)
    page = browser.new_page()
    page.goto(SITE, wait_until='domcontentloaded', timeout=60_000)
    time.sleep(2.5)  # CF clearance settles on the landing navigation
    _SHARED.update(pw=pw, browser=browser, page=page)
    ctx['log']('comick: chrome up, clearance navigated')
    return page


def close() -> None:
    try:
        if _SHARED['browser']:
            _SHARED['browser'].close()
        if _SHARED['pw']:
            _SHARED['pw'].stop()
    except Exception:  # noqa: BLE001
        pass
    _SHARED.update(browser=None, page=None, pw=None)


def _fetch(page, url: str) -> object:
    page.goto(url, wait_until='domcontentloaded', timeout=60_000)
    time.sleep(NAV_GAP)
    body = page.inner_text('body')
    return json.loads(body)


def _rows(payload) -> list[dict]:
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict):
        for k in ('data', 'chapters', 'result', 'items'):
            if isinstance(payload.get(k), list):
                return payload[k]
    return []


def _resolve(work: dict, page, ctx: dict) -> str | None:
    """SeriesMatcher-shaped pick: title sim + year + chapter-count
    proximity decide; ambiguity returns None, never a guess."""
    q = urllib.parse.quote(work['title'] or '')
    try:
        payload = _fetch(page, f'{API}/v1.0/search?q={q}')
    except Exception as e:  # noqa: BLE001
        ctx['log'](f'comick: search failed — {e}')
        return None
    cands = _rows(payload)
    if not cands:
        ctx['log']('comick: no search hits')
        return None

    want_ch = _num(work.get('total_chapters'))
    scored = []
    for c in cands[:12]:
        title = c.get('title') or c.get('name') or ''
        sim = title_sim(title, work['title'])
        year_ok = (c.get('year') and work.get('year')
                   and abs(int(c['year']) - int(work['year'])) <= 1)
        last_ch = _num(c.get('last_chapter') or c.get('chapter_count'))
        # graded proximity — nearer totals outscore; splits same-year
        # dupes like OPM's 241 vs 209 against a 238 canonical total.
        ch_score = 0.0
        if want_ch and last_ch:
            ch_score = 0.15 * max(0.0, 1 - abs(last_ch - want_ch) / max(want_ch * 0.3, 10))
        score = sim + (0.15 if year_ok else 0) + ch_score
        scored.append((score, c))
    scored.sort(key=lambda t: -t[0])
    best = scored[0]
    if best[0] < 0.55:
        ctx['log'](f"comick: weak candidates (best={best[0]:.2f} "
                   f"{best[1].get('title')!r}) — skipped")
        return None
    if len(scored) > 1 and scored[1][0] >= best[0] - 0.02:
        ctx['log']('comick: ambiguous candidates — skipped')
        return None
    hid = best[1].get('hid') or best[1].get('slug')
    ctx['log'](f"comick: hid={hid} ({best[1].get('title')!r}, score={best[0]:.2f})")
    return hid


def collect(work: dict, ctx: dict) -> list[dict]:
    page = _browser(ctx)
    hid = _resolve(work, page, ctx)
    if not hid:
        return []

    votes: list[dict] = []
    for page_no in range(1, MAX_PAGES + 1):
        url = (f'{API}/comic/{hid}/chapters?lang=en&limit={PAGE_LIMIT}'
               f'&page={page_no}')
        try:
            payload = _fetch(page, url)
        except Exception as e:  # noqa: BLE001
            ctx['log'](f'comick: page {page_no} failed — {e}')
            break
        rows = _rows(payload)
        if not rows:
            break
        for r in rows:
            ch = _num(r.get('chap') if 'chap' in r else r.get('chapter'))
            vol = _num(r.get('vol') if 'vol' in r else r.get('volume'))
            if ch is None or vol is None:
                continue
            grp = r.get('group_name')
            if isinstance(grp, list):
                grp = ','.join(sorted(str(g) for g in grp))
            votes.append({'chapter': ch, 'volume': vol,
                          'source': 'comick', 'group': grp or None})
        if len(rows) < PAGE_LIMIT:
            break
    ctx['log'](f'comick: {len(votes)} chapter votes')
    return votes


def _num(v) -> float | None:
    try:
        return float(v)
    except (TypeError, ValueError):
        return None
