"""Consensus merge (Phase 21-02 / NCM-016).

Per (work, chapter): every source lane casts votes {chapter, volume,
source, group?}. Rules locked in 21-02-PLAN:

- A (source, group, chapter, volume) tuple votes once — a scanlator
  echo inside one lane is not independent evidence.
- Winner = plurality. Ties prefer the volume feed_markers agreed on
  (the canonical feed's own numbering is the reference scheme).
- confidence = votes_for_winner / total_votes.
- Scheme-divergent sources (consistent constant offset vs the winner
  map, e.g. Viz-vs-Tankōbon) are flagged in the report, not flattened —
  their votes still count, but the winner's `scheme` records the
  feed-aligned scheme name.
- Losing votes persist per-boundary in `sources[]` (all contributing
  lanes are listed — provenance, not just winners).
"""
from __future__ import annotations

from collections import defaultdict
from statistics import median

REFERENCE_SOURCE = 'feed_markers'


def merge_votes(votes: list[dict], feed_volumes: dict[float, float] | None = None
                ) -> dict:
    """votes: [{'chapter':f, 'volume':f, 'source':s, 'group':g?}] →
    {boundaries, mean_confidence, scheme, report}."""
    # --- dedupe: one vote per (source, group, chapter) → plurality of its own
    per_chapter: dict[float, list[dict]] = defaultdict(list)
    seen: set[tuple] = set()
    for v in votes:
        key = (v['source'], v.get('group') or '', v['chapter'], v['volume'])
        if key in seen:
            continue
        seen.add(key)
        per_chapter[v['chapter']].append(v)

    boundaries = []
    divergent_sources: dict[str, float] = {}
    conf_sum = 0.0

    for chapter in sorted(per_chapter):
        rows = per_chapter[chapter]
        tally: dict[float, list[dict]] = defaultdict(list)
        for v in rows:
            tally[v['volume']].append(v)

        # plurality + feed-reference tie-break
        ranked = sorted(
            tally.items(),
            key=lambda kv: (
                -len(kv[1]),
                0 if (feed_volumes or {}).get(chapter) == kv[0] else 1,
                kv[0],
            ),
        )
        win_vol, win_rows = ranked[0]
        conf = len(win_rows) / len(rows)
        conf_sum += conf
        sources = sorted({v['source'] for v in rows})
        boundaries.append({
            'chapter': chapter,
            'volume': win_vol,
            'confidence': round(conf, 4),
            'scheme': 'tankobon',
            'sources': sources,
        })

    mean_conf = round(conf_sum / len(boundaries), 4) if boundaries else None

    # --- scheme-divergence detection: per-source constant offset vs winners
    win_map = {b['chapter']: b['volume'] for b in boundaries}
    by_source: dict[str, list[float]] = defaultdict(list)
    for v in votes:
        if v['chapter'] in win_map:
            by_source[v['source']].append(v['volume'] - win_map[v['chapter']])
    for src, deltas in by_source.items():
        if len(deltas) < 4:
            continue
        med = median(deltas)
        if abs(med) >= 0.5 and sum(1 for d in deltas if abs(d - med) < 0.25) / len(deltas) >= 0.8:
            divergent_sources[src] = med

    report = {
        'chapters_mapped': len(boundaries),
        'mean_confidence': mean_conf,
        'votes': len(votes),
        'distinct_votes': len(seen),
        'divergent_sources': {s: f'{d:+.1f}' for s, d in divergent_sources.items()},
        'confidence_histogram': histogram([b['confidence'] for b in boundaries]),
    }
    return {'boundaries': boundaries, 'mean_confidence': mean_conf,
            'scheme': 'tankobon', 'report': report}


def histogram(confs: list[float]) -> dict[str, int]:
    buckets = {'1.0': 0, '0.9': 0, '0.8': 0, '0.7': 0, '0.6': 0, '0.5': 0, '<0.5': 0}
    for c in confs:
        if c >= 1.0:
            buckets['1.0'] += 1
        elif c >= 0.5:
            for edge in (0.9, 0.8, 0.7, 0.6, 0.5):
                if c >= edge - 1e-9:
                    buckets[str(edge)] += 1
                    break
        else:
            buckets['<0.5'] += 1
    return buckets
