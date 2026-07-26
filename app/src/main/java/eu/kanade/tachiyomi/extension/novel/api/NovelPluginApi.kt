package eu.kanade.tachiyomi.extension.novel.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.model.NovelPlugin

class NovelPluginApi(
    private val repoProvider: NovelPluginRepoProvider,
    private val fetcher: NovelPluginIndexFetcher,
    private val parser: NovelPluginIndexParser,
) : NovelPluginApiFacade {
    override suspend fun fetchAvailablePlugins(): List<NovelPlugin.Available> {
        return withContext(Dispatchers.IO) {
            val repos = repoProvider.getAll()
            logcat(LogPriority.DEBUG) { "NovelPluginApi: fetching available plugins from ${repos.size} repos" }
            repos.flatMap { repo ->
                logcat(LogPriority.DEBUG) { "NovelPluginApi: fetching from repo baseUrl=${repo.baseUrl} name=${repo.name}" }
                fetchPluginsFromRepo(repo).map { plugin ->
                    plugin.copy(
                        repoName = repo.name.ifBlank { repo.shortName ?: repo.baseUrl },
                    )
                }
            }.also {
                logcat(LogPriority.DEBUG) { "NovelPluginApi: total available plugins fetched = ${it.size}" }
            }
        }
    }

    private suspend fun fetchPluginsFromRepo(repo: ExtensionRepo): List<NovelPlugin.Available> {
        return try {
            val payload = fetcher.fetch(repo.baseUrl)
            val plugins = parser.parse(payload, repo.baseUrl)
            logcat(LogPriority.DEBUG) { "NovelPluginApi: parsed ${plugins.size} plugins from ${repo.baseUrl}" }
            plugins
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch novel plugins from ${repo.baseUrl}" }
            emptyList()
        }
    }
}

interface NovelPluginRepoProvider {
    suspend fun getAll(): List<ExtensionRepo>
}

interface NovelPluginApiFacade {
    suspend fun fetchAvailablePlugins(): List<NovelPlugin.Available>
}

interface NovelPluginIndexFetcher {
    suspend fun fetch(repoUrl: String): String
}

/**
 * Implementation of [NovelPluginRepoProvider] that delegates to the existing
 * [NovelExtensionRepoRepository]. JS plugin repos and APK extension repos share
 * the same repository table — this means users add repos once and both systems
 * can use them.
 */
class NovelPluginRepoProviderImpl(
    private val repoRepository: mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository,
) : NovelPluginRepoProvider {
    override suspend fun getAll(): List<ExtensionRepo> {
        return repoRepository.getAll()
    }
}
