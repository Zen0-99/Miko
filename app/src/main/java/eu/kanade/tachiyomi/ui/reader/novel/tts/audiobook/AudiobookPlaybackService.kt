package eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.ui.main.MainActivity

/**
 * Media3 session service hosting the audiobook player's ExoPlayer.
 *
 * This is deliberately a NEW service rather than an extension of
 * [eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackService] (which
 * owns the MediaSessionCompat + live/rendered TTS pipeline for read-along).
 * The audiobook player is plain media playback — MediaSessionService gives
 * it the foreground lifecycle, a media notification, and lock-screen/
 * Bluetooth transport for free (REQ-008).
 *
 * All control flows through the Media3 session: [AudiobookPlayerController]
 * connects with a `MediaController` client, and external controllers (lock
 * screen, BT headsets, Android Auto) attach through the session directly.
 */
class AudiobookPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // -30s/+10s increments: the notification's rewind/forward buttons and
        // AudiobookPlayerController.rewind()/forward() share these (REQ-008).
        val player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(REWIND_MS)
            .setSeekForwardIncrementMs(FORWARD_MS)
            .build()
        // Session activity: tapping the media notification anywhere (not just
        // a transport button) launches MainActivity with SHOW_AUDIOBOOK_PLAYER,
        // which expands the full player overlay (REQ-008).
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(Constants.SHORTCUT_AUDIOBOOK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        // Distinct from NovelTtsPlaybackService's MediaSessionCompat tag —
        // system media UIs keep the two sessions separate.
        const val SESSION_ID = "eu.kanade.tachiyomi.AUDIOBOOK_SESSION"
        const val REWIND_MS = 30_000L
        const val FORWARD_MS = 10_000L
    }
}
