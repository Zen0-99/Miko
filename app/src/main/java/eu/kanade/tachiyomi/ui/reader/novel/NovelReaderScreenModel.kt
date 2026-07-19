package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.text.Spanned
import android.text.Html
import android.speech.tts.TextToSpeech
import org.jsoup.Jsoup
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.novelsource.model.NovelComment
import eu.kanade.tachiyomi.novelsource.model.SNovelChapterImpl
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.asNovelCover
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
    private var currentSource: NovelHttpSource? = null

    /** Periodic auto-save coroutine job — saves position every 15 seconds. */
    private var periodicSaveJob: kotlinx.coroutines.Job? = null

    private val _events = MutableSharedFlow<NovelReaderEvent>()
    val events: SharedFlow<NovelReaderEvent> = _events.asSharedFlow()

    private val _contentItems = MutableStateFlow<List<TextItem>>(emptyList())
    val contentItems = _contentItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
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

    private val _textConfig = MutableStateFlow(buildTextConfig())
    val textConfig = _textConfig.asStateFlow()

    private val _dictionaryQuery = MutableStateFlow<String?>(null)
    val dictionaryQuery = _dictionaryQuery.asStateFlow()

    internal var highlightManager: NovelHighlightManager? = null
        private set

    private val _showHighlightColorPicker = MutableStateFlow<String?>(null)
    val showHighlightColorPicker = _showHighlightColorPicker.asStateFlow()

    private var pendingSelectedText: String? = null

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
            // Already bound — start reading directly
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
        _ttsPlaybackState.value = NovelTtsPlaybackState()
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

    private fun buildTextConfig(themeBackgroundColor: Int? = null, themeTextColor: Int? = null): TextConfig {
        val bgColorMode = preferences.backgroundColorMode().get()
        val (backgroundColor, textColor) = when (bgColorMode) {
            NovelReaderBackgroundColor.WHITE -> android.graphics.Color.WHITE to android.graphics.Color.BLACK
            NovelReaderBackgroundColor.BLACK -> android.graphics.Color.BLACK to android.graphics.Color.WHITE
            NovelReaderBackgroundColor.GRAY -> android.graphics.Color.parseColor("#FF202020") to android.graphics.Color.WHITE
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

        return TextConfig(
            textSize = preferences.textSize().get(),
            textColor = effectiveTextColor,
            backgroundColor = effectiveBackgroundColor,
            lineSpacing = preferences.lineHeight().get(),
            paragraphSpacing = preferences.paragraphSpacing().get(),
            horizontalPadding = effectivePadding,
            textAlignment = preferences.textAlignment().get(),
            bionicReading = preferences.bionicReading().get(),
            forceBold = preferences.forceBoldText().get(),
            forceItalic = preferences.forceItalicText().get(),
            forceParagraphIndent = preferences.forceParagraphIndent().get(),
        )
    }

    fun refreshTextConfig(themeBackgroundColor: Int? = null, themeTextColor: Int? = null) {
        _textConfig.value = buildTextConfig(themeBackgroundColor, themeTextColor)
    }

    private fun isDark(context: Context): Boolean {
        return (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    val textConfigValue: TextConfig
        get() = _textConfig.value

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

                val source = sourceManager.getOrStub(novel.source) as? NovelHttpSource
                currentSource = source
                if (source == null) {
                    mutableState.update { it.copy(loading = false, error = "Source not available") }
                    return@launchIO
                }

                val chapterList = getNovelWithChapters.awaitChapters(novelId)
                _chapters.value = chapterList.sortedBy { it.sourceOrder }

                val targetChapter = if (chapterId != null) {
                    chapterList.find { it.id == chapterId }
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
        _currentChapter.value = chapter
        _isLoading.value = true
        mutableState.update { it.copy(loading = true) }
        upsertHistory(chapter)

        try {
            val source = currentSource
            if (source == null) {
                mutableState.update { it.copy(loading = false, error = "No source available") }
                return
            }

            val novel = _novel.value

            // Try downloaded content first (offline reading / instant load)
            var htmlContent: String? = null
            if (novel != null) {
                val isDownloaded = downloadManager.isChapterDownloaded(
                    chapterName = chapter.name,
                    chapterScanlator = chapter.scanlator,
                    novelTitle = novel.title,
                    sourceId = source.id,
                )
                if (isDownloaded) {
                    val chapterFile = downloadProvider.findChapterDir(
                        chapterName = chapter.name,
                        chapterScanlator = chapter.scanlator,
                        novelTitle = novel.title,
                        source = source,
                    )
                    if (chapterFile != null) {
                        htmlContent = chapterFile.openInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
                    }
                }
            }

            // Fall back to fetching from source
            if (htmlContent == null) {
                val sChapter = SNovelChapterImpl().apply {
                    url = chapter.url
                    name = chapter.name
                }
                htmlContent = source.getChapterText(sChapter)
            }

            val items = parseHtmlToParagraphs(htmlContent, chapter.id)
            val wrappedItems = wrapWithNavigation(items, chapter)

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

            _contentItems.value = wrappedItems
            resetLoadedChapters(chapter.id)
            mutableState.update { it.copy(loading = false, error = null) }
            _events.emit(NovelReaderEvent.ChapterChanged(chapter.name))

            // Restore saved character position for this chapter.
            val saved = positionTracker.loadSavedPosition(chapter)
            if (saved != null && saved.characterPosition > 0) {
                positionTracker.startReadingSession(saved.characterPosition)
                _events.emit(NovelReaderEvent.ScrollToCharacter(saved.characterPosition))
            } else {
                positionTracker.startReadingSession(0)
            }

            // Start periodic auto-save for this reading session.
            startPeriodicSave()
        } catch (e: Exception) {
            mutableState.update { it.copy(loading = false, error = "Failed to load chapter: ${e.message}") }
            _events.emit(NovelReaderEvent.ShowError("Failed to load chapter: ${e.message}"))
        } finally {
            _isLoading.value = false
        }
    }

    private fun parseHtmlToParagraphs(html: String, chapterId: Long): List<TextItem> {
        @Suppress("DEPRECATION")
        fun renderHtml(source: String): Spanned {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT)
            } else {
                Html.fromHtml(source)
            }
        }

        val items = mutableListOf<TextItem>()
        try {
            // Clean HTML with Jsoup — removes style/script/noscript that cause
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

                val spanned = renderHtml(paragraphHtml)
                if (spanned.isEmpty()) continue

                // Inclusive endChar: text.length=5 → chars 0..4, endChar=4.
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

        return items
    }

    private fun wrapWithNavigation(items: List<TextItem>, chapter: NovelChapter): List<TextItem> {
        val result = mutableListOf<TextItem>()

        // Look up chapter by id — during infinite scroll, currentChapterIndex
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

        // Chapter header — skip when joining chapters (infinite scroll only, not first chapter)
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
                // In DEFAULT/OVERSCROLL modes, clear content so the scroll bar
                // reflects only the current chapter.
                if (preferences.readingMode().get() != NovelReadingMode.INFINITE_SCROLL) {
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
                if (preferences.readingMode().get() != NovelReadingMode.INFINITE_SCROLL) {
                    _contentItems.value = emptyList()
                }
                loadChapter(chapter)
                markPreviousChapterRead()
                if (preferences.readingMode().get() != NovelReadingMode.INFINITE_SCROLL) {
                    _events.emit(NovelReaderEvent.ScrollToPosition(0))
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // Infinite-scroll / overscroll support
    // -----------------------------------------------------------------

    /** Chapter IDs currently loaded into the content list (for dedup). */
    private val loadedChapterIds = mutableSetOf<Long>()
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
                val sChapter = SNovelChapterImpl().apply {
                    url = nextChapter.url
                    name = nextChapter.name
                }
                val htmlContent = source.getChapterText(sChapter)
                val nextItems = parseHtmlToParagraphs(htmlContent, nextChapter.id)
                val wrappedNextItems = wrapWithNavigation(nextItems, nextChapter)

                // Append (drop loading indicator).
                val updatedItems = currentItems.dropLast(1) + wrappedNextItems
                _contentItems.value = updatedItems

                // Do NOT mutate _currentChapter/currentChapterIndex here.
                // The current chapter is derived from scroll position via
                // updateCurrentChapterById() — this is what fixes the
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
                val sChapter = SNovelChapterImpl().apply {
                    url = prevChapter.url
                    name = prevChapter.name
                }
                val htmlContent = source.getChapterText(sChapter)
                val prevItems = parseHtmlToParagraphs(htmlContent, prevChapter.id)
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
                val addedCount = updatedItems.size - prevItemCount
                _events.emit(NovelReaderEvent.AdjustScrollOffset(addedCount))
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
     * Update the current chapter based on which chapter is visually visible.
     * This is the ONLY method that changes the "current" chapter during
     * infinite scroll — called from the scroll listener, not from the
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
        // The progress is purely based on the current chapter — no catchup needed.
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
                        // Silent — not critical
                    }
                }
            }
        }
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
    fun updateCharacterPosition(characterPosition: Int) {
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

        positionTracker.updatePosition(
            novelId = novel.id,
            chapterId = chapter.id,
            characterPosition = perChapterPos,
            totalCharacters = chapterTotalChars,
        )

        if (preferences.showReadingProgress().get()) {
            // Progress is purely based on the current chapter (detected by header
            // visibility). When the user scrolls into a new chapter's header,
            // the current chapter changes and progress starts at 0% for the new
            // chapter. When scrolling up to a previous chapter, progress shows
            // that chapter's position — it does NOT jump to 100%.
            val pct = ((perChapterPos.toFloat() / chapterTotalChars) * 100)
                .toInt().coerceIn(0, 100)
            _progressPercent.value = pct
        }
    }

    /** Debounced save of the current character position to the database. */
    fun saveCurrentPosition() {
        screenModelScope.launchIO {
            positionTracker.savePosition()
        }
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
     * Force progress to 100% — called when the user reaches the absolute bottom
     * of the scrollable content (can't scroll further down).
     */
    fun forceProgressComplete() {
        if (preferences.showReadingProgress().get()) {
            _progressPercent.value = 100
        }
        // Also save position as complete
        val chapter = _currentChapter.value ?: return
        val novel = _novel.value ?: return
        val chapterParagraphs = _contentItems.value
            .filterIsInstance<TextItem.Paragraph>()
            .filter { it.chapterId == chapter.id }
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
    /** Adjust scroll offset by [delta] items (used after prepending in infinite scroll). */
    data class AdjustScrollOffset(val delta: Int) : NovelReaderEvent
    /** Scroll to a specific character offset within the current chapter. */
    data class ScrollToCharacter(val characterPosition: Int) : NovelReaderEvent
}
