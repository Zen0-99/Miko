package eu.kanade.tachiyomi.data.comments

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.injectLazy

/**
 * Kotlin twin of `worker/src/moderation.ts` — same rule order, same
 * rule ids. Instant client-side feedback only; the Worker always
 * re-validates server-side (modified clients can skip this).
 *
 * Ruleset comes from `GET /v1/ruleset` (cached in prefs) with a
 * bundled v1 fallback for offline/first-run. Spam patterns compile
 * with IGNORE_CASE — JS lacks inline `(?i)`, so the shared
 * ruleset.json keeps patterns flag-free (see ruleset v1).
 */
object RuleEvaluator {

    @Serializable
    data class Ruleset(
        val version: Int,
        val comment: CommentRules,
        val name: NameRules,
    ) {
        @Serializable
        data class CommentRules(
            val min_len: Int,
            val max_len: Int,
            val max_links: Int,
            val blocklist: List<String> = emptyList(),
            val blocked_domains: List<String> = emptyList(),
            val spam_patterns: List<String> = emptyList(),
            val rate: Rate = Rate(),
            val duplicate_window_h: Int = 24,
        ) {
            @Serializable
            data class Rate(val posts_per_min: Int = 3, val posts_per_hour: Int = 20)
        }

        @Serializable
        data class NameRules(
            val min_len: Int,
            val max_len: Int,
            val pattern: String,
            val reserved: List<String> = emptyList(),
        )
    }

    /** Bundled fallback — mirror of worker/ruleset.json v1. */
    val FALLBACK: Ruleset = Ruleset(
        version = 1,
        comment = Ruleset.CommentRules(
            min_len = 1,
            max_len = 2000,
            max_links = 3,
            spam_patterns = listOf(
                "\\b(buy|free|win)\\s+(now|here|followers|crypto)\\b",
                "\\b(onlyfans|casino|slots|airdrop)\\b",
            ),
        ),
        name = Ruleset.NameRules(
            min_len = 2,
            max_len = 24,
            pattern = "^[\\p{L}\\p{N} _.-]+$",
            reserved = listOf(
                "miko", "admin", "administrator", "mod", "moderator", "system",
                "anonymous", "staff", "support", "official", "dev", "developer",
                "jev", "mangabaka", "cognition",
            ),
        ),
    )

    private val URL_RE = Regex("https?://[^\\s)]+", RegexOption.IGNORE_CASE)

    /** Returns the rule id that fails, or null when the body passes. */
    fun evaluateComment(body: String, ruleset: Ruleset = FALLBACK): String? {
        val r = ruleset.comment
        val len = body.trim().length
        if (len < r.min_len) return "min_len"
        if (len > r.max_len) return "max_len"
        val links = URL_RE.findAll(body).map { it.value }.toList()
        if (links.size > r.max_links) return "max_links"
        for (domain in r.blocked_domains) {
            if (links.any { it.lowercase().contains(domain.lowercase()) }) return "blocked_domain"
        }
        val lower = body.lowercase()
        for (word in r.blocklist) {
            if (lower.contains(word.lowercase())) return "blocklist"
        }
        for (pattern in r.spam_patterns) {
            try {
                if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(body)) return "spam"
            } catch (_: Exception) {
                // Malformed pattern in a fetched ruleset — skip.
            }
        }
        return null
    }

    /** Returns the rule id that fails, or null when the name passes. */
    fun evaluateName(name: String, ruleset: Ruleset = FALLBACK): String? {
        val r = ruleset.name
        val trimmed = name.trim()
        if (trimmed.length < r.min_len) return "min_len"
        if (trimmed.length > r.max_len) return "max_len"
        try {
            if (!Regex(r.pattern).matches(trimmed)) return "pattern"
        } catch (_: Exception) {
            return "pattern"
        }
        if (r.reserved.any { it.equals(trimmed, ignoreCase = true) }) return "reserved"
        return null
    }
}

/**
 * Fetches + caches the server's ruleset so the evaluator tracks
 * `ruleset_version` without an app update.
 */
class RulesetProvider {

    private val api: CommentsApi by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val json: Json by injectLazy()

    private val versionPref by lazy { preferenceStore.getInt("comments_ruleset_version", 0) }
    private val jsonPref by lazy { preferenceStore.getString("comments_ruleset_json", "") }

    @Volatile
    private var cached: RuleEvaluator.Ruleset? = null

    fun current(): RuleEvaluator.Ruleset {
        cached?.let { return it }
        val stored = jsonPref.get()
        if (stored.isNotEmpty()) {
            runCatching {
                json.decodeFromString<RuleEvaluator.Ruleset>(stored)
            }.getOrNull()?.let {
                cached = it
                return it
            }
        }
        return RuleEvaluator.FALLBACK
    }

    /** Refresh from the Worker — called on app start / comment open. */
    suspend fun refresh(): RuleEvaluator.Ruleset {
        val res = api.getRuleset()
        if (res is CommentsApi.Result.Ok) {
            val ruleset = res.value.ruleset
            jsonPref.set(json.encodeToString(ruleset))
            versionPref.set(ruleset.version)
            cached = ruleset
        }
        return current()
    }
}
