package tachiyomi.presentation.core.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

/**
 * Whether the on-screen keyboard is currently visible. On Android this is
 * `androidx.compose.foundation.layout.isImeVisible`; on iOS it reads the CMP
 * `ime` insets, which mirror UIKeyboard notifications.
 */
@Composable
expect fun WindowInsets.Companion.isImeVisibleCompat(): Boolean
