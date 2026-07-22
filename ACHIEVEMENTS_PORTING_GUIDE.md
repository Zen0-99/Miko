# Achievements System — Analysis & Porting Guide

**Status:** Achievements system is **~90% ported from Tadami but NOT WIRED IN**. All
code exists (models, DB, rules, UI, event bus, handler) but the runtime is never
started and the UI is unreachable. This is a wiring problem, not a porting problem.

**Companion repos:**
- aniyomi-fork (this repo): `C:/Users/karol/OneDrive/Documents/GitHub/aniyomi-fork`
- Tadami (reference): `C:/Users/karol/OneDrive/Documents/GitHub/Tadami`

**Git history (aniyomi-fork):**
- `a111738eb` — feat: Phase 4.2 — Achievements system (skeleton)
- `9fd3a7a4d` — feat: port full achievements system from Tadami
- `9ea6a973f` — fix: wire achievement integration points and unstub RuleContext

---

## TL;DR — What's Broken

The achievements system in aniyomi-fork is a complete port of Tadami's system
(70+ files, 4 DB tables, 20+ rules, 80+ achievement definitions, full UI) but
**six wiring points are missing**, so nothing actually runs:

| # | Wiring point | Tadami | aniyomi-fork |
|---|---|---|---|
| 1 | `achievementHandler.start()` called on app launch | `App.kt:322` | **MISSING** |
| 2 | `loader.loadAchievements()` called on app launch | `App.kt:268` | **MISSING** (only loads when screen opens) |
| 3 | `sessionManager.onSessionStart/End()` in lifecycle | `App.kt:454,471` | **MISSING** (events emitted but no handler listening) |
| 4 | `AchievementUnlockBanner()` + `AchievementGroupNotification()` rendered in MainActivity | `MainActivity.kt:419,429` | **MISSING** |
| 5 | `AchievementBannerManager.setInReaderOrPlayer()` in Reader/Player activities | `ReaderActivity.kt:224,322`, `PlayerActivity.kt:246,320` | **MISSING** |
| 6 | Navigation entry to achievements screen (MoreTab + Settings) | `MoreTab.kt:109`, `SettingsNavigationItems.kt:108-113` | **MISSING** (AchievementsTab exists but is in no nav list) |

**Result:** Events ARE emitted (chapter read, episode watched, novel chapter read,
library added, app start, session end) into the `AchievementEventBus` SharedFlow,
but nobody is subscribed to process them. The `AchievementHandler.start()` method
exists and would subscribe, but it is never called. The achievements DB is created
at startup (eager init in `AppModule.kt:398`) but stays empty because
`AchievementLoader.loadAchievements()` is only invoked from
`AchievementScreenModel` — which is never instantiated because the screen is
unreachable.

---

## System Architecture (already ported — for reference)

```
User action (chapter read, etc.)
    │
    ▼
SetReadStatus.kt / SetSeenStatus.kt / SetNovelReadStatus.kt
    │  eventBus.tryEmit(AchievementEvent.ChapterRead(...))
    ▼
AchievementEventBus (SharedFlow)  ← events buffer here, nobody listening
    │  (would be subscribed to by:)
    ▼
AchievementHandler.processEvent(event)
    │  ├── streakChecker.logChapterRead()
    │  ├── diversityChecker.clearCache()
    │  ├── featureCollector.onFeatureUsed()
    │  └── for each achievement:
    │        ruleRegistry.getRule(id).evaluateDelta(event, progress, context)
    │        → if threshold met: onAchievementUnlocked(achievement)
    │             ├── activityDataRepository.recordAchievementUnlock()
    │             ├── pointsManager.addPoints(points)  → updates user_profile XP/level
    │             ├── unlockableManager.unlockAchievementRewards(achievement)
    │             └── unlockCallback?.onAchievementUnlocked(achievement)
    │                  → AchievementBannerManager.showAchievement(achievement)
    │                       → AchievementUnlockBanner() composable (in MainActivity)
    ▼
AchievementsDatabase (achievements.db, version 8)
    ├── achievements table (80+ rows from achievements.json)
    ├── achievement_progress table
    ├── activity_log table (daily activity)
    └── user_profile table (XP, level, badges, themes)
```

