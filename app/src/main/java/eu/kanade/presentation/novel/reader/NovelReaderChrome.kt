package eu.kanade.presentation.novel.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AppBar

@Composable
fun NovelReaderTopBar(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    progressPercent: Int = -1,
    wordCount: Int = -1,
    fullscreen: Boolean = false,
    readerBackgroundColor: Color = MaterialTheme.colorScheme.surface,
) {
    // Use the reader background color (semi-transparent) instead of theme
    // surface — matches the reader's actual background (sepia, black, etc.)
    val backgroundColor = readerBackgroundColor.copy(alpha = 0.9f)
    val onColor = if (readerBackgroundColor.luminance() > 0.5f) Color.Black else MaterialTheme.colorScheme.onSurface

    AppBar(
        modifier = modifier,
        backgroundColor = backgroundColor,
        title = title,
        subtitle = subtitle.takeIf { it.isNotBlank() },
        navigateUp = onBackClick,
        actions = {
            if (progressPercent in 0..100) {
                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = onColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = if (wordCount >= 0) 4.dp else 12.dp),
                )
            }
            if (wordCount >= 0) {
                val wordText = if (wordCount >= 1000) {
                    "%.1fk".format(wordCount / 1000f)
                } else {
                    "$wordCount"
                }
                Text(
                    text = wordText,
                    style = MaterialTheme.typography.labelMedium,
                    color = onColor.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        },
    )
}

@Composable
fun NovelReaderBottomBar(
    onChaptersClick: () -> Unit,
    onWebviewClick: () -> Unit,
    onHighlightsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    fullscreen: Boolean = false,
    showCommentsButton: Boolean = false,
    onCommentsClick: () -> Unit = {},
    onTtsClick: () -> Unit = {},
    isTtsActive: Boolean = false,
    readerBackgroundColor: Color = MaterialTheme.colorScheme.surface,
) {
    // Floating rounded pill using the reader background color.
    val pillColor = readerBackgroundColor.copy(alpha = 0.9f)
    val onColor = if (readerBackgroundColor.luminance() > 0.5f) Color.Black else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(pillColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onChaptersClick) {
            Icon(
                imageVector = Icons.Outlined.FormatListNumbered,
                contentDescription = "Chapters",
                tint = onColor,
            )
        }
        IconButton(onClick = onHighlightsClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = "Highlights",
                tint = onColor,
            )
        }
        if (showCommentsButton) {
            IconButton(onClick = onCommentsClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "Comments",
                    tint = onColor,
                )
            }
        }
        IconButton(onClick = onTtsClick) {
            Icon(
                imageVector = Icons.Filled.RecordVoiceOver,
                contentDescription = "Read aloud",
                tint = if (isTtsActive) {
                    accentColor ?: MaterialTheme.colorScheme.primary
                } else {
                    onColor
                },
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = onColor,
            )
        }
    }
}

@Composable
fun NovelReaderChrome(
    isMenuVisible: Boolean,
    title: String,
    subtitle: String,
    accentColor: Color? = null,
    progressPercent: Int = -1,
    wordCount: Int = -1,
    fullscreen: Boolean = false,
    showPhoneInfo: Boolean = false,
    estimatedReadingTime: Int = -1,
    readerBackgroundColor: Color = MaterialTheme.colorScheme.background,
    backgroundTexture: eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture = eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture.NONE,
    textureStrength: Int = 0,
    edgeFadeEnabled: Boolean = false,
    showCommentsButton: Boolean = false,
    isTtsActive: Boolean = false,
    onBackClick: () -> Unit,
    onChaptersClick: () -> Unit,
    onWebviewClick: () -> Unit,
    onHighlightsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCommentsClick: () -> Unit = {},
    onTtsClick: () -> Unit = {},
) {
    // The info overlay IS the edge gradient. When edge fade is on, the
    // overlay is taller (64dp gradient + 28dp info content) and its
    // background fades from transparent (top) to opaque (bottom).
    // When edge fade is off, the overlay is just the 28dp info content
    // with a solid background.
    val infoBarFadeHeight = if (edgeFadeEnabled) 64.dp else 0.dp
    val infoBarBaseHeight = 28.dp
    val infoBarTotalHeight = infoBarBaseHeight + infoBarFadeHeight

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isMenuVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            NovelReaderTopBar(
                title = title,
                subtitle = subtitle,
                onBackClick = onBackClick,
                accentColor = accentColor,
                progressPercent = progressPercent,
                wordCount = wordCount,
                fullscreen = fullscreen,
                readerBackgroundColor = readerBackgroundColor,
            )
        }
        if (showPhoneInfo) {
            NovelPhoneInfoOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                accentColor = accentColor,
                fullscreen = fullscreen,
                backgroundColor = readerBackgroundColor,
                backgroundTexture = backgroundTexture,
                textureStrength = textureStrength,
                estimatedReadingTime = estimatedReadingTime,
                edgeFadeEnabled = edgeFadeEnabled,
                fadeHeight = infoBarFadeHeight,
            )
        }
        AnimatedVisibility(
            visible = isMenuVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = if (showPhoneInfo) infoBarTotalHeight + 2.dp else 2.dp,
                ),
        ) {
            NovelReaderBottomBar(
                onChaptersClick = onChaptersClick,
                onWebviewClick = onWebviewClick,
                onHighlightsClick = onHighlightsClick,
                onSettingsClick = onSettingsClick,
                accentColor = accentColor,
                fullscreen = fullscreen,
                showCommentsButton = showCommentsButton,
                onCommentsClick = onCommentsClick,
                onTtsClick = onTtsClick,
                isTtsActive = isTtsActive,
                readerBackgroundColor = readerBackgroundColor,
            )
        }
    }
}

