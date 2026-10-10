package miko.ui.repos

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.DeleteMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.ReplaceMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.CreateNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.DeleteNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.ReplaceNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.UpdateNovelExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionRepoService

/**
 * Consolidated repos screen model — one StateFlow for all three repo types,
 * mirroring `ConsolidatedExtensionReposScreen` + the per-type Android screen
 * models. `addRepo` probes the repo and creates it in every matching tab;
 * fingerprint conflicts surface a replace dialog.
 */
class ExtensionReposScreenModel(
    private val getAnimeExtensionRepo: GetAnimeExtensionRepo,
    private val getMangaExtensionRepo: GetMangaExtensionRepo,
    private val getNovelExtensionRepo: GetNovelExtensionRepo,
    private val createAnimeExtensionRepo: CreateAnimeExtensionRepo,
    private val createMangaExtensionRepo: CreateMangaExtensionRepo,
    private val createNovelExtensionRepo: CreateNovelExtensionRepo,
    private val deleteAnimeExtensionRepo: DeleteAnimeExtensionRepo,
    private val deleteMangaExtensionRepo: DeleteMangaExtensionRepo,
    private val deleteNovelExtensionRepo: DeleteNovelExtensionRepo,
    private val replaceAnimeExtensionRepo: ReplaceAnimeExtensionRepo,
    private val replaceMangaExtensionRepo: ReplaceMangaExtensionRepo,
    private val replaceNovelExtensionRepo: ReplaceNovelExtensionRepo,
    private val updateAnimeExtensionRepo: UpdateAnimeExtensionRepo,
    private val updateMangaExtensionRepo: UpdateMangaExtensionRepo,
    private val updateNovelExtensionRepo: UpdateNovelExtensionRepo,
    private val extensionRepoService: ExtensionRepoService,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val scope = CoroutineScope(Job() + dispatcher)

    enum class RepoTab(val probeKey: String) {
        ANIME("anime"),
        MANGA("manga"),
        NOVEL("novel"),
    }

    enum class RepoEvent { InvalidUrl, RepoAlreadyExists }

    sealed interface RepoDialog {
        data class Delete(val type: RepoTab, val repo: ExtensionRepo) : RepoDialog
        data class Conflict(val type: RepoTab, val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : RepoDialog
    }

    data class State(
        val isInitialized: Boolean = false,
        val animeRepos: List<ExtensionRepo> = emptyList(),
        val mangaRepos: List<ExtensionRepo> = emptyList(),
        val novelRepos: List<ExtensionRepo> = emptyList(),
        val isProbing: Boolean = false,
        val event: RepoEvent? = null,
        val dialog: RepoDialog? = null,
        /** Tab to scroll to after a probe — mirrors Android's page jump. */
        val targetTab: RepoTab? = null,
    ) {
        fun reposFor(tab: RepoTab): List<ExtensionRepo> = when (tab) {
            RepoTab.ANIME -> animeRepos
            RepoTab.MANGA -> mangaRepos
            RepoTab.NOVEL -> novelRepos
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            combine(
                getAnimeExtensionRepo.subscribeAll(),
                getMangaExtensionRepo.subscribeAll(),
                getNovelExtensionRepo.subscribeAll(),
            ) { anime, manga, novel ->
                State(
                    isInitialized = true,
                    animeRepos = anime,
                    mangaRepos = manga,
                    novelRepos = novel,
                )
            }.collect { repos ->
                _state.value = _state.value.copy(
                    isInitialized = repos.isInitialized,
                    animeRepos = repos.animeRepos,
                    mangaRepos = repos.mangaRepos,
                    novelRepos = repos.novelRepos,
                )
            }
        }
    }

    /**
     * Probes the URL, then creates the repo in every matching tab — a combined
     * repo (e.g. MikoDB serving novels + manga + anime plugins) registers in
     * all three. Navigates the caller to the first matching tab.
     */
    fun addRepo(repoUrl: String) {
        scope.launch {
            _state.value = _state.value.copy(isProbing = true, event = null)
            try {
                val types = extensionRepoService.probeRepoTypes(repoUrl)
                val firstTab = RepoTab.entries.firstOrNull { it.probeKey in types }
                _state.value = _state.value.copy(targetTab = firstTab)

                types.forEach { type ->
                    when (type) {
                        "anime" -> mapResult(createAnimeExtensionRepo.await(repoUrl), RepoTab.ANIME)
                        "manga" -> mapResult(createMangaExtensionRepo.await(repoUrl), RepoTab.MANGA)
                        "novel" -> mapResult(createNovelExtensionRepo.await(repoUrl), RepoTab.NOVEL)
                    }
                }
            } finally {
                _state.value = _state.value.copy(isProbing = false)
            }
        }
    }

    private fun mapResult(result: Any, type: RepoTab) {
        when (result) {
            is CreateAnimeExtensionRepo.Result.DuplicateFingerprint ->
                _state.value = _state.value.copy(
                    dialog = RepoDialog.Conflict(type, result.oldRepo, result.newRepo),
                )
            is CreateMangaExtensionRepo.Result.DuplicateFingerprint ->
                _state.value = _state.value.copy(
                    dialog = RepoDialog.Conflict(type, result.oldRepo, result.newRepo),
                )
            is CreateNovelExtensionRepo.Result.DuplicateFingerprint ->
                _state.value = _state.value.copy(
                    dialog = RepoDialog.Conflict(type, result.oldRepo, result.newRepo),
                )
            CreateAnimeExtensionRepo.Result.InvalidUrl,
            CreateMangaExtensionRepo.Result.InvalidUrl,
            CreateNovelExtensionRepo.Result.InvalidUrl,
            -> _state.value = _state.value.copy(event = RepoEvent.InvalidUrl)
            CreateAnimeExtensionRepo.Result.RepoAlreadyExists,
            CreateMangaExtensionRepo.Result.RepoAlreadyExists,
            CreateNovelExtensionRepo.Result.RepoAlreadyExists,
            -> _state.value = _state.value.copy(event = RepoEvent.RepoAlreadyExists)
            else -> {}
        }
    }

    fun deleteRepo(type: RepoTab, baseUrl: String) {
        scope.launch {
            when (type) {
                RepoTab.ANIME -> deleteAnimeExtensionRepo.await(baseUrl)
                RepoTab.MANGA -> deleteMangaExtensionRepo.await(baseUrl)
                RepoTab.NOVEL -> deleteNovelExtensionRepo.await(baseUrl)
            }
        }
    }

    fun migrateConflict(type: RepoTab, newRepo: ExtensionRepo) {
        scope.launch {
            when (type) {
                RepoTab.ANIME -> replaceAnimeExtensionRepo.await(newRepo)
                RepoTab.MANGA -> replaceMangaExtensionRepo.await(newRepo)
                RepoTab.NOVEL -> replaceNovelExtensionRepo.await(newRepo)
            }
        }
    }

    fun refreshTab(tab: RepoTab) {
        scope.launch {
            when (tab) {
                RepoTab.ANIME -> updateAnimeExtensionRepo.awaitAll()
                RepoTab.MANGA -> updateMangaExtensionRepo.awaitAll()
                RepoTab.NOVEL -> updateNovelExtensionRepo.awaitAll()
            }
        }
    }

    fun showDialog(dialog: RepoDialog) {
        _state.value = _state.value.copy(dialog = dialog)
    }

    fun dismissDialog() {
        _state.value = _state.value.copy(dialog = null)
    }

    fun consumeEvent() {
        _state.value = _state.value.copy(event = null)
    }

    fun consumeTargetTab() {
        _state.value = _state.value.copy(targetTab = null)
    }

    fun dispose() = scope.cancel()
}
