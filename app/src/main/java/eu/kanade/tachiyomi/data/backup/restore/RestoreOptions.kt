package eu.kanade.tachiyomi.data.backup.restore

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

data class RestoreOptions(
    val libraryEntries: Boolean = true,
    val collections: Boolean = true,
    val appSettings: Boolean = true,
    val extensionRepoSettings: Boolean = true,
    val customButtons: Boolean = true,
    val sourceSettings: Boolean = true,
    val extensions: Boolean = false,
) {

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        collections,
        appSettings,
        extensionRepoSettings,
        customButtons,
        sourceSettings,
        extensions,
    )

    fun canRestore() = libraryEntries ||
        collections ||
        appSettings ||
        extensionRepoSettings ||
        customButtons ||
        sourceSettings ||
        extensions

    companion object {
        val options = persistentListOf(
            Entry(
                label = MR.strings.label_library,
                getter = RestoreOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = MR.strings.collections,
                getter = RestoreOptions::collections,
                setter = { options, enabled -> options.copy(collections = enabled) },
            ),
            Entry(
                label = MR.strings.app_settings,
                getter = RestoreOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.extensionRepo_settings,
                getter = RestoreOptions::extensionRepoSettings,
                setter = { options, enabled -> options.copy(extensionRepoSettings = enabled) },
            ),
            Entry(
                label = AYMR.strings.custom_button_settings,
                getter = RestoreOptions::customButtons,
                setter = { options, enabled -> options.copy(customButtons = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = RestoreOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            Entry(
                label = MR.strings.label_extensions,
                getter = RestoreOptions::extensions,
                setter = { options, enabled -> options.copy(extensions = enabled) },
            ),
        )

        fun fromBooleanArray(array: BooleanArray) = RestoreOptions(
            libraryEntries = array[0],
            collections = array[1],
            appSettings = array[2],
            extensionRepoSettings = array[3],
            customButtons = array[4],
            sourceSettings = array[5],
            extensions = array[6],
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (RestoreOptions) -> Boolean,
        val setter: (RestoreOptions, Boolean) -> RestoreOptions,
    )
}
