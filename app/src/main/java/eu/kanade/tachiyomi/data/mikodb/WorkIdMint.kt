package eu.kanade.tachiyomi.data.mikodb

import java.security.MessageDigest

/**
 * Deterministic, collision-resistant minting of Miko work IDs for
 * catalog-uncovered works (western comics, doujinshi, books, RoyalRoad
 * gaps). Same inputs produce the same ID on every device with no network
 * or registry — `mk:m:<sha256(title|type|author?|year?)>`.
 *
 * Optional parts only enter the hash when non-blank on the source
 * metadata; inconsistent metadata across sources yields different IDs —
 * those divergences and false collisions are logged server-side in
 * `identity_review` (Phase 8, research Q2) rather than silently merged.
 */
object WorkIdMint {

    fun mint(title: String, type: String, author: String? = null, year: Int? = null): String {
        val normalized = buildList {
            add(TitleNormalizer.normalize(title))
            add(type)
            author?.takeIf { it.isNotBlank() }?.let { add(TitleNormalizer.normalize(it)) }
            year?.let { add(it.toString()) }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.joinToString("|").toByteArray(Charsets.UTF_8))
        return "mk:m:" + digest.joinToString("") { "%02x".format(it) }
    }
}
