package eu.kanade.tachiyomi.data.suggestions.novel

import eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper
import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionSourceWeight
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.data.suggestions.util.bestMatchScoreFor
import eu.kanade.tachiyomi.data.suggestions.util.dedupeByCleanTitle
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel

/**
 * Tiered search fallback engine for novels.
 *
 * Searches the active source using title and author queries in priority
 * tiers, then scores and deduplicates the results. Genre-based queries are
 * included when the source supports them.
 */
class NovelSearchFallbackEngine {

    suspend fun fetchSearchFallback(
        novel: Novel,
        source: NovelCatalogueSource,
        seed: SuggestionSeed,
        maxResults: Int = 40,
        onProgress: ((List<SuggestionItem>) -> Unit)? = null,
    ): NovelFallbackOutcome {
        val boundedMaxResults = maxResults.coerceIn(1, 100)
        if (maxResults <= 0) {
            return NovelFallbackOutcome.Empty(NovelFallbackReason.SEARCH_EMPTY)
        }

        val cacheKey = SuggestionCache.makeKey(
            "search:${source.id}:limit:$boundedMaxResults",
            novel.url,
            "NOVEL",
            seed.candidateTitles,
        )
        val cached = SuggestionCache.get(cacheKey)
        if (cached != null) {
            logcat { "[NovelSearchFallbackEngine] Cache HIT for key $cacheKey, count=${cached.size}" }
            return if (cached.isEmpty()) {
                NovelFallbackOutcome.Empty(NovelFallbackReason.SEARCH_EMPTY)
            } else {
                NovelFallbackOutcome.Success(cached)
            }
        }

        logcat { "[NovelSearchFallbackEngine] Cache MISS. Running tiered search fallback for '${novel.title}'" }

        val authorParts = buildList {
            val author = novel.author
            if (!author.isNullOrBlank()) {
                val garbage = setOf(
                    "null", "undefined", "unknown", "none", "no author", "n/a",
                    "нет", "неизвестен", "неизвестный", "неизвестно",
                )
                addAll(
                    author.split(Regex("[,;/&]"))
                        .map { it.trim() }
                        .filter { it.length >= 2 && it.lowercase() !in garbage },
                )
            }
        }.distinct()

        val genreParts = buildList {
            val genres = novel.genre
            if (!genres.isNullOrEmpty()) {
                genres.take(4).forEach { genre ->
                    add(genre)
                    addAll(MultilingualQueryHelper.getGenreTranslations(genre))
                }
            }
        }.distinct()

        val freshFilterList = try {
            source.getFilterList()
        } catch (e: Exception) {
            NovelFilterList()
        }

        val mainTitle = seed.primaryTitle
        val titlesToProcess = listOf(mainTitle)

        // Tier 1: Exact titles
        val tier1Queries = buildList {
            addAll(titlesToProcess)
            SuggestionTitleResolver.parseOriginalTitle(novel.description)?.let { add(it) }
            addAll(seed.candidateTitles)
        }.map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

        // Tier 2: Relaxed title queries
        val tier2Queries = buildList {
            titlesToProcess.forEach { title ->
                val separators = listOf(":", "-", "(", "[", ",", ";")
                separators.forEach { sep ->
                    val part = title.substringBefore(sep).trim()
                    if (part.isNotEmpty() && part != title && part.length >= 6) {
                        add(part)
                    }
                }
                val cleaned = SuggestionTitleResolver.cleanTitle(title)
                if (cleaned.isNotEmpty() && cleaned != title && cleaned.length >= 6) {
                    add(cleaned)
                }
                val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.size >= 3) {
                    val first3 = words.take(3).joinToString(" ")
                    if (first3.length >= 6) add(first3)
                }
            }
        }.map { it.trim() }
            .filter { it.length >= 6 }
            .distinct()

        val tier3Queries = authorParts.map { it.trim() }.filter { it.length >= 2 }.distinct()
        val tier4Queries = genreParts.map { it.trim() }.filter { it.length >= 2 }.distinct()

        val queryTiers = listOf(
            Pair("Tier 1 (Exact Title)", tier1Queries),
            Pair("Tier 2 (Relaxed Title)", tier2Queries),
            Pair("Tier 3 (Author)", tier3Queries),
            Pair("Tier 4 (Genre)", tier4Queries),
        )

        val candidatesToScore = seed.candidateTitles.distinct()
        val uniqueResults = LinkedHashMap<String, SuggestionItem>()
        val filterList = freshFilterList
        var authorAdded = 0
        var genreAdded = 0
        val maxAuthor = 8
        val maxGenre = 8

