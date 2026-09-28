package eu.kanade.tachiyomi.data.comments

import eu.kanade.tachiyomi.data.mikodb.TitleNormalizer

/**
 * Compact, order-independent fingerprint of a source's chapter list for
 * one work (NCM-002 alignment). The server scores [numbers] against a
 * reference (MangaDex aggregate or inter-source consensus); [namesHash]
 * lets the server correlate identical lists without storing names.
 */
data class ChapterFingerprint(
    val count: Int,
    val numbers: List<Float>,
    val namesHash: String,
) {
    companion object {
        /**
         * Build from (name, chapter_number) pairs. Numbers resolve the
         * same way as [ChapterBinder]: non-positive values fall back to
         * the name. Entries that still resolve to nothing are dropped —
         * the fingerprint describes what CAN be bound to threads.
         */
        fun of(chapters: List<Pair<String, Float>>): ChapterFingerprint {
            val resolved = chapters.mapNotNull { (name, number) ->
                val n = if (number > 0f) number else ChapterBinder.numberFromName(name)
                n?.let { name to it }
            }
            val namesHash = DeviceIdentityProvider.sha256Hex(
                resolved
                    .sortedBy { it.second }
                    .joinToString("\n") { (name, n) ->
                        "${ChapterBinder.formatNumber(n)}|${TitleNormalizer.normalize(name)}"
                    },
            ).take(16)
            return ChapterFingerprint(
                count = resolved.size,
                numbers = resolved.map { it.second }.sorted(),
                namesHash = namesHash,
            )
        }
    }
}
