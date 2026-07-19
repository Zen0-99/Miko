# Miko — Fork Research & Port Ideas

Notes on features from other Aniyomi/Mihon/Yōkai forks that are candidates for porting into Miko. Updated as questions get answered.

---

## Tadami (andarcanum/Tadami-Aniyomi-fork) — 171 stars

**Repo:** https://github.com/andarcanum/Tadami-Aniyomi-fork
**Base:** Aniyomi fork with anime + manga + novels, Aurora-style UI.
**Status:** Most directly comparable to Miko.

### Features I like (candidates to port)

| # | Feature | Notes |
|---|---------|-------|
| 1 | **Media type toggles** (novel/manga/anime on/off) | Let users hide content types they don't use. Settings → media visibility preferences. |
| 2 | **Novel tracking** (NovelUpdates + NovelList) | Same tracker infrastructure as anime/manga. Bidirectional chapter progress sync with offline retry queue. Files: `data/track/novel/NovelTrackMapper.kt`, `NovelTrackRepositoryImpl.kt`, `app/.../data/track/novelupdates/NovelUpdates.kt`, `novellist/NovelList.kt`. Interactors: `RefreshNovelTracks`, `SyncNovelChapterProgressWithTrack`, `TrackNovelChapter`. |
| 3 | **Aurora Home hub** | Greeting header, hero card (recently viewed or next-up), Recently Viewed carousel, Recently Added carousel, media-specific sections. Per-media ScreenModels: `HomeHubScreenModel`, `MangaHomeHubScreenModel`, `NovelHomeHubScreenModel`. Uses StateScreenModel + Flow. |
| 4 | **Similar titles / recommendations** | **OPEN QUESTION** — research found no recommendation code in Tadami. The genre-tap search (v0.56.1) is search filtering, not recommendations. Need to re-examine in-app or ask the developer. |
| 5 | **Floating nav bar** (glassmorphism, localised) | Custom Compose component, Aurora styling. Configurable: floating pill vs old version. Settings → Appearance → Bottom navigation appearance. Like the glassmorphism only on the nav bar, not everywhere. |
| 6 | **Achievements system** | 10 types (QUANTITY, EVENT, DIVERSITY, STREAK, LIBRARY, META, BALANCED, SECRET, TIME_BASED, FEATURE_BASED), 4 categories (ANIME, MANGA, BOTH, SECRET). Tiered, XP, unlockable themes/badges. `AchievementHandler` subscribes to `AchievementEventBus` (SharedFlow). `PointsManager` for levels. `DiversityAchievementChecker` (genre/source diversity), `StreakAchievementChecker` (daily streaks). Config in `app/src/main/assets/achievements/achievements.json`. DB tables: `achievements`, `achievement_progress`, `achievement_activity`, `user_points`. Secret achievements (e.g. "Time Paradox" with tab-glow rewards). Genre badges (Romance, Isekai, Horror...). |
| 7 | **Online dictionary & translation in novel reader** | Highlight text → Wiktionary definitions or translate to preferred language. |
| 8 | **Novel reader tools** | Page joining, smart-fit margins, E-Ink binarization, estimated reading time. |
| 9 | **Genre search from title cards** | Tap genres in a title's info card to search the current source by those genres. Multi-select. |
| 10 | **Incognito policies** | "Automatic incognito" with "NSFW sources" mode — auto-enables incognito for NSFW extensions while global incognito is off. Per-extension toggles still work. |
| 11 | **FastScrollLazyColumn** | Replaced ScrollbarLazyColumn on Sources screens for smoother scrolling. |
| 12 | **Cold-launch performance work** | Heavy preference collection and non-critical work deferred past first frame. |

### Open questions about Tadami

