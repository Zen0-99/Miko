package eu.kanade.tachiyomi.ui.reader.novel

import android.text.style.BackgroundColorSpan

class NovelHighlightSpan(color: Int) : BackgroundColorSpan(color)

class NovelTtsHighlightSpan(
    textView: android.widget.TextView,
    private val rangeStart: Int,
    private val rangeEnd: Int,
    color: Int,
) : android.text.style.LineBackgroundSpan {
    private val view = java.lang.ref.WeakReference(textView)
    private val radius = 3f * textView.resources.displayMetrics.density
    private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val path = android.graphics.Path()
    private val region = android.graphics.Region()
    private val clip = android.graphics.Region()
    private val rect = android.graphics.Rect()

    override fun drawBackground(
        canvas: android.graphics.Canvas,
        paint: android.graphics.Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        val layout = view.get()?.layout ?: return
        val from = maxOf(start, rangeStart)
        val to = minOf(end, rangeEnd, text.length)
        if (from >= to) return
        path.reset()
        layout.getSelectionPath(from, to, path)
        clip.set(left, top, right, bottom)
        region.setPath(path, clip)
        val iterator = android.graphics.RegionIterator(region)
        while (iterator.next(rect)) {
            canvas.drawRoundRect(
                rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(),
                radius, radius, fill,
            )
        }
    }
}
