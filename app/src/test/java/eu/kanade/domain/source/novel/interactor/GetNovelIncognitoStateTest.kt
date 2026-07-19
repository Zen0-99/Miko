package eu.kanade.domain.source.novel.interactor

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.model.IncognitoPolicy
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class GetNovelIncognitoStateTest {

    private val preferenceStore = FakePreferenceStore()
    private val basePreferences = BasePreferences(
        context = mockk<Context>(relaxed = true),
        preferenceStore = preferenceStore,
    )
    private val sourcePreferences = SourcePreferences(preferenceStore)

    @Test
    fun `global incognito overrides extension state`() {
        val extensionManager = mockNovelExtensionManager(extensionPackage = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoNovelExtensions().set(setOf("plugin-a"))
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.NSFW_AUTO)
        basePreferences.incognitoMode().set(true)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.await(SOURCE_ID) shouldBe true
    }

    @Test
    fun `nsfw auto enables incognito for flagged source`() {
        val extensionManager = mockNovelExtensionManager(extensionPackage = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.NSFW_AUTO)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.await(SOURCE_ID) shouldBe true
    }

    @Test
    fun `per-extension incognito works in manual mode`() {
        val extensionManager = mockNovelExtensionManager(extensionPackage = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoNovelExtensions().set(setOf("plugin-a"))
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.MANUAL_ONLY)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.await(SOURCE_ID) shouldBe true
    }

    @Test
    fun `manual mode ignores nsfw without extension toggle`() {
        val extensionManager = mockNovelExtensionManager(extensionPackage = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.MANUAL_ONLY)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.await(SOURCE_ID) shouldBe false
    }

    @Test
    fun `nsfw auto pauses history outside library`() {
        val extensionManager = mockNovelExtensionManager(extensionPackage = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.NSFW_AUTO)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.shouldPauseHistory(SOURCE_ID, inLibrary = false) shouldBe true
    }

    @Test
    fun `library entry keeps history with nsfw auto`() {
        val extensionManager = mockNovelExtensionManager(extensionPackage = "plugin-a", isNsfw = true)
        sourcePreferences.incognitoPolicy().set(IncognitoPolicy.NSFW_AUTO)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.shouldPauseHistory(SOURCE_ID, inLibrary = true) shouldBe false
    }

    @Test
    fun `global incognito pauses history even in library`() {
        val extensionManager = mockNovelExtensionManager(extensionPackage = "plugin-a", isNsfw = false)
        basePreferences.incognitoMode().set(true)

        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.shouldPauseHistory(SOURCE_ID, inLibrary = true) shouldBe true
    }

    @Test
    fun `null source id only reflects global incognito`() {
        val extensionManager = mockNovelExtensionManager(extensionPackage = "plugin-a", isNsfw = true)
        val state = GetNovelIncognitoState(basePreferences, sourcePreferences, extensionManager)

        state.await(null) shouldBe false
        basePreferences.incognitoMode().set(true)
        state.await(null) shouldBe true
    }

    private fun mockNovelExtensionManager(
        extensionPackage: String,
        isNsfw: Boolean,
    ): NovelExtensionManager {
        val manager = mockk<NovelExtensionManager>()
        every { manager.getExtensionPackage(SOURCE_ID) } returns extensionPackage
        every { manager.getExtensionPackageAsFlow(SOURCE_ID) } returns MutableStateFlow(extensionPackage)
        every { manager.isNsfwForSource(SOURCE_ID) } returns isNsfw
        every { manager.isNsfwForSourceAsFlow(SOURCE_ID) } returns MutableStateFlow(isNsfw)
        return manager
    }

    private class FakePreferenceStore : PreferenceStore {
        private val booleans = mutableMapOf<String, Preference<Boolean>>()
        private val stringSets = mutableMapOf<String, Preference<Set<String>>>()
        private val objects = mutableMapOf<String, Preference<Any>>()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            error("Not used")

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            error("Not used")

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            error("Not used")

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            error("Not used")

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            booleans.getOrPut(key) { FakePreference(defaultValue) }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            stringSets.getOrPut(key) { FakePreference(defaultValue) }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> =
            objects.getOrPut(key) { FakePreference(defaultValue as Any) as Preference<Any> } as Preference<T>

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
        override fun stateIn(scope: kotlinx.coroutines.CoroutineScope) = state
    }

    private companion object {
        const val SOURCE_ID = 42L
    }
}
