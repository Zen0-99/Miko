package tachiyomi.domain.extension.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelLangNormalizerTest {

    @Test
    fun `null and blank normalize to empty`() {
        normalizeNovelLang(null) shouldBe ""
        normalizeNovelLang("  ") shouldBe ""
    }

    @Test
    fun `multi and all normalize to all`() {
        normalizeNovelLang("multi") shouldBe "all"
        normalizeNovelLang("MULTI") shouldBe "all"
        normalizeNovelLang("all") shouldBe "all"
    }

    @Test
    fun `bcp47 tags pass through lowercased with hyphen`() {
        normalizeNovelLang("en") shouldBe "en"
        normalizeNovelLang("PT_BR") shouldBe "pt-br"
        normalizeNovelLang("zh-Hant") shouldBe "zh-hant"
    }

    @Test
    fun `bidi marks are stripped`() {
        normalizeNovelLang("en") shouldBe "en"
    }

    @Test
    fun `display names resolve via platform locale`() {
        normalizeNovelLang("English") shouldBe "en"
        normalizeNovelLang("日本語") shouldBe "ja"
    }

    @Test
    fun `unrecognized input passes through cleaned`() {
        normalizeNovelLang("English (Fan)") shouldBe "English (Fan)"
    }

    @Test
    fun `first valid candidate in comma list wins`() {
        normalizeNovelLang("!!!, en, fr") shouldBe "en"
    }
}
