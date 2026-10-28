package com.mdev.mnotes.app

import androidx.compose.runtime.Composable
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.editor.Editor

/**
 * Skleja rozszerzenia w jedno [LabFeatures] dla [Flavor.lab]: łącze z telefonem z wariantu i kredę, gdy kompilacja ją
 * ma (gałąź `kreda`, katalog `src/kreda/`). Kredę bierzemy po nazwie klasy, bo main nie może jej importować, a gałąź
 * kredy ma tylko dokładać pliki, nie zmieniać plików main (wtedy scalanie main → kreda nie daje konfliktów).
 */
object Extensions {
    /** Obiekt kredy z `src/kreda/`; przy włączonej kiedyś minifikacji potrzebuje reguły `-keep`. */
    const val KREDA = "com.mdev.mnotes.lab.Lab"

    fun of(link: LabFeatures?): LabFeatures? = combine(listOfNotNull(link, load(KREDA)))

    fun combine(all: List<LabFeatures>): LabFeatures? = when (all.size) {
        0 -> null
        1 -> all[0]
        else -> All(all)
    }

    private fun load(name: String): LabFeatures? =
        runCatching { Class.forName(name).getField("INSTANCE").get(null) as LabFeatures }.getOrNull()

    private class All(val parts: List<LabFeatures>) : LabFeatures {
        override val board get() = parts.any { it.board }

        @Composable
        override fun BoardPanel(ed: Editor, imgs: List<ImageEl>, close: () -> Unit) = parts.forEach { it.BoardPanel(ed, imgs, close) }

        // bez skrótu `any`: każda część woła się przy każdej rekompozycji w tej samej kolejności
        @Composable
        override fun phoneButton(): Boolean = parts.map { it.phoneButton() }.any { it }

        @Composable
        override fun PhonePanel(ed: Editor, close: () -> Unit) = parts.forEach { it.PhonePanel(ed, close) }

        @Composable
        override fun SettingsSection() = parts.forEach { it.SettingsSection() }

        override fun onLecturePhoto(ed: Editor, pageId: String, image: ImageEl) = parts.forEach { it.onLecturePhoto(ed, pageId, image) }

        override fun imageInserted(ed: Editor, el: ImageEl) = parts.forEach { it.imageInserted(ed, el) }

        override fun commands(ed: Editor): List<LabCommand> = parts.flatMap { it.commands(ed) }

        @Composable
        override fun Overlay(ed: Editor) = parts.forEach { it.Overlay(ed) }
    }
}
