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

    private val ROMAN_NUMERAL = Regex("^[IVXLCDM]{2,}$")

    /**
     * Display casing for canonical titles — upstream catalogs store many
     * headline titles ALL CAPS ("BLEACH", "ONE PIECE"). Fires only when
     * the title has cased letters but no lowercase at all, so mixed-case
     * and stylized titles pass through untouched. Words that are pure
     * roman numerals keep their capitals ("PART II" → "Part II").
     */
    fun displayTitle(title: String): String {
        if (title.none { it.isLetter() } || title.any { it.isLowerCase() }) return title
        return title.split(SPACES).joinToString(" ") { word ->
            if (word.matches(ROMAN_NUMERAL)) word else capitalizeWord(word)
        }
    }

    private fun capitalizeWord(word: String): String {
        val sb = StringBuilder(word.lowercase())
        var cap = true
        for (i in sb.indices) {
            val c = sb[i]
            when {
                c.isLetter() && cap -> {
                    sb[i] = c.uppercaseChar()
                    cap = false
                }
                c.isLetterOrDigit() -> cap = false
                else -> cap = true // hyphen/apostrophe boundary: "K-ON" → "K-On"
            }
        }
        return sb.toString()
    }
}
