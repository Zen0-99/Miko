package eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   Caller (synth path)
//     │
//     │  raw text
//     ▼
//   Preprocessor.apply(text, enabledRules)
//     │
//     ├── for each rule in PreprocessingRules.ALL (CLI priority order):
//     │       if rule.name in enabledRules: text = rule.transform(text)
//     │
//     ├── collapse whitespace runs (newline-preserving — see below)
//     │
//     └── trim leading/trailing whitespace
//                │
//                ▼
//           normalized text  →  engine.synthesize(text, ...)
//
//   Notes:
//     - enabledRules order is irrelevant; ALL is the source of truth
//       for application order (so toggling rules in any order in
//       Settings can never break the pipeline).
//     - The whitespace collapse is unconditional but PRESERVES newline
//       structure: horizontal runs → one space, runs containing one
//       newline → "\n", runs containing 2+ newlines → "\n\n". This is a
//       deliberate divergence from the CLI's `re.sub(r"\s+", " ", ...)`:
//       the CLI splits lines BEFORE preprocessing (batch mode) so it can
//       flatten freely, whereas Android's consumers read the newline
//       structure AFTER preprocessing — TextChunker splits clauses on
//       `\n+` and paragraphs on `\n\s*\n`, and Pocket's P-AM rewrite
//       turns line breaks into sentence pauses. A flat `\s+ → " "` here
//       silently disabled all of that (upstream audit 2026-07-11).
// -----------------------------------------------------------------------------

/**
 * Applies a configured subset of [PreprocessingRules] to a string.
 *
 * Stateless — the only field is the rules-by-name lookup, populated once
 * at construction time. Miko has no DI need for this class (no Hilt):
 * callers construct it directly, or use the default argument which is
 * always the full catalog keyed by name.
 *
 * Ported from marmalade-tts-android `preprocessing/Preprocessor.kt`;
 * upstream's `@Singleton`/`@Inject` annotations were dropped and
 * [rulesByName] gained its default value.
 */
class Preprocessor(
    private val rulesByName: Map<String, PreprocessingRule> =
        PreprocessingRules.ALL.associateBy { it.name },
) {

    /**
     * Run every rule in [enabledRules] against [text] in
     * [PreprocessingRules.ALL]'s catalog order, then collapse the
     * whitespace runs the rules may have left behind and trim ends.
     *
     * Rules referenced by name that aren't in the catalog are silently
     * ignored — keeps persisted rule sets forward-compatible when a
     * rule is added and a previously-stored set predates it.
     *
     * @return the normalized text, ready to feed the engine.
     */
    fun apply(text: String, enabledRules: Set<String>): String {
        var out = text
        for (rule in PreprocessingRules.ALL) {
            if (rule.name in enabledRules) {
                out = rule.transform(out)
            }
        }
        // Final whitespace collapse, newline-preserving (see the module
        // comment for why this diverges from the CLI's flat collapse).
        return collapseWhitespacePreservingNewlines(out)
    }
}

/** Whitespace run containing two or more newlines → paragraph break. */
private val PARAGRAPH_RUN = Regex("[^\\S\\n]*\\n(?:[^\\S\\n]*\\n)+[^\\S\\n]*")

/** Whitespace run containing exactly one newline → line break. */
private val NEWLINE_RUN = Regex("[^\\S\\n]*\\n[^\\S\\n]*")

/** Horizontal whitespace run (spaces/tabs, no newline) → one space. */
private val HORIZONTAL_RUN = Regex("[^\\S\\n]+")

/**
 * Collapse whitespace runs while keeping the newline structure the
 * downstream chunkers/pause rewrites read: horizontal runs → one space,
 * one-newline runs → "\n", 2+-newline runs → "\n\n". Shared by
 * [Preprocessor.apply] and [EmojiProsody.stripEmojis] — both run before
 * the engine, and either one flattening newlines would silently disable
 * TextChunker's paragraph handling and Pocket's P-AM line-break pauses.
 */
internal fun collapseWhitespacePreservingNewlines(text: String): String =
    text
        .replace(PARAGRAPH_RUN, "\n\n")
        .replace(NEWLINE_RUN, "\n")
        .replace(HORIZONTAL_RUN, " ")
        .trim()
