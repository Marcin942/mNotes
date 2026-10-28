package com.mdev.mnotes.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExtensionsTest {
    private object Link : LabFeatures
    private object Board : LabFeatures { override val board = true }

    @Test
    fun noneGivesNull() = assertNull(Extensions.combine(emptyList()))

    @Test
    fun singleIsPassedThrough() = assertSame(Link, Extensions.combine(listOf(Link)))

    @Test
    fun boardWhenAnyPartHasIt() {
        assertTrue(Extensions.combine(listOf(Link, Board))!!.board)
        assertFalse(Extensions.combine(listOf(Link, Link))!!.board)
    }

    /** Kreda ładuje się po nazwie klasy: zmiana nazwy obiektu w `src/kreda` bez zmiany `Extensions.KREDA` ma tu wybuchnąć. */
    @Test
    fun kredaLoadsExactlyWhenCompiledIn() {
        val compiled = File("src/kreda/java").isDirectory
        val ext = Extensions.of(null)
        assertEquals(compiled, ext != null)
        assertEquals(compiled, ext?.board == true)
        if (compiled) assertTrue(Extensions.of(Link)!!.board) else assertSame(Link, Extensions.of(Link))
    }
}
