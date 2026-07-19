# Miko — Feature Implementation Plan

Comprehensive plan for porting/building all features identified in `FORK_RESEARCH_NOTES.md`. Each feature has: description, effort estimate, dependencies, decision points (marked with ❓), and implementation notes.

Decision points need user input before implementation can proceed. See "Open Decisions" section at the bottom.

---

## Status Overview (updated 2026-07-19)

| Phase | Feature | Status | Commit |
|-------|---------|--------|--------|
| 0.1 | Cloudflare Bypass for Extensions | ✅ Done | (Phase 0 batch) |
| 0.2 | Media Type Toggles (hide tabs only) | ✅ Done | (Phase 0 batch) |
| 0.3 | Perf patterns (FastScrollLazyColumn + cold-launch deferral) | ✅ Done | (Phase 0 batch) |
| 0.4 | Reduce Motion Toggle | ✅ Done | (Phase 0 batch) |
| 1.1 | Source Matching on Backup Restore (Level 1) | ✅ Done | (Phase 1 batch) |
| 1.2 | JS Plugin Runtime port from Tadami (skeleton) | ✅ Done | `a17de7f86` |
| 1.3 | Linked Sources (Level 2, reverse-compatible backups) | ✅ Done | `d8dadb1ab` |
| 2.1 | Similar Titles / Suggestions (skeleton with AniList) | ✅ Done | `a00be3f87` |
| 2.2 | Genre Search from Title Cards | ✅ Already implemented | — |
| 2.3 | Home Hub (minimal) | ✅ Done | `149d5bace` |
| 3.1 | Novel Reader Tools (all 4) | ✅ Done | `12ce1b9ad` |
| 3.2 | Online Dictionary & Translation | ⏭️ Skipped per D7 | — |
| 3.3 | Novel Tracking (NovelUpdates + NovelList) | ✅ Done | `649611e60` |
| 3.4 | Neural TTS (sherpa-onnx) | ✅ Done (skeleton) | `9717cc051` |
| 4.1 | Floating Nav Bar (glassmorphism nav bar only) | ✅ Done | `327363252` |
| 4.2 | Achievements (full system) | ✅ Done (skeleton) | `a111738eb` |
| 4.3 | Incognito Policies | ✅ Done | `7395af608` |
| 4.4 | Tabbed Library Display | ✅ Done | `798782340` |
| 5.1 | Navigation Framework Migration | ⏭️ N/A — already on Voyager (D1) | — |
| 5.2 | Extension Stores (Architectural) | ⏭️ Deferred per D11 | — |

**Milestone status:** All in-scope phases (0–4) are implemented and committed. Phase 5 was explicitly deferred/skipped per user decisions. Several phases are labelled "skeleton" — the core scaffolding, interfaces, and UI are in place but full production wiring (e.g. real sherpa-onnx model loading, achievement rule JSON population, AniList suggestion scoring pipeline) remains as follow-up work.

**Follow-up work (not yet tracked as phases):**
- Populate `achievements.json` with real achievement definitions and wire `AchievementHandler` to actual app events.
- Replace `NeuralTtsEngine` stub with real sherpa-onnx model loading + inference.
- Complete AniList suggestion scoring pipeline (currently skeleton with API client).
- JS plugin runtime: full QuickJS integration (currently skeleton with interfaces).
- Many uncommitted working-tree changes from earlier sessions (UI refactors, novel components, cover-color extraction, etc.) that are outside the scope of these phases — need review/commit pass.

---

## Phase 0 — Foundation & Quick Wins

Features that are self-contained, high-impact, and unblock other work.

### 0.1 Cloudflare Bypass for Extensions — ✅ Done
**Effort:** Small-Medium · **Impact:** High · **Dependencies:** None
**Status:** Implemented (Phase 0 batch commit).

Port the NovelDokusha Cloudflare bypass pattern (WebView + cookie extraction) as a shared utility that extensions can opt into.

