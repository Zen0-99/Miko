package miko.ui.platform

import androidx.compose.runtime.Composable

/**
 * Android's reader `fullscreen` pref — hides the status/navigation bars
 * while reading; bars reappear on swipe (transient) and restore on exit.
 */
@Composable
expect fun HideSystemBars(enabled: Boolean)
