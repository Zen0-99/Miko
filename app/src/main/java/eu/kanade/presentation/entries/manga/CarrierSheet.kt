package eu.kanade.presentation.entries.manga

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.entries.components.accentTintedSurface
import eu.kanade.tachiyomi.data.mikodb.WorkCarriers
import eu.kanade.tachiyomi.extension.InstallStep
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Tinted-sheet background shared by the carrier/provider sheets (16-04/
 * 17-02) — the entry accent darkened+desaturated into a surface, the
 * same `bc` logic the chapter cards and comments button use.
 */
@Composable
internal fun carrierSheetContainerColor(accentColor: Color?): Color = accentTintedSurface(
    accent = accentColor ?: MaterialTheme.colorScheme.primary,
    background = MaterialTheme.colorScheme.background,
    dimmed = false,
)

/**
 * 16-04 carrier sheet — the "you need an extension" moment. Shown when
 * a canonical entry's chapter is tapped but no installed+enabled
 * carrier can serve it: lists the bound extensions the repo can
 * install, with live install progress. Bottom sheet, accent-tinted.
 */
@Composable
fun CarrierInstallDialog(
    matrix: WorkCarriers.Matrix?,
    steps: Map<String, InstallStep>,
    accentColor: Color? = null,
    onInstall: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = accent)
    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
        containerColor = carrierSheetContainerColor(accentColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(MR.strings.carrier_needed_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            val installable = matrix?.installable.orEmpty()
            if (installable.isEmpty()) {
                Text(
                    text = stringResource(MR.strings.carrier_none_known),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            } else {
                Text(
                    text = stringResource(MR.strings.carrier_needed_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                installable.forEach { carrier ->
                    CarrierInstallRow(
                        carrier = carrier,
                        step = steps[carrier.pkgName],
                        buttonColors = buttonColors,
                        onInstall = { onInstall(carrier.pkgName) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismissRequest, colors = buttonColors) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun CarrierInstallRow(
    carrier: WorkCarriers.Carrier,
    step: InstallStep?,
    buttonColors: ButtonColors,
    onInstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIcon(
            iconUrl = carrier.iconUrl,
            modifier = Modifier.size(40.dp),
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        ) {
            Text(
                text = carrier.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = carrier.lang?.uppercase() ?: carrier.pkgName.substringAfterLast('.'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val installed = step == InstallStep.Installed
        val busy = step != null && !step.isCompleted()
        TextButton(
            onClick = onInstall,
            enabled = !installed && !busy,
            colors = buttonColors,
        ) {
            Text(
                text = stringResource(
                    when {
                        installed -> MR.strings.ext_installed
                        busy -> MR.strings.ext_installing
                        else -> MR.strings.ext_install
                    },
                ),
            )
        }
    }
}

/**
 * Preferred-carrier picker for a canonical entry — "Automatic" plus the
 * installed+enabled carriers from the matrix. The router ranks the
 * selection above stickiness/tier. [chapter] non-null scopes the pick
 * to that chapter (the per-chapter override); null writes the
 * work-level default. Bottom sheet, accent-tinted.
 */
@Composable
fun CarrierPickerDialog(
    matrix: WorkCarriers.Matrix?,
    preferred: String?,
    chapter: tachiyomi.domain.items.chapter.model.Chapter? = null,
    accentColor: Color? = null,
    /** "Find More Providers" — opens the provider-search sheet to mint
     *  additional carriers for this work. Null hides the button. */
    onFindMoreProviders: (() -> Unit)? = null,
    onSelect: (String?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val readable = matrix?.readable.orEmpty()
    // Controls take the detail-view accent, not theme primary.
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = accent)
    val radioColors = RadioButtonDefaults.colors(
        selectedColor = accent,
        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
        containerColor = carrierSheetContainerColor(accentColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = chapter?.let { "${it.name} — " }.orEmpty() +
                    stringResource(MR.strings.carrier_preferred_source),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            CarrierRadioRow(
                label = stringResource(MR.strings.carrier_auto),
                selected = preferred == null,
                radioColors = radioColors,
                onClick = {
                    onSelect(null)
                    onDismissRequest()
                },
            )
            readable.forEach { carrier ->
                CarrierRadioRow(
                    label = carrier.displayName,
                    icon = carrier.icon,
                    iconUrl = carrier.iconUrl,
                    selected = preferred == carrier.pkgName,
                    radioColors = radioColors,
                    onClick = {
                        onSelect(carrier.pkgName)
                        onDismissRequest()
                    },
                )
            }
            if (onFindMoreProviders != null) {
                OutlinedButton(
                    onClick = onFindMoreProviders,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, accent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                ) {
                    Text(text = stringResource(tachiyomi.i18n.aniyomi.AYMR.strings.find_more_providers))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismissRequest, colors = buttonColors) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            }
        }
    }
}

/** Radio row for the carrier picker — RadioItem takes no colors, so
 *  this draws radio + logo + label directly with the entry accent. */
@Composable
private fun CarrierRadioRow(
    label: String,
    selected: Boolean,
    radioColors: RadioButtonColors,
    icon: Drawable? = null,
    iconUrl: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = radioColors,
        )
        if (icon != null || iconUrl != null) {
            ExtensionIcon(
                iconDrawable = icon,
                iconUrl = iconUrl,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(28.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