### File map (all already present in aniyomi-fork)

**Domain models** — `domain/src/main/java/tachiyomi/domain/achievement/`
- `model/Achievement.kt`, `AchievementCategory.kt` (5: ANIME, MANGA, BOTH, SECRET, NOVEL),
  `AchievementType.kt` (10: QUANTITY, EVENT, DIVERSITY, STREAK, LIBRARY, META, BALANCED,
  SECRET, TIME_BASED, FEATURE_BASED), `AchievementProgress.kt`, `AchievementRarity.kt`,
  `AchievementTier.kt`, `AchievementEvent.kt` (sealed class), `UserProfile.kt` (XP/level),
  `UserPoints.kt`, `DayActivity.kt`, `MonthStats.kt`, `Reward.kt`
- `repository/AchievementRepository.kt`, `UserProfileRepository.kt`, `ActivityDataRepository.kt`
- `rule/AchievementRule.kt`, `RuleContext.kt`

**Data layer** — `data/src/main/java/tachiyomi/data/achievement/`
- `database/AchievementsDatabase.kt` (wraps generated SqlDelightAchievementsDatabase, version 8)
- `repository/AchievementRepositoryImpl.kt`, `UserProfileRepositoryImpl.kt`, `ActivityDataRepositoryImpl.kt`
- `handler/AchievementHandler.kt` (519 lines, fully implemented), `AchievementEventBus.kt`,
  `PointsManager.kt`, `SessionManager.kt`, `FeatureUsageCollector.kt`,
  `AchievementCalculator.kt`, `AchievementRuleRegistry.kt`, `RuleContextImpl.kt`
- `handler/checkers/` — `DiversityAchievementChecker.kt`, `StreakAchievementChecker.kt`,
  `TimeBasedAchievementChecker.kt`, `FeatureBasedAchievementChecker.kt`
- `loader/AchievementLoader.kt` (loads `assets/achievements/achievements.json` into DB)
- `localization/AchievementTextResolver.kt`, `migration/AchievementMigrationHelper.kt`
- `model/AchievementJson.kt`, `UnlockableManager.kt`, `UserProfileManager.kt`
- `rules/` — 20+ rule classes (QuantityRule, EventRule, DiversityRule, StreakRule,
  LibraryRule, MetaRule, BalancedRule, SecretRules, TimeBasedRule, FeatureBasedRule,
  GenreCountRule, CompletionCountRule, CompletionRatioRule, RankUpRule,
  ReadingImmersionRule, AnimeNovelHybridRule, ChadRule, DarkFantasyRule,
  EventHorizonCartographerRule, ThreeRealmsRule, TimeParadoxRule, TrinityRule,
  GenreAliases.kt)

**SQLDelight schemas** — `data/src/main/sqldelightachievements/tachiyomi/data/achievement/`
- `achievements.sq`, `achievement_progress.sq`, `activity_log.sq`, `user_profile.sq`
- DB name: `tachiyomi.achievementsdb` (set in `AppModule.kt:228`), schema version 8

**UI** — `app/src/main/java/eu/kanade/presentation/achievement/`
- `ui/AchievementScreen.kt` (560 lines), `screenmodel/AchievementScreenModel.kt` (205 lines),
  `screen/AchievementScreenVoyager.kt` (Voyager Screen wrapper)
- `components/` — AchievementCard, AchievementCategoryTabs, AchievementContent,
  AchievementDetailDialog, AchievementActivityGraph, AchievementStatsComparison,
  AchievementTabsAndGrid, AchievementIcon, AchievementUnlockBanner (contains
  `AchievementBannerManager` object), AchievementGroupNotification, AchievementListDialog,
  AchievementBannerPalette, AchievementTimeFormatter, ActivityStreakIndicator
- `utils/AchievementRevealHelper.kt`

