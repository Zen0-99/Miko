package eu.kanade.tachiyomi.novelsource

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

interface ConfigurableNovelSource : NovelSource {

    /**
     * Gets instance of [SharedPreferences] scoped to the specific source.
     */
    fun getSourcePreferences(): SharedPreferences =
        Injekt.get<Application>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

    fun setupPreferenceScreen(screen: PreferenceScreen)
}

fun ConfigurableNovelSource.preferenceKey(): String = "source_$id"

fun ConfigurableNovelSource.sourcePreferences(): SharedPreferences =
    Injekt.get<Application>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

fun novelSourcePreferences(key: String): SharedPreferences =
    Injekt.get<Application>().getSharedPreferences(key, Context.MODE_PRIVATE)
