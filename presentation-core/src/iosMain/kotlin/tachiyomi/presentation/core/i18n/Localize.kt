package tachiyomi.presentation.core.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.StringDesc

@Composable
@ReadOnlyComposable
actual fun stringResource(resource: StringResource): String {
    return StringDesc.Resource(resource).localized().fixed()
}

@Composable
@ReadOnlyComposable
actual fun stringResource(resource: StringResource, vararg args: Any): String {
    return StringDesc.ResourceFormatted(resource, *args).localized().fixed()
}

@Composable
@ReadOnlyComposable
actual fun pluralStringResource(resource: PluralsResource, count: Int): String {
    return StringDesc.Plural(resource, count).localized().fixed()
}

@Composable
@ReadOnlyComposable
actual fun pluralStringResource(resource: PluralsResource, count: Int, vararg args: Any): String {
    return StringDesc.PluralFormatted(resource, count, *args).localized().fixed()
}

// Same workaround as core:common's Android Localize for
// https://github.com/icerockdev/moko-resources/issues/337 — the moko
// formatting pipeline emits the escaped quotes on every target.
private fun String.fixed() =
    this.replace("""\""", """""")
