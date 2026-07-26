package eu.kanade.presentation.achievement.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AchievementActivityGraph(
    yearlyStats: List<Pair<YearMonth, MonthStats>>,
    modifier: Modifier = Modifier,
) {
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()

    // Sort all months chronologically — single view, no pager
    val allMonths = yearlyStats.sortedBy { it.first.monthValue }

    // Calculate max time in app across all months for a unified scale
    val maxTimeMinutes = remember(yearlyStats) {
        yearlyStats.maxOfOrNull { it.second.timeInAppMinutes } ?: 0
    }.coerceAtLeast(1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(AYMR.strings.achievement_year_activity_title).uppercase(),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }

        // Single surface — matches ExtensionCard style
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp, start = 8.dp, end = 16.dp),
            ) {
                MonthLineChart(
                    months = allMonths,
                    maxTimeMinutes = maxTimeMinutes,
                    locale = locale,
                )
            }
        }
    }
}

/**
 * Line chart with X/Y axes showing time in app per month.
 *
 * - X axis: month abbreviations (Jan, Feb, …)
 * - Y axis: time in app (formatted as hours/minutes)
 * - Line connects data points with circles; area under the line is filled
 *   with a vertical gradient.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthLineChart(
    months: List<Pair<YearMonth, MonthStats>>,
    maxTimeMinutes: Int,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    if (months.isEmpty()) return

    // Animation
    var animationStarted by remember { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "line_animation",
    )

    LaunchedEffect(months) {
        animationStarted = true
    }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    // Y axis labels — 3 levels: max, mid, 0
    val maxLabel = formatTimeShort(maxTimeMinutes)
    val midLabel = formatTimeShort(maxTimeMinutes / 2)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        // Y axis labels
        Column(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .padding(end = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = maxLabel,
                color = onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = midLabel,
                color = onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = "0",
                color = onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }

        // Chart area + X axis labels
        Column(
            modifier = Modifier.weight(1f),
        ) {
            val chartHeight = 170.dp
            val xLabelHeight = 30.dp

            // Tooltip state
            var hoveredIndex by remember { mutableStateOf<Int?>(null) }
            val tooltipState = rememberTooltipState()
            val coroutineScope = rememberCoroutineScope()

            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        ),
                    ) {
                        hoveredIndex?.let { idx ->
                            if (idx < months.size) {
                                ActivityTooltipContent(
                                    month = months[idx].first,
                                    stats = months[idx].second,
                                    locale = locale,
                                )
                            }
                        }
                    }
                },
                state = tooltipState,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .pointerInput(months) {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    val segmentWidth = size.width / months.size
                                    val idx = (tapOffset.x / segmentWidth).toInt()
                                        .coerceIn(0, months.lastIndex)
                                    hoveredIndex = idx
                                    coroutineScope.launch {
                                        tooltipState.show()
                                    }
                                },
                            )
                        }
                        .semantics {
                            contentDescription = "Time in app line chart"
                        },
                ) {
                    val w = size.width
                    val h = size.height
                    val strokeWidth = 1.dp.toPx()
                    val gridColor = Color.White.copy(alpha = 0.04f)
                    val axisColor = onSurfaceVariant.copy(alpha = 0.3f)

                    // Horizontal grid lines (at 0%, 50%, 100%)
                    listOf(0f, 0.5f, 1f).forEach { fraction ->
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, h * (1f - fraction)),
                            end = Offset(w, h * (1f - fraction)),
                            strokeWidth = strokeWidth,
                        )
                    }

                    // Y axis line (left)
                    drawLine(
                        color = axisColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, h),
                        strokeWidth = strokeWidth,
                    )

                    // X axis line (bottom)
                    drawLine(
                        color = axisColor,
                        start = Offset(0f, h),
                        end = Offset(w, h),
                        strokeWidth = strokeWidth,
                    )

                    if (months.isEmpty()) return@Canvas

                    val segmentWidth = w / months.size

                    // Calculate data points
                    val points = months.mapIndexed { index, (_, stats) ->
                        val x = segmentWidth * index + segmentWidth / 2f
                        val fraction = if (maxTimeMinutes > 0) {
                            stats.timeInAppMinutes.toFloat() / maxTimeMinutes
                        } else {
                            0f
                        }
                        val animatedFraction = fraction * animationProgress
                        val y = h * (1f - animatedFraction.coerceIn(0f, 1f))
                        Offset(x, y)
                    }

                    // Build the line path
                    val linePath = Path().apply {
                        if (points.isNotEmpty()) {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                // Smooth curve using cubic bezier
                                val prev = points[i - 1]
                                val curr = points[i]
                                val midX = (prev.x + curr.x) / 2f
                                cubicTo(
                                    midX, prev.y,
                                    midX, curr.y,
                                    curr.x, curr.y,
                                )
                            }
                        }
                    }

                    // Build the area fill path (line + bottom corners)
                    val areaPath = Path().apply {
                        if (points.isNotEmpty()) {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val curr = points[i]
                                val midX = (prev.x + curr.x) / 2f
                                cubicTo(
                                    midX, prev.y,
                                    midX, curr.y,
                                    curr.x, curr.y,
                                )
                            }
                            // Close to bottom
                            lineTo(points.last().x, h)
                            lineTo(points.first().x, h)
                            close()
                        }
                    }

                    // Draw area fill (vertical gradient)
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.25f),
                                primary.copy(alpha = 0.0f),
                            ),
                            startY = 0f,
                            endY = h,
                        ),
                    )

                    // Draw the line
                    drawPath(
                        path = linePath,
                        color = primary,
                        style = Stroke(width = 2.dp.toPx()),
                    )

                    // Draw data point circles
                    points.forEachIndexed { index, point ->
                        val isHovered = hoveredIndex == index
                        val radius = if (isHovered) 5.dp.toPx() else 3.dp.toPx()
                        drawCircle(
                            color = primary,
                            radius = radius,
                            center = point,
                        )
                        // Inner dot
                        drawCircle(
                            color = Color.White,
                            radius = (radius * 0.4f),
                            center = point,
                        )
                    }
                }
            }

            // X axis labels (month abbreviations)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(xLabelHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                months.forEach { (month, _) ->
                    val monthLabel = formatMonthShortLabel(month, locale)
                    Text(
                        text = monthLabel.uppercase(),
                        color = onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Formats minutes as a short time string for axis labels.
 * e.g. 150 -> "2h", 45 -> "45m", 90 -> "1h"
 */
