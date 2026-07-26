package eu.kanade.presentation.collection

import android.content.Context
import androidx.compose.runtime.Composable
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.collection.model.Collection
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

val Collection.visualName: String
    @Composable
    get() = when {
        isSystemCollection -> stringResource(MR.strings.label_default)
        else -> name
    }

fun Collection.visualName(context: Context): String =
    when {
        isSystemCollection -> context.stringResource(MR.strings.label_default)
        else -> name
    }
