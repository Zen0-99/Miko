"""wiki lane — Wikipedia/Fandom/mirror chapter-list tables.

MediaWiki `action=parse&prop=wikitext` on "List of X chapters" articles
returns wikitext whose {{Graphic novel list}} blocks carry
|VolumeNumber=N + |ChapterList= with `#` items anchored by
<li value="START">. en-academic/wikiwand/ultimatepopculture serve the
same wikitext family — one extractor covers them all.

Chapter→volume rule: within a VolumeNumber block, chapters run
sequentially from the first <li value> (or continue the running counter
when the anchor is absent). Best-effort: unparsed blocks are logged,
never guessed.
"""
from __future__ import annotations

import re
import urllib.parse

from common import http_json, norm_title

WP_API = 'https://en.wikipedia.org/w/api.php'

RE_GNL = re.compile(r'\{\{\s*Graphic novel list\b', re.I)
RE_VOLNUM = re.compile(r'\|\s*VolumeNumber\s*=\s*([0-9]+(?:\.[0-9]+)?)', re.I)
RE_CHLIST = re.compile(r'\|\s*ChapterList\s*=', re.I)
RE_LI_VALUE = re.compile(r'<li\s+value="([0-9]+)"', re.I)


def _mediawiki_get(host_api: str, params: dict) -> dict | None:
    params = {**params, 'format': 'json', 'formatversion': '2'}
    url = f"{host_api}?{urllib.parse.urlencode(params)}"
    try:
        return http_json(url)
    except Exception:  # noqa: BLE001
        return None


def _candidate_pages(title: str) -> list[str]:
    """Static guesses + opensearch discovery — split articles
    ('List of Naruto chapters (Part II, volumes 28–48)') only surface
    through search."""
    base = title.strip()
    cands = [
        f'List of {base} chapters',
        f'List of {base} volumes',
        f'{base} chapters',
        f'List of {base} chapters (Part I)',
        f'List of {base} chapters (Part II)',
    ]
    try:
        data = http_json(
            WP_API + '?' + urllib.parse.urlencode({
                'action': 'opensearch', 'search': f'List of {base} chapters',
                'limit': '10', 'namespace': '0',
            }))
        for t in (data[1] if isinstance(data, list) and len(data) > 1 else []):
            # Sibling-series articles ("List of Boruto chapters") share
            # numbering schemes — keep only pages naming the work itself.
            if norm_title(title) in norm_title(t) and t not in cands:
                cands.append(t)
    except Exception:  # noqa: BLE001
        pass
    return cands


def _blocks(wikitext: str):
    """Yield (volume_number, chapter_list_text) per GNL block."""
    for m in RE_GNL.finditer(wikitext):
        # Block ends at the closing }} at depth 0 — brace counting
        depth = 0
        i = m.start()
        end = len(wikitext)
        while i < len(wikitext) - 1:
            if wikitext[i:i + 2] == '{{':
                depth += 1
                i += 2
                continue
            if wikitext[i:i + 2] == '}}':
                depth -= 1
                i += 2
                if depth == 0:
                    end = i
                    break
                continue
            i += 1
        block = wikitext[m.start():end]
        vm = RE_VOLNUM.search(block)
        if not vm:
            continue
        cm = RE_CHLIST.search(block)
        chapter_text = ''
        if cm:
            # ChapterList value runs to the next top-level "|" param or block end
            tail = block[cm.end():]
            stop = re.search(r'\n\|\s*[A-Za-z]', tail)
            chapter_text = tail[:stop.start()] if stop else tail
        yield float(vm.group(1)), chapter_text


RE_NUMBERED = re.compile(r'\{\{\s*Numbered list\s*\|', re.I)
RE_START = re.compile(r'\bstart\s*=\s*([0-9]+)', re.I)


def _chapter_count(chapter_text: str) -> tuple[int, float | None]:
    """(item count, first chapter number) — two wikitext shapes:
    '#'-prefixed lists anchored by <li value=N> (Naruto), and
    {{Numbered list|start=N|item|item…}} blocks (One-Punch Man)."""
    items = re.findall(r'(?m)^\s*#', chapter_text)
    if items:
        li = RE_LI_VALUE.search(chapter_text)
        return len(items), (float(li.group(1)) if li else None)
    nm = RE_NUMBERED.search(chapter_text)
    if nm:
        block = chapter_text[nm.start():]
        # item lines look like "| {{Nihongo|…}}" — a bare "|" column.
        start = RE_START.search(block[:200])
        entries = [ln for ln in block.splitlines()
                   if re.match(r'^\s*\|\s*\{\{', ln)]
        return len(entries), (float(start.group(1)) if start else None)
    return 0, None


def collect(work: dict, ctx: dict) -> list[dict]:
    votes: list[dict] = []
    title = work.get('title') or ''
    found_any = False

    for page_name in _candidate_pages(title):
        # allow manual override titles (e.g. "Naruto" → Part I/II splits)
        data = _mediawiki_get(WP_API, {
            'action': 'parse', 'page': page_name, 'prop': 'wikitext',
            'redirects': '1',
        })
        wikitext = (((data or {}).get('parse') or {}).get('wikitext')) or ''
        if not wikitext:
            continue
        parsed_title = ((data or {}).get('parse') or {}).get('title', page_name)
        blocks = list(_blocks(wikitext))
        if not blocks:
            ctx['log'](f'wiki: {parsed_title} — no Graphic novel list blocks')
            continue
        found_any = True
        running: float | None = None
        for vol_no, chapter_text in blocks:
            count, anchor = _chapter_count(chapter_text)
            start = anchor if anchor is not None else running
            if start is None or count == 0:
                ctx['log'](f'wiki: {parsed_title} vol {vol_no} — '
                           f'{count} items, no anchor, skipped')
                continue
            for i in range(count):
                votes.append({'chapter': start + i, 'volume': vol_no,
                              'source': 'wiki', 'group': parsed_title})
            running = start + count
        ctx['log'](f'wiki: {parsed_title} — {len(blocks)} volume blocks')

    # Fandom mirrors share the wikitext family — try the obvious host.
    slug = norm_title(title).replace(' ', '')
    if slug:
        data = _mediawiki_get(f'https://{slug}.fandom.com/api.php', {
            'action': 'parse', 'page': f'List of {title} chapters',
            'prop': 'wikitext', 'redirects': '1',
        })
        wikitext = (((data or {}).get('parse') or {}).get('wikitext')) or ''
        if wikitext:
            blocks = list(_blocks(wikitext))
            running = None
            n = 0
            for vol_no, chapter_text in blocks:
                count, anchor = _chapter_count(chapter_text)
                start = anchor if anchor is not None else running
                if start is None or count == 0:
                    continue
                for i in range(count):
                    votes.append({'chapter': start + i, 'volume': vol_no,
                                  'source': 'wiki', 'group': f'fandom:{slug}'})
                    n += 1
                running = start + count
            if n:
                ctx['log'](f'wiki: fandom:{slug} — {n} chapter votes')
        elif not found_any:
            ctx['log']('wiki: no articles found on wikipedia or fandom')
    return votes
