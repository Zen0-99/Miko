package eu.kanade.tachiyomi.extension.novel.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelLoadResult
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.FORCE_NETWORK
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.UpdateNovelExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionIndexEntry
import mihon.domain.extensionrepo.service.ExtensionRepoService
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import kotlin.time.Duration.Companion.days

internal class NovelExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val getExtensionRepo: GetNovelExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateNovelExtensionRepo by injectLazy()
    private val extensionManager: NovelExtensionManager by injectLazy()
    private val json: Json by injectLazy()
    private val extensionRepoService: ExtensionRepoService by injectLazy()

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_novel_ext_check", 0)
    }

    suspend fun findExtensions(): List<NovelExtension.Available> {
        return withIOContext {
            val repos = getExtensionRepo.getAll()
            android.util.Log.i("NovelExtApi", "findExtensions: fetching from ${repos.size} repo(s)")
            repos.forEach { repo ->
                android.util.Log.i("NovelExtApi", "  repo: ${repo.baseUrl}")
            }
            repos
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<NovelExtension.Available> {
        val repoBaseUrl = extRepo.baseUrl
        return try {
            android.util.Log.i("NovelExtApi", "getExtensions: fetching index from $repoBaseUrl")
            val entries = extensionRepoService.fetchExtensionIndex(repoBaseUrl)
                ?: run {
                    android.util.Log.w("NovelExtApi", "getExtensions: fetchExtensionIndex returned null for $repoBaseUrl")
                    return emptyList()
                }
            android.util.Log.i("NovelExtApi", "getExtensions: got ${entries.size} entries from $repoBaseUrl")
            val extensions = entries.toNovelExtensions()
            android.util.Log.i("NovelExtApi", "getExtensions: ${extensions.size} entries passed lib version filter")
            extensions.forEach { ext ->
                android.util.Log.i("NovelExtApi", "  available: ${ext.name} v${ext.versionName} (code ${ext.versionCode}) pkg=${ext.pkgName}")
            }
            extensions
        } catch (e: Throwable) {
            android.util.Log.e("NovelExtApi", "Failed to get novel extensions from $repoBaseUrl", e)
            emptyList()
        }
    }

    private fun List<ExtensionIndexEntry>.toNovelExtensions(): List<NovelExtension.Available> {
        return this
            .filter {
                it.libVersion >= NovelExtensionLoader.LIB_VERSION_MIN &&
                    it.libVersion <= NovelExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                NovelExtension.Available(
                    name = it.name
                        .substringAfter("Tachiyomi: ")
                        .substringAfter("Miko: ")
                        .substringAfter("Yokai: "),
                    pkgName = it.pkgName,
                    versionName = it.versionName,
                    versionCode = it.versionCode,
                    libVersion = it.libVersion,
                    lang = it.lang,
                    isNsfw = it.isNsfw,
                    sources = it.sources.map { src ->
                        NovelExtension.Available.NovelSource(
                            id = src.id,
                            lang = src.lang,
                            name = src.name,
                            baseUrl = src.baseUrl,
                        )
                    },
                    apkName = it.apkName,
                    iconUrl = it.iconUrl,
                    repoUrl = it.repoUrl,
                )
            }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<NovelExtension.Installed>? {
        if (fromAvailableExtensionList &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            extensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
        }

        val installedExtensions = NovelExtensionLoader.loadExtensions(context)
            .filterIsInstance<NovelLoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<NovelExtension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(
                names = extensionsWithUpdate.map { it.name },
                novel = true,
            )
        }

        return extensionsWithUpdate
    }

    private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<NovelExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= NovelExtensionLoader.LIB_VERSION_MIN && libVersion <= NovelExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                NovelExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: ").substringAfter("Miko: ").substringAfter("Yokai: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources?.map(extensionSourceMapper).orEmpty(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    fun getApkUrl(extension: NovelExtension.Available): String {
        return "${extension.repoUrl}/apk/${extension.apkName}"
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDoubleOrNull() ?: 1.0
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: String,
    val lang: String,
    val name: String,
    val baseUrl: String,
    val supportsComments: Boolean? = null,
)

private val extensionSourceMapper: (ExtensionSourceJsonObject) -> NovelExtension.Available.NovelSource = {
    NovelExtension.Available.NovelSource(
        id = it.id.toLong(),
        lang = it.lang,
        name = it.name,
        baseUrl = it.baseUrl,
        supportsComments = it.supportsComments ?: false,
    )
}
