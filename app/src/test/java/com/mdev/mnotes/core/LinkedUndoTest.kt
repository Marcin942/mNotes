package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.DocEdit
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.LinkedUndo
import com.mdev.mnotes.core.model.UndoStack
import org.junit.Assert.assertEquals
import org.junit.Test

/** 2c-A5: przeniesienie między dokumentami stanowiska cofa się i ponawia jednym krokiem. */
class LinkedUndoTest {

    private val hosts = mapOf("A" to FakeHost().also { it.addPage("p") }, "B" to FakeHost().also { it.addPage("p") })
    private val stacks = mapOf("A" to UndoStack(), "B" to UndoStack())
    private val linked = LinkedUndo<String>()

    private fun ids(k: String) = hosts.getValue(k).contents.getValue("p").elements.map { it.id }

    private fun exec(k: String, e: DocEdit): DocEdit { e.apply(hosts.getValue(k)); stacks.getValue(k).push(e); return e }

    private fun undo(k: String) = linked.undo(k, stacks::get) { stacks.getValue(it).undo(hosts.getValue(it)) }
    private fun redo(k: String) = linked.redo(k, stacks::get) { stacks.getValue(it).redo(hosts.getValue(it)) }

    /** Kreska 1 w A, potem przeniesiona do B (tam z nowym id 2), tak jak robi to stanowisko. */
    private fun moveAToB() {
        val s = stroke(0f, 0f, 5f, 5f)
        exec("A", DocEdit.Elements("p", ElementsEdit.add(hosts.getValue("A").contents.getValue("p"), listOf(s))))
        val a = exec("A", DocEdit.Elements("p", ElementsEdit.remove(hosts.getValue("A").contents.getValue("p"), setOf(s.id))))
        val b = exec("B", DocEdit.Elements("p", ElementsEdit.add(hosts.getValue("B").contents.getValue("p"), listOf(s.withId(s.id + 1000)))))
        linked.link("A", a, "B", b)
        assertEquals(emptyList<Int>(), ids("A"))
        assertEquals(listOf(s.id + 1000), ids("B"))
    }

    @Test fun undoInTargetBringsObjectBack() {
        moveAToB()
        val moved = ids("B")
        undo("B")
        assertEquals(1, ids("A").size)
        assertEquals(emptyList<Int>(), ids("B"))
        redo("B")
        assertEquals(emptyList<Int>(), ids("A"))
        assertEquals(moved, ids("B"))
    }

    @Test fun undoInSourceGivesTheSameResult() {
        moveAToB()
        undo("A")
        assertEquals(1, ids("A").size)
        assertEquals(emptyList<Int>(), ids("B"))
        // Ponowienie z drugiej strony też obejmuje oba.
        redo("B")
        assertEquals(emptyList<Int>(), ids("A"))
        assertEquals(1, ids("B").size)
        // Po ponowieniu na wierzchu obu stosów znów leży para, więc cofnięcie w A ponownie obejmuje oba.
        undo("A")
        assertEquals(1, ids("A").size)
        assertEquals(emptyList<Int>(), ids("B"))
    }

    @Test fun partnerWithLaterStepsUndoesOnlyOwnStep() {
        moveAToB()
        val later = stroke(9f, 9f, 10f, 10f)
        exec("A", DocEdit.Elements("p", ElementsEdit.add(hosts.getValue("A").contents.getValue("p"), listOf(later))))
        undo("B")
        assertEquals(emptyList<Int>(), ids("B"))
        assertEquals(listOf(later.id), ids("A"))
    }

    @Test fun ordinaryStepsAreUntouched() {
        val s = stroke(0f, 0f, 1f, 1f)
        exec("A", DocEdit.Elements("p", ElementsEdit.add(hosts.getValue("A").contents.getValue("p"), listOf(s))))
        exec("B", DocEdit.Elements("p", ElementsEdit.add(hosts.getValue("B").contents.getValue("p"), listOf(stroke(2f, 2f, 3f, 3f)))))
        undo("A")
        assertEquals(emptyList<Int>(), ids("A"))
        assertEquals(1, ids("B").size)
    }
}
