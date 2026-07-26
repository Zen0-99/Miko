package eu.kanade.tachiyomi

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Looper
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.util.DebugLogger
import dev.mihon.injekt.patchInjekt
import eu.kanade.domain.DomainModule
import eu.kanade.domain.SYDomainModule
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.tachiyomi.crash.CrashActivity
import eu.kanade.tachiyomi.crash.GlobalExceptionHandler
import eu.kanade.tachiyomi.data.coil.AnimeCoverKeyer
import eu.kanade.tachiyomi.data.coil.AnimeImageFetcher
import eu.kanade.tachiyomi.data.coil.AnimeKeyer
import eu.kanade.tachiyomi.data.coil.BufferedSourceFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverKeyer
import eu.kanade.tachiyomi.data.coil.MangaKeyer
import eu.kanade.tachiyomi.data.coil.NovelCoverFetcher
import eu.kanade.tachiyomi.data.coil.NovelCoverKeyer
import eu.kanade.tachiyomi.data.coil.NovelKeyer
import eu.kanade.tachiyomi.data.coil.TachiyomiImageDecoder
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.di.AppModule
import eu.kanade.tachiyomi.di.PreferenceModule
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import okio.Path.Companion.toOkioPath
import mihon.core.migration.Migrator
import mihon.core.migration.migrations.migrations
import org.conscrypt.Conscrypt
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.data.achievement.handler.SessionManager
import tachiyomi.data.achievement.loader.AchievementLoader
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementEvent
import eu.kanade.presentation.achievement.components.AchievementBannerManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.widget.entries.anime.AnimeWidgetManager
import tachiyomi.presentation.widget.entries.manga.MangaWidgetManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.security.Security
import java.util.Calendar

class App : Application(), DefaultLifecycleObserver, SingletonImageLoader.Factory {

    private val basePreferences: BasePreferences by injectLazy()
    private val networkPreferences: NetworkPreferences by injectLazy()
    private val achievementEventBus: AchievementEventBus by injectLazy()
    private val achievementHandler: AchievementHandler by injectLazy()
    private val achievementLoader: AchievementLoader by injectLazy()
    private val sessionManager: SessionManager by injectLazy()
    private val activityDataRepository: tachiyomi.domain.achievement.repository.ActivityDataRepository by injectLazy()

    private val disableIncognitoReceiver = DisableIncognitoReceiver()
    private var sessionStartMs: Long = 0L
    private var lastFlushMs: Long = 0L
    private var sessionFlushJob: kotlinx.coroutines.Job? = null

    @SuppressLint("LaunchActivityFromNotification")
    override fun onCreate() {
        super<Application>.onCreate()
        patchInjekt()

        GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)

        // TLS 1.3 support for Android < 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        // Avoid potential crashes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val process = getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        }

