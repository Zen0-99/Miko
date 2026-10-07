"""Shared plumbing for the volume harvester (Phase 21-02 / NCM-016).

D1 access goes through `wrangler d1 execute --remote --json` — no local
DB replica, no extra deps. The seed push is the exact POST the worker's
trusted-minter lane expects; the harvester identity lives in
harvest.config.json (gitignored — run `volume_harvest.py --enroll`).
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import urllib.error

HERE = os.path.dirname(os.path.abspath(__file__))
WORKER_DIR = os.path.dirname(HERE)
CONFIG_PATH = os.path.join(HERE, 'harvest.config.json')
CACHE_DIR = os.path.join(HERE, '.cache')

WORKER_BASE = 'https://miko-comments.keypop3750.workers.dev'
UA = 'MikoHarvest/1.0 (local seeder)'

# ---------------------------------------------------------------- config

def load_config() -> dict:
    if not os.path.exists(CONFIG_PATH):
        sys.exit('missing harvest.config.json — run: python volume_harvest.py --enroll')
    with open(CONFIG_PATH, encoding='utf-8') as f:
        cfg = json.load(f)
    cfg.setdefault('worker_base', WORKER_BASE)
    return cfg


def save_config(cfg: dict) -> None:
    with open(CONFIG_PATH, 'w', encoding='utf-8') as f:
        json.dump(cfg, f, indent=2)

# ---------------------------------------------------------------- http

def http_json(url: str, timeout: int = 30, retries: int = 2):
    last = None
    for attempt in range(retries + 1):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': 'application/json'})
            return json.loads(urllib.request.urlopen(req, timeout=timeout).read())
        except Exception as e:  # noqa: BLE001 — lanes report, never crash the run
            last = e
            time.sleep(1.5 * (attempt + 1))
    raise last  # type: ignore[misc]

# ---------------------------------------------------------------- d1

def d1(sql: str, remote: bool = True) -> list[dict]:
    """wrangler d1 execute → rows. `--json` yields [{results:[...]}]."""
    import shutil
    npx = shutil.which('npx') or shutil.which('npx.cmd') or 'npx'
    cmd = [npx, 'wrangler', 'd1', 'execute', 'miko-comments',
           '--json', '--command', sql]
    cmd.append('--remote' if remote else '--local')
    out = subprocess.run(cmd, cwd=WORKER_DIR, capture_output=True, text=True,
                         encoding='utf-8', errors='replace', timeout=300)
    if out.returncode != 0:
        raise RuntimeError(f'd1 failed: {out.stderr.strip()[:400]}')
    payload = json.loads(out.stdout)
    rows: list[dict] = []
    for block in payload:
        rows.extend(block.get('results') or [])
    return rows

# ---------------------------------------------------------------- catalog reads

def get_work(work_id: str) -> dict | None:
    rows = d1(
        "SELECT miko_work_id, title, romanized_title, native_title, year, "
        "total_chapters, author, popularity, final_volume, covers_count, type, medium "
        f"FROM works_cache WHERE miko_work_id = '{sql_esc(work_id)}'"
    )
    return rows[0] if rows else None


def top_volume_works(limit: int) -> list[dict]:
    return d1(
        "SELECT miko_work_id, title, year, total_chapters, final_volume "
        "FROM works_cache WHERE miko_work_id LIKE 'mk:mb:%' "
        "AND final_volume IS NOT NULL "
        f"ORDER BY popularity ASC LIMIT {int(limit)}"
    )


def queued_works() -> list[dict]:
    rows = d1(
        "SELECT w.miko_work_id, w.title, w.year, w.total_chapters, w.final_volume "
        "FROM volume_seed_queue q JOIN works_cache w ON w.miko_work_id = q.work_id"
    )
    return rows


def feed_rows(work_id: str) -> list[dict]:
    return d1(
        "SELECT source_key, feed_json FROM canonical_feeds "
        f"WHERE work_id = '{sql_esc(work_id)}'"
    )


def mangadex_uuid(work_id: str) -> str | None:
    rows = d1(
        "SELECT external_id FROM external_ids "
        f"WHERE provider = 'mangadex' AND miko_work_id = '{sql_esc(work_id)}' LIMIT 1"
    )
    return rows[0]['external_id'] if rows else None


def sql_esc(s: str) -> str:
    return s.replace("'", "''")

# ---------------------------------------------------------------- seed push

def api_get(path: str, token: str | None = None) -> dict:
    headers = {'User-Agent': UA}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    req = urllib.request.Request(f'{WORKER_BASE}{path}', headers=headers)
    return json.loads(urllib.request.urlopen(req, timeout=30).read())


def live_boundaries(work_id: str, token: str | None = None) -> tuple[dict, float | None]:
    """Current live map + mean confidence (None when unmapped)."""
    body = api_get(f'/v1/works/{urllib.parse.quote(work_id, safe="")}/volumes', token)
    bounds = body.get('boundaries') or {}
    return bounds, body.get('boundary_confidence')


def seed_push(work_id: str, payload: dict, token: str) -> dict:
    data = json.dumps(payload).encode()
    req = urllib.request.Request(
        f'{WORKER_BASE}/v1/works/{urllib.parse.quote(work_id, safe="")}/volumes/seed',
        data=data, method='POST',
        headers={'User-Agent': UA, 'Content-Type': 'application/json',
                 'Authorization': f'Bearer {token}'},
    )
    try:
        return json.loads(urllib.request.urlopen(req, timeout=60).read())
    except urllib.error.HTTPError as e:
        return {'error': e.read().decode()[:200], 'status': e.code}

# ---------------------------------------------------------------- text utils

_WS = re.compile(r'\s+')
_PUNCT = re.compile(r'[^\w\s]')

def norm_title(t: str | None) -> str:
    if not t:
        return ''
    t = t.lower().strip()
    t = _PUNCT.sub(' ', t)
    return _WS.sub(' ', t).strip()


def title_sim(a: str, b: str) -> float:
    import difflib
    return difflib.SequenceMatcher(None, norm_title(a), norm_title(b)).ratio()
