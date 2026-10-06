package eu.kanade.presentation.entries.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.entries.manga.carrierSheetContainerColor
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.tachiyomi.data.mikodb.ProviderFinder
import eu.kanade.tachiyomi.data.mikodb.ProviderFinder.RecommendedExtension
import eu.kanade.tachiyomi.extension.InstallStep
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Find Content Provider sheet (17-02) — shown when a canonical work has
 * zero bound carriers. Streams per-extension search status, lets the
 * user confirm which found entries are actually this work (each confirm
 * mints a `user_picked` binding), and below suggests top-tier
 * not-installed extensions likely to carry it.
 */
@Composable
fun FindProviderDialog(
    searching: Boolean,
    rows: List<ProviderFinder.Row>,
    confirmed: Set<String>,
    recommendations: List<RecommendedExtension>,
    installSteps: Map<String, InstallStep>,
    accentColor: Color? = null,
    onToggle: (String) -> Unit,
    onInstall: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    // Controls take the detail-view accent, not theme primary — the
    // sheet belongs to the entry's visual family.
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
                // Expanded sheets slide under the status bar — the
                // title must clear the clock/battery row.
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(AYMR.strings.find_content_provider),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )

            // Per-extension results stream in as sources resolve.
            rows.forEach { row ->
                ProviderRow(
                    row = row,
                    accent = accent,
                    checked = row.pkgName in confirmed,
                    onToggle = { onToggle(row.pkgName) },
                )
            }
            if (!searching && rows.none { it.status == ProviderFinder.Status.FOUND }) {
                Text(
                    text = stringResource(AYMR.strings.find_provider_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }

            // Recommended installs — ranked likelihood, not a
            // confirmed mapping.
            if (recommendations.isNotEmpty()) {
                Text(
                    text = stringResource(AYMR.strings.find_provider_recommended),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                )
                recommendations.forEach { ext ->
                    RecommendedRow(
                        ext = ext,
                        step = installSteps[ext.pkgName],
                        buttonColors = buttonColors,
                        onInstall = { onInstall(ext.pkgName) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismissRequest, colors = buttonColors) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onConfirm,
                    enabled = confirmed.isNotEmpty(),
                    colors = buttonColors,
                ) {
                    Text(text = stringResource(AYMR.strings.find_provider_confirm, confirmed.size))
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    row: ProviderFinder.Row,
    accent: Color,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    // Global-search convention: the extension name is the row header;
    // a FOUND result renders as the matched work's cover card, not an
    // extension logo — the artwork is the fastest "is this the right
    // series" signal (fan edits/fakes show instantly).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Phase 20-04: the worker's ledger promoted this provider
            // past its surveyed manifest tier — a real trust mark,
            // never shown for equal or demoted overlays.
            if (row.communityVerified) {
                Text(
                    text = stringResource(MR.strings.miko_provider_community_verified),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            // Phase 20: a binding this device just minted is provisional
            // until the ledger confirms it — honest state on the row.
            if (row.provisionalMint) {
                Text(
                    text = stringResource(MR.strings.miko_binding_provisional),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            when (row.status) {
                ProviderFinder.Status.SEARCHING ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = accent,
                    )
                ProviderFinder.Status.FOUND ->
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = accent,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkmarkColor = rememberOnAccentColor(accent),
                        ),
                    )
                ProviderFinder.Status.NO_MATCH,
                ProviderFinder.Status.FAILED,
                -> Unit
            }
        }
        when (row.status) {
            ProviderFinder.Status.FOUND -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(row.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .width(48.dp)
                            .height(70.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentScale = ContentScale.Crop,
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = row.foundTitle ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Status chain: "N episodes/chapters" →
                        // "uncertain match" (error-colored segment only)
                        // → "missing M" → the one minimal-copy reason.
                        // The reason is meta text, never error-colored.
                        val countLabel = if (row.chapterCount > 0) {
                            stringResource(
                                if (row.medium == "shows") {
                                    AYMR.strings.find_provider_episodes
                                } else {
                                    AYMR.strings.find_provider_chapters
                                },
                                row.chapterCount,
                            )
                        } else {
                            null
                        }
                        val missingLabel = row.missingCount
                            .takeIf { it > 0 }
                            ?.let { stringResource(AYMR.strings.find_provider_missing, it) }
                        val uncertainLabel = if (row.tentative) {
                            stringResource(AYMR.strings.find_provider_uncertain)
                        } else {
                            null
                        }
                        val reasonLabel = if (row.tentative) {
                            row.reason?.let { findProviderReasonText(it) }
                        } else {
                            null
                        }
                        Text(
                            text = buildAnnotatedString {
                                var first = true
                                fun appendSep() {
                                    if (!first) append(" · ")
                                    first = false
                                }
                                countLabel?.let { appendSep(); append(it) }
                                uncertainLabel?.let {
                                    appendSep()
                                    withStyle(
                                        SpanStyle(color = MaterialTheme.colorScheme.error),
                                    ) {
                                        append(it)
                                    }
                                }
                                missingLabel?.let { appendSep(); append(it) }
                                reasonLabel?.let { appendSep(); append(it) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            else -> {
                Text(
                    text = when (row.status) {
                        ProviderFinder.Status.SEARCHING ->
                            stringResource(AYMR.strings.find_provider_searching)
                        ProviderFinder.Status.NO_MATCH -> listOfNotNull(
                            stringResource(AYMR.strings.find_provider_no_match),
                            row.foundTitle?.let {
                                stringResource(AYMR.strings.find_provider_closest, it)
                            },
                            row.reason?.let { findProviderReasonText(it) },
                        ).joinToString(" · ")
                        else -> stringResource(
                            when (row.error) {
                                "timeout" -> AYMR.strings.find_provider_timed_out
                                "unavailable" -> AYMR.strings.find_provider_unavailable
                                else -> AYMR.strings.find_provider_failed
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Minimal-copy scorecard segment (19-05, D-08): maps the matcher's
 * stable `dominantReason` key to one fixed string — unknown/new keys
 * render nothing (null-safe). Scores and machine codes never reach
 * the sheet; the verbose verdict stays in logcat.
 */
@Composable
private fun findProviderReasonText(key: String): String? = when (key) {
    "season_veto" -> stringResource(AYMR.strings.find_provider_reason_season_veto)
    "part_veto" -> stringResource(AYMR.strings.find_provider_reason_part_veto)
    "sequel" -> stringResource(AYMR.strings.find_provider_reason_sequel)
    "spinoff" -> stringResource(AYMR.strings.find_provider_reason_spinoff)
    "format" -> stringResource(AYMR.strings.find_provider_reason_format)
    "year" -> stringResource(AYMR.strings.find_provider_reason_year)
    "media_type" -> stringResource(AYMR.strings.find_provider_reason_media_type)
    "weak_title" -> stringResource(AYMR.strings.find_provider_reason_weak_title)
    "thin_margin" -> stringResource(AYMR.strings.find_provider_reason_thin_margin)
    "subtitle_tail" -> stringResource(AYMR.strings.find_provider_reason_subtitle_tail)
    else -> null
}

@Composable
private fun RecommendedRow(
    ext: RecommendedExtension,
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
        ExtensionIcon(iconUrl = ext.iconUrl, modifier = Modifier.size(40.dp))
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        ) {
            Text(text = ext.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(
                    ext.tier.takeIf { it.isNotBlank() }
                        ?.let { stringResource(AYMR.strings.find_provider_tier, it) },
                    ext.lang?.uppercase(),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val installed = step == InstallStep.Installed
        val busy = step != null && !step.isCompleted()
        TextButton(onClick = onInstall, enabled = !installed && !busy, colors = buttonColors) {
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
