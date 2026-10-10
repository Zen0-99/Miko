package eu.kanade.presentation.entries.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.tachiyomi.data.mikodb.ProviderFinder
import eu.kanade.tachiyomi.data.mikodb.ProviderFinder.RecommendedExtension
import eu.kanade.tachiyomi.extension.InstallStep
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clickableNoIndication

/**
 * Find Content Provider screen (17-02, cover-viewer redesign) — shown
 * when a canonical work has zero bound carriers. No longer a sheet: a
 * full-screen dialog in the cover viewer's visual language — a dark
 * scrim, "Content providers" heading with the entry's title beneath
 * it, each found provider paged horizontally (pages narrower than the
 * viewport so the next result peeks in) with its artwork floating
 * mid-screen carrying extension name, entry title + count. Tapping
 * the cover darkens it + drops the accent checkmark (the mint-pick
 * toggle); X dismisses, V confirms the picks and closes instantly —
 * the mint completes async.
 */
@Composable
fun FindProviderDialog(
    /** The entry's own title — the heading reads "X, content providers". */
    entryTitle: String,
    searching: Boolean,
    rows: List<ProviderFinder.Row>,
    confirmed: Set<String>,
    recommendations: List<RecommendedExtension>,
    installSteps: Map<String, InstallStep>,
    accentColor: Color? = null,
    onToggle: (String) -> Unit,
    onInstall: (String) -> Unit,
    /** 20-06: restore a locally-removed provider (clears the carrier
     *  exclusion + retracts the remove signal) — only ever offered on
     *  `previouslyRemoved` rows; null hides the affordance. */
    onRestore: ((String) -> Unit)? = null,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    // Controls take the detail-view accent, not theme primary — the
    // screen belongs to the entry's visual family.
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = accent)

    // Only providers with an actual match get a page — a source that
    // found nothing is noise, not a result. Pages stream in as the
    // per-extension searches resolve.
    val resultRows = rows.filter {
        it.status == ProviderFinder.Status.FOUND && !it.previouslyRemoved
    }
    val removedRows = rows.filter { it.previouslyRemoved }
    val pagerState = rememberPagerState { resultRows.size }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        EdgeToEdgeDialogWindow()
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .navigationBarsPadding(),
                ) {
                    // X on the left — dismiss without minting anything.
                    ActionsPill {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(MR.strings.action_close),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // V on the right — mint the checked providers. The
                    // screen closes on tap: the mint runs async behind
                    // it, waiting on it reads as "the click did nothing".
                    ActionsPill {
                        IconButton(
                            onClick = {
                                onConfirm()
                                onDismissRequest()
                            },
                            enabled = confirmed.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = stringResource(MR.strings.action_apply),
                                tint = if (confirmed.isNotEmpty()) {
                                    accent
                                } else {
                                    LocalContentColor.current.copy(alpha = 0.38f)
                                },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Scrim before inset padding — covers the status/nav
                    // strips of the dialog window too (cover viewer's).
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(contentPadding)
                    .clickableNoIndication(onClick = onDismissRequest),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    // Heading above everything — "Content providers",
                    // the entry's title on its own line underneath in
                    // the muted scrim tone the status text uses.
                    Text(
                        text = stringResource(AYMR.strings.content_providers),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Text(
                        text = entryTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = 24.dp,
                            end = 24.dp,
                            bottom = 12.dp,
                        ),
                    )

                    if (searching) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = accent,
                            )
                            Text(
                                text = stringResource(AYMR.strings.find_provider_searching),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }

                    // Center: one floating-cover page per result.
                    if (resultRows.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            // Chapter-carousel geometry: the page is
                            // 0.75 × screen width so both neighbors peek
                            // in beside the viewed card.
                            val screenW = LocalConfiguration.current.screenWidthDp.dp
                            val pageW = screenW * 0.75f
                            HorizontalPager(
                                state = pagerState,
                                pageSize = PageSize.Fixed(pageW),
                                pageSpacing = 12.dp,
                                contentPadding = PaddingValues(
                                    horizontal = (screenW - pageW) / 2,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) { page ->
                                val row = resultRows[page]
                                ProviderResultPage(
                                    row = row,
                                    accent = accent,
                                    checked = row.pkgName in confirmed,
                                    onToggle = { onToggle(row.pkgName) },
                                )
                            }
                            if (resultRows.size > 1) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(vertical = 8.dp),
                                ) {
                                    repeat(resultRows.size) { index ->
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (index == pagerState.currentPage) {
                                                        accent
                                                    } else {
                                                        Color.White.copy(alpha = 0.35f)
                                                    },
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!searching) {
                                Text(
                                    text = stringResource(AYMR.strings.find_provider_none),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )
                            }
                        }
                    }

                    // Removed providers + recommended installs live in a
                    // floating bottom card — same pill language as the
                    // actions, readable content colors inside.
                    if (removedRows.isNotEmpty() || recommendations.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                )
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            removedRows.forEach { row ->
                                ProviderRow(
                                    row = row,
                                    accent = accent,
                                    checked = false,
                                    onToggle = { },
                                    onRestore = onRestore?.let { restore ->
                                        { restore(row.pkgName) }
                                    },
                                )
                            }
                            if (recommendations.isNotEmpty()) {
                                Text(
                                    text = stringResource(AYMR.strings.find_provider_recommended),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(
                                        start = 4.dp,
                                        top = 8.dp,
                                        bottom = 4.dp,
                                    ),
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
                        }
                    }
                }
            }
        }
    }
}

