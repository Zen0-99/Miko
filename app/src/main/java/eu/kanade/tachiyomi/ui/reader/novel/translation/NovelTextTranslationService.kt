package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Lightweight text translation service using Google Translate's free
 * web endpoint (translate.googleapis.com). No API key required.
 *
 * Used for the "Selected text translation" feature in the novel reader.
 * When the user selects text and the preference is enabled, a "Translate"
 * action appears in the selection popup. Tapping it translates the selected
 * text to the target language and shows the result in a dialog.
 */
object NovelTextTranslationService {

    private val client: OkHttpClient by lazy {
        Injekt.get<NetworkHelper>().client
    }

    /**
     * Translate [text] to [targetLang] (ISO 639-1 code, e.g. "en", "es", "fr").
     * Returns the translated text, or null on failure.
     */
    suspend fun translate(text: String, targetLang: String = "en"): String? {
        if (text.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&dt=t&sl=auto&tl=$targetLang&q=$encodedText"

                val response = client.newCall(GET(url)).execute()
                if (!response.isSuccessful) {
                    logcat { "[Translation] API returned ${response.code}" }
                    return@withContext null
                }

                val body = response.body.string()
                // Response format: [[["translated text","original text",null,null],...],...]
                // Parse the JSON array manually (avoid pulling in a JSON parser dependency)
                val translated = parseTranslateResponse(body)
                if (translated.isNullOrBlank()) {
                    logcat { "[Translation] Failed to parse response" }
                    return@withContext null
                }
                translated
            } catch (e: Exception) {
                logcat { "[Translation] Error: ${e.message}" }
                null
            }
        }
    }

    /**
     * Parse the Google Translate response format.
     * The response is a JSON array: [[["translated","original",null,null],...], ["src",...], ...]
     * We extract all translated segments and concatenate them.
     */
    private fun parseTranslateResponse(body: String): String? {
        return try {
            // Use Jsoup to parse the JSON-ish response
            // Actually, the response is pure JSON. Let's parse it simply.
            // Find all strings that are the first element of inner arrays.
            val result = StringBuilder()
            var i = 0
            while (i < body.length) {
                // Look for pattern: ["translated text","original text"
                val start = body.indexOf("[\"", i)
                if (start == -1) break
                val textStart = start + 2
                val textEnd = body.indexOf("\"", textStart)
                if (textEnd == -1) break
                val segment = body.substring(textStart, textEnd)
                // Skip the first outer array bracket
                if (segment.isNotEmpty() && segment != "en" && segment != "auto") {
                    result.append(segment)
                }
                i = textEnd + 1
            }
            if (result.isEmpty()) null else result.toString()
        } catch (e: Exception) {
            null
        }
    }
}
