"""volume_harvest.py — Phase 21-02 orchestrator.

Concentrated gather: fans each work out to every boundary lane, merges
votes (merge.py), and pushes the result through the worker's
trusted-minter seed endpoint. Local-only — nothing here ships inside
the worker bundle.

    python volume_harvest.py --enroll           first run: register the
                                                harvester device +
                                                print the trust command
    python volume_harvest.py --work mk:mb:270 --dry
    python volume_harvest.py --top 500          popularity-ranked works
    python volume_harvest.py --queue            drain volume_seed_queue
    python volume_harvest.py --work X           push for real

Flags:
    --dry           merge + report, never POST
    --lanes a,b,c   restrict lanes (default: all six)

Low-confidence maps still push — the client renders them flat; the only
gate is never regressing an existing live map's mean confidence.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys
import time

# cp1252 consoles can't print → and CJK lane names.
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from common import (CACHE_DIR, CONFIG_PATH, WORKER_BASE, api_get,  # noqa: E402
                    get_work, live_boundaries, load_config, queued_works,
                    save_config, seed_push, top_volume_works)
from merge import merge_votes  # noqa: E402
from sources import comick, cubari, feed_markers, mangabaka, mangadex_dump, wiki  # noqa: E402

LANES = {
    'feed_markers': feed_markers,
    'mangabaka': mangabaka,
    'mangadex_dump': mangadex_dump,
    'comick': comick,
    'wiki': wiki,
    'cubari': cubari,
}


def log(msg: str) -> None:
    print(f'  {msg}', flush=True)


def enroll() -> None:
    """Register a dedicated harvester device; print the trust command."""
    import secrets
    import urllib.request
    if os.path.exists(CONFIG_PATH):
        sys.exit(f'config exists at {CONFIG_PATH} — delete it to re-enroll')
    device_hash = secrets.token_hex(32)
    req = urllib.request.Request(
        f'{WORKER_BASE}/v1/device/register', method='POST',
        data=json.dumps({'device_hash': device_hash}).encode(),
        headers={'Content-Type': 'application/json',
                 'User-Agent': 'MikoHarvest/1.0'})
    token = json.loads(urllib.request.urlopen(req, timeout=30).read())['token']
    save_config({'worker_base': WORKER_BASE,
                 'device_hash': device_hash, 'token': token})
    print('registered harvester device.')
    print('trust it once (remote D1):')
    print(f'  npx wrangler d1 execute miko-comments --remote --command '
          f'"INSERT INTO trusted_minters (device_hash, added_at) '
          f"VALUES ('{device_hash}', {int(time.time() * 1000)})\"")
    print('then: python volume_harvest.py --work mk:mb:270 --dry')


def gather(work: dict, lane_names: list[str]) -> list[dict]:
    votes: list[dict] = []
    for name in lane_names:
        lane = LANES[name]
        try:
            got = lane.collect(work, {'log': log})
            votes.extend(got)
            if got:
                log(f'{name}: +{len(got)} votes')
        except Exception as e:  # noqa: BLE001 — one bad lane never stops the work
            log(f'{name}: lane error — {e}')
    return votes


def harvest_work(work_id: str, args, token: str | None) -> dict:
    work = get_work(work_id)
    if not work:
        return {'work_id': work_id, 'error': 'not in works_cache'}
    print(f'\n== {work_id}  {work.get("title")!r} '
          f'(final_volume={work.get("final_volume")}, '
          f'total_chapters={work.get("total_chapters")})')

    votes = gather(work, args.lanes)
    feed_hint = {}
    if 'feed_markers' in args.lanes:
        try:
            feed_hint = feed_markers.feed_numbering(work, {'log': log})
        except Exception:  # noqa: BLE001
            feed_hint = {}

    merged = merge_votes(votes, feed_volumes=feed_hint)
    rep = merged['report']
    print(f'  chapters={rep["chapters_mapped"]} votes={rep["distinct_votes"]}'
          f'/{rep["votes"]} mean_conf={rep["mean_confidence"]}')
    print(f'  histogram: {rep["confidence_histogram"]}')
    if rep['divergent_sources']:
        print(f'  scheme divergence: {rep["divergent_sources"]}')

    if args.dry:
        # first/last few boundaries for eyeballing
        for b in merged['boundaries'][:3] + merged['boundaries'][-3:]:
            print(f"    ch{b['chapter']:g} → vol{b['volume']:g} "
                  f"conf={b['confidence']} {b['sources']}")
        return {'work_id': work_id, 'dry': True, 'report': rep}

    if not merged['boundaries']:
        return {'work_id': work_id, 'skipped': 'no votes'}

    # Refusal rule: never regress an existing live map.
    live_map, live_conf = live_boundaries(work_id, token)
    if live_map and live_conf is not None and \
            (merged['mean_confidence'] or 0) < live_conf - 1e-9:
        print(f'  REFUSED — live conf {live_conf} > new {merged["mean_confidence"]}')
        return {'work_id': work_id, 'refused': True,
                'live_conf': live_conf, 'new_conf': merged['mean_confidence']}

    payload = {'boundaries': merged['boundaries']}
    res = seed_push(work_id, payload, token)
    print(f'  push → {res}')
    return {'work_id': work_id, 'pushed': res, 'report': rep}


def main() -> None:
    ap = argparse.ArgumentParser(description='volume boundary harvester')
    ap.add_argument('--enroll', action='store_true')
    ap.add_argument('--work')
    ap.add_argument('--top', type=int)
    ap.add_argument('--queue', action='store_true')
    ap.add_argument('--dry', action='store_true')
    ap.add_argument('--lanes', default=','.join(LANES))
    args = ap.parse_args()
    args.lanes = [s.strip() for s in args.lanes.split(',') if s.strip() in LANES]

    if args.enroll:
        enroll()
        return
    os.makedirs(CACHE_DIR, exist_ok=True)

    cfg = load_config() if not args.dry else {}
    token = cfg.get('token')

    works: list[dict] = []
    if args.work:
        works = [{'miko_work_id': args.work}]
    elif args.top:
        works = top_volume_works(args.top)
    elif args.queue:
        works = queued_works()
    else:
        ap.error('pick --work, --top N, or --queue')

    results = []
    try:
        for w in works:
            results.append(harvest_work(w['miko_work_id'], args, token))
    finally:
        comick.close()

    out = os.path.join(CACHE_DIR, f'run-{int(time.time())}.json')
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=2)
    mapped = sum(1 for r in results if r.get('report', {}).get('chapters_mapped'))
    print(f'\ndone — {mapped}/{len(results)} works mapped; report → {out}')


if __name__ == '__main__':
    main()
