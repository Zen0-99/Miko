package miko.ui.platform

import androidx.compose.runtime.Composable

/**
 * Android's shareManga/shareNovel/shareAnime — the platform share sheet.
 * Returns a handler that opens the sheet for the given text (the entry's
 * source URL); iOS presents UIActivityViewController from the top-most
 * view controller, Android fires ACTION_SEND through a chooser.
 */
@Composable
expect fun rememberShareHandler(): (String) -> Unit
