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
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository
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
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val novelRepository: NovelRepository = Injekt.get(),
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
        // NSFW extensions are never probed for SFW works — searching a
        // safe title on a porn aggregator can only return wrong matches,
        // and a flagged ext's feed would poison the merged sequence.
        val installed = installedExts(medium)
            .let { list -> if (work.nsfw) list else list.filter { !it.isNsfw } }
        if (installed.isEmpty()) return emptyList()
        val installedByPkg = installed.associateBy { it.pkgName }
        val links = providerLinksOf(work.providerLinks)
        val meta = mikoDbManager.allSourceMeta()

        // Declared path: miko.json `providers` ∩ provider_links ∩ installed.
        val byProvider = mutableMapOf<String, MutableList<GoldenSource>>()
        for ((pkg, m) in meta) {
            val ext = installedByPkg[pkg] ?: continue
            for (provider in m.providers) {
                byProvider.getOrPut(provider) { mutableListOf() }
                    .add(
                        GoldenSource(
                            pkgName = pkg,
                            sourceIds = ext.sourceIds,
                            name = m.name,
                            tier = m.tier,
                            provider = provider,
                            externalId = null,
                            url = null,
                        ),
                    )
            }
        }
        val declared = links.flatMap { link ->
            val candidates = byProvider[link.provider] ?: return@flatMap emptyList()
            candidates.map { it.copy(externalId = link.externalId, url = link.url) }
        }

        // Heuristic path: bind installed packages whose identity
        // matches the carrier — pkg last segment vs provider slug or
        // link host ("...all.webtoons" ↔ "webtoons" ↔ webtoons.com).
        val declaredPkgs = declared.mapTo(HashSet()) { it.pkgName }
        val heuristic = links.flatMap { link ->
            val hostSlug = hostSlugOf(link.url)
            installed.filter { ext ->
                ext.pkgName !in declaredPkgs && pkgMatchesProvider(ext.pkgName, link.provider, hostSlug)
            }.map { ext ->
                GoldenSource(
                    pkgName = ext.pkgName,
                    sourceIds = ext.sourceIds,
                    name = meta[ext.pkgName]?.name ?: ext.pkgName.substringAfterLast('.'),
                    tier = meta[ext.pkgName]?.tier ?: "",
                    provider = link.provider,
                    externalId = link.externalId,
                    url = link.url,
                )
            }
        }

        val bound = (declared + heuristic).distinctBy { it.pkgName }
        if (bound.isNotEmpty()) {
            val ranked = bound.sortedBy { tierRank(it.tier) }
            return ranked.filter { tierRank(it.tier) <= 1 }.ifEmpty { ranked }
                .take(MAX_BOUND_SOURCES)
        }

        // Broad fallback: nothing link-bound — either no provider_links
        // or no carrier extension installed. Let the highest-tier
        // installed extensions title-search the work; SeriesMatcher
        // still gates identity downstream, so wrong-series feeds are
        // rejected rather than merged.
        //
        // Locally-bound sources rank first regardless of tier order:
        // an installed extension already holding a same-title row for
        // this work (library/read history) is a proven carrier — the
        // alphabetical tie-break must not cut it (e.g. Weeb Central
        // carrying a user's One-Punch Man row sorted 9th of 24).
        val localPkgs = localCarrierPkgs(work, installed, isBooks = medium == WorkIdResolver.MEDIUM_BOOKS)
        return installed
            .sortedWith(
                compareBy(
                    { if (it.pkgName in localPkgs) 0 else 1 },
                    { tierRank(meta[it.pkgName]?.tier ?: "") },
                    { it.pkgName },
                ),
            )
            .take(BROAD_FALLBACK_MAX)
            .map { ext ->
                GoldenSource(
                    pkgName = ext.pkgName,
                    sourceIds = ext.sourceIds,
                    name = meta[ext.pkgName]?.name ?: ext.pkgName.substringAfterLast('.'),
                    tier = meta[ext.pkgName]?.tier ?: "",
                    provider = "",
                    externalId = null,
                    url = null,
                )
            }
    }

    /** Second-level host label of a carrier URL ("www.webtoons.com" →
     *  "webtoons"), normalized for comparison with package segments. */
    internal fun hostSlugOf(url: String?): String? = url
        ?.let { runCatching { java.net.URI(it).host }.getOrNull() }
        ?.lowercase()
        ?.removePrefix("www.")
        ?.split('.')
        ?.let { labels -> if (labels.size >= 2) labels[labels.size - 2] else labels.firstOrNull() }
        ?.let(::normalizeKey)

    /** Normalized package tail ↔ provider slug / host match:
     *  `eu.kanade.tachiyomi.extension.all.webtoons` ↔ "webtoons",
     *  `yokai.extension.novel.en.annasarchive` ↔ "annas-archive". */
    internal fun pkgMatchesProvider(pkgName: String, provider: String, hostSlug: String?): Boolean {
        val seg = normalizeKey(pkgName.substringAfterLast('.'))
        return seg.isNotEmpty() &&
            (seg == normalizeKey(provider) || (hostSlug != null && seg == hostSlug))
    }

    /**
     * Packages of installed extensions that already hold a local entry
     * for this work — favorites + read-but-not-favorited rows whose
     * normalized title matches any of the work's titles. These are
     * proven carriers; identity is still verified downstream, so a
     * coincidental title match can only waste one probe, never poison
     * the merge.
     */
    private suspend fun localCarrierPkgs(
        work: MangaNovelIdBridge.WorkRow,
        installed: List<InstalledExt>,
        isBooks: Boolean,
    ): Set<String> {
        val wanted = listOfNotNull(work.title, work.romanizedTitle, work.nativeTitle)
            .map(TitleNormalizer::normalize)
            .filter { it.isNotEmpty() }
            .toSet()
        if (wanted.isEmpty()) return emptySet()
        val pkgBySourceId = installed
            .flatMap { e -> e.sourceIds.map { it to e.pkgName } }
            .toMap()
        val rows: List<Pair<Long, String>> = if (isBooks) {
            novelRepository.getNovelFavorites().map { it.source to it.title }
        } else {
            (mangaRepository.getMangaFavorites() + mangaRepository.getReadMangaNotInLibrary())
                .map { it.source to it.title }
        }
        return rows.mapNotNull { (sourceId, title) ->
            if (sourceId == 0L || TitleNormalizer.normalize(title) !in wanted) {
                return@mapNotNull null
            }
            pkgBySourceId[sourceId]
        }.toSet()
    }

    private fun normalizeKey(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

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

    private companion object {
        /** Cap on link-bound sources — bounds parallel collector fan-out. */
        const val MAX_BOUND_SOURCES = 6

        /** Cap on broad-fallback search candidates — every candidate is
         *  a getSearchManga + details + chapterList round trip. */
        const val BROAD_FALLBACK_MAX = 6
    }

    /** An installed extension the canonical pipeline may drive. */
    internal data class InstalledExt(
        val pkgName: String,
        val sourceIds: List<Long>,
        val isNsfw: Boolean,
    )

    /** Installed extensions for the medium, pkg-keyed with source ids. */
    private fun installedExts(medium: String): List<InstalledExt> =
        if (medium == WorkIdResolver.MEDIUM_BOOKS) {
            novelExtensions.installedExtensionsFlow.value
                .filterIsInstance<NovelExtension.Installed>()
                .map { InstalledExt(it.pkgName, it.sources.map { s -> s.id }, it.isNsfw) }
        } else {
            mangaExtensions.installedExtensionsFlow.value
                .filterIsInstance<MangaExtension.Installed>()
                .map { InstalledExt(it.pkgName, it.sources.map { s -> s.id }, it.isNsfw) }
        }
}