**App integration** — `app/src/main/java/eu/kanade/`
- `tachiyomi/ui/achievement/AchievementsTab.kt` (Voyager Tab, index 9u — NOT in nav)
- `tachiyomi/data/achievement/localization/AchievementTextResolverImpl.kt`
- `tachiyomi/data/backup/create/creators/AchievementBackupCreator.kt`
- `tachiyomi/data/backup/restore/restorers/AchievementRestorer.kt`
- `tachiyomi/data/backup/models/BackupAchievement.kt`

**Configuration** — `app/src/main/assets/achievements/achievements.json` (2953 lines,
version 19, 80+ achievement definitions including manga/anime/novel/both/secret categories)

**Event emitters** (already wired — these work):
- `app/src/main/java/eu/kanade/domain/items/chapter/interactor/SetReadStatus.kt:75,87`
  — emits `ChapterRead` and `MangaCompleted`
- `app/src/main/java/eu/kanade/domain/items/episode/interactor/SetSeenStatus.kt:73,85`
  — emits `EpisodeWatched` and `AnimeCompleted`
- `app/src/main/java/eu/kanade/domain/items/chapter/interactor/SetNovelReadStatus.kt:67,79`
  — emits `NovelChapterRead` and `NovelCompleted`
- `app/src/main/java/eu/kanade/tachiyomi/App.kt:196` — emits `AppStart`
- `app/src/main/java/eu/kanade/tachiyomi/App.kt:273` — emits `SessionEnd`
- `data/src/main/java/tachiyomi/data/entries/novel/NovelRepositoryImpl.kt:116,118,158,160,201,203`
  — emits `LibraryAdded`/`LibraryRemoved` for novels
- `data/src/main/java/tachiyomi/data/entries/anime/AnimeRepositoryImpl.kt:227,229`
  — emits `LibraryAdded`/`LibraryRemoved` for anime
- `data/src/main/java/tachiyomi/data/entries/manga/MangaRepositoryImpl.kt:182,184`
  — emits `LibraryAdded`/`LibraryRemoved` for manga

**DI wiring** — `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt`
- Lines 225-253: `sqlDriverAchievements` + `AchievementsDatabase` (eager init at line 398)
- Lines 332-390: All achievement singletons registered (AchievementEventBus,
  PointsManager, UserProfileManager, UnlockableManager, SessionManager, all 4 checkers,
  AchievementRuleRegistry, AchievementCalculator, AchievementHandler, AchievementLoader)

**i18n** — `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml`
- `label_achievements`, `pref_achievements_summary`, `achievement_stats_points`

---

## What Needs To Be Done (in order)

### Task 1 — Start the AchievementHandler on app launch ★ CRITICAL

**File:** `app/src/main/java/eu/kanade/tachiyomi/App.kt`

**Tadami reference** (`App.kt:298-333`):
```kotlin
// Inside onCreate(), within a Handler(Looper.getMainLooper()).post { ... } block,
// after Injekt modules are imported, on the main process only:
try {
    val achievementHandler = Injekt.get<tachiyomi.data.achievement.handler.AchievementHandler>()
    // Set up callback to show unlock banners
    achievementHandler.unlockCallback =
        object : tachiyomi.data.achievement.handler.AchievementHandler.AchievementUnlockCallback {
            override fun onAchievementUnlocked(
                achievement: tachiyomi.domain.achievement.model.Achievement,
            ) {
                AchievementBannerManager.showAchievement(achievement)
            }
        }
    achievementHandler.start()
} catch (e: Exception) {
    logcat(LogPriority.ERROR) { "[ACHIEVEMENTS-INIT] Failed to start achievement handler: ${e.message}" }
}
```

**aniyomi-fork current state:** `App.kt:194-197` already emits `AppStart` but never
calls `achievementHandler.start()`. The existing block:
```kotlin
android.os.Handler(android.os.Looper.getMainLooper()).post {
    val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    achievementEventBus.tryEmit(AchievementEvent.AppStart(hourOfDay = hourOfDay))
}
```

**Action:** Add the handler start + callback registration inside this existing
`Handler.post` block (or a new one right after it). Import
`eu.kanade.presentation.achievement.components.AchievementBannerManager` and
`eu.kanade.presentation.achievement.components.AchievementBannerManager.showAchievement`.
The `AchievementHandler` class and its `start()` method already exist at
`data/src/main/java/tachiyomi/data/achievement/handler/AchievementHandler.kt:61`.

