"""feed_markers lane — Vol.N already inside canonical feed chapter names.

The cheapest vote: canonical_feeds rows are data we already own. Chapter
entries may carry a structured `volume` field or embed "Vol.N"/"VN"/
"Book N" in the display name — same regex family as the worker's
chapterparse.ts. group=source_key so each feed votes once per chapter;
a feed repeating itself inside its own list still counts once.
"""
from __future__ import annotations

import json
import re

from common import feed_rows

# Ported from worker/src/chapterparse.ts marker family (volume arm only).
RE_VOLUME = re.compile(r'\bvol(?:ume)?s?\.?\s*([0-9]+(?:\.[0-9]+)?)', re.I)
RE_VOLUME_V = re.compile(r'\bv([0-9]+)\b')
RE_COMPACT = re.compile(r'\bv([0-9]+)\s*c([0-9]+(?:\.[0-9]+)?)\b')
RE_BOOK = re.compile(r'\bbook\s*([0-9]+)', re.I)
RE_CHAPTER = re.compile(
    r'\b(?:chapter|chap|ch|episode|ep)\.?\s*([0-9]+(?:\.[0-9]+)?)', re.I)
RE_LEAD_NUM = re.compile(r'^\s*([0-9]+(?:\.[0-9]+)?)\s*[:.\-— ]')


def _marker_volume(name: str) -> float | None:
    m = RE_COMPACT.search(name)
    if m:
        return float(m.group(1))
    for rx in (RE_VOLUME, RE_VOLUME_V, RE_BOOK):
        m = rx.search(name)
        if m:
            return float(m.group(1))
    return None


def _chapter_number(name: str) -> float | None:
    m = RE_CHAPTER.search(name)
    if m:
        return float(m.group(1))
    m = RE_LEAD_NUM.search(name)
    if m:
        return float(m.group(1))
    return None


def collect(work: dict, ctx: dict) -> list[dict]:
    votes: list[dict] = []
    try:
        rows = feed_rows(work['miko_work_id'])
    except Exception as e:  # noqa: BLE001
        ctx['log'](f"feed_markers: d1 read failed — {e}")
        return votes

    for row in rows:
        try:
            chapters = json.loads(row['feed_json'])
        except Exception:  # noqa: BLE001
            continue
        if not isinstance(chapters, list):
            continue
        seen: set[tuple] = set()
        for ch in chapters:
            if not isinstance(ch, dict):
                continue
            name = ch.get('name') or ''
            num = ch.get('number')
            if num is None:
                num = _chapter_number(name)
            vol = ch.get('volume')
            if vol is None:
                vol = _marker_volume(name)
            if num is None or vol is None:
                continue
            key = (float(num), float(vol))
            if key in seen:
                continue
            seen.add(key)
            votes.append({'chapter': float(num), 'volume': float(vol),
                          'source': 'feed_markers', 'group': row['source_key']})
    return votes


def feed_numbering(work: dict, ctx: dict) -> dict[float, float]:
    """The canonical feed's own chapter→volume where markers exist —
    the reference scheme merge_votes breaks ties toward."""
    return {v['chapter']: v['volume'] for v in collect(work, ctx)}
