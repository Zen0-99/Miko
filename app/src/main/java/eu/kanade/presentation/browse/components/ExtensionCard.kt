package eu.kanade.presentation.browse.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Extension card with a two-row layout:
 *
 *  [icon]                              [cog]
 *
 *  lang - version
 *  Title
 *                              [comments badge]
 *
 * The gap between the top row (icon/cog) and the bottom row (lang/title/badge)
 * provides visual separation between the identity and metadata sections.
 * Fill uses surfaceContainerHigh for a lighter-than-background card surface.
 */
@Composable
fun ExtensionCard(
    title: String,
    lang: String,
    version: String,
    iconDrawable: Drawable? = null,
    iconUrl: String? = null,
    hasUpdate: Boolean = false,
    isUpdating: Boolean = false,
    supportsComments: Boolean = false,
    onClick: () -> Unit,
    onCogClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        // Lighter than the background — surfaceContainerHigh lifts the card
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Top row: icon (left) + cog/download/progress (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExtensionIcon(
                    iconDrawable = iconDrawable,
                    iconUrl = iconUrl,
                    modifier = Modifier.size(40.dp),
                )

                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    IconButton(
                        onClick = onCogClick,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (hasUpdate) Icons.Outlined.Download else Icons.Outlined.Settings,
                            contentDescription = if (hasUpdate) "Update" else stringResource(MR.strings.label_extension_info),
                            tint = if (hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // Bottom row: lang/version + title (left) + comments badge (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    // Language - Version (above title)
                    val langVersion = if (version.isNotBlank()) "$lang - $version" else lang
                    Text(
                        text = langVersion,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Title (below lang/version)
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (supportsComments) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Comment,
                        contentDescription = "Supports comments",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Renders an extension icon from either a Drawable (installed) or URL (available).
 */
@Composable
fun ExtensionIcon(
    iconDrawable: Drawable? = null,
    iconUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.extraSmall
    when {
        iconDrawable != null -> {
            Image(
                bitmap = iconDrawable.toImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.clip(shape),
            )
        }
        iconUrl != null -> {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.clip(shape),
            )
        }
        else -> {
            Box(
                modifier = modifier.clip(shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private fun Drawable.toImageBitmap(): androidx.compose.ui.graphics.ImageBitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        android.graphics.Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