@Composable
fun NovelPhoneInfoOverlay(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    fullscreen: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    backgroundTexture: eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture = eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture.NONE,
    textureStrength: Int = 0,
    estimatedReadingTime: Int = -1,
    edgeFadeEnabled: Boolean = false,
    fadeHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textColor = when {
        backgroundColor.luminance() > 0.5f -> androidx.compose.ui.graphics.Color.Black
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Time
    val timeText = remember {
        val format = android.text.format.DateFormat.is24HourFormat(context)
        if (format) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        else java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
    }
    var currentTime by remember { mutableStateOf(timeText.format(java.util.Date())) }

    // Battery
    var batteryPct by remember { mutableStateOf(100) }

    // Update time every second
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            currentTime = timeText.format(java.util.Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    // Battery receiver
    androidx.compose.runtime.DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (scale > 0) {
                    batteryPct = (level * 100 / scale.toFloat()).toInt()
                }
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // The info overlay IS the edge gradient. The overlay uses a single
    // vertical gradient that spans the full height with no visible boundary
    // between the fade area and the solid info area.
    //
    // The gradient uses smooth color stops across the entire height:
    // - Top: transparent (text fades out here)
    // - Middle: gradually increasing opacity (the fade zone)
    // - Bottom: fully opaque (solid background for info content)
    //
    // There is no hard cutoff — the gradient smoothly reaches 1.0 near the
    // bottom, so the "solid" area is just where the gradient is already at
    // full opacity. This eliminates any visible boundary.
    //
    // Brush.verticalGradient uses native dithered rendering — no banding.
    //
    // When edgeFadeEnabled is off, the entire overlay is solid.
    val infoContentHeight = 28.dp
    val totalHeight = fadeHeight + infoContentHeight

    // Build texture brush in composable context (imageResource is @Composable)
    val textureBrush: ShaderBrush? = if (backgroundTexture != eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture.NONE && textureStrength > 0) {
        val imageRes = when (backgroundTexture) {
            eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture.PAPER_GRAIN -> eu.kanade.tachiyomi.R.drawable.texture_paper
            eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture.LINEN -> eu.kanade.tachiyomi.R.drawable.texture_linen
            eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture.CANVAS -> eu.kanade.tachiyomi.R.drawable.texture_canvas
            eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture.KRAFT -> eu.kanade.tachiyomi.R.drawable.texture_kraft
            eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture.DOTTED -> eu.kanade.tachiyomi.R.drawable.texture_dotted
            eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture.NONE -> null
        }
        if (imageRes != null) {
            val imageBitmap = ImageBitmap.imageResource(id = imageRes)
            remember(imageBitmap) {
                ShaderBrush(
                    ImageShader(
                        image = imageBitmap,
                        tileModeX = TileMode.Repeated,
                        tileModeY = TileMode.Repeated,
                    ),
                )
            }
        } else {
            null
        }
    } else {
        null
    }
    val texAlpha = (textureStrength.coerceIn(0, 100) / 100f)

    // Background gradient: smooth across the full height, no hard boundary.
    // The fadeHeight portion is where the gradient transitions from
    // transparent to near-opaque. Below that, it smoothly reaches full
    // opacity. Using multiple stops for a natural ease-in curve.
    val bgGradient = if (edgeFadeEnabled && fadeHeight > 0.dp) {
        val fadeFraction = (fadeHeight / totalHeight).coerceIn(0f, 1f)
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to backgroundColor.copy(alpha = 0f),
                fadeFraction * 0.3f to backgroundColor.copy(alpha = 0.08f),
                fadeFraction * 0.5f to backgroundColor.copy(alpha = 0.25f),
                fadeFraction * 0.7f to backgroundColor.copy(alpha = 0.55f),
                fadeFraction * 0.85f to backgroundColor.copy(alpha = 0.85f),
                fadeFraction to backgroundColor.copy(alpha = 0.97f),
                1f to backgroundColor.copy(alpha = 1f),
            ),
        )
    } else {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to backgroundColor,
                1f to backgroundColor,
            ),
        )
    }

    // Texture alpha gradient: matches the background gradient so texture
    // also fades in smoothly from top to bottom. Applied via a separate
    // offscreen compositing layer to support BlendMode.DstIn masking.
    val texAlphaGradient = if (edgeFadeEnabled && fadeHeight > 0.dp) {
        val fadeFraction = (fadeHeight / totalHeight).coerceIn(0f, 1f)
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                fadeFraction * 0.3f to Color.White.copy(alpha = texAlpha * 0.08f),
                fadeFraction * 0.5f to Color.White.copy(alpha = texAlpha * 0.25f),
                fadeFraction * 0.7f to Color.White.copy(alpha = texAlpha * 0.55f),
                fadeFraction * 0.85f to Color.White.copy(alpha = texAlpha * 0.85f),
                fadeFraction to Color.White.copy(alpha = texAlpha * 0.97f),
                1f to Color.White.copy(alpha = texAlpha),
            ),
        )
    } else {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = texAlpha),
                1f to Color.White.copy(alpha = texAlpha),
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .background(bgGradient),
    ) {
        // Texture overlay in a separate layer with offscreen compositing
        // so BlendMode.DstIn can mask it with the alpha gradient.
        if (textureBrush != null && texAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawRect(brush = textureBrush)
                        drawRect(brush = texAlphaGradient, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                    },
            )
        }

        // Info content at the bottom of the gradient
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (fullscreen) {
                        Modifier.padding(bottom = 4.dp)
                    } else {
                        Modifier.navigationBarsPadding()
                    },
                )
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            if (estimatedReadingTime >= 0) {
                val timeText = if (estimatedReadingTime == 0) {
                    "<1m left"
                } else {
                    "${estimatedReadingTime}m left"
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = "$batteryPct%",
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
        }
    }
}
