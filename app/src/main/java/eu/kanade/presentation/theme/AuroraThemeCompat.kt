package eu.kanade.presentation.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.i18n.MR

/**
 * Compatibility shim for Tadami's AuroraTheme system.
 * Maps Aurora color concepts to Material3 color scheme values.
 * This allows achievement UI components ported from Tadami to work
 * with the aniyomi-fork's standard Material3 theming.
 */
@Immutable
data class AuroraColors(
    val accent: Color,
    val accentVariant: Color,
    val background: Color,
    val surface: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val glass: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnAccent: Color,
    val cardBackground: Color,
    val divider: Color,
    val isDark: Boolean,
    val isEInk: Boolean = false,
    val isAmoled: Boolean = false,
    // Aniview Premium specific colors
    val progressCyan: Color,
    val glowEffect: Color,
    val gradientPurple: Color,
    // Semantic colors for achievements and feedback
    val success: Color,
    val warning: Color,
    val error: Color,
    val achievementGold: Color,
    val ratingStar: Color = Color(0xFFFACC15),
    val ctaContentOnGlassDark: Color = Color(0xFFE2E8F0),
) {
    val backgroundGradient: Brush
        get() = Brush.verticalGradient(listOf(gradientStart, gradientEnd))

    val cardGradient: Brush
        get() = Brush.verticalGradient(
            listOf(
                gradientStart.copy(alpha = 0.85f),
                gradientEnd.copy(alpha = 0.95f),
                gradientEnd,
            ),
        )

    // Aniview gradient: electric blue to purple
    val aniviewGradient: Brush
        get() = Brush.horizontalGradient(
            listOf(
                glowEffect,
                gradientPurple,
            ),
        )

    companion object {
        /**
         * Creates AuroraColors dynamically from the selected ColorScheme.
         * This allows Aurora theme to adapt to user's selected accent color
         * while maintaining Aurora's unique gradient and glass aesthetics.
         */
        fun fromColorScheme(
            colorScheme: ColorScheme,
            isDark: Boolean,
            isAmoled: Boolean = false,
        ): AuroraColors {
            val effectiveBackground = if (isDark && isAmoled) {
                Color.Black
            } else {
                colorScheme.background
            }

            val effectiveSurface = if (isDark && isAmoled) {
                Color(0xFF0C0C0C)
            } else {
                effectiveBackground
            }

            val gradientStart = if (isDark) {
                colorScheme.primary.copy(alpha = 0.15f).compositeOver(effectiveBackground)
            } else {
                colorScheme.primary.copy(alpha = 0.12f).compositeOver(effectiveBackground)
            }

            val gradientEnd = effectiveBackground

            return AuroraColors(
                accent = colorScheme.primary,
                accentVariant = colorScheme.primaryContainer,
                background = effectiveBackground,
                surface = effectiveSurface,
                gradientStart = gradientStart,
                gradientEnd = gradientEnd,
                glass = if (isDark) {
                    Color.White.copy(alpha = 0.22f)
                } else {
                    Color(0xE6FFFFFF)
                },
                textPrimary = colorScheme.onBackground,
                textSecondary = colorScheme.onSurfaceVariant,
                textOnAccent = colorScheme.onPrimary,
                cardBackground = if (isDark) {
                    Color.White.copy(alpha = 0.12f)
                } else {
                    colorScheme.surfaceContainerHigh
                },
                divider = colorScheme.outlineVariant,
                isDark = isDark,
                isEInk = false,
                isAmoled = isAmoled,
                progressCyan = colorScheme.secondary,
                glowEffect = colorScheme.primary,
                gradientPurple = colorScheme.tertiary,
                success = if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E),
                warning = if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B),
                error = if (isDark) Color(0xFFF87171) else Color(0xFFEF4444),
                achievementGold = Color(0xFFFFB800),
                ratingStar = Color(0xFFFACC15),
            )
        }

        // Default dark theme colors
        val Dark = AuroraColors(
            accent = Color(0xFF6C8AE0),
            accentVariant = Color(0xFF3A4A6B),
            background = Color(0xFF0F1115),
            surface = Color(0xFF0F1115),
            gradientStart = Color(0xFF1A1D24),
            gradientEnd = Color(0xFF0F1115),
            glass = Color.White.copy(alpha = 0.22f),
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.7f),
            textOnAccent = Color.White,
            cardBackground = Color.White.copy(alpha = 0.12f),
            divider = Color.White.copy(alpha = 0.1f),
            isDark = true,
            isEInk = false,
            isAmoled = false,
            progressCyan = Color(0xFF4ECDC4),
            glowEffect = Color(0xFF6C8AE0),
            gradientPurple = Color(0xFF9B59B6),
            success = Color(0xFF4ADE80),
            warning = Color(0xFFFBBF24),
            error = Color(0xFFF87171),
            achievementGold = Color(0xFFFFB800),
            ratingStar = Color(0xFFFACC15),
        )

        // Default light theme colors
        val Light = AuroraColors(
            accent = Color(0xFF4A6FE3),
            accentVariant = Color(0xFFD6E0FF),
            background = Color(0xFFF8F9FA),
            surface = Color(0xFFF1F3F5),
            gradientStart = Color(0xFFF2F2F5),
            gradientEnd = Color(0xFFF8F9FA),
            glass = Color(0xE6FFFFFF),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            textOnAccent = Color.White,
            cardBackground = Color(0xFFF0F2F4),
            divider = Color(0xFFD0D4D8),
            isDark = false,
            isEInk = false,
            isAmoled = false,
            progressCyan = Color(0xFF4ECDC4),
            glowEffect = Color(0xFF4A6FE3),
            gradientPurple = Color(0xFF6366F1),
            success = Color(0xFF22C55E),
            warning = Color(0xFFF59E0B),
            error = Color(0xFFEF4444),
            achievementGold = Color(0xFFFFB800),
            ratingStar = Color(0xFFFACC15),
        )
    }
}

/** CompositionLocal for providing AuroraColors throughout the composition tree. */
val LocalAuroraColors = staticCompositionLocalOf { AuroraColors.Dark }

/**
 * AuroraTheme object providing access to the current AuroraColors.
 * In the aniyomi-fork, this maps to Material3 color scheme values.
 */
object AuroraTheme {
    val colors: AuroraColors
        @Composable
        get() = LocalAuroraColors.current

    @Composable
    fun colorsForCurrentTheme(): AuroraColors {
        return AuroraColors.fromColorScheme(
            colorScheme = MaterialTheme.colorScheme,
            isDark = androidx.compose.foundation.isSystemInDarkTheme(),
        )
    }
}

/**
 * Simple top bar layout compatible with Tadami's AuroraTopBarLayout.
 * Provides a standard top bar with optional back navigation and actions.
 */
@Composable
fun AuroraTopBarLayout(
    title: String,
    titleContent: (@Composable () -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onNavigateUp != null) {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(MR.strings.action_bar_up_description),
                    tint = AuroraTheme.colors.textPrimary,
                )
            }
        }

        if (titleContent != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (onNavigateUp != null) 12.dp else 4.dp,
                        end = 12.dp,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                titleContent()
            }
        } else {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (onNavigateUp != null) 12.dp else 4.dp,
                        end = 12.dp,
                    ),
                color = AuroraTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}


