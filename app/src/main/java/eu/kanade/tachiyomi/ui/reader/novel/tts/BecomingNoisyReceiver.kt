package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * BroadcastReceiver that pauses TTS playback when audio becomes noisy
 * (e.g. headphones are unplugged or a Bluetooth audio device disconnects).
 *
 * Registered/unregistered by [NovelTtsPlaybackService] when playback starts/stops.
 */
class BecomingNoisyReceiver(
    private val onBecomingNoisy: () -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            onBecomingNoisy()
        }
    }

    companion object {
        /** The intent filter action used to register this receiver. */
        const val ACTION = AudioManager.ACTION_AUDIO_BECOMING_NOISY
    }
}
