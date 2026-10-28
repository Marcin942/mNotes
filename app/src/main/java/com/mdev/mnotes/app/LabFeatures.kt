package com.mdev.mnotes.app

import androidx.compose.runtime.Composable
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.editor.Editor

/** Polecenie wariantu lab w menu i palecie poleceń (Ctrl+K); [group] to indeks grupy z `CmdDefs.groups`. */
class LabCommand(val id: String, val group: Int, val pl: String, val en: String, val enabled: () -> Boolean, val run: () -> Unit)

/**
 * Punkt zaczepienia rozszerzeń: łącze z telefonem (wariant `gms`, `src/gms/`) i kreda (gałąź `kreda`, `src/kreda/`).
 * Wspólny kod pyta tylko o ten interfejs przez [Flavor.lab] (null = żadnego rozszerzenia) i nigdy nie widzi ich kodu;
 * kilka rozszerzeń naraz skleja [Extensions]. Każda metoda ma domyślne „nic”, więc rozszerzenie nadpisuje tylko swoje.
 */
interface LabFeatures {
    /** Czy jest kreda: przycisk „Oczyść tablicę” w menu zdjęć i zdania o kredzie w przewodniku. */
    val board: Boolean get() = false

    /**
     * Panel „Oczyść tablicę” dla zaznaczonych zdjęć (otwierany przyciskiem w menu zaznaczenia); kilka zdjęć tej samej
     * tablicy łączy medianą.
     */
    @Composable
    fun BoardPanel(ed: Editor, imgs: List<ImageEl>, close: () -> Unit) {}

    /** Czy pokazać przycisk „Zdjęcie z telefonu” (łącze Nearby włączone w ustawieniach laboratorium). */
    @Composable
    fun phoneButton(): Boolean = false

    /** Okno „Zdjęcie z telefonu”: tablet czeka na zdjęcia ze sparowanego telefonu i wstawia je na stronę. */
    @Composable
    fun PhonePanel(ed: Editor, close: () -> Unit) {}

    /** Sekcje w kategorii „Laboratorium” w ustawieniach. */
    @Composable
    fun SettingsSection() {}

    /**
     * Zdjęcie z trybu wykładu właśnie trafiło na nową stronę [pageId] jako [image] (A2-8; wołane na wątku UI, po
     * wstawieniu). Lab może je przepuścić przez kredę jako osobną edycję, więc jedno cofnięcie wraca do oryginału (B2-10).
     * Domyślnie nic — wariant bez tej funkcji i wspólny kod nie muszą o niej wiedzieć.
     */
    fun onLecturePhoto(ed: Editor, pageId: String, image: ImageEl) {}

    /**
     * Zdjęcie [el] właśnie wstawione na stronę (wywołanie z wątku UI). Lab sprawdza w tle, czy to ciemna tablica,
     * i raz podpowiada „Oczyść tablicę”; domyślnie nic, żeby inne implementacje nie musiały o tym wiedzieć.
     */
    fun imageInserted(ed: Editor, el: ImageEl) {}

    /** Polecenia laboratorium dopisywane do listy poleceń edytora [ed] (menu, paleta); w core żadnych. */
    fun commands(ed: Editor): List<LabCommand> = emptyList()

    /** Okna laboratorium otwierane poleceniami z [commands], rysowane raz nad edytorem; domyślnie nic. */
    @Composable
    fun Overlay(ed: Editor) {}
}
