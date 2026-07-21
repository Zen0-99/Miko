package eu.kanade.tachiyomi.ui.entries.novel.track

import eu.kanade.tachiyomi.data.track.Tracker
import tachiyomi.domain.track.novel.model.NovelTrack

data class NovelTrackItem(val track: NovelTrack?, val tracker: Tracker)
