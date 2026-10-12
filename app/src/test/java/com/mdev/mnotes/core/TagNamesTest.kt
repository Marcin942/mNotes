package com.mdev.mnotes.core

import com.mdev.mnotes.core.text.TagNames
import org.junit.Assert.assertEquals
import org.junit.Test

class TagNamesTest {
    @Test
    fun cleanDropsHashAndBreaks() {
        assertEquals("fizyka", TagNames.clean("#fizyka"))
        assertEquals("a b", TagNames.clean("a\nb"))
        assertEquals("", TagNames.clean("#"))
    }

    @Test
    fun renameKeepsPosition() {
        assertEquals(listOf("a", "x", "c"), TagNames.renamed(listOf("a", "b", "c"), "b", "x"))
    }

    @Test
    fun renameIntoExistingMerges() {
        assertEquals(listOf("a", "c"), TagNames.renamed(listOf("a", "b", "c"), "b", "a"))
        assertEquals(listOf("a"), TagNames.renamed(listOf("a", "b"), "b", "a"))
    }

    @Test
    fun renameOfAbsentTagChangesNothing() {
        assertEquals(listOf("a", "b"), TagNames.renamed(listOf("a", "b"), "z", "x"))
    }

    @Test
    fun colorFollowsRenamedTag() {
        assertEquals(mapOf("x" to 5, "k" to 9), TagNames.renamedColors(mapOf("b" to 5, "k" to 9), "b", "x"))
    }

    @Test
    fun mergeKeepsColorOfTarget() {
        assertEquals(mapOf("a" to 1), TagNames.renamedColors(mapOf("a" to 1, "b" to 2), "b", "a"))
    }

    @Test
    fun colorlessOldTagLeavesTargetUntouched() {
        assertEquals(mapOf("a" to 1), TagNames.renamedColors(mapOf("a" to 1), "b", "a"))
    }
}
