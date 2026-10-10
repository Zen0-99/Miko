package miko.jssource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsDomStoreTest {
    private val html = """
        <html><head><title>T</title></head><body>
          <div id="root" class="a b">
            <p class="item">one <b>bold</b></p>
            <p class="item special" data-x="42">two</p>
            <span>tail</span>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun loadAndSelect() {
        val store = JsDomStore()
        val doc = store.loadDocument(html)
        assertTrue(doc > 0)
        val items = store.select(doc, "p.item")
        assertEquals(2, items.size)
        assertEquals("one bold", store.getText(items[0]))
        assertEquals("two", store.getText(items[1]))
    }

    @Test
    fun traversalAndPredicates() {
        val store = JsDomStore()
        val doc = store.loadDocument(html)
        val root = store.select(doc, "#root")[0]
        val kids = store.children(root, null)
        assertEquals(3, kids.size)
        assertEquals("p", store.getTagName(kids[0]))
        assertEquals("span", store.getTagName(kids[2]))
        // next/prev
        assertEquals(kids[1], store.next(kids[0], null))
        assertEquals(kids[0], store.prev(kids[1], null))
        assertEquals(kids[2], store.next(kids[0], "span"))
        // predicates
        assertTrue(store.matches(kids[1], ".special"))
        assertFalse(store.matches(kids[0], ".special"))
        assertTrue(store.hasClass(kids[1], "item"))
        // closest("div") climbs to #root; closest("p") returns self
        assertEquals(root, store.closest(kids[1], "div"))
        assertEquals(kids[1], store.closest(kids[1], "p"))
    }

    @Test
    fun attributesAndContent() {
        val store = JsDomStore()
        val doc = store.loadDocument(html)
        val root = store.select(doc, "#root")[0]
        assertEquals("a b", store.getAttr(root, "class"))
        val item = store.select(doc, ".special")[0]
        assertEquals("42", store.getAttr(item, "data-x"))
        assertEquals("42", store.getData(item, "x"))
        assertNull(store.getAttr(item, "nope"))
        val attrs = store.getAllAttrs(item)
        assertEquals("item special", attrs["class"])
        assertEquals("42", attrs["data-x"])
    }

    @Test
    fun mutation() {
        val store = JsDomStore()
        val doc = store.loadDocument(html)
        val root = store.select(doc, "#root")[0]
        val span = store.select(doc, "span")[0]
        store.before(span, "<em>ins</em>")
        val ems = store.select(doc, "em")
        assertEquals(1, ems.size)
        store.addClass(span, "tagged")
        assertTrue(store.hasClass(span, "tagged"))
        store.setAttr(span, "data-y", "7")
        assertEquals("7", store.getAttr(span, "data-y"))
        store.remove(span)
        assertEquals(0, store.select(doc, "span").size)
    }

    @Test
    fun contentsIncludesTextNodes() {
        val store = JsDomStore()
        val doc = store.loadDocument("<div>hi<b>x</b>yo</div>")
        val div = store.select(doc, "div")[0]
        val contents = store.contents(div)
        assertEquals(3, contents.size)
        assertTrue(store.isTextNode(contents[0]))
        assertFalse(store.isTextNode(contents[1]))
        assertEquals("hi", store.getText(contents[0]))
    }

    @Test
    fun releaseInvalidatesHandles() {
        val store = JsDomStore()
        val doc = store.loadDocument(html)
        val p = store.select(doc, "p")[0]
        store.release(p)
        assertEquals("", store.getText(p))
        store.releaseAll()
        assertEquals(0, store.select(doc, "p").size)
    }
}
