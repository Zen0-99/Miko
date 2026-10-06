package eu.kanade.tachiyomi.data.mikodb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `Matrix.readable`/`installable` lifecycle gate (Phase 20, NCM-014):
 * confirmed serves everyone, provisional only its minter, quarantined
 * never — the client-side mirror of the worker's serve predicate. The
 * server remains authoritative; this gate is defense-in-depth for
 * cached/offline matrices inside the 6h TTL.
 */
class WorkCarriersMatrixTest {

    private fun carrier(
        pkg: String,
        status: String = "confirmed",
        mine: Boolean = false,
        installed: Boolean = true,
        enabled: Boolean = true,
        installable: Boolean = false,
    ) = WorkCarriers.Carrier(
        pkgName = pkg,
        displayName = pkg,
        tier = "A",
        providerRef = "/series/$pkg",
        provenance = "device_feed",
        lastSeenAt = null,
        installed = installed,
        enabled = enabled,
        sourceIds = if (installed) listOf(1L) else emptyList(),
        iconUrl = null,
        lang = "en",
        installable = installable,
        status = status,
        mine = mine,
    )

    private fun matrix(vararg carriers: WorkCarriers.Carrier) =
        WorkCarriers.Matrix("mk:m:1", carriers.toList(), fetched = true)

    @Test
    fun `confirmed carrier is readable for everyone`() {
        val m = matrix(carrier("a.a"))
        assertEquals(listOf("a.a"), m.readable.map { it.pkgName })
    }

    @Test
    fun `provisional binding the device did not mint is not readable`() {
        val m = matrix(
            carrier("a.a", status = "provisional", mine = false),
            carrier("b.b", status = "confirmed"),
        )
        assertFalse(m.readable.any { it.pkgName == "a.a" })
        assertEquals(listOf("b.b"), m.readable.map { it.pkgName })
    }

    @Test
    fun `provisional binding the device minted stays readable`() {
        val m = matrix(carrier("a.a", status = "provisional", mine = true))
        assertEquals(listOf("a.a"), m.readable.map { it.pkgName })
    }

    @Test
    fun `quarantined is never readable — not even for its minter`() {
        val m = matrix(
            carrier("a.a", status = "quarantined", mine = false),
            carrier("b.b", status = "quarantined", mine = true),
        )
        assertTrue(m.readable.isEmpty())
    }

    @Test
    fun `quarantined is never installable`() {
        val m = matrix(
            carrier(
                "a.a",
                status = "quarantined",
                installed = false,
                enabled = false,
                installable = true,
            ),
        )
        assertTrue(m.installable.isEmpty())
        // …but stays on `carriers` so the sheet can badge it.
        assertEquals(1, m.carriers.size)
    }

    @Test
    fun `uninstalled provisional mint is still installable`() {
        val m = matrix(
            carrier(
                "a.a",
                status = "provisional",
                mine = true,
                installed = false,
                enabled = false,
                installable = true,
            ),
        )
        assertEquals(listOf("a.a"), m.installable.map { it.pkgName })
    }

    @Test
    fun `carrier built without lifecycle fields defaults to confirmed`() {
        // Positional fixture without the lifecycle tail — the pre-20
        // shape a CanonicalChapterRouterTest-style construction uses.
        val c = WorkCarriers.Carrier(
            pkgName = "a.a", displayName = "a.a", tier = "A", providerRef = "/x",
            provenance = null, lastSeenAt = null, installed = true, enabled = true,
            sourceIds = listOf(1L), iconUrl = null, lang = "en", installable = false,
        )
        assertEquals("confirmed", c.status)
        assertFalse(c.mine)
        assertEquals(listOf("a.a"), matrix(c).readable.map { it.pkgName })
    }

    @Test
    fun `disabled carrier never reads regardless of lifecycle`() {
        val m = matrix(
            carrier("a.a", status = "confirmed", enabled = false),
            carrier("b.b", status = "provisional", mine = true, enabled = false),
        )
        assertTrue(m.readable.isEmpty())
    }

    @Test
    fun `carriers keeps every row so sheets can badge them`() {
        val m = matrix(
            carrier("a.a", status = "confirmed"),
            carrier("b.b", status = "provisional", mine = false),
            carrier("c.c", status = "quarantined"),
        )
        assertEquals(3, m.carriers.size)
        assertEquals(listOf("a.a"), m.readable.map { it.pkgName })
    }
}
