package eu.kanade.tachiyomi.ui.discovery

import eu.kanade.domain.ui.model.Carrier
import eu.kanade.tachiyomi.metadata.tmdb.TmdbDiscover
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState

class DiscoveryScreenModelTest {

    private val tmdb = mockk<TmdbDiscover>()

    private fun model(): DiscoveryScreenModel =
        DiscoveryScreenModel(tmdbDiscover = tmdb)

    private fun item(id: Long) = TmdbDiscover.Item(
        id = id,
        title = "Title $id",
        posterUrl = null,
        year = 2020,
        voteAverage = 7.5,
        mediaType = MediaType.MOVIE,
    )

    /**
     * Voyager's screenModelScope launches on Dispatchers.Main — point it
     * at a dispatcher sharing this test's scheduler so advanceUntilIdle
     * drives the debounced reload.
     */
    private fun TestScope.useTestMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `inactive filter never calls discover and resets the surface`() = runTest {
        useTestMain()
        val sm = model()
        sm.onDiscoverInputsChanged(MediaType.MOVIE)
        advanceUntilIdle()
        coVerify(exactly = 0) { tmdb.discover(any(), any(), any()) }
        assertTrue(sm.state.value.discoverItems.isEmpty())
    }

    @Test
    fun `active filter fires one debounced page-1 discover call`() = runTest {
        useTestMain()
        every { tmdb.hasKey() } returns true
        coEvery { tmdb.discover(any(), any(), any()) } returns
            TmdbDiscover.Page(listOf(item(1)), page = 1, hasMore = false)

        val sm = model()
        sm.setFilter(
            Carrier.SHOWS,
            DiscoveryFilter().withGenreState(28, TriState.ENABLED_IS),
        )
        // Two rapid facet taps collapse into a single request.
        sm.onDiscoverInputsChanged(MediaType.MOVIE)
        sm.onDiscoverInputsChanged(MediaType.MOVIE)
        advanceUntilIdle()

        coVerify(exactly = 1) { tmdb.discover(MediaType.MOVIE, any(), 1) }
        assertEquals(listOf(1L), sm.state.value.discoverItems.map { it.id })
    }

    @Test
    fun `filters are isolated per carrier`() = runTest {
        useTestMain()
        every { tmdb.hasKey() } returns true
        coEvery { tmdb.discover(any(), any(), any()) } returns
            TmdbDiscover.Page(emptyList(), page = 1, hasMore = false)

        val sm = model()
        sm.setFilter(
            Carrier.MANGA,
            DiscoveryFilter().withGenreState(28, TriState.ENABLED_IS),
        )
        // Manga's filter is stored but never drives TMDB — the shows
        // filter is still inactive so nothing loads.
        sm.onDiscoverInputsChanged(MediaType.MOVIE)
        advanceUntilIdle()

        coVerify(exactly = 0) { tmdb.discover(any(), any(), any()) }
        assertFalse(sm.filterFor(Carrier.SHOWS).isActive)
        assertTrue(sm.filterFor(Carrier.MANGA).isActive)
    }

    @Test
    fun `paging appends and de-duplicates items`() = runTest {
        useTestMain()
        every { tmdb.hasKey() } returns true
        coEvery { tmdb.discover(any(), any(), 1) } returns
            TmdbDiscover.Page(listOf(item(1), item(2)), page = 1, hasMore = true)
        coEvery { tmdb.discover(any(), any(), 2) } returns
            TmdbDiscover.Page(listOf(item(2), item(3)), page = 2, hasMore = false)

        val sm = model()
        sm.setFilter(
            Carrier.SHOWS,
            DiscoveryFilter().withGenreState(28, TriState.ENABLED_IS),
        )
        sm.onDiscoverInputsChanged(MediaType.MOVIE)
        advanceUntilIdle()
        sm.loadNextDiscoverPage(MediaType.MOVIE)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L, 3L), sm.state.value.discoverItems.map { it.id })
        assertFalse(sm.state.value.discoverHasMore)
    }

    @Test
    fun `clearing the filter resets the discover surface`() = runTest {
        useTestMain()
        every { tmdb.hasKey() } returns true
        coEvery { tmdb.discover(any(), any(), any()) } returns
            TmdbDiscover.Page(listOf(item(1)), page = 1, hasMore = false)

        val sm = model()
        sm.setFilter(
            Carrier.SHOWS,
            DiscoveryFilter().withGenreState(28, TriState.ENABLED_IS),
        )
        sm.onDiscoverInputsChanged(MediaType.MOVIE)
        advanceUntilIdle()
        assertTrue(sm.state.value.discoverItems.isNotEmpty())

        sm.setFilter(Carrier.SHOWS, DiscoveryFilter())
        sm.onDiscoverInputsChanged(MediaType.MOVIE)
        advanceUntilIdle()

        assertTrue(sm.state.value.discoverItems.isEmpty())
        assertTrue(sm.state.value.discoverHasMore)
    }

    @Test
    fun `missing key surfaces the BYOK notice instead of loading`() = runTest {
        useTestMain()
        every { tmdb.hasKey() } returns false

        val sm = model()
        sm.setFilter(
            Carrier.SHOWS,
            DiscoveryFilter().withGenreState(28, TriState.ENABLED_IS),
        )
        sm.onDiscoverInputsChanged(MediaType.MOVIE)
        advanceUntilIdle()

        coVerify(exactly = 0) { tmdb.discover(any(), any(), any()) }
        assertTrue(sm.state.value.discoverKeyMissing)
        assertFalse(sm.state.value.discoverHasMore)
    }

    @Test
    fun `debounce window delays the discover call`() = runTest {
        useTestMain()
        every { tmdb.hasKey() } returns true
        coEvery { tmdb.discover(any(), any(), any()) } returns
            TmdbDiscover.Page(emptyList(), page = 1, hasMore = false)

        val sm = model()
        sm.setFilter(
            Carrier.SHOWS,
            DiscoveryFilter().withGenreState(28, TriState.ENABLED_IS),
        )
        sm.onDiscoverInputsChanged(MediaType.MOVIE)
        advanceTimeBy(DiscoveryScreenModel.FILTER_DEBOUNCE_MS - 1)
        coVerify(exactly = 0) { tmdb.discover(any(), any(), any()) }
        advanceUntilIdle()
        coVerify(exactly = 1) { tmdb.discover(any(), any(), any()) }
    }
}
