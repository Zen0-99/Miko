package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import logcat.LogPriority
import logcat.logcat

/**
 * Manages Chromecast session state. Provides callbacks for when cast sessions
 * start/end so the player can route playback accordingly.
 */
class CastManager(
    private val context: Context,
) {
    private var castContext: CastContext? = null
    private var sessionManagerListener: SessionManagerListener<CastSession>? = null

    var isCasting = false
        private set

    var onCastStateChanged: ((Boolean) -> Unit)? = null

    fun initialize() {
        try {
            castContext = CastContext.getSharedInstance(context)
            val sessionManager = castContext?.sessionManager
            val listener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    isCasting = true
                    onCastStateChanged?.invoke(true)
                    logcat(LogPriority.INFO) { "Cast session started: $sessionId" }
                }

                override fun onSessionEnded(session: CastSession, error: Int) {
                    isCasting = false
                    onCastStateChanged?.invoke(false)
                    logcat(LogPriority.INFO) { "Cast session ended" }
                }

                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    isCasting = true
                    onCastStateChanged?.invoke(true)
                }

                override fun onSessionStarting(session: CastSession) {}
                override fun onSessionStartFailed(session: CastSession, error: Int) {}
                override fun onSessionEnding(session: CastSession) {}
                override fun onSessionResuming(session: CastSession, sessionId: String) {}
                override fun onSessionResumeFailed(session: CastSession, error: Int) {}
                override fun onSessionSuspended(session: CastSession, reason: Int) {}
            }
            sessionManagerListener = listener
            sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to initialize CastContext: ${e.message}" }
        }
    }

    fun getCurrentCastSession(): CastSession? {
        return castContext?.sessionManager?.currentCastSession
    }

    fun release() {
        val listener = sessionManagerListener ?: return
        castContext?.sessionManager?.removeSessionManagerListener(
            listener,
            CastSession::class.java,
        )
    }

    val isCastAvailable: Boolean
        get() = castContext != null
}
