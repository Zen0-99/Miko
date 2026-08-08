package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.IBinder
import android.text.Spanned
import android.text.Html
import android.text.SpannableString
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.novelsource.model.NovelComment
import eu.kanade.tachiyomi.novelsource.model.SNovelChapterImpl
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.items.chapter.interactor.GetNovelChapter
import eu.kanade.domain.items.chapter.interactor.SetNovelReadStatus
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackService
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsServiceConnection
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.history.novel.interactor.UpsertNovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelReaderScreenModel(
    private val novelId: Long,
    private val chapterId: Long?,
    private val getNovel: GetNovel = Injekt.get(),
    private val getNovelWithChapters: GetNovelWithChapters = Injekt.get(),
    private val getNovelChapter: GetNovelChapter = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val setReadStatus: SetNovelReadStatus = Injekt.get(),
    private val upsertNovelHistory: UpsertNovelHistory = Injekt.get(),
    private val downloadManager: eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager = Injekt.get(),
    private val downloadProvider: eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider = Injekt.get(),
    private val updateNovelChapter: tachiyomi.domain.items.chapter.interactor.UpdateNovelChapter = Injekt.get(),
    val preferences: NovelReaderPreferences = Injekt.get(),
    val readerPreferences: ReaderPreferences = Injekt.get(),
    val ttsPreferences: NovelTtsPreferences = Injekt.get(),
    val positionTracker: CharacterPositionTracker = Injekt.get(),
) : StateScreenModel<NovelReaderScreenModel.State>(State()) {

    private var currentChapterIndex = 0
    private var currentSource: NovelCatalogueSource? = null

    /** Periodic auto-save coroutine job â€” saves position every 15 seconds. */
    private var periodicSaveJob: kotlinx.coroutines.Job? = null

    private val _events = MutableSharedFlow<NovelReaderEvent>()
    val events: SharedFlow<NovelReaderEvent> = _events.asSharedFlow()

    private val _contentItems = MutableStateFlow<List<TextItem>>(emptyList())
    val contentItems = _contentItems.asStateFlow()

    // Starts true: the screen always loads its first chapter on init, so
    // auto-scroll and other consumers should wait until content is ready.
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _currentChapter = MutableStateFlow<NovelChapter?>(null)
    val currentChapter = _currentChapter.asStateFlow()

    private val _novel = MutableStateFlow<Novel?>(null)
    val novel = _novel.asStateFlow()

    private val _accentColor = MutableStateFlow<Int?>(null)
    val accentColor = _accentColor.asStateFlow()

    private val _chapters = MutableStateFlow<List<NovelChapter>>(emptyList())
    val chapters = _chapters.asStateFlow()

    private val _isControlsVisible = MutableStateFlow(true)
    val isControlsVisible = _isControlsVisible.asStateFlow()

    private val _isSettingsVisible = MutableStateFlow(false)
    val isSettingsVisible = _isSettingsVisible.asStateFlow()

    private val _isChaptersSheetVisible = MutableStateFlow(false)
    val isChaptersSheetVisible = _isChaptersSheetVisible.asStateFlow()

    // ===== Comments state =====
    private val _isCommentsDialogVisible = MutableStateFlow(false)
    val isCommentsDialogVisible = _isCommentsDialogVisible.asStateFlow()

    private val _comments = MutableStateFlow<List<NovelComment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments = _isLoadingComments.asStateFlow()

    private val _commentsError = MutableStateFlow<String?>(null)
    val commentsError = _commentsError.asStateFlow()

    /** Whether the current source supports comments. */
    val supportsComments: Boolean
        get() = currentSource?.supportsComments == true

    private val _progressPercent = MutableStateFlow(0)
    val progressPercent = _progressPercent.asStateFlow()

    // Progress catchup tracking for infinite scroll: when a new chapter becomes
    // current but the user is already partway through it, we show 0% and
    // accumulate the "hidden" offset faster so the percentage feels natural.

    // Theme colors pushed from the Compose side (MaterialTheme.colorScheme).
    // Updated via refreshTextConfig(themeBg, themeText) â€” kept as a StateFlow
    // so the combined textConfig flow below reacts to theme changes too.
    private val _themeColors = MutableStateFlow<Pair<Int?, Int?>>(null to null)

    /**
     * Flow-based TextConfig: auto-rebuilds whenever any text-affecting
     * preference OR the theme colors change. This replaces the old manual
     * refreshTextConfig() pattern â€” settings changes are now reflected
     * immediately without callers needing to trigger a refresh.
     *
     * combine() supports max 5 flows, so we chain via flatMapLatest to
     * handle 27 preference flows + the theme color flow.
     */
    val textConfig: StateFlow<TextConfig> = combine(
        preferences.textSize().changes(),
        preferences.lineHeight().changes(),
        preferences.paragraphSpacing().changes(),
        preferences.sidePadding().changes(),
        preferences.textAlignment().changes(),
    ) { textSize, lineHeight, paragraphSpacing, sidePadding, textAlignment ->
        TextConfigBatch1(textSize, lineHeight, paragraphSpacing, sidePadding, textAlignment)
    }.flatMapLatest { b1 ->
        combine(
            preferences.bionicReading().changes(),
            preferences.forceBoldText().changes(),
            preferences.forceItalicText().changes(),
            preferences.forceParagraphIndent().changes(),
            preferences.preserveSourceTextAlignInNative().changes(),
        ) { bionic, bold, italic, indent, preserve ->
            TextConfigBatch2(b1, bionic, bold, italic, indent, preserve)
        }
    }.flatMapLatest { b2 ->
        combine(
            preferences.textSelectionEnabled().changes(),
            preferences.textShadowEnabled().changes(),
            preferences.textShadowBlur().changes(),
            preferences.textShadowY().changes(),
            preferences.textShadowColor().changes(),
        ) { selectable, shadowOn, shadowBlur, shadowY, shadowColor ->
            TextConfigBatch3(b2, selectable, shadowOn, shadowBlur, shadowY, shadowColor)
        }
    }.flatMapLatest { b3 ->
        combine(
            preferences.backgroundColorMode().changes(),
            preferences.backgroundColor().changes(),
            preferences.textColor().changes(),
            preferences.eInkBinarization().changes(),
            preferences.smartFitMargins().changes(),
        ) { bgMode, bgCustom, textCustom, eInk, smartFit ->
            TextConfigBatch4(b3, bgMode, bgCustom, textCustom, eInk, smartFit)
        }
    }.flatMapLatest { b4 ->
        // Nest combines: combine() supports max 5 flows.
        combine(
            preferences.typographyPreset().changes(),
            preferences.customFontFamily().changes(),
            preferences.backgroundTexture().changes(),
            preferences.nativeTextureStrength().changes(),
        ) { typoPreset, fontFamily, bgTexture, textureStrength ->
            Quad(typoPreset, fontFamily, bgTexture, textureStrength)
        }.flatMapLatest { quad ->
            combine(
                preferences.oledEdgeGradient().changes(),
                preferences.edgeFadeEnabled().changes(),
            ) { oledGrad, edgeFade ->
                TextConfigBatch5(b4, quad.first, quad.second, quad.third, quad.fourth, oledGrad, edgeFade)
            }
        }
    }.flatMapLatest { b5 ->
        combine(
            preferences.pageEdgeShadowEnabled().changes(),
            preferences.pageEdgeShadowAlpha().changes(),
            _themeColors,
        ) { pageShadowOn, pageShadowAlpha, (themeBg, themeText) ->
            buildTextConfigFromValues(
                textSize = b5.b4.b3.b2.b1.textSize,
                lineHeight = b5.b4.b3.b2.b1.lineHeight,
                paragraphSpacing = b5.b4.b3.b2.b1.paragraphSpacing,
                sidePadding = b5.b4.b3.b2.b1.sidePadding,
                textAlignment = b5.b4.b3.b2.b1.textAlignment,
                bionicReading = b5.b4.b3.b2.bionicReading,
                forceBold = b5.b4.b3.b2.forceBold,
                forceItalic = b5.b4.b3.b2.forceItalic,
                forceParagraphIndent = b5.b4.b3.b2.forceParagraphIndent,
                preserveSourceTextAlign = b5.b4.b3.b2.preserveSourceTextAlign,
                isTextSelectable = b5.b4.b3.isTextSelectable,
                textShadowEnabled = b5.b4.b3.textShadowEnabled,
                textShadowBlur = b5.b4.b3.textShadowBlur,
                textShadowY = b5.b4.b3.textShadowY,
                textShadowColor = b5.b4.b3.textShadowColor,
                backgroundColorMode = b5.b4.backgroundColorMode,
                backgroundColorCustom = b5.b4.backgroundColorCustom,
                textColorCustom = b5.b4.textColorCustom,
                eInkBinarization = b5.b4.eInkBinarization,
                smartFitMargins = b5.b4.smartFitMargins,
                typographyPreset = b5.typographyPreset,
                customFontFamily = b5.customFontFamily,
                backgroundTexture = b5.backgroundTexture,
                textureStrength = b5.textureStrength,
                oledEdgeGradient = b5.oledEdgeGradient,
                edgeFadeEnabled = b5.edgeFadeEnabled,
                pageEdgeShadowEnabled = pageShadowOn,
                pageEdgeShadowAlpha = pageShadowAlpha,
                themeBackgroundColor = themeBg,
                themeTextColor = themeText,
            )
        }
    }.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildTextConfig(),
    )

    // Intermediate batch data classes for the chained combine pattern.
    private data class TextConfigBatch1(
        val textSize: Float, val lineHeight: Float,
        val paragraphSpacing: Int, val sidePadding: Int,
        val textAlignment: TextAlignment,
    )
    private data class TextConfigBatch2(
        val b1: TextConfigBatch1,
        val bionicReading: Boolean, val forceBold: Boolean, val forceItalic: Boolean,
        val forceParagraphIndent: Boolean, val preserveSourceTextAlign: Boolean,
    )
    private data class TextConfigBatch3(
        val b2: TextConfigBatch2,
        val isTextSelectable: Boolean, val textShadowEnabled: Boolean,
        val textShadowBlur: Float, val textShadowY: Float, val textShadowColor: String,
    )
    private data class TextConfigBatch4(
        val b3: TextConfigBatch3,
        val backgroundColorMode: NovelReaderBackgroundColor,
        val backgroundColorCustom: Int, val textColorCustom: Int,
        val eInkBinarization: Boolean, val smartFitMargins: Boolean,
    )
    private data class Quad<A, B, C, D>(
        val first: A, val second: B, val third: C, val fourth: D,
    )

    private data class TextConfigBatch5(
        val b4: TextConfigBatch4,
        val typographyPreset: NovelReaderTypographyPreset,
        val customFontFamily: String,
        val backgroundTexture: NovelReaderBackgroundTexture,
        val textureStrength: Int, val oledEdgeGradient: Boolean,
        val edgeFadeEnabled: Boolean,
    )

    private val _dictionaryQuery = MutableStateFlow<String?>(null)
    val dictionaryQuery = _dictionaryQuery.asStateFlow()

    internal var highlightManager: NovelHighlightManager? = null
        private set

    private val _showHighlightColorPicker = MutableStateFlow<String?>(null)
    val showHighlightColorPicker = _showHighlightColorPicker.asStateFlow()

    private var pendingSelectedText: String? = null

    /**
     * When previous chapter is prepended in infinite scroll, this holds the
     * number of items added. The screen's `update` callback reads and clears
     * it, then scrolls after `submitList` completes â€” avoiding a race where
     * the scroll runs before the adapter has the new list.
     */
    @Volatile
    var pendingScrollAdjustment: Int? = null

    /**
     * Pending scroll restoration (item index + pixel offset) for when the
     * RecyclerView becomes ready. Set in [loadChapter] from the saved
     * position, consumed in the screen's onRecyclerViewReady callback.
     *
     * Unlike the old character-position approach, this restores the exact
     * pixel-perfect scroll position (Tadami-style), eliminating both the
     * visual jump and the per-session position drift.
     */
    @Volatile
    var pendingScrollRestore: Pair<Int, Int>? = null

    /**
     * Save the current RecyclerView scroll position so it can be restored
     * when the RecyclerView is recreated (e.g. when the user navigates away
     * from the reader and returns). This is separate from [pendingScrollRestore]
     * which is set during chapter loading — this handles the case where the
     * chapter is already loaded but the RecyclerView is being recreated.
     */
    fun saveCurrentScrollPosition(itemIndex: Int, pixelOffset: Int) {
        if (itemIndex >= 0) {
            pendingScrollRestore = Pair(itemIndex, pixelOffset)
        }
    }

    /**
     * Pending chapter transition: position to scroll to after submitList completes.
     * 0 = top (next chapter), -1 = bottom (previous chapter).
     * Used to trigger the page transition animation at the exact moment the
     * adapter has the new items, avoiding the need for postDelayed.
     */
    @Volatile
    var pendingTransitionPosition: Int? = null

    fun initHighlightManager(context: Context) {
        if (highlightManager == null) {
            highlightManager = NovelHighlightManager(context)
        }
    }

    fun onTextSelected(selectedText: String) {
        // Inline color picker is now handled by the selection popup itself;
        // this hook is kept for dictionary lookup from the popup's Define action.
        if (selectedText.isNotBlank() && selectedText.length > 1) {
            pendingSelectedText = selectedText
        }
    }

    fun dismissHighlightPicker() {
        _showHighlightColorPicker.value = null
        pendingSelectedText = null
    }

    /**
     * Save a highlight with an explicit color hex (called from the inline color
     * circles in the selection popup).
     */
    fun saveHighlightWithColor(selectedText: String, colorHex: String) {
        val chapter = _currentChapter.value ?: return
        val novel = _novel.value ?: return
        val mgr = highlightManager ?: return

        screenModelScope.launchIO {
            mgr.saveHighlight(
                novelKey = NovelHighlightManager.NovelKey(title = novel.title, novelId = novel.id),
                chapterNumber = chapter.chapterNumber,
                chapterTitle = chapter.name,
                selectedText = selectedText,
                color = colorHex,
            )
            _events.emit(NovelReaderEvent.ShowMessage("Highlight saved"))
        }
    }

    fun saveHighlight(color: String) {
        val text = pendingSelectedText ?: return
        saveHighlightWithColor(text, color)
        pendingSelectedText = null
    }

    fun showDictionary(word: String) {
        _dictionaryQuery.value = word
    }

    fun dismissDictionary() {
        _dictionaryQuery.value = null
    }

    // ===== Text translation =====
    private val _translationState = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val translationState = _translationState.asStateFlow()

    fun translateText(text: String) {
        val targetLang = preferences.selectedTextTranslationTargetLang().get()
        _translationState.value = TranslationState.Loading(text)
        screenModelScope.launchIO {
            val result = eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTextTranslationService.translate(text, targetLang)
            _translationState.value = if (result != null) {
                TranslationState.Result(text, result, targetLang)
            } else {
                TranslationState.Error("Translation failed. Check your network connection.")
            }
        }
    }

    fun dismissTranslation() {
        _translationState.value = TranslationState.Idle
    }

    private var context: Context? = null
    private var tts: TextToSpeech? = null

    // ===== TTS playback service (background reading) =====
    private var ttsService: NovelTtsPlaybackService? = null
    private var ttsServiceConnection: NovelTtsServiceConnection? = null
    private var ttsStateCollectionJob: kotlinx.coroutines.Job? = null
    private var pendingTtsStart: Triple<List<String>, Int, String>? = null

    private val _ttsPlaybackState = MutableStateFlow(NovelTtsPlaybackState())
    val ttsPlaybackState = _ttsPlaybackState.asStateFlow()

    /** Whether TTS playback is currently active (initialized or playing). */
    val isTtsActive: Boolean
        get() = _ttsPlaybackState.value.isInitialized || _ttsPlaybackState.value.isPlaying

    // ===== Neural TTS voice management =====
    private val neuralVoiceManager by lazy {
        eu.kanade.tachiyomi.ui.reader.novel.tts.NeuralVoiceManager(
            context ?: Injekt.get<Context>(),
            ttsPreferences,
        )
    }
    private val _installedNeuralVoices = MutableStateFlow<List<eu.kanade.tachiyomi.ui.reader.novel.tts.InstalledNeuralVoice>>(emptyList())
    val installedNeuralVoices = _installedNeuralVoices.asStateFlow()

    private val _downloadingVoiceId = MutableStateFlow<String?>(null)
    val downloadingVoiceId = _downloadingVoiceId.asStateFlow()

    private val _voiceDownloadProgress = MutableStateFlow(0f)
    val voiceDownloadProgress = _voiceDownloadProgress.asStateFlow()

    /** Refresh the list of installed neural voices from disk. */
    fun refreshInstalledNeuralVoices() {
        screenModelScope.launchIO {
            _installedNeuralVoices.value = neuralVoiceManager.getInstalledVoices()
        }
    }

    /** Download a neural TTS voice bundle. */
    fun downloadNeuralVoice(entry: eu.kanade.tachiyomi.ui.reader.novel.tts.NeuralVoiceEntry) {
        if (_downloadingVoiceId.value != null) return // don't allow concurrent downloads
        screenModelScope.launchIO {
            _downloadingVoiceId.value = entry.id
            _voiceDownloadProgress.value = 0f
            val result = neuralVoiceManager.downloadVoice(entry) { progress ->
                _voiceDownloadProgress.value = progress
            }
            _downloadingVoiceId.value = null
            _voiceDownloadProgress.value = 0f
            result.onSuccess {
                _installedNeuralVoices.value = neuralVoiceManager.getInstalledVoices()
            }.onFailure { error ->
                _events.tryEmit(NovelReaderEvent.ShowError(error.message ?: "Download failed"))
            }
        }
    }

    /** Uninstall a neural TTS voice. */
    fun uninstallNeuralVoice(voiceId: String) {
        screenModelScope.launchIO {
            neuralVoiceManager.uninstallVoice(voiceId)
            _installedNeuralVoices.value = neuralVoiceManager.getInstalledVoices()
            // If the uninstalled voice was selected, clear the selection
            if (ttsPreferences.voiceName().get() == voiceId) {
                ttsPreferences.voiceName().set("")
                ttsPreferences.neuralModelPath().set("")
            }
        }
    }

    /** Select an installed neural voice as the active voice. */
    fun selectNeuralVoice(voice: eu.kanade.tachiyomi.ui.reader.novel.tts.InstalledNeuralVoice) {
        ttsPreferences.voiceName().set(voice.voiceId)
        ttsPreferences.neuralModelPath().set(voice.path.absolutePath)
        ttsPreferences.neuralModelType().set(voice.family)
    }

    fun initContext(context: Context) {
        this.context = context.applicationContext
    }

    fun copyToClipboard(text: String) {
        val ctx = context ?: return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Novel text", text))
        screenModelScope.launchIO {
            _events.emit(NovelReaderEvent.ShowMessage("Copied to clipboard"))
        }
    }

    fun showMessage(message: String) {
        screenModelScope.launchIO {
            _events.emit(NovelReaderEvent.ShowMessage(message))
        }
    }

    fun shareText(text: String) {
        val ctx = context ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun readAloud(text: String) {
        val ctx = context ?: return
        if (tts == null) {
            tts = TextToSpeech(ctx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    speakText(text)
                }
            }
        } else {
            speakText(text)
        }
    }

    private fun speakText(text: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "novelReadAloud")
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    fun shutdownTts() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    // ===== TTS playback service methods =====

    /**
     * Start TTS playback of the full chapter via the foreground service.
     * Extracts paragraph texts from the current content items.
     */
    fun startTtsPlayback(startIndex: Int = 0) {
        val ctx = context ?: return
        val items = _contentItems.value
        val paragraphs = items
            .filterIsInstance<TextItem.Paragraph>()
            .map { it.text.toString() }
            .filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) return

        val title = currentChapter.value?.name ?: novel.value?.title ?: "Novel"

        // Start the foreground service
        NovelTtsPlaybackService.start(ctx)

        // Bind to the service if not already bound
        if (ttsServiceConnection == null) {
            pendingTtsStart = Triple(paragraphs, startIndex, title)
            ttsServiceConnection = NovelTtsServiceConnection(
                onConnected = { service ->
                    ttsService = service
                    // Start collecting playback state
                    ttsStateCollectionJob?.cancel()
                    ttsStateCollectionJob = screenModelScope.launchIO {
                        service.playbackState.collect { state ->
                            _ttsPlaybackState.value = state
                        }
                    }
                    // If there's a pending start request, execute it now
                    pendingTtsStart?.let { (paras, idx, ttl) ->
                        service.startReading(paras, idx, ttl)
                        pendingTtsStart = null
                    }
                },
                onDisconnected = {
                    ttsService = null
                    ttsStateCollectionJob?.cancel()
                    ttsStateCollectionJob = null
                },
            )
            ctx.bindService(
                Intent(ctx, NovelTtsPlaybackService::class.java),
                ttsServiceConnection!!,
                Context.BIND_AUTO_CREATE,
            )
        } else {
            // Already bound â€” start reading directly
            ttsService?.startReading(paragraphs, startIndex, title)
        }
    }

    fun pauseTtsPlayback() {
        ttsService?.pause()
    }

    fun resumeTtsPlayback() {
        ttsService?.play()
    }

    fun nextTtsParagraph() {
        ttsService?.next()
    }

    fun previousTtsParagraph() {
        ttsService?.previous()
    }

    fun stopTtsPlayback() {
        ttsService?.stop()
        // Keep the current text visible during the fade-out animation.
        // Clear isPlaying/isInitialized so AnimatedVisibility starts fading out,
        // but preserve currentText so it fades with the overlay rather than
        // disappearing instantly.
        val currentText = _ttsPlaybackState.value.currentText
        _ttsPlaybackState.value = _ttsPlaybackState.value.copy(
            isPlaying = false,
            isInitialized = false,
            currentText = currentText,
        )
        // Clear the text after the fade-out animation has time to complete
        screenModelScope.launchIO {
            kotlinx.coroutines.delay(400)
            if (!_ttsPlaybackState.value.isPlaying && !_ttsPlaybackState.value.isInitialized) {
                _ttsPlaybackState.value = NovelTtsPlaybackState()
            }
        }
    }

    /**
     * Unbind from the TTS service and clean up.
     * Called when the reader is closed.
     */
    fun unbindTtsService() {
        val ctx = context
        ttsStateCollectionJob?.cancel()
        ttsStateCollectionJob = null
        ttsServiceConnection?.let { conn ->
            if (ctx != null) {
                runCatching { ctx.unbindService(conn) }
            }
        }
        ttsServiceConnection = null
        ttsService = null
        _ttsPlaybackState.value = NovelTtsPlaybackState()
    }

    /**
     * Build a TextConfig from explicit parameter values (used by the
     * flow-based textConfig combinator). This avoids re-reading preferences
     * on every flow emission â€” the values are already provided by the
     * upstream preference flows.
     */
    @Suppress("LongParameterList")
    private fun buildTextConfigFromValues(
        textSize: Float,
        lineHeight: Float,
        paragraphSpacing: Int,
        sidePadding: Int,
        textAlignment: TextAlignment,
        bionicReading: Boolean,
        forceBold: Boolean,
        forceItalic: Boolean,
        forceParagraphIndent: Boolean,
        preserveSourceTextAlign: Boolean,
        isTextSelectable: Boolean,
        textShadowEnabled: Boolean,
        textShadowBlur: Float,
        textShadowY: Float,
        textShadowColor: String,
        backgroundColorMode: NovelReaderBackgroundColor,
        backgroundColorCustom: Int,
        textColorCustom: Int,
        eInkBinarization: Boolean,
        smartFitMargins: Boolean,
        typographyPreset: NovelReaderTypographyPreset,
        customFontFamily: String,
        backgroundTexture: NovelReaderBackgroundTexture,
        textureStrength: Int,
        oledEdgeGradient: Boolean,
        edgeFadeEnabled: Boolean,
        pageEdgeShadowEnabled: Boolean,
        pageEdgeShadowAlpha: Float,
        themeBackgroundColor: Int?,
        themeTextColor: Int?,
    ): TextConfig {
        val (backgroundColor, textColor) = when (backgroundColorMode) {
            NovelReaderBackgroundColor.WHITE -> android.graphics.Color.WHITE to android.graphics.Color.BLACK
            NovelReaderBackgroundColor.BLACK -> android.graphics.Color.BLACK to android.graphics.Color.WHITE
            NovelReaderBackgroundColor.GRAY -> android.graphics.Color.parseColor("#FF202020") to android.graphics.Color.WHITE
            NovelReaderBackgroundColor.CUSTOM -> backgroundColorCustom to textColorCustom
            NovelReaderBackgroundColor.SMART_THEME -> {
                if (themeBackgroundColor != null && themeTextColor != null) {
                    themeBackgroundColor to themeTextColor
                } else {
                    val ctx = context
                    if (ctx != null && isDark(ctx)) {
                        android.graphics.Color.parseColor("#FF202020") to android.graphics.Color.WHITE
                    } else {
                        android.graphics.Color.WHITE to android.graphics.Color.BLACK
                    }
                }
            }
        }
        val effectiveTextColor = if (eInkBinarization) android.graphics.Color.BLACK else textColor
        val effectiveBackgroundColor = if (eInkBinarization) android.graphics.Color.WHITE else backgroundColor

        val effectivePadding = if (smartFitMargins) {
            val dm = context?.resources?.displayMetrics
            val screenWidthDp: Int = dm?.let { (it.widthPixels / it.density).toInt() } ?: 0
            when {
                screenWidthDp <= 360 -> 8
                screenWidthDp <= 480 -> 16
                screenWidthDp <= 720 -> 32
                else -> 48
            }
        } else {
            sidePadding
        }

        val (effectiveTextSize, effectiveLineSpacing) = when (typographyPreset) {
            NovelReaderTypographyPreset.SUPERGOLDEN -> textSize to (textSize * 0.618f)
            NovelReaderTypographyPreset.GOLDEN -> textSize to (textSize * 0.33f)
            NovelReaderTypographyPreset.CUSTOM -> textSize to lineHeight
        }

        val customTypeface: Typeface? = if (customFontFamily.isNotBlank()) {
            runCatching {
                context?.assets?.let { assets ->
                    val possiblePaths = listOf(
                        "fonts/$customFontFamily.ttf",
                        "fonts/$customFontFamily.otf",
                        "fonts/$customFontFamily",
                    )
                    val path = possiblePaths.firstOrNull { runCatching { assets.open(it) }.isSuccess }
                    if (path != null) Typeface.createFromAsset(assets, path) else null
                }
            }.getOrNull()
        } else {
            null
        }

        return TextConfig(
            textSize = effectiveTextSize,
            textColor = effectiveTextColor,
            backgroundColor = effectiveBackgroundColor,
            textFont = customTypeface,
            lineSpacing = effectiveLineSpacing,
            paragraphSpacing = paragraphSpacing,
            horizontalPadding = effectivePadding,
            isTextSelectable = isTextSelectable,
            textAlignment = textAlignment,
            bionicReading = bionicReading,
            forceBold = forceBold,
            forceItalic = forceItalic,
            forceParagraphIndent = forceParagraphIndent,
            preserveSourceTextAlign = preserveSourceTextAlign,
            textShadowEnabled = textShadowEnabled,
            textShadowColor = textShadowColor,
            textShadowBlur = textShadowBlur,
            textShadowX = preferences.textShadowX().get(),
            textShadowY = textShadowY,
            backgroundTexture = backgroundTexture,
            textureStrength = textureStrength,
            oledEdgeGradient = oledEdgeGradient,
            edgeFadeEnabled = edgeFadeEnabled,
            pageEdgeShadowEnabled = pageEdgeShadowEnabled,
            pageEdgeShadowAlpha = pageEdgeShadowAlpha,
        )
    }

    private fun buildTextConfig(themeBackgroundColor: Int? = null, themeTextColor: Int? = null): TextConfig {
        val bgColorMode = preferences.backgroundColorMode().get()
        val (backgroundColor, textColor) = when (bgColorMode) {
            NovelReaderBackgroundColor.WHITE -> android.graphics.Color.WHITE to android.graphics.Color.BLACK
            NovelReaderBackgroundColor.BLACK -> android.graphics.Color.BLACK to android.graphics.Color.WHITE
            NovelReaderBackgroundColor.GRAY -> android.graphics.Color.parseColor("#FF202020") to android.graphics.Color.WHITE
            NovelReaderBackgroundColor.CUSTOM -> {
                preferences.backgroundColor().get() to preferences.textColor().get()
            }
            NovelReaderBackgroundColor.SMART_THEME -> {
                // Smart-by-theme: use the actual app Material theme colors.
                if (themeBackgroundColor != null && themeTextColor != null) {
                    themeBackgroundColor to themeTextColor
                } else {
                    // Fallback before Compose is ready: detect night mode from context.
                    val ctx = context
                    if (ctx != null && isDark(ctx)) {
                        android.graphics.Color.parseColor("#FF202020") to android.graphics.Color.WHITE
                    } else {
                        android.graphics.Color.WHITE to android.graphics.Color.BLACK
                    }
                }
            }
        }
        // E-Ink binarization: force pure black on white
        val effectiveTextColor = if (preferences.eInkBinarization().get()) {
            android.graphics.Color.BLACK
        } else {
            textColor
        }
        val effectiveBackgroundColor = if (preferences.eInkBinarization().get()) {
            android.graphics.Color.WHITE
        } else {
            backgroundColor
        }

        // Smart-fit margins: use smaller padding on narrow screens, larger on wide
        val effectivePadding = if (preferences.smartFitMargins().get()) {
            val dm = context?.resources?.displayMetrics
            val screenWidthDp: Int = dm?.let { (it.widthPixels / it.density).toInt() } ?: 0
            when {
                screenWidthDp <= 360 -> 8
                screenWidthDp <= 480 -> 16
                screenWidthDp <= 720 -> 32
                else -> 48
            }
        } else {
            preferences.sidePadding().get()
        }

        // Typography preset: override lineSpacing with mathematical ratios
        // lineSpacing is the extra spacing in sp added via setLineSpacing(spacing, 1f)
        val baseTextSize = preferences.textSize().get()
        val baseLineHeight = preferences.lineHeight().get()
        val (effectiveTextSize, effectiveLineSpacing) = when (preferences.typographyPreset().get()) {
            NovelReaderTypographyPreset.SUPERGOLDEN -> {
                // Super Golden ratio: line height = text size Ã— 1.618
                // lineSpacing (extra sp) = textSize Ã— (1.618 - 1) â‰ˆ textSize Ã— 0.618
                baseTextSize to (baseTextSize * 0.618f)
            }
            NovelReaderTypographyPreset.GOLDEN -> {
                // Golden ratio: line height = text size Ã— 1.33
                // lineSpacing (extra sp) = textSize Ã— (1.33 - 1) â‰ˆ textSize Ã— 0.33
                baseTextSize to (baseTextSize * 0.33f)
            }
            NovelReaderTypographyPreset.CUSTOM -> {
                baseTextSize to baseLineHeight
            }
        }

        // Custom font family: load Typeface from assets/fonts/ if specified
        val customFontFamily = preferences.customFontFamily().get()
        val customTypeface: Typeface? = if (customFontFamily.isNotBlank()) {
            runCatching {
                context?.assets?.let { assets ->
                    val possiblePaths = listOf(
                        "fonts/$customFontFamily.ttf",
                        "fonts/$customFontFamily.otf",
                        "fonts/$customFontFamily",
                    )
                    val path = possiblePaths.firstOrNull { runCatching { assets.open(it) }.isSuccess }
                    if (path != null) Typeface.createFromAsset(assets, path) else null
                }
            }.getOrNull()
        } else {
            null
        }

        return TextConfig(
            textSize = effectiveTextSize,
            textColor = effectiveTextColor,
            backgroundColor = effectiveBackgroundColor,
            textFont = customTypeface,
            lineSpacing = effectiveLineSpacing,
            paragraphSpacing = preferences.paragraphSpacing().get(),
            horizontalPadding = effectivePadding,
            isTextSelectable = preferences.textSelectionEnabled().get(),
            textAlignment = preferences.textAlignment().get(),
            bionicReading = preferences.bionicReading().get(),
            forceBold = preferences.forceBoldText().get(),
            forceItalic = preferences.forceItalicText().get(),
            forceParagraphIndent = preferences.forceParagraphIndent().get(),
            preserveSourceTextAlign = preferences.preserveSourceTextAlignInNative().get(),
            textShadowEnabled = preferences.textShadowEnabled().get(),
            textShadowColor = preferences.textShadowColor().get(),
            textShadowBlur = preferences.textShadowBlur().get(),
            textShadowX = preferences.textShadowX().get(),
            textShadowY = preferences.textShadowY().get(),
            backgroundTexture = preferences.backgroundTexture().get(),
            textureStrength = preferences.nativeTextureStrength().get(),
            oledEdgeGradient = preferences.oledEdgeGradient().get(),
            edgeFadeEnabled = preferences.edgeFadeEnabled().get(),
            pageEdgeShadowEnabled = preferences.pageEdgeShadowEnabled().get(),
            pageEdgeShadowAlpha = preferences.pageEdgeShadowAlpha().get(),
        )
    }

    /**
     * Update the theme colors used by SMART_THEME background mode.
     * This triggers the flow-based textConfig to rebuild automatically.
     * The old manual refresh is no longer needed â€” preference changes
     * are collected via flows, and theme changes flow through here.
     */
    fun refreshTextConfig(themeBackgroundColor: Int? = null, themeTextColor: Int? = null) {
        _themeColors.value = themeBackgroundColor to themeTextColor
    }

    private fun isDark(context: Context): Boolean {
        return (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    val textConfigValue: TextConfig
        get() = textConfig.value

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
    )

    fun initialize() {
        screenModelScope.launchIO {
            try {
                mutableState.update { it.copy(loading = true, error = null) }

                val novel = getNovel.await(novelId)
                if (novel == null) {
                    mutableState.update { it.copy(loading = false, error = "Novel not found") }
                    return@launchIO
                }
                _novel.value = novel

                // Extract cover accent color for reader UI
                val cachedBase = eu.kanade.tachiyomi.util.novel.NovelCoverMetadata.getBaseColor(novel.id)
                if (cachedBase != null) {
                    _accentColor.value = cachedBase
                } else {
                    // Extract asynchronously
                    val ctx = context
                    if (ctx != null) {
                        launchIO {
                            val base = eu.kanade.presentation.entries.novel.components.extractNovelCoverBaseColor(
                                context = ctx,
                                cover = novel.asNovelCover(),
                            )
                            if (base != null) {
                                eu.kanade.tachiyomi.util.novel.NovelCoverMetadata.setBaseColor(novel.id, base)
                                eu.kanade.tachiyomi.util.novel.NovelCoverMetadata.savePrefs()
                                _accentColor.value = base
                            }
                        }
                    }
                }

                val source = sourceManager.getOrStub(novel.source) as? NovelCatalogueSource
                currentSource = source
                if (source == null) {
                    mutableState.update { it.copy(loading = false, error = "Source not available") }
                    return@launchIO
                }

                val chapterList = getNovelWithChapters.awaitChapters(novelId)
                // Sort by chapter number ascending to ensure story order (Chapter 1, 2, 3...)
                // regardless of how the source returns chapters.
                _chapters.value = chapterList.sortedBy { it.chapterNumber }

                val targetChapter = if (chapterId != null) {
                    // Fetch directly from DB to get fresh lastCharRead data,
                    // since the chapter list may be cached/stale.
                    getNovelChapter.await(chapterId) ?: chapterList.find { it.id == chapterId }
                } else {
                    chapterList.find { !it.read } ?: chapterList.firstOrNull()
                }

                if (targetChapter != null) {
                    currentChapterIndex = chapterList.indexOf(targetChapter)
                    loadChapter(targetChapter)
                } else {
                    mutableState.update { it.copy(loading = false, error = "No chapters available") }
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = "Failed: ${e.message}") }
            }
        }
    }

    private suspend fun loadChapter(chapter: NovelChapter) {
        android.util.Log.d("NovelReader", "loadChapter: '${chapter.name}' (id=${chapter.id})")
        _currentChapter.value = chapter

        // Check if the chapter is cached (loaded, downloaded, or prefetched) before
        // showing the loading indicator. If cached, the load is instant and
        // the loading circle would flash unnecessarily.
        val novel = _novel.value
        val source = currentSource
        val isLoadedCached = loadedChapterCache.containsKey(chapter.id)
        val isDownloaded = novel != null && source != null && downloadManager.isChapterDownloaded(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            novelTitle = novel.title,
            sourceId = source.id,
        )
        val isPrefetched = prefetchedChapterCache.containsKey(chapter.id)
        val isCached = isLoadedCached || isDownloaded || isPrefetched
        android.util.Log.d("NovelReader", "loadChapter: isCached=$isCached (loadedCached=$isLoadedCached downloaded=$isDownloaded prefetched=$isPrefetched)")

        if (!isCached) {
            _isLoading.value = true
            mutableState.update { it.copy(loading = true) }
        }
        upsertHistory(chapter)

        try {
            if (source == null) {
                mutableState.update { it.copy(loading = false, error = "No source available") }
                return
            }

            // Try loaded chapter cache first (fully parsed items, instant)
            val wrappedItems: List<TextItem>
            var initialBatchShown = false
            if (isLoadedCached) {
                android.util.Log.d("NovelReader", "loadChapter: using loadedChapterCache (instant)")
                wrappedItems = loadedChapterCache[chapter.id]!!
            } else {
                // Try downloaded content first (offline reading / instant load)
                var htmlContent: String? = null
                if (isDownloaded && novel != null) {
                    val chapterFile = downloadProvider.findChapterDir(
                        chapterName = chapter.name,
                        chapterScanlator = chapter.scanlator,
                        novelTitle = novel.title,
                        source = source,
                    )
                    if (chapterFile != null) {
                        android.util.Log.d("NovelReader", "loadChapter: reading downloaded file ${chapterFile.name}")
                        val fileContent = chapterFile.openInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
                        // Only use the downloaded file if it has actual content.
                        // A blank/empty file means the download failed silently —
                        // fall back to prefetch cache or source fetch below.
                        htmlContent = fileContent.takeIf { it.isNotBlank() }
                        android.util.Log.d("NovelReader", "loadChapter: downloaded file content length=${fileContent.length}, usable=${htmlContent != null}")
                    } else {
                        android.util.Log.w("NovelReader", "loadChapter: isDownloaded=true but findChapterDir returned null!")
                    }
                }

                // Fall back to prefetched cache (instant load if prefetched)
                if (htmlContent.isNullOrBlank()) {
                    prefetchedChapterCache.remove(chapter.id)?.let { cached ->
                        android.util.Log.d("NovelReader", "loadChapter: using prefetched cache (length=${cached.length})")
                        htmlContent = cached
                    }
                }

                // Fall back to fetching from source
                if (htmlContent == null) {
                    android.util.Log.d("NovelReader", "loadChapter: fetching from source (network)")
                    val sChapter = SNovelChapterImpl().apply {
                        url = chapter.url
                        name = chapter.name
                    }
                    htmlContent = source.getChapterText(sChapter)
                    android.util.Log.d("NovelReader", "loadChapter: source returned content length=${htmlContent?.length}")
                }

                // Progressive parsing: parse the first 30 paragraphs instantly,
                // show them to the user, then parse the rest in the background.
                // This makes chapter loading feel instant even for very long
                // chapters — the user sees text within ~50ms instead of waiting
                // for the entire chapter to be parsed.
                val parseStart = System.currentTimeMillis()

                val items = parseHtmlToParagraphsProgressive(
                    html = htmlContent,
                    chapterId = chapter.id,
                    initialBatchSize = 30,
                ) { batch ->
                    // Phase 1 callback: show the initial batch immediately.
                    val batchWrapped = wrapWithNavigation(batch, chapter)
                    android.util.Log.d("NovelReader", "loadChapter: initial batch ${batch.size} items shown in ${System.currentTimeMillis() - parseStart}ms")
                    _contentItems.value = batchWrapped
                    resetLoadedChapters(chapter.id)
                    mutableState.update { it.copy(loading = false, error = null) }
                    _events.emit(NovelReaderEvent.ChapterChanged(chapter.name))
                    initialBatchShown = true

                    // Restore saved scroll position after the initial batch is shown.
                    val saved = positionTracker.loadSavedPosition(chapter)
                    if (saved != null && (saved.characterPosition > 0 || saved.itemIndex > 0)) {
                        android.util.Log.d("NovelReader", "loadChapter: restoring scroll pos (progressive) itemIndex=${saved.itemIndex} pixelOffset=${saved.pixelOffset}")
                        positionTracker.startReadingSession(saved.characterPosition)
                        pendingScrollRestore = Pair(saved.itemIndex, saved.pixelOffset)
                    } else {
                        positionTracker.startReadingSession(0)
                    }
                }

                android.util.Log.d("NovelReader", "loadChapter: full parse complete ${items.size} items in ${System.currentTimeMillis() - parseStart}ms")

                // If parsing yielded no paragraphs, the content is empty/corrupt.
                if (items.isEmpty()) {
                    if (!initialBatchShown) {
                        mutableState.update {
                            it.copy(
                                loading = false,
                                error = "This chapter has no readable content. The download may be corrupt or the source returned empty text.",
                            )
                        }
                        _events.emit(NovelReaderEvent.ShowError("Chapter content is empty"))
                    }
                    return
                }

                wrappedItems = wrapWithNavigation(items, chapter)

                // Persist total character count so the detail screen can show "% Read".
                val totalCharCount = items.filterIsInstance<TextItem.Paragraph>().lastOrNull()?.endCharIndex?.plus(1) ?: 0
                if (totalCharCount > 0 && chapter.id > 0) {
                    updateNovelChapter.await(
                        tachiyomi.domain.items.chapter.model.NovelChapterUpdate(
                            id = chapter.id,
                            totalCharCount = totalCharCount.toLong(),
                        ),
                    )
                }

                // Cache the loaded chapter for instant navigation back
                loadedChapterCache[chapter.id] = wrappedItems
            }

            if (initialBatchShown) {
                // Phase 2: replace the partial content with the full list.
                // DiffUtil will detect that the first 30 items are the same
                // (same IDs) and only append the remaining items — no visual
                // jump for items that were already visible.
                //
                // However, if the saved scroll position was beyond the initial
                // batch (e.g. itemIndex=105 but only 31 items were in the batch),
                // the onRecyclerViewReady callback clamped to item 30. Now that
                // the full list (92 items) is available, re-set
                // pendingScrollRestore so the update callback can scroll to the
                // correct position.
                android.util.Log.d("NovelReader", "loadChapter: replacing initial batch with full list (${wrappedItems.size} items)")
                val saved = positionTracker.loadSavedPosition(chapter)
                if (saved != null && saved.itemIndex >= wrappedItems.size) {
                    // Saved index is beyond even the full list — it was from
                    // infinite scroll mode. Clamp to the last item.
                    android.util.Log.d("NovelReader", "loadChapter: saved itemIndex=${saved.itemIndex} > full list size=${wrappedItems.size}, clamping")
                    pendingScrollRestore = Pair(wrappedItems.size - 1, saved.pixelOffset)
                } else if (saved != null && saved.itemIndex > 0) {
                    // Saved index is within the full list — re-set it so the
                    // update callback scrolls to the right position.
                    android.util.Log.d("NovelReader", "loadChapter: re-setting pendingScrollRestore for full list (index=${saved.itemIndex})")
                    pendingScrollRestore = Pair(saved.itemIndex, saved.pixelOffset)
                }
                _contentItems.value = wrappedItems
            } else {
                // No progressive batch was shown (loaded cache or empty content).
                // Use the original flow: set content, restore scroll, etc.
                _contentItems.value = wrappedItems
                resetLoadedChapters(chapter.id)
                mutableState.update { it.copy(loading = false, error = null) }
                _events.emit(NovelReaderEvent.ChapterChanged(chapter.name))

                // Restore saved scroll position for this chapter.
                val saved = positionTracker.loadSavedPosition(chapter)
                if (saved != null && (saved.characterPosition > 0 || saved.itemIndex > 0)) {
                    android.util.Log.d("NovelReader", "loadChapter: restoring scroll pos itemIndex=${saved.itemIndex} pixelOffset=${saved.pixelOffset} charPos=${saved.characterPosition}")
                    positionTracker.startReadingSession(saved.characterPosition)
                    pendingScrollRestore = Pair(saved.itemIndex, saved.pixelOffset)
                } else {
                    android.util.Log.d("NovelReader", "loadChapter: no saved position, starting at 0")
                    positionTracker.startReadingSession(0)
                }
            }

            // Start periodic auto-save for this reading session.
            startPeriodicSave()

            // Prefetch next chapter if enabled — caches the HTML content
            // in the background so navigation to the next chapter is instant.
            // This does NOT append to content items (no infinite scroll effect).
            // Always prefetch nearby chapters (1 behind + 5 ahead) for
            // smooth reading, regardless of the prefetch preference.
            prefetchNextChapter()
        } catch (e: Exception) {
            mutableState.update { it.copy(loading = false, error = "Failed to load chapter: ${e.message}") }
            _events.emit(NovelReaderEvent.ShowError("Failed to load chapter: ${e.message}"))
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun parseHtmlToParagraphs(html: String, chapterId: Long): List<TextItem> = withContext(Dispatchers.Default) {
        @Suppress("DEPRECATION")
        fun renderHtml(source: String): Spanned {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT)
            } else {
                Html.fromHtml(source)
            }
        }

        // Fast path: if the paragraph HTML contains no HTML tags, use plain
        // text directly instead of calling the expensive Html.fromHtml.
        // This skips the XML parser for the majority of novel paragraphs
        // (which are plain text), cutting parse time by 5-10x.
        fun renderParagraph(paragraphHtml: String): Spanned {
            if (!paragraphHtml.contains('<')) {
                return SpannableString(paragraphHtml)
            }
            return renderHtml(paragraphHtml)
        }

        val items = mutableListOf<TextItem>()
        try {
            // Clean HTML with Jsoup â€” removes style/script/noscript that cause
            // stray "-->" artifacts and massive gaps.
            val document = Jsoup.parse(html)
            document.select("style, script, noscript").remove()

            // Extract paragraphs (select <p> tags).
            val paragraphs = document.select("p")

            // Fallback: if no <p> tags, split body text by double-newlines.
            val elementsToProcess = if (paragraphs.isEmpty()) {
                val bodyText = document.body().text()
                if (bodyText.isNotBlank()) {
                    bodyText.split(Regex("\n\n+|\r\n\r\n+"))
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { line ->
                            org.jsoup.nodes.Element(
                                org.jsoup.parser.Tag.valueOf("p"),
                                "",
                            ).apply { html(line) }
                        }
                } else {
                    emptyList()
                }
            } else {
                paragraphs
            }

            var charIndex = 0
            var paragraphIndex = 0
            for (element in elementsToProcess) {
                val paragraphHtml = element.html()
                // Skip empty paragraphs (fixes weird gaps).
                if (paragraphHtml.isBlank()) continue

                val spanned = renderParagraph(paragraphHtml)
                if (spanned.isEmpty()) continue

                // Inclusive endChar: text.length=5 â†’ chars 0..4, endChar=4.
                val startChar = charIndex
                val endChar = charIndex + spanned.length - 1

                items.add(
                    TextItem.Paragraph(
                        id = (chapterId * 100000) + paragraphIndex.toLong(),
                        chapterId = chapterId,
                        paragraphIndex = paragraphIndex,
                        text = spanned,
                        startCharIndex = startChar,
                        endCharIndex = endChar,
                    ),
                )
                charIndex = endChar + 1
                paragraphIndex++
            }
        } catch (e: Exception) {
            // Fallback: if Jsoup fails, use raw HTML rendering.
            val spanned = renderHtml(html)
            if (spanned.isNotEmpty()) {
                items.add(
                    TextItem.Paragraph(
                        id = (chapterId * 100000),
                        chapterId = chapterId,
                        paragraphIndex = 0,
                        text = spanned,
                        startCharIndex = 0,
                        endCharIndex = spanned.length - 1,
                    ),
                )
            }
        }

        return@withContext items
    }

    /**
     * Progressive parse: Jsoup-preprocess the HTML, then render only the first
     * [initialBatchSize] paragraphs and call [onInitialBatch] immediately so
     * the UI can display content while the remaining paragraphs are parsed.
     *
     * The initial batch items have the same IDs and charIndex values as they
     * will in the final list, so DiffUtil correctly identifies them and the
     * RecyclerView doesn't jump when the full list replaces the partial one.
     *
     * Returns the complete list of parsed items.
     */
    private suspend fun parseHtmlToParagraphsProgressive(
        html: String,
        chapterId: Long,
        initialBatchSize: Int = 30,
        onInitialBatch: suspend (List<TextItem>) -> Unit,
    ): List<TextItem> = withContext(Dispatchers.Default) {
        @Suppress("DEPRECATION")
        fun renderHtml(source: String): Spanned {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT)
            } else {
                Html.fromHtml(source)
            }
        }

        fun renderParagraph(paragraphHtml: String): Spanned {
            if (!paragraphHtml.contains('<')) {
                return SpannableString(paragraphHtml)
            }
            return renderHtml(paragraphHtml)
        }

        val items = mutableListOf<TextItem>()
        try {
            val document = Jsoup.parse(html)
            document.select("style, script, noscript").remove()
            val paragraphs = document.select("p")

            val elementsToProcess = if (paragraphs.isEmpty()) {
                val bodyText = document.body().text()
                if (bodyText.isNotBlank()) {
                    bodyText.split(Regex("\n\n+|\r\n\r\n+"))
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { line ->
                            org.jsoup.nodes.Element(
                                org.jsoup.parser.Tag.valueOf("p"),
                                "",
                            ).apply { html(line) }
                        }
                } else {
                    emptyList()
                }
            } else {
                paragraphs
            }

            // Phase 1: parse the first [initialBatchSize] non-empty paragraphs
            // and emit them immediately so the UI can render.
            var charIndex = 0
            var paragraphIndex = 0
            var emitted = 0
            val batch = mutableListOf<TextItem>()
            var lastProcessedElementIdx = -1
            for ((idx, element) in elementsToProcess.withIndex()) {
                val paragraphHtml = element.html()
                if (paragraphHtml.isBlank()) continue

                val spanned = renderParagraph(paragraphHtml)
                if (spanned.isEmpty()) continue

                val startChar = charIndex
                val endChar = charIndex + spanned.length - 1

                val item = TextItem.Paragraph(
                    id = (chapterId * 100000) + paragraphIndex.toLong(),
                    chapterId = chapterId,
                    paragraphIndex = paragraphIndex,
                    text = spanned,
                    startCharIndex = startChar,
                    endCharIndex = endChar,
                )
                batch.add(item)
                items.add(item)
                charIndex = endChar + 1
                paragraphIndex++
                emitted++
                lastProcessedElementIdx = idx

                if (emitted >= initialBatchSize) break
            }

            // Emit the initial batch — caller sets content and clears loading.
            // Switch to Main dispatcher so UI state updates happen on the main thread.
            if (batch.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    onInitialBatch(batch.toList())
                }
            }

            // Phase 2: parse the remaining paragraphs.
            for (i in (lastProcessedElementIdx + 1) until elementsToProcess.size) {
                val element = elementsToProcess[i]
                val paragraphHtml = element.html()
                if (paragraphHtml.isBlank()) continue

                val spanned = renderParagraph(paragraphHtml)
                if (spanned.isEmpty()) continue

                val startChar = charIndex
                val endChar = charIndex + spanned.length - 1

                items.add(
                    TextItem.Paragraph(
                        id = (chapterId * 100000) + paragraphIndex.toLong(),
                        chapterId = chapterId,
                        paragraphIndex = paragraphIndex,
                        text = spanned,
                        startCharIndex = startChar,
                        endCharIndex = endChar,
                    ),
                )
                charIndex = endChar + 1
                paragraphIndex++
            }
        } catch (e: Exception) {
            if (items.isEmpty()) {
                val spanned = renderHtml(html)
                if (spanned.isNotEmpty()) {
                    items.add(
                        TextItem.Paragraph(
                            id = (chapterId * 100000),
                            chapterId = chapterId,
                            paragraphIndex = 0,
                            text = spanned,
                            startCharIndex = 0,
                            endCharIndex = spanned.length - 1,
                        ),
                    )
                }
            }
        }

        return@withContext items
    }

    private fun wrapWithNavigation(items: List<TextItem>, chapter: NovelChapter): List<TextItem> {
        val result = mutableListOf<TextItem>()

        // Look up chapter by id â€” during infinite scroll, currentChapterIndex
        // may not match the chapter being wrapped.
        val chapterIndex = _chapters.value.indexOfFirst { it.id == chapter.id }
        val hasPrev = chapterIndex > 0
        val hasNext = chapterIndex >= 0 && chapterIndex < _chapters.value.size - 1

        // Hide nav buttons in INFINITE_SCROLL mode (chapters load automatically).
        val showNavButtons = readingMode != NovelReadingMode.INFINITE_SCROLL

        // Previous Chapter button at top (only if not first chapter AND not infinite scroll).
        if (hasPrev && showNavButtons) {
            result.add(
                TextItem.ChapterNavigation(
                    id = chapter.id * 1000000 - 10,
                    direction = TextItem.LoadDirection.PREVIOUS,
                    chapterTitle = _chapters.value.getOrNull(chapterIndex - 1)?.name ?: "",
                    isEnabled = true,
                ),
            )
        }

        // Chapter header â€” skip when joining chapters (infinite scroll only, not first chapter)
        val isJoinMode = preferences.joinChapters().get() &&
            readingMode == NovelReadingMode.INFINITE_SCROLL &&
            hasPrev
        if (!isJoinMode) {
            result.add(
                TextItem.ChapterHeader(
                    id = chapter.id * 1000000 - 1,
                    chapterId = chapter.id,
                    chapterTitle = chapter.name,
                ),
            )
        }

        // All content items (paragraphs).
        result.addAll(items)

        // Next Chapter button at bottom (only if not infinite scroll).
        if (showNavButtons) {
            result.add(
                TextItem.ChapterNavigation(
                    id = chapter.id * 1000000 - 11,
                    direction = TextItem.LoadDirection.NEXT,
                    chapterTitle = if (hasNext) _chapters.value.getOrNull(chapterIndex + 1)?.name ?: "" else "",
                    isEnabled = hasNext,
                ),
            )
        }

        return result
    }

    fun navigateToPreviousChapter() {
        if (currentChapterIndex > 0) {
            currentChapterIndex--
            val chapter = _chapters.value[currentChapterIndex]
            screenModelScope.launchIO {
                // Only clear content if the chapter is NOT cached.
                val isCached = loadedChapterCache.containsKey(chapter.id) ||
                    prefetchedChapterCache.containsKey(chapter.id) ||
                    (_novel.value?.let { novel ->
                        currentSource?.let { src ->
                            downloadManager.isChapterDownloaded(
                                chapterName = chapter.name,
                                chapterScanlator = chapter.scanlator,
                                novelTitle = novel.title,
                                sourceId = src.id,
                            )
                        }
                    } ?: false)

                if (preferences.readingMode().get() != NovelReadingMode.INFINITE_SCROLL && !isCached) {
                    _contentItems.value = emptyList()
                }
                loadChapter(chapter)
                // Scroll to bottom of previous chapter for natural back-nav.
                if (preferences.readingMode().get() != NovelReadingMode.INFINITE_SCROLL) {
                    _events.emit(NovelReaderEvent.ScrollToPosition(-1))
                }
            }
        }
    }

    fun navigateToNextChapter() {
        if (currentChapterIndex < _chapters.value.size - 1) {
            currentChapterIndex++
            val chapter = _chapters.value[currentChapterIndex]
            screenModelScope.launchIO {
                // Only clear content if the chapter is NOT cached.
                val isCached = loadedChapterCache.containsKey(chapter.id) ||
                    prefetchedChapterCache.containsKey(chapter.id) ||
                    (_novel.value?.let { novel ->
                        currentSource?.let { src ->
                            downloadManager.isChapterDownloaded(
                                chapterName = chapter.name,
                                chapterScanlator = chapter.scanlator,
                                novelTitle = novel.title,
                                sourceId = src.id,
                            )
                        }
                    } ?: false)

                if (preferences.readingMode().get() != NovelReadingMode.INFINITE_SCROLL && !isCached) {
                    _contentItems.value = emptyList()
                }
                loadChapter(chapter)
                markPreviousChapterRead()
                if (preferences.readingMode().get() != NovelReadingMode.INFINITE_SCROLL) {
                    _events.emit(NovelReaderEvent.ScrollToPosition(0))
                }
                // Chain prefetch for nearby chapters
                prefetchNextChapter()
            }
        }
    }

    // -----------------------------------------------------------------
    // Infinite-scroll / overscroll support
    // -----------------------------------------------------------------

    /** Chapter IDs currently loaded into the content list (for dedup). */
    private val loadedChapterIds = mutableSetOf<Long>()
    /** Cache of prefetched chapter HTML content (chapterId → htmlContent). */
    private val prefetchedChapterCache = mutableMapOf<Long, String>()
    /** Cache of fully loaded chapters: chapterId → parsed TextItems. */
    private val loadedChapterCache = mutableMapOf<Long, List<TextItem>>()

    /** Max chapters to prefetch ahead (QuickNovel-style preload range). */
    private val PREFETCH_AHEAD = 5
    /** Max chapters to prefetch behind. */
    private val PREFETCH_BEHIND = 1
    /** Max entries in the prefetch cache before eviction. */
    private val MAX_PREFETCH_CACHE_SIZE = 12
    @Volatile private var isLoadingNextChapter = false
    @Volatile private var isLoadingPreviousChapter = false

    /** Current reading mode (read from preferences). */
    val readingMode: NovelReadingMode get() = preferences.readingMode().get()

    /** Tracks whether the next/prev chapter is being loaded (for UI indicators). */
    val isLoadingNext: Boolean get() = isLoadingNextChapter
    val isLoadingPrevious: Boolean get() = isLoadingPreviousChapter

    /**
     * Append the next chapter to the content list for INFINITE_SCROLL mode.
     * Called by the scroll listener when the user nears the bottom.
     */
    fun loadNextChapterInBackground() {
        if (isLoadingNextChapter) return
        if (currentChapterIndex >= _chapters.value.size - 1) return

        // Find the highest loaded chapter index to get the true next chapter.
        val highestLoadedIndex = _chapters.value.indexOfLast { loadedChapterIds.contains(it.id) }
        val actualNextIndex = if (highestLoadedIndex >= 0) highestLoadedIndex + 1 else currentChapterIndex + 1
        if (actualNextIndex >= _chapters.value.size) return

        val nextChapter = _chapters.value[actualNextIndex]
        if (loadedChapterIds.contains(nextChapter.id)) return

        screenModelScope.launchIO {
            isLoadingNextChapter = true
            try {
                // Add loading indicator at end.
                val currentItems = _contentItems.value.toMutableList()
                currentItems.add(
                    TextItem.Loading(
                        id = System.currentTimeMillis(),
                        chapterId = nextChapter.id,
                        loadingMessage = "Loading next chapter...",
                    ),
                )
                _contentItems.value = currentItems

                val source = currentSource ?: return@launchIO
                // Use prefetched cache if available (faster than re-fetching)
                var htmlContent: String? = prefetchedChapterCache.remove(nextChapter.id)
                // Try downloaded file next (offline reading)
                if (htmlContent.isNullOrBlank()) {
                    val novel = _novel.value
                    if (novel != null && downloadManager.isChapterDownloaded(
                            chapterName = nextChapter.name,
                            chapterScanlator = nextChapter.scanlator,
                            novelTitle = novel.title,
                            sourceId = source.id,
                        )
                    ) {
                        val chapterFile = downloadProvider.findChapterDir(
                            chapterName = nextChapter.name,
                            chapterScanlator = nextChapter.scanlator,
                            novelTitle = novel.title,
                            source = source,
                        )
                        if (chapterFile != null) {
                            val fileContent = chapterFile.openInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
                            htmlContent = fileContent.takeIf { it.isNotBlank() }
                        }
                    }
                }
                // Fall back to fetching from source
                if (htmlContent.isNullOrBlank()) {
                    val sChapter = SNovelChapterImpl().apply {
                        url = nextChapter.url
                        name = nextChapter.name
                    }
                    htmlContent = source.getChapterText(sChapter)
                }
                val nextItems = parseHtmlToParagraphs(htmlContent, nextChapter.id)
                // Skip appending if the chapter has no content (empty/corrupt)
                if (nextItems.isEmpty()) {
                    val currentItems2 = _contentItems.value.toMutableList()
                    _contentItems.value = currentItems2.dropLast(1)
                    _events.emit(NovelReaderEvent.ShowError("Next chapter has no readable content"))
                    return@launchIO
                }
                val wrappedNextItems = wrapWithNavigation(nextItems, nextChapter)

                // Append (drop loading indicator).
                val updatedItems = currentItems.dropLast(1) + wrappedNextItems
                _contentItems.value = updatedItems

                // Do NOT mutate _currentChapter/currentChapterIndex here.
                // The current chapter is derived from scroll position via
                // updateCurrentChapterById() â€” this is what fixes the
                // "chapter 3 100%" progress bug.
                loadedChapterIds.add(nextChapter.id)
            } catch (e: Exception) {
                // Remove loading indicator on error.
                val currentItems = _contentItems.value.toMutableList()
                _contentItems.value = currentItems.dropLast(1)
                _events.emit(NovelReaderEvent.ShowError("Failed to load next chapter: ${e.message}"))
            } finally {
                isLoadingNextChapter = false
            }
        }
    }

    /**
     * Prepend the previous chapter to the content list for INFINITE_SCROLL mode.
     * Called by the scroll listener when the user nears the top.
     */
    fun loadPreviousChapterInBackground() {
        if (isLoadingPreviousChapter) return
        if (currentChapterIndex <= 0) return

        val lowestLoadedIndex = _chapters.value.indexOfFirst { loadedChapterIds.contains(it.id) }
        val actualPrevIndex = if (lowestLoadedIndex > 0) lowestLoadedIndex - 1 else currentChapterIndex - 1
        if (actualPrevIndex < 0) return

        val prevChapter = _chapters.value[actualPrevIndex]
        if (loadedChapterIds.contains(prevChapter.id)) return

        screenModelScope.launchIO {
            isLoadingPreviousChapter = true
            try {
                val currentItems = _contentItems.value.toMutableList()
                val prevItemCount = currentItems.size
                // Add loading indicator at beginning.
                currentItems.add(
                    0,
                    TextItem.Loading(
                        id = System.currentTimeMillis(),
                        chapterId = prevChapter.id,
                        loadingMessage = "Loading previous chapter...",
                    ),
                )
                _contentItems.value = currentItems

                val source = currentSource ?: return@launchIO
                // Use prefetched cache if available (faster than re-fetching)
                var htmlContent: String? = prefetchedChapterCache.remove(prevChapter.id)
                // Try downloaded file next (offline reading)
                if (htmlContent.isNullOrBlank()) {
                    val novel = _novel.value
                    if (novel != null && downloadManager.isChapterDownloaded(
                            chapterName = prevChapter.name,
                            chapterScanlator = prevChapter.scanlator,
                            novelTitle = novel.title,
                            sourceId = source.id,
                        )
                    ) {
                        val chapterFile = downloadProvider.findChapterDir(
                            chapterName = prevChapter.name,
                            chapterScanlator = prevChapter.scanlator,
                            novelTitle = novel.title,
                            source = source,
                        )
                        if (chapterFile != null) {
                            val fileContent = chapterFile.openInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
                            htmlContent = fileContent.takeIf { it.isNotBlank() }
                        }
                    }
                }
                // Fall back to fetching from source
                if (htmlContent.isNullOrBlank()) {
                    val sChapter = SNovelChapterImpl().apply {
                        url = prevChapter.url
                        name = prevChapter.name
                    }
                    htmlContent = source.getChapterText(sChapter)
                }
                val prevItems = parseHtmlToParagraphs(htmlContent, prevChapter.id)
                // Skip prepending if the chapter has no content (empty/corrupt)
                if (prevItems.isEmpty()) {
                    val currentItems2 = _contentItems.value.toMutableList()
                    _contentItems.value = currentItems2.drop(1)
                    _events.emit(NovelReaderEvent.ShowError("Previous chapter has no readable content"))
                    return@launchIO
                }
                val wrappedPrevItems = wrapWithNavigation(prevItems, prevChapter)

                // Prepend (drop loading indicator). Preserve scroll by offsetting.
                val updatedItems = wrappedPrevItems + currentItems.drop(1)
                _contentItems.value = updatedItems
                loadedChapterIds.add(prevChapter.id)

                // Do NOT mutate _currentChapter/currentChapterIndex here.
                // The current chapter is derived from scroll position via
                // updateCurrentChapterById().

                // Notify the screen to adjust scroll offset by the number of
                // prepended items so the user stays at the same spot.
                // Stored as a pending field so the screen can apply it AFTER
                // submitList completes â€” avoiding a race condition that caused
                // random scroll jumps.
                val addedCount = updatedItems.size - prevItemCount
                pendingScrollAdjustment = addedCount
            } catch (e: Exception) {
                val currentItems = _contentItems.value.toMutableList()
                _contentItems.value = currentItems.drop(1)
                _events.emit(NovelReaderEvent.ShowError("Failed to load previous chapter: ${e.message}"))
            } finally {
                isLoadingPreviousChapter = false
            }
        }
    }

    /** Reset loaded-chapter tracking when a fresh chapter is loaded. */
    private fun resetLoadedChapters(chapterId: Long) {
        loadedChapterIds.clear()
        loadedChapterIds.add(chapterId)
    }

    /**
     * Prefetch the next chapter's HTML content into a cache â€” does NOT append
     * to content items. Used when "Prefetch next chapter" is enabled and
     * infinite scroll is OFF. The cached content is used by [loadChapter] for
     * instant navigation when the user taps "next chapter".
     * Also chains: when the user moves to the prefetched chapter, the next
     * one is automatically prefetched.
     */
    fun prefetchNextChapter() {
        val chapters = _chapters.value
        if (chapters.isEmpty()) return
        val source = currentSource ?: return
        val novel = _novel.value

        // Prefetch chapters ahead and behind, excluding the current chapter
        // and any already loaded/cached chapters.
        val indicesToPrefetch = mutableListOf<Int>()
        // Behind
        for (i in 1..PREFETCH_BEHIND) {
            val idx = currentChapterIndex - i
            if (idx >= 0) indicesToPrefetch.add(idx)
        }
        // Ahead
        for (i in 1..PREFETCH_AHEAD) {
            val idx = currentChapterIndex + i
            if (idx < chapters.size) indicesToPrefetch.add(idx)
        }

        for (idx in indicesToPrefetch) {
            val chapter = chapters[idx]
            // Already cached or loaded - skip
            if (prefetchedChapterCache.containsKey(chapter.id)) continue
            if (loadedChapterIds.contains(chapter.id)) continue

            screenModelScope.launchIO {
                try {
                    // Check if already downloaded (no need to prefetch)
                    if (novel != null) {
                        val isDownloaded = downloadManager.isChapterDownloaded(
                            chapterName = chapter.name,
                            chapterScanlator = chapter.scanlator,
                            novelTitle = novel.title,
                            sourceId = source.id,
                        )
                        if (isDownloaded) return@launchIO // Will load from disk instantly
                    }
                    val sChapter = SNovelChapterImpl().apply {
                        url = chapter.url
                        name = chapter.name
                    }
                    val htmlContent = source.getChapterText(sChapter)
                    // Evict oldest entries if cache is full
                    if (prefetchedChapterCache.size >= MAX_PREFETCH_CACHE_SIZE) {
                        prefetchedChapterCache.keys.firstOrNull()?.let {
                            prefetchedChapterCache.remove(it)
                        }
                    }
                    prefetchedChapterCache[chapter.id] = htmlContent
                } catch (e: Exception) {
                    // Silent - prefetch is best-effort
                }
            }
        }
    }

    /**
     * Update the current chapter based on which chapter is visually visible.
     * This is the ONLY method that changes the "current" chapter during
     * infinite scroll â€” called from the scroll listener, not from the
     * background loaders. Also marks the previous chapter as read.
     */
    fun updateCurrentChapterById(chapterId: Long) {
        val chapter = _chapters.value.find { it.id == chapterId } ?: return
        if (_currentChapter.value?.id == chapterId) return

        val previousChapter = _currentChapter.value
        _currentChapter.value = chapter
        currentChapterIndex = _chapters.value.indexOf(chapter)
        _events.tryEmit(NovelReaderEvent.ChapterChanged(chapter.name))

        // In infinite scroll, when the chapter changes because the user scrolled
        // When the current chapter changes (user scrolled into a new chapter's
        // header in infinite scroll), reset progress to 0% for the new chapter.
        // The progress is purely based on the current chapter â€” no catchup needed.
        if (preferences.readingMode().get() == NovelReadingMode.INFINITE_SCROLL) {
            _progressPercent.value = 0
        }

        // Mark the previous chapter as read since the user has scrolled past it.
        previousChapter?.let { prev ->
            if (!prev.read) {
                screenModelScope.launchIO {
                    try {
                        setReadStatus.await(true, prev)
                    } catch (e: Exception) {
                        // Silent â€” not critical
                    }
                }
            }
        }

        // Chain prefetch: when the current chapter changes, prefetch nearby
        // chapters. This ensures continuous prefetching as the user reads.
        prefetchNextChapter()
    }

    private suspend fun markPreviousChapterRead() {
        val prevIndex = currentChapterIndex - 1
        if (prevIndex >= 0) {
            val prevChapter = _chapters.value.getOrNull(prevIndex)
            if (prevChapter != null && !prevChapter.read) {
                setReadStatus.await(true, prevChapter)
            }
        }
    }

    private var chapterLoadTime: Long = 0L

    private fun upsertHistory(chapter: NovelChapter) {
        val now = System.currentTimeMillis()
        val duration = if (chapterLoadTime > 0) now - chapterLoadTime else 0L
        chapterLoadTime = now
        screenModelScope.launchIO {
            upsertNovelHistory.await(
                NovelHistoryUpdate(
                    chapterId = chapter.id,
                    readAt = java.util.Date(now),
                    sessionReadDuration = duration,
                ),
            )
        }
    }

    fun toggleControls() {
        _isControlsVisible.value = !_isControlsVisible.value
    }

    fun setMenuVisible(visible: Boolean) {
        _isControlsVisible.value = visible
    }

    fun showSettings() {
        _isControlsVisible.value = false
        _isSettingsVisible.value = true
        refreshInstalledNeuralVoices()
    }

    fun dismissSettings() {
        _isSettingsVisible.value = false
    }

    fun showChapters() {
        _isControlsVisible.value = false
        _isChaptersSheetVisible.value = true
    }

    fun dismissChapters() {
        _isChaptersSheetVisible.value = false
    }

    // ===== Comments =====

    fun showComments() {
        if (!supportsComments) return
        _isCommentsDialogVisible.value = true
        if (_comments.value.isEmpty()) {
            loadComments()
        }
    }

    fun dismissComments() {
        _isCommentsDialogVisible.value = false
    }

    fun refreshComments() {
        loadComments()
    }

    private fun loadComments() {
        val source = currentSource ?: return
        val chapter = _currentChapter.value ?: return
        _isLoadingComments.value = true
        _commentsError.value = null
        screenModelScope.launchIO {
            try {
                val sChapter = SNovelChapterImpl().apply {
                    url = chapter.url
                    name = chapter.name
                }
                val result = source.getChapterComments(sChapter)
                _comments.value = result
            } catch (e: Throwable) {
                _commentsError.value = e.message ?: "Failed to load comments"
            } finally {
                _isLoadingComments.value = false
            }
        }
    }

    fun openChapterInWebView() {
        val ctx = context ?: return
        val chapter = _currentChapter.value ?: return
        val n = _novel.value ?: return
        val intent = WebViewActivity.newIntent(ctx, chapter.url, n.source, n.title)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    fun applyReaderTheme(theme: Int, themeBackgroundColor: Int? = null, themeTextColor: Int? = null) {
        // Novel reader has its own background color preference; no need to
        // overwrite the manga reader's readerTheme.
        refreshTextConfig(themeBackgroundColor, themeTextColor)
    }

    /**
     * Apply the user's chosen orientation to the host activity.
     * Called when the orientation dropdown changes.
     */
    fun applyOrientation() {
        val ctx = context ?: return
        val orientation = preferences.orientation().get()
        val activity = ctx as? Activity
        if (activity != null) {
            activity.requestedOrientation = when (orientation) {
                NovelOrientation.FREE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                NovelOrientation.PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                NovelOrientation.LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                NovelOrientation.LOCKED_PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                NovelOrientation.LOCKED_LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
            }
        }
    }

    fun updateProgress(percent: Int) {
        _progressPercent.value = percent
    }

    /**
     * Update the character-level reading position from the visible RecyclerView
     * state. Called by the scroll listener.
     *
     * [characterPosition] is the GLOBAL character offset across all loaded
     * chapters. We convert it to a per-chapter offset by subtracting the
     * current chapter's first paragraph startCharIndex, then compute progress
     * relative to the current chapter's total character count.
     */
    fun updateCharacterPosition(
        characterPosition: Int,
        itemIndex: Int = 0,
        pixelOffset: Int = 0,
    ) {
        val chapter = _currentChapter.value ?: return
        val novel = _novel.value ?: return

        // Filter paragraphs belonging to the CURRENT chapter only.
        // The current chapter is determined by which chapter header is visible
        // (via updateCurrentChapterById), so progress is always relative to the
        // chapter the user is actually reading.
        val chapterParagraphs = _contentItems.value
            .filterIsInstance<TextItem.Paragraph>()
            .filter { it.chapterId == chapter.id }

        if (chapterParagraphs.isEmpty()) return

        val chapterStart = chapterParagraphs.first().startCharIndex
        val chapterEnd = chapterParagraphs.last().endCharIndex
        val chapterTotalChars = (chapterEnd - chapterStart + 1).coerceAtLeast(1)

        // Convert global position to per-chapter position.
        val perChapterPos = (characterPosition - chapterStart).coerceIn(0, chapterTotalChars)

        // Convert global adapter itemIndex to chapter-relative itemIndex.
        // In infinite scroll mode, the adapter contains items from multiple
        // chapters. We need the index relative to the current chapter's first
        // item so that scroll restoration works correctly when reopening the
        // chapter (which loads only one chapter's items).
        val chapterFirstItem = _contentItems.value.indexOfFirst {
            (it is TextItem.Paragraph && it.chapterId == chapter.id) ||
                (it is TextItem.ChapterHeader && it.chapterId == chapter.id)
        }
        val chapterRelativeItemIndex = if (chapterFirstItem >= 0) {
            (itemIndex - chapterFirstItem).coerceAtLeast(0)
        } else {
            itemIndex
        }

        positionTracker.updatePosition(
            novelId = novel.id,
            chapterId = chapter.id,
            characterPosition = perChapterPos,
            totalCharacters = chapterTotalChars,
            itemIndex = chapterRelativeItemIndex,
            pixelOffset = pixelOffset,
        )

        if (preferences.showReadingProgress().get()) {
            // Progress is purely based on the current chapter (detected by header
            // visibility). When the user scrolls into a new chapter's header,
            // the current chapter changes and progress starts at 0% for the new
            // chapter. When scrolling up to a previous chapter, progress shows
            // that chapter's position — it does NOT jump to 100%.
            var pct = ((perChapterPos.toFloat() / chapterTotalChars) * 100)
                .toInt().coerceIn(0, 100)

            // Accelerate progress for the LAST chapter: when there's no next
            // chapter, boost the progress so it reaches 100% as the user
            // approaches the bottom. Without this, the progress would stall
            // at ~85% because the last paragraph may not fill the screen.
            // We accelerate from 80% onward: 80%→80%, 90%→95%, 100%→100%.
            if (isLastChapter() && pct >= 80) {
                // Map [80, 100] → [80, 100] with acceleration
                val accelerated = 80 + ((pct - 80) * 5 / 4)
                pct = accelerated.coerceIn(0, 100)
            }

            _progressPercent.value = pct
        }
    }

    /** Debounced save of the current character position to the database. */
    fun saveCurrentPosition() {
        screenModelScope.launchIO {
            positionTracker.savePosition()
        }
    }

    /** Synchronous save â€” for use in onDispose when the scope is being cancelled. */
    suspend fun saveCurrentPositionBlocking() {
        positionTracker.savePosition()
    }

    /**
     * Start a periodic auto-save that persists the reading position every
     * 15 seconds. This ensures position is saved even if the user is reading
     * without scrolling (e.g. a long paragraph on screen).
     */
    private fun startPeriodicSave() {
        periodicSaveJob?.cancel()
        periodicSaveJob = screenModelScope.launchIO {
            while (true) {
                kotlinx.coroutines.delay(15_000L)
                positionTracker.savePosition()
            }
        }
    }

    private fun stopPeriodicSave() {
        periodicSaveJob?.cancel()
        periodicSaveJob = null
    }

    /**
     * Re-wrap the current content items with updated navigation buttons.
     * Called when the reading mode changes (e.g., switching to/from infinite scroll)
     * so that next/prev buttons appear or disappear immediately.
     */
    fun onReadingModeChanged() {
        val currentChapter = _currentChapter.value ?: return
        val currentItems = _contentItems.value

        // Group items by chapter and re-wrap each chapter's paragraphs
        val chapterGroups = currentItems
            .filterIsInstance<TextItem.Paragraph>()
            .groupBy { it.chapterId }

        val newItems = mutableListOf<TextItem>()
        for (chapterId in chapterGroups.keys) {
            val chapter = _chapters.value.find { it.id == chapterId } ?: continue
            val paragraphs = chapterGroups[chapterId] ?: continue
            newItems.addAll(wrapWithNavigation(paragraphs, chapter))
        }

        _contentItems.value = newItems
    }

    /**
     * Force progress to 100% â€” called when the user reaches the absolute bottom
     * of the scrollable content (can't scroll further down).
     */
    fun forceProgressComplete() {
        // Guard: don't force-complete when the current chapter has no
        // paragraph content. An empty/corrupt chapter is non-scrollable,
        // which falsely triggers this method — marking the chapter as read
        // without the user actually reading it.
        val chapter = _currentChapter.value ?: return
        val novel = _novel.value ?: return
        val chapterParagraphs = _contentItems.value
            .filterIsInstance<TextItem.Paragraph>()
            .filter { it.chapterId == chapter.id }
        if (chapterParagraphs.isEmpty()) return

        if (preferences.showReadingProgress().get()) {
            _progressPercent.value = 100
        }
        // Also save position as complete
        if (chapterParagraphs.isNotEmpty()) {
            val chapterTotalChars = (chapterParagraphs.last().endCharIndex - chapterParagraphs.first().startCharIndex + 1)
                .coerceAtLeast(1)
            positionTracker.updatePosition(
                novelId = novel.id,
                chapterId = chapter.id,
                characterPosition = chapterTotalChars,
                totalCharacters = chapterTotalChars,
            )
        }
        // If this is the last chapter, mark it as read since there's no
        // next chapter to scroll into (which would normally mark it read).
        if (isLastChapter() && !chapter.read) {
            screenModelScope.launchIO {
                try {
                    setReadStatus.await(true, chapter)
                } catch (_: Exception) {
                    // Silent — not critical
                }
            }
        }
    }

    fun loadChapterById(id: Long) {
        val chapter = _chapters.value.find { it.id == id } ?: return
        currentChapterIndex = _chapters.value.indexOf(chapter)
        screenModelScope.launchIO {
            // Save the current chapter's position before switching.
            stopPeriodicSave()
            positionTracker.savePosition()
            loadChapter(chapter)
        }
    }

    /**
     * Advance to the next chapter. Returns true if there is a next chapter,
     * false if already at the last chapter. Used by auto-scroll chapter-end
     * behavior.
     */
    fun advanceToNextChapter(): Boolean {
        if (currentChapterIndex >= _chapters.value.size - 1) return false
        navigateToNextChapter()
        return true
    }

    /**
     * Returns true if the current chapter is the last chapter in the list
     * (no next chapter to scroll/navigate to).
     */
    fun isLastChapter(): Boolean {
        return currentChapterIndex >= _chapters.value.size - 1
    }

    /**
     * Save position when the screen model is disposed (user leaves reader).
     * This ensures the scroll position is persisted even if the debounced
     * save hasn't fired recently.
     */
    override fun onDispose() {
        // Stop the periodic save timer.
        stopPeriodicSave()
        // Save synchronously in a blocking way since the scope is about to be cancelled
        kotlinx.coroutines.runBlocking {
            positionTracker.savePosition()
            positionTracker.endReadingSession()
        }
        super.onDispose()
    }
}

sealed interface NovelReaderEvent {
    data class ShowError(val message: String) : NovelReaderEvent
    data class ShowMessage(val message: String) : NovelReaderEvent
    data class ChapterChanged(val title: String) : NovelReaderEvent
    /** Scroll to a position: 0 = top, -1 = bottom. */
    data class ScrollToPosition(val position: Int) : NovelReaderEvent
    /** Scroll to a specific character offset within the current chapter. */
    data class ScrollToCharacter(val characterPosition: Int) : NovelReaderEvent
}

sealed interface TranslationState {
    object Idle : TranslationState
    data class Loading(val originalText: String) : TranslationState
    data class Result(val originalText: String, val translatedText: String, val targetLang: String) : TranslationState
    data class Error(val message: String) : TranslationState
}