- [ ] **Q1.1 — Extension Stores:** I couldn't tell the difference from the old repo model when using the same extensions I already had. **Research finding:** Extension Stores fetch metadata from remote store indexes rather than scraping individual repos. Per-media-type store management, deep links (`tadami://extension-store`), backup/restore of stores, signing-key conflict dialog. In practice the user-facing flow looks similar; the difference is architectural (indexed metadata vs per-repo scraping). **Need to confirm:** whether this actually matters for Miko's use case, or if the current repo model is fine.
- [x] **Q1.2 — Similar titles logic:** **ANSWERED.** Tadami has a full `data/suggestions/` system. See the "Similar Titles — How It Works" section below.
- [ ] **Q1.3 — Navigation smoothness:** Tadami uses Voyager 1.1.0-beta03, Compose-first, state hoisting, FastScrollLazyColumn, deferred cold-launch work, serialized tab switching, 350ms tween tab recentering. No shared-element transitions or predictive back found. **Question for Miko:** is Miko on Voyager or the old Conductor/navigation framework? If Conductor, migrating to Voyager (or at least adopting the deferred-work + FastScroll patterns) could be the biggest smoothness win.

### Tadami novel extension structure — IMPORTANT for Miko

**Tadami uses a JavaScript-based novel extension system** compatible with LNReader-style plugins:
- Novel extensions load via `NovelJsSource` (`app/src/main/java/eu/kanade/tachiyomi/extension/novel/runtime/NovelJsSource.kt`).
- They use a **JS runtime bridge** (`NovelJsRuntime`, `NovelJsRuntimeFactory`, `NovelPluginApi`) — NOT the standard `MangaSource`/`AnimeSource` interfaces.
- Sources come from [LNReader plugins](https://github.com/LNReader/lnreader-plugins), run on a QuickJS on-device runtime.
- Managed by `AndroidNovelSourceManager` (`app/src/main/java/eu/kanade/tachiyomi/source/novel/AndroidNovelSourceManager.kt`).
- Compatibility tooling: `.github/workflows/novel_plugin_compat_nightly.yml`.

**This is why Tadami doesn't recognise Miko's compiled-APK novel extensions** — and conversely why Miko's custom novel sources aren't recognised by Tadami. They are fundamentally different extension models:
- **Miko (Yōkai lineage):** compiled APK extensions with `yokai.novel.extension` feature flag + `yokai.novel.extension.class`/`.factory` metadata in AndroidManifest.xml. `NovelExtensionLoader` loads them via `PathClassLoader`/`ChildFirstPathClassLoader`. `NovelSource` and `NovelSourceFactory` interfaces. (Confirmed in `aniyomi-fork/app/src/main/java/eu/kanade/tachiyomi/extension/novel/util/NovelExtensionLoader.kt` lines 40-43.)
- **Tadami:** JS plugins run on QuickJS, sourced from LNReader plugin repos.

**Decision needed:** Does Miko want to (a) stay on compiled-APK novel extensions (current Yōkai model, what MikoNovelSources produces), or (b) adopt a JS runtime like Tadami/Hayai for LNReader plugin compatibility, or (c) support both? Option (c) is the most work but gives access to the large LNReader plugin ecosystem AND keeps the existing MikoNovelSources extensions.

**IMPORTANT — Comments feature impact:** Miko's `NovelSource` interface has `supportsComments` and `getChapterComments()` (see `source-api/.../novelsource/NovelSource.kt` lines 37-102, `NovelComment` model). This is a Miko-specific feature — Tadami's `NovelJsSource` has **NO comments support at all** (confirmed: zero matches for "comment" in `NovelJsSource.kt`). If Miko switches to the JS runtime model (option b), the comments feature would be **lost** unless explicitly re-implemented in the JS plugin API layer. LNReader plugins don't have a comments concept natively. If Miko supports both (option c), only the compiled-APK extensions would support comments; JS plugins would not unless the JS API is extended with a `getChapterComments` method.

### Similar Titles — How It Works (ANSWERED)

Tadami has a full **`data/suggestions/`** system. The "similar titles" row in the detail view is powered by a multi-source recommendation pipeline. Tadami repo cloned to `C:/Users/karol/OneDrive/Documents/GitHub/Tadami`.

**Architecture:**
- `SuggestionCoordinator` (`data/suggestions/SuggestionCoordinator.kt`) — orchestrates all sources in parallel with a 10s timeout per source.
- `NovelRelatedSuggestionCoordinator` (`data/suggestions/novel/NovelRelatedSuggestionCoordinator.kt`) — fetches native "related novels" from the source itself.
- `NovelSearchFallbackEngine` (`data/suggestions/novel/NovelSearchFallbackEngine.kt`) — fallback: searches the active source by title/author/genre.
- `AuroraSuggestionsRow` (`presentation/entries/components/aurora/AuroraSuggestionsRow.kt`) — the UI carousel component shown in the detail screen.
- `SuggestionState` — Idle / Loading / Success / Empty / Error / Disabled (toggled by preference `entrySuggestionsEnabled`).

**Two parallel tasks run when a novel detail screen opens:**

**Task 1 — External recommendations** (via `SuggestionCoordinator.fetchSuggestions`):
Queries external tracker/catalogue APIs in parallel, deduplicates by clean title, and sorts by a weighted score. Sources:
- **AniList** (`AniListRecommendationSource.kt`) — always included for all media types. Uses the AniList GraphQL API `Recommendations` query: searches for the seed title, gets its `recommendations { edges { node { ... } } }`, filters by type/format. Covers MANGA, ANIME, and NOVEL (AniList stores novels as MANGA with format=NOVEL).
- **MyAnimeList** (`MyAnimeListRecommendationSource.kt`) — only for ANIME. Uses the Jikan API (public MAL API).
- **MangaUpdates** (`MangaUpdatesSimilarSource.kt`) — for MANGA and NOVEL (MU stores light novels under type "Novel"). Gated by `suggestionsUseMangaUpdatesNovel` preference.
- **NovelUpdates** (`NovelUpdatesSimilarSource.kt`) — NOVEL only. Gated by `suggestionsUseNovelUpdates` preference. Searches NovelUpdates' WordPress JSON API (`/wp-json/wp/v2/series?search=...`), picks the best match by title overlap, then scrapes the series page HTML for the "Series Recommendations" section (regex: `<a href=".../series/(\d+)/">title</a>`).

**Task 2 — Native related novels** (via `NovelRelatedSuggestionCoordinator`):
If the source supports it (`NovelCatalogueSource.supportsRelatedNovels`), calls `source.getRelatedNovels(novel)` directly. This is a per-source method that the extension can implement to return related/recommended titles from the source's own website. Tadami's JS plugins expose this via `capabilities.hasRelatedNovels`. Miko's compiled extensions could add this to the `NovelSource` interface.

**Fallback engine** (if external + native yield nothing):
`NovelSearchFallbackEngine` runs a **3-tier search** on the active source:
- **Tier 1 (Exact Title):** searches the source by the primary title + alt titles + titles parsed from the description.
- **Tier 2 (Relaxed Title):** splits titles by separators (`:`, `-`, `(`, `[`, `,`, `;`), cleans volume/season suffixes, takes first 3 words of long titles.
- **Tier 3 (Author):** searches by author name parts.
- **Genre backfill:** if still empty, searches by the novel's genres (up to 4, with multilingual genre translation via `MultilingualQueryHelper`).
- **Popular backfill:** if all else fails, pulls from the source's popular catalogue (cap 8).

**Seed construction** (`buildSuggestionSeed` in `NovelScreenModel.kt` line 693):
- `primaryTitle` = `novel.displayTitle`
- `candidateTitles` = resolved from title + alt titles + description + URL via `SuggestionTitleResolver.resolveCandidates()`
- `description`, `author`, `genres` included for fallback engine use.
- Multilingual: `MultilingualQueryHelper.translate()` translates the primary title (e.g. Cyrillic → Latin) and adds it as a candidate.

**Deduplication & scoring:**
- `dedupeByCleanTitle()` — removes duplicates by normalised title.
- `SuggestionSourceWeight.finalScore(reason, bestMatchScore)` — weights results by their source (RELATED > EXTERNAL_ANILIST > EXTERNAL_NU > SEARCH_TITLE > SEARCH_AUTHOR > SEARCH_GENRE > POPULAR_BACKFILL) and by how well the title matches the seed.
- Self-dedup: filters out the seed title itself (`isSameProviderEntry`) and franchise duplicates (`isFranchiseDuplicate` — e.g. "Solo Leveling" won't suggest "Solo Leveling: Ragnarok").
- Progressive emission: results are emitted as they arrive (`emitProgressiveSuggestions`), not waited on — the UI updates incrementally.

**Caching:**
- `SuggestionCache` — in-memory cache keyed by source name + title + media type + candidates + description + author. Cache hits skip network entirely.
- Cache can be invalidated per-seed (`SuggestionCache.invalidateForSeed`) on retry.

**UI:**
- `AuroraSuggestionsRow` — a `LazyRow` carousel of 100×150dp cards with cover thumbnails, staggered reveal animation (50ms delay per card), shimmer loading state, retry button on error, empty state message.
- Shown inline in the detail screen (if `entrySuggestionsExpandInline` is on) or as a button that opens a full `EntrySuggestionsScreen`.
- Tapping a suggestion searches the current source for that title and opens the first result.

**What Miko would need to port this:**
1. The `data/suggestions/` package (~15 files: coordinator, sources, fallback engines, cache, title resolver, weight scoring, multilingual helper, item model, state, seed).
2. `supportsRelatedNovels` / `getRelatedNovels` added to the `NovelCatalogueSource` interface (optional — works without it, just no native related).
3. `AuroraSuggestionsRow` UI component + integration into `NovelScreen`/`NovelScreenAurora`.
4. `EntrySuggestionsScreen` for the full-page view.
5. Preferences: `entrySuggestionsEnabled`, `entrySuggestionsExpandInline`, `entrySuggestionsInOverflow`, `suggestionsUseNovelUpdates`, `suggestionsUseMangaUpdatesNovel`.
6. AniList API integration (Miko may already have tracker auth for AniList — can reuse).
7. NovelUpdates scraping (WordPress JSON API + HTML regex — self-contained, no auth needed).

---

## Hayai (HayaiApp/hayai) — 30 stars

**Repo:** https://github.com/HayaiApp/hayai
**Base:** Yōkai fork (same lineage as Miko-Yokai-Old) with first-class novel support.

### Features worth noting

| Feature | Notes |
|---------|-------|
| **JS plugin support for novels** | Same LNReader-plugin-on-QuickJS approach as Tadami. Sources from [LNReader plugins](https://github.com/LNReader/lnreader-plugins). Novel plugin manager: install, update, sort, uninstall. Per-repo plugin cache + validation. |
| **`parsePage` support** | Handles plugins that paginate chapter listings — fetches the full chapter list, not just page 1. |
| **Browse infinite scroll** | Pages keep loading until a plugin returns empty (LNReader convention). |
| **Novel translation view** | Dedicated `NovelTranslationView` with provider setup, language selectors, filters. Translate & Web Search actions in the novel WebView viewer. `TranslationService`, `TranslationPreferences`. |
| **Reduce motion toggle** | Globally disables animations, fades, crossfades, skeleton-loader pulses. Wired centrally across Conductor, Compose, Coil, activity transitions. Settings → Appearance → Motion. Good for low-end devices, motion-sensitive users, battery saver. |
| **Tabbed library display** | Each category on its own swipeable page with pinned tab strip + per-tab counts. Separate pull-to-refresh per tab. Layout choice offered during onboarding. |
| **HayaiTTS companion** | Offline neural TTS, 186 voices via sherpa-onnx (Piper/Kokoro/Matcha/ZipVoice). Material 3 Expressive. |

---

## Mohyeong (sjoygsh/Mohyeong) — Mihon fork

**Repo:** https://github.com/sjoygsh/Mohyeong
**Base:** Mihon fork. **Note:** has since migrated to Flutter; the Kotlin implementation is the relevant reference.

### Linked Sources — how it works

- **Data model:** new `manga_links` SQLDelight table with `linkedId` (cluster id) and `primaryId` (primary manga in the cluster). `subscribeAllPrimariesByLinked` exposes a bulk linkedId → primary Manga Flow.
- **Files:** `data/src/main/sqldelight/tachiyomi/data/manga_links.sq`, `data/src/main/java/tachiyomi/data/manga/MangaLinkRepositoryImpl.kt`, `domain/.../manga/repository/MangaLinkRepository.kt`, `domain/.../manga/interactor/GetLinkedMangas.kt`. Merging in `MangaScreenModel.kt`.
- **Merging:** chapters from all linked sources are fetched and combined in-memory (DB rows untouched). Resequenced by chapter number descending; a synthetic `sourceOrder` is assigned for display only via `copy()` on the in-memory Chapter values flowing into UI state.
- **Duplicates:** **no explicit deduplication found.** Since each source has its own `manga_id`, chapters from different sources are separate rows even if they have the same chapter number. Duplicates may appear in the list — they're treated as separate entries from different sources.
- **Routing:** chapter open uses the chapter's real (linked) `mangaId` so the Reader loads pages from the correct source. No manual source-selection UI — the system tracks provenance per chapter.
- **Updates feed:** when a linked-source manga gets a new chapter, the Updates row shows the primary's title + cover; tapping routes to the primary's library entry.

### How Linked Sources could benefit Miko without duplicate chapters

**OPEN QUESTION — needs design decision.** Options:
1. **Mohyeong's approach (no dedup):** show duplicates as separate entries from different sources. Pro: simple, preserves source provenance. Con: cluttered chapter list.
2. **Dedupe by chapter number:** pick one source per chapter number (e.g. preferred source, or highest quality, or most recent upload). Pro: clean list. Con: need a preference for "preferred source" per title, and a way to fall back to another source if the preferred one is missing a chapter.
3. **Group by chapter number with source picker:** show one row per chapter number, but allow the user to pick which source to download/read from when opening. Pro: clean list + choice. Con: more UI work.
4. **Hybrid:** dedupe by default, with a toggle to "show all sources" for power users.

**Recommendation to discuss:** option 3 (group + source picker) gives the cleanest UX and is the most Miko-like (compact, customisable). The picker could be a bottom sheet listing each source's version of that chapter with scanlator/quality/date metadata.

---

## NovelDokusha / WebnovelReader (Nabeelshar fork) — 23 stars

**Repos:** https://github.com/nanihadesuka/NovelDokusha (original, unmaintained), https://github.com/Nabeelshar/WebnovelReader (active fork)
**Base:** Standalone novel reader, not a manga fork.

### Automatic Cloudflare bypass — how it works

- **Technique:** WebView + cookie extraction (native Android, no external service).
- **Key files:** `networking/src/main/java/my/noveldokusha/network/interceptors/CloudfareVerificationInterceptor.kt` (517 lines), `BrowserHeadersInterceptor.kt` (118 lines), `UserAgentInterceptor.kt`, `features/webview/src/main/java/my/noveldokusha/webview/WebViewActivity.kt`.
- **Flow:**
  1. OkHttp request fails with 403/503/429.
  2. Interceptor detects Cloudflare challenge markers — checks `cf-mitigated: challenge` header, requires both body markers AND `Server: cloudflare` header (tightened to prevent false positives). Keeps Turnstile detection for HTTP 200.
  3. WebView launches with the URL.
  4. User solves the challenge (or it auto-solves for non-interactive challenges).
  5. WebView obtains `cf_clearance` cookie; `CookieManager.flush()` persists it.
  6. LaunchedEffect polls CookieManager directly for cookie changes; auto-dismisses WebView on Turnstile cookie without page reload.
  7. Request is retried with the valid cookie.
- **Browser headers:** `BrowserHeadersInterceptor` adds browser-like headers (User-Agent, Accept, Accept-Language, etc.) to avoid detection.

### Can we use this for Miko extensions?

**Yes — highly adaptable.** The pattern is already used in the wider ecosystem:
- **EHViewer-NekoInverter:** embedded WebView + cookie auto-sync, detects 403 + CF headers, full-screen WebView, syncs cookies, auto-closes on solve.
- **Han1meViewer:** `CloudflareInterceptor` pops a verification Activity on 403.
- **Neko (github version):** `CloudflareInterceptor` waits for challenge completion.
- Aniyomi/Mihon already has a WebView component and OkHttp interceptors, so the building blocks are in place.

**What would need to be added:**
1. Detection logic for CF challenge markers (`cf-mitigated: challenge`, body markers + `Server: cloudflare`, Turnstile on 200).
2. Logic to trigger WebView on challenge detection.
3. Cookie extraction + persistence via `CookieManager`.
4. Request retry with valid cookies.
5. Browser-like headers interceptor.

**For the Ranobes extension (Cloudflare problems):** this is exactly the use case. The extension would need a `CloudflareInterceptor` in its OkHttp client that, on a CF challenge, falls back to WebView cookie extraction. The interceptor can live in the extension itself or in a shared utility module that extensions opt into.

**Reference commits to study (from the HnDK0/NoveLA fork lineage):**
- `bdf3cb9` — initial implementation
- `05a95b8` — detection improvements (tightened false positives)
- `360c84d` — cookie persistence
- `9fd25d8` — thread safety (ConcurrentHashMap DNS cache, removed redundant `Dispatchers.Main`)

**Alternative libraries:**
- `darkryh/Cloudflare-Bypass` — Android library with custom WebViewClient (ready-to-use).
- FlareSolverr — external proxy, requires separate deployment (not ideal for a phone app).

---

## Other forks noted (lower priority for now)

- **Chimahon** (hcrgm/chimahon) — Mihon/Komikku fork. Novel reader with integrated dictionary lookup (Hoshi Reader), OCR text capture, flashcard creation for vocabulary mining, `.mokuro` support, Auto-Theme (UI adapts to cover art). Interesting for language-learner niche.
- **Yakuyomi** (joyeli/Yakuyomi) — Mihon fork. On-device AI manga translation (ONNX detection/OCR/inpainting + cloud LLM translation). Niche but impressive.
- **QuickNovel** (LagradOst/QuickNovel) — the app Miko's novel providers were ported from. TTS up to 7x, online translation, PDF, EPUB import, chapter filter/sort, paragraph spacing, reading progress on bookmarks. Worth referencing for TTS/translation features.

---

## Priority / next steps (to be refined after open questions are answered)

1. **Source matching on backup restore (Level 1)** — small effort, high impact. Solves migration from Tadami/Hayai. Self-contained in backup restore code.
2. **Cloudflare bypass** for Ranobes and other CF-protected sources — self-contained, high impact, reference implementations exist.
3. **Media type toggles** — relatively self-contained settings work.
4. **Similar titles / suggestions** — port Tadami's `data/suggestions/` system. Medium-large but high user value.
5. **Novel tracking** (NovelUpdates + NovelList) — medium effort, reuses existing tracker infrastructure.
6. **JS plugin runtime + linking (Level 2)** — large effort, gives LNReader ecosystem access + comments bridge.
7. **Aurora Home hub** — medium-large effort, new screens + ScreenModels.
8. **Achievements** — large effort, new DB tables + handler + UI, but high user-engagement payoff.
9. **Floating nav bar** — small-medium UI work, localised glassmorphism.
10. **Novel reader tools** (dictionary, translation, page join, E-Ink, reading time) — incremental, can pick subset.
11. **Reduce motion toggle + FastScrollLazyColumn + cold-launch perf** — incremental perf/UX wins.

---

## Design Proposal: Source Linking — Bridging APK and JS Novel Extensions

### The problem

Every other novel reader (Tadami, Hayai, LNReader) uses **JS plugins** for novel sources. Miko uses **compiled APK extensions**. A user switching from any of those apps to Miko loses their entire novel library because:
- Source IDs are computed differently: APK uses `MD5("name/lang/versionId")`, JS uses `SHA-256(pluginId)`.
- The same site (e.g. lightnovelpub.org) has a completely different source ID in each model.
- Backup restore matches by `sourceId` — unknown IDs become stub sources with no chapters.

### The idea

Borrow Mohyeong's "linked sources" concept and apply it to **bridge the two extension models**. A single novel entry in the library can be backed by both an APK extension AND a JS plugin, linked together. This gives:

1. **Data preservation on migration** — users coming from Tadami/Hayai keep their library.
2. **Comments via APK** — the APK source provides chapter comments; the JS source doesn't.
3. **Redundancy** — if one source goes down, the other fills in.
4. **LNReader ecosystem access** — JS plugins give access to hundreds of sources Miko doesn't have APK extensions for.

### Three implementation levels (incremental)

#### Level 1: Source matching on backup restore (no JS runtime needed)

**Effort:** Small. **Impact:** High — solves the migration problem immediately.

When restoring a backup with unknown novel source IDs:
1. For each unknown source, Miko has the source `name` from the backup's `BackupNovelSource` entries.
2. Miko searches installed APK novel extensions for a source with a matching `baseUrl` or similar name.
   - **Matching by `baseUrl`** is most reliable: both APK (`NovelHttpSource.baseUrl`) and JS (`NovelJsSource.siteUrl`) expose the website root URL. Normalise by stripping protocol, `www.`, trailing slash, and comparing.
   - Fallback: fuzzy name matching (e.g. "LightNovelPub" ≈ "Light Novel Pub").
3. If a match is found, **remap the sourceId** from the backup's JS source ID to the APK extension's source ID.
4. Library entries, chapters, history, and tracking all transfer to the APK source.
5. The novel `url` may differ between models (different URL patterns for the same novel on the same site). Miko can re-search the APK source for the novel by title to find the correct URL.

**Result:** Users import a Tadami/Hayai backup and their novels appear under Miko's APK extensions. No JS runtime needed. Comments work immediately.

**Limitation:** Only works for sites where Miko has an APK extension. Sites without an APK equivalent remain stubs.

#### Level 2: Support JS plugins + auto-link (full vision)

**Effort:** Large (port QuickJS runtime + plugin manager from Tadami). **Impact:** Full LNReader ecosystem access + data preservation.

1. Port the JS novel plugin runtime from Tadami:
   - `NovelJsSource`, `NovelJsRuntime`, `NovelJsRuntimeFactory`, `NovelPluginApi` (~15-20 files).
   - `NovelPluginManager` / `DefaultNovelExtensionManager` (plugin install, update, uninstall).
   - QuickJS on-device runtime integration.
   - Plugin repo fetching and validation (LNReader plugin repos).
2. Both APK extensions and JS plugins register sources with the `NovelSourceManager`.
3. A `novel_links` table (adapted from Mohyeong's `manga_links`):

   ```sql
   CREATE TABLE novel_links (
     _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
     linked_id INTEGER NOT NULL,       -- cluster id (shared by all linked entries)
     novel_id INTEGER NOT NULL,        -- the per-source novel entry
     source_id INTEGER NOT NULL,       -- which source this entry belongs to
     is_primary INTEGER AS Boolean NOT NULL DEFAULT 0,
     extension_type TEXT NOT NULL DEFAULT 'apk',  -- 'apk' or 'js'
     FOREIGN KEY(novel_id) REFERENCES novels (_id) ON DELETE CASCADE
   );
   ```

4. **Auto-linking by `baseUrl`:** When a user installs both an APK extension and a JS plugin for the same site, Miko detects the matching `baseUrl`/`siteUrl` and offers to auto-link existing library entries. Or does it silently with a notification.
5. **Chapter merging with deduplication** (improving on Mohyeong, which has no dedup):
   - Fetch chapters from all linked sources.
   - **Deduplicate by chapter number** (not by URL — URL formats differ between models).
   - If both sources have chapter 1, show one row but track both source URLs internally.
   - When opening a chapter, prefer the primary source. If it fails or is missing, fall back to the linked source.
   - User can set "preferred source" per linked cluster (or auto: APK if available for comments, else JS).
6. **Comments routing:** Comments come from whichever linked source supports them (APK). If only JS sources are linked, the comments button is hidden. If both are linked, comments come from the APK source.
7. **Backup/restore:** Backup includes both source types' entries + link relationships. On restore, if JS plugins are installed, JS-backed entries recover. If APK extensions are also installed, links are restored and comments become available.

#### Level 1.5: Source matching + JS runtime (pragmatic middle ground)

**Effort:** Medium-large. **Impact:** Full migration + LNReader ecosystem, but linking is manual.

1. Implement Level 1 (source matching on restore).
2. Port the JS runtime (Level 2 step 1).
3. Both models coexist independently — no auto-linking.
4. Users can manually "migrate" a novel from a JS source to an APK source (existing migration feature in Aniyomi), which would transfer the entry.
5. Linking (Level 2's `novel_links` table) can be added later as enhancement.

### Source ID schemes (confirmed)

| Model | ID generation | Example (lightnovelpub) |
|-------|--------------|------------------------|
| APK (Miko) | `MD5("name.lowercase()/lang/versionId")` → first 8 bytes as Long | `MD5("lightnovelpub/en/1")` |
| JS (Tadami) | `SHA-256(pluginId)` → first 8 bytes as Long, masked with `Long.MAX_VALUE` | `SHA-256("lnreader-plugins/lightnovelpub")` |

These are **completely different** — the same site will never have the same source ID across models. This is why backup restore fails across apps.

### Matching strategy

**Primary: `baseUrl` matching.**
- APK: `NovelHttpSource.baseUrl` (e.g. `https://www.lightnovelpub.org`)
- JS: `NovelJsSource.siteUrl` (resolved at runtime from the plugin)
- Normalise: lowercase, strip protocol (`http://`/`https://`), strip `www.`, strip trailing slash.
- Match if normalised URLs are equal, or if one is a subdomain/path of the other.

**Fallback: name matching.**
- Normalise source names: lowercase, remove spaces/punctuation, compare.
- e.g. "LightNovelPub" → "lightnovelpub", "Light Novel Pub" → "lightnovelpub" — match.

**Last resort: URL pattern.**
- Take a sample novel URL from the backup, fetch it, see if the page structure matches what the APK extension expects.
- Too expensive for automatic use — only for manual migration confirmation.

### Migration flow (Tadami → Miko, with Level 2)

1. User installs Miko, installs APK novel extensions from MikoNovelSources.
2. User imports Tadami backup.
3. Miko restores JS-backed novel entries (if JS plugins are installed) or creates stubs (if not).
4. Miko scans for matching APK extensions by `baseUrl`.
5. **Prompt:** "We found APK extensions for 4 of your novel sources that support chapter comments. Link them?"
6. On confirm: `novel_links` entries created, APK sources set as primary, chapters merge, comments become available.
7. User's library looks exactly as it did in Tadami, but now with comments and APK reliability.

### What to port from Tadami for Level 2

| Component | Files | Notes |
|-----------|-------|-------|
| JS runtime | `NovelJsSource.kt`, `NovelJsRuntime.kt`, `NovelJsRuntimeFactory.kt`, `NovelPluginApi` | Core QuickJS bridge |
| Plugin manager | `DefaultNovelExtensionManager.kt`, `NovelPluginManager` | Install/update/uninstall JS plugins |
| Plugin ID | `NovelPluginId.kt` | SHA-256 source ID generation |
| Plugin repos | Plugin repo fetching, validation, caching | LNReader plugin repo compatibility |
| Source manager | `AndroidNovelSourceManager.kt` | Unified manager for both APK + JS sources |
| QuickJS dependency | Gradle dependency | `com.github.openjdk:quickjs` or similar |

### Recommendation

**Start with Level 1** (source matching on restore). It's small, self-contained, and immediately solves the migration problem. Then progress to Level 2 (JS runtime + linking) as a larger phase. Level 1.5 is the natural intermediate step if the JS runtime port takes time.

Level 1 can be implemented entirely within the existing backup restore code (`NovelRestorer.kt`, `BackupRestorer.kt`) — no new DB tables, no new runtime, no new UI. Just a sourceId remapping step.