/**
 * One provider's page — the cover-viewer composition: the matched
 * artwork floats large mid-screen carrying its own info (extension
 * name + icon top-left, the provider's entry title beneath it, the
 * chapter/episode count bottom-right). Match notes ("Uncertain match",
 * "Missing N", the reason) sit outside the card underneath. Tapping
 * the cover darkens it and drops the accent check — the mint pick.
 */
@Composable
private fun ProviderResultPage(
    row: ProviderFinder.Row,
    accent: Color,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.84f)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onToggle),
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(row.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = row.foundTitle,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                // Scrim top + bottom — the in-card text reads over any
                // artwork, bright or dark.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.6f),
                                0.35f to Color.Transparent,
                                0.75f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.6f),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExtensionIcon(
                            iconDrawable = row.icon,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = row.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f, fill = false),
                        )
                        row.points?.let {
                            Text(
                                text = stringResource(
                                    AYMR.strings.find_provider_points,
                                    it,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Text(
                        text = row.foundTitle ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (row.communityVerified) {
                        Text(
                            text = stringResource(
                                MR.strings.miko_provider_community_verified,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                val countLabel = row.chapterCount.takeIf { it > 0 }?.let {
                    stringResource(
                        if (row.medium == "shows") {
                            AYMR.strings.find_provider_episodes
                        } else {
                            AYMR.strings.find_provider_chapters
                        },
                        it,
                    )
                }
                if (countLabel != null) {
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp),
                    )
                }
                if (checked) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                    )
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp),
                    )
                }
            }
        }

        // Match notes live outside the card — the count moved inside
        // it. The slot is fixed-height: a warning appearing mid-search
        // must not push the floating cover upward.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (row.tentative || row.missingCount > 0) {
                Text(
                    text = buildAnnotatedString {
                        var first = true
                        fun appendSep() {
                            if (!first) append(" · ")
                            first = false
                        }
                        if (row.tentative) {
                            appendSep()
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                                append(stringResource(AYMR.strings.find_provider_uncertain))
                            }
                            row.reason?.let { reason ->
                                findProviderReasonText(reason)?.let {
                                    appendSep()
                                    append(it)
                                }
                            }
                        }
                        if (row.missingCount > 0) {
                            appendSep()
                            append(
                                stringResource(
                                    AYMR.strings.find_provider_missing,
                                    row.missingCount,
                                ),
                            )
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
    onRestore: (() -> Unit)? = null,
) {
    // The removed-provider row: restore affordance + the ledger's
    // global "pts" reputation for context.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIcon(iconDrawable = row.icon, modifier = Modifier.size(28.dp))
        Text(
            text = row.displayName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f, fill = false),
        )
        if (row.points != null) {
            Text(
                text = stringResource(AYMR.strings.find_provider_points, row.points),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(MR.strings.miko_carrier_removed_by_you),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (onRestore != null) {
            TextButton(onClick = onRestore) {
                Text(
                    text = stringResource(MR.strings.miko_carrier_restore),
                    color = accent,
                )
            }
        }
    }
}

/**
 * Minimal-copy scorecard segment (19-05, D-08): maps the matcher's
 * stable `dominantReason` key to one fixed string — unknown/new keys
 * render nothing (null-safe). Scores and machine codes never reach
 * the screen; the verbose verdict stays in logcat.
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
private fun ActionsPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
    ) {
        content()
    }
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
                    ext.points?.let { stringResource(AYMR.strings.find_provider_points, it) },
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
