package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.model.DocEdit
import com.mdev.mnotes.core.model.DocHost
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSnapshot
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.model.UndoStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dokument w pamięci dla testów cofania (też [BoardTest]). */
internal class FakeHost : DocHost {
    val order = ArrayList<String>()
    val contents = HashMap<String, PageContent>()
    val specs = HashMap<String, PageSpec>()

    fun addPage(id: String) {
        order.add(id); contents[id] = PageContent.EMPTY; specs[id] = PageSpec()
    }

    override fun applyElements(pageId: String, edit: ElementsEdit) {
        contents[pageId] = contents.getValue(pageId).apply(edit)
    }

    override fun shiftElements(pageId: String, dx: Float, dy: Float) {
        val m = Affine.translation(dx, dy)
        contents[pageId] = PageContent.of(contents.getValue(pageId).elements.map { it.transformed(m) })
    }

    override fun spec(pageId: String) = specs.getValue(pageId)
    override fun setSpec(pageId: String, spec: PageSpec) { specs[pageId] = spec }

    override fun insertPages(entries: List<Pair<Int, PageSnapshot>>) {
        for ((i, s) in entries.sortedBy { it.first }) {
            order.add(i.coerceAtMost(order.size), s.id)
            contents[s.id] = s.content
            specs[s.id] = s.spec
        }
    }

    override fun removePages(ids: List<String>) {
        order.removeAll(ids.toSet())
        ids.forEach { contents.remove(it); specs.remove(it) }
    }

    override fun movePage(pageId: String, toIndex: Int) {
        order.remove(pageId); order.add(toIndex, pageId)
    }

    override fun indexOf(pageId: String) = order.indexOf(pageId)
}

class UndoStackTest {

    private fun newHost(vararg pages: String) = FakeHost().also { h -> pages.forEach(h::addPage) }

    @Test
    fun undoRedoElements() {
        val host = newHost("p1")
        val stack = UndoStack()
        val s = stroke(0f, 0f, 5f, 5f)
        val edit = ElementsEdit.add(host.contents.getValue("p1"), listOf(s))
        val e = DocEdit.Elements("p1", edit)
        e.apply(host); stack.push(e)
        assertEquals(1, host.contents.getValue("p1").size)

        assertEquals(e, stack.undo(host))
        assertEquals(0, host.contents.getValue("p1").size)
        assertTrue(stack.canRedo)
        assertEquals(e, stack.redo(host))
        assertEquals(1, host.contents.getValue("p1").size)
        assertNull(stack.redo(host))
    }

    @Test
    fun pushClearsRedo() {
        val host = newHost("p1")
        val stack = UndoStack()
        fun push() {
            val e = DocEdit.Elements("p1", ElementsEdit.add(host.contents.getValue("p1"), listOf(stroke(0f, 0f, 1f, 1f))))
            e.apply(host); stack.push(e)
        }
        push(); push()
        stack.undo(host)
        assertTrue(stack.canRedo)
        push()
        assertFalse(stack.canRedo)
        assertEquals(2, host.contents.getValue("p1").size)
    }

    @Test
    fun limitDropsOldest() {
        val host = newHost("p1")
        val stack = UndoStack(limit = 3)
        repeat(5) {
            val e = DocEdit.Elements("p1", ElementsEdit.add(host.contents.getValue("p1"), listOf(stroke(0f, 0f, 1f, 1f))))
            e.apply(host); stack.push(e)
        }
        var undone = 0
        while (stack.undo(host) != null) undone++
        assertEquals(3, undone)
        assertEquals(2, host.contents.getValue("p1").size)
    }

    @Test
    fun specEditRoundTrip() {
        val host = newHost("p1")
        val before = host.spec("p1")
        val after = before.withTemplate(Template.GRID)
        val e = DocEdit.Spec("p1", before, after)
        e.apply(host)
        assertEquals(Template.GRID, host.spec("p1").template)
        e.revert(host)
        assertEquals(before, host.spec("p1"))
    }

    @Test
    fun pageInsertRemoveMove() {
        val host = newHost("a", "b", "c")
        val snapB = PageSnapshot("b", host.spec("b"), host.contents.getValue("b"))
        val snapC = PageSnapshot("c", host.spec("c"), host.contents.getValue("c"))
        val rm = DocEdit.RemovePages(listOf(1 to snapB, 2 to snapC))
        rm.apply(host)
        assertEquals(listOf("a"), host.order)
        rm.revert(host)
        assertEquals(listOf("a", "b", "c"), host.order)

        val mv = DocEdit.MovePage("a", 0, 2)
        mv.apply(host)
        assertEquals(listOf("b", "c", "a"), host.order)
        mv.revert(host)
        assertEquals(listOf("a", "b", "c"), host.order)

        val ins = DocEdit.InsertPages(listOf(0 to PageSnapshot("z", PageSpec(), PageContent.EMPTY)))
        ins.apply(host)
        assertEquals(listOf("z", "a", "b", "c"), host.order)
        ins.revert(host)
        assertEquals(listOf("a", "b", "c"), host.order)
    }

    @Test
    fun groupRevertsInReverseOrder() {
        val host = newHost("p1")
        val s0 = host.spec("p1")
        val s1 = s0.withTemplate(Template.LINED)
        val s2 = s1.copy(spacing = 30f)
        val g = DocEdit.Group(listOf(DocEdit.Spec("p1", s0, s1), DocEdit.Spec("p1", s1, s2)))
        g.apply(host)
        assertEquals(s2, host.spec("p1"))
        g.revert(host)
        assertEquals(s0, host.spec("p1"))
        assertEquals(listOf("p1"), g.pageIds)
    }
}
