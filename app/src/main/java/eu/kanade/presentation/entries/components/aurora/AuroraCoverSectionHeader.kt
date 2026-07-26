package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Section header used above cover carousels in the Aurora entry layout.
 *
 * Ported from Tadami's AuroraCoverSectionHeader, adapted for aniyomi-fork's
 * Material3 theming (no auroraSpringClick — uses standard clickable).
 */
@Composable
fun AuroraCoverSectionHeader(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    count: String? = null,
    showChevron: Boolean = false,
    onChevronClick: (() -> Unit)? = null,
    accentColor: Color? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    // Cover-derived accent (from detail screen) overrides the global theme accent
    val effectiveAccent = accentColor ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (isDark) {
                                Color.White.copy(alpha = 0.08f)
                            } else {
                                effectiveAccent
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDark) effectiveAccent else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                text = title,
                color = if (isDark) MaterialTheme.colorScheme.onBackground else Color(0xFF241A16),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            trailingContent?.invoke()

            if (count != null) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isDark) {
                                Color.White.copy(alpha = 0.08f)
                            } else {
                                Color.White.copy(alpha = 0.62f)
                            },
                            shape = RoundedCornerShape(100.dp),
                        )
                        .then(
                            if (!isDark) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.46f),
                                    shape = RoundedCornerShape(100.dp),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = count,
                        color = effectiveAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (showChevron && onChevronClick != null) {
                Box(
                    modifier = Modifier
                        .clickable(onClick = onChevronClick)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = effectiveAccent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}