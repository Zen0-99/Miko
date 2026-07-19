package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Transport actions for the TTS media notification and media session. */
enum class NovelTtsTransportAction {
    PREVIOUS,
    PLAY,
    PAUSE,
    NEXT,
    STOP,
}

/** UI-facing playback state exposed by the service via StateFlow. */
data class NovelTtsPlaybackState(
    val isPlaying: Boolean = false,
    val isInitialized: Boolean = false,
    val currentParagraphIndex: Int = 0,
    val totalParagraphs: Int = 0,
    val currentText: String = "",
    val title: String = "",
    val error: String? = null,
)

/**
 * Foreground service that manages novel TTS playback with background support.
 *
 * Wraps the existing [TtsController] and adds:
 * - Foreground service lifecycle (survives screen off)
 * - Media notification with play/pause/skip/stop controls
 * - [MediaSessionCompat] for lock screen and Bluetooth controls
 * - [NovelTtsAudioFocusManager] for pausing when other audio plays
 * - [BecomingNoisyReceiver] for pausing when headphones are unplugged
 *
 * The reader activity binds to this service via [LocalBinder] to control playback.
 */
class NovelTtsPlaybackService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 20260408
        private const val ACTION_PREVIOUS = "eu.kanade.tachiyomi.ui.reader.novel.tts.action.PREVIOUS"
        private const val ACTION_PLAY = "eu.kanade.tachiyomi.ui.reader.novel.tts.action.PLAY"
        private const val ACTION_PAUSE = "eu.kanade.tachiyomi.ui.reader.novel.tts.action.PAUSE"
        private const val ACTION_NEXT = "eu.kanade.tachiyomi.ui.reader.novel.tts.action.NEXT"
        private const val ACTION_STOP = "eu.kanade.tachiyomi.ui.reader.novel.tts.action.STOP"

        /**
         * Convenience method to start the foreground service from the reader.
         * The caller should then bind to the service and call [startReading].
         */
        fun start(context: Context) {
            val intent = Intent(context, NovelTtsPlaybackService::class.java)
            context.startForegroundService(intent)
        }

        /**
         * Convenience method to stop the service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, NovelTtsPlaybackService::class.java)
            context.stopService(intent)
        }
    }

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioFocusManager: NovelTtsAudioFocusManager
    private var becomingNoisyReceiver: BecomingNoisyReceiver? = null

    private var ttsController: TtsController? = null
    private var paragraphs: List<String> = emptyList()
    private var chapterTitle: String = ""

    private val _playbackState = MutableStateFlow(NovelTtsPlaybackState())
    val playbackState: StateFlow<NovelTtsPlaybackState> = _playbackState.asStateFlow()

    // ---------------------------------------------------------------------
    // Service lifecycle
    // ---------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()

        val preferences = Injekt.get<NovelTtsPreferences>()
        ttsController = TtsController(applicationContext, preferences)

        audioFocusManager = NovelTtsAudioFocusManager(applicationContext) {
            pause()
        }

        mediaSession = MediaSessionCompat(this, "NovelTtsPlaybackService").apply {
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() = dispatchTransportAction(NovelTtsTransportAction.PLAY)
                    override fun onPause() = dispatchTransportAction(NovelTtsTransportAction.PAUSE)
                    override fun onSkipToPrevious() = dispatchTransportAction(NovelTtsTransportAction.PREVIOUS)
                    override fun onSkipToNext() = dispatchTransportAction(NovelTtsTransportAction.NEXT)
                    override fun onStop() = dispatchTransportAction(NovelTtsTransportAction.STOP)
                },
            )
            isActive = true
        }

        // Start foreground immediately with a placeholder notification to comply with
        // the 5-second startForeground requirement on Android 12+.
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        resolveTransportAction(intent?.action)?.let(::dispatchTransportAction)
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterBecomingNoisyReceiver()
        audioFocusManager.abandonPlaybackFocus()
        ttsController?.shutdown()
        ttsController = null
        mediaSession.release()
        serviceJob.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Public API — called via binder from the reader activity/screen model
    // ---------------------------------------------------------------------

    /**
     * Start reading a list of paragraphs from [startIndex].
     * Initializes the TTS engine if needed, then begins playback.
     */
    fun startReading(
        paragraphs: List<String>,
        startIndex: Int = 0,
        title: String = "",
    ) {
        this.paragraphs = paragraphs
        this.chapterTitle = title

        _playbackState.value = _playbackState.value.copy(
            totalParagraphs = paragraphs.size,
            title = title,
            error = null,
        )

        val controller = ttsController
        if (controller == null || !controller.isSpeaking) {
            // Engine not yet initialized — initialize then start
            ttsController?.initializeEngine(
                onReady = {
                    _playbackState.value = _playbackState.value.copy(isInitialized = true)
                    beginReading(startIndex)
                },
                onError = { error ->
                    logcat(LogPriority.ERROR) { "TTS init error: $error" }
                    _playbackState.value = _playbackState.value.copy(error = error)
                },
            )
        } else {
            beginReading(startIndex)
        }
    }

    /**
     * Resume playback from the current position.
     */
    fun play() {
        val controller = ttsController ?: return
        if (!controller.isSpeaking) {
            val granted = audioFocusManager.requestPlaybackFocus()
            if (granted) {
                registerBecomingNoisyReceiver()
                controller.resume()
                _playbackState.value = _playbackState.value.copy(isPlaying = true, error = null)
                updateNotification()
            } else {
                _playbackState.value = _playbackState.value.copy(error = "Could not gain audio focus")
            }
        }
    }

    /**
     * Pause playback.
     */
    fun pause() {
        ttsController?.pause()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
        updateNotification()
    }

    /**
     * Skip to the next paragraph.
     */
    fun next() {
        ttsController?.next()
    }

    /**
     * Skip to the previous paragraph.
     */
    fun previous() {
        ttsController?.previous()
    }

    /**
     * Stop playback and shut down the service.
     */
    fun stop() {
        unregisterBecomingNoisyReceiver()
        audioFocusManager.abandonPlaybackFocus()
        ttsController?.stop()
        _playbackState.value = NovelTtsPlaybackState()
        stopForegroundPlayback()
    }

    /**
     * Update TTS engine settings (speech rate, pitch).
     */
    fun setSpeechRate(rate: Float) {
        ttsController?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        ttsController?.setPitch(pitch)
    }

    /**
     * Get available voices from the current TTS engine.
     */
    fun getAvailableVoices(): List<TtsVoice> {
        return ttsController?.getAvailableVoices() ?: emptyList()
    }

    fun setVoice(voiceName: String) {
        ttsController?.setVoice(voiceName)
    }

    // ---------------------------------------------------------------------
    // Internal playback logic
    // ---------------------------------------------------------------------

    private fun beginReading(startIndex: Int) {
        val controller = ttsController ?: return
        val granted = audioFocusManager.requestPlaybackFocus()
        if (!granted) {
            _playbackState.value = _playbackState.value.copy(error = "Could not gain audio focus")
            return
        }
        registerBecomingNoisyReceiver()

        controller.startReading(
            paragraphs = paragraphs,
            startIndex = startIndex,
            onParagraphChanged = { index ->
                _playbackState.value = _playbackState.value.copy(
                    currentParagraphIndex = index,
                    currentText = paragraphs.getOrNull(index)?.take(120) ?: "",
                )
                updateNotification()
            },
            onComplete = {
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
                unregisterBecomingNoisyReceiver()
                audioFocusManager.abandonPlaybackFocus()
                stopForegroundPlayback()
            },
        )

        _playbackState.value = _playbackState.value.copy(
            isPlaying = true,
            currentParagraphIndex = startIndex,
            currentText = paragraphs.getOrNull(startIndex)?.take(120) ?: "",
        )
        updateNotification()
    }

    private fun dispatchTransportAction(action: NovelTtsTransportAction) {
        serviceScope.launch {
            when (action) {
                NovelTtsTransportAction.PREVIOUS -> previous()
                NovelTtsTransportAction.PLAY -> play()
                NovelTtsTransportAction.PAUSE -> pause()
                NovelTtsTransportAction.NEXT -> next()
                NovelTtsTransportAction.STOP -> stop()
            }
            updateMediaSessionState()
            updateNotification()
        }
    }

    private fun resolveTransportAction(action: String?): NovelTtsTransportAction? {
        return when (action) {
            ACTION_PREVIOUS -> NovelTtsTransportAction.PREVIOUS
            ACTION_PLAY -> NovelTtsTransportAction.PLAY
            ACTION_PAUSE -> NovelTtsTransportAction.PAUSE
            ACTION_NEXT -> NovelTtsTransportAction.NEXT
            ACTION_STOP -> NovelTtsTransportAction.STOP
            else -> null
        }
    }

    // ---------------------------------------------------------------------
    // Headset disconnect receiver
    // ---------------------------------------------------------------------

    private fun registerBecomingNoisyReceiver() {
        if (becomingNoisyReceiver != null) return
        becomingNoisyReceiver = BecomingNoisyReceiver { pause() }
        registerReceiver(becomingNoisyReceiver, IntentFilter(BecomingNoisyReceiver.ACTION))
    }

    private fun unregisterBecomingNoisyReceiver() {
        becomingNoisyReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
            becomingNoisyReceiver = null
        }
    }

    // ---------------------------------------------------------------------
    // Media session + notification
    // ---------------------------------------------------------------------

    private fun updateMediaSessionState() {
        val state = _playbackState.value
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_STOP,
            )
            .setState(
                if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                1f,
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateNotification() {
        updateMediaSessionState()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val state = _playbackState.value
        val playPauseAction = if (state.isPlaying) {
            NovelTtsTransportAction.PAUSE
        } else {
            NovelTtsTransportAction.PLAY
        }
        val actions = listOf(
            NovelTtsTransportAction.PREVIOUS,
            playPauseAction,
            NovelTtsTransportAction.NEXT,
            NovelTtsTransportAction.STOP,
        )
        val compactActionIndices = intArrayOf(0, 1, 2)

        return notificationBuilder(Notifications.CHANNEL_NOVEL_TTS) {
            setContentTitle(state.title.ifEmpty { "Novel TTS" })
            setContentText(state.currentText)
            setSmallIcon(R.drawable.ic_play_arrow_24dp)
            setOngoing(state.isPlaying)
            setOnlyAlertOnce(true)
            setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            actions.forEach { addAction(buildNotificationAction(it)) }
            setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(createServicePendingIntent(ACTION_STOP))
                    .setShowActionsInCompactView(*compactActionIndices),
            )
            setDeleteIntent(createServicePendingIntent(ACTION_STOP))
        }.build()
    }

    private fun buildNotificationAction(action: NovelTtsTransportAction): NotificationCompat.Action {
        return NotificationCompat.Action(
            resolveActionIcon(action),
            resolveActionTitle(action),
            createServicePendingIntent(resolveActionIntent(action)),
        )
    }

    private fun resolveActionIcon(action: NovelTtsTransportAction): Int = when (action) {
        NovelTtsTransportAction.PREVIOUS -> R.drawable.ic_skip_previous_24dp
        NovelTtsTransportAction.PLAY -> R.drawable.ic_play_arrow_24dp
        NovelTtsTransportAction.PAUSE -> R.drawable.ic_pause_24dp
        NovelTtsTransportAction.NEXT -> R.drawable.ic_skip_next_24dp
        NovelTtsTransportAction.STOP -> R.drawable.ic_close_24dp
    }

    private fun resolveActionTitle(action: NovelTtsTransportAction): String = when (action) {
        NovelTtsTransportAction.PREVIOUS -> "Previous"
        NovelTtsTransportAction.PLAY -> "Play"
        NovelTtsTransportAction.PAUSE -> "Pause"
        NovelTtsTransportAction.NEXT -> "Next"
        NovelTtsTransportAction.STOP -> "Stop"
    }

    private fun resolveActionIntent(action: NovelTtsTransportAction): String = when (action) {
        NovelTtsTransportAction.PREVIOUS -> ACTION_PREVIOUS
        NovelTtsTransportAction.PLAY -> ACTION_PLAY
        NovelTtsTransportAction.PAUSE -> ACTION_PAUSE
        NovelTtsTransportAction.NEXT -> ACTION_NEXT
        NovelTtsTransportAction.STOP -> ACTION_STOP
    }

    private fun createServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, NovelTtsPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopForegroundPlayback() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------------------------------------------------------------
    // Binder
    // ---------------------------------------------------------------------

    inner class LocalBinder : Binder() {
        fun getService(): NovelTtsPlaybackService = this@NovelTtsPlaybackService
    }
}

/**
 * Helper for binding to [NovelTtsPlaybackService] from the reader.
 * Manages the [ServiceConnection] lifecycle and exposes the service instance.
 */
class NovelTtsServiceConnection(
    private val onConnected: (NovelTtsPlaybackService) -> Unit,
    private val onDisconnected: () -> Unit,
) : ServiceConnection {

    var service: NovelTtsPlaybackService? = null
        private set

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        val localBinder = binder as? NovelTtsPlaybackService.LocalBinder ?: return
        service = localBinder.getService()
        onConnected(service!!)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
        onDisconnected()
    }
}
