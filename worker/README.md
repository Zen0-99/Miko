# miko-comments worker

Cloudflare Worker + D1 backend for Miko's native comments
(`.planning/phases/08-cloudflare-comments-backend/`). Deploy target:
`miko-comments.keypop3750.workers.dev` on the paid Workers account —
**not** the animestream worker.

## Local dev

```bash
npm install
npm run dev          # wrangler dev --local
npm test             # vitest (workers pool, local D1)
npm run typecheck
```

## Database

```bash
npm run migrate:local            # apply migrations to local D1
wrangler d1 create miko-comments # one-time; paste database_id into wrangler.toml
npm run migrate                  # apply to remote (deploy gate below)
```

## Deploy gate

`wrangler deploy` and remote `d1 migrations apply` touch live
infrastructure — only run after explicit user approval.

## `POST /v1/works/resolve` — book identity (Phase 11)

Request fields beyond the base `title`/`medium`/`provider`/`external_id`/
`author`/`year`:

- `kind: "book"` — marks a published-book source (Anna's Archive).
  Gates the OpenLibrary fallthrough; without it (and without `isbn`),
  a `medium=novel` miss never reaches OpenLibrary, so webnovels can't
  fuzzy-merge into published books.
- `isbn: "…"` — ISBN-10/13. Exact edition→work rollup via
  `/isbn/{isbn}.json` → `works[0].key` → canonical `mk:ol:OL…W`.

Response `work.type` may be `"book"` (OpenLibrary work). External IDs
persisted: `openlibrary` = work id (`OL…W`), `isbn` = every ISBN on the
edition — later edition hits dedupe for free.

Unsafe results (`edition` without a `works[]` rollup, tied search
candidates) return `work:null` and write an `identity_review` row —
no silent bad mints.

Book comments use the existing `novel:` thread prefix
(`novel:mk:ol:OL…W`); no separate medium.

## `POST /v1/chapters/canonize` — feed provenance vocabulary

`feeds[].provenance` is a free-form ≤40-char string stamped onto the
`external_ids` binding row (when `provider_ref` is present — a feed may
carry `chapters: []` and still mint the binding). Accepted values:

| provenance | meaning |
|---|---|
| `device_feed` | default — device-submitted chapter list |
| `user_picked` | a human confirmed the extension result in the Find Provider sheet; `verified=true` lifts `confidence` to ≥0.9 |
| `crawler` | managed crawler host — requires `x-crawler-key`; unauthenticated claims degrade to `device_feed` |
| `mangadex` / `mangabaka` | seed-provenance records, not app-drivable carriers |

Feeds with `verified=false` always write an `identity_review` row
(the pending tier) regardless of provenance.

## Provider trust — binding lifecycle (Phase 20 / migration 0015)

Every `external_ids` binding carries a lifecycle:
`provisional → confirmed | quarantined`.

- A first-party `provider_ref` mint lands **provisional** — served only
  to its minter's Bearer; its feed persists `canonical_feeds.pending=1`
  and stays out of the shared chapter/episode merge (the minter sees it
  via an in-memory overlay on GET).
- A binding **solidifies** once `bindings.confirm_points_required`
  (ruleset v2: 3) distinct devices have re-posted it **and** its ledger
  score (`confirms − reportWeight + ageBonus`) clears `margin_multiplier`
  × the next-best sibling binding on the same `(work, provider)`.
  Solidification publishes the pending feed on the same request's
  remerge.
- **Trusted minters** (`trusted_minters` table) and **authenticated
  crawler** provenance (`x-crawler-key`) mint `confirmed` directly.
  Unauthenticated mints are provisional with a NULL minter — visible to
  nobody until identified devices confirm them.
- `user_picked` raises `confidence`, never `status` — a human pick still
  runs the lifecycle.
- Pre-0015 rows are grandfathered `confirmed` with a `points = 3.0`
  incumbency stamp; resolve-side catalog mints (MangaBaka/OpenLibrary/
  seeds) stay `confirmed_at IS NULL` and floor at
  `confirm_points_required − reportWeight`.
- `GET /v1/works/:id/bindings` lists confirmed rows for everyone,
  provisional rows only to their minter (`mine: true`), and quarantined
  rows flagged for transparency. Bearer callers also get per-row
  `my: {vote, reported, removed}` signal state for the carrier picker's
  toggles.

### Carrier signals (20-05 / NCM-015)

User-facing carrier actions map to four signals:

| Action | Endpoint | Ledger effect | Reversible |
|---|---|---|---|
| upvote | `POST /v1/bindings/vote` `vote:'up'` | +1 `binding_confirms` row | yes (`vote:'clear'`) |
| downvote | `POST /v1/bindings/vote` `vote:'down'` | −`downvote_weight` (flat 0.25), `kind='downvote'` | yes (`'clear'` / `'up'`) |
| report | `POST /v1/bindings/report` `kind:<reason>` | −reporter-age weight, `kind='report'` + `reason` | no |
| remove | `POST /v1/bindings/remove` `removed:bool` | −`remove_weight` (flat 0.25), `kind='remove'` | yes (`removed:false`) |

Up/down share one mutually-exclusive slot — the handler deletes the
opposite signal, so a device can never hold both. Report and remove
**stack** on the vote slot: per-device maximum influence is −1.5 vs
+1 for an upvote (owner-locked 1/1.5). Report `kind` values
(`wrong_work`/`dead_feed`/`low_quality`/`other`) are stored in the
`reason` column — ledger `kind` stays `'report'` so the kind-scoped PK
(`provider, external_id, device_hash, kind`, migration 0016) still
dedupes one report per device. Only `kind='report'` weight counts
toward `report_quarantine_weight` — downvotes/removes erode margin
score, incumbency floor and provider derank sums but can never
quarantine alone.

### Seeding a trusted minter

The allowlist is D1-only — no HTTP admin path by design. A device hash
is the SHA-256 of the app's `ANDROID_ID` (visible in logcat as
`comments_device_hash`):

