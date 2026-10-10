# iOS JS Extension Repos — Design

**Date:** 2026-10-10
**Status:** Approved (user)
**Approach:** Shared pipeline in `commonMain` (approach A), Android code untouched this slice.

## Goal

Mirror Android's "add GitHub repo → browse extensions → install" flow on iOS
through shared code. A user adds `mikoDB`, it registers under every content type
its JS index serves, plugins appear in the Extensions list, and installing one
feeds the existing `IosNovelSourceManager` → `NovelCatalogueSource` bridge.

Locked decisions:

- JS index entries gain an optional **`kind`** field; APK `index.pb` stays
  untouched for Android and is never read on iOS.
- UX is a **mirror of Android's BrowseTab** — BROWSE|EXTENSIONS pill, per-type
  consolidated repos screen, no iOS-specific divergence.
- All parsing/fetching/installing lives in `commonMain`; `IosExtensionRepoService`
  is the only iosMain piece; Android keeps its `app/` parser until a later
  delegation cleanup.

## Existing reusable infra (verified)

- `ExtensionRepo` model + `*ExtensionRepoRepository` ×3 + create/delete/get
  interactors — all `domain/commonMain`, all three DB handlers wired in `AppGraph`
  (`mangaDatabase`, `animeDatabase`, `novelDatabase`).
- `CreateNovelExtensionRepo` (and anime/manga siblings) already strip
  `plugins.min.json`/`plugins.json`/`index.json` suffixes and synthesize a
  `NOFINGERPRINT-sha256` fingerprint when `fetchRepoDetails` returns null —
  works for metadata-less JS plugin repos unchanged.
- `NovelPluginRepository` + `NovelPluginInstaller` + `NovelPluginStorage` —
  commonMain; installer downloads script + custom JS/CSS, validates SHA-256.
- `IosNovelSourceManager` — subscribes installed plugins → `IosNovelJsSource`
  (lazy JSCore runtime); source IDs match Android via `NovelPluginId`.
- `platformHttpClient()` — Ktor Darwin client, already in `AppGraph`.
- `toHttpUrlOrNull` — iosMain actual exists; interactors compile as-is.

## Design

### domain/commonMain

**`JsPluginIndexParser`** (`tachiyomi.domain.extension.novel.api`,
domain/commonMain): port of `app/`'s `NovelPluginIndexParser` minus OkHttp:

- Parses plugin-index JSON array → `List<NovelPlugin.Available>`.
- JS entries: `id`, `name`, `site`, `lang`, `version`/`code`, `url`, `iconUrl`,
  `customJS`, `customCSS`, `hasSettings`, `sha256`, `nsfw`, plus new optional
  **`kind`**, normalized at parse: `novel`/`book`→`novel`, `manga`→`manga`,
  `anime`/`show`/`shows`→`anime`; absent or unrecognized → `novel`
  (LNReader-compatible default).
- APK-shaped entries (`pkg`/`apk`, `isNovel: true`) are **ignored** — iOS never
  installs binaries.
- Relative URLs (`url`, `iconUrl`, `customJS`, `customCSS`) resolved against the
  repo base URL via a string-based `resolveRelative(base, ref)` helper.

**`NovelPlugin.kind`** — new `kind: String` field on `Available` and `Installed`,
default `"novel"`.

**`IosExtensionRepoService`** (domain/iosMain — only iosMain piece):

- `fetchRepoDetails`: GET `$baseUrl/repo.json` → `ExtensionRepoMetaDto` →
  `ExtensionRepo`. No `index.pb` fallback — APK metadata is meaningless on iOS.
- `probeRepoTypes`: fetch the plugin index via shared fetcher, parse `kind`s →
  `{novel, manga, anime}` subset; all-absent `kind` → `{novel}` (LNReader compat).
- `fetchExtensionIndex`: returns `null` — APK index entries have no iOS consumer.

### data/commonMain

- **`JsPluginIndexFetcher`** interface (domain/commonMain, same package as the
  parser) + **`KtorJsIndexFetcher`** impl (data/commonMain) — Ktor port of
  `NetworkNovelPluginIndexFetcher`: candidate paths `plugins.min.json` →
  `plugins.json` → `.dist/plugins.min.json` → `.dist/plugins.json` →
  `index.min.json` → `index.json`; first 2xx wins.
