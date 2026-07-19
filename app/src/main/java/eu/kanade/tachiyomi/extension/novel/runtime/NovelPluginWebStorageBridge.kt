package eu.kanade.tachiyomi.extension.novel.runtime

import tachiyomi.data.extension.novel.NovelPluginKeyValueStore

/**
 * Bridge that provides browser-like localStorage and sessionStorage semantics
 * for novel plugins that depend on web storage APIs.
 *
 * - localStorage: persisted via [NovelPluginKeyValueStore] (SharedPreferences-backed)
 * - sessionStorage: in-memory only, cleared when the process dies
 *
 * Both namespaces are plugin-scoped and use separate key prefixes from generic
 * plugin key-value storage to avoid collisions.
 */
class NovelPluginWebStorageBridge(
    private val pluginId: String,
    private val keyValueStore: NovelPluginKeyValueStore,
) {
    private companion object {
        const val LOCAL_PREFIX = "web_local_"
        const val SESSION_PREFIX = "web_session_"
        const val LOCAL_SYNC_META = "web_local_sync_timestamp"
        const val SESSION_SYNC_META = "web_session_sync_timestamp"
    }

    private val sessionStore: MutableMap<String, String> = mutableMapOf()
    private var sessionSyncTimestamp: Long = 0L

    fun localStorageGet(key: String): String? {
        return keyValueStore.get(pluginId, LOCAL_PREFIX + key)
    }

    fun localStorageSet(key: String, value: String) {
        keyValueStore.set(pluginId, LOCAL_PREFIX + key, value)
        updateLocalSyncTimestamp()
    }

    fun localStorageRemove(key: String) {
        keyValueStore.remove(pluginId, LOCAL_PREFIX + key)
        updateLocalSyncTimestamp()
    }

    fun localStorageClear() {
        val allKeys = keyValueStore.keys(pluginId)
        allKeys
            .filter { it.startsWith(LOCAL_PREFIX) }
            .forEach { keyValueStore.remove(pluginId, it) }
        updateLocalSyncTimestamp()
    }

    fun localStorageKeys(): Set<String> {
        return keyValueStore.keys(pluginId)
            .filter { it.startsWith(LOCAL_PREFIX) && it != LOCAL_SYNC_META }
            .map { it.removePrefix(LOCAL_PREFIX) }
            .toSet()
    }

    fun localStorageSyncTimestamp(): Long {
        val stored = keyValueStore.get(pluginId, LOCAL_SYNC_META)
        return stored?.toLongOrNull() ?: 0L
    }

    fun sessionStorageGet(key: String): String? {
        return sessionStore[key]
    }

    fun sessionStorageSet(key: String, value: String) {
        sessionStore[key] = value
        updateSessionSyncTimestamp()
    }

    fun sessionStorageRemove(key: String) {
        sessionStore.remove(key)
        updateSessionSyncTimestamp()
    }

    fun sessionStorageClear() {
        sessionStore.clear()
        updateSessionSyncTimestamp()
    }

    fun sessionStorageKeys(): Set<String> {
        return sessionStore.keys.toSet()
    }

    fun sessionStorageSyncTimestamp(): Long {
        return sessionSyncTimestamp
    }

    private fun updateLocalSyncTimestamp() {
        val now = System.currentTimeMillis()
        keyValueStore.set(pluginId, LOCAL_SYNC_META, now.toString())
    }

    private fun updateSessionSyncTimestamp() {
        sessionSyncTimestamp = System.currentTimeMillis()
    }
}
