package eu.kanade.tachiyomi.ui.entries.shows

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.domain.entries.shows.interactor.UpdateAnime
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.util.editBackground
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.shows.interactor.GetAnime
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.entries.shows.model.AnimeUpdate
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

class ShowsImageScreenModel(
    private val animeId: Long,
    private val getAnime: GetAnime = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    private val coverCache: AnimeCoverCache = Injekt.get(),
    private val backgroundCache: AnimeBackgroundCache = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val preferenceStore: tachiyomi.core.common.preference.PreferenceStore = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    val pagerState: PagerState = PagerState(pageCount = { 2 }),
) : StateScreenModel<Anime?>(null) {

    private val isCover: Boolean
        get() = pagerState.currentPage != 1

    /** An image mutation landed while the dialog is open — the snackbar
     *  is deferred to [flushImageToast] so picking stays silent. */
    @Volatile
    private var pendingImageToast = false

    init {
        screenModelScope.launchIO {
            getAnime.subscribe(animeId)
                .collect { newAnime -> mutableState.update { newAnime } }
        }
    }

    fun saveImage(context: Context) {
        val savedStringResource = if (isCover) {
            MR.strings.cover_saved
        } else {
            AYMR.strings.background_saved
        }
        val errorSavingStringResource = if (isCover) {
            MR.strings.error_saving_cover
        } else {
            AYMR.strings.error_saving_background
        }
        screenModelScope.launch {
            try {
                saveImageInternal(context, temp = false)
                snackbarHostState.showSnackbar(
                    context.stringResource(savedStringResource),
                    withDismissAction = true,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(errorSavingStringResource),
                    withDismissAction = true,
                )
            }
        }
    }

    fun shareImage(context: Context) {
        val errorSharingStringResource = if (isCover) {
            MR.strings.error_sharing_cover
        } else {
            AYMR.strings.error_sharing_background
        }
        screenModelScope.launch {
            try {
                val uri = saveImageInternal(context, temp = true) ?: return@launch
                withUIContext {
                    context.startActivity(uri.toShareIntent(context))
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(errorSharingStringResource),
                    withDismissAction = true,
                )
            }
        }
    }

    /**
     * Save anime image Bitmap to picture or temporary share directory.
     *
     * @param context The context for building and executing the ImageRequest
     * @return the uri to saved file
     */
    private suspend fun saveImageInternal(context: Context, temp: Boolean): Uri? {
        val anime = state.value ?: return null
        val req = ImageRequest.Builder(context)
            .data(anime)
            .size(Size.ORIGINAL)
            .build()

        return withIOContext {
            val result = context.imageLoader.execute(req).image?.asDrawable(context.resources)

            // TODO: Handle animated image
            val bitmap = result?.getBitmapOrNull() ?: return@withIOContext null
            imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    name = if (isCover) "cover" else "background",
                    location = if (temp) Location.Cache else Location.Pictures(anime.title),
                ),
            )
        }
    }

    /**
     * Update image with local file.
     *
     * @param context Context.
     * @param data uri of the image resource.
     */
    fun editImage(context: Context, data: Uri) {
        val anime = state.value ?: return
        screenModelScope.launchIO {
            context.contentResolver.openInputStream(data)?.use {
                try {
                    if (isCover) {
                        anime.editCover(Injekt.get(), it, updateAnime, coverCache)
                    } else {
                        anime.editBackground(Injekt.get(), it, updateAnime, backgroundCache)
                    }
                    pendingImageToast = true
                } catch (e: Exception) {
                    notifyFailedImageUpdate(context, e)
                }
            }
        }
    }

    fun deleteCustomImage(context: Context) {
        val animeId = state.value?.id ?: return
        screenModelScope.launchIO {
            try {
                if (isCover) {
                    coverCache.deleteCustomCover(animeId)
                    updateAnime.awaitUpdateCoverLastModified(animeId)
                } else {
                    backgroundCache.deleteCustomBackground(animeId)
                    updateAnime.awaitUpdateBackgroundLastModified(animeId)
                }
                pendingImageToast = true
            } catch (e: Exception) {
                notifyFailedImageUpdate(context, e)
            }
        }
    }

    /**
     * Pin a strip-picked artwork entry-wide: the bytes land in the
     * custom-cover cache (which already outranks season/provider art on
     * every surface) and the option key persists so the strip can mark
     * the pinned tile. Always the COVER file — the strip only exists on
     * the cover page.
     */
    fun pinCover(context: Context, option: eu.kanade.presentation.entries.components.CoverOption) {
        val anime = state.value ?: return
        screenModelScope.launchIO {
            try {
                val req = ImageRequest.Builder(context)
                    .data(option.data)
                    .size(Size.ORIGINAL)
                    .build()
                val bitmap = context.imageLoader.execute(req).image
                    ?.asDrawable(context.resources)
                    ?.getBitmapOrNull()
                    ?: throw IllegalStateException("cover decode failed")
                val bytes = java.io.ByteArrayOutputStream().also { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }.toByteArray()
                // Direct cache write — editCover() gates on `favorite`,
                // which would silently drop the pin on a browsed entry.
                bytes.inputStream().use {
                    coverCache.setCustomCoverToCache(anime, it)
                }
                // Persist the pick as the row's thumbnail too — surfaces
                // that re-read thumbnail_url on reload would otherwise
                // fall back to the auto art and drop the pin.
                updateAnime.await(
                    AnimeUpdate(
                        id = anime.id,
                        thumbnailUrl = (option.data as? String)
                            ?: "file://${coverCache.getCustomCoverFile(anime.id).absolutePath}",
                        coverLastModified = Instant.now().toEpochMilli(),
                    ),
                )
                preferenceStore.getString(
                    eu.kanade.presentation.entries.components.coverPinPrefKey(
                        eu.kanade.presentation.entries.components.COVER_PIN_KIND_ANIME,
                        anime.id,
                    ),
                    "",
                ).set(option.key)
                // The commit path dismisses the dialog immediately —
                // the dismiss-flush would race this coroutine and
                // swallow the toast, so pin/unpin toast on success
                // themselves. In-dialog edits still batch.
                notifyImageUpdated(context)
            } catch (e: Exception) {
                notifyFailedImageUpdate(context, e)
            }
        }
    }

    /** Clear the cover pin — deletes the custom cover + marker pref. */
    fun unpinCover(context: Context) {
        val animeId = state.value?.id ?: return
        screenModelScope.launchIO {
            try {
                coverCache.deleteCustomCover(animeId)
                updateAnime.awaitUpdateCoverLastModified(animeId)
                preferenceStore.getString(
                    eu.kanade.presentation.entries.components.coverPinPrefKey(
                        eu.kanade.presentation.entries.components.COVER_PIN_KIND_ANIME,
                        animeId,
                    ),
                    "",
                ).delete()
                notifyImageUpdated(context)
            } catch (e: Exception) {
                notifyFailedImageUpdate(context, e)
            }
        }
    }

    /**
     * Image mutations inside the dialog stay silent while the user is
     * still picking — this fires the single "updated" snackbar when the
     * dialog dismisses with changes pending.
     */
    fun flushImageToast(context: Context) {
        if (pendingImageToast) {
            pendingImageToast = false
            notifyImageUpdated(context)
        }
    }

    private fun notifyImageUpdated(context: Context) {
        val updatedStringResource = if (isCover) {
            MR.strings.cover_updated
        } else {
            AYMR.strings.background_updated
        }
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(updatedStringResource),
                withDismissAction = true,
            )
        }
    }

    private fun notifyFailedImageUpdate(context: Context, e: Throwable) {
        val updateFailedStringResource = if (isCover) {
            MR.strings.notification_cover_update_failed
        } else {
            AYMR.strings.notification_background_update_failed
        }
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(updateFailedStringResource),
                withDismissAction = true,
            )
            logcat(LogPriority.ERROR, e)
        }
    }
}