```bash
wrangler d1 execute miko-comments --command "INSERT INTO trusted_minters (device_hash, added_at) VALUES ('<sha256-of-ANDROID_ID>', <ms>)"
```

### Rate limiting — the real mass-attack cost ceiling

The worker has **no KV / Rate-Limit binding** — in-worker counters would
not survive isolate churn anyway. The locked control is a Cloudflare
dashboard rate-limit rule (owner action, one-time per endpoint):

- `POST /v1/chapters/canonize`
- `POST /v1/episodes/canonize`
- `POST /v1/bindings/report`
- `POST /v1/bindings/unbind`
- `POST /v1/bindings/vote`
- `POST /v1/bindings/remove`

Configure under **Security → WAF → Rate limiting rules** on the
`miko-comments` zone/route. This is what actually prices a multi-device
sybil attack; `bindings.rate.*` in `ruleset.json` is the soft in-worker
complement, not the ceiling.

## Work volumes — covers + consensus boundaries (Phase 21 / migration 0017)

MangaBaka is canonical for `final_volume`, per-volume covers, and edition
metadata (ISBN/release date/pages). Chapter→volume boundaries are a
multi-source consensus ledger — sources vote, majority wins per chapter,
`confidence` = agreeing/total, losing votes stay in `sources`.

- `GET /v1/works/:id/volumes` — unauthenticated read. Returns
  `{final_volume, covers_count, volumes[], boundaries{chapter→volume},
  boundary_confidence, schemes, queued_for_seed}`. A **Bearer** read on a
  work with no volume data enqueues it into `volume_seed_queue` (the
  tail-feed the local harvester drains); anonymous reads never enqueue.
- `POST /v1/works/:id/volumes/seed` — trusted-minter lane. Body:
  `{final_volume?, covers_count?, volumes[], boundaries[]}`.
  Upsert-only — a re-seed may raise confidence but never regresses an
  existing row's confidence (`MAX(confidence)` on conflict). Seeding
  dequeues the work from `volume_seed_queue`.
- `POST /v1/volumes/seed-run` — maintainer trigger behind
  `x-crawler-key` (same gate as the chart refresh). Runs the MangaBaka
  covers/meta seeder over `volume_seed_queue` ∪ top-`limit` works by
  catalog popularity; fills `work_volumes` + `works_cache.final_volume`/
  `covers_count`. Chapter boundaries are NOT filled here — they come
  from the local 21-02 harvester (ComicK-via-Patchright, geospiza dump,
  wiki tables, feed markers) POSTing merged votes to `/volumes/seed`.

Source vocabulary for `volumes[].source` / `boundaries[].sources`:
`mangabaka`, `mangadex`, `comick`, `cubari`, `wiki`, `feed_markers`.

### Bundled mint telemetry

`POST /v1/ids/seen`, `POST /v1/works/resolve`, and both canonize routes
accept an optional `telemetry: [{pkg, served?, failed?}]` array
(≤ `telemetry.max_entries`, 50). Counts upsert into
`provider_stats.served_count`/`failed_count` — observational only, never
an input to lifecycle scores, tiers, or mint/solidify counters.
Malformed entries clamp out and over-cap batches drop whole; telemetry
can never fail its carrier request.
