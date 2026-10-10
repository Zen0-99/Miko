package tachiyomi.core.common.util.system

import logcat.asLog
import logcat.logcat

actual fun Any.logcat(
    priority: LogPriority,
    throwable: Throwable?,
    message: () -> String,
) = logcat(priority = priority.toLibPriority()) {
    var msg = message()
    if (throwable != null) {
        if (msg.isNotBlank()) msg += "\n"
        msg += throwable.asLog()
    }
    msg
}

private fun LogPriority.toLibPriority(): logcat.LogPriority = when (this) {
    LogPriority.VERBOSE -> logcat.LogPriority.VERBOSE
    LogPriority.DEBUG -> logcat.LogPriority.DEBUG
    LogPriority.INFO -> logcat.LogPriority.INFO
    LogPriority.WARN -> logcat.LogPriority.WARN
    LogPriority.ERROR -> logcat.LogPriority.ERROR
}

/**
 * Compatibility overload for app call sites that pass the Square
 * `logcat.LogPriority` enum directly (Android classpath only).
 */
fun Any.logcat(
    priority: logcat.LogPriority,
    throwable: Throwable? = null,
    message: () -> String = { "" },
) = logcat(priority = priority) {
    var msg = message()
    if (throwable != null) {
        if (msg.isNotBlank()) msg += "\n"
        msg += throwable.asLog()
    }
    msg
}
