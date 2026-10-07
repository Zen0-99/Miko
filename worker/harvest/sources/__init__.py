"""Source lanes for the volume harvester.

Each lane exposes `collect(work: dict, ctx: dict) -> list[dict]` where a
vote is {'chapter': float, 'volume': float, 'source': str, 'group': str?}.
Lanes must never raise on missing data — return [] and log why.
"""
