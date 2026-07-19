package eu.kanade.tachiyomi.extension.novel.api

internal class NovelExtensionUpdateRunner(
    private val api: NovelPluginUpdateApi = NovelPluginUpdateApi(),
) {
    suspend fun run() {
        api.checkForUpdates()
    }
}
