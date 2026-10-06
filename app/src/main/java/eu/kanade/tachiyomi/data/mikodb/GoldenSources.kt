package eu.kanade.tachiyomi.data.mikodb

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.entries.shows.repository.AnimeRepository
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
    private val json: Json = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val novelRepository: NovelRepository = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val mangaExtensions: MangaExtensionManager = Injekt.get(),
    private val novelExtensions: NovelExtensionManager = Injekt.get(),
    private val animeExtensions: AnimeExtensionManager = Injekt.get(),
    private val providerTiers: ProviderTiers = Injekt.get(),
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
        /** Installed extension's icon — the provider sheets (17-02)
         *  render it via the browse-screen ExtensionIcon logic. */
        val icon: Drawable? = null,
    )

    /**
     * Provider links ∩ installed extensions ∩ tier ∈ {S, A}, ordered by
     * tier. Falls back to B-tier installed matches when no S/A source is
     * present — a non-golden feed beats none for structure reference.
     */
    suspend fun forWork(workId: String, medium: String): List<GoldenSource> {
        val work = bridge.byWorkId(workId) ?: return emptyList()
        return forWorkRow(work, medium)
    }

    /**
     * Capped candidate set for the background pipeline — bounds parallel
     * fan-out. Bound sources rank by tier (S/A only unless none exist);
     * broad fallback takes the top [BROAD_FALLBACK_MAX].
     */
    suspend fun forWorkRow(work: MangaNovelIdBridge.WorkRow, medium: String): List<GoldenSource> {
        val pool = candidatePool(work, medium) ?: return emptyList()
        if (pool.bound.isNotEmpty()) {
            val ranked = pool.bound.sortedBy { tierRank(it.tier) }
            return ranked.filter { tierRank(it.tier) <= 1 }.ifEmpty { ranked }
                .take(MAX_BOUND_SOURCES)
        }
        return pool.broad.take(BROAD_FALLBACK_MAX)
    }

    /**
     * Uncapped candidates for the interactive Find Content Provider
     * search (17-02): bound sources first (they direct-fetch by URL),
     * then every other installed extension ranked local-carrier/tier.
     * The user explicitly wants a full fan-out — the caps exist for the
     * background pipeline, not a user-driven search.
     */
    suspend fun allForWorkRow(work: MangaNovelIdBridge.WorkRow, medium: String): List<GoldenSource> {
        val pool = candidatePool(work, medium) ?: return emptyList()
        return pool.bound.sortedBy { tierRank(it.tier) } + pool.broad
    }

    private class CandidatePool(
        val bound: List<GoldenSource>,
        val broad: List<GoldenSource>,
    )

    /**
     * Bound (link-joined) and broad (title-search) candidates; broad
     * excludes bound pkgs. NSFW-flagged extensions sort last for SFW
     * works but are never excluded — the flag marks hosting capability,
     * not content scope, and SeriesMatcher still gates every feed.
     */
    private suspend fun candidatePool(
        work: MangaNovelIdBridge.WorkRow,
        medium: String,
    ): CandidatePool? {
        // NSFW-flagged extensions are NOT excluded even for SFW works —
        // the flag means "hosts some adult titles", not "porn-only";
        // mainstream sources (Weeb Central, MangaFire, MangaKatana…)
        // are flagged yet carry the SFW catalog, and silently dropping
        // them removed proven carriers from Find Provider entirely.
        // For SFW works they sort to the end of `broad` so clean sources
        // are probed first; SeriesMatcher gates identity regardless.
        val installed = installedExts(medium)
        if (installed.isEmpty()) return null
        val installedByPkg = installed.associateBy { it.pkgName }
        val links = providerLinksOf(work.providerLinks)
        val meta = mikoDbManager.allSourceMeta()

        // Declared path: miko.json `providers` ∩ provider_links ∩ installed.
        // Phase 20-04: every tier stamped on a candidate is the
        // EFFECTIVE tier — manifest overlaid with the worker's
        // community-verified promotion / report-cap demotion in the
        // single ProviderTiers seam. Readers of GoldenSource.tier
        // (ranking here, collector feeds, router carriers, the Find
        // Provider sheet) see the composed value for free.
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
                            tier = providerTiers.effectiveTier(pkg),
                            provider = provider,
                            externalId = null,
                            url = null,
                            icon = ext.icon,
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
                    tier = providerTiers.effectiveTier(ext.pkgName),
                    provider = link.provider,
                    externalId = link.externalId,
                    url = link.url,
                    icon = ext.icon,
                )
            }
        }

        val bound = (declared + heuristic).distinctBy { it.pkgName }
        val boundPkgs = bound.mapTo(HashSet()) { it.pkgName }

        // Broad path: every other installed extension title-searches the
        // work; SeriesMatcher still gates identity downstream, so
        // wrong-series feeds are rejected rather than merged.
        //
        // Locally-bound sources rank first regardless of tier order:
        // an installed extension already holding a same-title row for
        // this work (library/read history) is a proven carrier — the
        // alphabetical tie-break must not cut it (e.g. Weeb Central
        // carrying a user's One-Punch Man row sorted 9th of 24).
        val localPkgs = localCarrierPkgs(work, installed, medium)
        val broad = installed
            .filter { it.pkgName !in boundPkgs }
            .sortedWith(
                compareBy(
                    { if (it.pkgName in localPkgs) 0 else 1 },
                    { if (!work.nsfw && it.isNsfw) 1 else 0 },
                    { tierRank(providerTiers.effectiveTier(it.pkgName)) },
                    { it.pkgName },
                ),
            )
            .map { ext ->
                GoldenSource(
                    pkgName = ext.pkgName,
                    sourceIds = ext.sourceIds,
                    name = meta[ext.pkgName]?.name ?: ext.pkgName.substringAfterLast('.'),
                    tier = providerTiers.effectiveTier(ext.pkgName),
                    provider = "",
                    externalId = null,
                    url = null,
                    icon = ext.icon,
                )
            }
        return CandidatePool(bound, broad)
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
        medium: String,
    ): Set<String> {
        val wanted = listOfNotNull(work.title, work.romanizedTitle, work.nativeTitle)
            .map(TitleNormalizer::normalize)
            .filter { it.isNotEmpty() }
            .toSet()
        if (wanted.isEmpty()) return emptySet()
        val pkgBySourceId = installed
            .flatMap { e -> e.sourceIds.map { it to e.pkgName } }
            .toMap()
        val rows: List<Pair<Long, String>> = when (medium) {
            WorkIdResolver.MEDIUM_BOOKS ->
                novelRepository.getNovelFavorites().map { it.source to it.title }
            WorkIdResolver.MEDIUM_SHOWS ->
                animeRepository.getAnimeFavorites().map { it.source to it.title } +
                    animeRepository.getLibraryAnime()
                        .filter { !it.anime.favorite }
                        .map { it.anime.source to it.anime.title }
            else ->
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

        /** Bound on waiting for a manager's first extension enumeration
         *  — a cold process shouldn't stall the sheet forever. */
        private const val INIT_TIMEOUT_MS = 10_000L
    }

    /** An installed extension the canonical pipeline may drive. */
    internal data class InstalledExt(
        val pkgName: String,
        val sourceIds: List<Long>,
        val isNsfw: Boolean,
        val icon: Drawable? = null,
    )

    /** Installed extensions for the medium, pkg-keyed with source ids.
     *  Awaits the manager's first extension-load pass — Find Provider
     *  opened early in a session otherwise enumerates a partial (or
     *  empty) list and misses carriers. */
    private suspend fun installedExts(medium: String): List<InstalledExt> {
        val manager = when (medium) {
            WorkIdResolver.MEDIUM_BOOKS -> novelExtensions.isInitialized
            WorkIdResolver.MEDIUM_SHOWS -> animeExtensions.isInitialized
            else -> mangaExtensions.isInitialized
        }
        withTimeoutOrNull(INIT_TIMEOUT_MS) { manager.first { it } }
        return when (medium) {
            WorkIdResolver.MEDIUM_BOOKS ->
                novelExtensions.installedExtensionsFlow.value
                    .filterIsInstance<NovelExtension.Installed>()
                    .map { InstalledExt(it.pkgName, it.sources.map { s -> s.id }, it.isNsfw, it.icon) }
            WorkIdResolver.MEDIUM_SHOWS ->
                animeExtensions.installedExtensionsFlow.value
                    .filterIsInstance<AnimeExtension.Installed>()
                    .map { InstalledExt(it.pkgName, it.sources.map { s -> s.id }, it.isNsfw, it.icon) }
            else ->
                mangaExtensions.installedExtensionsFlow.value
                    .filterIsInstance<MangaExtension.Installed>()
                    .map { InstalledExt(it.pkgName, it.sources.map { s -> s.id }, it.isNsfw, it.icon) }
        }
    }
}

/** THE tier rank for the mikodb package — S best, then A, B, C;
 *  unknown last. File scope so the 20-04 overlay ([ProviderTiers]),
 *  the router, and the screen models share one definition (the old
 *  per-class copies were drift risk). */
internal fun tierRank(tier: String) = when (tier.uppercase()) {
    "S" -> 0
    "A" -> 1
    "B" -> 2
    "C" -> 3
    else -> 4
}