        // Warm up the WebView default user agent on the main thread before any source loads.
        // Some sources call WebSettings.getDefaultUserAgent() while being constructed on a
        // background thread. If Chromium needs the main thread at the same time, startup can
        // stall behind the splash.
        try {
            android.webkit.WebSettings.getDefaultUserAgent(this)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "Failed to warm up WebView user agent: ${e.message}" }
        }

        Injekt.importModule(PreferenceModule(this))
        Injekt.importModule(AppModule(this))
        Injekt.importModule(DomainModule())
        // SY -->
        Injekt.importModule(SYDomainModule())
        // SY <--

        // Set initial content mode based on start screen preference (only on app launch)
        val uiPreferences = Injekt.get<UiPreferences>()
        uiPreferences.startScreen().get().contentMode?.let { mode ->
            if (uiPreferences.contentMode().get() != mode) {
                uiPreferences.contentMode().set(mode)
            }
        }

        setupNotificationChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val scope = ProcessLifecycleOwner.get().lifecycleScope

        // Show notification to disable Incognito Mode when it's enabled
        basePreferences.incognitoMode().changes()
            .onEach { enabled ->
                if (enabled) {
                    disableIncognitoReceiver.register()
                    notify(
                        Notifications.ID_INCOGNITO_MODE,
                        Notifications.CHANNEL_INCOGNITO_MODE,
                    ) {
                        setContentTitle(stringResource(MR.strings.pref_incognito_mode))
                        setContentText(stringResource(MR.strings.notification_incognito_text))
                        setSmallIcon(R.drawable.ic_glasses_24dp)
                        setOngoing(true)

                        val pendingIntent = PendingIntent.getBroadcast(
                            this@App,
                            0,
                            Intent(ACTION_DISABLE_INCOGNITO_MODE),
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        setContentIntent(pendingIntent)
                    }
                } else {
                    disableIncognitoReceiver.unregister()
                    cancelNotification(Notifications.ID_INCOGNITO_MODE)
                }
            }
            .launchIn(ProcessLifecycleOwner.get().lifecycleScope)

        basePreferences.hardwareBitmapThreshold().let { preference ->
            if (!preference.isSet()) preference.set(GLUtil.DEVICE_TEXTURE_LIMIT)
        }

        basePreferences.hardwareBitmapThreshold().changes()
            .onEach { ImageUtil.hardwareBitmapThreshold = it }
            .launchIn(scope)

        setAppCompatDelegateThemeMode(Injekt.get<UiPreferences>().themeMode().get())

        // Updates widget update
        with(MangaWidgetManager(Injekt.get(), Injekt.get())) {
            init(ProcessLifecycleOwner.get().lifecycleScope)
        }

        with(AnimeWidgetManager(Injekt.get(), Injekt.get())) {
            init(ProcessLifecycleOwner.get().lifecycleScope)
        }

        if (!LogcatLogger.isInstalled && networkPreferences.verboseLogging().get()) {
            LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
        }

        initializeMigrator()

        // Defer achievement init past the first frame so it doesn't block startup.
        // Loads achievement definitions from assets into the DB, then starts the
        // event handler so emitted events (chapter read, etc.) are processed.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope.launch {
                try {
                    achievementLoader.loadAchievements()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "[ACHIEVEMENTS-INIT] Failed to load achievements: ${e.message}" }
                }

                try {
                    achievementHandler.unlockCallback =
                        object : AchievementHandler.AchievementUnlockCallback {
                            override fun onAchievementUnlocked(achievement: Achievement) {
                                AchievementBannerManager.showAchievement(achievement)
                            }
                        }
                    achievementHandler.start()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "[ACHIEVEMENTS-INIT] Failed to start achievement handler: ${e.message}" }
                }

                val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                achievementEventBus.tryEmit(AchievementEvent.AppStart(hourOfDay = hourOfDay))
            }
        }
    }

    private fun initializeMigrator() {
        val preferenceStore = Injekt.get<PreferenceStore>()
        val preference = preferenceStore.getInt(Preference.appStateKey("last_version_code"), 0)
        logcat { "Migration from ${preference.get()} to ${BuildConfig.VERSION_CODE}" }
        Migrator.initialize(
            old = preference.get(),
            new = BuildConfig.VERSION_CODE,
            migrations = migrations,
            onMigrationComplete = {
                logcat { "Updating last version to ${BuildConfig.VERSION_CODE}" }
                preference.set(BuildConfig.VERSION_CODE)
            },
        )
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(this).apply {
            val callFactoryLazy = lazy { Injekt.get<NetworkHelper>().client }
            components {
                // NetworkFetcher.Factory
                add(OkHttpNetworkFetcherFactory(callFactoryLazy::value))
                // Decoder.Factory
                add(TachiyomiImageDecoder.Factory())
                // Fetcher.Factory
                add(BufferedSourceFetcher.Factory())
                add(MangaCoverFetcher.MangaFactory(callFactoryLazy))
                add(MangaCoverFetcher.MangaCoverFactory(callFactoryLazy))
                add(NovelCoverFetcher.NovelFactory(callFactoryLazy))
                add(NovelCoverFetcher.NovelCoverFactory(callFactoryLazy))
                add(AnimeImageFetcher.AnimeFactory(callFactoryLazy))
                add(AnimeImageFetcher.AnimeCoverFactory(callFactoryLazy))
                // Keyer
                add(AnimeKeyer())
                add(MangaKeyer())
                add(NovelKeyer())
                add(AnimeCoverKeyer())
                add(MangaCoverKeyer())
                add(NovelCoverKeyer())
            }

            crossfade((300 * this@App.animatorDurationScale).toInt())
            allowRgb565(DeviceUtil.isLowRamDevice(this@App))
            memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizePercent(this@App, 0.25)
                    .build()
            }
            diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(this@App.cacheDir.resolve("coil_cache").toOkioPath())
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            if (networkPreferences.verboseLogging().get()) logger(DebugLogger())

            // Coil spawns a new thread for every image load by default
            val isLowRam = DeviceUtil.isLowRamDevice(this@App)
            fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(if (isLowRam) 8 else 16))
            decoderCoroutineContext(Dispatchers.IO.limitedParallelism(if (isLowRam) 3 else 4))
        }
            .build()
    }

    override fun onStart(owner: LifecycleOwner) {
        SecureActivityDelegate.onApplicationStart()
        sessionStartMs = System.currentTimeMillis()
        lastFlushMs = sessionStartMs
        sessionManager.onSessionStart()

        // Ensure an activity_log row exists for today and start periodic flushing
        CoroutineScope(Dispatchers.IO).launch {
            try {
                activityDataRepository.recordAppOpen()
            } catch (_: Exception) {
            }
        }
        sessionFlushJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(60_000L) // flush every 60 seconds
                flushSessionTime()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        SecureActivityDelegate.onApplicationStopped()
        sessionFlushJob?.cancel()
        sessionFlushJob = null
        // Emit SessionEnd event for achievements system and persist remaining session duration
        if (sessionStartMs > 0L) {
            // Flush remaining session time in a NonCancellable coroutine so
            // the write completes even as the app goes to background.
            CoroutineScope(Dispatchers.IO).launch {
                flushSessionTime()
            }
            val durationMs = System.currentTimeMillis() - sessionStartMs
            achievementEventBus.tryEmit(AchievementEvent.SessionEnd(durationMs = durationMs))
            sessionStartMs = 0L
        }
        sessionManager.onSessionEnd()
    }

    /**
     * Flushes the elapsed session time since the last flush to the activity log.
     * Uses [NonCancellable] so the write completes even when the app is going
     * to background.
     */
    private suspend fun flushSessionTime() {
        val now = System.currentTimeMillis()
        if (sessionStartMs <= 0L || lastFlushMs <= 0L) return
        val delta = now - lastFlushMs
        lastFlushMs = now
        if (delta <= 0L) return
        withContext(NonCancellable) {
            try {
                activityDataRepository.recordAppSession(delta)
            } catch (_: Exception) {
            }
        }
    }

    override fun getPackageName(): String {
        try {
            // Override the value passed as X-Requested-With in WebView requests
            val stackTrace = Looper.getMainLooper().thread.stackTrace
            val isChromiumCall = stackTrace.any { trace ->
                trace.className.equals("org.chromium.base.BuildInfo", ignoreCase = true) &&
                    setOf("getAll", "getPackageName", "<init>").any { trace.methodName.equals(it, ignoreCase = true) }
            }

            if (isChromiumCall) return WebViewUtil.spoofedPackageName(applicationContext)
        } catch (_: Exception) {
        }

        return super.getPackageName()
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to modify notification channels" }
        }
    }

    private inner class DisableIncognitoReceiver : BroadcastReceiver() {
        private var registered = false

        override fun onReceive(context: Context, intent: Intent) {
            basePreferences.incognitoMode().set(false)
        }

        fun register() {
            if (!registered) {
                ContextCompat.registerReceiver(
                    this@App,
                    this,
                    IntentFilter(ACTION_DISABLE_INCOGNITO_MODE),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                unregisterReceiver(this)
                registered = false
            }
        }
    }
}

private const val ACTION_DISABLE_INCOGNITO_MODE = "tachi.action.DISABLE_INCOGNITO_MODE"