- **`KtorNovelPluginDownloader : NovelPluginDownloader`** —
  `client.get(url).body<ByteArray>()`; usable by both platforms.
- **`novel_plugins.sq`**: add `kind TEXT NOT NULL DEFAULT 'novel'` + `6.sqm`
  migration (`ALTER TABLE … ADD COLUMN kind`); update `NovelPluginRepositoryImpl`
  insert/mapping.
- **`NovelPluginInstaller`**: pass `kind` through `Available` → `Installed`.
- **`JsPluginApi`** (mirroring `NovelPluginApi`, domain/commonMain): repo
  provider **unions all three** `extension_repos` tables (novel/manga/anime),
  deduped by `baseUrl` — a repo that registered only under manga must still
  surface its plugins — + fetcher + parser → `List<NovelPlugin.Available>`;
  per-repo failures logged and skipped; `repoName` stamped on each plugin.

### ios-app/commonMain

- `IosNovelSourceManager.createSource` skips `kind != "novel"` plugins (manga/
  anime-kind installs park in the table until their bridges exist).
- `AppGraph`: `IosExtensionRepoService(httpClient)`, `KtorJsIndexFetcher`,
  `KtorNovelPluginDownloader`, `JsPluginApi`, three repo repositories +
  create/delete/get interactors; expose through `MikoUiGraph`.

### :ui/commonMain — Android-parity UX

- **Browse screen**: BROWSE|EXTENSIONS segmented pill in the top bar area
  (equivalent of `BrowseSearchPill`); BROWSE shows existing sources list,
  EXTENSIONS shows the extensions list.
- **`ExtensionsScreen`**: `Installed` section (icon, name, version, kind, lang,
  uninstall action) + `Available`/`Not Installed` grouped by language
  (install button, NSFW chip when `isNsfw`, site label); refresh action
  re-fetches `JsPluginApi`. All `kind`s list and install — novel-kind ones
  flow to sources now, manga/anime-kind park until their bridges land.
  (Search is out of scope: the shared Browse screen has no search bar yet;
  it arrives with that feature.)
- **Overflow → "Extension repos"** → `ExtensionReposScreen` mirroring
  `ConsolidatedExtensionReposScreen`: `PrimaryTabRow` with three tabs
  (Shows/Manga/Books naming consistent with the app's content-mode labels),
  per-tab repo rows (name, baseUrl, delete), add dialog (URL input + example
  hint, accepts full `index.min.json`/`plugins.min.json`/… URLs — the
  interactors strip suffixes), shared add flow =
  `probeRepoTypes` → create in every matching tab → jump to first match;
  error states map `InvalidUrl`/`RepoAlreadyExists`/`DuplicateFingerprint`/
  `Error` to the same messages Android shows.
- `ScreenModel`s: `ExtensionsScreenModel` (installed flow + refreshable
  available list), `ExtensionReposScreenModel` (three repo flows + probe/create/
  delete). Same StateFlow + `dispose()` convention as existing `:ui` models.

### What stays out of this slice

- Manga/anime JS source bridges + their source managers (contract design is a
  separate phase; `kind` lands now so they can route later).
- Content-mode (shows/manga/books) tabs on the Browse screen — only novel
  sources exist on iOS yet.
- Extension update checks, `hasSettings` screens, APK trust dialogs.
- Icon loading uses existing Coil-in-`:ui` setup.

## Testing

- `domain`/`data` commonTest: parser fixtures (all `kind` values + absent kind,
  relative URL resolution, APK entries dropped, malformed rows skipped),
  `probeRepoTypes` on a mixed-kind index, `KtorJsIndexFetcher` candidate order
  via `MockEngine`, `KtorNovelPluginDownloader` bytes round-trip,
  `CreateNovelExtensionRepo` URL normalization (already common — cover it).
- iOS CI gate: unchanged pipeline (KMP compiles, JSCore tests, framework link,
  xcodegen, XCTest probe).
- Android CI: unchanged — no `app/` files touched.
- Manual: add real mikoDB URL in-app → install a novel plugin → appears in
  Sources → feed loads.

## Open item

- Concrete mikoDB index URL — not required for implementation (user adds it
  in-app); if provided, use it as a test fixture.