        logcat {
            "[NovelSearchFallbackEngine] Starting suggestions search for '${novel.title}' (url: ${novel.url}). Candidates: ${seed.candidateTitles}, author: '${novel.author}', genres: ${novel.genre}"
        }

        for ((tierName, tierQueries) in queryTiers) {
            if (synchronized(uniqueResults) { uniqueResults.size >= boundedMaxResults }) break
            if (tierQueries.isEmpty()) continue
            logcat { "[NovelSearchFallbackEngine] Processing $tierName with queries: $tierQueries" }

            coroutineScope {
                tierQueries.forEach { query ->
                    launch {
                        if (synchronized(uniqueResults) { uniqueResults.size >= boundedMaxResults }) return@launch
                        try {
                            val page = source.getSearchNovels(1, query, filterList)
                            if (page.novels.isEmpty()) return@launch

                            val isAuthorQuery = authorParts.any { it.equals(query, ignoreCase = true) }
                            val isGenreQuery = genreParts.any { it.equals(query, ignoreCase = true) }
                            val isTitleQuery = !isAuthorQuery && !isGenreQuery

                            val scoredItems = page.novels.mapNotNull { sNovel ->
                                if (sNovel.url == novel.url) return@mapNotNull null
                                if (SuggestionTitleResolver.isFranchiseDuplicate(sNovel.title, novel.title)) return@mapNotNull null
                                if (synchronized(uniqueResults) { uniqueResults.containsKey(sNovel.url) }) return@mapNotNull null

                                val bestScore = candidatesToScore.maxOfOrNull { candidate ->
                                    SuggestionTitleResolver.scoreMatch(candidate, sNovel.title)
                                } ?: 0

                                val finalScore = when {
                                    bestScore >= 20 -> bestScore
                                    isTitleQuery -> 0
                                    isAuthorQuery -> 40 + minOf(bestScore / 10, 10)
                                    isGenreQuery -> 20
                                    else -> 0
                                }

                                if (finalScore >= 20) {
                                    val itemReason = when {
                                        isAuthorQuery -> SuggestionReason.SEARCH_AUTHOR
                                        isGenreQuery -> SuggestionReason.SEARCH_GENRE
                                        else -> SuggestionReason.SEARCH_TITLE
                                    }
                                    SuggestionItem(
                                        title = sNovel.title,
                                        searchQueries = listOf(sNovel.title),
                                        thumbnailUrl = resolveThumbnail(source, sNovel),
                                        providerName = source.name,
                                        providerUrl = sNovel.url,
                                        providerId = "${source.id}:${sNovel.url}",
                                        mediaType = SuggestionMediaType.NOVEL,
                                        reason = itemReason,
                                    ) to finalScore
                                } else {
                                    null
                                }
                            }

                            val currentProgress = synchronized(uniqueResults) {
                                if (isGenreQuery && genreAdded >= maxGenre) return@launch
                                if (isAuthorQuery && authorAdded >= maxAuthor) return@launch
                                var addedAny = false
                                scoredItems.sortedByDescending { it.second }.forEach { (item, _) ->
                                    if (!uniqueResults.containsKey(item.providerUrl) && uniqueResults.size < boundedMaxResults) {
                                        if ((isGenreQuery && genreAdded >= maxGenre) || (isAuthorQuery && authorAdded >= maxAuthor)) return@forEach
                                        uniqueResults[item.providerUrl] = item
                                        addedAny = true
                                        if (isGenreQuery) genreAdded++
                                        if (isAuthorQuery) authorAdded++
                                    }
                                }
                                if (addedAny) uniqueResults.values.toList() else null
                            }
                            if (currentProgress != null) onProgress?.invoke(currentProgress)
                        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logcat { "[NovelSearchFallbackEngine] Search failed for query '$query': ${e.message}" }
                        }
                    }
                }
            }
        }

        // Final pass: dedupe by cleaned title
        val items = uniqueResults.values.toList()
            .dedupeByCleanTitle(seed)
            .sortedByDescending { SuggestionSourceWeight.finalScore(it.reason, it.bestMatchScoreFor(seed)) }

        SuggestionCache.put(cacheKey, items)
        return if (items.isEmpty()) {
            NovelFallbackOutcome.Empty(NovelFallbackReason.SEARCH_EMPTY)
        } else {
            NovelFallbackOutcome.Success(items)
        }
    }

    private suspend fun resolveThumbnail(
        source: NovelCatalogueSource,
        novel: eu.kanade.tachiyomi.novelsource.model.SNovel,
    ): String? {
        return novel.thumbnail_url?.takeIf { it.isNotBlank() }
            ?: runCatching { source.getNovelDetails(novel.copy()).thumbnail_url?.takeIf { it.isNotBlank() } }
                .getOrNull()
    }
}
