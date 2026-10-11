package miko.ui.platform

import androidx.compose.runtime.Composable

/**
 * No-op on iOS — `prefersStatusBarHidden` lives on the hosting
 * UIViewController, which shared code can't subclass or reach; the
 * reader's own chrome still collapses on center-tap.
 */
@Composable
actual fun HideSystemBars(enabled: Boolean) = Unit
