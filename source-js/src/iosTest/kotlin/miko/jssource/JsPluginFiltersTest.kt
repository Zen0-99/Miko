package miko.jssource

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.source.model.Filter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * LNReader `__plugin.filters` descriptor → carrier FilterLists →
 * {key:{type,value}} values envelope — pure mapper tests plus a real
 * JSCore end-to-end where the plugin echoes the filters arg back.
 */
class JsPluginFiltersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val descriptor = """
        {
            "query": {
                "type": "Text",
                "label": "Title contains",
                "value": "abc"
            },
            "premium": {
                "type": "Switch",
                "label": "Premium only",
                "value": true
            },
            "order": {
                "type": "Picker",
                "label": "Order",
                "value": "new",
                "options": [
                    { "label": "Newest", "value": "new" },
                    { "label": "Popular", "value": "pop" }
                ]
            },
            "genres": {
                "type": "Checkbox",
                "label": "Genres",
                "value": ["action"],
                "options": [
                    { "label": "Action", "value": "action" },
                    { "label": "Drama", "value": "drama" }
                ]
            },
            "tags": {
                "type": "XCheckbox",
                "label": "Tags",
                "value": { "include": ["fast"], "exclude": ["slow"] },
                "options": [
                    { "label": "Fast", "value": "fast" },
                    { "label": "Slow", "value": "slow" },
                    { "label": "Odd", "value": "odd" }
                ]
            }
        }
    """.trimIndent()

    @Test
    fun defsParseAllTypes() {
        val defs = parseJsFilterDefs(descriptor, json)
        assertEquals(5, defs.size)
        assertEquals("query", defs[0].key)
        assertEquals("Title contains", defs[0].label)
        assertEquals(listOf("new", "pop"), defs[2].options.map { it.value })
    }

    @Test
    fun defsSkipMalformedEntries() {
        val defs = parseJsFilterDefs(
            """{"ok":{"type":"Text","label":"L","value":""},"bad":{"nope":1}}""",
            json,
        )
        assertEquals(1, defs.size)
    }

    @Test
    fun novelFiltersRoundTripState() {
        val filters = jsDefsToNovelFilterList(parseJsFilterDefs(descriptor, json))

        val text = filters[0]
        assertIs<NovelFilter.Text>(text)
        assertEquals("abc", text.state)

        val switch = filters[1]
        assertIs<NovelFilter.Switch>(switch)
        assertEquals(true, switch.state)

        val picker = filters[2]
        assertIs<NovelFilter.Select<*>>(picker)
        assertEquals(0, picker.state) // default "new" → index 0

        val checkGroup = filters[3]
        assertIs<NovelFilter.Group<*>>(checkGroup)
        val boxes = checkGroup.state.filterIsInstance<NovelFilter.CheckBox>()
        assertEquals(listOf(true, false), boxes.map { it.state })

        val triGroup = filters[4]
        assertIs<NovelFilter.Group<*>>(triGroup)
        val tris = triGroup.state.filterIsInstance<NovelFilter.TriState>()
        assertEquals(
            listOf(
                NovelFilter.TriState.STATE_INCLUDE,
                NovelFilter.TriState.STATE_EXCLUDE,
                NovelFilter.TriState.STATE_IGNORE,
            ),
            tris.map { it.state },
        )

        // Defaults serialize to the plugin's {key:{type,value}} envelope.
        val defaults = novelFilterValuesJson(filters)
        assertTrue("\"order\":{\"type\":\"Picker\",\"value\":\"new\"}" in defaults)
        assertTrue("\"premium\":{\"type\":\"Switch\",\"value\":true}" in defaults)
        assertTrue("\"query\":{\"type\":\"Text\",\"value\":\"abc\"}" in defaults)
        assertTrue("\"genres\":{\"type\":\"Checkbox\",\"value\":[\"action\"]}" in defaults)
        assertTrue(
            "\"tags\":{\"type\":\"XCheckbox\",\"value\":{\"include\":[\"fast\"],\"exclude\":[\"slow\"]}}" in
                defaults,
        )

        // Mutations flow back through the same list.
        text.state = "xyz"
        picker.state = 1
        val mutated = novelFilterValuesJson(filters)
        assertTrue("\"query\":{\"type\":\"Text\",\"value\":\"xyz\"}" in mutated)
        assertTrue("\"order\":{\"type\":\"Picker\",\"value\":\"pop\"}" in mutated)
    }

    @Test
    fun mangaFiltersMapSwitchAndTriState() {
        val filters = jsDefsToMangaFilterList(parseJsFilterDefs(descriptor, json))

        // Switch → CheckBox carrying jsType "Switch" for the envelope.
        assertIs<Filter.CheckBox>(filters[1])
        val values = mangaFilterValuesJson(filters)
        assertTrue("\"premium\":{\"type\":\"Switch\",\"value\":true}" in values)
        assertTrue("\"order\":{\"type\":\"Picker\",\"value\":\"new\"}" in values)

        // XCheckbox → TriState group.
        val triGroup = filters[4]
        assertIs<Filter.Group<*>>(triGroup)
        val tris = triGroup.state.filterIsInstance<Filter.TriState>()
        assertEquals(Filter.TriState.STATE_EXCLUDE, tris[1].state)
    }

    @Test
    fun animeFiltersMapSwitchAndTriState() {
        val filters = jsDefsToAnimeFilterList(parseJsFilterDefs(descriptor, json))
        assertIs<AnimeFilter.CheckBox>(filters[1])
        val values = animeFilterValuesJson(filters)
        assertTrue("\"premium\":{\"type\":\"Switch\",\"value\":true}" in values)
        val triGroup = filters[4]
        assertIs<AnimeFilter.Group<*>>(triGroup)
    }

    @Test
    fun filterEnvelopeReachesPluginVerbatim() = runBlocking {
        // The plugin echoes the raw filters arg back as a novel title —
        // proves the {key:{type,value}} envelope survives the JS boundary.
        val script = """
            module.exports = {
                filters: {
                    flag: { type: "Switch", label: "Flag", value: false },
                    mode: {
                        type: "Picker",
                        label: "Mode",
                        value: "a",
                        options: [
                            { label: "A", value: "a" },
                            { label: "B", value: "b" }
                        ]
                    }
                },
                searchNovels: function(query, page, filters) {
                    return Promise.resolve({
                        novels: [
                            {
                                path: "/n/echo",
                                name: JSON.stringify(filters),
                                cover: ""
                            }
                        ],
                        hasNextPage: false
                    });
                }
            };
        """.trimIndent()

        val source = IosNovelJsSource(
            runtimeProvider = { IosJsRuntimeFactory().create("test-filters-src") },
            script = script,
            pluginId = "test-filters-src",
            json = json,
            id = 13L,
            name = "Filtered",
            lang = "en",
        )
        try {
            val filters = source.getFilterList()
            assertEquals(2, filters.size)
            val switch = filters[0]
            assertIs<NovelFilter.Switch>(switch)
            switch.state = true
            val picker = filters[1]
            assertIs<NovelFilter.Select<*>>(picker)
            picker.state = 1

            val page = source.getSearchNovels(1, "q", filters)
            val echoed = page.novels[0].title
            assertTrue("\"flag\":{\"type\":\"Switch\",\"value\":true}" in echoed)
            assertTrue("\"mode\":{\"type\":\"Picker\",\"value\":\"b\"}" in echoed)
        } finally {
            source.shutdown()
        }
    }
}
