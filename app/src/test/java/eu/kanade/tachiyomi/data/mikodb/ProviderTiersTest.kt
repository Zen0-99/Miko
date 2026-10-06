package eu.kanade.tachiyomi.data.mikodb

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * `ProviderTiers.effectiveTier` goldens (Phase 20-04, SD-26): the
 * community overlay PROMOTES over the manifest tier, the report cap
 * DEMOTES below it, and every failure mode — dead worker, cold device,
 * absent row — falls back to the manifest survey unchanged.
 */
class ProviderTiersTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val api = mockk<WorkIdentityApi>()
    private val mikoDbManager = mockk<MikoDbManager>()
    private val store = InMemoryPreferenceStore()
    private val tiers = ProviderTiers(api, mikoDbManager, store, json)

    private fun manifest(pkg: String, tier: String?) {
        every { mikoDbManager.sourceMeta(pkg) } returns
            tier?.let { MikoDbManager.SourceMeta(tier = it) }
    }

    private fun payload(vararg p: Pair<String, WorkIdentityApi.TierDto>) =
        WorkIdentityApi.TiersResponse(version = 2, providers = p.toMap())

    // ---- compose(): the pure composition seam ----

    @Test
    fun `dynamic tier promotes above manifest`() {
        assertEquals("A", tiers.compose(manifest = "B", dynamicTier = "A", tierCap = null))
        assertEquals("B", tiers.compose(manifest = "", dynamicTier = "B", tierCap = null))
        assertEquals("A", tiers.compose(manifest = "C", dynamicTier = "A", tierCap = null))
    }

    @Test
    fun `dynamic tier never demotes the manifest row`() {
        assertEquals("A", tiers.compose(manifest = "A", dynamicTier = "B", tierCap = null))
        assertEquals("B", tiers.compose(manifest = "B", dynamicTier = "B", tierCap = null))
        assertEquals("C", tiers.compose(manifest = "C", dynamicTier = "", tierCap = null))
    }

    @Test
    fun `tier_cap demotes below the promoted tier`() {
        assertEquals("C", tiers.compose(manifest = "A", dynamicTier = "A", tierCap = "C"))
        assertEquals("C", tiers.compose(manifest = "S", dynamicTier = null, tierCap = "C"))
    }

    @Test
    fun `tier_cap never promotes`() {
        assertEquals("", tiers.compose(manifest = "", dynamicTier = null, tierCap = "C"))
        assertEquals("C", tiers.compose(manifest = "C", dynamicTier = null, tierCap = "B"))
    }

    @Test
    fun `absent overlay leaves the manifest tier`() {
        assertEquals("B", tiers.compose(manifest = "B", dynamicTier = null, tierCap = null))
        assertEquals("", tiers.compose(manifest = "", dynamicTier = null, tierCap = null))
    }

    // ---- overlay path: fetch → effectiveTier ----

    @Test
    fun `a fetched overlay promotes the effective tier`() = runBlocking {
        manifest("pkg.a", "B")
        coEvery { api.getProviderTiers() } returns payload(
            "pkg.a" to WorkIdentityApi.TierDto(dynamic_tier = "A"),
        )
        tiers.ensureFresh()
        assertEquals("A", tiers.effectiveTier("pkg.a"))
        assertEquals("A", tiers.dynamic("pkg.a"))
        assertNull(tiers.dynamic("pkg.absent"))
        Unit
    }

    @Test
    fun `cold device with a dead fetch gets the manifest tier`() = runBlocking {
        manifest("pkg.a", "B")
        coEvery { api.getProviderTiers() } returns null
        tiers.ensureFresh()
        assertEquals("B", tiers.effectiveTier("pkg.a"))
        assertNull(tiers.dynamic("pkg.a"))
        Unit
    }

    @Test
    fun `a worker 'S' dynamic tier is filtered client-side`() = runBlocking {
        manifest("pkg.a", "B")
        coEvery { api.getProviderTiers() } returns payload(
            "pkg.a" to WorkIdentityApi.TierDto(dynamic_tier = "S"),
        )
        tiers.ensureFresh()
        assertEquals("B", tiers.effectiveTier("pkg.a"))
        assertNull(tiers.dynamic("pkg.a"))
        Unit
    }

    @Test
    fun `tiers fetch at most once per hour`() = runBlocking {
        coEvery { api.getProviderTiers() } returns payload()
        repeat(3) { tiers.ensureFresh() }
        manifest("pkg.a", "B")
        tiers.effectiveTier("pkg.a") // kicks requestRefresh — TTL blocks it
        coVerify(exactly = 1) { api.getProviderTiers() }
    }

    // ---- persisted cache: stale-serve-forever ----

    @Test
    fun `a stale persisted overlay survives a failed refetch`() = runBlocking {
        manifest("pkg.a", "B")
        val stale = ProviderTiers.TiersCache(
            fetched_at = 0, // ancient — forces the refresh path
            providers = mapOf("pkg.a" to WorkIdentityApi.TierDto(dynamic_tier = "A")),
        )
        val seeded = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    "miko_provider_tiers", json.encodeToString(stale), "",
                ),
            ),
        )
        val cached = ProviderTiers(api, mikoDbManager, seeded, json)
        coEvery { api.getProviderTiers() } returns null // dead worker
        cached.ensureFresh() // fails — the overlay must still serve
        assertEquals("A", cached.effectiveTier("pkg.a"))
        assertEquals("A", cached.dynamic("pkg.a"))
        Unit
    }
}
