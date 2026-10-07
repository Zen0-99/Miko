"""mangabaka lane — works.inc_chapters opportunist.

MB work records carry an `inc_chapters` field for the physical edition's
chapter inclusion list. It's null nearly everywhere today (verified on
Naruto), but the schema is real — when populated it's the most
authoritative boundary source. This lane is pure opportunist: fetch
works, emit votes only where inc_chapters parses.
"""
from __future__ import annotations

import re

from common import http_json

MB = 'https://api.mangabaka.org'
MB_ID = re.compile(r'^mk:mb:(\d+)$')


def _inc_votes(inc, volume: float, votes: list[dict]) -> int:
    """inc_chapters shapes seen/anticipated: explicit number lists,
    'a-b' range strings, or {chapters:[..]} wrappers. Unknown shapes log
    and skip — never guessed."""
    n = 0
    items = inc
    if isinstance(inc, dict):
        items = inc.get('chapters') or inc.get('list') or []
    if isinstance(items, (int, float, str)):
        items = [items]
    for it in items or []:
        if isinstance(it, (int, float)):
            votes.append({'chapter': float(it), 'volume': volume,
                          'source': 'mangabaka', 'group': None})
            n += 1
        elif isinstance(it, str):
            m = re.match(r'^\s*([0-9]+(?:\.[0-9]+)?)\s*[-–]\s*([0-9]+(?:\.[0-9]+)?)\s*$', it)
            if m:
                a, b = float(m.group(1)), float(m.group(2))
                ch = a
                while ch <= b:
                    votes.append({'chapter': ch, 'volume': volume,
                                  'source': 'mangabaka', 'group': None})
                    ch += 1
                    n += 1
            else:
                try:
                    votes.append({'chapter': float(it), 'volume': volume,
                                  'source': 'mangabaka', 'group': None})
                    n += 1
                except ValueError:
                    continue
    return n


def collect(work: dict, ctx: dict) -> list[dict]:
    m = MB_ID.match(work.get('miko_work_id') or '')
    if not m:
        return []
    mb_id = m.group(1)
    votes: list[dict] = []
    page = 1
    while page <= 40:
        try:
            body = http_json(f'{MB}/v1/series/{mb_id}/works?page={page}')
        except Exception as e:  # noqa: BLE001
            ctx['log'](f'mangabaka: works page {page} failed — {e}')
            break
        rows = body.get('data') if isinstance(body, dict) else None
        if not rows:
            break
        for w in rows:
            vol = w.get('sequence_numeric')
            inc = w.get('inc_chapters')
            if vol is None or inc in (None, [], ''):
                continue
            got = _inc_votes(inc, float(vol), votes)
            if got:
                ctx['log'](f'mangabaka: work {w.get("id")} vol {vol} '
                           f'→ {got} inc_chapters votes')
        nxt = (body.get('pagination') or {}).get('next')
        if not nxt:
            break
        page += 1
    return votes
