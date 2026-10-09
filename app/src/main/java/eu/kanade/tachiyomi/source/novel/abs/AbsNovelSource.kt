package eu.kanade.tachiyomi.source.novel.abs

import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.novelsource.novelSourcePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.preference.Preference

/**
 * Built-in Audiobookshelf source — the user's self-hosted ABS server as a
 * first-class catalogue (SD-1..SD-8).
 *
 * Fixed id [ABS_SOURCE_ID] = -1L: extension ids are `generateId` MD5s
 * masked `and Long.MAX_VALUE`, so negatives can never collide (the
 * `CANONICAL_SOURCE_ID = 0L` trick). Seeded into AndroidNovelSourceManager
 * via `builtInSources` so it surfaces in Browse → Sources, pinning, and
 * global search with no awaitSource polling.
 *
 * Items materialize as `abs://item/{itemId}` novels; tracks as
 * `abs://track/{itemId}/{ino}` chapters. Chapters are audio — there is no
 * text — so [getChapterText] throws UnsupportedOperationException (SD-8)
 * and 22-02's downloader guard keeps them out of the text queue.
 *
 * Server URL + API token + library live in the `source_-1`
 * SharedPreferences file; the token key is `__PRIVATE_abs_api_key`
 * (backup-filtered, masked, never logged — see [AbsClient]).
 */
