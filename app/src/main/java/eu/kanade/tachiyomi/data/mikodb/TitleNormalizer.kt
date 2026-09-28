package eu.kanade.tachiyomi.data.mikodb

import java.text.Normalizer

/**
 * Canonical title normalization shared with `scripts/mikodb/build-manga-map.mjs`.
 * MUST produce identical output to the JS `normalize()` for every input —
 * parity is enforced by golden vectors in
 * `app/src/test/resources/title_normalizer_golden.json`.
 */
object TitleNormalizer {

    private val NONWORD = Regex("[^\\p{L}\\p{N}\\s]")
    private val SPACES = Regex("\\s+")

    fun normalize(title: String?): String = title
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
        ?.lowercase()
        ?.replace(NONWORD, " ")
        ?.replace(SPACES, " ")
        ?.trim()
        .orEmpty()
}
