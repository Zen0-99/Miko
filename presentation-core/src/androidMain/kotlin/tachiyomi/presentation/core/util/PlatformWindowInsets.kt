package tachiyomi.presentation.core.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable

@Composable
actual fun WindowInsets.Companion.isImeVisibleCompat(): Boolean = isImeVisible
