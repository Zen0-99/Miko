package miko.ui.platform

import androidx.compose.runtime.Composable

/**
 * Android's pref_default_orientation_type_key — applies the reader's
 * orientation lock while the reader screen is composed. Values are
 * Android's ReaderOrientation flagValues (0 default, 8 free, 0x10
 * portrait, 0x18 landscape, 0x20 locked portrait, 0x28 locked
 * landscape, 0x30 reverse portrait).
 */
@Composable
expect fun SetReaderOrientation(flagValue: Int)
