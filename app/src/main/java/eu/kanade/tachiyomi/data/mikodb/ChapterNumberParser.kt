package eu.kanade.tachiyomi.data.mikodb

/**
 * Robust chapter-name parser (Phase 12-04). Kotlin twin of
 * `worker/src/chapterparse.ts` — keep logic identical; both consume
 * `chapter_parser_golden.json`.
 *
 * Handles "Chapter 1", "Ch. 01", "142.5", "Vol. 2 Ch. 10",
 * "Chapter Twenty-Three", "Ch. 30 Part 2", "30b", kind buckets
 * (prologue/extra/afterword/notice), and garbage → [Kind.UNKNOWN]
 * (never force a number onto unparseable names).
 */
object ChapterNumberParser {

    enum class Kind {
        CHAPTER,
        PROLOGUE,
        EPILOGUE,
        AFTERWORD,
        EXTRA,
        NOTICE,
        UNKNOWN,
        ;

        /** Wire value shared with the worker schema. */
        val wire: String
            get() = name.lowercase()
    }

    data class Parsed(
        val number: Double?,
        val volume: Double?,
        val part: Int?,
        val kind: Kind,
    )

    private val ONES = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf(
        "twenty" to 20,
        "thirty" to 30,
        "forty" to 40,
        "fifty" to 50,
        "sixty" to 60,
        "seventy" to 70,
        "eighty" to 80,
        "ninety" to 90,
    )
    private val ROMAN = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100, 'd' to 500, 'm' to 1000)

    fun romanToInt(s: String): Int? {
        if (!s.all { it in ROMAN }) return null
        var total = 0
        for (i in s.indices) {
            val v = ROMAN.getValue(s[i])
            val next = s.getOrNull(i + 1)?.let { ROMAN[it] } ?: 0
            total += if (v < next) -v else v
        }
        return if (total > 0) total else null
    }

    /** Consume leading number-words; returns value + tokens consumed. */
    fun wordToNumber(tokens: List<String>): Pair<Int, Int>? {
        var i = 0
        var total = 0
        var any = false
        if (tokens.getOrNull(i) == "a" && tokens.getOrNull(i + 1) == "hundred") i++ // "a hundred"
        val t0 = tokens.getOrNull(i)
        if (ONES.containsKey(t0) && tokens.getOrNull(i + 1) == "hundred") {
            total = ONES.getValue(t0!!) * 100
            i += 2
            any = true
        } else if (t0 == "hundred") {
            total = 100
            i++
            any = true
        }
        if (any && tokens.getOrNull(i) == "and") i++
        val ti = tokens.getOrNull(i)
        if (TENS.containsKey(ti)) {
            total += TENS.getValue(ti!!)
            i++
            any = true
            val oi = tokens.getOrNull(i)
            if (ONES.containsKey(oi)) {
                total += ONES.getValue(oi!!)
                i++
            }
        } else if (ONES.containsKey(ti)) {
            total += ONES.getValue(ti!!)
            i++
            any = true
        }
        return if (any) total to i else null
    }

    private val RE_KIND = listOf(
        Regex("""\b(prologue|foreword|preface)\b""") to Kind.PROLOGUE,
        Regex("""\bepilogue\b""") to Kind.EPILOGUE,
        Regex("""\bafterword\b""") to Kind.AFTERWORD,
        Regex(
            """\b(omake|extra|extras|bonus|special|oneshot|one[ -]shot|side[ -]stor(?:y|ies)|interlude|intermission|appendix|gaiden|omitted|spin[ -]off)\b""",
        ) to
            Kind.EXTRA,
        Regex("""\b(notice|announcement|news|hiatus)\b""") to Kind.NOTICE,
    )
    private val RE_VOLUME = Regex("""\bvol(?:ume)?s?\.?\s*(\d+(?:\.\d+)?)""")
    private val RE_VOLUME_V = Regex("""\bv(\d+)\b""")
    private val RE_CHAPTER =
        Regex("""\b(?:chapter|chap|ch|episode|ep|no)\.?\s*([0-9]+(?:\.[0-9]+)?[a-z]?|[a-z]+(?:[ -][a-z]+)*)""")
    private val RE_CHAPTER_HASH = Regex("""#\s*(\d+(?:\.\d+)?)""")
    private val RE_COMPACT = Regex("""\bv(\d+)\s*c(\d+(?:\.\d+)?)\b""")
    private val RE_NUMERIC_TOKEN = Regex("""^([0-9]+)(?:\.([0-9]+))?([a-z])?$""")
    private val RE_BARE = Regex("""^\s*\d+(?:\.\d+)?\s*$""")
    private val RE_PART = Regex("""\b(?:part|pt)\.?\s*(?:([0-9]+)|([ivxlcdm]+))\b""")
    private val RE_LOOSE_INT = Regex("""\b([0-9]+)\b""")
    private val RE_BARE_SUFFIX = Regex("""^\s*(\d+)([a-z])\s*$""")

    fun parse(raw: String): Parsed {
        val text = " ${raw.lowercase().trim()} "
        var volume: Double? = null
        var number: Double? = null
        var part: Int? = null
        var kind = Kind.CHAPTER

        for ((re, k) in RE_KIND) {
            if (re.containsMatchIn(text)) {
                kind = k
                break
            }
        }

        val compact = RE_COMPACT.find(text)
        val vol = RE_VOLUME.find(text) ?: if (compact == null) RE_VOLUME_V.find(text) else null
        if (vol != null) volume = vol.groupValues[1].toDouble()
        if (compact != null) {
            volume = compact.groupValues[1].toDouble()
            number = compact.groupValues[2].toDouble()
        }

        if (number == null) {
            val m = RE_CHAPTER.find(text) ?: RE_CHAPTER_HASH.find(text)
            if (m != null) {
                val tok = m.groupValues[1]
                val num = RE_NUMERIC_TOKEN.find(tok)
                if (num != null) {
                    val frac = num.groupValues[2]
                    number = (if (frac.isNotEmpty()) "${num.groupValues[1]}.$frac" else num.groupValues[1]).toDouble()
                    num.groupValues[3].firstOrNull()?.let { part = it - 'a' + 1 } // "30b" → part 2
                } else {
                    wordToNumber(tok.split(Regex("[\\s-]+"))).let { if (it != null) number = it.first.toDouble() }
                }
            }
        }

        val pm = RE_PART.find(text)
        if (pm != null) {
            part = pm.groupValues[1].toIntOrNull() ?: pm.groupValues[2].let { romanToInt(it) }
        }

        if (number == null) {
            if (RE_BARE.containsMatchIn(raw)) {
                number = raw.trim().toDouble()
            } else {
                RE_BARE_SUFFIX.find(raw)?.let { bs ->
                    number = bs.groupValues[1].toDouble()
                    if (part == null) part = bs.groupValues[2].first() - 'a' + 1
                }
            }
        }

        if (number == null && kind != Kind.CHAPTER && kind != Kind.NOTICE) {
            RE_LOOSE_INT.find(text)?.let { number = it.groupValues[1].toDouble() }
        }

        if (number == null && kind == Kind.CHAPTER) kind = Kind.UNKNOWN

        return Parsed(number, volume, part, kind)
    }
}
