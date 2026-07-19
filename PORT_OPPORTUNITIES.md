# Miko — Port Opportunities & Missing Tiers

Audit results from cross-repo comparison (2026-07-19). Each tier lists what's missing,
where to find the reference implementation, and the estimated effort to port.

Reference repos (cloned to `C:/Users/karol/OneDrive/Documents/GitHub/`):
- `Tadami/` — andarcanum/Tadami-Aniyomi-fork (primary reference for most tiers)
- `Mohyeong/` — sjoygsh/Mohyeong (linked sources)
- `NovelDokusha/` — nanihadesuka/NovelDokusha (Cloudflare bypass)
- `WebnovelReader/` — Nabeelshar/WebnovelReader (active NovelDokusha fork)
- `HayaiTTS/` — HayaiApp/HayaiTTS (pre-done neural TTS voices, sherpa-onnx)
- `QuickNovel/` — LagradOst/QuickNovel (TTS background playback reference)

---

## Tier 2 — Cloudflare Bypass Improvements

**Status:** aniyomi-fork has a basic implementation (~70%). Tadami and NovelDokusha both have better versions.

### What's missing (from Tadami)

| Feature | Tadami file | Effort |
|---------|-------------|--------|
| Host-level locking (`ConcurrentHashMap<String, Any>`) | `CloudflareInterceptor.kt` | Small |
| Immediate cookie retry (avoids unnecessary WebView) | `CloudflareInterceptor.kt` | Small |
| Double retry after WebView (cookie propagation delay) | `CloudflareInterceptor.kt` | Small |
| Interactive widget detection (Turnstile JS probe) | `CloudflareInterceptor.kt` | Small |
| `CloudflareChallengeResolver` abstraction | `CloudflareChallengeResolver.kt` | Small |
| Dedicated exception types (`CloudflareInteractiveChallengeException`) | `CloudflareInterceptor.kt` | Small |
| `CookieManager.flush()` after bypass | `CloudflareInterceptor.kt` | Trivial |
| Reduce peek bytes from 64KB to 8KB | `CloudflareInterceptor.kt` | Trivial |
| Comprehensive tests | `CloudflareInterceptorTest.kt`, `CloudflareChallengeResolverTest.kt` | Medium |

### NovelDokusha's alternative approach (worth studying)

NovelDokusha's `CloudfareVerificationInterceptor.kt` (517 lines) has:
- Tightened false-positive prevention: requires both body markers AND `Server: cloudflare` header
- Turnstile detection on HTTP 200 (not just 403/503)
- `LaunchedEffect` polls CookieManager directly for cookie changes
- Auto-dismisses WebView on Turnstile cookie without page reload
- `BrowserHeadersInterceptor` adds browser-like headers to avoid detection

**Reference commits to study (from the HnDK0/NoveLA fork lineage):**
- `bdf3cb9` — initial implementation
- `05a95b8` — detection improvements (tightened false positives)
- `360c84d` — cookie persistence
- `9fd25d8` — thread safety (ConcurrentHashMap DNS cache)

### Plan

1. Port Tadami's host-level locking + immediate cookie retry + double retry
2. Port NovelDokusha's tightened false-positive logic + Turnstile-on-200 detection
3. Add `CloudflareChallengeResolver` abstraction for testability
4. Add `BrowserHeadersInterceptor` from NovelDokusha
5. Add comprehensive tests from Tadami

**Estimated effort:** 1-2 days

---

## Tier 3 — Novel Reader Features

**Status:** aniyomi-fork has ~40%. Missing major Tadami features.

### What's missing (from Tadami's `NovelReaderPreferences.kt`)

