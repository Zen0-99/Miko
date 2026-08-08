package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TextAdapter(
    private val getConfig: () -> TextConfig,
    private val activity: Activity?,
    private val onNavigationClick: ((TextItem.LoadDirection) -> Unit)? = null,
    private val onTextSelected: ((String) -> Unit)? = null,
    private val onHighlightWithColor: ((selectedText: String, start: Int, end: Int, colorHex: String) -> Unit)? = null,
    private val onCopy: ((String) -> Unit)? = null,
    private val onShare: ((String) -> Unit)? = null,
    private val onReadAloud: ((String) -> Unit)? = null,
    private val onTranslate: ((String) -> Unit)? = null,
    /** Called when text selection becomes active (true) or inactive (false). */
    private val onSelectionActiveChange: ((Boolean) -> Unit)? = null,
    /**
     * Highlight colors are computed dynamically from the current background
     * color so they're theme-aware: darker colors on dark backgrounds (where
     * text is white) and lighter colors on light backgrounds (where text is
     * black). This ensures highlighted text remains legible.
     */
    private val highlightColors: List<Int> = emptyList(),
    /** Returns the highlight manager for the current novel, or null. */
    private val getHighlightManager: (() -> NovelHighlightManager?)? = null,
    /** Returns novel title for highlight lookup. */
    private val getNovelTitle: (() -> String)? = null,
    /** Returns novel ID for highlight lookup. */
    private val getNovelId: (() -> Long?)? = null,
    /** Returns the current chapter number for highlight lookup. */
    private val getChapterNumber: (() -> Double)? = null,
    /** Called when a highlight is deleted via tap-to-delete. */
    private val onHighlightDeleted: (() -> Unit)? = null,
    /** Returns the accent color for navigation buttons (cover accent or null). */
    private val getAccentColor: (() -> Int?)? = null,
) : ListAdapter<TextItem, TextAdapter.TextViewHolder>(TextItemDiffCallback()) {

    /** Compute theme-aware highlight colors from the current config. */
    private fun getHighlightColors(): List<Int> {
        if (highlightColors.isNotEmpty()) return highlightColors
        val config = getConfig()
        val accent = android.graphics.Color.parseColor("#3E7BFA")
        return HighlightColorUtils.computeColors(accent, config.backgroundColor)
    }

    private var currentChapterId: Long = -1L

    /** Tracks the currently-active selection callback so the screen can dismiss it on scroll. */
    @Volatile
    var activeSelectionCallback: NovelSelectionActionModeCallback? = null
        private set

    /** Tracks the currently-active highlight tap popup (for dismissal on scroll). */
    @Volatile
    private var activeHighlightPopup: PopupWindow? = null

    // ===== Multi-paragraph range selection =====
    /** When non-null, the user is selecting a range of paragraphs. This is the
     * adapter position of the first paragraph in the range. */
    @Volatile
    var rangeSelectStart: Int? = null
        private set

    /** Callback invoked when range selection completes with the combined text. */
    var onRangeSelectComplete: ((String) -> Unit)? = null

    /** Callback to show a toast/message to the user. */
    var onShowMessage: ((String) -> Unit)? = null

    /** Called when range selection mode is entered or exited. */
    var onRangeSelectModeChange: ((Boolean) -> Unit)? = null

    fun setCurrentChapterId(id: Long) {
        currentChapterId = id
    }

    fun getCurrentChapterId(): Long = currentChapterId

    /** Start multi-paragraph range selection from the given adapter position. */
    fun startRangeSelection(adapterPosition: Int) {
        rangeSelectStart = adapterPosition
        onRangeSelectModeChange?.invoke(true)
        onShowMessage?.invoke("Tap the last paragraph to select")
        notifyDataSetChanged()
    }

    /** Complete range selection: the user tapped [endAdapterPosition]. Collects
     * all paragraph text between start and end (inclusive) and calls
     * [onRangeSelectComplete]. */
    fun completeRangeSelection(endAdapterPosition: Int) {
        val start = rangeSelectStart ?: return
        rangeSelectStart = null
        onRangeSelectModeChange?.invoke(false)

        val from = minOf(start, endAdapterPosition)
        val to = maxOf(start, endAdapterPosition)

        val textParts = mutableListOf<String>()
        for (i in from..to) {
            val item = currentList.getOrNull(i) as? TextItem.Paragraph ?: continue
            textParts.add(item.text.toString())
        }

        val combined = textParts.joinToString("\n\n")
        if (combined.isNotBlank()) {
            onRangeSelectComplete?.invoke(combined)
        }
        notifyDataSetChanged()
    }

    /** Cancel range selection mode. */
    fun cancelRangeSelection() {
        if (rangeSelectStart != null) {
            rangeSelectStart = null
            onRangeSelectModeChange?.invoke(false)
            notifyDataSetChanged()
        }
    }

    /** Check if an adapter position is within the current range selection. */
    fun isInRangeSelection(adapterPosition: Int): Boolean {
        val start = rangeSelectStart ?: return false
        // While selecting, only the start is highlighted. The range is
        // determined when the user taps the end paragraph.
        return adapterPosition == start
    }

    /** Dismiss any active selection popup (call from RecyclerView scroll listener). */
    fun dismissActiveSelectionPopup() {
        activeSelectionCallback?.onScrollDismiss()
        activeSelectionCallback = null
        activeHighlightPopup?.dismiss()
        activeHighlightPopup = null
    }

    abstract class TextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(item: TextItem)
    }

    class ParagraphViewHolder(
        view: View,
        private val getConfig: () -> TextConfig,
        private val activity: Activity?,
        private val onTextSelected: ((String) -> Unit)?,
        private val onHighlightWithColor: ((selectedText: String, start: Int, end: Int, colorHex: String) -> Unit)?,
        private val onCopy: ((String) -> Unit)?,
        private val onShare: ((String) -> Unit)?,
        private val onReadAloud: ((String) -> Unit)?,
        private val onTranslate: ((String) -> Unit)?,
        private val highlightColors: List<Int>,
        private val onCallbackActivated: ((NovelSelectionActionModeCallback) -> Unit)?,
        private val getHighlightManager: (() -> NovelHighlightManager?)?,
        private val getNovelTitle: (() -> String)?,
        private val getNovelId: (() -> Long?)?,
        private val getChapterNumber: (() -> Double)?,
        private val onHighlightDeleted: (() -> Unit)?,
        private val onPopupShown: ((PopupWindow) -> Unit)?,
        private val onSelectionActiveChange: ((Boolean) -> Unit)? = null,
        private val isRangeSelected: ((Int) -> Boolean)? = null,
        private val getAdapter: (() -> TextAdapter)? = null,
    ) : TextViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.paragraph_text)

        // Track the latest touch position for use in onLongClick.
        @Volatile private var latestTouchX: Float = 0f
        @Volatile private var latestTouchY: Float = 0f

        // Re-enable text selection when the view is (re-)attached to the window.
        // setTextIsSelectable(true) in bind() calls Editor.prepareCursorControllers(),
        // but at bind time the view may not be attached to the window yet. When the
        // view is later attached (after recycling), the selection controllers may
        // not be re-enabled. Toggling setTextIsSelectable off/on forces
        // prepareCursorControllers() to be called again with the view attached.
        init {
            textView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    if (textView.isTextSelectable) {
                        textView.setTextIsSelectable(false)
                        textView.setTextIsSelectable(true)
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {}
            })
        }

        override fun bind(item: TextItem) {
            if (item is TextItem.Paragraph) {
                val config = getConfig()

                // Start with base text (or bionic reading).
                val baseText: CharSequence = if (config.bionicReading) {
                    applyBionicReading(item.text)
                } else {
                    item.text
                }

                // Apply saved highlight spans from storage.
                val displayText = applyHighlightSpans(baseText, item)

                // Ensure text is always Spannable — the Editor requires it for
                // selection. If displayText is a plain String (no highlights,
                // no bionic reading), wrap it in SpannableStringBuilder.
                val spannableText: CharSequence = if (displayText is android.text.Spannable) {
                    displayText
                } else {
                    android.text.SpannableStringBuilder(displayText)
                }
                textView.text = spannableText
                textView.textSize = config.textSize
                textView.setTextColor(config.textColor)
                textView.setLineSpacing(config.lineSpacing, 1f)

                // Range selection highlight: if this paragraph is the start of
                // a range selection, tint its background.
                val adapter = getAdapter?.invoke()
                if (adapter != null && isRangeSelected?.invoke(bindingAdapterPosition) == true) {
                    textView.setBackgroundColor(0x30FF9800.toInt()) // semi-transparent orange
                } else {
                    textView.background = null
                }
                val density = textView.context.resources.displayMetrics.density
                val hPadding = (config.horizontalPadding * density).toInt()
                val vPadding = ((config.verticalPadding / 2) * density).toInt()
                textView.setPadding(
                    hPadding,
                    vPadding,
                    hPadding,
                    vPadding,
                )

                val layoutParams = textView.layoutParams as? ViewGroup.MarginLayoutParams
                if (layoutParams != null) {
                    val spacing = (config.paragraphSpacing * density).toInt()
                    layoutParams.topMargin = spacing / 2
                    layoutParams.bottomMargin = spacing / 2
                    textView.layoutParams = layoutParams
                }

                // Set text selectable — simple, no toggle.
                // The OnAttachStateChangeListener in the ViewHolder constructor
                // handles re-enabling selection controllers when the view is
                // re-attached after recycling.
                textView.setTextIsSelectable(config.isTextSelectable)

                if (config.isTextSelectable && activity != null) {
                    val colors = if (highlightColors.isNotEmpty()) highlightColors
                    else HighlightColorUtils.computeColors(
                        android.graphics.Color.parseColor("#3E7BFA"),
                        config.backgroundColor,
                    )
                    val callback = NovelSelectionActionModeCallback(
                        activity = activity,
                        textView = textView,
                        highlightColors = colors,
                        onCopy = onCopy,
                        onDefine = onTextSelected,
                        onHighlight = onHighlightWithColor,
                        onShare = onShare,
                        onReadAloud = onReadAloud,
                        onTranslate = onTranslate,
                        onSelectRange = {
                            // Enter multi-paragraph range selection mode.
                            // The current adapter position is the start of the range.
                            getAdapter?.invoke()?.startRangeSelection(bindingAdapterPosition)
                        },
                        onSelectionActiveChange = onSelectionActiveChange,
                    )
                    textView.customSelectionActionModeCallback = callback
                    onCallbackActivated?.invoke(callback)

                    // Simple touch listener — only handles highlight taps and
                    // range selection taps. The TextView's built-in long-press
                    // handles selection natively (shows handles + popup).
                    var downX = 0f
                    var downY = 0f
                    var downTime = 0L
                    textView.setOnTouchListener { _, event ->

                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                                downTime = System.currentTimeMillis()
                                latestTouchX = event.x
                                latestTouchY = event.y
                            }
                            MotionEvent.ACTION_UP -> {
                                val duration = System.currentTimeMillis() - downTime
                                val distance = kotlin.math.hypot(event.x - downX, event.y - downY)
                                val touchSlop = ViewConfiguration.get(textView.context).scaledTouchSlop

                                // Multi-paragraph range selection: if we're in
                                // range-select mode and the user taps a paragraph,
                                // complete the range selection.
                                val adapter = getAdapter?.invoke()
                                if (adapter != null && adapter.rangeSelectStart != null && duration < 400 && distance < touchSlop * 2) {
                                    val start = adapter.rangeSelectStart!!
                                    if (bindingAdapterPosition != start) {
                                        adapter.completeRangeSelection(bindingAdapterPosition)
                                    } else {
                                        adapter.cancelRangeSelection()
                                        adapter.onShowMessage?.invoke("Selection cancelled")
                                    }
                                    return@setOnTouchListener true
                                }

                                // Highlight tap: short tap on an existing highlight
                                if (duration < 200 && distance < touchSlop && !textView.hasSelection()) {
                                    val offset = textView.getOffsetForPosition(event.x, event.y)
                                    if (offset >= 0) {
                                        val spannable = textView.text as? Spannable
                                        val spans = spannable?.getSpans(offset, offset, NovelHighlightSpan::class.java)
                                        if (!spans.isNullOrEmpty()) {
                                            val span = spans.first()
                                            val spanStart = spannable.getSpanStart(span)
                                            val spanEnd = spannable.getSpanEnd(span)
                                            val highlightText = spannable.substring(spanStart, spanEnd)
                                            showHighlightActions(textView, highlightText, spanStart, spanEnd, item, event.rawX, event.rawY)
                                            return@setOnTouchListener true
                                        }
                                    }
                                }
                            }
                        }
                        false
                    }
                } else {
                    textView.setOnTouchListener(null)
                }

                // Apply text alignment only if not preserving source alignment
                if (!config.preserveSourceTextAlign) {
                    when (config.textAlignment) {
                        TextAlignment.LEFT -> textView.gravity = Gravity.START
                        TextAlignment.CENTER -> textView.gravity = Gravity.CENTER
                        TextAlignment.JUSTIFY -> {
                            textView.gravity = Gravity.START
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                textView.justificationMode = 1
                            }
                        }
                        TextAlignment.RIGHT -> textView.gravity = Gravity.END
                    }
                }

                config.textFont?.let { font ->
                    val style = when {
                        config.forceBold && config.forceItalic -> Typeface.BOLD_ITALIC
                        config.forceBold -> Typeface.BOLD
                        config.forceItalic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                    textView.typeface = Typeface.create(font, style)
                } ?: run {
                    val style = when {
                        config.forceBold && config.forceItalic -> Typeface.BOLD_ITALIC
                        config.forceBold -> Typeface.BOLD
                        config.forceItalic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                    if (style != Typeface.NORMAL) {
                        textView.typeface = Typeface.create(textView.typeface, style)
                    }
                }

                // Force paragraph indent: add leading margin to first line
                if (config.forceParagraphIndent && item is TextItem.Paragraph) {
                    val indentPx = (config.textSize * 2).toInt()
                    textView.setPadding(indentPx, textView.paddingTop, textView.paddingRight, textView.paddingBottom)
                }

                // Apply text shadow if enabled
                if (config.textShadowEnabled) {
                    val shadowColor = try {
                        android.graphics.Color.parseColor(config.textShadowColor)
                    } catch (_: Exception) {
                        0x80000000.toInt()
                    }
                    textView.setShadowLayer(config.textShadowBlur, config.textShadowX, config.textShadowY, shadowColor)
                } else {
                    textView.setShadowLayer(0f, 0f, 0f, 0)
                }

                // Highlight tap detection is now handled by the combined touch listener above.
            }
        }

        /**
         * Apply saved highlight spans from the manager onto the paragraph text.
         * Searches for each highlight's text within the paragraph and applies
         * a [NovelHighlightSpan] at every matching position.
         */
        private fun applyHighlightSpans(text: CharSequence, item: TextItem.Paragraph): CharSequence {
            val manager = getHighlightManager?.invoke() ?: return text
            val novelTitle = getNovelTitle?.invoke() ?: return text
            val chapterNumber = getChapterNumber?.invoke() ?: return text
            val novelId = getNovelId?.invoke()

            val highlights = manager.getChapterHighlights(
                NovelHighlightManager.NovelKey(title = novelTitle, novelId = novelId),
                chapterNumber,
            )
            if (highlights.isEmpty()) return text

            val spannable = if (text is Spannable) text else SpannableString(text)
            val plainText = spannable.toString()

            for (hl in highlights) {
                val color = try {
                    HighlightColorUtils.fromHex(hl.color ?: NovelHighlightManager.COLOR_YELLOW)
                } catch (e: Exception) {
                    HighlightColorUtils.fromHex(NovelHighlightManager.COLOR_YELLOW)
                }
                // Find all occurrences of the highlight text in this paragraph.
                var start = plainText.indexOf(hl.text)
                while (start >= 0) {
                    val end = start + hl.text.length
                    spannable.setSpan(
                        NovelHighlightSpan(color),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    start = plainText.indexOf(hl.text, end)
                }
            }
            return spannable
        }

        /**
         * Show a floating popup with Copy, Search, Delete actions for a
         * tapped highlight.
         */
        private fun showHighlightActions(
            textView: TextView,
            highlightText: String,
            spanStart: Int,
            spanEnd: Int,
            item: TextItem.Paragraph,
            x: Float,
            y: Float,
        ) {
            val ctx = textView.context
            val density = ctx.resources.displayMetrics.density

            val popup = PopupWindow(ctx).apply {
                setBackgroundDrawable(null)
                isOutsideTouchable = true
                isFocusable = false
                height = WindowManager.LayoutParams.WRAP_CONTENT
                width = WindowManager.LayoutParams.WRAP_CONTENT
            }

            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply {
                    cornerRadius = 16 * density
                    setColor(Color.parseColor("#E6333333"))
                    setStroke(1 * density.toInt(), Color.parseColor("#40FFFFFF"))
                }
                setPadding(
                    (8 * density).toInt(),
                    (6 * density).toInt(),
                    (8 * density).toInt(),
                    (6 * density).toInt(),
                )
            }

            val iconSize = (24 * density).toInt()
            val iconPadding = (12 * density).toInt()

            fun addIcon(drawableRes: Int, onClick: () -> Unit) {
                val iv = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        marginEnd = iconPadding
                    }
                    setImageResource(drawableRes)
                    setColorFilter(Color.WHITE)
                    setOnClickListener {
                        popup.dismiss()
                        onClick()
                    }
                }
                container.addView(iv)
            }

            // Copy
            addIcon(R.drawable.ic_content_copy_24dp) {
                onCopy?.invoke(highlightText)
            }

            // Search (dictionary)
            addIcon(R.drawable.ic_search_24dp) {
                onTextSelected?.invoke(highlightText)
            }

            // Delete
            addIcon(R.drawable.ic_delete_24dp) {
                deleteHighlight(textView, highlightText, spanStart, spanEnd, item)
            }

            popup.contentView = container
            popup.showAtLocation(textView, Gravity.NO_GRAVITY, x.toInt(), y.toInt())
            onPopupShown?.invoke(popup)
        }

        /**
         * Delete a highlight: remove from storage and remove the span.
         */
        private fun deleteHighlight(
            textView: TextView,
            highlightText: String,
            spanStart: Int,
            spanEnd: Int,
            item: TextItem.Paragraph,
        ) {
            val manager = getHighlightManager?.invoke() ?: return
            val novelTitle = getNovelTitle?.invoke() ?: return
            val chapterNumber = getChapterNumber?.invoke() ?: return
            val novelId = getNovelId?.invoke()

            val key = NovelHighlightManager.NovelKey(title = novelTitle, novelId = novelId)
            val highlights = manager.getChapterHighlights(key, chapterNumber)

            // Remove the span visually IMMEDIATELY (before the async delete)
            // so the user sees instant feedback.
            val spannable = textView.text as? Spannable
            spannable?.getSpans(spanStart, spanEnd, NovelHighlightSpan::class.java)?.forEach {
                spannable.removeSpan(it)
            }

            // Find the matching highlight by text (first match) and delete async.
            val match = highlights.find { it.text == highlightText }
            if (match != null) {
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    manager.deleteHighlight(key, chapterNumber, match.text, match.timestamp)
                }
            }

            onHighlightDeleted?.invoke()
        }

        companion object {
            const val MENU_ID_COPY = 100
            const val MENU_ID_DICTIONARY = 101
            const val MENU_ID_HIGHLIGHT = 102
            const val MENU_ID_SHARE = 103
            const val MENU_ID_TTS = 104

            /**
             * Find the start of the word containing [offset] in [text].
             * Word boundaries are non-alphanumeric characters.
             */
            private fun findWordStart(text: CharSequence, offset: Int): Int {
                var i = offset
                while (i > 0 && i < text.length && isWordChar(text[i - 1])) i--
                return i
            }

            /**
             * Find the end of the word containing [offset] in [text].
             * Returns the index after the last word character.
             */
            private fun findWordEnd(text: CharSequence, offset: Int): Int {
                var i = offset
                while (i < text.length && isWordChar(text[i])) i++
                return i
            }

            private fun isWordChar(c: Char): Boolean {
                return c.isLetterOrDigit() || c == '\'' || c == '\u2019'
            }

            private fun applyBionicReading(text: CharSequence): CharSequence {
                if (text.isEmpty()) return text
                val spannable = if (text is android.text.Spannable) {
                    android.text.SpannableStringBuilder(text)
                } else {
                    android.text.SpannableString(text)
                }
                val plain = spannable.toString()
                val wordPattern = Regex("\\S+")
                wordPattern.findAll(plain).forEach { match ->
                    val word = match.value
                    if (word.length > 1) {
                        val boldEnd = match.range.first + (word.length + 1) / 2
                        spannable.setSpan(
                            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                            match.range.first,
                            boldEnd,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
                return spannable
            }
        }
    }

    class ChapterHeaderViewHolder(
        view: View,
        private val getConfig: () -> TextConfig,
    ) : TextViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.chapter_title)

        override fun bind(item: TextItem) {
            if (item is TextItem.ChapterHeader) {
                val config = getConfig()
                titleView.text = item.chapterTitle
                titleView.setTextColor(config.textColor)
            }
        }
    }

    class LoadingViewHolder(view: View) : TextViewHolder(view) {
        override fun bind(item: TextItem) {}
    }

    class ChapterNavigationViewHolder(
        view: View,
        private val onNavigationClick: ((TextItem.LoadDirection) -> Unit)?,
        private val getAccentColor: (() -> Int?)? = null,
    ) : TextViewHolder(view) {
        private val button: com.google.android.material.button.MaterialButton =
            view.findViewById(R.id.navigation_button)

        override fun bind(item: TextItem) {
            if (item is TextItem.ChapterNavigation) {
                val buttonText = when {
                    !item.isEnabled && item.direction == TextItem.LoadDirection.NEXT -> "No more chapters"
                    item.direction == TextItem.LoadDirection.PREVIOUS -> "Previous Chapter"
                    item.direction == TextItem.LoadDirection.NEXT -> "Next Chapter"
                    else -> ""
                }
                button.text = buttonText
                button.isEnabled = item.isEnabled
                button.alpha = if (item.isEnabled) 1.0f else 0.5f

                // Apply accent color if available (cover accent color toggle)
                val accentColor = getAccentColor?.invoke()
                if (accentColor != null && item.isEnabled) {
                    button.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
                    button.setTextColor(android.graphics.Color.WHITE)
                }

                if (item.isEnabled) {
                    button.setOnClickListener { onNavigationClick?.invoke(item.direction) }
                } else {
                    button.setOnClickListener(null)
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TextItem.Paragraph -> VIEW_TYPE_PARAGRAPH
            is TextItem.ChapterHeader -> VIEW_TYPE_CHAPTER_HEADER
            is TextItem.Loading -> VIEW_TYPE_LOADING
            is TextItem.Error -> VIEW_TYPE_ERROR
            is TextItem.ChapterNavigation -> VIEW_TYPE_CHAPTER_NAVIGATION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_PARAGRAPH -> ParagraphViewHolder(
                inflater.inflate(R.layout.item_novel_paragraph, parent, false),
                getConfig,
                activity,
                onTextSelected,
                onHighlightWithColor,
                onCopy,
                onShare,
                onReadAloud,
                onTranslate,
                getHighlightColors(),
                onCallbackActivated = { cb -> activeSelectionCallback = cb },
                getHighlightManager = getHighlightManager,
                getNovelTitle = getNovelTitle,
                getNovelId = getNovelId,
                getChapterNumber = getChapterNumber,
                onHighlightDeleted = onHighlightDeleted,
                onPopupShown = { popup -> activeHighlightPopup = popup },
                onSelectionActiveChange = onSelectionActiveChange,
                isRangeSelected = { pos -> isInRangeSelection(pos) },
                getAdapter = { this@TextAdapter },
            )
            VIEW_TYPE_CHAPTER_HEADER -> ChapterHeaderViewHolder(
                inflater.inflate(R.layout.item_novel_chapter_header, parent, false),
                getConfig,
            )
            VIEW_TYPE_LOADING -> LoadingViewHolder(
                inflater.inflate(R.layout.item_novel_loading, parent, false),
            )
            VIEW_TYPE_CHAPTER_NAVIGATION -> ChapterNavigationViewHolder(
                inflater.inflate(R.layout.item_novel_chapter_navigation, parent, false),
                onNavigationClick,
                getAccentColor,
            )
            else -> LoadingViewHolder(
                inflater.inflate(R.layout.item_novel_loading, parent, false),
            )
        }
    }

    override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: TextViewHolder, position: Int, payloads: List<Any>) {
        // When called with a "config" payload (from textConfig change),
        // force a full re-bind to pick up the new styling.
        holder.bind(getItem(position))
    }

    companion object {
        const val VIEW_TYPE_PARAGRAPH = 0
        const val VIEW_TYPE_CHAPTER_HEADER = 1
        const val VIEW_TYPE_LOADING = 2
        const val VIEW_TYPE_ERROR = 3
        const val VIEW_TYPE_CHAPTER_NAVIGATION = 4
    }
}

class TextItemDiffCallback : DiffUtil.ItemCallback<TextItem>() {
    override fun areItemsTheSame(oldItem: TextItem, newItem: TextItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: TextItem, newItem: TextItem): Boolean {
        return oldItem == newItem
    }
}
