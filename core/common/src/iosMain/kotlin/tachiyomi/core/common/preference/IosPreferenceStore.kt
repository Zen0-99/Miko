package tachiyomi.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDefaultsDidChangeNotification

/**
 * NSUserDefaults-backed [PreferenceStore] — iOS counterpart of
 * AndroidPreferenceStore (SharedPreferences). Scalar reads use the typed
 * accessors guarded by objectForKey so non-default defaults survive
 * the "missing key returns zero" semantics.
 */
class IosPreferenceStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : PreferenceStore {

    private inner class IosPreference<T>(
        private val key: String,
        private val defaultValue: T,
        private val reader: NSUserDefaults.(String) -> T,
        private val writer: NSUserDefaults.(String, T) -> Unit,
    ) : Preference<T> {
        override fun key() = key
        override fun get(): T = defaults.reader(key)
        override fun set(value: T) {
            defaults.writer(key, value)
            defaults.synchronize()
        }
        override fun isSet(): Boolean = defaults.objectForKey(key) != null
        override fun delete() = defaults.removeObjectForKey(key)
        override fun defaultValue(): T = defaultValue
        override fun changes(): Flow<T> = callbackFlow {
            trySend(get())
            val observer = NSNotificationCenter.defaultCenter.addObserverForName(
                NSUserDefaultsDidChangeNotification,
                null,
                NSOperationQueue.mainQueue,
            ) { _ ->
                trySend(get())
            }
            awaitClose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
        }
        override fun stateIn(scope: CoroutineScope): StateFlow<T> =
            changes().stateIn(scope, SharingStarted.Eagerly, get())
    }

    private fun <T> preference(
        key: String,
        defaultValue: T,
        reader: NSUserDefaults.(String) -> T,
        writer: NSUserDefaults.(String, T) -> Unit,
    ): Preference<T> = IosPreference(key, defaultValue, reader, writer)

    private inline fun <T> readOrDefault(key: String, default: T, read: NSUserDefaults.() -> T): T =
        if (defaults.objectForKey(key) == null) default else defaults.read()

    override fun getString(key: String, defaultValue: String): Preference<String> = preference(
        key,
        defaultValue,
        reader = { readOrDefault(key, defaultValue) { stringForKey(key) ?: defaultValue } },
        writer = { k, v -> setObject(v, forKey = k) },
    )

    override fun getLong(key: String, defaultValue: Long): Preference<Long> = preference(
        key,
        defaultValue,
        reader = { readOrDefault(key, defaultValue) { integerForKey(key) } },
        writer = { k, v -> setInteger(v, forKey = k) },
    )

    override fun getInt(key: String, defaultValue: Int): Preference<Int> = preference(
        key,
        defaultValue,
        reader = { readOrDefault(key, defaultValue) { integerForKey(key).toInt() } },
        writer = { k, v -> setInteger(v.toLong(), forKey = k) },
    )

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> = preference(
        key,
        defaultValue,
        reader = { readOrDefault(key, defaultValue) { floatForKey(key) } },
        writer = { k, v -> setFloat(v, forKey = k) },
    )

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = preference(
        key,
        defaultValue,
        reader = { readOrDefault(key, defaultValue) { boolForKey(key) } },
        writer = { k, v -> setBool(v, forKey = k) },
    )

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> = preference(
        key,
        defaultValue,
        reader = {
            readOrDefault(key, defaultValue) {
                (arrayForKey(key) as? List<*>)?.filterIsInstance<String>()?.toSet() ?: defaultValue
            }
        },
        writer = { k, v -> setObject(v.toList(), forKey = k) },
    )

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = preference(
        key,
        defaultValue,
        reader = {
            readOrDefault(key, defaultValue) {
                (stringForKey(key) ?: return@readOrDefault defaultValue)
                    .let { raw -> runCatching { deserializer(raw) }.getOrDefault(defaultValue) }
            }
        },
        writer = { k, v -> setObject(serializer(v), forKey = k) },
    )

    override fun getAll(): Map<String, *> = defaults.dictionaryRepresentation()
}
