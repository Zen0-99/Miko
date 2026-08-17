package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import okhttp3.OkHttpClient
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Wraps a Media3 ExoPlayer instance as a fallback engine when MPV fails to load a video.
 * Provides basic playback control (load, play, pause, seek) and reports state changes
 * back to the PlayerViewModel via callbacks.
 */
class ExoPlayerEngine(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onReady()
        fun onPlayingChanged(isPlaying: Boolean)
        fun onPositionChanged(positionMs: Long, durationMs: Long)
        fun onError(message: String)
        fun onEnded()
    }

    var player: ExoPlayer? = null
        private set

    private var hasLoadedFile = false

    fun initialize() {
        if (player != null) return
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                hasLoadedFile = true
                                callbacks.onReady()
                            }
                            Player.STATE_ENDED -> callbacks.onEnded()
                            Player.STATE_IDLE, Player.STATE_BUFFERING -> {}
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        callbacks.onPlayingChanged(isPlaying)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        callbacks.onError(error.message ?: "ExoPlayer error")
                    }
                })
            }
    }

    fun loadVideo(url: String, startPositionMs: Long = 0) {
        val exoPlayer = player ?: return
        hasLoadedFile = false
        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (startPositionMs > 0) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackParameters(
            androidx.media3.common.PlaybackParameters(speed, 1f),
        )
    }

    fun release() {
        player?.release()
        player = null
    }

    val isPlaying: Boolean
        get() = player?.isPlaying == true

    val currentPositionMs: Long
        get() = player?.currentPosition ?: 0

    val durationMs: Long
        get() = player?.duration ?: 0
}
