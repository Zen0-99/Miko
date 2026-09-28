package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Golden-source selection for canonical works (NCM-007 / Phase 12-03).
 *
 * A work's `provider_links` name the providers that carry it (mangadex,
 * webtoons, royalroad…); `miko.json`'s tiered source blocks name which
 * extension serves each provider and how trustworthy its chapter layout
 * is. The intersection — linked providers ∩ installed extensions ∩
 * tier S/A — is the golden-source set the canonical chapter pipeline
 * (12-04) merges chapter structure from.
 */
class GoldenSources(
    private val mikoDbManager: MikoDbManager = Injekt.get(),
    private val bridge: MangaNovelIdBridge = Injekt.get(),
    private val mangaExtensions: MangaExtensionManager = Injekt.get(),
    private val novelExtensions: NovelExtensionManager = Injekt.get(),
    private val json: Json = Injekt.get(),
) {

    /** A provider link joined to an installed, tiered extension. */
    data class GoldenSource(
        val pkgName: String,
        val sourceIds: List<Long>,
        val name: String,
        val tier: String,
        val provider: String,
        val externalId: String?,
        val url: String?,
    )

    /** Tier rank for sorting — S best, then A, B, C; unknown last. */
    internal fun tierRank(tier: String) = when (tier.uppercase()) {
        "S" -> 0
        "A" -> 1
        "B" -> 2
        "C" -> 3
        else -> 4
    }

    /**
     * Provider links ∩ installed extensions ∩ tier ∈ {S, A}, ordered by
     * tier. Falls back to B-tier installed matches when no S/A source is
     * present — a non-golden feed beats none for structure reference.
     */
    suspend fun forWork(workId: String, medium: String): List<GoldenSource> {
        val work = bridge.byWorkId(workId) ?: return emptyList()
        return forWorkRow(work, medium)
    }

    suspend fun forWorkRow(work: MangaNovelIdBridge.WorkRow, medium: String): List<GoldenSource> {
        val links = providerLinksOf(work.providerLinks)
        if (links.isEmpty()) return emptyList()

        val installed = installedPkgs(medium)
        if (installed.isEmpty()) return emptyList()

        val byProvider = mutableMapOf<String, MutableList<GoldenSource>>()
        for ((pkg, m) in mikoDbManager.allSourceMeta()) {
            if (pkg !in installed) continue
            for (provider in m.providers) {
                byProvider.getOrPut(provider) { mutableListOf() }
                    .add(
                        GoldenSource(
                            pkgName = pkg,
                            sourceIds = installed.getValue(pkg),
                            name = m.name,
                            tier = m.tier,
                            provider = provider,
                            externalId = null,
                            url = null,
                        ),
                    )
            }
        }

        return links.flatMap { link ->
            val candidates = byProvider[link.provider] ?: return@flatMap emptyList()
            candidates.map { it.copy(externalId = link.externalId, url = link.url) }
        }
            .distinctBy { it.pkgName }
            .sortedBy { tierRank(it.tier) }
            .let { ranked ->
                ranked.filter { tierRank(it.tier) <= 1 }.ifEmpty { ranked }
            }
    }

    internal data class ProviderLink(
        val provider: String,
        val externalId: String?,
        val url: String?,
    )

    internal fun providerLinksOf(raw: String): List<ProviderLink> = try {
        json.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
            val obj = el.jsonObject
            val provider = obj["provider"]?.jsonPrimitive?.content ?: return@mapNotNull null
            ProviderLink(
                provider = provider,
                externalId = obj["external_id"]?.jsonPrimitive?.contentOrNull,
                url = obj["url"]?.jsonPrimitive?.contentOrNull,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Installed extension pkg → its source ids, for the medium. */
    private fun installedPkgs(medium: String): Map<String, List<Long>> =
        if (medium == WorkIdResolver.MEDIUM_BOOKS) {
            novelExtensions.installedExtensionsFlow.value
                .filterIsInstance<NovelExtension.Installed>()
                .associate { it.pkgName to it.sources.map { s -> s.id } }
        } else {
            mangaExtensions.installedExtensionsFlow.value
                .filterIsInstance<MangaExtension.Installed>()
                .associate { it.pkgName to it.sources.map { s -> s.id } }
        }
}