**Note:** aniyomi-fork's `App.kt` does NOT have an `isMainProcess` guard like Tadami.
Either add one (preferred — matches Tadami) or accept that achievements will start
in all processes. The `isMainProcess` pattern in Tadami is at `App.kt:147-149`:
```kotlin
isMainProcess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    val process = getProcessName()
    if (packageName != process) WebView.setDataDirectorySuffix(process)
    packageName == process
} else true
```

### Task 2 — Load achievements JSON on app launch ★ CRITICAL

**File:** `app/src/main/java/eu/kanade/tachiyomi/App.kt`

**Tadami reference** (`App.kt:265-271`):
```kotlin
try {
    val loader = Injekt.get<tachiyomi.data.achievement.loader.AchievementLoader>()
    loader.loadAchievements()
} catch (e: Exception) {
    logcat(LogPriority.ERROR) { "Error during achievement initialization: ${e.message}" }
}
```

**aniyomi-fork current state:** `AchievementLoader` is registered in DI
(`AppModule.kt:335`) but `loadAchievements()` is only called from
`AchievementScreenModel.kt:75` — which never runs because the screen is unreachable.

**Action:** Add the loader call in `App.kt onCreate()`, inside the same
`Handler.post` block as Task 1, BEFORE calling `achievementHandler.start()`.
The handler needs achievements in the DB before it can evaluate them.

### Task 3 — Wire SessionManager into lifecycle ★ CRITICAL

**File:** `app/src/main/java/eu/kanade/tachiyomi/App.kt`

**Tadami reference** (`App.kt:452-454, 469-471`):
```kotlin
override fun onStart(owner: LifecycleOwner) {
    if (isMainProcess) {
        SecureActivityDelegate.onApplicationStart()
        sessionManager.onSessionStart()
    }
}
override fun onStop(owner: LifecycleOwner) {
    if (isMainProcess) {
        SecureActivityDelegate.onApplicationStopped()
        sessionManager.onSessionEnd()
    }
}
```

**aniyomi-fork current state:** `App.kt:263-276` manually emits `AppStart` and
`SessionEnd` events but does NOT call `sessionManager.onSessionStart/End()`. The
`SessionManager` is registered in DI (`AppModule.kt`) and has the methods, but
they're never called.

**Action:** Either:
- (a) Add `sessionManager.onSessionStart()` / `sessionManager.onSessionEnd()` calls
  in the existing `onStart`/`onStop` overrides (preferred — matches Tadami), OR
- (b) Keep the manual `tryEmit` approach but ensure Task 1 is done so the handler
  is subscribed to receive them.

Option (a) is cleaner because `SessionManager` does more than just emit events
(it tracks session duration, updates activity_log, etc.). Inject `SessionManager`
via `injectLazy()` at the top of `App.kt` alongside the existing
`achievementEventBus` field.

### Task 4 — Render unlock banners in MainActivity ★ HIGH

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`

**Tadami reference** (`MainActivity.kt:419-429`):
```kotlin
// Inside the main Composable, overlaid on the nav host:
AchievementUnlockBanner(
    modifier = Modifier.align(Alignment.BottomCenter),
)
AchievementGroupNotification(
    modifier = Modifier.align(Alignment.BottomCenter),
)
```

**aniyomi-fork current state:** The composables exist
(`AchievementUnlockBanner.kt`, `AchievementGroupNotification.kt`) and
`AchievementBannerManager` object is defined inside `AchievementUnlockBanner.kt:676`,
but neither composable is rendered anywhere. The `AchievementBannerManager` has
callbacks (`registerOnShowCallback`, `registerOnShowGroupCallback`) that the
composables register into — but if the composables are never composed, the
callbacks are never registered, so even when `showAchievement()` is called (from
Task 1's unlock callback), nothing displays.

**Action:** Find the root Composable in `MainActivity.kt` (likely the `setContent`
block or the main `HomeScreen` host) and add the two banner composables as
overlays. They should be in a `Box` scope so `Modifier.align(Alignment.BottomCenter)`
works. Import:
- `eu.kanade.presentation.achievement.components.AchievementUnlockBanner`
- `eu.kanade.presentation.achievement.components.AchievementGroupNotification`

### Task 5 — Set reader/player flag on AchievementBannerManager ★ MEDIUM

**Files:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt` (if exists)

