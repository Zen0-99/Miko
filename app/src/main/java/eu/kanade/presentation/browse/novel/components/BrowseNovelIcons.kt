package eu.kanade.presentation.browse.novel.components

import android.util.DisplayMetrics
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionLoader
import eu.kanade.tachiyomi.novelsource.NovelSource
import tachiyomi.core.common.util.lang.withIOContext

private val defaultModifier = Modifier
    .height(40.dp)
    .aspectRatio(1f)

/**
 * Check if any source in an installed extension supports comments.
 */
private fun NovelExtension.Installed.supportsComments(): Boolean {
    return sources.any { it.supportsComments }
}

/**
 * Check if any source in an available (not-installed) extension supports comments.
 */
private fun NovelExtension.Available.supportsComments(): Boolean {
    return sources.any { it.supportsComments }
}

@Composable
fun NovelExtensionIcon(
    extension: NovelExtension,
    modifier: Modifier = Modifier,
    density: Int = DisplayMetrics.DENSITY_DEFAULT,
    showCommentsBadge: Boolean = true,
) {
    val supportsComments = (showCommentsBadge && when (extension) {
        is NovelExtension.Installed -> extension.supportsComments()
        is NovelExtension.Available -> extension.supportsComments()
        else -> false
    })

    Box(modifier = modifier) {
        when (extension) {
            is NovelExtension.Available -> {
                val iconUrl = extension.iconUrl
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(LocalContext.current)
                        .data(iconUrl)
                        .memoryCacheKey(iconUrl)
                        .diskCacheKey(iconUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    placeholder = ColorPainter(Color(0x1F888888)),
                    error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                    modifier = Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.extraSmall),
                )
            }
            is NovelExtension.Installed -> {
                val icon by extension.getIcon(density)
                when (icon) {
                    NovelIconResult.Loading -> Box(modifier = Modifier.matchParentSize())
                    is NovelIconResult.Success -> Image(
                        bitmap = (icon as NovelIconResult.Success<ImageBitmap>).value,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                    )
                    NovelIconResult.Error -> Image(
                        bitmap = ImageBitmap.imageResource(id = R.mipmap.ic_default_source),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            is NovelExtension.Untrusted -> Image(
                imageVector = Icons.Filled.Dangerous,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                modifier = Modifier.matchParentSize().then(defaultModifier),
            )
        }

        // Comments support badge — small circle with comment icon at bottom-right
        if (supportsComments) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "Supports comments",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}

@Composable
private fun NovelExtension.Installed.getIcon(density: Int = DisplayMetrics.DENSITY_DEFAULT): State<NovelIconResult<ImageBitmap>> {
    val context = LocalContext.current
    return produceState<NovelIconResult<ImageBitmap>>(initialValue = NovelIconResult.Loading, this) {
        withIOContext {
            value = try {
                // 1. Use the already-loaded icon Drawable from the extension (most reliable)
                val drawable = icon ?: run {
                    // 2. Fall back to loading from package manager
                    val appInfo = NovelExtensionLoader.getNovelExtensionPackageInfoFromPkgName(
                        context,
                        pkgName,
                    )?.applicationInfo
                    if (appInfo != null && appInfo.icon != 0) {
                        val appResources = context.packageManager.getResourcesForApplication(appInfo)
                        appResources.getDrawableForDensity(appInfo.icon, density, null)
                    } else {
                        null
                    }
                }
                if (drawable != null) {
                    NovelIconResult.Success(drawable.toBitmap().asImageBitmap())
                } else {
                    NovelIconResult.Error
                }
            } catch (e: Exception) {
                NovelIconResult.Error
            }
        }
    }
}

sealed class NovelIconResult<out T> {
    data object Loading : NovelIconResult<Nothing>()
    data object Error : NovelIconResult<Nothing>()
    data class Success<out T>(val value: T) : NovelIconResult<T>()
}
