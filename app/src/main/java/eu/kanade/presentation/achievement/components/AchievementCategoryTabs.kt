package eu.kanade.presentation.achievement.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AchievementCategoryTabs(
    selectedCategory: AchievementCategory,
    onCategoryChanged: (AchievementCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        AchievementCategory.BOTH to stringResource(AYMR.strings.achievement_category_all),
        AchievementCategory.ANIME to stringResource(AYMR.strings.achievement_category_anime),
        AchievementCategory.MANGA to stringResource(AYMR.strings.achievement_category_manga),
        AchievementCategory.NOVEL to stringResource(AYMR.strings.achievement_category_novel),
        AchievementCategory.SECRET to stringResource(AYMR.strings.achievement_category_secret),
    )

    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(40.dp),
    ) {
        val segmentWidth = maxWidth / tabs.size
        val selectedIndex = tabs.indexOfFirst { it.first == selectedCategory }.coerceAtLeast(0)
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "tab_indicator_offset",
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .fillMaxHeight()
                    .width(segmentWidth)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { (category, label) ->
                    val isSelected = category == selectedCategory
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tab_text_color",
                    )

                    val tabFontSize = 10.sp
                    val tabLetterSpacing = 0.5.sp
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onCategoryChanged(category) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            fontSize = tabFontSize,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = tabLetterSpacing,
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
