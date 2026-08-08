package eu.kanade.tachiyomi.ui.reader.novel

import android.graphics.Color
import android.graphics.Typeface

data class TextConfig(
    val textSize: Float = 16f,
    val textColor: Int = Color.BLACK,
    val backgroundColor: Int = Color.WHITE,
    val textFont: Typeface? = null,
    val lineSpacing: Float = 1.5f,
    val paragraphSpacing: Int = 16,
    val horizontalPadding: Int = 16,
    val verticalPadding: Int = 24,
    val isTextSelectable: Boolean = true,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val bionicReading: Boolean = false,
    val forceBold: Boolean = false,
    val forceItalic: Boolean = false,
    val forceParagraphIndent: Boolean = false,
    val preserveSourceTextAlign: Boolean = false,
    val textShadowEnabled: Boolean = false,
    val textShadowColor: String = "#80000000",
    val textShadowBlur: Float = 0f,
    val textShadowX: Float = 0f,
    val textShadowY: Float = 0f,
    // --- Tier 3 additive fields (rendered as Compose overlays) ---
    val backgroundTexture: NovelReaderBackgroundTexture = NovelReaderBackgroundTexture.NONE,
    val textureStrength: Int = 50,
    val oledEdgeGradient: Boolean = false,
    val pageEdgeShadowEnabled: Boolean = false,
    val pageEdgeShadowAlpha: Float = 0.25f,
    val edgeFadeEnabled: Boolean = false,
)