**Tadami reference** (`ReaderActivity.kt:224,322`):
```kotlin
// In onCreate / onResume:
AchievementBannerManager.setInReaderOrPlayer(true)
// In onPause / onDestroy:
AchievementBannerManager.setInReaderOrPlayer(false)
```

**aniyomi-fork current state:** Not called. The `AchievementBannerManager` has
an `isInReaderOrPlayer` flag that, when true, defers banner display until the
user exits the reader/player (stores in `pendingAchievements` list). Without
this, achievement unlock banners would appear over the reader UI, which is
disruptive.

**Action:** Add the two calls in `ReaderActivity.kt` lifecycle (and
`PlayerActivity.kt` if aniyomi-fork has an anime/video player activity). Import
`eu.kanade.presentation.achievement.components.AchievementBannerManager`.

### Task 6 — Add navigation entry to achievements screen ★ CRITICAL (UX)

The achievements screen is fully built but unreachable. Two options:

#### Option A — Add to Settings navigation (preferred, matches Tadami)

**Tadami reference** (`SettingsNavigationItems.kt:107-113`):
```kotlin
SettingsNavigationItem(
    key = "achievements",
    titleRes = AYMR.strings.label_achievements,
    subtitleRes = AYMR.strings.pref_achievements_summary,
    icon = Icons.Outlined.EmojiEvents,
    screen = AchievementScreenVoyager,
),
```

**aniyomi-fork current state:** `SettingsScreen.kt` has a `Destination` sealed
class with only `About`, `DataAndStorage`, `Tracking`. No achievements entry.
The `AchievementScreenVoyager` Screen object exists at
`app/src/main/java/eu/kanade/presentation/achievement/screen/AchievementScreenVoyager.kt`
and is ready to be pushed onto a Voyager navigator.

**Action:** aniyomi-fork uses a different settings navigation pattern than Tadami
(it has a `Destination` sealed class in `SettingsScreen.kt` rather than a
`SettingsNavigationItems` list). The cleanest approach:
- Add `data object Achievements : Destination(N)` to the `Destination` sealed class
  in `SettingsScreen.kt` (use next available N — currently 0,1,2).
- Add the routing case: `Destination.Achievements.id -> AchievementScreenVoyager`
  in both `when (destination)` blocks (lines ~38 and ~58).
- Add a settings row somewhere in the settings root screen that navigates to
  `SettingsScreen(destination = SettingsScreen.Destination.Achievements)`. Use
  `Icons.Outlined.EmojiEvents` as the icon, `AYMR.strings.label_achievements`
  as the title, `AYMR.strings.pref_achievements_summary` as the subtitle.

#### Option B — Add AchievementsTab to bottom nav

**aniyomi-fork current state:** `AchievementsTab` is defined at
`app/src/main/java/eu/kanade/tachiyomi/ui/achievement/AchievementsTab.kt`
with `index = 9u` but is NOT in `NavStyle.tabs` (`app/src/main/java/eu/kanade/domain/ui/model/NavStyle.kt:46-54`).

**Action:** Add `AchievementsTab` to the `tabs` list in `NavStyle.kt`. Note that
aniyomi-fork's nav was deliberately reduced to 4 tabs (Home, Library, Updates,
Browse) with a three-dot overflow menu replacing the More tab. Adding a 5th tab
contradicts this design decision. **Option A is strongly preferred.**

#### Option C — Add to overflow menu

The three-dot overflow menu in each tab's top bar (via `globalOverflowActions()`
in `presentation/components/GlobalOverflowActions.kt`) could get an "Achievements"
item that pushes `AchievementScreenVoyager`. This fits the existing design but
requires access to the Voyager navigator from the overflow action, which may need
plumbing. Check how `Settings` is currently navigated to from the overflow menu
and mirror that pattern.

