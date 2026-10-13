package miko.ios

import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Android's `AniSkipApi`, ported to ktor — same endpoints and payload
 * shape. Skip segments (OP/ED/recap/mixed) keyed by MAL id; the AniList
 * → MAL lookup goes through AniList's GraphQL like Android's.
 * credits: https://github.com/saikou-app/saikou (mirrors Android's note)
 */
class AniSkipApi(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun getResult(malId: Long, episodeNumber: Int, episodeLengthSec: Long): List<TimeStamp>? {
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed" +
                "&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap" +
                "&episodeLength=$episodeLengthSec"
        return try {
            val res = json.decodeFromString<AniSkipResponse>(client.get(url).bodyAsText())
            if (res.found) {
                res.results?.map {
                    TimeStamp(
                        start = it.interval.startTime,
                        end = it.interval.endTime,
                        name = it.skipType.label(),
                        type = it.skipType.toChapterType(),
                    )
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Android's `getMalIdFromAL` — `Media(id:$id){idMal}` GraphQL lookup;
     * 0 on failure/unknown (callers treat 0 as "no id").
     */
    suspend fun getMalIdFromAL(anilistId: Long): Long {
        val query = "query{Media(id:$anilistId){idMal}}"
        return try {
            client.post("https://graphql.anilist.co") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("query", query) }.toString())
            }.bodyAsText()
                .substringAfter("idMal\":")
                .substringBefore("}")
                .toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}

@Serializable
private data class AniSkipResponse(
    val found: Boolean,
    val results: List<Stamp>?,
)

@Serializable
private data class Stamp(
    val interval: AniSkipInterval,
    val skipType: SkipType,
)

@Serializable
private enum class SkipType {
    @SerialName("op")
    OP,

    @SerialName("ed")
    ED,

    @SerialName("recap")
    RECAP,

    @SerialName("mixed-op")
    MIXED_OP, ;

    fun label(): String = when (this) {
        OP -> "Opening"
        ED -> "Ending"
        RECAP -> "Recap"
        MIXED_OP -> "Mixed-op"
    }

    fun toChapterType(): ChapterType = when (this) {
        OP -> ChapterType.Opening
        ED -> ChapterType.Ending
        RECAP -> ChapterType.Recap
        MIXED_OP -> ChapterType.MixedOp
    }
}

@Serializable
private data class AniSkipInterval(
    val startTime: Double,
    val endTime: Double,
)
