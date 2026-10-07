"""mangadex_dump lane — geospiza.me MangaDex chapter parquet.

One-time bulk download into .cache/ (duckdb filters in place — the dump
covers all of MangaDex, we only need rows for works whose canonical
identity already carries a `mangadex` external_id). Snapshot is static
(2022 scrape): stale data is fine, it's one vote among several.

Schema: id.chapter, id.volume, id.translatedLanguage,
relationships.manga (uuid), relationships.scanlation_group.
"""
from __future__ import annotations

import os
import urllib.request

from common import CACHE_DIR, UA, mangadex_uuid

PARQUET_URL = 'https://manga-recsys.geospiza.me/api/v1/data/chapter.parquet'
PARQUET_PATH = os.path.join(CACHE_DIR, 'chapter.parquet')


def ensure_cached(ctx: dict) -> str | None:
    if os.path.exists(PARQUET_PATH):
        return PARQUET_PATH
    os.makedirs(CACHE_DIR, exist_ok=True)
    ctx['log']('mangadex_dump: downloading chapter.parquet (one-time, large)…')
    try:
        req = urllib.request.Request(PARQUET_URL, headers={'User-Agent': UA})
        with urllib.request.urlopen(req, timeout=600) as r, \
                open(PARQUET_PATH + '.part', 'wb') as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
        os.replace(PARQUET_PATH + '.part', PARQUET_PATH)
        return PARQUET_PATH
    except Exception as e:  # noqa: BLE001
        ctx['log'](f'mangadex_dump: download failed — {e}')
        try:
            os.remove(PARQUET_PATH + '.part')
        except OSError:
            pass
        return None


def collect(work: dict, ctx: dict) -> list[dict]:
    uuid = mangadex_uuid(work['miko_work_id'])
    if not uuid:
        ctx['log']('mangadex_dump: no mangadex external_id — skipped')
        return []
    path = ensure_cached(ctx)
    if not path:
        return []
    try:
        import duckdb
        con = duckdb.connect()
        rel = con.execute(
            "SELECT attributes.chapter AS ch, attributes.volume AS vol, "
            "       relationships.scanlation_group AS grp "
            f"FROM read_parquet('{path.replace(os.sep, '/')}') "
            "WHERE relationships.manga = ? "
            "  AND (attributes.translatedLanguage = 'en' "
            "       OR attributes.translatedLanguage IS NULL)",
            [uuid],
        ).fetchall()
        con.close()
    except Exception as e:  # noqa: BLE001
        ctx['log'](f'mangadex_dump: query failed — {e}')
        return []

    votes: list[dict] = []
    for ch, vol, grp in rel:
        try:
            c = float(ch)
            v = float(vol)
        except (TypeError, ValueError):
            continue
        votes.append({'chapter': c, 'volume': v,
                      'source': 'mangadex', 'group': grp or None})
    return votes
