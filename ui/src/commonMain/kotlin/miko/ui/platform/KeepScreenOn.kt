package miko.ui.platform

import androidx.compose.runtime.Composable

/**
 * Android's FLAG_KEEP_SCREEN_ON / iOS idleTimerDisabled — driven by the
 * reader's pref_novel_keep_screen_on pref.
 */
@Composable
expect fun KeepScreenOn(enabled: Boolean)
