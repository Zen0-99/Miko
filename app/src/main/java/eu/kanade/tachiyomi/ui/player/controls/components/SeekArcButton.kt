package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * A seek button with an animated arc sweep effect on press.
 * Draws a circular arc that fills from 0 to 360 degrees when pressed,
 * showing the seek direction and amount.
 */
@Composable
fun SeekArcButton(
    icon: ImageVector,
    amount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val arcProgress = remember { Animatable(0f) }
    var isPressed by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val arcColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isPressed) {
        if (isPressed) {
            arcProgress.snapTo(0f)
            arcProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400),
            )
            isPressed = false
        } else {
            arcProgress.snapTo(0f)
        }
    }

    androidx.compose.foundation.layout.Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                onClick = {
                    onClick()
                    scope.launch {
                        isPressed = true
                    }
                },
            ),
    ) {
        // Arc background
        Canvas(modifier = androidx.compose.ui.Modifier.size(56.dp)) {
            val strokeWidth = 3.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

            // Background track
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Animated arc
            if (arcProgress.value > 0f) {
                val sweepDirection = if (amount > 0) 1f else -1f
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * arcProgress.value * sweepDirection,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        // Icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.38f),
            modifier = androidx.compose.ui.Modifier.size(28.dp),
        )

        // Amount label below icon
        Text(
            text = "${amount}s",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}
