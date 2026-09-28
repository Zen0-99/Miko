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
