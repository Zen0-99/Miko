package miko.jssource

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.JavaScriptCore.JSContextGetGlobalObject
import platform.JavaScriptCore.JSContextRef
import platform.JavaScriptCore.JSEvaluateScript
import platform.JavaScriptCore.JSGlobalContextCreate
import platform.JavaScriptCore.JSGlobalContextRef
import platform.JavaScriptCore.JSGlobalContextRelease
import platform.JavaScriptCore.JSObjectCallAsFunction
import platform.JavaScriptCore.JSObjectGetPrivate
import platform.JavaScriptCore.JSObjectGetProperty
import platform.JavaScriptCore.JSObjectMakeFunctionWithCallback
import platform.JavaScriptCore.JSObjectRef
import platform.JavaScriptCore.JSObjectSetPrivate
import platform.JavaScriptCore.JSObjectSetProperty
import platform.JavaScriptCore.JSStringCreateWithUTF8CString
import platform.JavaScriptCore.JSStringGetMaximumUTF8CStringSize
import platform.JavaScriptCore.JSStringGetUTF8CString
import platform.JavaScriptCore.JSStringRef
import platform.JavaScriptCore.JSStringRelease
import platform.JavaScriptCore.JSType
import platform.JavaScriptCore.JSValueGetType
import platform.JavaScriptCore.JSValueMakeString
import platform.JavaScriptCore.JSValueRef
import platform.JavaScriptCore.JSValueRefVar
import platform.JavaScriptCore.JSValueToBoolean
import platform.JavaScriptCore.JSValueToNumber
import platform.JavaScriptCore.JSValueToStringCopy
import platform.JavaScriptCore.kJSPropertyAttributeNone
import platform.posix.size_t

/**
 * JavaScriptCore-backed [JsEngine] for iOS.
 *
 * Uses the plain C API (the only JS-interop surface K/N binds fully):
 * a single `__nativeInvoke(name, argsJson)` callback is registered on the
 * global object; [JsNativeDispatch] builds the `__native.*` JS facade on
 * top. The callback's private data holds a [StableRef] to the
 * [JsNativeDispatcher], which is how JS calls reach Kotlin without
 * capturing lambdas (staticCFunction requires non-capturing callbacks).
 */
