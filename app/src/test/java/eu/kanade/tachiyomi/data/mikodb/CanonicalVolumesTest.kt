package eu.kanade.tachiyomi.data.mikodb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CanonicalVolumesTest {

    private fun volumes(
        confidence: Double?,
        boundaries: Map<Double, Double>,
        covers: Map<Double, CanonicalVolumes.VolumeMeta> = emptyMap(),
    ) = CanonicalVolumes.WorkVolumes(
        boundaryConfidence = confidence,
        boundaries = boundaries,
        covers = covers,
    )

    @Test
    fun `groupable requires map and confidence floor`() {
        assertEquals(true, volumes(0.5, mapOf(1.0 to 1.0)).groupable())
        assertEquals(true, volumes(1.0, mapOf(1.0 to 1.0)).groupable())
        assertEquals(false, volumes(0.49, mapOf(1.0 to 1.0)).groupable())
        assertEquals(false, volumes(null, mapOf(1.0 to 1.0)).groupable())
        assertEquals(false, volumes(1.0, emptyMap()).groupable())
    }

    @Test
    fun `grouping orders volumes ascending and matches by chapter number`() {
        val wv = volumes(
            1.0,
            mapOf(
                1.0 to 1.0, 9.0 to 1.0,
                10.0 to 2.0, 18.0 to 2.0,
                19.0 to 3.0,
            ),
        )
        val g = wv.grouping(listOf(1.0, 9.0, 10.0, 18.0, 19.0), "Chapters")!!
        assertEquals(listOf("Volume 1", "Volume 2", "Volume 3"), g.groups)
        assertEquals("Volume 1", g.chapterToGroup[9.0])
        assertEquals("Volume 3", g.chapterToGroup[19.0])
    }

    @Test
    fun `unmapped chapters land in an ungrouped tail`() {
        val wv = volumes(1.0, mapOf(1.0 to 1.0, 2.0 to 1.0, 3.0 to 2.0))
        val g = wv.grouping(listOf(1.0, 2.0, 3.0, 99.0), "Chapters")!!
        assertEquals(listOf("Volume 1", "Volume 2", "Chapters"), g.groups)
        assertNull(g.chapterToGroup[99.0])
    }

    @Test
    fun `low confidence or empty maps do not group`() {
        assertNull(volumes(0.4, mapOf(1.0 to 1.0)).grouping(listOf(1.0), "Chapters"))
        assertNull(volumes(null, mapOf(1.0 to 1.0)).grouping(listOf(1.0), "Chapters"))
        assertNull(volumes(1.0, emptyMap()).grouping(listOf(1.0), "Chapters"))
        // Map exists but covers none of the visible chapters.
        assertNull(volumes(1.0, mapOf(500.0 to 50.0)).grouping(listOf(1.0), "Chapters"))
    }

    @Test
    fun `fractional volumes keep real labels`() {
        val wv = volumes(1.0, mapOf(1.0 to 2.5, 2.0 to 3.0))
        val g = wv.grouping(listOf(1.0, 2.0), "Chapters")!!
        assertEquals(listOf("Volume 2.5", "Volume 3"), g.groups)
    }

    @Test
    fun `meta maps every volume label even without covers`() {
        val wv = volumes(1.0, mapOf(1.0 to 1.0, 2.0 to 2.0))
        val g = wv.grouping(listOf(1.0, 2.0), "Chapters")!!
        assertEquals(null, g.meta["Volume 1"]?.coverThumbUrl)
        assertEquals(1.0, g.meta["Volume 1"]?.volumeNo)
    }
}
