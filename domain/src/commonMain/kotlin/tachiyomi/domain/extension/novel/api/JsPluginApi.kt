package tachiyomi.domain.extension.novel.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionIndexEntry
import mihon.domain.extensionrepo.service.ExtensionRepoService
import tachiyomi.core.common.util.system.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.model.NovelPlugin

interface JsPluginApiFacade {
    suspend fun fetchAvailablePlugins(): List<NovelPlugin.Available>

    /**
     * APK-index entries across all saved repos. Android shows these in the
     * extensions list as installable; iOS renders them display-only since
     * APKs can't run there.
     */
    suspend fun fetchRepoExtensions(): List<ExtensionIndexEntry>
}

interface JsPluginRepoProvider {
    suspend fun getAll(): List<ExtensionRepo>
}

/**
 * Unions every saved repo across the three per-type `extension_repos` tables,
 * deduped by baseUrl — a combined repo registered under multiple tabs is
 * fetched once, and a repo serving only manga/anime-kind plugins still
 * surfaces.
 */
class CombinedJsPluginRepoProvider(
    private val novelRepoRepository: NovelExtensionRepoRepository,
    private val mangaRepoRepository: MangaExtensionRepoRepository,
    private val animeRepoRepository: AnimeExtensionRepoRepository,
) : JsPluginRepoProvider {
    override suspend fun getAll(): List<ExtensionRepo> {
        return (
            novelRepoRepository.getAll() +
                mangaRepoRepository.getAll() +
                animeRepoRepository.getAll()
            ).distinctBy { it.baseUrl }
    }
}

/**
 * Shared counterpart of the app's `NovelPluginApi`. [includeKotlinExtensions]
 * stays true on Android (APK rows are installable there); iOS passes false —
 * APK entries can't be installed and shouldn't appear in the extensions list.
 */
class JsPluginApi(
    private val repoProvider: JsPluginRepoProvider,
    private val fetcher: JsPluginIndexFetcher,
    private val parser: JsPluginIndexParser,
    private val includeKotlinExtensions: Boolean = true,
    private val extensionRepoService: ExtensionRepoService? = null,
) : JsPluginApiFacade {
    override suspend fun fetchAvailablePlugins(): List<NovelPlugin.Available> {
        return withContext(Dispatchers.Default) {
            val repos = repoProvider.getAll()
            logcat(LogPriority.DEBUG) { "JsPluginApi: fetching available plugins from ${repos.size} repos" }
            repos.flatMap { repo ->
                fetchPluginsFromRepo(repo).map { plugin ->
                    plugin.copy(
                        repoName = repo.name.ifBlank { repo.shortName ?: repo.baseUrl },
                    )
                }
            }.let { plugins ->
                if (includeKotlinExtensions) {
                    plugins
                } else {
                    plugins.filterNot { it.isKotlinExtension }
                }
            }.also {
                logcat(LogPriority.DEBUG) { "JsPluginApi: total available plugins fetched = ${it.size}" }
            }
        }
    }

    override suspend fun fetchRepoExtensions(): List<ExtensionIndexEntry> {
        val service = extensionRepoService ?: return emptyList()
        return withContext(Dispatchers.Default) {
            repoProvider.getAll().flatMap { repo ->
                try {
                    service.fetchExtensionIndex(repo.baseUrl).orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to fetch extension index from ${repo.baseUrl}" }
                    emptyList()
                }
            }
        }
    }

    private suspend fun fetchPluginsFromRepo(repo: ExtensionRepo): List<NovelPlugin.Available> {
        return try {
            val payload = fetcher.fetch(repo.baseUrl)
            parser.parse(payload, repo.baseUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch JS plugins from ${repo.baseUrl}" }
            emptyList()
        }
    }
}