- **What:** `CloudflareInterceptor` OkHttp interceptor that detects CF challenges (403/503/429 + `cf-mitigated` header + `Server: cloudflare` + body markers, or Turnstile on 200), launches WebView, extracts `cf_clearance` cookie via `CookieManager`, retries request.
- **Where:** Shared utility module in `source-api` or a new `cloudflare-utils` module. Extensions add the interceptor to their OkHttp client.
- **Reference:** NovelDokusha `CloudfareVerificationInterceptor.kt` (517 lines), `BrowserHeadersInterceptor.kt` (118 lines). Also EHViewer-NekoInverter, Han1meViewer patterns.
- **Target:** Ranobes extension + any other CF-protected sources.
- **Files to create:**
  - `source-api/.../network/CloudflareInterceptor.kt`
  - `source-api/.../network/BrowserHeadersInterceptor.kt`
  - Integration with existing WebView component in app module.
- **Decision ✅:** Shared utility — all extensions opt-in. More maintainable.

### 0.2 Media Type Toggles — ✅ Done
**Effort:** Small · **Impact:** Medium · **Dependencies:** None
**Status:** Implemented (Phase 0 batch commit). Hide-tabs-only behaviour per D12.

Let users hide content types (novel/manga/anime) they don't use. Cleans up the UI for users who only care about novels, or only anime, etc.

- **What:** Preferences: `showAnimeTab`, `showMangaTab`, `showNovelTab` (all default true). When false, the corresponding tab is hidden from bottom nav, browse, library, and history.
- **Where:** `SourcePreferences` or a new `AppearancePreferences`. Apply in:
  - Bottom navigation bar (hide tabs).
  - Browse screen (hide extension sections).
  - Library screen (hide library tabs).
  - History screen (hide media-specific sections).
  - Settings (hide media-specific settings sections).
- **Reference:** Tadami's "media visibility preferences" — "Respect media visibility preferences in Browse and History tabs".
- **Decision ✅:** Hide tabs only (main nav tabs for that type). Extensions and settings remain accessible.

### 0.3 Performance: FastScrollLazyColumn + Cold-Launch Deferral — ✅ Done
**Effort:** Small · **Impact:** Medium · **Dependencies:** None
**Status:** Implemented (Phase 0 batch commit).

Adopt Tadami's performance patterns for smoother UX.

- **FastScrollLazyColumn:** Replace `ScrollbarLazyColumn` on Sources screens and other long lists. Drop-in replacement for smoother scrolling.
- **Cold-launch deferral:** Move heavy preference collection and non-critical initialisation past the first frame. Use `LaunchedEffect` with low priority or a deferred init queue.
- **Decision ✅:** Miko is already on Voyager. No migration needed — just adopt the perf patterns.

### 0.4 Reduce Motion Toggle — ✅ Done
**Effort:** Small · **Impact:** Medium (accessibility) · **Dependencies:** None
**Status:** Implemented (Phase 0 batch commit).

Globally disable animations for users who prefer reduced motion or have low-end devices.

- **What:** A single preference `reduceMotion` (default false). When true:
  - Disable screen-transition animations.
  - Disable in-app fades and skeleton-loader pulses.
  - Disable image crossfades (Coil).
  - Disable activity transitions.
- **Where:** Settings → Appearance → Motion. Wired centrally so every animation respects the toggle without per-screen opt-in.
- **Reference:** Hayai's implementation — "wired centrally so every Conductor controller transition, Compose animation, Coil image load, and activity transition honors the toggle".

---

## Phase 1 — Migration & Compatibility

Solving the cross-app data migration problem.

### 1.1 Source Matching on Backup Restore (Source Linking Level 1) — ✅ Done
**Effort:** Small · **Impact:** Very High · **Dependencies:** None
**Status:** Implemented (Phase 1 batch commit).

Allow users to import backups from Tadami/Hayai and have their novel library transfer to Miko's APK extensions.

- **What:** During backup restore, when a novel source ID is unknown (JS-based SHA-256 ID), match it to an installed APK extension by `baseUrl` or name, and remap the sourceId.
- **Where:** `NovelRestorer.kt` and `BackupRestorer.kt` — add a sourceId remapping step before inserting novels.
- **Matching strategy:**
  1. Primary: normalise `baseUrl` (lowercase, strip protocol/`www.`/trailing slash) and compare.
  2. Fallback: normalise source names (lowercase, remove spaces/punctuation) and compare.
  3. If matched: remap sourceId, re-search the APK source by title to find the correct novel URL (URL patterns differ between models).
- **Files to modify:**
  - `app/.../data/backup/restore/BackupRestorer.kt` — add novel source remapping.
  - `app/.../data/backup/restore/restorers/NovelRestorer.kt` — use remapped sourceId.
  - New: `app/.../data/backup/restore/NovelSourceMatcher.kt` — matching logic.
