package eu.kanade.tachiyomi.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EntryDescriptionParserTest {

    @Test
    fun `stats cluster is stripped and rating recovered`() {
        val raw = "Kim Gong-ja hunts the tower.\n\nRank: #6 - Rating 9,75 - Bookmarks 78.7K"
        val p = EntryDescriptionParser.parse(raw)
        assertEquals("Kim Gong-ja hunts the tower.", p.description)
        assertEquals("9.75", p.rating)
        assertEquals("6", p.rank)
    }

    @Test
    fun `misspelled bookmarkks still parses`() {
        val p = EntryDescriptionParser.parse("Synopsis.\nRank: #6 - Rating 9,75 - Bookmarkks 78.7K")
        assertEquals("9.75", p.rating)
        assertEquals("Synopsis.", p.description)
    }

    @Test
    fun `inline cluster without newline is stripped`() {
        val p = EntryDescriptionParser.parse("A tower climber. Rating: 8.4 - Comments: 1.2K")
        assertEquals("A tower climber.", p.description)
        assertEquals("8.4", p.rating)
    }

    @Test
    fun `prose mentioning rank survives`() {
        val raw = "He is an S rank hero who fights an SS-rank villain."
        val p = EntryDescriptionParser.parse(raw)
        assertEquals(raw, p.description)
        assertNull(p.rating)
        assertNull(p.rank)
    }

    @Test
    fun `prose with colon does not match`() {
        val raw = "Note: the rating system in this world is harsh."
        val p = EntryDescriptionParser.parse(raw)
        assertEquals(raw, p.description)
        assertNull(p.rating)
    }

    @Test
    fun `single stat line is stripped`() {
        val p = EntryDescriptionParser.parse("A synopsis.\nRating: 7.2")
        assertEquals("A synopsis.", p.description)
        assertEquals("7.2", p.rating)
    }

    @Test
    fun `slash denominator rating parses`() {
        val p = EntryDescriptionParser.parse("Synopsis.\nScore: 8.5/10")
        assertEquals("Synopsis.", p.description)
        assertEquals("8.5/10", p.rating)
    }

    @Test
    fun `alternative titles are captured and removed`() {
        val raw = "A hunter climbs.\n\nAlternative titles: SSS-Class Revival Hunter; SSS-Class Hunter Who Dies to Live; SSS급 죽어야 사는 헌터"
        val p = EntryDescriptionParser.parse(raw)
        assertEquals("A hunter climbs.", p.description)
        assertEquals(
            "SSS-Class Revival Hunter; SSS-Class Hunter Who Dies to Live; SSS급 죽어야 사는 헌터",
            p.altTitles,
        )
    }

    @Test
    fun `also known as label works`() {
        val p = EntryDescriptionParser.parse("Text.\nAlso known as: Foo, Bar")
        assertEquals("Foo, Bar", p.altTitles)
        assertEquals("Text.", p.description)
    }

    @Test
    fun `bare label with bullet list is fully removed`() {
        val raw = "Rank: #5 • Rating: 9.74 • Bookmarks: 79.1K\n\n" +
            "I want an S-Rank skill too! I could die for it!\n\n" +
            "Alternative Titles:\n" +
            "- SSS级重生猎人\n" +
            "- SSS급 죽어야 사는 헌터\n" +
            "- Cazador Suicida de Clase SSS\n" +
            "- SSS-Rank Suicide Hunter"
        val p = EntryDescriptionParser.parse(raw)
        assertEquals(
            "I want an S-Rank skill too! I could die for it!",
            p.description,
        )
        assertEquals("9.74", p.rating)
        assertEquals(
            "SSS级重生猎人; SSS급 죽어야 사는 헌터; Cazador Suicida de Clase SSS; SSS-Rank Suicide Hunter",
            p.altTitles,
        )
    }

    @Test
    fun `bare label with unbulleted list is removed`() {
        val raw = "A story.\nAlternative titles:\nTitle One\nTitle Two\n\nNext paragraph stays."
        val p = EntryDescriptionParser.parse(raw)
        assertEquals("A story.\n\nNext paragraph stays.", p.description)
        assertEquals("Title One; Title Two", p.altTitles)
    }

    @Test
    fun `prose line after label resumes description`() {
        val raw = "Also known as:\nThe hero fell from grace. Then he rose again.\n\nA story."
        val p = EntryDescriptionParser.parse(raw)
        // "The hero fell..." is short and ends with '.' → prose, kept.
        assertEquals("The hero fell from grace. Then he rose again.\n\nA story.", p.description)
        assertNull(p.altTitles)
    }

    @Test
    fun `clean description is unchanged`() {
        val raw = "A perfectly normal synopsis with no junk."
        val p = EntryDescriptionParser.parse(raw)
        assertEquals(raw, p.description)
        assertNull(p.rating)
        assertNull(p.altTitles)
    }

    @Test
    fun `null and blank are safe`() {
        assertEquals("", EntryDescriptionParser.parse(null).description)
        assertEquals("", EntryDescriptionParser.parse("").description)
    }

    @Test
    fun `stats before alt titles both parse`() {
        val raw = "Story.\nRank: #6 - Rating 9,75 - Bookmarks 78.7K\nAlternative titles: Alpha / Beta"
        val p = EntryDescriptionParser.parse(raw)
        assertEquals("9.75", p.rating)
        assertEquals("Alpha / Beta", p.altTitles)
        assertEquals("Story.", p.description)
        assertTrue(p.description.lines().size == 1)
    }
}
