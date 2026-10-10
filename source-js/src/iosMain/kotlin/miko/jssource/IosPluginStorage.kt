package miko.jssource

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults

/**
 * Plugin-scoped persistent KV storage backed by NSUserDefaults.
 * Mirrors NovelPluginKeyValueStore semantics: values are namespaced per
 * plugin id so plugins cannot collide.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPluginStorage(
    private val pluginId: String,
    private val suitePrefix: String = "storage",
) {
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
    private val nsPrefix = "noveljs:$pluginId:$suitePrefix:"

    fun get(key: String): String? = defaults.stringForKey(nsPrefix + key)

    fun set(key: String, value: String) {
        defaults.setObject(value, forKey = nsPrefix + key)
    }

    fun remove(key: String) {
        defaults.removeObjectForKey(nsPrefix + key)
    }

    fun clear() {
        keys().forEach { remove(it) }
    }

    fun keys(): List<String> {
        @Suppress("UNCHECKED_CAST")
        val dict = defaults.dictionaryRepresentation() as? Map<Any?, *> ?: return emptyList()
        return dict.keys.mapNotNull { it as? String }
            .filter { it.startsWith(nsPrefix) }
            .map { it.removePrefix(nsPrefix) }
    }
}

/** In-memory session storage — cleared when the runtime is released. */
class IosSessionStorage {
    private val map = mutableMapOf<String, String>()

    fun get(key: String): String? = map[key]
    fun set(key: String, value: String) {
        map[key] = value
    }
    fun remove(key: String) {
        map.remove(key)
    }
    fun clear() {
        map.clear()
    }
    fun keys(): List<String> = map.keys.toList()
}
