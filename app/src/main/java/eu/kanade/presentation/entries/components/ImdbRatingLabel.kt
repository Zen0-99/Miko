package eu.kanade.presentation.entries.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import eu.kanade.tachiyomi.R

/**
 * IMDb rating label — the IMDb glyph logo followed by the rating number.
 * Replaces the previous "★ N.N" text wherever Cinemeta ratings are shown.
 *
 * The glyph is a cutout vector (letters are transparent holes), so [tint]
 * controls the whole mark. Sized to roughly match the surrounding text.
 *
 * @param rating bare rating value, e.g. "8.4" (no star prefix)
 */
@Composable
fun ImdbRatingLabel(
    rating: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    val glyphHeight: Dp = if (style.fontSize.isSpecified) {
        with(LocalDensity.current) {
            (style.fontSize * 0.95f).toDp()
        }
    } else {
        14.dp
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_imdb_glyph),
            contentDescription = "IMDb",
            tint = tint,
            modifier = Modifier
                .height(glyphHeight)
                .width(glyphHeight * (500f / 230f)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = rating,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
