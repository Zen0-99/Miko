package eu.kanade.tachiyomi.data.track.novelupdates

import android.graphics.Color
import eu.kanade.domain.track.novel.model.toNovelTrack
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.NovelTracker
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import logcat.LogPriority
import okhttp3.FormBody
import okhttp3.Headers
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.domain.track.novel.model.NovelTrack as DomainNovelTrack
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.track.manga.model.MangaTrack as DomainTrack

class NovelUpdates(id: Long) : BaseTracker(id, "NovelUpdates"), NovelTracker {

    override val novelTrackerId: Long = id

    private val insertTrack: InsertNovelTrack by injectLazy()
    private val baseUrl = "https://www.novelupdates.com"

    override fun getLogo(): Int = R.drawable.ic_tracker_novelupdates

    override fun getLogoColor(): Int = Color.rgb(0, 121, 107)

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
    }

    override fun getStatusListManga() = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)

    override fun getStatusForManga(status: Long): StringResource? {
        return when (status) {
            READING -> MR.strings.reading
            COMPLETED -> MR.strings.completed
            ON_HOLD -> MR.strings.on_hold
            DROPPED -> MR.strings.dropped
            PLAN_TO_READ -> MR.strings.plan_to_read
            else -> null
        }
    }

    override fun getReadingStatus() = READING

    override fun getRereadingStatus() = READING

    override fun getCompletionStatus() = COMPLETED

    override fun getScoreList(): ImmutableList<String> = persistentListOf(
        "10", "9", "8", "7", "6", "5", "4", "3", "2", "1", "",
    )

    override fun indexToScore(index: Int): Double {
        return if (index == 10) 0.0 else (10 - index).toDouble()
    }

    override fun get10PointScore(track: DomainTrack): Double = track.score

    override fun displayScore(track: DomainTrack): String {
        return if (track.score == 0.0) "-" else track.score.toInt().toString()
    }

    private fun getAuthHeaders(): Headers {
        val cookies = getPassword()
        return Headers.Builder()
            .add("Cookie", cookies)
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
            .add("Referer", "$baseUrl/")
            .build()
    }

    private suspend fun getNovelId(seriesUrl: String): String? {
        return try {
            val response = client.newCall(GET(seriesUrl, getAuthHeaders())).awaitSuccess()
            val document = response.asJsoup()

            val shortlink = document.select("link[rel=shortlink]").attr("href")
            Regex("p=(\\d+)").find(shortlink)?.groupValues?.get(1)?.let { return it }

            val activityLink = document.select("a[href*=activity-stats]").attr("href")
            Regex("seriesid=(\\d+)").find(activityLink)?.groupValues?.get(1)?.let { return it }

            val postId = document.select("input#mypostid").attr("value")
            if (postId.isNotEmpty()) return postId

            null
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get novel ID from $seriesUrl" }
            null
        }
    }

    private suspend fun getReadingListStatus(novelId: String): Long? {
        return try {
            val response = client.newCall(GET("$baseUrl/series/?p=$novelId", getAuthHeaders())).awaitSuccess()
            val document = response.asJsoup()

            val sticon = document.select("div.sticon")
            if (sticon.select("img[src*=addme.png]").isNotEmpty()) {
                return null
            }

            val listLink = sticon.select("span.sttitle a").attr("href")
            val listId = Regex("list=(\\d+)").find(listLink)?.groupValues?.get(1)?.toLongOrNull() ?: return null
            when (listId) {
                0L -> READING
                1L -> COMPLETED
                2L -> PLAN_TO_READ
                3L -> ON_HOLD
                4L, 5L -> DROPPED
                else -> READING
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get reading list status" }
            null
        }
    }

    private suspend fun getNotesProgress(novelId: String): Int {
        return try {
            val formBody = FormBody.Builder()
                .add("action", "wi_notestagsfic")
                .add("strSID", novelId)
                .build()

            val request = POST("$baseUrl/wp-admin/admin-ajax.php", getAuthHeaders(), formBody)
            val response = client.newCall(request).awaitSuccess()
            val cleanedText = response.body.string().trim().replace(Regex("""\}\s*0+$"""), "}")

            val notes = Regex("\"notes\"\\s*:\\s*\"([^\"]+)\"").find(cleanedText)
                ?.groupValues
                ?.get(1)
                ?: return 0
            Regex("total\\s+chapters\\s+read:\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(notes)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: 0
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get notes progress" }
            0
        }
    }

    private suspend fun updateNotesProgress(novelId: String, chapters: Int) {
        try {
            val getNotesBody = FormBody.Builder()
                .add("action", "wi_notestagsfic")
                .add("strSID", novelId)
                .build()

            val getRequest = POST("$baseUrl/wp-admin/admin-ajax.php", getAuthHeaders(), getNotesBody)
            val getResponse = client.newCall(getRequest).awaitSuccess()
            val cleanedText = getResponse.body.string().trim().replace(Regex("""\}\s*0+$"""), "}")

            val notes = Regex("\"notes\"\\s*:\\s*\"([^\"]+)\"").find(cleanedText)
                ?.groupValues
                ?.get(1)
                ?: ""
            val tags = Regex("\"tags\"\\s*:\\s*\"([^\"]+)\"").find(cleanedText)
                ?.groupValues
                ?.get(1)
                ?: ""

            val chapterPattern = Regex("total\\s+chapters\\s+read:\\s*\\d+", RegexOption.IGNORE_CASE)
            val replacement = "total chapters read: $chapters"
            val updatedNotes = if (chapterPattern.containsMatchIn(notes)) {
                notes.replace(chapterPattern, replacement)
            } else if (notes.isEmpty()) {
                replacement
            } else {
                "$notes<br/>$replacement"
            }

            val updateBody = FormBody.Builder()
                .add("action", "wi_rlnotes")
                .add("strSID", novelId)
                .add("strNotes", updatedNotes)
                .add("strTags", tags)
                .build()

            client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", getAuthHeaders(), updateBody)).awaitSuccess()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update notes progress" }
        }
    }

    private suspend fun moveToList(novelId: String, listId: Long) {
        try {
            client.newCall(
                GET("$baseUrl/updatelist.php?sid=$novelId&lid=$listId&act=move", getAuthHeaders()),
            ).awaitSuccess()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to move to list" }
        }
    }

    private fun statusToListId(status: Long): Long = when (status) {
        READING -> 0L
        COMPLETED -> 1L
        PLAN_TO_READ -> 2L
        ON_HOLD -> 3L
        DROPPED -> 4L
        else -> 0L
    }

    override suspend fun update(track: MangaTrack, didReadChapter: Boolean): MangaTrack {
        val novelId = track.remote_id.toString()

        moveToList(novelId, statusToListId(track.status))

        if (track.last_chapter_read > 0) {
            updateNotesProgress(novelId, track.last_chapter_read.toInt())
        }

        return track
    }

    override suspend fun bind(track: MangaTrack, hasReadChapters: Boolean): MangaTrack {
        val slug = Regex("series/([^/]+)/?").find(track.tracking_url)?.groupValues?.get(1)
        if (slug != null) {
            val novelId = getNovelId("$baseUrl/series/$slug/")
            if (novelId != null) {
                track.remote_id = novelId.toLongOrNull() ?: track.remote_id
            }
        }

        val currentStatus = getReadingListStatus(track.remote_id.toString())
        track.status = currentStatus ?: if (hasReadChapters) READING else PLAN_TO_READ

        val progress = getNotesProgress(track.remote_id.toString())
        if (progress > 0) {
            track.last_chapter_read = progress.toDouble()
        }

        return track
    }

    override suspend fun searchManga(query: String): List<MangaTrackSearch> {
        val encodedQuery = query.replace(" ", "+")
        val url = "$baseUrl/series-finder/?sf=1&sh=$encodedQuery&sort=sdate&order=desc"
        return try {
            val response = client.newCall(GET(url, getAuthHeaders())).awaitSuccess()
            val document = response.asJsoup()
            document.select("div.search_main_box_nu").map { element ->
                val track = MangaTrackSearch.create(id)
                val titleElement = element.select("div.search_title a").first()
                    ?: element.select(".search_title a").first()
                track.title = titleElement?.text()?.trim().orEmpty()
                track.tracking_url = titleElement?.attr("href").orEmpty()

                val sidSpan = element.select("span[id^=sid]").first()
                val sidId = sidSpan?.attr("id")
                val numericId = sidId?.let { Regex("sid(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull() }

                val slug = Regex("series/([^/]+)/?").find(track.tracking_url)?.groupValues?.get(1).orEmpty()
                track.remote_id = numericId ?: slug.hashCode().toLong().let { if (it < 0) -it else it }

                val coverImg = element.select("div.search_img_nu img, .search_img_nu img").first()
                track.cover_url = coverImg?.attr("src")?.let { src ->
                    if (src.startsWith("http")) src else "$baseUrl$src"
                }.orEmpty()

                val descContainer = element.select("div.search_body_nu").first()
                val hiddenText = descContainer?.select(".testhide")?.text().orEmpty()
                val visibleText = descContainer?.text()?.replace(hiddenText, "")?.trim().orEmpty()
                track.summary = (visibleText + " " + hiddenText)
                    .replace("... more>>", "")
                    .replace("<<less", "")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                val genreText = element.select("div.search_genre, .search_genre").text()
                track.publishing_status = when {
                    genreText.contains("Completed", ignoreCase = true) -> "Completed"
                    genreText.contains("Ongoing", ignoreCase = true) -> "Ongoing"
                    else -> ""
                }
                track
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "NovelUpdates search failed" }
            emptyList()
        }
    }

    override suspend fun refresh(track: MangaTrack): MangaTrack {
        val novelId = track.remote_id.toString()

        getReadingListStatus(novelId)?.let { track.status = it }

        val progress = getNotesProgress(novelId)
        if (progress > 0) {
            track.last_chapter_read = progress.toDouble()
        }

        return track
    }

    override suspend fun login(username: String, password: String) {
        saveCredentials(username, password)
    }

    override suspend fun setRemoteMangaStatus(track: MangaTrack, status: Long) {
        track.status = status
        if (track.status == getCompletionStatus() && track.total_chapters != 0L) {
            track.last_chapter_read = track.total_chapters.toDouble()
        }
        updateRemoteNovel(track)
    }

    override suspend fun setRemoteLastChapterRead(track: MangaTrack, chapterNumber: Int) {
        if (track.last_chapter_read == 0.0 &&
            track.last_chapter_read < chapterNumber &&
            track.status != getRereadingStatus()
        ) {
            track.status = getReadingStatus()
        }
        track.last_chapter_read = chapterNumber.toDouble()
        if (track.total_chapters != 0L &&
            track.last_chapter_read.toLong() == track.total_chapters
        ) {
            track.status = getCompletionStatus()
            track.finished_reading_date = System.currentTimeMillis()
        }
        updateRemoteNovel(track)
    }

    override suspend fun setRemoteScore(track: MangaTrack, scoreString: String) {
        track.score = indexToScore(getScoreList().indexOf(scoreString))
        updateRemoteNovel(track)
    }

    override suspend fun setRemoteStartDate(track: MangaTrack, epochMillis: Long) {
        track.started_reading_date = epochMillis
        updateRemoteNovel(track)
    }

    override suspend fun setRemoteFinishDate(track: MangaTrack, epochMillis: Long) {
        track.finished_reading_date = epochMillis
        updateRemoteNovel(track)
    }

    override suspend fun setRemotePrivate(track: MangaTrack, private: Boolean) {
        track.private = private
        updateRemoteNovel(track)
    }

    private suspend fun updateRemoteNovel(track: MangaTrack): Unit = withIOContext {
        try {
            update(track)
            track.toNovelTrack(idRequired = false)?.let {
                insertTrack.await(it)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update remote novel track data id=${track.id}" }
        }
    }
}