**Recommendation:** Option A (settings entry) is the lowest-risk, matches Tadami,
and keeps the nav bar clean. The i18n strings `label_achievements` and
`pref_achievements_summary` already exist.

---

## Verification Steps

After completing Tasks 1-6, verify the system works end-to-end:

1. **Build:** `.\gradlew assembleDebug -Dorg.gradle.java.home="C:\Program Files\Android\Android Studio\jbr"`

2. **Install:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`

3. **Check DB populated:** After first launch, check logcat for
   `[ACHIEVEMENTS] AchievementHandler.start() called` and no errors from
   `loader.loadAchievements()`. The achievements.db should have 80+ rows in the
   `achievements` table.

4. **Trigger an event:** Read a manga chapter. Check logcat for
   `[ACHIEVEMENTS] Event received: ChapterRead(...)` — confirms the handler is
   subscribed and processing.

5. **Check progress:** Open the achievements screen (via the new settings entry).
   The "First steps" achievement (read 1 manga chapter) should show as unlocked
   with a progress bar at 100%.

6. **Check unlock banner:** If the banner composables are rendered (Task 4), an
   unlock banner should appear at the bottom of the screen when an achievement
   is unlocked. Check logcat for `Achievement unlocked: First steps (+10 points)`.

7. **Check XP/level:** The `user_profile` table should have `total_xp > 0` and
   `level >= 1` after unlocking achievements. The achievements screen should
   display the level and XP progress bar.

8. **Check streak:** Read a chapter on two consecutive days. The
   `activity_log` table should have entries for both days. The streak indicator
   on the achievements screen should show `Current streak: 2`.

---

## Notes for the Implementing Agent

- **No new files needed.** All 70+ achievement files already exist. This is
  purely a wiring task — adding ~30 lines across 3-4 existing files
  (`App.kt`, `MainActivity.kt`, `SettingsScreen.kt`, optionally
  `ReaderActivity.kt`).

- **The `AchievementHandler.start()` method** is at
  `data/src/main/java/tachiyomi/data/achievement/handler/AchievementHandler.kt:61`.
  It launches a coroutine that subscribes to `eventBus.events` and calls
  `processEvent(event)` for each. It also runs `sanitizeCrossCategoryFirstAchievements()`
  once at start.

- **The `AchievementLoader.loadAchievements()` method** is at
  `data/src/main/java/tachiyomi/data/achievement/loader/AchievementLoader.kt:44`.
  It reads `assets/achievements/achievements.json`, parses it, and inserts/upserts
  into the `achievements` table. It handles versioned migrations (JSON version 19
  → DB schema version 8). It is idempotent — safe to call on every launch.

- **The `AchievementBannerManager`** is a singleton object at
  `app/src/main/java/eu/kanade/presentation/achievement/components/AchievementUnlockBanner.kt:676`.
  It has `registerOnShowCallback` / `registerOnShowGroupCallback` that the
  `AchievementUnlockBanner()` and `AchievementGroupNotification()` composables
  call in their `LaunchedEffect`. If those composables are never rendered, the
  callbacks are never registered, and `showAchievement()` calls are silently
  no-ops (the achievement is still unlocked in the DB, just no banner shown).

- **The `unlockCallback` on `AchievementHandler`** is a separate callback
  (set in `App.kt`) that fires `AchievementBannerManager.showAchievement()`.
  This is the bridge between "achievement unlocked in DB" and "banner shown to
  user". Both the callback (Task 1) AND the composable rendering (Task 4) are
  required for banners to work.

- **aniyomi-fork has no `MoreTab`** (it was removed and replaced by a three-dot
  overflow menu). Tadami's `MoreTab.kt:109` has
  `onAchievementsClick = { navigator.push(AchievementScreenVoyager) }` — this
  path does not exist in aniyomi-fork. Use the Settings route (Option A above).

- **aniyomi-fork's `App.kt` has no `isMainProcess` guard.** Tadami gates all
  achievement init behind `if (isMainProcess)`. Without this, achievements will
  start in all processes (e.g., the backup process, the reader process). This
  may cause duplicate event processing or DB contention. Consider adding the
  guard (Tadami `App.kt:147-149` pattern) if multi-process issues arise.

- **The `SessionManager`** (`data/src/main/java/tachiyomi/data/achievement/handler/SessionManager.kt`)
  has `onSessionStart()` and `onSessionEnd()` methods. It emits `AppStart` and
  `SessionEnd` events AND records session duration in `activity_log`. aniyomi-fork
  currently manually emits these events from `App.kt` but doesn't call
  `SessionManager` — so the activity_log duration tracking is lost. Task 3 fixes
  this. If you skip Task 3, session-duration-based achievements (ReadingImmersionRule)
  will not work.

- **DB migrations:** The achievements DB uses SQLDelight's schema versioning
  (version 8). The `AndroidSqliteDriver` in `AppModule.kt:225` handles schema
  creation/upgrade automatically via `AchievementsDatabase.Schema`. No manual
  migration files are needed for the achievements DB — SQLDelight generates them
  from the `.sq` files. The `AchievementMigrationHelper` handles JSON version
  migrations (data-level, not schema-level).

- **Backup/restore** is already wired: `AchievementBackupCreator.kt` and
  `AchievementRestorer.kt` exist and are integrated into the backup system. No
  action needed.

- **The `RecomputeGenreAchievementsMigration`** in Tadami
  (`app/src/main/java/eu/mihon/core/migration/migrations/`) is a one-time
  migration that fixes genre matching for localized (Cyrillic) genres. aniyomi-fork
  has `AchievementMigrationHelper.kt` which may serve the same purpose — verify
  if needed, but this is a data-quality fix, not a blocking issue.

---

## XP/Level System (for reference)

Already implemented in `UserProfile.kt`:
- **XP formula:** `XP for level N = 100 * N^1.5` (so level 2 = 283 XP, level 10 = 3162 XP)
- **Level from XP:** cumulative sum of `getXPForLevel(1..N)` until it exceeds `totalXP`
- **Level names:** Новичок (1-4), Опытный (5-9), Ветеран (10-24), Эксперт (25-49),
  Мастер (50-99), Легенда (100+) — these are in Russian in the Tadami source;
  aniyomi-fork may need English equivalents if not already localized.
- **PointsManager** (`data/src/main/java/tachiyomi/data/achievement/handler/PointsManager.kt`)
  handles `addPoints()` → updates `user_profile.total_xp`, recalculates level,
  updates `current_xp` / `xp_to_next_level`.

## Unlockable Rewards (for reference)

Already implemented in `UnlockableManager.kt`:
- Reward ID prefixes: `theme_*`, `badge_*`, `aura_*`, `title_*`, `display_*`,
  `profile_*`, `avatar_*`, `reader_*`, `home_*`, `special_*`
- Stored in SharedPreferences with key prefix `"unlocked_"`
- `theme_*` rewards are mirrored into `UserProfile.unlockedThemes` via
  `UserProfileManager.unlockTheme()`
- The theme picker would need to read from `UserProfile.unlockedThemes` to show
  unlockable themes — verify this integration exists or add it (lower priority,
  not blocking the core achievements functionality).

---

## Summary Checklist

- [ ] Task 1: Call `achievementHandler.start()` + set `unlockCallback` in `App.kt`
- [ ] Task 2: Call `loader.loadAchievements()` in `App.kt` (before handler start)
- [ ] Task 3: Call `sessionManager.onSessionStart/End()` in `App.kt` lifecycle
- [ ] Task 4: Render `AchievementUnlockBanner()` + `AchievementGroupNotification()` in `MainActivity.kt`
- [ ] Task 5: Call `AchievementBannerManager.setInReaderOrPlayer()` in `ReaderActivity.kt` (+ `PlayerActivity.kt` if exists)
- [ ] Task 6: Add navigation entry — preferred: Settings route in `SettingsScreen.kt` → `AchievementScreenVoyager`
- [ ] Verify: Build, install, read a chapter, see achievement unlock + banner + XP gain
