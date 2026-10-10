package miko.jssource

/**
 * Low-level script engine seam. Each platform supplies an implementation:
 * J2V8 on Android (existing `NovelJsRuntime` in `app`), JavaScriptCore on iOS.
 *
 * Implementations must bind the [JsNativeApi] surface — iOS does this via a
 * single `__nativeInvoke(name, argsJson)` callback plus the JS-side
 * [JsNativeDispatch] adapter, Android via direct J2V8 method registration.
 *
 * All calls are synchronous and happen on the engine's own thread/context;
 * implementations are NOT required to be thread-safe.
 */
interface JsEngine {
    /**
     * Evaluate [script] and return its completion value as a Kotlin
     * primitive/boxed type (Boolean, Double, String, or null). Structured
     * results are marshalled via JSON.stringify on the JS side.
     */
    fun evaluate(script: String, fileName: String = "plugin.js"): Any?

    /** Release engine resources. After release, [evaluate] must not be called. */
    fun release()
}
