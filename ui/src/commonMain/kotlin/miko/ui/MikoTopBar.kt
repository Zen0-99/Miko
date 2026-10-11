package miko.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Port of the Android `SearchFirstTopBarContent` — a 44dp search-first bar:
 *
 *   [back|search icon] [title | hint pill | search field] [actions] [X]
 *
 * Search activation mirrors Android exactly: a non-null [searchQuery] means
 * the field is open (empty string = just opened). The optional [pillContent]
 * renders below the bar in an AnimatedVisibility while search is active —
 * Android's BROWSE|EXTENSIONS toggle lives there.
 */
@Composable
fun MikoTopBar(
    title: String,
    canSearch: Boolean,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    onSearch: (String) -> Unit,
    searchPlaceholderText: String?,
    navigateUp: (() -> Unit)?,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    pillContent: (@Composable () -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isSearchActive = canSearch && searchQuery != null
    val hasNavigateUp = navigateUp != null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left slot (48dp): back arrow on sub-screens, search icon on
            // search-capable root destinations, empty otherwise.
            if (hasNavigateUp || canSearch) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasNavigateUp) {
                        IconButton(onClick = { navigateUp?.invoke() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    } else {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(MR.strings.action_search),
                            )
                        }
                    }
                }
            }

            // Center slot: search field, tappable hint, or plain title.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                when {
                    isSearchActive -> {
                        val focusRequester = remember { FocusRequester() }
                        val keyboardController = LocalSoftwareKeyboardController.current
                        LaunchedEffect(Unit) {
                            try {
                                focusRequester.requestFocus()
                            } catch (_: Exception) {
                            }
                        }
                        val submitSearch: () -> Unit = {
                            val query = searchQuery
                            if (!query.isNullOrBlank()) {
                                onSearch(query)
                                keyboardController?.hide()
                            }
                        }
                        BasicTextField(
                            value = searchQuery ?: "",
                            onValueChange = { onSearchQueryChange(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (searchQuery.isNullOrBlank()) {
                                        Text(
                                            text = searchPlaceholderText
                                                ?: stringResource(MR.strings.action_search_hint),
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }
                    canSearch -> {
                        // Tappable hint — activates search like the icon does.
                        var lastHint by remember { mutableStateOf(searchPlaceholderText) }
                        if (searchPlaceholderText != null) lastHint = searchPlaceholderText
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onSearchQueryChange("") }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = lastHint ?: "",
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }

            // Right slot: action icons (hidden while searching) + X close.
            if (!isSearchActive) {
                actions()
            }
            if (isSearchActive) {
                IconButton(onClick = { onSearchQueryChange(null) }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                    )
                }
            }
        }

        // Pill content below the bar while search is active — the
        // BROWSE|EXTENSIONS toggle on Android.
        AnimatedVisibility(
            visible = isSearchActive,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            pillContent?.invoke()
        }
    }
}