- **Decision ✅:** Both APK and JS with linking (Level 2). This feature (Level 1) is the first step.

### 1.2 JS Plugin Runtime (Source Linking Level 1.5 / 2) — ✅ Done (skeleton)
**Effort:** Large · **Impact:** Very High · **Dependencies:** 1.1 recommended first
**Status:** Skeleton committed (`a17de7f86`). Interfaces and runtime factory scaffolding in place; full QuickJS integration is follow-up work.

Port the QuickJS-based novel plugin runtime from Tadami to support LNReader plugins.

- **What:** JS plugins run on an on-device QuickJS runtime, implementing the LNReader plugin API. Managed alongside APK extensions in a unified source manager.
- **Components to port from Tadami:**
  - `NovelJsSource.kt`, `NovelJsRuntime.kt`, `NovelJsRuntimeFactory.kt`, `NovelPluginApi` (~15-20 files).
  - `NovelPluginManager` / `DefaultNovelExtensionManager` (plugin install/update/uninstall).
  - `NovelPluginId.kt` (SHA-256 source ID generation).
  - Plugin repo fetching, validation, caching (LNReader plugin repo compatibility).
  - `AndroidNovelSourceManager.kt` (unified manager for APK + JS).
  - QuickJS Gradle dependency.
- **Decision ✅:** Yes, port the JS runtime. Full linking (Level 2) is the goal.

### 1.3 Linked Sources (Full — Source Linking Level 2) — ✅ Done
**Effort:** Large · **Impact:** High · **Dependencies:** 1.2
**Status:** Implemented (`d8dadb1ab`). Reverse-compatible backups per D2. Dedup-by-default with "Show duplicates" filter per D3.

Mohyeong-style linked sources, adapted to bridge APK and JS extensions.

- **What:** A `novel_links` DB table clusters novel entries from different sources. Chapters merge with deduplication. Comments route from APK source. Auto-linking by `baseUrl`.
- **Schema:**
  ```sql
  CREATE TABLE novel_links (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    linked_id INTEGER NOT NULL,
    novel_id INTEGER NOT NULL,
    source_id INTEGER NOT NULL,
    is_primary INTEGER AS Boolean NOT NULL DEFAULT 0,
    extension_type TEXT NOT NULL DEFAULT 'apk',
    FOREIGN KEY(novel_id) REFERENCES novels (_id) ON DELETE CASCADE
  );
  ```
- **Chapter merging:** Fetch from all linked sources, deduplicate by chapter number (not URL — formats differ), show one row per chapter, track both source URLs internally. Prefer primary source on open; fall back to linked source on failure.
- **Comments routing:** Comments from APK source (JS has no comments). If only JS linked, hide comments button.
- **Auto-linking:** When user installs both APK + JS for same site, detect matching `baseUrl`, offer to link. Or silent with notification.
- **Decision ✅:** Dedup by chapter number by default. "Show duplicates" option added to the existing filter icon in the chapter list.

---

## Phase 2 — Discovery & Recommendations

Features that help users find new content.

### 2.1 Similar Titles / Suggestions — ✅ Done (skeleton)
**Effort:** Medium-Large · **Impact:** High · **Dependencies:** None (works with APK sources; better with tracking)
**Status:** Skeleton committed (`a00be3f87`). AniList API client and suggestion pipeline scaffolding in place; full scoring/dedup pipeline is follow-up work.

Port Tadami's `data/suggestions/` system — a multi-source recommendation pipeline shown as a carousel in the novel detail screen.

- **What:** When a user opens a novel detail screen, two parallel tasks run:
  1. **External recommendations:** AniList (GraphQL Recommendations query), MangaUpdates, NovelUpdates (scrapes WordPress JSON API + HTML). Gated by preferences.
  2. **Native related novels:** `NovelCatalogueSource.supportsRelatedNovels` + `getRelatedNovels()` — per-extension method.
  3. **Fallback:** 3-tier search on active source (exact title → relaxed title → author), plus genre backfill and popular backfill.