| Feature | Tadami preference | Notes |
|---------|-------------------|-------|
| Typography presets | SUPERGOLDEN, GOLDEN, CUSTOM | Mathematical text-size/line-height ratios |
| Force paragraph indent | `forceParagraphIndent()` | |
| Preserve source text align | `preserveSourceTextAlignInNative()` | |
| Custom font family | `customFontFamily()` | |
| Force bold/italic text | `forceBoldText()`, `forceItalicText()` | |
| Text shadow | `textShadowColor()`, `textShadowBlur()`, `textShadowX()`, `textShadowY()` | Readability in bright backgrounds |
| Page edge shadow | `pageEdgeShadowAlpha()` | |
| Background textures | PAPER_GRAIN, LINEN, PARCHMENT | |
| Native texture strength | `nativeTextureStrength()` | |
| Appearance presets | Multiple preset modes | |
| Custom backgrounds | `customBackgroundSource()` | |
| OLED edge gradient | `oledEdgeGradient()` | |
| Custom themes | User-defined color themes | |
| Page transition styles | SLIDE, etc. | |
| Book flip animation speed | `bookFlipAnimationSpeed()` | |
| Page turn speed/intensity/zone | `pageTurnSpeed()`, `pageTurnIntensity()`, `pageTurnActivationZone()` | |
| Vertical seekbar | `verticalSeekbar()` | |
| Swipe to next/prev chapter | `swipeToNextChapter()`, `swipeToPrevChapter()` | |
| Tap to scroll | `tapToScroll()` | |
| Auto-scroll with adaptive delay | `autoScrollAdaptiveDelay()` | |
| Auto-scroll chapter end behavior | StopAtEnd, AdvanceAndStop, ContinuousReading | |
| Show auto-scroll floating button | `showAutoScrollFloatingButton()` | |
| Prefetch next chapter | `prefetchNextChapter()` | |
| Show scroll percentage | `showScrollPercentage()` | |
| Show battery and time | `showBatteryAndTime()` | |
| Show Kindle info block | `showKindleInfoBlock()` | |
| Show time to end | `showTimeToEnd()` | |
| Show word count | `showWordCount()` | |
| Text selection enabled | `textSelectionEnabled()` | |
| Selected text translation | Multiple providers: Gemini, OpenRouter, DeepSeek, Mistral, NVIDIA, Ollama | |
| Novel dictionary | `novelDictionary()` | Wiktionary integration |
| TTS in reader | Full TTS with voice selection, speech rate, pitch, highlight modes | See Tier 7 |
| Comprehensive E-Ink optimization | Separate E-Ink screen, auto-optimization | |

### Plan

1. Port typography presets (SUPERGOLDEN/GOLDEN) — quick win, big readability improvement
2. Port text shadow settings — readability in bright environments
3. Port background textures (paper grain, linen, parchment) — aesthetic
4. Port auto-scroll with adaptive delay + chapter end behavior — hands-free reading
5. Port text selection + translation providers — language learners
6. Port novel dictionary (Wiktionary) — language learners
7. Port E-Ink comprehensive settings — e-reader users
8. Port page transition styles + book flip animation — aesthetic
9. Port custom themes — personalization

**Estimated effort:** 5-7 days

---

## Tier 4 — Home Hub Enhancement

**Status:** aniyomi-fork has ~15% (minimal history rows). Tadami has 1905-line version.

### What's missing (from Tadami's `HomeHubTab.kt` — 1905 lines)

| Feature | Tadami file | Notes |
|---------|-------------|-------|
| Greeting system | `HomeHubTab.kt` | Time-based, customizable ("Good morning, Karol") |
| User profile section | `HomeHubTab.kt` | Avatar, nickname, profile title |
| Streak counter | `HomeHubTab.kt` | Reading streak display with multiple styles |
| Hero card | `HomeHubTab.kt` | Large featured entry with progress, play/read actions |
| Media-specific sections | `HomeHubTab.kt` | Anime, Manga, Novel tabs |
| Header layout customization | `HomeHubHeaderSections.kt` | Drag-and-drop header elements |
| Scroll-based header collapse | `HomeHubTab.kt` | Smooth hiding on scroll |
| Category filters | `HomeHubTab.kt` | Per-section category filtering |
| Fast cache | `HomeHubFastCache` | Instant load on cold start |
| Recommendations section | `HomeHubTab.kt` | AI-powered suggestions (depends on Tier 5) |
| Achievement display | `HomeHubTab.kt` | Unlocked achievements count |
| Month stats | `HomeHubTab.kt` | Episodes watched, library size, chapters read |
| Aurora glass effects | `HomeHubTab.kt` | Sophisticated visual effects |
| Tabbed navigation | `AuroraTabRow` | Media tabs |

### Plan

1. Port greeting system + user profile section — quick visual win
2. Port hero card (featured entry with progress) — high visual impact
3. Port streak counter — gamification, ties into achievements
4. Port `HomeHubFastCache` — performance, instant load
5. Port scroll-based header collapse — UX polish
6. Port category filters — power users
7. Port month stats — reading statistics
8. Port achievement display — ties into existing achievements system
9. Port recommendations section — depends on Tier 5 (Suggestions)
10. Port Aurora glass effects + tabbed navigation — aesthetic

**Estimated effort:** 4-6 days

