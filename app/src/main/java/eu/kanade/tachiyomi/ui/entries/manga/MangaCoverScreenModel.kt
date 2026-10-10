package eu.kanade.tachiyomi.ui.entries.manga

import android.content.Context
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
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
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

class MangaCoverScreenModel(
    private val mangaId: Long,
    private val getManga: GetManga = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    private val coverCache: MangaCoverCache = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val preferenceStore: tachiyomi.core.common.preference.PreferenceStore = Injekt.get(),
    private val coverUpdates: eu.kanade.tachiyomi.data.cover.CoverUpdateBus = Injekt.get(),

    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<Manga?>(null) {

    /** A cover mutation landed while the dialog is open — the snackbar
     *  is deferred to [flushCoverToast] so picking stays silent. */
    @Volatile
    private var pendingCoverToast = false

    init {
        screenModelScope.launchIO {
            getManga.subscribe(mangaId)
                .collect { newManga -> mutableState.update { newManga } }
        }
    }

    fun saveCover(context: Context, coverData: Any? = null) {
        screenModelScope.launch {
            try {
                saveCoverInternal(context, temp = false, coverData = coverData)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.cover_saved),
                    withDismissAction = true,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_saving_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    fun shareCover(context: Context, coverData: Any? = null) {
        screenModelScope.launch {
            try {
                val uri = saveCoverInternal(context, temp = true, coverData = coverData) ?: return@launch
                withUIContext {
                    context.startActivity(uri.toShareIntent(context))
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_sharing_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    /**
     * Save manga cover Bitmap to picture or temporary share directory.
     *
     * @param context The context for building and executing the ImageRequest
     * @return the uri to saved file
     */
    private suspend fun saveCoverInternal(context: Context, temp: Boolean, coverData: Any?): Uri? {
        val manga = state.value ?: return null
        val req = ImageRequest.Builder(context)
            .data(coverData ?: manga)
            .size(Size.ORIGINAL)
            .build()

        return withIOContext {
            val result = context.imageLoader.execute(req).image?.asDrawable(context.resources)

            // TODO: Handle animated cover
            val bitmap = result?.getBitmapOrNull() ?: return@withIOContext null
            imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    name = manga.title,
                    location = if (temp) Location.Cache else Location.Pictures.create(),
                ),
            )
        }
    }

    /**
     * Update cover with local file.
     *
     * @param context Context.
     * @param data uri of the cover resource.
     */
    fun editCover(context: Context, data: Uri) {
        val manga = state.value ?: return
        screenModelScope.launchIO {
            context.contentResolver.openInputStream(data)?.use {
                try {
                    manga.editCover(Injekt.get(), it, updateManga, coverCache)
                    // editCover() silently no-ops on non-favorites — only
                    // repaint surfaces when the file actually landed.
                    val file = coverCache.getCustomCoverFile(manga.id)
                    if (file.exists()) {
                        // A gallery pick has no remote url — the file
                        // path is the art: persist it as the row's
                        // thumbnail so unbound surfaces + cold reloads
                        // keep it too.
                        val fileUri = "file://${file.absolutePath}"
                        updateManga.await(
                            MangaUpdate(
                                id = manga.id,
                                thumbnailUrl = fileUri,
                                coverLastModified = Instant.now().toEpochMilli(),
                            ),
                        )
                        coverUpdates.emit(manga.id, manga.url, fileUri, manual = true)
                        pendingCoverToast = true
                    }
                } catch (e: Exception) {
                    notifyFailedCoverUpdate(context, e)
                }
            }
        }
    }

    fun deleteCustomCover(context: Context) = deleteCustomCover(context, toastNow = false)

    // `toastNow` toasts on success itself — the commit path dismisses the
    // dialog immediately, so the dismiss-flush would race the coroutine
    // and swallow it. In-dialog deletes still batch through the flag.
    private fun deleteCustomCover(context: Context, toastNow: Boolean) {
        val manga = state.value ?: return
        screenModelScope.launchIO {
            try {
                coverCache.deleteCustomCover(manga.id)
                updateManga.awaitUpdateCoverLastModified(manga.id)
                preferenceStore.getString(
                    eu.kanade.presentation.entries.components.coverPinPrefKey(
                        eu.kanade.presentation.entries.components.COVER_PIN_KIND_MANGA,
                        manga.id,
                    ),
                    "",
                ).delete()
                // Repaint cells back to the automatic art — the pinned
                // file is gone so surfaces fall through to the url.
                manga.thumbnailUrl?.let {
                    coverUpdates.emit(manga.id, manga.url, it, manual = true)
                }
                if (toastNow) {
                    notifyCoverUpdated(context)
                } else {
                    pendingCoverToast = true
                }
            } catch (e: Exception) {
                notifyFailedCoverUpdate(context, e)
            }
        }
    }

    /**
     * Pin a strip-picked artwork entry-wide: the bytes land in the
     * custom-cover cache (which already outranks volume/provider art on
     * every surface) and the option key persists so the strip can mark
     * the pinned tile.
     */
    fun pinCover(context: Context, option: eu.kanade.presentation.entries.components.CoverOption) {
        val manga = state.value ?: return
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
                    coverCache.setCustomCoverToCache(manga, it)
                }
                // Persist the pick as the row's thumbnail too: the bus
                // only reaches live surfaces — cold reloads and feeds
                // that don't subscribe re-read the canonical row's
                // thumbnail_url and would re-heal back to the auto art.
                updateManga.await(
                    MangaUpdate(
                        id = manga.id,
                        thumbnailUrl = (option.data as? String)
                            ?: "file://${coverCache.getCustomCoverFile(manga.id).absolutePath}",
                        coverLastModified = Instant.now().toEpochMilli(),
                    ),
                )
                preferenceStore.getString(
                    eu.kanade.presentation.entries.components.coverPinPrefKey(
                        eu.kanade.presentation.entries.components.COVER_PIN_KIND_MANGA,
                        manga.id,
                    ),
                    "",
                ).set(option.key)
                // Repaint cells with the picked art — the bus patches
                // bound+unbound discovery rows and feed caches where the
                // custom-cover file path can't reach (work-hash ids).
                coverUpdates.emit(
                    manga.id,
                    manga.url,
                    (option.data as? String)
                        ?: "file://${coverCache.getCustomCoverFile(manga.id).absolutePath}",
                    manual = true,
                )
                notifyCoverUpdated(context)
            } catch (e: Exception) {
                notifyFailedCoverUpdate(context, e)
            }
        }
    }

    /** Clear the pin — same custom-cover delete plus the marker pref. */
    fun unpinCover(context: Context) = deleteCustomCover(context, toastNow = true)

    /**
     * Cover mutations inside the dialog stay silent while the user is
     * still picking — this fires the single "cover updated" snackbar
     * when the dialog dismisses with changes pending.
     */
    fun flushCoverToast(context: Context) {
        if (pendingCoverToast) {
            pendingCoverToast = false
            notifyCoverUpdated(context)
        }
    }

    private fun notifyCoverUpdated(context: Context) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.cover_updated),
                withDismissAction = true,
            )
        }
    }

    private fun notifyFailedCoverUpdate(context: Context, e: Throwable) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.notification_cover_update_failed),
                withDismissAction = true,
            )
            logcat(LogPriority.ERROR, e)
        }
    }
}
