package eu.kanade.tachiyomi.data.suggestions.sources

import eu.kanade.tachiyomi.data.suggestions.SuggestionCache
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver
import eu.kanade.tachiyomi.data.suggestions.sources.dto.JikanRecommendationResponse
import eu.kanade.tachiyomi.data.suggestions.sources.dto.JikanSearchResponse
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

/**
 * MyAnimeList / Jikan recommendation source.
 *
 * Uses the public Jikan v4 API (unofficial MAL API) to search for the seed
 * title, then fetches the recommendations for the best-matching anime.
 *
 * Only enabled for ANIME media type.
 */
class MyAnimeListRecommendationSource(
    override val mediaType: SuggestionMediaType,
) : RecommendationPagingSource() {

    override val name: String = "MyAnimeList"

    private val client by lazy { Injekt.get<NetworkHelper>().client }
    private val json by lazy { Injekt.get<Json>() }

    override suspend fun fetchSuggestions(seed: SuggestionSeed): List<SuggestionItem> {
        if (mediaType != SuggestionMediaType.ANIME) return emptyList()

        val cacheKey = SuggestionCache.makeKey(name, seed.primaryTitle, mediaType.name, seed.candidateTitles)
        SuggestionCache.get(cacheKey)?.let {
            logcat { "[MAL] CACHE HIT for '${seed.primaryTitle}' (${seed.candidateTitles.size} candidates)" }
            return it
        }
        logcat { "[MAL] START fetching for '${seed.primaryTitle}', candidates=${seed.candidateTitles}" }

        val suggestions = try {
            val allSearchEntries = mutableListOf<eu.kanade.tachiyomi.data.suggestions.sources.dto.JikanSearchEntry>()

            for ((index, candidate) in seed.candidateTitles.take(3).withIndex()) {
                if (index > 0) delay(350)
                try {
                    val searchUrl = "https://api.jikan.moe/v4/anime?q=${URLEncoder.encode(candidate, "UTF-8")}&limit=5"
                    logcat { "MAL suggestions: searching via $searchUrl" }

                    val response = client.newCall(GET(searchUrl)).awaitSuccess()
                    val responseStr = response.body?.string() ?: ""
                    val searchResponse = json.decodeFromString<JikanSearchResponse>(responseStr)

                    allSearchEntries.addAll(searchResponse.data)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat { "MAL search failed for candidate '$candidate': ${e.message}" }
                }
            }

            val entryWithScore = allSearchEntries.distinctBy { it.malId }.map { entry ->
                val score = seed.candidateTitles.maxOfOrNull { candidate ->
                    SuggestionTitleResolver.scoreMatch(entry.title, candidate)
                } ?: 0
                Pair(entry, score)
            }

            val bestMatch = entryWithScore
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?.first
                ?: return emptyList()

            logcat {
                "[MAL] Base anime selected: '${bestMatch.title}' (ID=${bestMatch.malId}) for '${seed.primaryTitle}'"
            }

            // Wait to respect rate limiting
            delay(350)

            val recUrl = "https://api.jikan.moe/v4/anime/${bestMatch.malId}/recommendations"
            logcat { "MAL suggestions: fetching recommendations from $recUrl" }

            val recResponse = client.newCall(GET(recUrl)).awaitSuccess()
            val recResponseStr = recResponse.body?.string() ?: ""
            val recData = json.decodeFromString<JikanRecommendationResponse>(recResponseStr)

            recData.data
                .sortedByDescending { it.votes }
                .map { item ->
                    SuggestionItem(
                        title = item.entry.title,
                        searchQueries = listOf(item.entry.title),
                        thumbnailUrl = item.entry.images?.jpg?.imageUrl,
                        providerName = name,
                        providerUrl = item.entry.url,
                        providerId = item.entry.malId.toString(),
                        mediaType = mediaType,
                        reason = SuggestionReason.EXTERNAL_MAL,
                    )
                }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[MAL] ERROR for '${seed.primaryTitle}': ${e.message}" }
            emptyList()
        }

        logcat { "[MAL] END for '${seed.primaryTitle}': ${suggestions.size} suggestions" }
        if (suggestions.isNotEmpty()) {
            SuggestionCache.put(cacheKey, suggestions)
        }
        return suggestions
    }
}
