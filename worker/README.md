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
  rows flagged for transparency.

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

Configure under **Security → WAF → Rate limiting rules** on the
`miko-comments` zone/route. This is what actually prices a multi-device
sybil attack; `bindings.rate.*` in `ruleset.json` is the soft in-worker
complement, not the ceiling.

### Bundled mint telemetry

`POST /v1/ids/seen`, `POST /v1/works/resolve`, and both canonize routes
accept an optional `telemetry: [{pkg, served?, failed?}]` array
(≤ `telemetry.max_entries`, 50). Counts upsert into
`provider_stats.served_count`/`failed_count` — observational only, never
an input to lifecycle scores, tiers, or mint/solidify counters.
Malformed entries clamp out and over-cap batches drop whole; telemetry
can never fail its carrier request.
