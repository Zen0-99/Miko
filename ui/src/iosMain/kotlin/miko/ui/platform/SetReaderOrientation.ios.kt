package miko.ui.platform

import androidx.compose.runtime.Composable

/**
 * iOS can't force an interface orientation without app-delegate level
 * supported-orientation plumbing; the pref still persists so a future
 * UIDeviceOrientation hook can honor it.
 */
@Composable
actual fun SetReaderOrientation(flagValue: Int) = Unit
