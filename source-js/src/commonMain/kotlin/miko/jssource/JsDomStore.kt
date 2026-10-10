package miko.jssource

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * Handle-based DOM store backed by Ksoup (KMP jsoup port).
 *
 * Ported verbatim from NovelJsDomStore — each parsed [Document] and every
 * [Element]/[TextNode] selected from it receives a stable integer handle;
 * the JS cheerio module operates exclusively on these handles.
 *
 * Thread-safety: instances are **not** thread-safe. All calls happen on the
 * owning runtime's engine thread.
 */
class JsDomStore {

    private var nextHandle = 1
    private val nodes = LinkedHashMap<Int, Node>(256, 0.75f, true)

    // ------------------------------------------------------------------
    // Document lifecycle
    // ------------------------------------------------------------------

    fun loadDocument(html: String): Int {
        val doc: Document = Ksoup.parse(html)
        return assignHandle(doc)
    }

    // ------------------------------------------------------------------
    // CSS selectors
    // ------------------------------------------------------------------

    fun select(handle: Int, selector: String): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        return el.select(selector).map { assignHandle(it) }.toIntArray()
    }

    /** One-shot helper for `__native.select(html, selector)`. */
    fun selectHtml(html: String, selector: String): List<Int> {
        val doc = loadDocument(html)
        return select(doc, selector).toList()
    }

    // ------------------------------------------------------------------
    // Tree traversal
    // ------------------------------------------------------------------

    fun parent(handle: Int): Int {
        val node = nodes[handle] ?: return -1
        val p = node.parent() ?: return -1
        if (p is Document) return -1
        if (p.parentNode() is Document) return -1
        return assignHandle(p)
    }

    fun children(handle: Int, selector: String?): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        val kids = el.children()
        return if (selector.isNullOrBlank()) {
            kids.map { assignHandle(it) }.toIntArray()
        } else {
            kids.filter { it.`is`(selector) }.map { assignHandle(it) }.toIntArray()
        }
    }

    fun next(handle: Int, selector: String?): Int {
        val el = elementOrNull(handle) ?: return -1
        if (selector.isNullOrBlank()) {
            val n = el.nextElementSibling() ?: return -1
            return assignHandle(n)
        }
        var cur = el.nextElementSibling()
        while (cur != null) {
            if (cur.`is`(selector)) return assignHandle(cur)
            cur = cur.nextElementSibling()
        }
        return -1
    }

    fun nextAll(handle: Int, selector: String?): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        val result = mutableListOf<Int>()
        var cur = el.nextElementSibling()
        while (cur != null) {
            if (selector.isNullOrBlank() || cur.`is`(selector)) {
                result += assignHandle(cur)
            }
            cur = cur.nextElementSibling()
        }
        return result.toIntArray()
    }

    fun prev(handle: Int, selector: String?): Int {
        val el = elementOrNull(handle) ?: return -1
        if (selector.isNullOrBlank()) {
            val p = el.previousElementSibling() ?: return -1
            return assignHandle(p)
        }
        var cur = el.previousElementSibling()
        while (cur != null) {
            if (cur.`is`(selector)) return assignHandle(cur)
            cur = cur.previousElementSibling()
        }
        return -1
    }

    fun prevAll(handle: Int, selector: String?): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        val result = mutableListOf<Int>()
        var cur = el.previousElementSibling()
        while (cur != null) {
            if (selector.isNullOrBlank() || cur.`is`(selector)) {
                result += assignHandle(cur)
            }
            cur = cur.previousElementSibling()
        }
        return result.toIntArray()
    }

    fun siblings(handle: Int, selector: String?): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        val parent = el.parent() ?: return IntArray(0)
        return parent.children()
            .filter { it !== el && (selector.isNullOrBlank() || it.`is`(selector)) }
            .map { assignHandle(it) }
            .toIntArray()
    }

    fun closest(handle: Int, selector: String): Int {
        var cur: Element? = elementOrNull(handle) ?: return -1
        while (cur != null && cur !is Document) {
            if (cur.`is`(selector)) return assignHandle(cur)
            val p = cur.parent()
            cur = if (p is Element && p !is Document) p else null
        }
        return -1
    }

    fun contents(handle: Int): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        return el.childNodes().map { assignHandle(it) }.toIntArray()
    }

    // ------------------------------------------------------------------
    // Predicates
    // ------------------------------------------------------------------

    fun matches(handle: Int, selector: String): Boolean {
        val el = elementOrNull(handle) ?: return false
        return el.`is`(selector)
    }

    fun has(handle: Int, selector: String): Boolean {
        val el = elementOrNull(handle) ?: return false
        return el.select(selector).isNotEmpty()
    }

    fun not(handle: Int, selector: String): IntArray {
        val el = elementOrNull(handle) ?: return IntArray(0)
        return if (!el.`is`(selector)) intArrayOf(handle) else IntArray(0)
    }

    // ------------------------------------------------------------------
    // Content accessors
    // ------------------------------------------------------------------

    fun getHtml(handle: Int): String {
        val node = nodes[handle] ?: return ""
        return when (node) {
            is Element -> {
                val tagName = node.tagName().lowercase()
                if (tagName == "script" || tagName == "style") node.data() else node.html()
            }
            is TextNode -> node.getWholeText()
            else -> ""
        }
    }

    fun getOuterHtml(handle: Int): String {
        val node = nodes[handle] ?: return ""
        return when (node) {
            is Element -> node.outerHtml()
            is TextNode -> node.getWholeText()
            else -> ""
        }
    }

    fun getXml(handle: Int): String {
        val node = nodes[handle] ?: return ""
        return when (node) {
            is Document -> {
                val original = node.outputSettings().clone()
                node.outputSettings()
                    .syntax(Document.OutputSettings.Syntax.xml)
                    .escapeMode(com.fleeksoft.ksoup.nodes.Entities.EscapeMode.xhtml)
                val result = node.html()
                node.outputSettings(original)
                result
            }
            is Element -> {
                val doc = node.ownerDocument()
                val original = doc?.outputSettings()?.clone()
                doc?.outputSettings()
                    ?.syntax(Document.OutputSettings.Syntax.xml)
                    ?.escapeMode(com.fleeksoft.ksoup.nodes.Entities.EscapeMode.xhtml)
                val result = node.outerHtml()
                if (doc != null && original != null) {
                    doc.outputSettings(original)
                }
                result
            }
            is TextNode -> node.getWholeText()
            else -> ""
        }
    }

    fun getText(handle: Int): String {
        val node = nodes[handle] ?: return ""
        return when (node) {
            is Element -> {
                val tagName = node.tagName().lowercase()
                if (tagName == "script" || tagName == "style") node.data() else node.text()
            }
            is TextNode -> node.getWholeText()
            else -> ""
        }
    }

    fun getAttr(handle: Int, name: String): String? {
        val el = elementOrNull(handle) ?: return null
        if (!el.hasAttr(name)) return null
        return el.attr(name)
    }

    fun setAttr(handle: Int, name: String, value: String) {
        val el = elementOrNull(handle) ?: return
        el.attr(name, value)
    }

    fun removeAttr(handle: Int, name: String) {
        val el = elementOrNull(handle) ?: return
        el.removeAttr(name)
    }

    fun getAllAttrs(handle: Int): Map<String, String> {
        val el = elementOrNull(handle) ?: return emptyMap()
        return el.attributes().associate { it.key to it.value }
    }

    fun hasClass(handle: Int, className: String): Boolean {
        val el = elementOrNull(handle) ?: return false
        return el.hasClass(className)
    }

    fun getData(handle: Int, key: String): String? {
        return getAttr(handle, "data-$key")
    }

    fun getVal(handle: Int): String? {
        val el = elementOrNull(handle) ?: return null
        // Ksoup has no Element.val(); jsoup's is the "value" attribute.
        return if (el.hasAttr("value")) el.attr("value") else null
    }

    fun getTagName(handle: Int): String {
        val el = elementOrNull(handle) ?: return ""
        return el.tagName()
    }

    fun isTextNode(handle: Int): Boolean {
        return nodes[handle] is TextNode
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    fun replaceWith(handle: Int, newHtml: String) {
        val node = nodes[handle] ?: return
        val parsed = Ksoup.parseBodyFragment(newHtml).body().childNodes()
        if (parsed.isEmpty()) {
            node.remove()
        } else {
            val parent = node.parentNode() ?: return
            val siblings = parent.childNodes()
            val idx = siblings.indexOf(node)
            if (idx < 0) return
            parsed.reversed().forEach { newNode ->
                node.before(newNode.clone())
            }
            node.remove()
        }
    }

    fun before(handle: Int, html: String) {
        val el = elementOrNull(handle) ?: return
        if (html.isBlank()) return
        el.before(html)
    }

    fun after(handle: Int, html: String) {
        val el = elementOrNull(handle) ?: return
        if (html.isBlank()) return
        el.after(html)
    }

    fun append(handle: Int, html: String) {
        val el = elementOrNull(handle) ?: return
        if (html.isBlank()) return
        el.append(html)
    }

    fun prepend(handle: Int, html: String) {
        val el = elementOrNull(handle) ?: return
        if (html.isBlank()) return
        el.prepend(html)
    }

    fun empty(handle: Int) {
        val el = elementOrNull(handle) ?: return
        el.empty()
    }

    fun remove(handle: Int) {
        val node = nodes[handle] ?: return
        node.remove()
    }

    fun addClass(handle: Int, className: String) {
        val el = elementOrNull(handle) ?: return
        el.addClass(className)
    }

    fun removeClass(handle: Int, className: String) {
        val el = elementOrNull(handle) ?: return
        el.removeClass(className)
    }

    // ------------------------------------------------------------------
    // Handle management
    // ------------------------------------------------------------------

    fun release(handle: Int) {
        nodes.remove(handle)
    }

    fun releaseAll() {
        nodes.clear()
        nextHandle = 1
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun assignHandle(node: Node): Int {
        for ((h, n) in nodes) {
            if (n === node) return h
        }
        val h = nextHandle++
        nodes[h] = node
        evictIfNeeded()
        return h
    }

    private fun elementOrNull(handle: Int): Element? {
        return nodes[handle] as? Element
    }

    private fun evictIfNeeded() {
        if (nodes.size <= MAX_HANDLES) return
        val iterator = nodes.iterator()
        val toRemove = nodes.size - MAX_HANDLES
        var removed = 0
        while (iterator.hasNext() && removed < toRemove) {
            iterator.next()
            iterator.remove()
            removed++
        }
    }

    companion object {
        private const val MAX_HANDLES = 50_000
    }
}

/** Encode an IntArray of handles as the JSON string the JS bridge expects. */
fun IntArray.toHandleJson(): String = joinToString(",", "[", "]")
