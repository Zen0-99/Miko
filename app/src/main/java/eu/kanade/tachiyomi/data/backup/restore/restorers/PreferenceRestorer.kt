package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.models.BackupCollection
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.source.sourcePreferences
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.plusAssign
import tachiyomi.domain.collection.anime.interactor.GetAnimeCollections
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PreferenceRestorer(
    private val context: Context,
    private val getMangaCollections: GetMangaCollections = Injekt.get(),
    private val getAnimeCollections: GetAnimeCollections = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {
    suspend fun restoreApp(
        preferences: List<BackupPreference>,
        backupCollections: List<BackupCollection>?,
    ) {
        restorePreferences(
            preferences,
            preferenceStore,
            backupCollections,
        )

        AnimeLibraryUpdateJob.setupTask(context)
        MangaLibraryUpdateJob.setupTask(context)
        BackupCreateJob.setupTask(context)
    }

    suspend fun restoreSource(preferences: List<BackupSourcePreferences>) {
        preferences.forEach {
            val sourcePrefs = AndroidPreferenceStore(context, sourcePreferences(it.sourceKey))
            restorePreferences(it.prefs, sourcePrefs)
        }
    }

    private suspend fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
        backupCollections: List<BackupCollection>? = null,
    ) {
        val allMangaCollections = if (backupCollections != null) getMangaCollections.await() else emptyList()
        val allAnimeCollections = if (backupCollections != null) getAnimeCollections.await() else emptyList()

        val mangaCollectionsByName = allMangaCollections.associateBy { it.name }
        val animeCollectionsByName = allAnimeCollections.associateBy { it.name }
        val backupCollectionsById = backupCollections?.associateBy { it.id.toString() }.orEmpty()

        val prefs = preferenceStore.getAll()
        toRestore.forEach { (key, value) ->
            try {
                when (value) {
                    is IntPreferenceValue -> {
                        if (prefs[key] is Int?) {
                            val newValue = if (key == LibraryPreferences.DEFAULT_MANGA_COLLECTION_PREF_KEY) {
                                backupCollectionsById[value.value.toString()]
                                    ?.let { mangaCollectionsByName[it.name]?.id?.toInt() }
                            } else if (key == LibraryPreferences.DEFAULT_ANIME_COLLECTION_PREF_KEY) {
                                backupCollectionsById[value.value.toString()]
                                    ?.let { animeCollectionsByName[it.name]?.id?.toInt() }
                            } else {
                                value.value
                            }

                            newValue?.let { preferenceStore.getInt(key).set(it) }
                        }
                    }
                    is LongPreferenceValue -> {
                        if (prefs[key] is Long?) {
                            preferenceStore.getLong(key).set(value.value)
                        }
                    }
                    is FloatPreferenceValue -> {
                        if (prefs[key] is Float?) {
                            preferenceStore.getFloat(key).set(value.value)
                        }
                    }
                    is StringPreferenceValue -> {
                        if (prefs[key] is String?) {
                            preferenceStore.getString(key).set(value.value)
                        }
                    }
                    is BooleanPreferenceValue -> {
                        if (prefs[key] is Boolean?) {
                            preferenceStore.getBoolean(key).set(value.value)
                        }
                    }
                    is StringSetPreferenceValue -> {
                        if (prefs[key] is Set<*>?) {
                            val restored = restoreCollectionsPreference(
                                key,
                                value.value,
                                preferenceStore,
                                backupCollectionsById,
                                mangaCollectionsByName,
                                animeCollectionsByName,
                            )
                            if (!restored) preferenceStore.getStringSet(key).set(value.value)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PreferenceRestorer", "Failed to restore preference <$key>", e)
            }
        }
    }

    private fun restoreCollectionsPreference(
        key: String,
        value: Set<String>,
        preferenceStore: PreferenceStore,
        backupCollectionsById: Map<String, BackupCollection>,
        mangaCollectionsByName: Map<String, Collection>,
        animeCollectionsByName: Map<String, Collection>,
    ): Boolean {
        val collectionPreferences = LibraryPreferences.collectionPreferenceKeys + DownloadPreferences.collectionPreferenceKeys
        if (key !in collectionPreferences) return false

        val ids = value.flatMap {
            listOf(
                backupCollectionsById[it]?.name?.let { name ->
                    mangaCollectionsByName[name]?.id?.toString()
                },
                backupCollectionsById[it]?.name?.let { name ->
                    animeCollectionsByName[name]?.id?.toString()
                },
            )
        }.filterNotNull()

        if (ids.isNotEmpty()) {
            preferenceStore.getStringSet(key) += ids
        }
        return true
    }
}
