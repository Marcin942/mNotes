package com.mdev.mnotes.core

import com.mdev.mnotes.core.text.NameOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** 3c-A16: nazwy w bibliotece w kolejności polskiego alfabetu, bez względu na wielkość liter. */
class NameOrderTest {
    @Test fun polishLettersInAlphabeticalPlace() {
        val names = listOf("Zoologia", "Łacina", "biologia", "Ćwiczenia", "Ósemki", "Środa", "Ala", "Ąbc", "lato", "Cebula")
        assertEquals(
            listOf("Ala", "Ąbc", "biologia", "Cebula", "Ćwiczenia", "lato", "Łacina", "Ósemki", "Środa", "Zoologia"),
            names.sortedWith(NameOrder),
        )
    }

    @Test fun caseDoesNotDecideOrder() {
        assertEquals(listOf("alfa", "Beta", "gamma"), listOf("gamma", "Beta", "alfa").sortedWith(NameOrder))
    }
}
