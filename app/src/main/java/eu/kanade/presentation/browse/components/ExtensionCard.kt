package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * A horizontal rectangle card for displaying extensions in browse.
 * The extension icon is used as a large blurred background fill via [iconContent].
 * Bold title in top-left, language below, version inline with language (dot-separated).
 * Settings cog (or download icon when update available) in bottom-right.
 * When [isUpdating] is true, a progress indicator replaces the cog.
 */
@Composable
fun ExtensionCard(
    title: String,
    lang: String,
    version: String,
    iconUrl: String? = null,
    iconContent: (@Composable (Modifier) -> Unit)? = null,
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
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Blurred icon as large background fill
            if (iconContent != null) {
                iconContent(
                    Modifier
                        .fillMaxSize()
                        .blur(24.dp),
                )
            } else if (iconUrl != null) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(24.dp),
                )
            }

            // Dark overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top section: title + comment badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Language · Version (only show dot separator if version is non-empty)
                        val langVersion = if (version.isNotBlank()) "$lang · $version" else lang
                        Text(
                            text = langVersion,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (supportsComments) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = "Supports comments",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Bottom section: cog, download, or progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        IconButton(
                            onClick = onCogClick,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (hasUpdate) Icons.Outlined.Download else Icons.Outlined.Settings,
                                contentDescription = if (hasUpdate) "Update" else stringResource(MR.strings.label_extension_info),
                                tint = if (hasUpdate) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
