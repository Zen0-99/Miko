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
        Regex("""\b(epilogue|epl)\b""") to Kind.EPILOGUE,
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

    // Web-novel compound coordinate: "CH.Book 7 14" / "Book 2 ex5" —
    // the book ordinal is a high-order component, folded into `number`
    // as N*BOOK_SCALE + sub so a single Double keeps per-book identity
    // and cross-book ordering (a bare `chapter_number` column has no
    // volume dimension). `exN` is the book's own extra/interlude index.
    private val RE_BOOK = Regex("""\bbook\s*([0-9]+)(?:\s+ex\s*([0-9]+))?""")
    private const val BOOK_SCALE = 10000.0
    private const val SUB_EXTRAS = 5000.0
    private const val SUB_NOTICE = 9995.0
    private const val SUB_AFTERWORD = 9997.0
    private const val SUB_EPILOGUE = 9999.0
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

        // "Book N" is consumed up-front and blanked so the chapter token
        // regexes only see the sub-chapter coordinate ("CH.Book 7 14" →
        // book 7 + chapter 14). Without this, RE_CHAPTER captures the
        // literal word "book" and the name collapses to number=null.
        var bookCoord: Double? = null
        var bookExtraIdx: Double? = null
        val bm = RE_BOOK.find(text)
        val coordText = if (bm != null) {
            bookCoord = bm.groupValues[1].toDouble()
            bookExtraIdx = bm.groupValues[2].toDoubleOrNull()
            text.replaceRange(bm.range, " ".repeat(bm.range.last - bm.range.first + 1))
        } else {
            text
        }

        val compact = RE_COMPACT.find(coordText)
        val vol = RE_VOLUME.find(coordText) ?: if (compact == null) RE_VOLUME_V.find(coordText) else null
        if (vol != null) volume = vol.groupValues[1].toDouble()
        if (compact != null) {
            volume = compact.groupValues[1].toDouble()
            number = compact.groupValues[2].toDouble()
        }

        if (number == null) {
            val m = RE_CHAPTER.find(coordText) ?: RE_CHAPTER_HASH.find(coordText)
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

        val pm = RE_PART.find(coordText)
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
            RE_LOOSE_INT.find(coordText)?.let { number = it.groupValues[1].toDouble() }
        }

        if (bookCoord != null) {
            // Sub-coordinate spaces inside a book: mains [0,4999],
            // extras [5000,9899] — `CH.Book 7 ex5` must not share
            // `chapter_number` with `CH.Book 7 5` (threshold marks and
            // next-unread picks would ambiguate) — and end-matter pins
            // to the tail so it sorts after every chapter of its book.
            number = bookCoord * BOOK_SCALE + when (kind) {
                Kind.EPILOGUE -> SUB_EPILOGUE + (number ?: 0.0)
                Kind.AFTERWORD -> SUB_AFTERWORD + (number ?: 0.0)
                Kind.NOTICE -> SUB_NOTICE + (number ?: 0.0)
                else -> {
                    val extra = bookExtraIdx != null || kind == Kind.EXTRA
                    val idx = bookExtraIdx ?: number ?: 0.0
                    if (extra) SUB_EXTRAS + idx else idx
                }
            }
            if (bookExtraIdx != null && kind == Kind.CHAPTER) kind = Kind.EXTRA
            if (volume == null) volume = bookCoord
        }

        if (number == null && kind == Kind.CHAPTER) kind = Kind.UNKNOWN

        return Parsed(number, volume, part, kind)
    }

    /**
     * Human-facing name for a book-coordinate chapter — "CH.Book 6 14:
     * Audience" renders "Chapter 14: Audience". The stored name stays
     * untouched (it keys download dirs and audio manifests); this is a
     * display transform only. Returns null when the name isn't a
     * book-coordinate chapter or has no resolvable sub-title — callers
     * fall back to the raw name.
     */
    fun displayName(raw: String): String? {
        val text = raw.trim()
        val padded = " ${text.lowercase()} "
        val bm = RE_BOOK.find(padded) ?: return null
        val p = parse(raw)
        if (p.number == null || p.volume == null) return null
        val sub = p.number - p.volume * BOOK_SCALE
        val title = text.substringAfter(':', "").trim().takeIf {
            it.isNotEmpty() && it != text
        }
        // "Book 7" alone carries no sub-coordinate and no title — the
        // raw name is already the right label, rewriting it to
        // "Chapter 0" would be nonsense.
        if (sub <= 0.0 && p.kind == Kind.CHAPTER && title == null) return null
        val base = when (p.kind) {
            Kind.EPILOGUE -> "Epilogue"
            Kind.AFTERWORD -> "Afterword"
            Kind.NOTICE -> "Notice"
            Kind.PROLOGUE -> "Prologue"
            Kind.EXTRA -> "Extra ${formatLocal(sub - SUB_EXTRAS)}"
            else -> "Chapter ${formatLocal(sub)}"
        }
        return when {
            title == null -> base
            // "CH.Book 7 epl: Epilogue I" — the title already carries
            // the kind word; "Epilogue: Epilogue I" would stutter.
            title.lowercase().startsWith(base.lowercase()) -> title
            else -> "$base: $title"
        }
    }

    /**
     * [displayName] plus the leading book/volume/season-style group
     * marker — "CH.Book 6 14: Audience" → "Book 6 Chapter 14: Audience",
     * "Vol.2 Ch.30: Title" → "Volume 2 Chapter 30: Title". For
     * continue/resume labels where the stripped name alone loses which
     * book the chapter belongs to. Null when the name carries no group
     * marker — callers fall back to [displayName] or the raw name.
     */
    fun qualifiedDisplayName(raw: String): String? {
        val group = eu.kanade.tachiyomi.util.chapter.ChapterNameGroup
            .parse(raw) ?: return null
        val stripped = eu.kanade.tachiyomi.util.chapter.ChapterNameGroup
            .stripMarker(raw).orEmpty()
        val inner = displayName(raw)
            ?: stripped.takeIf { it.isNotBlank() }
                ?.let { simpleDisplayName(it) ?: it }
        return if (inner.isNullOrBlank() || inner == group) {
            group
        } else {
            "$group $inner"
        }
    }

    /**
     * Bare-numbering chapters — "#3/3#/3", "003", "3.5" — carry no words
     * worth showing; the number IS the name. Renders "Chapter 3". Null
     * when the name carries any letters (a real title/label) or resolves
     * no usable number — callers fall back to the raw name.
     */
    fun bareNumberDisplayName(raw: String): String? {
        if (raw.any { it.isLetter() }) return null
        val p = parse(raw)
        if (p.number == null || p.volume != null) return null
        return "Chapter ${formatLocal(p.number)}"
    }

    /**
     * Number/kind formatter without [displayName]'s book-coordinate
     * requirement — "Ch.30: Title" → "Chapter 30: Title". Feeds the
     * inner label of [qualifiedDisplayName] once the group marker is
     * stripped; null when the name has no usable chapter number or still
     * carries a volume coordinate of its own.
     */
    private fun simpleDisplayName(raw: String): String? {
        val p = parse(raw)
        if (p.number == null || p.volume != null) return null
        val title = raw.substringAfter(':', "").trim().takeIf {
            it.isNotEmpty() && it != raw
        }
        val base = when (p.kind) {
            Kind.EPILOGUE -> "Epilogue"
            Kind.AFTERWORD -> "Afterword"
            Kind.NOTICE -> "Notice"
            Kind.PROLOGUE -> "Prologue"
            Kind.EXTRA -> "Extra ${formatLocal(p.number)}"
            else -> "Chapter ${formatLocal(p.number)}"
        }
        return when {
            title == null -> base
            title.lowercase().startsWith(base.lowercase()) -> title
            else -> "$base: $title"
        }
    }

    private fun formatLocal(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}
