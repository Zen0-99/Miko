package eu.kanade.presentation.browse.novel.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.novel.model.icon
import eu.kanade.tachiyomi.R
import tachiyomi.domain.source.novel.model.NovelSource

private val defaultModifier = Modifier
    .height(40.dp)
    .aspectRatio(1f)

@Composable
fun NovelSourceIcon(
    source: NovelSource,
    modifier: Modifier = Modifier,
) {
    val icon = source.icon

    Box(modifier = modifier.then(defaultModifier)) {
        when {
            source.isStub && icon == null -> {
                Image(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            icon != null -> {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Image(
                    painter = painterResource(R.mipmap.ic_default_source),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Comments support badge — small icon at bottom-end, kept within the
        // 40dp icon bounds (no offset) so novel icons aren't visually larger
        // than manga/anime icons.
        if (source.supportsComments) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Comment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(2.dp)
                        .size(10.dp),
                )
            }
        }
    }
}
