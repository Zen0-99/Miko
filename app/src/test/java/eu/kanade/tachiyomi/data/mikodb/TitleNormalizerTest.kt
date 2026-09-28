package eu.kanade.tachiyomi.data.mikodb

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Golden-vector parity check: `TitleNormalizer` must produce byte-identical
 * output to `scripts/mikodb/build-manga-map.mjs` `normalize()` — the vectors
 * file is validated against the JS implementation by the build pipeline.
 */
class TitleNormalizerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `normalizer matches golden vectors`() {
        val text = javaClass.classLoader!!
            .getResourceAsStream("title_normalizer_golden.json")!!
            .bufferedReader().readText()
        val vectors = json.decodeFromString<List<List<String>>>(text)
        for (vector in vectors) {
            val (input, expected) = vector
            assertEquals(expected, TitleNormalizer.normalize(input), "normalize(\"$input\")")
        }
    }

    @Test
    fun `normalizer handles null and blank`() {
        assertEquals("", TitleNormalizer.normalize(null))
        assertEquals("", TitleNormalizer.normalize(""))
        assertEquals("", TitleNormalizer.normalize("   "))
    }
}
