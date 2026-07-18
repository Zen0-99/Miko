package eu.kanade.tachiyomi.data.suggestions.sources

import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionReason
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * AniList recommendation source using the public GraphQL API.
 *
 * Searches for the seed title on AniList, then fetches the `recommendations`
 * edges for each matched media. Covers ANIME, MANGA, and NOVEL (mapped to
 * MANGA type on AniList).
 *
 * No authentication required — uses the public AniList API endpoint.
 */
class AniListRecommendationSource(
    override val mediaType: SuggestionMediaType,
) : RecommendationPagingSource() {

    override val name: String = "AniList"

    private val client by lazy { Injekt.get<NetworkHelper>().client }
    private val json by lazy { Injekt.get<Json>() }
    private val aniListType = mediaType.toAniListType()

    private fun isValidRecommendationType(type: String?, format: String?): Boolean {
        val upperType = type?.uppercase()
        val upperFormat = format?.uppercase()
        return when (mediaType) {
            SuggestionMediaType.ANIME -> upperType == "ANIME"
            SuggestionMediaType.MANGA -> upperType == "MANGA" && upperFormat != "NOVEL"
            SuggestionMediaType.NOVEL -> upperType == "MANGA"
        }
    }

    override suspend fun fetchSuggestions(seed: SuggestionSeed): List<SuggestionItem> = coroutineScope {
        val candidatesToFetch = if (mediaType == SuggestionMediaType.NOVEL) {
            listOf(seed.primaryTitle) + seed.candidateTitles.filter { it != seed.primaryTitle }
        } else {
            seed.candidateTitles
        }

        val query = """
            query Recommendations(${'$'}search: String!, ${'$'}type: MediaType!) {
                Page {
                    media(search: ${'$'}search, type: ${'$'}type) {
                        id
                        type
                        format
                        title { romaji english native }
                        recommendations {
                            edges {
                                node {
                                    mediaRecommendation {
                                        id
                                        type
                                        format
                                        siteUrl
                                        title { romaji english native }
                                        coverImage { large }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        suspend fun fetchForType(type: String): List<JsonObject> {
            val jobs = candidatesToFetch.take(3).map { candidate ->
                async {
                    try {
                        val payload = buildJsonObject {
                            put("query", query)
                            put(
                                "variables",
                                buildJsonObject {
                                    put("search", candidate)
                                    put("type", type)
                                },
                            )
                        }
                        val body = payload.toString().toRequestBody(jsonMime)
                        val response = client.newCall(POST("https://graphql.anilist.co/", body = body))
                            .awaitSuccess()
                        val responseStr = response.body?.string() ?: ""
                        val data = json.parseToJsonElement(responseStr).jsonObject

                        data["data"]?.jsonObject
                            ?.get("Page")?.jsonObject
                            ?.get("media")?.jsonArray
                            ?.mapNotNull { it.jsonObject }
                            ?: emptyList()
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat { "AniList query failed for '$candidate' type=$type: ${e.message}" }
                        emptyList()
                    }
                }
            }
            return jobs.awaitAll().flatten()
        }

        var allResults = fetchForType(aniListType).distinctBy { it["id"]?.jsonPrimitive?.contentOrNull }

        // For NOVEL: try ANIME type as fallback
        if (mediaType == SuggestionMediaType.NOVEL && allResults.isEmpty()) {
            val animeResults = fetchForType("ANIME").distinctBy { it["id"]?.jsonPrimitive?.contentOrNull }
            if (animeResults.isNotEmpty()) {
                allResults = animeResults
            }
        }

        if (allResults.isEmpty()) {
            logcat { "[AniList] No base media found for '${seed.primaryTitle}'" }
            return@coroutineScope emptyList()
        }

        matchedBase = true

        val items = mutableListOf<SuggestionItem>()
        val seenIds = mutableSetOf<String>()

        for (media in allResults) {
            val recEdges = media["recommendations"]?.jsonObject
                ?.get("edges")?.jsonArray ?: continue

            for (edge in recEdges) {
                val rec = edge.jsonObject["node"]?.jsonObject
                    ?.get("mediaRecommendation")?.jsonObject ?: continue

                val id = rec["id"]?.jsonPrimitive?.contentOrNull ?: continue
                if (id in seenIds) continue

                val type = rec["type"]?.jsonPrimitive?.contentOrNull
                val format = rec["format"]?.jsonPrimitive?.contentOrNull
                if (!isValidRecommendationType(type, format)) continue

                val title = rec["title"]?.jsonObject?.let { titles ->
                    titles["english"]?.jsonPrimitive?.contentOrNull
                        ?: titles["romaji"]?.jsonPrimitive?.contentOrNull
                        ?: titles["native"]?.jsonPrimitive?.contentOrNull
                } ?: continue

                val siteUrl = rec["siteUrl"]?.jsonPrimitive?.contentOrNull ?: "https://anilist.co/anime/$id"
                val cover = rec["coverImage"]?.jsonObject
                    ?.get("large")?.jsonPrimitive?.contentOrNull

                seenIds.add(id)
                items.add(
                    SuggestionItem(
                        title = title,
                        thumbnailUrl = cover,
                        providerName = "AniList",
                        providerUrl = siteUrl,
                        providerId = "anilist:$id",
                        mediaType = mediaType,
                        reason = SuggestionReason.EXTERNAL_ANILIST,
                    ),
                )
            }
        }

        logcat { "[AniList] Done '${seed.primaryTitle}': ${items.size} recommendations" }
        items
    }
}
