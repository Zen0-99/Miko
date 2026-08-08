package eu.kanade.tachiyomi.ui.reader.novel.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import org.json.JSONArray

class DictionaryManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private var database: SQLiteDatabase? = null
    private val dbFile = File(appContext.filesDir, "dictionary.db")

    companion object {
        @Volatile
        private var instance: DictionaryManager? = null

        fun getInstance(context: Context): DictionaryManager {
            return instance ?: synchronized(this) {
                instance ?: DictionaryManager(context).also { instance = it }
            }
        }
    }

    fun initialize() {
        if (database != null) return
        if (!dbFile.exists()) {
            extractDatabase()
        }
        if (dbFile.exists()) {
            database = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        }
    }

    private fun extractDatabase() {
        try {
            appContext.assets.open("dictionary.db.gz").use { gzipInput ->
                GZIPInputStream(gzipInput).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Log.d("DictionaryManager", "Extracted dictionary.db.gz to ${dbFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("DictionaryManager", "Failed to extract dictionary.db.gz", e)
            try {
                appContext.assets.open("dictionary.db").use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("DictionaryManager", "Copied uncompressed dictionary.db as fallback")
            } catch (e2: Exception) {
                Log.e("DictionaryManager", "Failed to copy fallback dictionary.db", e2)
            }
        }
    }

    fun lookup(word: String): DictionaryEntry? {
        val clean = word.lowercase().trim()
        val db = database ?: return null

        lookupRaw(db, clean)?.let { return it.copy(word = word) }

        generateStems(clean).forEach { stem ->
            lookupRaw(db, stem)?.let { return it.copy(word = word) }
        }

        return null
    }

    fun isRealWord(word: String): Boolean {
        val clean = word.lowercase().trim()
        if (clean.isBlank() || clean.length < 2) return false
        val db = database ?: return false

        db.rawQuery(
            "SELECT 1 FROM entries WHERE word = ? COLLATE NOCASE LIMIT 1",
            arrayOf(clean),
        ).use { cursor ->
            if (cursor.moveToFirst()) return true
        }

        generateStems(clean).forEach { stem ->
            db.rawQuery(
                "SELECT 1 FROM entries WHERE word = ? COLLATE NOCASE LIMIT 1",
                arrayOf(stem),
            ).use { cursor ->
                if (cursor.moveToFirst()) return true
            }
        }

        return false
    }

    private fun lookupRaw(db: SQLiteDatabase, word: String): DictionaryEntry? {
        val cursor = db.rawQuery(
            "SELECT phonetic, definitions_json FROM entries WHERE word = ? COLLATE NOCASE",
            arrayOf(word),
        )
        return cursor.use {
            if (it.moveToFirst()) {
                val phonetic = it.getString(0).takeIf { it.isNotBlank() }
                val definitionsJson = it.getString(1)
                val definitions = parseDefinitions(definitionsJson)
                DictionaryEntry(word, phonetic, definitions)
            } else {
                null
            }
        }
    }

    private fun generateStems(word: String): List<String> {
        val stems = mutableListOf<String>()

        // 1. Irregular verb forms — check first, before suffix rules.
        IRREGULAR_VERBS[word]?.let { stems.add(it) }

        // 2. Doubled consonant patterns (e.g. "shrugged" → "shrug", "stopped" → "stop")
        //    "shrugged" → drop "ed" → "shrugg" → not a word, but "shrug" is
        //    Handle by checking if the letter before "ed"/"ing" is doubled
        if (word.endsWith("ed") && word.length > 4) {
            val withoutEd = word.dropLast(2)
            // Check for doubled consonant: "shrugg" + ed → "shrug"
            if (withoutEd.length >= 2 && withoutEd.last() == withoutEd.dropLast(1).last()) {
                val consonant = withoutEd.last()
                // Only strip if it's a consonant (not a vowel)
                if (consonant.lowercase() !in "aeiou") {
                    stems.add(withoutEd.dropLast(1))
                }
            }
        }
        if (word.endsWith("ing") && word.length > 5) {
            val withoutIng = word.dropLast(3)
            if (withoutIng.length >= 2 && withoutIng.last() == withoutIng.dropLast(1).last()) {
                val consonant = withoutIng.last()
                if (consonant.lowercase() !in "aeiou") {
                    stems.add(withoutIng.dropLast(1))
                }
            }
        }

        // 3. Standard suffix rules
        if (word.endsWith("ies") && word.length > 4) {
            stems.add(word.dropLast(3) + "y")
        }
        if (word.endsWith("es") && word.length > 3) {
            stems.add(word.dropLast(2))
            stems.add(word.dropLast(1))
        }
        if (word.endsWith("s") && !word.endsWith("ss") && word.length > 2) {
            stems.add(word.dropLast(1))
        }

        if (word.endsWith("ied") && word.length > 4) {
            stems.add(word.dropLast(3) + "y")
        }
        if (word.endsWith("ed") && word.length > 3) {
            stems.add(word.dropLast(2))
            stems.add(word.dropLast(1))
        }

        if (word.endsWith("ying") && word.length > 5) {
            stems.add(word.dropLast(4) + "ie")
        }
        if (word.endsWith("ing") && word.length > 4) {
            stems.add(word.dropLast(3))
            stems.add(word.dropLast(3) + "e")
        }

        if (word.endsWith("er") && word.length > 3) {
            stems.add(word.dropLast(2))
        }
        if (word.endsWith("est") && word.length > 4) {
            stems.add(word.dropLast(3))
        }

        if (word.endsWith("ly") && word.length > 3) {
            stems.add(word.dropLast(2))
            stems.add(word.dropLast(2) + "e")
        }

        if (word.endsWith("let") && word.length > 4) {
            stems.add(word.dropLast(3))
        }

        if (word.endsWith("ness") && word.length > 5) {
            stems.add(word.dropLast(4))
            stems.add(word.dropLast(4) + "y")
        }

        if (word.endsWith("ment") && word.length > 5) {
            stems.add(word.dropLast(4))
            stems.add(word.dropLast(4) + "e")
        }

        return stems.distinct()
    }

    /**
     * Mapping of common English irregular verb forms (past tense, past
     * participle, and present participle where it differs) to their base
     * form (lemma). This handles cases that the suffix-stripping rules
     * cannot — e.g. "fought" → "fight", "shrugged" → "shrug" (though
     * "shrugged" is also handled by the doubled-consonant rule above).
     */
    private val IRREGULAR_VERBS = mapOf(
        // A
        "arose" to "arise", "arisen" to "arise",
        "am" to "be", "is" to "be", "are" to "be", "was" to "be", "were" to "be", "been" to "be",
        "awoke" to "awake", "awoken" to "awake",
        // B
        "bore" to "bear", "born" to "bear", "borne" to "bear",
        "beat" to "beat", "beaten" to "beat",
        "became" to "become", "become" to "become",
        "began" to "begin", "begun" to "begin", "beginning" to "begin",
        "bent" to "bend",
        "bet" to "bet",
        "bound" to "bind",
        "bled" to "bleed",
        "blew" to "blow", "blown" to "blow",
        "broke" to "break", "broken" to "break", "breaking" to "break",
        "bred" to "breed",
        "brought" to "bring",
        "built" to "build", "building" to "build",
        "burnt" to "burn", "burned" to "burn",
        "burst" to "burst",
        "bought" to "buy", "buying" to "buy",
        // C
        "cast" to "cast",
        "caught" to "catch", "catching" to "catch",
        "chose" to "choose", "chosen" to "choose", "choosing" to "choose",
        "clung" to "cling",
        "came" to "come", "come" to "come", "coming" to "come",
        "cost" to "cost",
        "crept" to "creep",
        "cut" to "cut",
        // D
        "dealt" to "deal", "dealing" to "deal",
        "dug" to "dig", "digging" to "dig",
        "drew" to "draw", "drawn" to "draw", "drawing" to "draw",
        "dreamt" to "dream", "dreamed" to "dream",
        "drank" to "drink", "drunk" to "drink", "drinking" to "drink",
        "drove" to "drive", "driven" to "drive", "driving" to "drive",
        "dwelt" to "dwell",
        // E
        "ate" to "eat", "eaten" to "eat", "eating" to "eat",
        // F
        "fell" to "fall", "fallen" to "fall", "falling" to "fall",
        "fed" to "feed", "feeding" to "feed",
        "felt" to "feel", "feeling" to "feel",
        "fought" to "fight", "fighting" to "fight",
        "found" to "find", "finding" to "find",
        "fled" to "flee",
        "flew" to "fly", "flown" to "fly", "flying" to "fly",
        "forbade" to "forbid", "forbidden" to "forbid",
        "forgot" to "forget", "forgotten" to "forget", "forgetting" to "forget",
        "forgave" to "forgive", "forgiven" to "forgive",
        "froze" to "freeze", "frozen" to "freeze", "freezing" to "freeze",
        // G
        "got" to "get", "gotten" to "get", "getting" to "get",
        "gave" to "give", "given" to "give", "giving" to "give",
        "went" to "go", "gone" to "go", "going" to "go",
        "ground" to "grind",
        "grew" to "grow", "grown" to "grow", "growing" to "grow",
        // H
        "had" to "have", "having" to "have",
        "heard" to "hear", "hearing" to "hear",
        "hid" to "hide", "hidden" to "hide", "hiding" to "hide",
        "hit" to "hit",
        "held" to "hold", "holding" to "hold",
        "hurt" to "hurt",
        // K
        "kept" to "keep", "keeping" to "keep",
        "knelt" to "kneel",
        "knew" to "know", "known" to "know", "knowing" to "know",
        // L
        "laid" to "lay", "laying" to "lay",
        "led" to "lead", "leading" to "lead",
        "leant" to "lean",
        "leapt" to "leap",
        "learnt" to "learn", "learned" to "learn",
        "left" to "leave", "leaving" to "leave",
        "lent" to "lend",
        "lit" to "light", "lighting" to "light",
        // M
        "made" to "make", "making" to "make",
        "meant" to "mean", "meaning" to "mean",
        "met" to "meet", "meeting" to "meet",
        // P
        "paid" to "pay", "paying" to "pay",
        "put" to "put",
        // R
        "read" to "read",
        "rode" to "ride", "ridden" to "ride", "riding" to "ride",
        "rang" to "ring", "rung" to "ring", "ringing" to "ring",
        "rose" to "rise", "risen" to "rise", "rising" to "rise",
        "ran" to "run", "run" to "run", "running" to "run",
        // S
        "said" to "say", "saying" to "say",
        "saw" to "see", "seen" to "see", "seeing" to "see",
        "sought" to "seek", "seeking" to "seek",
        "sold" to "sell", "selling" to "sell",
        "sent" to "send", "sending" to "send",
        "set" to "set",
        "sewed" to "sew", "sewn" to "sew",
        "shook" to "shake", "shaken" to "shake", "shaking" to "shake",
        "shone" to "shine",
        "shot" to "shoot", "shooting" to "shoot",
        "showed" to "show", "shown" to "show", "showing" to "show",
        "shrank" to "shrink", "shrunk" to "shrink", "shrinking" to "shrink",
        "shut" to "shut",
        "sang" to "sing", "sung" to "sing", "singing" to "sing",
        "sank" to "sink", "sunk" to "sink", "sinking" to "sink",
        "sat" to "sit", "sitting" to "sit",
        "slew" to "slay", "slain" to "slay",
        "slept" to "sleep", "sleeping" to "sleep",
        "slid" to "slide", "sliding" to "slide",
        "spoke" to "speak", "spoken" to "speak", "speaking" to "speak",
        "spent" to "spend", "spending" to "spend",
        "spilt" to "spill", "spilled" to "spill",
        "spun" to "spin", "spinning" to "spin",
        "spread" to "spread",
        "sprang" to "spring", "sprung" to "spring",
        "stood" to "stand", "standing" to "stand",
        "stole" to "steal", "stolen" to "steal", "stealing" to "steal",
        "stuck" to "stick", "sticking" to "stick",
        "stung" to "sting",
        "stank" to "stink", "stunk" to "stink",
        "strung" to "string",
        "struck" to "strike", "stricken" to "strike",
        "swore" to "swear", "sworn" to "swear",
        "swept" to "sweep", "sweeping" to "sweep",
        "swam" to "swim", "swum" to "swim", "swimming" to "swim",
        "swung" to "swing", "swinging" to "swing",
        // T
        "took" to "take", "taken" to "take", "taking" to "take",
        "taught" to "teach", "teaching" to "teach",
        "tore" to "tear", "torn" to "tear", "tearing" to "tear",
        "told" to "tell", "telling" to "tell",
        "thought" to "think", "thinking" to "think",
        "threw" to "throw", "thrown" to "throw", "throwing" to "throw",
        "understood" to "understand",
        // W
        "woke" to "wake", "woken" to "wake", "waking" to "wake",
        "wore" to "wear", "worn" to "wear", "wearing" to "wear",
        "wove" to "weave", "woven" to "weave",
        "wed" to "wed",
        "wept" to "weep", "weeping" to "weep",
        "won" to "win", "winning" to "win",
        "wound" to "wind",
        "withdrew" to "withdraw", "withdrawn" to "withdraw",
        "wrote" to "write", "written" to "write", "writing" to "write",
        // Present tense 3rd person singular (adds 's')
        "does" to "do", "goes" to "go", "has" to "have",
        // Common contractions / forms
        "couldn't" to "could", "wouldn't" to "would", "shouldn't" to "should",
        "didn't" to "do", "doesn't" to "do", "wasn't" to "be", "weren't" to "be",
        "hadn't" to "have", "hasn't" to "have", "haven't" to "have",
    )

    private fun parseDefinitions(json: String): List<DictionaryEntry.Definition> {
        val list = mutableListOf<DictionaryEntry.Definition>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val pos = obj.getString("pos")
            val meaning = obj.getString("meaning")
            val examplesArr = obj.optJSONArray("examples") ?: JSONArray()
            val examples = mutableListOf<String>()
            for (j in 0 until examplesArr.length()) {
                examples.add(examplesArr.getString(j))
            }
            list.add(DictionaryEntry.Definition(pos, meaning, examples))
        }
        return list
    }
}