- **Scoring:** Weighted by source (RELATED > EXTERNAL_ANILIST > EXTERNAL_NU > SEARCH_TITLE > SEARCH_AUTHOR > SEARCH_GENRE > POPULAR_BACKFILL). Dedup by clean title. Self/franchise dedup (won't suggest sequels). Progressive emission — UI updates as results arrive.
- **UI:** `AuroraSuggestionsRow` — LazyRow carousel of cover cards with staggered reveal, shimmer loading, retry on error. Inline in detail screen or button to full `EntrySuggestionsScreen`.
- **Caching:** In-memory `SuggestionCache` keyed by source+title+mediaType+candidates.
- **Files to port from Tadami:** ~15 files in `data/suggestions/` + `AuroraSuggestionsRow.kt` + `EntrySuggestionsScreen.kt` + preferences.
- **Decision ✅:** All media types (novel + manga + anime).

### 2.2 Genre Search from Title Cards — ✅ Already implemented
**Effort:** Small · **Impact:** Medium · **Dependencies:** None
**Status:** No work needed — already present in the codebase.

Tap genres in a title's info card to search the current source by those genres. Multi-select.

- **What:** In the novel/manga/anime detail screen, genre chips become tappable. Tapping one (or multi-selecting several) triggers a source search filtered by those genres.
- **Where:** `NovelScreenAurora.kt` / `MangaScreenAurora.kt` / `AnimeScreenAurora.kt` — genre chip row. Wire to existing source search with genre filters.
- **Reference:** Tadami v0.56.1 — "Genre search from title cards".

### 2.3 Aurora Home Hub — ✅ Done (minimal)
**Effort:** Medium-Large · **Impact:** High · **Dependencies:** 0.2 (media type toggles) recommended first
**Status:** Minimal version committed (`149d5bace`). Recently viewed + recently added only, per D5.

A home screen that shows recently viewed/added content and media-specific sections.

- **What:** Greeting header, hero card (recently viewed or next-up entry), Recently Viewed carousel, Recently Added carousel, media-specific sections (anime/manga/novel).
- **Architecture:** Per-media `ScreenModel`s (`HomeHubScreenModel`, `MangaHomeHubScreenModel`, `NovelHomeHubScreenModel`) using `StateScreenModel` + Flow. Combines multiple data streams.
- **UI:** Aurora-themed components. Respects media type toggles (hide sections for disabled types).
- **Decision ✅:** Minimal — just recently viewed + recently added, no greeting/hero.

---

## Phase 3 — Reading Experience

Features that improve the actual reading/watching experience.

### 3.1 Novel Reader Tools — ✅ Done (all 4)
**Effort:** Medium (subset) to Large (full) · **Impact:** Medium-High · **Dependencies:** None
**Status:** All four tools committed (`12ce1b9ad`): estimated reading time, smart-fit margins, page joining, E-Ink binarization.

A collection of tools for the novel reader, can be implemented incrementally.

- **Page joining:** Join consecutive pages/chapters into one continuous read.
- **Smart-fit margins:** Automatically adjust margins based on screen size and content.
- **E-Ink binarization:** Convert text to black/white for E-Ink displays.
- **Estimated reading time:** Show estimated time to finish chapter based on word count and reading speed.
- **Decision ✅:** All four tools (estimated reading time, smart-fit margins, page joining, E-Ink binarization).

### 3.2 Online Dictionary & Translation in Novel Reader — ⏭️ Skipped (D7)
**Effort:** Medium · **Impact:** Medium · **Dependencies:** None
**Status:** Skipped per D7. Can be revisited later.

Highlight text in the novel reader to look up definitions (Wiktionary) or translate to your preferred language.

- **What:** Text selection action mode in the novel reader adds "Define" and "Translate" actions. Define opens Wiktionary; Translate uses Google Translate / DeepL / configurable provider.
- **Reference:** Tadami's "Online Dictionary & Translation in Novel Reader". Hayai's `NovelTranslationView` with provider setup, language selectors, filters.
- **Decision ✅:** Skip for now. Can be revisited later.

### 3.3 Novel Tracking (NovelUpdates + NovelList) — ✅ Done
**Effort:** Medium · **Impact:** High · **Dependencies:** None
**Status:** Implemented (`649611e60`). NovelTracker interface, both trackers, interactors, and DomainModule registration.

Track novel reading progress with NovelUpdates and NovelList trackers, reusing the existing tracker infrastructure.

- **What:** Two new trackers added to the existing tracker system. Bidirectional chapter progress sync with offline retry queue.
- **Components:**
  - `data/track/novelupdates/NovelUpdates.kt` — NovelUpdates tracker.
  - `data/track/novellist/NovelList.kt` — NovelList tracker.
  - `data/track/novel/NovelTrackMapper.kt`, `NovelTrackRepositoryImpl.kt`.
  - Interactors: `RefreshNovelTracks`, `SyncNovelChapterProgressWithTrack`, `TrackNovelChapter`.
  - UI: Add to existing track tab in novel detail screen.
- **Reference:** Tadami added these in v0.40. Same tracker infrastructure as anime/manga.

### 3.4 TTS (Text-to-Speech) for Novels — ✅ Done (skeleton)
**Effort:** Medium (Android TTS) to Large (neural TTS) · **Impact:** Medium · **Dependencies:** None
**Status:** Skeleton committed (`9717cc051`). `NovelTtsPreferences`, `TtsEngine` interface, `AndroidTtsEngine` (working), `NeuralTtsEngine` (stub), `TtsController`. Real sherpa-onnx model loading is follow-up work.

Read novels aloud with TTS.

- **Option A — Android TTS:** Use Android's built-in TTS engine. Adjustable voice, pitch, speed. Background playback with media notification controls. Lower effort, lower quality.
- **Option B — Neural TTS companion:** Port Hayai's `HayaiTTS` — offline neural TTS with 186 voices via sherpa-onnx (Piper/Kokoro/Matcha). Higher quality, larger effort, separate companion app or integrated module.
- **Reference:** QuickNovel has TTS up to 7x speed. NovelDokusha has TTS with background playback, media notification, saved voice preferences. Hayai has neural TTS companion.
- **Decision ✅:** Neural TTS — port Hayai's sherpa-onnx-based neural TTS with 186 voices.

---

## Phase 4 — UI/UX Polish

Visual and interaction improvements.

### 4.1 Floating Nav Bar (Localised Glassmorphism) — ✅ Done
**Effort:** Small-Medium · **Impact:** Medium · **Dependencies:** None
**Status:** Implemented (`327363252`). `NavBarAppearance` enum, `FloatingGlassNavigationBar` composable, preference in `UiPreferences` + `HomeScreen`, setting in `SettingsAppearanceScreen`.

A floating, pill-shaped bottom navigation bar with localised glassmorphism (blur effect only on the nav bar, not everywhere).

- **What:** Custom Compose `NavigationBar` with:
  - Floating pill shape (rounded corners, margin from screen edge).
  - Glassmorphism: semi-transparent background with blur effect.
  - Configurable: floating pill vs traditional full-width bar (user preference).
  - Smooth animations on tab switch.
- **Where:** Settings → Appearance → Bottom navigation appearance.
- **Decision ✅:** Nav bar only — glassmorphism localised to the floating nav bar.

### 4.2 Achievements System — ✅ Done (skeleton)
**Effort:** Large · **Impact:** Medium-High (engagement) · **Dependencies:** None
**Status:** Skeleton committed (`a111738eb`). `Achievement` model, `AchievementEventBus`, `PointsManager`, `AchievementHandler`, in-memory repository, placeholder `achievements.json`, `AchievementScreenModel` + `AchievementScreen` UI. Populating real achievement rules and wiring to app events is follow-up work.

A gamification system with achievements, XP, levels, and unlockable rewards.

- **What:**
  - 10 achievement types: QUANTITY, EVENT, DIVERSITY, STREAK, LIBRARY, META, BALANCED, SECRET, TIME_BASED, FEATURE_BASED.
  - 4 categories: ANIME, MANGA, BOTH, SECRET.
  - Tiered achievements with progressive rewards.
  - XP system with unlockable themes and badges.
  - Secret achievements (e.g. "Time Paradox" with tab-glow rewards).
  - Genre badges (Romance, Isekai, Horror...).
- **Architecture:**
  - `AchievementHandler` subscribes to `AchievementEventBus` (SharedFlow).
  - `PointsManager` for levels.
  - `DiversityAchievementChecker` (genre/source diversity), `StreakAchievementChecker` (daily streaks).
  - Config in `app/src/main/assets/achievements/achievements.json`.
  - DB tables: `achievements`, `achievement_progress`, `achievement_activity`, `user_points`.
  - UI: `AchievementScreenModel` + presentation components.
- **Decision ✅:** Full system — all 10 types, 4 categories, XP, levels, unlockable themes/badges, secret achievements.

### 4.3 Incognito Policies — ✅ Done
**Effort:** Small · **Impact:** Medium · **Dependencies:** None
**Status:** Implemented (`7395af608`). `IncognitoPolicy` enum (MANUAL_ONLY, NSFW_AUTO), `IncognitoStateLogic`, `isNsfwForSource()` on all three extension managers, `GetNovelIncognitoState` (+ updated manga/anime), UI setting in `SettingsBrowseScreen`.

"Automatic incognito" with "NSFW sources" mode — auto-enables incognito for NSFW extensions while global incognito is off.

- **What:** New setting: incognito mode can be "Off", "Always" (existing), or "Automatic (NSFW sources)". In automatic mode, NSFW-flagged extensions are always incognito. Per-extension toggles still work.
- **Where:** Settings → Privacy. Extension model needs `isNsfw` flag (already exists in `NovelExtension`).
- **Reference:** Tadami v0.56.1.

### 4.4 Tabbed Library Display — ✅ Done
**Effort:** Medium · **Impact:** Medium · **Dependencies:** None
**Status:** Implemented (`798782340`). `LibraryCategoryDisplay` enum (TABBED, CONTINUOUS), `categoryDisplayMode` preference in `LibraryPreferences`, `MangaLibraryContinuousContent` composable, conditional rendering in `MangaLibraryContent`, UI toggle in `MangaLibrarySettingsDialog`.

Each library category on its own swipeable page with a pinned tab strip and per-tab item counts.

- **What:** Alternative to continuous scroll library. Each category = one page in a horizontal pager. Pinned tab strip shows category names with counts. Separate pull-to-refresh per tab. Whole-library update moved to toolbar menu.
- **Where:** Library screen. Offer layout choice during onboarding.
- **Reference:** Hayai's "Tabbed library display".

---

## Phase 5 — Architecture (Optional, Larger Effort)

### 5.1 Navigation Framework Migration — ⏭️ N/A (already on Voyager)
**Effort:** Large · **Impact:** High (smoothness) · **Dependencies:** Decision D1
**Status:** Not needed — Miko is already on Voyager (D1).

If Miko is on Conductor, migrating to Voyager (Compose-first navigation) would be the biggest smoothness win, matching Tadami's feel.

- **What:** Replace Conductor-based navigation with Voyager 1.1.0-beta03. Screens extend `Screen` with `@Composable Content()`. ScreenModels extend `StateScreenModel`.
- **Benefits:** Compose-first architecture, state hoisting, smoother transitions, better integration with modern Compose patterns.
- **Risk:** Large refactor touching every screen. High regression risk.
- **Decision ❓:** ❓ See Decision D1 — is Miko on Voyager or Conductor? If already Voyager, skip this.

### 5.2 Extension Stores (Architectural) — ⏭️ Deferred (D11)
**Effort:** Medium-Large · **Impact:** Low-Medium · **Dependencies:** None
**Status:** Deferred per D11. Keep current extension repository model.

Replace the old extension repository model with a unified "Extension Stores" architecture that fetches metadata from remote store indexes.

- **What:** Per-media-type store management. Add/rename/delete/refresh stores. Signing-key conflict dialog. Backup/restore of stores. Deep links (`miko://extension-store`).
- **Decision ✅:** Defer — keep current model for now. May revisit later if user base grows.

---

## Resolved Decisions

All decisions have been made by the user. Implementation can proceed.

### D1 — Navigation Framework ✅ Already on Voyager
Miko is already using Voyager (confirmed: 182 files import `cafe.adriel.voyager`, `app/build.gradle.kts` has `libs.bundles.voyager`). No migration needed. Just adopt perf patterns: FastScrollLazyColumn, cold-launch deferral, state hoisting.

### D2 — Novel Extension Model ✅ Both with linking (Level 2), reverse-compatible
Support both APK and JS novel extensions with linked sources bridging them. **Critical addition:** backups must be reverse-compatible — if a user backs up from Miko and restores on a JS-only app (Tadami/Hayai), they should retain all their data in JS manner. This means **both APK and JS data need to be stored** in backups. For each linked novel entry, the backup should include:
- The APK source's novel entry (sourceId, url, chapters, history, tracking).
- The JS source's novel entry (sourceId, url, chapters, history, tracking).
- The link relationship between them.
On restore: if the target app supports APK, APK entries recover. If JS-only, JS entries recover. If both, links restore and comments become available.

### D3 — Linked Sources Dedup ✅ Dedup by default, "Show duplicates" in filter
Chapters from linked sources are deduplicated by chapter number by default (one row per chapter, prefer primary source). The existing filter icon in the chapter list gets a new option: "Show duplicates" — when enabled, all chapters from all linked sources are shown as separate entries. This reuses the existing filter UI rather than adding a new control.

### D4 — Similar Titles Scope ✅ All media types
Port the full suggestions system for novels, manga, and anime. AniList covers all three. MAL/Jikan for anime. MangaUpdates for manga + novels. NovelUpdates for novels only.

### D5 — Home Hub Design ✅ Minimal
Just recently viewed + recently added, no greeting header or hero card. Simpler than Tadami's Aurora Home. Respects media type toggles (hide sections for disabled types).

### D6 — Novel Reader Tools ✅ All four
Implement all novel reader tools: estimated reading time, smart-fit margins, page joining, and E-Ink binarization.

### D7 — Dictionary & Translation ✅ Skip for now
Not a priority. Can be revisited later.

### D8 — TTS Approach ✅ Neural TTS
Port Hayai's sherpa-onnx-based neural TTS. 186 voices (Piper/Kokoro/Matcha/ZipVoice). High quality. Large effort, larger APK. Background playback with media notification controls.

### D9 — Glassmorphism Scope ✅ Nav bar only
Glassmorphism (blur/transparency) localised to the floating nav bar only. Not used elsewhere in the app.

### D10 — Achievements Scope ✅ Full system
Full achievements system: all 10 types, 4 categories, XP, levels, unlockable themes/badges, secret achievements. Like Tadami. Large effort.

### D11 — Extension Stores Refactor ✅ Defer
Keep the current extension repository model for now. May revisit later if the user base grows.

### D12 — Media Type Toggle Behaviour ✅ Hide tabs only
When a media type is toggled off, only hide the main navigation tabs (Library/Browse/History for that type). Extensions and settings for that type remain accessible. Less aggressive than Tadami's approach.

---

## Suggested Implementation Order (decisions resolved)

1. **Phase 0** (foundation): Cloudflare bypass → Media toggles (tabs only) → Perf patterns (Voyager already, just FastScroll + deferred init) → Reduce motion. — ✅ All done
2. **Phase 1** (migration): Source matching (Level 1) → JS runtime port → Linked sources (Level 2, with reverse-compatible backups storing both APK + JS data, dedup by default with "Show duplicates" filter option). — ✅ All done (JS runtime is skeleton)
3. **Phase 2** (discovery): Similar titles (all media types) → Genre search → Home hub (minimal: recently viewed + recently added). — ✅ All done (suggestions is skeleton)
4. **Phase 3** (reading): Novel reader tools (all 4) → Novel tracking (NovelUpdates + NovelList) → Neural TTS (sherpa-onnx). Dictionary/translation skipped. — ✅ All done (TTS is skeleton)
5. **Phase 4** (polish): Floating nav bar (glassmorphism nav bar only) → Achievements (full system) → Incognito policies → Tabbed library. — ✅ All done (achievements is skeleton)
6. **Phase 5** (architecture): Extension Stores deferred. Navigation migration not needed (already on Voyager). — ⏭️ Deferred / N/A

This order front-loads high-impact, low-effort features and defers large architectural work until the user base benefits from quick wins.

---

## Next Steps

The Phase 0–4 milestone is complete. Remaining work falls into two buckets:

### Production hardening (turning skeletons into working features)
- **JS Plugin Runtime (1.2):** Full QuickJS integration — replace stub with real JS engine, plugin install/update/uninstall, repo fetching.
- **Suggestions (2.1):** Complete AniList scoring pipeline, add MangaUpdates/NovelUpdates external sources, wire `AuroraSuggestionsRow` into detail screens.
- **Neural TTS (3.4):** Replace `NeuralTtsEngine` stub with real sherpa-onnx model loading + inference; add model download UI.
- **Achievements (4.2):** Populate `achievements.json` with real achievement definitions; wire `AchievementHandler` to actual app events (chapter read, library added, streak tracking); persist to DB.

### Working-tree cleanup
- Many uncommitted changes from earlier sessions (UI refactors, novel components, cover-color extraction via `androidx.palette`, consolidated extension repos screen, etc.) are outside the scope of these phases. They need a review/commit pass or selective revert.
