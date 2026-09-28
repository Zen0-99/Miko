package eu.kanade.tachiyomi.ui.entries.shows

import miko.domain.shows.SeasonAnime

data class ShowsSeasonItem(
    val seasonAnime: SeasonAnime,
    val downloadCount: Long = -1L,
    val unseenCount: Long = -1L,
    val isLocal: Boolean = false,
    val sourceLanguage: String = "",
    val showContinueOverlay: Boolean = false,
)
