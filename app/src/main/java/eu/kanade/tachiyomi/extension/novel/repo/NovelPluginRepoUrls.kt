package eu.kanade.tachiyomi.extension.novel.repo

internal fun resolveNovelPluginRepoIndexUrls(baseUrl: String): List<String> {
    val normalized = baseUrl.trim().trimEnd('/')
    if (normalized.isEmpty()) return emptyList()

    return if (normalized.endsWith(".json", ignoreCase = true)) {
        listOf(normalized)
    } else {
        listOf(
            "$normalized/plugins.min.json",
            "$normalized/plugins.json",
            "$normalized/.dist/plugins.min.json",
            "$normalized/.dist/plugins.json",
            "$normalized/index.min.json",
            "$normalized/index.json",
        )
    }
}

internal fun resolveNovelPluginRepoIndexUrl(baseUrl: String): String {
    return resolveNovelPluginRepoIndexUrls(baseUrl).firstOrNull().orEmpty()
}
