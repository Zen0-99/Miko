package eu.kanade.tachiyomi.ui.browse.shows.migration.search

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.shows.interactor.GetAnime
import tachiyomi.domain.entries.shows.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ShowsMigrateSearchScreenDialogScreenModel(
    val animeId: Long,
    getAnime: GetAnime = Injekt.get(),
) : StateScreenModel<ShowsMigrateSearchScreenDialogScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            val anime = getAnime.await(animeId)!!

            mutableState.update {
                it.copy(anime = anime)
            }
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update {
            it.copy(dialog = dialog)
        }
    }

    @Immutable
    data class State(
        val anime: Anime? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data class Migrate(val anime: Anime) : Dialog
    }
}
