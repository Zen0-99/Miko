package eu.kanade.presentation.entries.manga

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
            // Revoked bindings render dimmed — dead content, never an
            // install candidate (Phase 20).
            matrix?.carriers.orEmpty()
                .filter { it.status == "quarantined" }
                .forEach { carrier ->
                    CarrierInstallRow(
                        carrier = carrier,
                        step = null,
                        buttonColors = buttonColors,
                        badge = stringResource(MR.strings.miko_binding_quarantined),
                        dimmed = true,
                        showAction = false,
                        onInstall = {},
                    )
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
    badge: String? = null,
    dimmed: Boolean = false,
    showAction: Boolean = true,
    onInstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (dimmed) Modifier.alpha(0.55f) else Modifier)
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
                text = listOfNotNull(
                    carrier.lang?.uppercase() ?: carrier.pkgName.substringAfterLast('.'),
                    badge,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showAction) {
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
}

/** Report-reason rows on the staged report page — keys are the
 *  worker-side `reason` taxonomy (20-05), labels localized. */
private val REPORT_REASONS = listOf(
    "wrong_work" to MR.strings.miko_carrier_reason_wrong_work,
    "dead_feed" to MR.strings.miko_carrier_reason_dead_feed,
    "low_quality" to MR.strings.miko_carrier_reason_low_quality,
    "other" to MR.strings.miko_carrier_reason_other,
)

private sealed interface CarrierPickerPage {
    data object Pick : CarrierPickerPage
    data class Report(val carrier: WorkCarriers.Carrier) : CarrierPickerPage
}

