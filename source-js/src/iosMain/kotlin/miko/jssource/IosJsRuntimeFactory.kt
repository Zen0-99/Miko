package miko.jssource

import io.ktor.client.HttpClient
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/**
 * iOS entry point: creates a [JsRuntime] hosting a JavaScriptCore engine
 * with the full `__native` bridge. Mirrors NovelJsRuntimeFactory.create.
 *
 * @param assetLoader loads bundled JS assets (dayjs, htmlparser2). Default
 * reads `<bundle>/js/<name>` from the app/test bundle; callers embedding
 * assets elsewhere inject their own.
 */
@OptIn(ExperimentalForeignApi::class)
class IosJsRuntimeFactory(
    private val json: Json = Json,
    private val assetLoader: ((String) -> String?)? = defaultBundleAssetLoader,
) {
    fun create(
        pluginId: String,
        client: HttpClient? = null,
    ): JsRuntime {
        val nativeApi = if (client != null) {
            IosJsNativeApi(pluginId = pluginId, client = client, json = json)
        } else {
            IosJsNativeApi(pluginId = pluginId, json = json)
        }
        val engine = JsCoreEngine(pluginId, nativeApi)
        return JsRuntime(
            pluginId = pluginId,
            engine = engine,
            moduleRegistry = JsModuleRegistry(assetLoader),
        )
    }

    companion object {
        /**
         * Reads `js/<name>` assets from the main bundle. Returns null when the
         * bundle doesn't ship them — the registry then installs stub modules.
         */
        private val defaultBundleAssetLoader: (String) -> String? = { path ->
            val bundle = NSBundle.mainBundle
            val file = bundle.pathForResource(
                name = path.substringAfterLast('/'),
                ofType = null,
                inDirectory = path.substringBeforeLast('/', ""),
            )
            file?.let {
                @Suppress("CAST_NEVER_SUCCEEDS")
                NSString.stringWithContentsOfFile(it, encoding = NSUTF8StringEncoding, error = null) as? String
            }
        }
    }
}
