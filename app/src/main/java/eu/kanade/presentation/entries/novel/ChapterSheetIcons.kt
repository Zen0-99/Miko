package eu.kanade.presentation.entries.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom icons for the chapter long-press bottom sheet, matching the
 * Miko-Yokai-Old design:
 *
 * - EyeDown: eye with arrow down (mark previous as read)
 * - EyeOffDown: crossed eye with arrow down (mark previous as unread)
 * - EyeDots: eye with three dots under it (mark range as read)
 * - EyeOffDots: crossed eye with three dots under it (mark range as unread)
 */

private fun buildIcon(
    name: String,
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    fill = SolidColor(Color.Black),
    fillAlpha = 1f,
    stroke = null,
    strokeLineWidth = 0f,
    strokeLineCap = StrokeCap.Butt,
    strokeLineJoin = StrokeJoin.Miter,
    strokeLineMiter = 4f,
    pathFillType = PathFillType.NonZero,
    pathBuilder = block,
).build()

/** Eye with arrow pointing down — "mark previous chapters as read" */
val EyeDown: ImageVector by lazy {
    buildIcon("EyeDown") {
        // Eye outline
        moveTo(12f, 5f)
        curveTo(7f, 5f, 2.73f, 8.11f, 1f, 12f)
        curveTo(2.73f, 15.89f, 7f, 19f, 12f, 19f)
        curveTo(17f, 19f, 21.27f, 15.89f, 23f, 12f)
        curveTo(21.27f, 8.11f, 17f, 5f, 12f, 5f)
        close()
        // Eye pupil
        moveTo(12f, 9f)
        curveTo(13.66f, 9f, 15f, 10.34f, 15f, 12f)
        curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
        curveTo(10.34f, 15f, 9f, 13.66f, 9f, 12f)
        curveTo(9f, 10.34f, 10.34f, 9f, 12f, 9f)
        close()
        // Arrow down (below eye)
        moveTo(12f, 20f)
        lineTo(12f, 23f)
        moveTo(9f, 22f)
        lineTo(12f, 23f)
        lineTo(15f, 22f)
    }
}

/** Crossed eye with arrow pointing down — "mark previous chapters as unread" */
val EyeOffDown: ImageVector by lazy {
    ImageVector.Builder(
        name = "EyeOffDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Eye outline (stroked)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = {
                moveTo(2f, 2f)
                lineTo(22f, 22f)
                moveTo(6.71f, 6.71f)
                curveTo(3.27f, 8.16f, 1f, 10f, 1f, 12f)
                curveTo(2.73f, 15.89f, 7f, 19f, 12f, 19f)
                curveTo(13.5f, 19f, 14.94f, 18.7f, 16.24f, 18.18f)
                moveTo(19.45f, 15.45f)
                curveTo(20.84f, 14.4f, 22.02f, 13.23f, 23f, 12f)
                curveTo(21.27f, 8.11f, 17f, 5f, 12f, 5f)
                curveTo(10.83f, 5f, 9.7f, 5.18f, 8.64f, 5.52f)
            },
        )
        // Arrow down
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathBuilder = {
                moveTo(12f, 20f)
                lineTo(12f, 23f)
                moveTo(9f, 22f)
                lineTo(12f, 23f)
                lineTo(15f, 22f)
            },
        )
    }.build()
}

/** Eye with three dots under it — "mark range as read" */
val EyeDots: ImageVector by lazy {
    ImageVector.Builder(
        name = "EyeDots",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Eye outline + pupil
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathBuilder = {
                moveTo(12f, 5f)
                curveTo(7f, 5f, 2.73f, 8.11f, 1f, 12f)
                curveTo(2.73f, 15.89f, 7f, 19f, 12f, 19f)
                curveTo(17f, 19f, 21.27f, 15.89f, 23f, 12f)
                curveTo(21.27f, 8.11f, 17f, 5f, 12f, 5f)
                close()
                moveTo(12f, 9f)
                curveTo(13.66f, 9f, 15f, 10.34f, 15f, 12f)
                curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
                curveTo(10.34f, 15f, 9f, 13.66f, 9f, 12f)
                curveTo(9f, 10.34f, 10.34f, 9f, 12f, 9f)
                close()
            },
        )
        // Three dots under the eye
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathBuilder = {
                moveTo(8f, 22f)
                arcTo(1f, 1f, 0f, false, true, 9f, 21f)
                arcTo(1f, 1f, 0f, false, true, 10f, 22f)
                arcTo(1f, 1f, 0f, false, true, 8f, 22f)
                close()
                moveTo(11f, 22f)
                arcTo(1f, 1f, 0f, false, true, 12f, 21f)
                arcTo(1f, 1f, 0f, false, true, 13f, 22f)
                arcTo(1f, 1f, 0f, false, true, 11f, 22f)
                close()
                moveTo(14f, 22f)
                arcTo(1f, 1f, 0f, false, true, 15f, 21f)
                arcTo(1f, 1f, 0f, false, true, 16f, 22f)
                arcTo(1f, 1f, 0f, false, true, 14f, 22f)
                close()
            },
        )
    }.build()
}

/** Crossed eye with three dots under it — "mark range as unread" */
val EyeOffDots: ImageVector by lazy {
    ImageVector.Builder(
        name = "EyeOffDots",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Crossed eye outline (stroked)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = {
                moveTo(2f, 2f)
                lineTo(22f, 22f)
                moveTo(6.71f, 6.71f)
                curveTo(3.27f, 8.16f, 1f, 10f, 1f, 12f)
                curveTo(2.73f, 15.89f, 7f, 19f, 12f, 19f)
                curveTo(13.5f, 19f, 14.94f, 18.7f, 16.24f, 18.18f)
                moveTo(19.45f, 15.45f)
                curveTo(20.84f, 14.4f, 22.02f, 13.23f, 23f, 12f)
                curveTo(21.27f, 8.11f, 17f, 5f, 12f, 5f)
                curveTo(10.83f, 5f, 9.7f, 5.18f, 8.64f, 5.52f)
            },
        )
        // Three dots under the eye
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathBuilder = {
                moveTo(8f, 22f)
                arcTo(1f, 1f, 0f, false, true, 9f, 21f)
                arcTo(1f, 1f, 0f, false, true, 10f, 22f)
                arcTo(1f, 1f, 0f, false, true, 8f, 22f)
                close()
                moveTo(11f, 22f)
                arcTo(1f, 1f, 0f, false, true, 12f, 21f)
                arcTo(1f, 1f, 0f, false, true, 13f, 22f)
                arcTo(1f, 1f, 0f, false, true, 11f, 22f)
                close()
                moveTo(14f, 22f)
                arcTo(1f, 1f, 0f, false, true, 15f, 21f)
                arcTo(1f, 1f, 0f, false, true, 16f, 22f)
                arcTo(1f, 1f, 0f, false, true, 14f, 22f)
                close()
            },
        )
    }.build()
}