private fun formatTimeShort(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 -> "${hours}h"
        mins > 0 -> "${mins}m"
        else -> "0"
    }
}

@Composable
private fun ActivityTooltipContent(
    month: YearMonth,
    stats: MonthStats,
    locale: Locale,
) {
    val monthName = remember(month, locale) {
        formatMonthYearLabel(month, locale)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp),
    ) {
        // Month name
        Text(
            text = monthName,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Total activity
        if (stats.totalActivity > 0) {
            Text(
                text = stringResource(AYMR.strings.achievement_stat_total, stats.totalActivity),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
        }

        // Chapters
        if (stats.chaptersRead > 0) {
            Text(
                text = stringResource(AYMR.strings.achievement_stat_chapters, stats.chaptersRead),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }

        // Episodes
        if (stats.episodesWatched > 0) {
            Text(
                text = stringResource(AYMR.strings.achievement_stat_episodes, stats.episodesWatched),
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 11.sp,
            )
        }

        // Time in app
        if (stats.timeInAppMinutes > 0) {
            val hours = stats.timeInAppMinutes / 60
            val minutes = stats.timeInAppMinutes % 60
            val timeText = if (hours > 0) {
                stringResource(AYMR.strings.achievement_hours_minutes_alt, hours, minutes)
            } else {
                stringResource(AYMR.strings.achievement_minutes_alt, minutes)
            }
            Text(
                text = stringResource(AYMR.strings.achievement_stat_time, timeText),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }

        // Achievements
        if (stats.achievementsUnlocked > 0) {
            Text(
                text = stringResource(AYMR.strings.achievement_stat_achievements, stats.achievementsUnlocked),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                fontSize = 11.sp,
            )
        }

        // No activity message
        if (stats.totalActivity == 0) {
            Text(
                text = stringResource(AYMR.strings.achievement_no_activity),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * Calculates the total activity (chapters + episodes) for a month
 */
private val MonthStats.totalActivity: Int
    get() = chaptersRead + episodesWatched

internal fun formatMonthShortLabel(month: YearMonth, locale: Locale): String {
    return month.month.getDisplayName(TextStyle.SHORT, locale)
        .replace(".", "")
        .lowercase(locale)
        .take(3)
}

internal fun formatMonthYearLabel(month: YearMonth, locale: Locale): String {
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    return month.format(formatter)
        .replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase(locale)
            } else {
                char.toString()
            }
        }
}
