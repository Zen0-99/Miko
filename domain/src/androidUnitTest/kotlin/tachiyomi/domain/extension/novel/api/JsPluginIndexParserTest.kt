package tachiyomi.domain.extension.novel.api

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class JsPluginIndexParserTest {

    private val parser = JsPluginIndexParser(Json { ignoreUnknownKeys = true })
    private val repo = "https://raw.githubusercontent.com/example/mikoDB/main"

    private fun entry(extra: String = ""): String = """
        {
            "id": "example",
            "name": "Example Source",
            "site": "https://example.com",
            "lang": "en",
            "url": "plugins/example.js",
            "iconUrl": "icons/example.png",
            "version": "1.2.3"
            ${if (extra.isNotEmpty()) ",$extra" else ""}
        }
    """.trimIndent()

    private fun parse(vararg entries: String) =
        parser.parse("[${entries.joinToString(",")}]", repo)

    @Test
    fun `parses a full LNReader-style entry`() {
        val plugins = parse(entry())

        plugins shouldHaveSize 1
        with(plugins.single()) {
            id shouldBe "example"
            name shouldBe "Example Source"
            site shouldBe "https://example.com"
            lang shouldBe "en"
            url shouldBe "$repo/plugins/example.js"
            iconUrl shouldBe "$repo/icons/example.png"
            versionCode shouldBe 1_002_003
            versionName shouldBe "1.2.3"
            repoUrl shouldBe repo
            isNsfw shouldBe false
            kind shouldBe "novel"
            isKotlinExtension shouldBe false
        }
    }

    @Test
    fun `kind defaults to novel when absent`() {
        parse(entry()).single().kind shouldBe "novel"
    }

    @Test
    fun `kind normalizes aliases`() {
        val kinds = listOf(
            "book" to "novel",
            "books" to "novel",
            "novel" to "novel",
            "show" to "anime",
            "shows" to "anime",
            "anime" to "anime",
            "manga" to "manga",
            "MANGA" to "manga",
            "garbage" to "novel",
        )
        kinds.forEach { (raw, expected) ->
            parse(entry(""""kind": "$raw"""")).single().kind shouldBe expected
        }
    }

    @Test
    fun `relative refs resolve against repo root`() {
        val plugins = parse(
            """
            {
                "id": "a", "name": "A", "site": "https://a.com",
                "url": "./dist/a.js",
                "iconUrl": "/icons/a.png",
                "customJS": "https://cdn.example.com/custom.js",
                "version": 3
            }
            """.trimIndent(),
        )
        with(plugins.single()) {
            url shouldBe "$repo/dist/a.js"
            iconUrl shouldBe "https://raw.githubusercontent.com/icons/a.png"
            customJs shouldBe "https://cdn.example.com/custom.js"
        }
    }

    @Test
    fun `refs resolve against directory when repo url ends in json`() {
        val plugins = parser.parse(
            """
            [{"id": "a", "name": "A", "site": "https://a.com", "url": "a.js", "version": 1}]
            """.trimIndent(),
            "$repo/plugins.min.json",
        )
        plugins.single().url shouldBe "$repo/a.js"
    }

    @Test
    fun `dot segments normalize in resolved urls`() {
        val plugins = parse(
            """
            {"id": "a", "name": "A", "site": "https://a.com", "url": "../other/x.js", "version": 1}
            """.trimIndent(),
        )
        plugins.single().url shouldBe
            "https://raw.githubusercontent.com/example/mikoDB/other/x.js"
    }

    @Test
    fun `kotlin apk entries are flagged not dropped`() {
        val plugins = parse(
            """
            {
                "isNovel": true,
                "pkg": "com.example.ext",
                "apk": "ext.apk",
                "name": "Tsundoku: Example",
                "lang": "en",
                "code": 42,
                "nsfw": 1
            }
            """.trimIndent(),
        )
        plugins shouldHaveSize 1
        with(plugins.single()) {
            isKotlinExtension shouldBe true
            pkgName shouldBe "com.example.ext"
            url shouldBe "$repo/apk/ext.apk"
            iconUrl shouldBe "$repo/icon/com.example.ext.png"
            name shouldBe "Example"
            versionCode shouldBe 42
            isNsfw shouldBe true
            kind shouldBe "novel"
        }
    }

    @Test
    fun `entries missing required fields are skipped`() {
        val plugins = parse(
            """{"name": "no id"}""",
            """{"id": "no-url", "name": "x", "site": "https://x.com"}""",
            entry(),
        )
        plugins shouldHaveSize 1
        plugins.single().id shouldBe "example"
    }

    @Test
    fun `version parses int string and dotted forms`() {
        listOf(
            """"version": 7""" to 7,
            """"version": "12"""" to 12,
            """"version": "1.2.3"""" to 1_002_003,
            """"version": "2.0-beta"""" to 2_000_000,
        ).forEach { (field, expected) ->
            parse(
                """{"id": "a", "name": "A", "site": "s", "url": "u", $field}""",
            ).single().versionCode shouldBe expected
        }
    }

    @Test
    fun `nsfw accepts bool int and string forms`() {
        listOf(
            """"nsfw": true""" to true,
            """"nsfw": 1""" to true,
            """"nsfw": "1"""" to true,
            """"nsfw": "true"""" to true,
            """"nsfw": false""" to false,
            """"nsfw": 0""" to false,
        ).forEach { (field, expected) ->
            parse(
                """{"id": "a", "name": "A", "site": "s", "url": "u", $field}""",
            ).single().isNsfw shouldBe expected
        }
    }

    @Test
    fun `lang normalizes`() {
        parse(entry(""""lang": "multi"""")).single().lang shouldBe "all"
        parse(entry(""""lang": "pt_br"""")).single().lang shouldBe "pt-br"
    }
}