@OptIn(ExperimentalForeignApi::class)
class JsCoreEngine(
    private val pluginId: String,
    nativeApi: JsNativeApi,
) : JsEngine {

    private val dispatcher = JsNativeDispatcher(nativeApi)
    private val dispatcherRef = StableRef.create(dispatcher)
    private val context: JSGlobalContextRef

    init {
        context = JSGlobalContextCreate(null)
            ?: error("Failed to create JavaScriptCore global context for plugin $pluginId")
        bindNativeInvoke()
        // Build the __native.* JS facade on top of __nativeInvoke.
        evaluate(JsNativeDispatch.script, "__native-dispatch.js")
    }

    private fun bindNativeInvoke() {
        memScoped {
            val nameStr = JSStringCreateWithUTF8CString("__nativeInvoke")
            val fn = JSObjectMakeFunctionWithCallback(context, nameStr, nativeInvokeCallback)
            JSObjectSetPrivate(fn, dispatcherRef.asCPointer())
            val global = JSContextGetGlobalObject(context)
            JSObjectSetProperty(context, global, nameStr, fn, kJSPropertyAttributeNone, null)
            JSStringRelease(nameStr)
        }
    }

    override fun evaluate(script: String, fileName: String): Any? {
        return memScoped {
            val jsScript = JSStringCreateWithUTF8CString(script)
            val jsFile = JSStringCreateWithUTF8CString(fileName)
            val exceptionVar = alloc<JSValueRefVar>()
            val result = JSEvaluateScript(
                context,
                jsScript,
                null,
                jsFile,
                1,
                exceptionVar.ptr,
            )
            val exception = exceptionVar.value
            JSStringRelease(jsScript)
            JSStringRelease(jsFile)
            if (exception != null) {
                throw JsEngineException(
                    "JS error in $fileName (plugin=$pluginId): ${jsValueToKotlinString(exception)}",
                )
            }
            result?.let { jsValueToKotlin(it) }
        }
    }

    override fun release() {
        JSGlobalContextRelease(context)
        dispatcherRef.dispose()
    }

    // ------------------------------------------------------------------
    // JSValue <-> Kotlin conversion
    // ------------------------------------------------------------------

    private fun jsValueToKotlin(value: JSValueRef): Any? {
        val ctx = context
        return when (JSValueGetType(ctx, value)) {
            JSType.kJSTypeUndefined, JSType.kJSTypeNull -> null
            JSType.kJSTypeBoolean -> JSValueToBoolean(ctx, value)
            JSType.kJSTypeNumber -> JSValueToNumber(ctx, value, null)
            JSType.kJSTypeString -> jsStringRefToKotlin(JSValueToStringCopy(ctx, value, null))
            JSType.kJSTypeObject -> jsValueToJsonString(value)
            else -> jsStringRefToKotlin(JSValueToStringCopy(ctx, value, null))
        }
    }

    private fun jsValueToKotlinString(value: JSValueRef): String {
        return runCatching { jsValueToJsonString(value) }
            .getOrNull()
            ?: jsStringRefToKotlin(JSValueToStringCopy(context, value, null))
            ?: "<unprintable>"
    }

    /** JSON.stringify(value) — used for object results and error reporting. */
    private fun jsValueToJsonString(value: JSValueRef): String? {
        val ctx = context
        return memScoped {
            val jsonName = JSStringCreateWithUTF8CString("JSON")
            val stringifyName = JSStringCreateWithUTF8CString("stringify")
            val global = JSContextGetGlobalObject(ctx)
            val jsonObj = JSObjectGetProperty(ctx, global, jsonName, null)
            val stringifyFn = jsonObj?.let {
                JSObjectGetProperty(ctx, it as JSObjectRef, stringifyName, null)
            }
            JSStringRelease(jsonName)
            JSStringRelease(stringifyName)
            if (stringifyFn == null) return@memScoped null
            val argsArr = allocArray<JSValueRefVar>(1)
            argsArr[0] = value
            val stringified = JSObjectCallAsFunction(
                ctx,
                stringifyFn as JSObjectRef,
                jsonObj as JSObjectRef,
                1u.convert(),
                argsArr,
                null,
            )
            stringified?.let { jsStringRefToKotlin(JSValueToStringCopy(ctx, it, null)) }
        }
    }

    private fun jsStringRefToKotlin(jsString: JSStringRef?): String? {
        if (jsString == null) return null
        return memScoped {
            val maxSize = JSStringGetMaximumUTF8CStringSize(jsString)
            val buffer = allocArray<ByteVar>(maxSize.convert())
            val written = JSStringGetUTF8CString(jsString, buffer, maxSize)
            JSStringRelease(jsString)
            if (written.toLong() > 0) buffer.toKString() else ""
        }
    }

    class JsEngineException(message: String) : Exception(message)

    companion object {
        /**
         * JS→Kotlin trampoline for `__nativeInvoke(name, argsJson)`.
         * Must be a non-capturing function for staticCFunction; the
         * dispatcher is recovered from the function object's private data.
         */
        private fun nativeInvokeTrampoline(
            ctx: JSContextRef?,
            function: JSObjectRef?,
            thisObject: JSObjectRef?,
            argumentCount: size_t,
            arguments: CPointer<JSValueRefVar>?,
            exception: CPointer<JSValueRefVar>?,
        ): JSValueRef? {
            val dispatcher = function?.let { JSObjectGetPrivate(it) }
                ?.asStableRef<JsNativeDispatcher>()
                ?.get() ?: return null
            fun argToString(i: Int): String? {
                if (arguments == null || i.toULong() >= argumentCount) return null
                val str = JSValueToStringCopy(ctx, arguments[i], null) ?: return null
                return memScoped {
                    val maxSize = JSStringGetMaximumUTF8CStringSize(str)
                    val buffer = allocArray<ByteVar>(maxSize.convert())
                    val written = JSStringGetUTF8CString(str, buffer, maxSize)
                    JSStringRelease(str)
                    if (written.toLong() > 0) buffer.toKString() else ""
                }
            }
            return try {
                val name = argToString(0) ?: ""
                val argsJson = argToString(1) ?: "[]"
                val resultJson = dispatcher.dispatch(name, argsJson)
                val resultStr = JSStringCreateWithUTF8CString(resultJson)
                val out = ctx?.let { JSValueMakeString(it, resultStr) }
                JSStringRelease(resultStr)
                out
            } catch (e: Throwable) {
                val msg = JSStringCreateWithUTF8CString("native invoke failed: ${e.message}")
                val errValue = ctx?.let { JSValueMakeString(it, msg) }
                exception?.pointed?.let { it.value = errValue }
                JSStringRelease(msg)
                null
            }
        }

        private val nativeInvokeCallback = staticCFunction(::nativeInvokeTrampoline)
    }
}
