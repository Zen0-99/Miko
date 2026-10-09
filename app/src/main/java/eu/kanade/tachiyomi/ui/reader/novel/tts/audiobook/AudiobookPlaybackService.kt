package eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.source.novel.abs.AbsNovelSource
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Media3 library session service hosting the audiobook player's ExoPlayer.
 *
 * This is deliberately a NEW service rather than an extension of
 * [eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackService] (which
 * owns the MediaSessionCompat + live/rendered TTS pipeline for read-along).
 * The audiobook player is plain media playback — MediaLibraryService gives
 * it the foreground lifecycle, a media notification, lock-screen/Bluetooth
 * transport, AND an Android Auto browse tree (library novels as albums,
 * chapters as tracks — see [AudiobookLibraryCallback]).
 *
 * All control flows through the Media3 session: [AudiobookPlayerController]
 * connects with a `MediaController` client, and external controllers (lock
 * screen, BT headsets, Android Auto) attach through the session directly.
 */
@UnstableApi
class AudiobookPlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * `host:port` of a request URI, port normalized to the scheme default —
     * the same shape [AbsClient.hostKey] emits, so a configured
     * `https://abs.local` (implicit 443) matches a `?`-less request to
     * `abs.local:443` and nothing else.
     */
    private fun hostKeyOf(uri: Uri): String? {
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it >= 0 }
            ?: if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
        return "$host:$port"
    }

    /**
     * Host-scoped Bearer injection (SD-1, T-22-02-01): the ABS token is
     * attached ONLY when the request targets the configured server —
     * `setDefaultRequestProperties` on the whole HttpDataSource would leak
     * it to every streamed host. Credentials are re-read per resolve (no
     * cached snapshot) so a token change mid-session applies on the next
     * request; an unconfigured/mismatched request passes through untouched.
     */
    private fun absAuthResolver(): ResolvingDataSource.Resolver =
        ResolvingDataSource.Resolver { dataSpec: DataSpec ->
            val client = runCatching {
                Injekt.get<NovelSourceManager>()
                    .get(AbsNovelSource.ABS_SOURCE_ID) as? AbsNovelSource
            }.getOrNull()?.client
            if (client != null && client.isConfigured() &&
                hostKeyOf(dataSpec.uri) == client.hostKey()
            ) {
                dataSpec.withAdditionalHeaders(client.authHeaders())
            } else {
                dataSpec
            }
        }

    override fun onCreate() {
        super.onCreate()
        // Remote audiobook URIs (abs://track → https stream) resolve through
        // DefaultMediaSourceFactory → ResolvingDataSource → DefaultDataSource:
        // http(s) hits the DefaultHttpDataSource arm (where the resolver's
        // Bearer header lands), file:// /content:// local tracks dispatch to
        // their own sources and pass the resolver through untouched (the
        // header is attached iff hostKeyOf matches the configured ABS host).
        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, DefaultHttpDataSource.Factory()),
            absAuthResolver(),
        )
        // -30s/+10s increments: the notification's rewind/forward buttons and
        // AudiobookPlayerController.rewind()/forward() share these (REQ-008).
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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
        mediaSession = MediaLibrarySession.Builder(
            this,
            player,
            AudiobookLibraryCallback(this, serviceScope),
        )
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceScope.cancel()
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
