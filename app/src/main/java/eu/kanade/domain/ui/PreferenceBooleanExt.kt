package eu.kanade.domain.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference

/**
 * Wraps an enum [Preference] into a [Preference]<Boolean> that is true when the
 * enum value equals [targetValue] and false otherwise. Setting true sets the
 * enum to [targetValue]; setting false sets it to [defaultValue].
 */
fun <T> Preference<T>.asBooleanPreference(
    targetValue: T,
    defaultValue: T,
): Preference<Boolean> where T : Enum<T>, T : Comparable<T> {
    val source = this
    return object : Preference<Boolean> {
        override fun key(): String = source.key()
        override fun get(): Boolean = source.get() == targetValue
        override fun set(value: Boolean) {
            source.set(if (value) targetValue else defaultValue)
        }
        override fun isSet(): Boolean = source.isSet()
        override fun delete() = source.delete()
        override fun defaultValue(): Boolean = source.defaultValue() == targetValue
        override fun changes(): Flow<Boolean> = source.changes().map { it == targetValue }
        override fun stateIn(scope: CoroutineScope): StateFlow<Boolean> =
            source.changes().map { it == targetValue }
                .stateIn(scope, SharingStarted.Eagerly, get())
    }
}
