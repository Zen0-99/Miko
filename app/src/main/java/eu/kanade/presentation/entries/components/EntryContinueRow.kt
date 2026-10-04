package eu.kanade.presentation.entries.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standalone primary action block for entry detail views — no card chrome.
 * A single accent action bar flanked by boxed toggle buttons: favourite on
 * the left, mark-all on the right. One action = single zone; two = split
 * bar (Read | Listen).
 *
 * Zone text/icons and the toggles' on-state icons are contrast-aware
 * (black or white) via [rememberOnAccentColor], so cover-derived accents
 * stay legible.
 */
@Composable
fun EntryContinueRow(
    actions: List<EntryNextUpAction>,
    actionsEnabled: Boolean,
    accentColor: Color?,
    favorite: Boolean,
    allDone: Boolean,
    favoriteContentDescription: String,
    doneContentDescription: String,
    onFavoriteClick: () -> Unit,
    onToggleDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    /**
     * Shared-element key pairing the accent action bar with the home
     * hero's resume pill — the pill morphs width-wise into this bar on
     * push and back on pop while labels crossfade and the fill animates
     * between theme primary and the entry accent. The flanking toggles
     * stay unshared and fade with the screen.
     */
    sharedKey: Any? = null,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val onAccent = rememberOnAccentColor(accentColor)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleBoxAction(
            icon = favoriteActionIcon(favorite),
            contentDescription = favoriteContentDescription,
            active = favorite,
            activeColor = accent,
            onActiveColor = onAccent,
            onClick = onFavoriteClick,
        )
        Row(
            modifier = Modifier
                .then(heroSharedBoundsModifier(sharedKey, HeroSharedKind.Container))
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (actionsEnabled) {
                        accent
                    } else {
                        // Disabled ("All up to date") — the entry accent
                        // darkened into a muted surface, same recipe as
                        // the chapter cards.
                        accentTintedSurface(
                            accent = accent,
                            background = MaterialTheme.colorScheme.background,
                            dimmed = false,
                        )
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEachIndexed { index, action ->
                val contentColor = if (actionsEnabled) {
                    onAccent
                } else {
                    // Disabled ("All up to date") — accent text on the
                    // accent-tinted surface, same as the pill rows.
                    accent
                }
                ActionZone(
                    icon = action.icon,
                    label = action.label,
                    contentColor = contentColor,
                    onClickLabel = action.onClickLabel,
                    enabled = actionsEnabled,
                    onClick = action.onClick,
                    modifier = Modifier.weight(1f),
                )
                if (index < actions.lastIndex) {
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .width(1.dp)
                            .background(contentColor.copy(alpha = 0.3f)),
                    )
                }
            }
        }
        ToggleBoxAction(
            icon = markDoneActionIcon(allDone),
            contentDescription = doneContentDescription,
            active = allDone,
            activeColor = accent,
            onActiveColor = onAccent,
            onClick = onToggleDoneClick,
        )
    }
}

/**
 * Rounded-square toggle button — inactive = dimmed white/black outline
 * box with a neutral icon; active = filled accent box with a
 * contrast-aware icon.
 */
@Composable
private fun ToggleBoxAction(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    activeColor: Color,
    onActiveColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .then(
                if (active) {
                    Modifier.background(activeColor)
                } else {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        shape,
                    )
                },
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) {
                onActiveColor
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * One tap zone inside the accent action bar — icon + label over the accent
 * color. One entry = single full-width action; two entries = split bar
 * (e.g. Read | Listen).
 */
data class EntryNextUpAction(
    val icon: ImageVector,
    val label: String,
    val onClickLabel: String,
    val onClick: () -> Unit,
)

/** Large circular icon button for header surfaces (44dp touch target). */
@Composable
fun CardIconAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Filled/outlined heart for the in-library toggle. */
fun favoriteActionIcon(favorite: Boolean): ImageVector =
    if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder

/** Filled/outlined check circle for the mark-all-done toggle. */
fun markDoneActionIcon(allDone: Boolean): ImageVector =
    if (allDone) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle

@Composable
private fun ActionZone(
    icon: ImageVector,
    label: String,
    contentColor: Color,
    onClickLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(enabled = enabled, onClickLabel = onClickLabel, onClick = onClick)
            // Horizontal inset keeps a long ellipsized label visibly clear
            // of the bar's rounded ends instead of running edge-to-edge.
            .padding(horizontal = 20.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // fill=false lets short labels stay centred while long
            // chapter names ellipsize instead of bleeding past the bar.
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 6.dp),
        )
    }
}