/**
 * Preferred-carrier picker for a canonical entry — "Automatic" plus the
 * installed+enabled carriers from the matrix. The router ranks the
 * selection above stickiness/tier. [chapter] non-null scopes the pick
 * to that chapter (the per-chapter override); null writes the
 * work-level default. Bottom sheet, accent-tinted.
 *
 * Every carrier row (Automatic excluded) carries a ⋯ button that
 * expands bare-icon actions Nuvio-detail-style — up/down the binding,
 * report it (staged page), or remove it for this work (20-06). The
 * provisional chip was dropped by the owner: lifecycle stays invisible.
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
    /** Vote slot toggle — 'up'|'down'|'clear' (20-06). */
    onVote: ((WorkCarriers.Carrier, String) -> Unit)? = null,
    /** Report submission from the staged page — (carrier, reason key). */
    onReport: ((WorkCarriers.Carrier, String) -> Unit)? = null,
    /** Local removal — excludes the provider for this work (20-06). */
    onRemove: ((WorkCarriers.Carrier) -> Unit)? = null,
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
    var page by remember { mutableStateOf<CarrierPickerPage>(CarrierPickerPage.Pick) }
    // Only one row's action strip can be expanded at a time.
    var expandedPkg by remember { mutableStateOf<String?>(null) }

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
        containerColor = carrierSheetContainerColor(accentColor),
    ) {
        val current = page
        when (current) {
            is CarrierPickerPage.Report -> CarrierReportPage(
                carrier = current.carrier,
                accent = accent,
                onSubmit = { kind ->
                    onReport?.invoke(current.carrier, kind)
                    page = CarrierPickerPage.Pick
                },
                onBack = { page = CarrierPickerPage.Pick },
            )
            CarrierPickerPage.Pick -> Column(
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
                    val expanded = expandedPkg == carrier.pkgName
                    CarrierRadioRow(
                        label = carrier.displayName,
                        icon = carrier.icon,
                        iconUrl = carrier.iconUrl,
                        selected = preferred == carrier.pkgName,
                        radioColors = radioColors,
                        trailing = {
                            CarrierActionStrip(
                                carrier = carrier,
                                expanded = expanded,
                                actionsEnabled = onVote != null,
                                onToggle = {
                                    expandedPkg = if (expanded) null else carrier.pkgName
                                },
                                onVote = onVote?.let { vote ->
                                    {
                                        // Tapping the active vote clears the slot.
                                        vote(carrier, if (carrier.myVote == it) "clear" else it)
                                    }
                                },
                                onReport = { page = CarrierPickerPage.Report(carrier) },
                                onRemove = onRemove?.let { { it(carrier) } },
                            )
                        },
                        onClick = {
                            onSelect(carrier.pkgName)
                            onDismissRequest()
                        },
                    )
                }
                // Revoked bindings render dimmed — visible so the matrix is
                // honest, but they offer no pick (Phase 20).
                matrix?.carriers.orEmpty()
                    .filter { it.status == "quarantined" }
                    .forEach { carrier ->
                        CarrierRadioRow(
                            label = carrier.displayName,
                            icon = carrier.icon,
                            iconUrl = carrier.iconUrl,
                            selected = false,
                            enabled = false,
                            badge = stringResource(MR.strings.miko_binding_quarantined),
                            radioColors = radioColors,
                            onClick = null,
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
}

/**
 * The ⋯ toggle plus its expandable bare-icon action strip — the Nuvio
 * detail-screen pattern without the circle fills: icons slide out
 * horizontally from the trailing edge. Up/down render filled when the
 * caller's vote slot sits there; the flag dims once reported.
 */
@Composable
private fun CarrierActionStrip(
    carrier: WorkCarriers.Carrier,
    expanded: Boolean,
    actionsEnabled: Boolean,
    onToggle: () -> Unit,
    /** Called with the target vote 'up'|'down' — the caller decides
     *  whether that's a set or a clear. */
    onVote: ((String) -> Unit)?,
    onReport: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(
                expandFrom = Alignment.End,
                animationSpec = tween(220),
            ) + fadeIn(tween(160)),
            exit = shrinkHorizontally(
                shrinkTowards = Alignment.End,
                animationSpec = tween(180),
            ) + fadeOut(tween(120)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalIcon(
                    image = if (carrier.myVote == "up") {
                        Icons.Filled.ThumbUp
                    } else {
                        Icons.Outlined.ThumbUp
                    },
                    desc = MR.strings.miko_carrier_upvote,
                    enabled = actionsEnabled,
                    onClick = { onVote?.invoke("up") },
                )
                SignalIcon(
                    image = if (carrier.myVote == "down") {
                        Icons.Filled.ThumbDown
                    } else {
                        Icons.Outlined.ThumbDown
                    },
                    desc = MR.strings.miko_carrier_downvote,
                    enabled = actionsEnabled,
                    onClick = { onVote?.invoke("down") },
                )
                SignalIcon(
                    image = Icons.Outlined.Flag,
                    desc = MR.strings.miko_carrier_report,
                    enabled = actionsEnabled && !carrier.myReported,
                    onClick = onReport,
                )
                if (onRemove != null) {
                    SignalIcon(
                        image = Icons.Outlined.RemoveCircleOutline,
                        desc = MR.strings.miko_carrier_remove,
                        enabled = actionsEnabled,
                        onClick = onRemove,
                    )
                }
                SignalIcon(
                    image = Icons.Outlined.KeyboardArrowLeft,
                    desc = MR.strings.miko_carrier_collapse,
                    onClick = onToggle,
                )
            }
        }
        if (!expanded) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(MR.strings.miko_carrier_actions),
                )
            }
        }
    }
}

@Composable
private fun SignalIcon(
    image: ImageVector,
    desc: dev.icerock.moko.resources.StringResource,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = image,
            contentDescription = stringResource(desc),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Staged page inside the picker sheet — reason list for a report
 *  against one carrier (20-06). Same staged-sheet pattern as the
 *  create-catalog flow. */
@Composable
private fun CarrierReportPage(
    carrier: WorkCarriers.Carrier,
    accent: Color,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(MR.strings.action_webview_back),
                    tint = accent,
                )
            }
            Text(
                text = stringResource(
                    MR.strings.miko_carrier_report_title,
                    carrier.displayName,
                ),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        REPORT_REASONS.forEach { (kind, label) ->
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSubmit(kind) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
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
    badge: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (enabled && onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier.alpha(0.55f)
                },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
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
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}
