package eu.kanade.tachiyomi.data.catalogrows

import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class CatalogRowStoreTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun spec(id: String, order: Int, feed: Feed = Feed.Tmdb("TV")) =
        CatalogRowSpec(
            id = id,
            medium = WorkIdResolver.MEDIUM_SHOWS,
            feed = feed,
            order = order,
        )

    @Test
    fun `spec list round-trips through json with sealed feed polymorphism`() {
        val specs = listOf(
            CatalogRowSpec(
                id = "a",
                title = "My row",
                medium = WorkIdResolver.MEDIUM_SHOWS,
                feed = Feed.Tmdb("MOVIE"),
                genreIds = listOf(28, 12),
                keywordIds = listOf(201712),
                watchProviderIds = listOf(8, 337),
                yearFrom = 2010,
                voteAverageGte = 7.0,
                sort = "RATING",
                order = 0,
            ),
            spec("b", 1, feed = Feed.Cinemeta("series", "top")),
            spec("c", 2, feed = Feed.Canonical("ol")),
            spec("d", 3, feed = Feed.Extension("pkg.x", 42L, ExtKind.LATEST)),
            spec("e", 4, feed = Feed.Library),
        )
        val decoded = json.decodeFromString<List<CatalogRowSpec>>(json.encodeToString(specs))
        assertEquals(specs, decoded)
    }

    @Test
    fun `decode sorts by order then id`() {
        // Seed raw JSON with a colliding order — decode must sort
        // deterministically by (order, id), not by written sequence.
        val seeded = json.encodeToString(
            listOf(spec("b", 0), spec("a", 0), spec("c", 1)),
        )
        val store = CatalogRowStore(FakePreferenceStore(seeded), json)
        assertEquals(listOf("a", "b", "c"), store.rows.value.map { it.id })
    }

    @Test
    fun `upsert appends new specs at max order plus one`() {
        val store = CatalogRowStore(FakePreferenceStore(), json)
        store.upsert(spec("a", 0))
        store.upsert(spec("b", 5)) // incoming order ignored for new specs
        assertEquals(listOf(0, 1), store.rows.value.map { it.order })
    }

    @Test
    fun `upsert edit keeps the existing order`() {
        val store = CatalogRowStore(FakePreferenceStore(), json)
        store.upsert(spec("a", 0))
        store.upsert(spec("b", 0))
        store.upsert(spec("b", 99, feed = Feed.Library))
        assertEquals(listOf("a", "b"), store.rows.value.map { it.id })
        assertEquals(Feed.Library, store.rows.value[1].feed)
    }

    @Test
    fun `reorder rewrites order fields in the given sequence`() {
        val store = CatalogRowStore(FakePreferenceStore(), json)
        store.upsert(spec("a", 0))
        store.upsert(spec("b", 0))
        store.upsert(spec("c", 0))
        store.reorder(listOf("c", "a"))
        assertEquals(listOf("c", "a", "b"), store.rows.value.map { it.id })
        assertEquals(listOf(0, 1, 2), store.rows.value.map { it.order })
    }

    @Test
    fun `setVisible toggles without touching order`() {
        val store = CatalogRowStore(FakePreferenceStore(), json)
        store.upsert(spec("a", 0))
        store.setVisible("a", false)
        assertEquals(false, store.rows.value.single().visible)
        assertEquals(0, store.rows.value.single().order)
    }

    @Test
    fun `corrupt json decodes to empty list`() {
        val store = CatalogRowStore(FakePreferenceStore("{not json"), json)
        assertTrue(store.rows.value.isEmpty())
    }

    @Test
    fun `contentKey ignores order and visibility`() {
        val store = CatalogRowStore(FakePreferenceStore(), json)
        val a = spec("x", 0)
        val b = spec("x", 7).copy(visible = false)
        assertEquals(store.contentKey(a), store.contentKey(b))
    }

    @Test
    fun `first run seeds a recents row per medium ahead of existing specs`() {
        val existing = json.encodeToString(listOf(spec("mine", 0)))
        val store = CatalogRowStore(FakePreferenceStore(existing, seeded = false), json)
        val history = store.rows.value.filter { it.feed is Feed.History }
        assertEquals(3, history.size)
        assertEquals(
            setOf(
                WorkIdResolver.MEDIUM_SHOWS,
                WorkIdResolver.MEDIUM_MANGA,
                WorkIdResolver.MEDIUM_BOOKS,
            ),
            history.map { it.medium }.toSet(),
        )
        // Seeds lead; the user's pre-existing row survives shifted after.
        assertEquals("mine", store.rows.value.last().id)
        assertEquals(3, store.rows.value.last().order)
    }

    @Test
    fun `deleted recents row does not re-seed across restarts`() {
        val prefs = FakePreferenceStore(seeded = false)
        val first = CatalogRowStore(prefs, json)
        val mangaRecents = first.rows.value.first {
            it.feed is Feed.History && it.medium == WorkIdResolver.MEDIUM_MANGA
        }
        first.delete(mangaRecents.id)
        val restarted = CatalogRowStore(prefs, json)
        assertEquals(2, restarted.rows.value.count { it.feed is Feed.History })
        assertTrue(restarted.rows.value.none { it.id == mangaRecents.id })
    }

    private class FakePreferenceStore(
        initial: String = "",
        seeded: Boolean = true,
    ) : PreferenceStore {
        private val string = FakePreference(initial)
        private val bool = FakePreference(seeded)

        override fun getString(key: String, defaultValue: String): Preference<String> = string
        override fun getLong(key: String, defaultValue: Long): Preference<Long> = error("Not used")
        override fun getInt(key: String, defaultValue: Int): Preference<Int> = error("Not used")
        override fun getFloat(key: String, defaultValue: Float): Preference<Float> = error("Not used")
        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = bool
        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> = error("Not used")
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = error("Not used")
        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    }

    private class FakePreference<T>(initial: T) : Preference<T> {
        private val state = MutableStateFlow(initial)
        override fun key(): String = "fake"
        override fun get(): T = state.value
        override fun set(value: T) {
            state.value = value
        }
        override fun isSet(): Boolean = true
        override fun delete() = Unit
        override fun defaultValue(): T = state.value
        override fun changes(): Flow<T> = state
        override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
    }
}