---

## Tier 5 — Suggestions Pipeline

**Status:** aniyomi-fork has ~25% (AniList works, no UI, no other sources, no fallback engines).

### What's missing

| Component | Tadami file | Priority |
|-----------|-------------|----------|
| `SuggestionCache` | `SuggestionCache.kt` | HIGH — avoids rate limiting |
| `MultilingualQueryHelper` | `MultilingualQueryHelper.kt` | HIGH — genre translation, Cyrillic |
| Enhanced `SuggestionTitleResolver` | `SuggestionTitleResolver.kt` | HIGH — missing 5 methods |
| MAL (Jikan) source | `MyAnimeListRecommendationSource.kt` + `JikanDto.kt` | MEDIUM |
| MangaUpdates source | `MangaUpdatesSimilarSource.kt` + `MangaUpdatesDto.kt` | MEDIUM |
| NovelUpdates source | `NovelUpdatesSimilarSource.kt` | MEDIUM |
| Anime fallback engine | `AnimeSearchFallbackEngine.kt` + outcomes | MEDIUM |
| Manga fallback engine | `MangaSearchFallbackEngine.kt` + outcomes | MEDIUM |
| Novel fallback engine | `NovelSearchFallbackEngine.kt` + outcomes | MEDIUM |
| Novel related coordinator | `NovelRelatedSuggestionCoordinator.kt` | LOW |
| `AuroraSuggestionsRow` UI | `AuroraSuggestionsRow.kt` | HIGH — carousel component |
| `EntrySuggestionsScreen` | `EntrySuggestionsScreen.kt` | MEDIUM — full-page view |
| ScreenModel integration | `MangaScreenModel.kt`, `AnimeScreenModel.kt`, `NovelScreenModel.kt` | HIGH — wire into detail screens |
| Screen UI integration | `MangaScreen.kt`, `AnimeScreen.kt`, `NovelScreen.kt` | HIGH — wire handlers |
| Preferences | `SourcePreferences.kt` | HIGH — toggle, expand, overflow, source gates |
| DI setup | `AppModule.kt` | HIGH — register all components |

### Plan

1. Port `SuggestionCache` (1h) — in-memory cache with 24h TTL
2. Enhance `SuggestionTitleResolver` (1h) — add missing methods
3. Port `MultilingualQueryHelper` (2h) — genre translation + title translation
4. Add preferences to `SourcePreferences.kt` (1h)
5. Port MAL/Jikan source + DTO (2h)
6. Port MangaUpdates source + DTO (2h)
7. Port NovelUpdates source (2h) — HTML scraping
8. Update `SuggestionCoordinator` with all sources (2h)
9. Port fallback engines + outcomes (6h)
10. Port `AuroraSuggestionsRow` UI (2h)
11. Port `EntrySuggestionsScreen` + navigation (2h)
12. Integrate into ScreenModels (6h) — manga, anime, novel
13. Integrate into Screens (6h) — manga, anime, novel

**Estimated effort:** 6-8 days

---

## Tier 6 — JS Plugin Runtime

**Status:** aniyomi-fork has ~5% (5 of 56 files, uses QuickJS). Tadami has full J2V8 implementation.

### Critical decision: QuickJS vs J2V8

| Option | Pros | Cons |
|--------|------|------|
| **Keep QuickJS** | Already in Gradle, lighter weight | Must rewrite all J2V8 bindings, may have compat issues |
| **Switch to J2V8** (recommended) | Direct port of proven implementation, LNReader compat tested | Larger native library, exclude QuickJS from build |

### What's missing (37 critical files across 7 layers)

**Layer 1: Runtime Core (8 files)**
- `NovelJsRuntime.kt` (J2V8 version), `NovelJsRuntimeFactory.kt`, `NovelJsRuntimeBinder.kt`
- `NovelJsSource.kt` (2268 lines in Tadami!), `NovelJsSourceFactory.kt`
- `NovelJsPromiseShim.kt`, `NovelJsDomStore.kt`, `NovelPluginScriptSanitizer.kt`

**Layer 2: Data Layer (5 files)**
- `domain/model/NovelPlugin.kt`, `domain/repository/NovelPluginRepository.kt`
- `data/NovelPluginRepositoryImpl.kt`, `data/NovelPluginStorage.kt`, `data/NovelPluginKeyValueStore.kt`