class AbsNovelSource(
    val client: AbsClient = AbsClient(novelSourcePreferences("source_$ABS_SOURCE_ID")),
) : NovelCatalogueSource,
    ConfigurableNovelSource {

    override val id: Long = ABS_SOURCE_ID
    override val name: String = "Audiobookshelf"
    override val lang: String = "all"
    override val supportsLatest: Boolean = true

    /** Libraries fetched for the filter sheet + settings picker; cached so
     *  the synchronous [getFilterList] doesn't re-hit the network. */
    @Volatile
    private var cachedLibraries: List<AbsLibrary> = emptyList()

    // ---- Browse ------------------------------------------------------------

    override suspend fun getPopularNovels(page: Int): NovelsPage =
        getPopularNovels(page, getFilterList())

    /**
     * Page 1 = `/api/me/items-in-progress` first ∪ `items?sort=addedAt&
     * desc=1`, deduped by item id (SD-4); pages ≥2 are items pagination
     * only. A non-default Sort filter swaps the merge for a plain sorted
     * items page.
     */
    override suspend fun getPopularNovels(page: Int, filters: NovelFilterList): NovelsPage {
        val libraryId = libraryFor(filters)
        val (sort, desc) = sortParams(filters, defaultSort = "addedAt", defaultDesc = true)

        if (page != 1 || sort != "addedAt" || !desc) {
            val resp = client.items(libraryId, page = page - 1, sort = sort, desc = desc, filter = null)
            return resp.toNovelsPage()
        }

        val inProgress = runCatching { client.itemsInProgress() }.getOrDefault(emptyList())
        val resp = client.items(libraryId, page = 0, sort = sort, desc = desc, filter = null)
        val seen = mutableSetOf<String>()
        val novels = (inProgress + resp.results)
            .filter { seen.add(it.id) }
            .map(::itemToSNovel)
        return NovelsPage(novels, hasNextPage = resp.hasNextPage())
    }

    override suspend fun getLatestUpdates(page: Int): NovelsPage =
        getLatestUpdates(page, getFilterList())

    override suspend fun getLatestUpdates(page: Int, filters: NovelFilterList): NovelsPage {
        val resp = client.items(
            libraryFor(filters),
            page = page - 1,
            sort = "addedAt",
            desc = true,
            filter = null,
        )
        return resp.toNovelsPage()
    }

    /** `items?filter={query}` — title/author text search (SD-4). */
    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        if (query.isBlank()) return getPopularNovels(page, filters)
        val (sort, desc) = sortParams(filters, defaultSort = "media.metadata.title", defaultDesc = false)
        val resp = client.items(
            libraryFor(filters),
            page = page - 1,
            sort = sort,
            desc = desc,
            filter = query,
        )
        return resp.toNovelsPage()
    }

    override fun getFilterList(): NovelFilterList {
        val libs = cachedLibraries.ifEmpty {
            // Mirror of NovelJsSource's blocking filter load — bounded so a
            // dead server can't stall the sheet.
            runBlocking {
                runCatching {
                    withTimeoutOrNull(FILTER_LIBS_TIMEOUT_MS) { client.libraries() }
                }.getOrNull()?.orEmpty().orEmpty()
                    .also { if (it.isNotEmpty()) cachedLibraries = it }
            }
        }
        return NovelFilterList(
            buildList {
                if (libs.isEmpty()) {
                    add(NovelFilter.Header("Configure the server in ⚙ source settings"))
                } else {
                    add(LibraryFilter(libs.map { it.name }.toTypedArray()))
                }
                add(AbsSort())
            },
        )
    }

    // ---- Details / chapters --------------------------------------------------

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        val itemId = AbsUrls.itemIdFrom(novel.url) ?: return novel
        val item = client.item(itemId)
        val meta = item.media?.metadata
        return novel.copy().apply {
            meta?.title?.takeIf { it.isNotBlank() }?.let { title = it }
            author = meta?.authorName
            artist = meta?.narratorName
            description = meta?.description
            genre = meta?.genres?.joinToString(", ")?.takeIf { it.isNotBlank() }
            thumbnail_url = client.baseUrl().takeIf { it.isNotBlank() }
                ?.let { AbsUrls.coverUrl(it, item.id) }
            status = SNovel.COMPLETED
            initialized = true
        }
    }

    /**
     * `media.tracks[]` → one chapter per audio file (SD-5): the `ino` rides
     * the `abs://track/` url so 22-02's stream resolver can rebuild
     * `/api/items/{id}/file/{ino}` without a second fetch. Narrator rides
     * `scanlator` — it's already part of the audio filename scheme.
     */
    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        val itemId = AbsUrls.itemIdFrom(novel.url) ?: return emptyList()
        val item = client.item(itemId)
        val narrator = item.media?.metadata?.narratorName
        return item.media?.tracks.orEmpty().map { track ->
            SNovelChapter.create().apply {
                url = AbsUrls.trackUrl(item.id, track.ino)
                name = track.title?.takeIf { it.isNotBlank() }
                    ?: track.filename?.takeIf { it.isNotBlank() }
                    ?: "Track ${track.index}"
                chapter_number = track.index.toFloat()
                date_upload = item.addedAt
                scanlator = narrator
            }
        }
    }

    /**
     * ABS chapters are audio tracks — no text. Defensive throw: unreachable
     * once 22-02's downloader guard lands, and a throw beats a silent ""
     * that would store a blank .txt (SD-8).
     */
    override suspend fun getChapterText(chapter: SNovelChapter): String {
        throw UnsupportedOperationException("ABS chapters are audio tracks — no text")
    }

    // ---- Source settings (ConfigurableNovelSource) ---------------------------

    /**
     * Server URL + masked API token + async library picker + reachability
     * status. All writes land in the `source_-1` file via
     * SharedPreferencesDataStore; the token rides the `__PRIVATE_` key so
     * it is masked here, filtered from backups, and never logged (SD-2).
     */
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        EditTextPreference(context).apply {
            key = AbsClient.PREF_BASE_URL
            title = "Server URL"
            summary = client.baseUrl().takeIf { it.isNotBlank() } ?: "https://abs.example:13378"
            setOnPreferenceChangeListener { _, newValue ->
                summary = newValue.toString().takeIf { it.isNotBlank() } ?: "https://abs.example:13378"
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(context).apply {
            key = Preference.privateKey(AbsClient.PREF_API_KEY)
            title = "API token"
            summary = if (client.token().isNotBlank()) "••••••" else "Not set"
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = if (newValue.toString().isBlank()) "Not set" else "••••••"
                true
            }
        }.also(screen::addPreference)

        val libraryPref = ListPreference(context).apply {
            key = AbsClient.PREF_LIBRARY_ID
            title = "Library"
            entries = arrayOf("Default")
            entryValues = arrayOf("")
            summary = "Libraries load once the server is reachable"
        }.also(screen::addPreference)

        val statusPref = androidx.preference.Preference(context).apply {
            key = "abs_status"
            title = "Status"
            summary = if (client.isConfigured()) "Checking…" else "Not configured"
            isSelectable = false
        }.also(screen::addPreference)

        if (!client.isConfigured()) return

        // The screen stays open while libraries + the signed-in user fetch —
        // failures degrade to summaries, never a crash (SD-7 propagation).
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            runCatching { client.libraries() }
                .onSuccess { libs ->
                    cachedLibraries = libs
                    if (libs.isNotEmpty()) {
                        libraryPref.entries = libs.map { it.name }.toTypedArray()
                        libraryPref.entryValues = libs.map { it.id }.toTypedArray()
                        libraryPref.summary = libs.firstOrNull { it.id == client.libraryId() }?.name
                            ?: "Choose a library"
                    } else {
                        libraryPref.summary = "No book libraries on this server"
                    }
                }
                .onFailure { e ->
                    libraryPref.entries = arrayOf("Default")
                    libraryPref.entryValues = arrayOf("")
                    libraryPref.summary = "Couldn't load libraries: ${e.message}"
                }
            runCatching { client.me() }
                .onSuccess { statusPref.summary = "Signed in as ${it.username}" }
                .onFailure { statusPref.summary = "Unreachable: ${it.message}" }
        }
    }

    // ---- mapping -------------------------------------------------------------

    private fun itemToSNovel(item: AbsItem): SNovel {
        val meta = item.media?.metadata
        return SNovel.create().apply {
            url = AbsUrls.itemUrl(item.id)
            title = meta?.title?.takeIf { it.isNotBlank() }
                ?: item.path.substringAfterLast('/').ifBlank { item.id }
            author = meta?.authorName
            artist = meta?.narratorName
            description = meta?.description
            genre = meta?.genres?.joinToString(", ")?.takeIf { it.isNotBlank() }
            thumbnail_url = client.baseUrl().takeIf { it.isNotBlank() }
                ?.let { AbsUrls.coverUrl(it, item.id) }
        }
    }

    private fun AbsItemsResponse.toNovelsPage(): NovelsPage =
        NovelsPage(results.map(::itemToSNovel), hasNextPage = hasNextPage())

    private fun AbsItemsResponse.hasNextPage(): Boolean =
        limit > 0 && total > (page + 1) * limit

    private fun requireConfigured() {
        check(client.isConfigured()) {
            "Audiobookshelf source is not configured — set the server URL and API token"
        }
    }

    /** The settings-screen library is authoritative; the browse filter's
     *  Library select overrides it per query when the user picked one. */
    private fun libraryFor(filters: NovelFilterList): String {
        requireConfigured()
        val select = filters.filterIsInstance<LibraryFilter>().firstOrNull()
        if (select != null) {
            cachedLibraries.getOrNull(select.state)?.let { return it.id }
        }
        return client.libraryId()
            ?: throw IllegalStateException("No Audiobookshelf library selected — pick one in source settings")
    }

    private fun sortParams(
        filters: NovelFilterList,
        defaultSort: String,
        defaultDesc: Boolean,
    ): Pair<String, Boolean> {
        val selection = filters.filterIsInstance<NovelFilter.Sort>().firstOrNull()?.state
            ?: return defaultSort to defaultDesc
        return when (selection.index) {
            1 -> "media.metadata.title" to !selection.ascending
            2 -> "addedAt" to !selection.ascending
            3 -> "media.metadata.authorName" to !selection.ascending
            else -> defaultSort to defaultDesc
        }
    }

    private class LibraryFilter(values: Array<String>) :
        NovelFilter.Select<String>("Library", values)

    private class AbsSort : NovelFilter.Sort(
        "Sort",
        arrayOf("Relevance", "Title", "Recently added", "Author"),
    )

    companion object {
        /** Reserved built-in id — extension `generateId` MD5s are masked
         *  `and Long.MAX_VALUE`, so negatives can never collide. `-2L` /
         *  `-3L` are reserved for LibriVox / Internet Archive (22-03). */
        const val ABS_SOURCE_ID = -1L

        private const val FILTER_LIBS_TIMEOUT_MS = 5_000L
    }
}
