package eu.kanade.tachiyomi.novelsource.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/**
 * A simple implementation for novel sources from a website.
 */
@Suppress("unused")
abstract class NovelHttpSource : NovelCatalogueSource {

    /**
     * Network service.
     */
    protected val network: NetworkHelper by injectLazy()

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    abstract val baseUrl: String

    /**
     * Version id used to generate the source id. If the site completely changes and urls are
     * incompatible, you may increase this value and it'll be considered as a new source.
     */
    open val versionId = 1

    /**
     * ID of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string `"${name.lowercase()}/$lang/$versionId"`.
     */
    override val id by lazy { generateId(name, lang, versionId) }

    /**
     * Headers used for requests.
     */
    val headers: Headers by lazy { headersBuilder().build() }

    /**
     * Default network client for doing requests.
     */
    open val client: OkHttpClient
        get() = network.client

    /**
     * Generates a unique ID for the source based on the provided [name], [lang] and
     * [versionId].
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    /**
     * Headers builder for requests. Implementations can override this method for custom headers.
     */
    protected open fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", network.defaultUserAgentProvider())
    }

    /**
     * Visible name of the source.
     */
    override fun toString() = "$name (${lang.uppercase()})"

    // ===== Popular Novels =====

    override suspend fun getPopularNovels(page: Int): NovelsPage {
        return client.newCall(popularNovelsRequest(page))
            .awaitSuccess()
            .let { response ->
                popularNovelsParse(response)
            }
    }

    /**
     * Returns the request for the popular novels given the page.
     */
    protected abstract fun popularNovelsRequest(page: Int): Request

    /**
     * Parses the response from the site and returns a [NovelsPage] object.
     */
    protected abstract fun popularNovelsParse(response: Response): NovelsPage

    // ===== Search Novels =====

    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        return client.newCall(searchNovelsRequest(page, query, filters))
            .awaitSuccess()
            .let { response ->
                searchNovelsParse(response)
            }
    }

    /**
     * Returns the request for the search novels given the page.
     */
    protected abstract fun searchNovelsRequest(page: Int, query: String, filters: NovelFilterList): Request

    /**
     * Parses the response from the site and returns a [NovelsPage] object.
     */
    protected abstract fun searchNovelsParse(response: Response): NovelsPage

    // ===== Latest Updates =====

    override suspend fun getLatestUpdates(page: Int): NovelsPage {
        return client.newCall(latestUpdatesRequest(page))
            .awaitSuccess()
            .let { response ->
                latestUpdatesParse(response)
            }
    }

    /**
     * Returns the request for latest novels given the page.
     */
    protected abstract fun latestUpdatesRequest(page: Int): Request

    /**
     * Parses the response from the site and returns a [NovelsPage] object.
     */
    protected abstract fun latestUpdatesParse(response: Response): NovelsPage

    // ===== Novel Details =====

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        return client.newCall(novelDetailsRequest(novel))
            .awaitSuccess()
            .let { response ->
                novelDetailsParse(response).apply { initialized = true }
            }
    }

    /**
     * Returns the request for the details of a novel. Override only if it's needed to change the
     * url, send different headers or request method like POST.
     */
    open fun novelDetailsRequest(novel: SNovel): Request {
        return GET(baseUrl + novel.url, headers)
    }

    /**
     * Parses the response from the site and returns the details of a novel.
     */
    protected abstract fun novelDetailsParse(response: Response): SNovel

    // ===== Chapter List =====

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        return client.newCall(chapterListRequest(novel))
            .awaitSuccess()
            .let { response ->
                chapterListParse(response)
            }
    }

    /**
     * Returns the request for updating the chapter list. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     */
    protected open fun chapterListRequest(novel: SNovel): Request {
        return GET(baseUrl + novel.url, headers)
    }

    /**
     * Parses the response from the site and returns a list of chapters.
     */
    protected abstract fun chapterListParse(response: Response): List<SNovelChapter>

    // ===== Chapter Text =====

    override suspend fun getChapterText(chapter: SNovelChapter): String {
        return client.newCall(chapterTextRequest(chapter))
            .awaitSuccess()
            .let { response ->
                chapterTextParse(response)
            }
    }

    /**
     * Returns the request for getting the chapter text. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     */
    protected open fun chapterTextRequest(chapter: SNovelChapter): Request {
        return GET(baseUrl + chapter.url, headers)
    }

    /**
     * Parses the response from the site and returns the chapter text content (HTML or plain text).
     */
    protected abstract fun chapterTextParse(response: Response): String

    // ===== Helpers =====

    /**
     * Assigns the url of the chapter without the scheme and domain. It saves some redundancy from
     * database and the urls could still work after a domain change.
     */
    fun SNovelChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    /**
     * Assigns the url of the novel without the scheme and domain. It saves some redundancy from
     * database and the urls could still work after a domain change.
     */
    fun SNovel.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    /**
     * Returns the url of the given string without the scheme and domain.
     */
    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig.replace(" ", "%20"))
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (e: URISyntaxException) {
            orig
        }
    }

    /**
     * Returns the url of the provided novel.
     */
    open fun getNovelUrl(novel: SNovel): String {
        return novelDetailsRequest(novel).url.toString()
    }

    /**
     * Returns the url of the provided chapter.
     */
    open fun getChapterUrl(chapter: SNovelChapter): String {
        return chapterTextRequest(chapter).url.toString()
    }

    /**
     * Called before inserting a new chapter into database. Use it if you need to override chapter
     * fields, like the title or the chapter number.
     */
    open fun prepareNewChapter(chapter: SNovelChapter, novel: SNovel) {}

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = NovelFilterList()
}