**Layer 3: Plugin Management (6 files)**
- `DefaultNovelExtensionManager.kt`, `NovelPluginSourceFactory.kt`
- `NovelPluginDownloader.kt`, `NovelPluginInstaller.kt`
- `NovelPluginRepoParser.kt`, `NovelPluginRepoService.kt`

**Layer 4: Features (8 files)**
- `NovelPluginSettingsBridge.kt`, `NovelPluginFilterMapper.kt`, `NovelPluginFilters.kt`
- `NovelPluginCapabilities.kt`, `NovelPluginResultNormalizer.kt`
- `NovelPluginWebStorageBridge.kt`, `NovelPluginAssetBindings.kt`, `NovelConfigurableJsSource.kt`

**Layer 5: Source Integration (5 files)**
- `NovelSiteSource.kt`, `NovelWebUrlSource.kt`, `NovelPluginCapabilitySource.kt`
- `NovelPluginSettingsSource.kt`, `NovelPluginIdentitySource.kt`
- Update `AndroidNovelSourceManager` to support JS sources

**Layer 6: Repo Integration (5 files)**
- `NovelPluginRepoUpdateInteractor.kt`, `NovelPluginChecksum.kt`
- `NovelExtensionListingInteractor.kt`, `NovelPluginApi.kt`, `NovelPluginIndexParser.kt`

**Layer 7: Database Migration**
- Add `novel_plugins` table to database schema

### Plan

1. Decide on JS engine (recommend J2V8)
2. Add J2V8 dependency, exclude QuickJS from build
3. Port Layer 1 (Runtime Core) — 3-4 days
4. Port Layer 2 (Data) — 2-3 days
5. Port Layer 3 (Management) — 3-4 days
6. Port Layer 5 (Source Integration) — 2-3 days
7. Port Layer 4 (Features) — 3-4 days
8. Port Layer 6 (Repo Integration) — 2-3 days
9. Testing with real LNReader plugins — 3-5 days

**Estimated effort:** 3-4 weeks

### Important: Comments feature impact

Miko's `NovelSource` interface has `supportsComments` and `getChapterComments()`. Tadami's `NovelJsSource` has NO comments support. If switching to JS runtime, comments would be lost unless explicitly re-implemented in the JS plugin API layer. LNReader plugins don't have a comments concept natively.

**Recommendation:** Support both APK + JS (option c from FORK_RESEARCH_NOTES.md). Only APK extensions support comments; JS plugins would not unless the JS API is extended.

---

## Tier 7 — Neural TTS (sherpa-onnx)

**Status:** aniyomi-fork has ~20%. Android TTS works, neural TTS is completely stubbed. No background service.

### What's working
- `AndroidTtsEngine.kt` — fully functional
- `TtsController.kt` — basic paragraph-by-paragraph reading
- `NovelTtsPreferences.kt` — preferences defined
- "Read Aloud" button in selection popup

### What's stubbed/missing

| Component | Status | Reference |
|-----------|--------|-----------|
| `NeuralTtsEngine.initialize()` | Stubbed | HayaiTTS |
| `NeuralTtsEngine.speak()` | Stubbed | HayaiTTS |
| `NeuralTtsEngine.stop/pause/resume()` | Stubbed | HayaiTTS |
| `NeuralTtsEngine.setVoice/setLanguage()` | Stubbed | HayaiTTS |
| sherpa-onnx dependency | Missing | XDcobra Maven |
| Foreground service | Missing | Tadami `NovelTtsPlaybackService.kt` (330 lines) |
| Media notification | Missing | Tadami |
| MediaSessionCompat | Missing | Tadami |
| Audio focus manager | Missing | Tadami `NovelTtsAudioFocusManager.kt` |
| Headset disconnect handling | Missing | QuickNovel `BecomingNoisyReceiver.kt` |
| TTS settings UI | Missing | Tadami |
| TTS controls UI | Missing | Tadami |
| Voice download/management UI | Missing | HayaiTTS |
| Model download manager | Missing | HayaiTTS |
| Session checkpointing | Missing | Tadami `NovelTtsSessionStore.kt` |
| Word-level highlighting | Missing | Tadami `NovelTtsHighlightEstimator.kt` |

### HayaiTTS — pre-done neural TTS voices

**Repo:** `C:/Users/karol/OneDrive/Documents/GitHub/HayaiTTS/`
**Features:**
- sherpa-onnx integration (proven on Android)
- 600+ voices across 7 model families (Piper, VITS, Matcha, Kokoro, Kitten, ZipVoice, Pocket, Supertonic)
- Voice cloning for ZipVoice/Pocket
- Material 3 Expressive UI
- In-app voice downloader
- Streaming runtime
- System-wide TTS engine (registers in Android settings)

