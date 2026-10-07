"""cubari lane — community Cubari JSON sources on GitHub.

Cubari mirrors embed per-chapter `volume` fields:
    {"chapters": {"12": {"volume": "2", "title": "...", "groups": {...}}}}

Unauthenticated GitHub search is rate-thin (10/min) so this lane is
deliberately cheap: repo search on the normalized title + 'cubari',
then the git tree of each candidate for *.json blobs whose shape looks
like a cubari source. Spotty coverage is expected — it's a bonus vote,
never the backbone.
"""
from __future__ import annotations

import urllib.parse

from common import http_json, norm_title

GH = 'https://api.github.com'


def _gh(path: str):
    return http_json(f'{GH}{path}', timeout=20)


def _extract_votes(doc: dict, votes: list[dict], group: str) -> int:
    chapters = doc.get('chapters')
    if not isinstance(chapters, dict):
        return 0
    n = 0
    for ch_key, ch in chapters.items():
        if not isinstance(ch, dict):
            continue
        vol = ch.get('volume')
        try:
            c = float(ch_key)
            v = float(vol)
        except (TypeError, ValueError):
            continue
        votes.append({'chapter': c, 'volume': v,
                      'source': 'cubari', 'group': group})
        n += 1
    return n


def collect(work: dict, ctx: dict) -> list[dict]:
    votes: list[dict] = []
    title = norm_title(work.get('title'))
    if not title:
        return votes
    try:
        res = _gh('/search/repositories?q=' +
                  urllib.parse.quote(f'{title} cubari') + '&per_page=5')
    except Exception as e:  # noqa: BLE001 — search quota is thin
        ctx['log'](f'cubari: repo search failed — {e}')
        return votes

    for repo in (res.get('items') or [])[:5]:
        full = repo.get('full_name')
        if not full:
            continue
        try:
            tree = _gh(f"/repos/{full}/git/trees/{repo['default_branch']}"
                       '?recursive=1')
        except Exception:  # noqa: BLE001
            continue
        blobs = [t['path'] for t in (tree.get('tree') or [])
                 if t.get('type') == 'blob' and t['path'].endswith('.json')][:8]
        for path in blobs:
            try:
                doc = http_json(
                    f'https://raw.githubusercontent.com/{full}/'
                    f"{repo['default_branch']}/{urllib.parse.quote(path)}",
                    timeout=15)
            except Exception:  # noqa: BLE001
                continue
            if isinstance(doc, dict) and _extract_votes(doc, votes, full):
                ctx['log'](f'cubari: {full}/{path} — votes extracted')
                break
    return votes
