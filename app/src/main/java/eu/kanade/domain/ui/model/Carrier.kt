package eu.kanade.domain.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

/**
 * The three content carriers of the unified taxonomy (Phase 15).
 *
 * A carrier keys a whole pipeline — extension family, library, trackers —
 * while `kind` (work row `type`) and `nsfw` are orthogonal worker-owned
 * attributes. [medium] IS the worker-contract medium key: it goes on the
 * wire in resolve/markSeen/align payloads and prefixes comment thread
 * ids (`shows:`/`manga:`/`books:`), so this enum is the single owner of
 * the medium vocabulary everywhere it appears.
 *
 * `labelRes`/`icon`/`pipeline` bind each carrier to its UI identity and
 * existing content pipeline; the `carrier_*` string keys land in 15-04 —
 * until then the legacy `label_*` resources stand in.
 */
enum class Carrier(
    val medium: String,
    val labelRes: StringResource,
    val icon: ImageVector,
    val pipeline: ContentMode,
) {
    SHOWS("shows", AYMR.strings.label_anime, Icons.Outlined.LiveTv, ContentMode.ANIME),
    MANGA("manga", AYMR.strings.label_manga, Icons.Outlined.CollectionsBookmark, ContentMode.MANGA),
    BOOKS("books", AYMR.strings.label_novel, Icons.Outlined.MenuBook, ContentMode.NOVEL),
    ;

    companion object {
        /** The carrier vocabulary — validation for anywhere medium strings appear. */
        val mediums: Set<String> get() = entries.mapTo(linkedSetOf()) { it.medium }

        /**
         * Parses a wire medium to its carrier. Legacy `anime`/`novel`
         * keys are honored during the one-cycle alias window (the worker
         * accepts the same aliases); unknown keys return null.
         */
        fun fromMedium(medium: String?): Carrier? = when (medium?.trim()?.lowercase()) {
            "shows", "anime" -> SHOWS
            "manga" -> MANGA
            "books", "novel" -> BOOKS
            else -> null
        }
    }
}