**APK size impact:** sherpa-onnx native libs are ~10-20 MB per architecture. User accepts this tradeoff.

### sherpa-onnx dependency options

| Option | Source | Effort |
|--------|--------|--------|
| XDcobra Maven (easiest) | `https://xdcobra.github.io/maven/` | 30 min |
| Official GitHub releases | `https://github.com/k2-fsa/sherpa-onnx` | 1h (build AAR) |
| HayaiTTS companion approach | Install HayaiTTS as separate app | 1h (docs only) |

### Plan

**Path A: Android TTS + Background Playback (MVP, 3-5 days)**
1. Wire existing `TtsController` to reader (replace old TTS code)
2. Add TTS settings UI to `NovelReaderSettingsDialog`
3. Add TTS controls UI (play/pause/skip buttons)
4. Port Tadami's `NovelTtsPlaybackService` (foreground service)
5. Port Tadami's media notification
6. Port Tadami's `NovelTtsAudioFocusManager`
7. Port QuickNovel's `BecomingNoisyReceiver` (headset disconnect)

**Path B: Neural TTS with sherpa-onnx (additional 5-7 days)**
1. Add sherpa-onnx dependency (XDcobra Maven)
2. Implement `NeuralTtsEngine.initialize()` — load model files
3. Implement `NeuralTtsEngine.speak()` — call `sherpaTts.generate()`, play via AudioTrack
4. Implement `NeuralTtsEngine.stop/pause/resume()` — AudioTrack control
5. Implement `NeuralTtsEngine.setVoice()` — switch model files
6. Port HayaiTTS voice download/management UI
7. Port HayaiTTS voice catalog (600+ voices)
8. Add voice preview (play sample text before selecting)

**Path C: HayaiTTS Companion (quickest, 3-4 days)**
1. Document HayaiTTS installation for users
2. Add engine selector in TTS settings (let users choose system TTS engines)
3. Implement foreground service (Path A steps 4-7)
4. No sherpa-onnx integration needed in aniyomi-fork

**Recommended:** Path A first (get background playback working), then Path B (add neural TTS).

**Estimated effort:** 3-5 days (Path A) + 5-7 days (Path B) = 8-12 days total

---

## Tier 8 — Linked Sources (from Mohyeong)

**Status:** aniyomi-fork has ~60% (basic linking, missing dedup/fallback/quality priority).

### Mohyeong's implementation

**Repo:** `C:/Users/karol/OneDrive/Documents/GitHub/Mohyeong/`
**Key files:**
- `data/src/main/sqldelight/tachiyomi/data/manga_links.sq` — `manga_links` table
- `data/src/main/java/tachiyomi/data/manga/MangaLinkRepositoryImpl.kt`
- `domain/.../manga/repository/MangaLinkRepository.kt`
- `domain/.../manga/interactor/GetLinkedMangas.kt`
- `MangaScreenModel.kt` — merging logic

### What's missing in aniyomi-fork

| Feature | Mohyeong | aniyomi-fork |
|---------|----------|--------------|
| `manga_links` table | ✅ | ✅ (as `novel_links`) |
| Cluster merging | ✅ | ✅ Basic |
| Primary/secondary designation | ✅ | ✅ |
| Extension type tracking | ❌ | ✅ (apk/js) |
| Chapter number-based dedup | ❌ | ❌ Missing |
| Fallback logic (primary fails → linked) | ❌ | ❌ Missing |
| Quality-based source priority | ❌ | ❌ Missing |
| Conflict resolution | ❌ | ❌ Missing |

### Plan

