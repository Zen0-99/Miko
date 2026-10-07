# volume harvester (Phase 21-02 / NCM-016)

Local-only multi-source chapter→volume consensus gatherer. Nothing in
this directory ships inside the worker bundle — it produces seed
payloads and POSTs them through `/v1/works/:id/volumes/seed`, the same
trusted-minter lane 21-01 exposed.

## Setup

```bash
pip install requests duckdb mwparserfromhell patchright
patchright install chrome        # one-time browser bits
python volume_harvest.py --enroll   # registers a device, prints the
                                    # trusted_minters INSERT to run once
```

`harvest.config.json` holds the device hash + bearer token (gitignored).

## Run

```bash
python volume_harvest.py --work mk:mb:270 --dry   # merge + report only
python volume_harvest.py --work mk:mb:270         # push for real
python volume_harvest.py --top 500                # popularity-ranked sweep
python volume_harvest.py --queue                  # drain volume_seed_queue
python volume_harvest.py --work X --lanes comick,feed_markers
```

## Lanes (votes into merge.py)

| lane | data | notes |
|---|---|---|
| `feed_markers` | `Vol.N` inside canonical_feeds chapter names | free, always on |
| `mangabaka` | `works.inc_chapters` | schema-ready, mostly null today |
| `mangadex_dump` | geospiza `chapter.parquet` (one-time download) | needs `mangadex` external_id on the work |
| `comick` | api.comick.dev via Patchright headed Chrome | **nav-only fetch, ≥1.6s spacing** — XHR is endpoint-hardened |
| `wiki` | MediaWiki wikitext `{{Graphic novel list}}` blocks | wikipedia + `{slug}.fandom.com`; mirrors share the wikitext |
| `cubari` | GitHub cubari repos `chapters[n].volume` | best-effort, thin search quota |

## Merge semantics

Per chapter: plurality wins; confidence = winner/total; ties break
toward the canonical feed's own numbering; constant-offset divergent
lanes (Viz-vs-tankōbon) are reported, never flattened; all contributing
sources stay in `sources[]`. Push is refused when it would lower the
live map's mean confidence.

ComicK is the load-bearing lane for licensed works (MangaDex is purged
there) — keep its spacing honest; a flagged IP kills the run.
