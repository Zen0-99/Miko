package eu.kanade.tachiyomi.novelsource.online

import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * A simple implementation for novel sources from a website using Jsoup, an HTML parser.
 */
@Suppress("unused")
abstract class ParsedNovelHttpSource : NovelHttpSource() {

    // ===== Popular Novels =====

    override fun popularNovelsParse(response: Response): NovelsPage {
        val document = response.asJsoup()

        val novels = document.select(popularNovelsSelector()).map { element ->
            popularNovelsFromElement(element)
        }

        val hasNextPage = popularNovelsNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return NovelsPage(novels, hasNextPage)
    }

    protected abstract fun popularNovelsSelector(): String

    protected abstract fun popularNovelsFromElement(element: Element): SNovel

    protected abstract fun popularNovelsNextPageSelector(): String?

    // ===== Search Novels =====

    override fun searchNovelsParse(response: Response): NovelsPage {
        val document = response.asJsoup()

        val novels = document.select(searchNovelsSelector()).map { element ->
            searchNovelsFromElement(element)
        }

        val hasNextPage = searchNovelsNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return NovelsPage(novels, hasNextPage)
    }

    protected abstract fun searchNovelsSelector(): String

    protected abstract fun searchNovelsFromElement(element: Element): SNovel

    protected abstract fun searchNovelsNextPageSelector(): String?

    // ===== Latest Updates =====

    override fun latestUpdatesParse(response: Response): NovelsPage {
        val document = response.asJsoup()

        val novels = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return NovelsPage(novels, hasNextPage)
    }

    protected abstract fun latestUpdatesSelector(): String

    protected abstract fun latestUpdatesFromElement(element: Element): SNovel

    protected abstract fun latestUpdatesNextPageSelector(): String?

    // ===== Novel Details =====

    override fun novelDetailsParse(response: Response): SNovel {
        return novelDetailsParse(response.asJsoup())
    }

    protected abstract fun novelDetailsParse(document: Document): SNovel

    // ===== Chapter List =====

    override fun chapterListParse(response: Response): List<SNovelChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    protected abstract fun chapterListSelector(): String

    protected abstract fun chapterFromElement(element: Element): SNovelChapter

    // ===== Chapter Text =====

    override fun chapterTextParse(response: Response): String {
        return chapterTextParse(response.asJsoup())
    }

    protected abstract fun chapterTextParse(document: Document): String
}