1. Study Mohyeong's `manga_links.sq` and `MangaLinkRepositoryImpl.kt`
2. Add chapter number-based dedup to aniyomi-fork's linking
3. Add fallback logic (if primary source fails, try linked sources)
4. Add quality-based source priority (user preference per cluster)
5. Add conflict resolution for conflicting chapter metadata
6. Design source-picker UI (bottom sheet listing each source's version)

**Estimated effort:** 3-4 days

---

## Tier 9 — Source Matching (Phase 1.1)

**Status:** NOT IMPLEMENTED in aniyomi-fork. Tadami has `SourceMangaRatingSourceMatcher`.

### What's missing

| Feature | Tadami file | Notes |
|---------|-------------|-------|
| `SourceMangaRatingSourceMatcher` | `SourceMangaRatingSourceMatcher.kt` | Source family detection for rating aggregation |
| GroupLE source family | — | readmanga, mintmanga, seimanga, selfmanga, usagi, allhentai, rumix |
| InkStory source family | — | detects by source name OR base URL host |
| Madara source family | — | detects by class name |
| Multi-criteria matching | — | source name + base URL + class name |
| Source family enums | — | for grouping related sources |
| Tests | `SourceMangaRatingSourceMatcherTest.kt` | |

### Plan

1. Port `SourceMangaRatingSourceMatcher` from Tadami
2. Add source family enums (GroupLE, InkStory, Madara)
3. Add multi-criteria matching (name + URL + class name)
4. Port tests

**Estimated effort:** 1-2 days

---

## Tier 10 — Novel Tracking Improvements

**Status:** aniyomi-fork has ~70%. Missing Tadami's sophisticated sync resolution.

### What's missing (from Tadami)

| Feature | Tadami file | Notes |
|---------|-------------|-------|
| `ResolveTrackProgressSync` | — | Sophisticated sync resolution logic |
| `SyncAction` enum | — | NoOp, MarkLocalUntil, PushRemoteTo |
| Trigger-based sync | — | OPEN_REFRESH, etc. |
| Error handling with logging | — | Catches and logs sync failures |
| Tests | `SyncNovelChapterProgressWithTrackTest.kt`, `AddNovelTracksTest.kt` | |

### Plan

1. Port `ResolveTrackProgressSync` from Tadami
2. Add `SyncAction` enum
3. Add trigger-based sync (different behaviors for different triggers)
4. Improve error handling (catch and log instead of crash)
5. Port tests

**Estimated effort:** 2-3 days

---

## Tier 11 — Tabbed Library Display

**Status:** aniyomi-fork has ~60%. Missing Tadami's pinned items, series support, stacked covers.

### What's missing (from Tadami)

| Feature | Tadami file | Notes |
|---------|-------------|-------|
| Pinned items support | `MangaLibraryPager.kt` | `onTogglePinned` callback |
| Series support | `MangaLibraryPager.kt` | `onSeriesClicked` for grouped entries |
| Stacked cover cards | `MangaLibraryCompactGrid.kt` | Visual grouping for series |
| Pinned badge | — | Visual indicator |
| Better selection handling | — | `selectedItems` vs `selectedManga` |
| Aurora library cards | — | Enhanced visual effects |
| Performance mode | — | For large grids |

### Plan

1. Add pinned items support (`onTogglePinned` callback)
2. Add series support (`onSeriesClicked` for grouped entries)
3. Add stacked cover cards (visual grouping)
4. Add pinned badge (visual indicator)
5. Add performance mode for large grids

**Estimated effort:** 2-3 days

---

## Tier 12 — Incognito Policies

**Status:** aniyomi-fork has ~90%. Missing tests and foreground/reader states.

### What's missing (from Tadami)

| Feature | Tadami file | Notes |
|---------|-------------|-------|
| Test coverage | `GetNovelIncognitoStateTest.kt` | 19 test cases |
| Foreground incognito state | — | Additional use case |
| Novel reader incognito state | — | Reader-specific logic |

### Plan

1. Port 19 test cases from Tadami
2. Add foreground incognito state use case
3. Add novel reader incognito state logic

**Estimated effort:** 1 day

---

## Summary: Priority Order

| Tier | Feature | Effort | Impact | Dependencies |
|------|---------|--------|--------|--------------|
| 2 | Cloudflare Bypass | 1-2 days | HIGH | None |
| 9 | Source Matching | 1-2 days | HIGH | None |
| 12 | Incognito Policies | 1 day | LOW | None |
| 8 | Linked Sources | 3-4 days | MEDIUM | None |
| 10 | Novel Tracking | 2-3 days | MEDIUM | None |
| 11 | Tabbed Library | 2-3 days | MEDIUM | None |
| 3 | Novel Reader | 5-7 days | HIGH | None |
| 4 | Home Hub | 4-6 days | HIGH | Tier 5 (partial) |
| 7 | TTS (Path A) | 3-5 days | HIGH | None |
| 7 | TTS (Path B) | +5-7 days | HIGH | Path A |
| 5 | Suggestions | 6-8 days | HIGH | None |
| 6 | JS Plugin Runtime | 3-4 weeks | HIGH | None |

**Total estimated effort:** ~8-10 weeks for all tiers
