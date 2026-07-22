package eu.kanade.presentation.novel.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NovelReaderTopBar(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    progressPercent: Int = -1,
    estimatedReadingTime: Int = -1,
    wordCount: Int = -1,
    timeToEnd: Int = -1,
    fullscreen: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (fullscreen) {
                        Modifier.padding(top = 24.dp) // Fixed padding when system bars hidden
                    } else {
                        Modifier.statusBarsPadding()
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = accentColor ?: MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor ?: MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = (accentColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (progressPercent in 0..100) {
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.labelMedium,
                        color = (accentColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = if (estimatedReadingTime >= 0) 4.dp else 12.dp),
                    )
                }
                if (estimatedReadingTime >= 0) {
                    val timeText = if (estimatedReadingTime == 0) {
                        "<1m"
                    } else {
                        "${estimatedReadingTime}m"
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = (accentColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = if (wordCount >= 0 || timeToEnd >= 0) 4.dp else 12.dp),
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
                        color = (accentColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = if (timeToEnd >= 0) 4.dp else 12.dp),
                    )
                }
                if (timeToEnd >= 0) {
                    val endText = if (timeToEnd == 0) {
                        "<1m left"
                    } else {
                        "${timeToEnd}m left"
                    }
                    Text(
                        text = endText,
                        style = MaterialTheme.typography.labelMedium,
                        color = (accentColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        }
    }
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
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (fullscreen) {
                        Modifier.padding(bottom = 24.dp) // Fixed padding when system bars hidden
                    } else {
                        Modifier.navigationBarsPadding()
                    },
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onChaptersClick) {
                Icon(
                    imageVector = Icons.Outlined.FormatListNumbered,
                    contentDescription = "Chapters",
                    tint = accentColor ?: MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onHighlightsClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = "Highlights",
                    tint = accentColor ?: MaterialTheme.colorScheme.onSurface,
                )
            }
            if (showCommentsButton) {
                IconButton(onClick = onCommentsClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Comment,
                        contentDescription = "Comments",
                        tint = accentColor ?: MaterialTheme.colorScheme.onSurface,
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
                        accentColor ?: MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = accentColor ?: MaterialTheme.colorScheme.onSurface,
                )
            }
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
    estimatedReadingTime: Int = -1,
    wordCount: Int = -1,
    timeToEnd: Int = -1,
    fullscreen: Boolean = false,
    showPhoneInfo: Boolean = false,
    readerBackgroundColor: Color = MaterialTheme.colorScheme.background,
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
                estimatedReadingTime = estimatedReadingTime,
                wordCount = wordCount,
                timeToEnd = timeToEnd,
                fullscreen = fullscreen,
            )
        }
        if (showPhoneInfo) {
            NovelPhoneInfoOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                accentColor = accentColor,
                fullscreen = fullscreen,
                backgroundColor = readerBackgroundColor,
            )
        }
        AnimatedVisibility(
            visible = isMenuVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
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
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textColor = when {
        backgroundColor.luminance() > 0.5f -> androidx.compose.ui.graphics.Color.Black
        accentColor != null -> accentColor
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .then(
                if (fullscreen) {
                    Modifier.padding(bottom = 4.dp)
                } else {
                    Modifier.navigationBarsPadding()
                },
            )
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = currentTime,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = 11.sp,
        )
        Text(
            text = "$batteryPct%",
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}
