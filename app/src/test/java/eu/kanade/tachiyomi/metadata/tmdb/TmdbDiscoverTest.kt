package eu.kanade.tachiyomi.metadata.tmdb

import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TmdbDiscoverTest {

    @Test
    fun `tri-state genres map to with and without_genres`() {
        val params = TmdbDiscover.queryParams(
            MediaType.MOVIE,
            TmdbDiscover.DiscoverQuery(
                withGenres = listOf(28, 16),
                withoutGenres = listOf(27),
            ),
        )
        assertEquals("28,16", params["with_genres"])
        assertEquals("27", params["without_genres"])
        assertFalse("with_keywords" in params)
    }

    @Test
    fun `keyword facets map to with and without_keywords`() {
        val params = TmdbDiscover.queryParams(
            MediaType.TV,
            TmdbDiscover.DiscoverQuery(
                withKeywords = listOf(201712, 184125),
                withoutKeywords = listOf(6152),
            ),
        )
        assertEquals("201712,184125", params["with_keywords"])
        assertEquals("6152", params["without_keywords"])
    }

    @Test
    fun `year range maps to release-date window for movies`() {
        val params = TmdbDiscover.queryParams(
            MediaType.MOVIE,
            TmdbDiscover.DiscoverQuery(yearGte = 1990, yearLte = 1999),
        )
        assertEquals("1990-01-01", params["primary_release_date.gte"])
        assertEquals("1999-12-31", params["primary_release_date.lte"])
        assertFalse("first_air_date.gte" in params)
    }

    @Test
    fun `year range maps to air-date window for tv`() {
        val params = TmdbDiscover.queryParams(
            MediaType.TV,
            TmdbDiscover.DiscoverQuery(yearGte = 2000, yearLte = 2010),
        )
        assertEquals("2000-01-01", params["first_air_date.gte"])
        assertEquals("2010-12-31", params["first_air_date.lte"])
        assertFalse("primary_release_date.gte" in params)
    }

    @Test
    fun `rating floor implies a default vote-count floor`() {
        val params = TmdbDiscover.queryParams(
            MediaType.MOVIE,
            TmdbDiscover.DiscoverQuery(voteAverageGte = 7.0),
        )
        assertEquals("7.0", params["vote_average.gte"])
        assertEquals(
            TmdbDiscover.DEFAULT_VOTE_COUNT_GTE.toString(),
            params["vote_count.gte"],
        )
    }

    @Test
    fun `explicit vote floor wins over the implied default`() {
        val params = TmdbDiscover.queryParams(
            MediaType.MOVIE,
            TmdbDiscover.DiscoverQuery(voteAverageGte = 6.0, voteCountGte = 500),
        )
        assertEquals("500", params["vote_count.gte"])
    }

    @Test
    fun `rating sort implies the top-rated vote-count floor`() {
        val params = TmdbDiscover.queryParams(
            MediaType.TV,
            TmdbDiscover.DiscoverQuery(sortBy = "vote_average.desc"),
        )
        assertEquals(
            TmdbDiscover.TOP_RATED_VOTE_COUNT_GTE.toString(),
            params["vote_count.gte"],
        )
        assertFalse("vote_average.gte" in params)
    }

    @Test
    fun `rating sort wins over the rating-facet floor`() {
        // Sorting by rating needs the heavier 500-vote floor even when a
        // rating facet is set (its implied 100 is too low for top-rated).
        val params = TmdbDiscover.queryParams(
            MediaType.MOVIE,
            TmdbDiscover.DiscoverQuery(
                voteAverageGte = 7.0,
                sortBy = "vote_average.desc",
            ),
        )
        assertEquals(
            TmdbDiscover.TOP_RATED_VOTE_COUNT_GTE.toString(),
            params["vote_count.gte"],
        )
        assertEquals("7.0", params["vote_average.gte"])
    }

    @Test
    fun `popularity sort carries no implied floors`() {
        val params = TmdbDiscover.queryParams(
            MediaType.TV,
            TmdbDiscover.DiscoverQuery(sortBy = "popularity.desc"),
        )
        assertEquals("popularity.desc", params["sort_by"])
        assertFalse("vote_count.gte" in params)
        assertFalse("vote_average.gte" in params)
    }
}
