package com.mdev.mnotes.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Lekka lokalizacja PL/EN: `T.t("tekst", "text")`. Język = ustawienie aplikacji (0 system, 1 polski, 2 angielski);
 * zmiana jest stanem Compose, więc interfejs odświeża się od razu.
 */
object T {
    /** 0 = systemowy, 1 = polski, 2 = angielski. */
    var mode by mutableIntStateOf(0)

    val pl: Boolean get() = when (mode) { 1 -> true; 2 -> false; else -> Locale.getDefault().language == "pl" }

    fun t(pl: String, en: String): String = if (this.pl) pl else en
}
