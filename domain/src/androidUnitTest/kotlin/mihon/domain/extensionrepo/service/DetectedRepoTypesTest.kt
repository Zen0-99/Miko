package mihon.domain.extensionrepo.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DetectedRepoTypesTest {

    private fun entry(pkg: String, name: String = pkg) = ExtensionIndexEntry(
        name = name,
        pkgName = pkg,
        versionName = "1",
        versionCode = 1,
        libVersion = 1.0,
        lang = "en",
        isNsfw = false,
        sources = emptyList(),
        apkName = "$pkg.apk",
        iconUrl = "",
        repoUrl = "https://example.com/repo",
    )

    @Test
    fun `anime extension pkgs map to anime`() {
        entry("eu.kanade.tachiyomi.animeextension.all.animeonsen")
            .detectedRepoTypes() shouldBe setOf("anime")
    }

    @Test
    fun `novel extension pkgs map to novel`() {
        entry("yokai.extension.novel.en.royalroad")
            .detectedRepoTypes() shouldBe setOf("novel")
    }

    @Test
    fun `manga extension pkgs map to manga`() {
        entry("eu.kanade.tachiyomi.extension.en.asurascans")
            .detectedRepoTypes() shouldBe setOf("manga")
    }

    @Test
    fun `keiyoushi pkgs map to manga`() {
        entry("eu.kanade.tachiyomi.extension.all.keiyoushi")
            .detectedRepoTypes() shouldBe setOf("manga")
    }

    @Test
    fun `name-only matches classify`() {
        entry("com.example.something", name = "My Novel Reader")
            .detectedRepoTypes() shouldBe setOf("novel")
    }

    @Test
    fun `unmatched pkgs produce no types`() {
        entry("com.example.unrelated").detectedRepoTypes() shouldBe emptySet()
    }

    @Test
    fun `pkg matching multiple carriers returns all`() {
        entry("yokai.extension.novel.anime", name = "anime novels")
            .detectedRepoTypes() shouldBe setOf("novel", "anime")
    }
}
