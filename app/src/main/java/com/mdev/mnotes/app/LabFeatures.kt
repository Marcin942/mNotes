package com.mdev.mnotes.app

import androidx.compose.runtime.Composable
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.editor.Editor

/**
 * Punkt zaczepienia wariantu `lab` (kreda, rozpoznawanie, Nearby). Wariant `core` daje [Flavor.lab] = null, więc
 * wspólny kod pyta tylko o ten interfejs i nigdy nie widzi kodu laboratorium; implementacja leży w `src/lab/`.
 */
interface LabFeatures {
    /**
     * Panel „Oczyść tablicę” dla zaznaczonych zdjęć (otwierany przyciskiem w menu zaznaczenia); kilka zdjęć tej samej
     * tablicy łączy medianą.
     */
    @Composable
    fun BoardPanel(ed: Editor, imgs: List<ImageEl>, close: () -> Unit)

    /** Czy pokazać przycisk „Zdjęcie z telefonu” (łącze Nearby włączone w ustawieniach laboratorium). */
    @Composable
    fun phoneButton(): Boolean

    /** Okno „Zdjęcie z telefonu”: tablet czeka na zdjęcia ze sparowanego telefonu i wstawia je na stronę. */
    @Composable
    fun PhonePanel(ed: Editor, close: () -> Unit)

    /** Kategoria „Laboratorium” w ustawieniach. */
    @Composable
    fun SettingsSection()
}
