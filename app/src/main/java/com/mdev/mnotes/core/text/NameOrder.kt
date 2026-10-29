package com.mdev.mnotes.core.text

import java.text.Collator
import java.util.Locale

/**
 * Kolejność nazw w bibliotece (zeszyty, foldery, etykiety) po polsku, bez rozróżniania wielkości liter. 3c-A16: porównanie
 * kodów znaków stawiało „Ćwiczenia”, „Łacina”, „Środa” za „Zoologia”.
 */
object NameOrder : Comparator<String> {
    private val collator: Collator = Collator.getInstance(Locale("pl")).apply { strength = Collator.SECONDARY }

    @Synchronized override fun compare(a: String, b: String): Int = collator.compare(a, b)
}
