package eu.kanade.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.domain.ui.model.ContentMode.Companion.carouselOrderFor
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Inline full-width mode selector row (Anime | Manga | Novels).
 *
 * Each visible mode takes equal width. Tapping a mode switches to it.
 * Returns nothing (renders empty) when only one or zero modes are visible.
 */
@Composable
fun ModePill(
    modifier: Modifier = Modifier,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val contentMode by uiPreferences.contentMode().collectAsStateWithLifecycle()
    val showManga by uiPreferences.showMangaMode().collectAsStateWithLifecycle()
    val showAnime by uiPreferences.showAnimeMode().collectAsStateWithLifecycle()
    val showNovel by uiPreferences.showNovelMode().collectAsStateWithLifecycle()

    val visibleModes = remember(showManga, showAnime, showNovel) {
        val modes = mutableSetOf<ContentMode>()
        if (showManga) modes.add(ContentMode.MANGA)
        if (showAnime) modes.add(ContentMode.ANIME)
        if (showNovel) modes.add(ContentMode.NOVEL)
        carouselOrderFor(modes)
    }

    // Hide entirely if only one (or zero) mode is visible
    if (visibleModes.size <= 1) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleModes.forEach { mode ->
            val isActive = mode == contentMode
            Text(
                text = stringResource(mode.titleRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        uiPreferences.contentMode().set(mode)
                    }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
