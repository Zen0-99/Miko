package tachiyomi.core.common.util.system

actual fun Any.logcat(
    priority: LogPriority,
    throwable: Throwable?,
    message: () -> String,
) {
    val tag = this::class.simpleName ?: "logcat"
    val suffix = throwable?.let { "\n" + it.stackTraceToString() }.orEmpty()
    println("[${priority.name}] $tag: ${message()}$suffix")
}
