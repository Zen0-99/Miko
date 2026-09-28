package eu.kanade.tachiyomi.data.comments

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Parity with worker/src/moderation.ts — same rule ids, same order.
 * Cases mirror worker/test/moderation.test.ts.
 */
class RuleEvaluatorTest {

    @Test
    fun `length bounds`() {
        assertEquals("min_len", RuleEvaluator.evaluateComment("   "))
        assertEquals("max_len", RuleEvaluator.evaluateComment("x".repeat(2001)))
        assertNull(RuleEvaluator.evaluateComment("nice chapter"))
    }

    @Test
    fun `link cap`() {
        assertEquals(
            "max_links",
            RuleEvaluator.evaluateComment(
                "a http://x.co/1 b http://x.co/2 c http://x.co/3 d http://x.co/4",
            ),
        )
        assertNull(RuleEvaluator.evaluateComment("see http://x.co/1"))
    }

    @Test
    fun `spam patterns case-insensitive`() {
        assertEquals("spam", RuleEvaluator.evaluateComment("WIN CRYPTO!!!"))
        assertEquals("spam", RuleEvaluator.evaluateComment("buy now followers"))
        assertEquals("spam", RuleEvaluator.evaluateComment("join my onlyfans"))
        assertNull(RuleEvaluator.evaluateComment("the winner is announced now"))
    }

    @Test
    fun `blocked domain`() {
        val ruleset = RuleEvaluator.FALLBACK.copy(
            comment = RuleEvaluator.FALLBACK.comment.copy(
                blocked_domains = listOf("bad.example"),
            ),
        )
        assertEquals("blocked_domain", RuleEvaluator.evaluateComment("http://bad.example/x", ruleset))
        assertNull(RuleEvaluator.evaluateComment("http://good.example/x", ruleset))
    }

    @Test
    fun `blocklist substring`() {
        val ruleset = RuleEvaluator.FALLBACK.copy(
            comment = RuleEvaluator.FALLBACK.comment.copy(blocklist = listOf("spoilerword")),
        )
        assertEquals("blocklist", RuleEvaluator.evaluateComment("has SPOILERWORD inside", ruleset))
    }

    @Test
    fun `name rules`() {
        assertEquals("min_len", RuleEvaluator.evaluateName("x"))
        assertEquals("max_len", RuleEvaluator.evaluateName("x".repeat(25)))
        assertEquals("pattern", RuleEvaluator.evaluateName("bad!name"))
        assertEquals("reserved", RuleEvaluator.evaluateName("Admin"))
        assertEquals("reserved", RuleEvaluator.evaluateName("MIKO"))
        assertNull(RuleEvaluator.evaluateName("Reader A"))
        assertNull(RuleEvaluator.evaluateName("日本語の名前"))
    }
}
