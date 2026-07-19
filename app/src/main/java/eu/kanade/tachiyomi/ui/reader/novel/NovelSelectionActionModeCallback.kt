package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.text.Spannable
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import eu.kanade.tachiyomi.R
import java.lang.ref.WeakReference

/**
 * Completely custom floating popup for novel text selection.
 * Replaces the stock ActionMode menu with a styled PopupWindow that morphs
 * between three states:
 *   1. MAIN   – Copy, Define, Highlight, Share, More
 *   2. COLORS – 5 color circles + back arrow
 *   3. TTS    – "Read Aloud" button + back arrow
 *
 * The popup dismisses on:
 *   - outside tap
 *   - RecyclerView scroll (via [onScrollDismiss])
 *   - selection cleared
 *   - any action that calls [finishSelection]
 */
class NovelSelectionActionModeCallback(
    private val activity: Activity,
    textView: TextView,
    private val highlightColors: List<Int>,
    private val onCopy: ((String) -> Unit)? = null,
    private val onDefine: ((String) -> Unit)? = null,
    private val onHighlight: ((selectedText: String, start: Int, end: Int, colorHex: String) -> Unit)? = null,
    private val onShare: ((String) -> Unit)? = null,
    private val onReadAloud: ((String) -> Unit)? = null,
) : ActionMode.Callback {

    private val textViewRef = WeakReference(textView)

    private var popupWindow: PopupWindow? = null
    private var popupContainer: LinearLayout? = null
    private var currentMode: PopupMode = PopupMode.MAIN
    private var tts: android.speech.tts.TextToSpeech? = null

    private enum class PopupMode { MAIN, COLORS, TTS }

    private var pendingSelectedText: String? = null
    private var pendingStart = -1
    private var pendingEnd = -1

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        // Hide the stock floating toolbar completely; we render our own popup
        menu?.clear()
        mode?.title = null
        mode?.subtitle = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mode?.hide(java.lang.Long.MAX_VALUE)
        }

        val tv = textViewRef.get() ?: return true
        val txt = getSelectedText(tv)
        if (!txt.isNullOrBlank()) {
            showPopup(tv, txt)
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mode?.hide(java.lang.Long.MAX_VALUE)
        }
        return true
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode?) {
        dismissPopup()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    // ---------------------------------------------------------------------=
    // Public dismiss hook (called by RecyclerView scroll listener)
    // ---------------------------------------------------------------------=

    fun onScrollDismiss() {
        if (popupWindow != null) {
            dismissPopup()
            val tv = textViewRef.get() ?: return
            tv.clearFocus()
            tv.post {
                val act = tv.context as? Activity
                act?.window?.decorView?.clearFocus()
            }
        }
    }

    // ---------------------------------------------------------------------=
    // Popup construction
    // ---------------------------------------------------------------------=

    private fun showPopup(textView: TextView, selectedText: String) {
        dismissPopup()
        currentMode = PopupMode.MAIN
        pendingSelectedText = selectedText
        pendingStart = textView.selectionStart
        pendingEnd = textView.selectionEnd

        val ctx = textView.context
        val density = ctx.resources.displayMetrics.density

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((18 * density).toInt(), (12 * density).toInt(), (18 * density).toInt(), (12 * density).toInt())
            background = createPopupBackground(density)
        }
        popupContainer = container

        buildMainState(container, textView, selectedText, density)

        val popup = PopupWindow(container, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = true
            isFocusable = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { popupWindow = null }
        }
        popupWindow = popup

        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        val pos = computePopupPosition(textView, container.measuredWidth, container.measuredHeight, density)
        popup.showAtLocation(textView.rootView, Gravity.NO_GRAVITY, pos.first, pos.second)
    }

    private fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
        popupContainer = null
    }

    private fun createPopupBackground(density: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 24 * density
        setColor(Color.parseColor("#E6222222"))
    }

    private fun computePopupPosition(
        textView: TextView,
        popupW: Int,
        popupH: Int,
        density: Float,
    ): Pair<Int, Int> {
        val tvLoc = IntArray(2)
        textView.getLocationOnScreen(tvLoc)

        val rootLoc = IntArray(2)
        textView.rootView.getLocationOnScreen(rootLoc)
        val relTvX = tvLoc[0] - rootLoc[0]
        val relTvY = tvLoc[1] - rootLoc[1]

        val selEnd = textView.selectionEnd.coerceAtLeast(0)
        val layout = textView.layout
        val line = layout?.getLineForOffset(selEnd) ?: 0

        val lineBounds = Rect()
        textView.getLineBounds(line, lineBounds)

        val selX = if (layout != null) {
            val lineLeft = layout.getLineLeft(line).toInt()
            val lineRight = layout.getLineRight(line).toInt()
            relTvX + (lineLeft + lineRight) / 2
        } else {
            relTvX + textView.width / 2
        }
        val popupX = (selX - popupW / 2).coerceIn(0, textView.rootView.width - popupW)

        val gap = (8 * density).toInt()
        val aboveY = relTvY + lineBounds.top - popupH - gap
        val belowY = relTvY + lineBounds.bottom + gap
        val popupY = if (aboveY >= relTvY) aboveY else belowY

        return Pair(popupX, popupY)
    }

    // ---------------------------------------------------------------------=
    // State builders
    // ---------------------------------------------------------------------=

    private fun buildMainState(
        container: LinearLayout,
        textView: TextView,
        selectedText: String,
        density: Float,
    ) {
        container.removeAllViews()
        currentMode = PopupMode.MAIN

        val iconSize = (22 * density).toInt()
        val iconPadding = (14 * density).toInt()
        val tint = Color.WHITE

        fun addIcon(iconRes: Int, isMore: Boolean = false, onClick: () -> Unit) {
            val iv = ImageView(container.context).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = iconPadding
                }
                setImageResource(iconRes)
                setColorFilter(tint)
                scaleType = if (isMore) ImageView.ScaleType.FIT_XY else ImageView.ScaleType.FIT_CENTER
                setPadding(if (isMore) (2 * density).toInt() else 0, 0, if (isMore) (2 * density).toInt() else 0, 0)
                setOnClickListener { onClick() }
            }
            container.addView(iv)
        }

        fun addDivider() {
            val div = View(container.context).apply {
                layoutParams = LinearLayout.LayoutParams((1 * density).toInt(), iconSize).apply {
                    marginEnd = iconPadding
                }
                setBackgroundColor(Color.parseColor("#40FFFFFF"))
            }
            container.addView(div)
        }

        // Copy
        addIcon(R.drawable.ic_content_copy_24dp) {
            onCopy?.invoke(selectedText)
            finishSelection()
        }

        // Define
        addIcon(R.drawable.ic_search_24dp) {
            onDefine?.invoke(selectedText)
            finishSelection()
        }

        // Highlight → switch to color state
        addIcon(R.drawable.ic_bookmark_24dp) {
            buildColorState(container, textView, selectedText, density)
        }

        // Share
        addIcon(R.drawable.ic_share_24dp) {
            onShare?.invoke(selectedText)
            finishSelection()
        }

        addDivider()

        // More → switch to TTS state
        addIcon(R.drawable.ic_more_vert_24dp, isMore = true) {
            buildTtsState(container, selectedText, density)
        }
    }

    private fun buildColorState(
        container: LinearLayout,
        textView: TextView,
        selectedText: String,
        density: Float,
    ) {
        container.removeAllViews()
        currentMode = PopupMode.COLORS

        // Back arrow
        val backSize = (24 * density).toInt()
        val back = ImageView(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(backSize, backSize).apply {
                marginEnd = (12 * density).toInt()
            }
            setImageResource(R.drawable.ic_arrow_back_24dp)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                buildMainState(container, textView, selectedText, density)
                currentMode = PopupMode.MAIN
            }
        }
        container.addView(back)

        // Divider
        val div = View(container.context).apply {
            layoutParams = LinearLayout.LayoutParams((1 * density).toInt(), backSize).apply {
                marginEnd = (10 * density).toInt()
            }
            setBackgroundColor(Color.parseColor("#40FFFFFF"))
        }
        container.addView(div)

        // Color circles
        highlightColors.forEach { color ->
            val circleSize = (28 * density).toInt()
            val circle = View(container.context).apply {
                layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                    marginEnd = (10 * density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke((2 * density).toInt(), Color.WHITE)
                }
                setOnClickListener {
                    applyHighlight(textView, selectedText, textView.selectionStart, textView.selectionEnd, color)
                    finishSelection()
                }
            }
            container.addView(circle)
        }
    }

    private fun buildTtsState(container: LinearLayout, selectedText: String, density: Float) {
        container.removeAllViews()
        currentMode = PopupMode.TTS

        // Back arrow
        val backSize = (24 * density).toInt()
        val back = ImageView(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(backSize, backSize).apply {
                marginEnd = (12 * density).toInt()
            }
            setImageResource(R.drawable.ic_arrow_back_24dp)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                val tv = textViewRef.get() ?: return@setOnClickListener
                buildMainState(container, tv, selectedText, density)
                currentMode = PopupMode.MAIN
            }
        }
        container.addView(back)

        // Read Aloud button
        val tv = TextView(container.context).apply {
            text = "Read Aloud"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                onReadAloud?.invoke(selectedText)
                finishSelection()
            }
        }
        container.addView(tv)
    }

    // ---------------------------------------------------------------------=
    // Actions
    // ---------------------------------------------------------------------=

    private fun applyHighlight(textView: TextView, selectedText: String, start: Int, end: Int, color: Int) {
        if (start < 0 || end < 0 || start >= end) return
        val text = textView.text
        if (text is Spannable) {
            text.setSpan(NovelHighlightSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        onHighlight?.invoke(selectedText, start, end, HighlightColorUtils.toHex(color))
    }

    private fun getSelectedText(textView: TextView): String? {
        val start = textView.selectionStart
        val end = textView.selectionEnd
        if (start < 0 || end < 0 || start >= end) return null
        return textView.text?.subSequence(start, end)?.toString()
    }

    private fun finishSelection() {
        dismissPopup()
        val tv = textViewRef.get() ?: return
        tv.clearFocus()
        tv.post {
            val act = tv.context as? Activity
            act?.window?.decorView?.clearFocus()
        }
    }
}
