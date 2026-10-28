package com.mdev.mnotes.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.core.layout.Workbench

/**
 * Stanowisko (2b-A7, od 2d-A1 jeden model widoku): zeszyt otwarty w edytorze ([main]) i dokumenty dołożone obok — każdy
 * to pas stron (rząd albo kolumna, [horizontal]) we wspólnym widoku ([WorkspaceView], model
 * [com.mdev.mnotes.core.layout.Workspace]). Trzyma edytory pasów, aktywny pas (ten, którego ostatnio dotknięto: jego
 * zaznaczenie, panele i menu) oraz zapisane stanowiska. Pióro i narzędzie są wspólne: aktywny pas przejmuje narzędzie poprzedniego.
 */
class Station(private val ctx: Context, val main: Editor) {
    val extra = mutableStateListOf<Editor>()
    var active by mutableStateOf(main)
        private set
    /** Kierunek stanowiska: rzędy (strony obok siebie) albo kolumny; domyślnie jak pierwszy dokument. */
    var horizontal by mutableStateOf(true)
        private set
    /** Widok stanowiska (stan widoku do zapisu); ustawia go płótno. */
    var view: WorkspaceView? = null
    /** Zapisane stanowisko czekające na ułożenie pasów (powiększenie, przewinięcie, przesunięcia, aktywny pas). */
    private var restore: Workbench.Station? = null
    /** Otwarte okno wyboru zeszytu do dołożenia i okno zapisanych stanowisk. */
    var picking by mutableStateOf(false)
    var listing by mutableStateOf(false)

    val rows: List<Editor> get() = listOf(main) + extra
    val on: Boolean get() = extra.isNotEmpty()

    init {
        main.station = this
        // Przełączenie na zapisane stanowisko: otwarto jego pierwszy zeszyt, reszta dochodzi pod nim.
        main.app.pendingStation?.takeIf { it.ids.firstOrNull() == main.nbId }?.let { s ->
            main.app.pendingStation = null
            s.ids.drop(1).forEach { add(it) }
            applyMode(s); restore = s
        }
    }

    /** Dokumenty zapisanego stanowiska czekającego na odtworzenie (stan stosuje się, gdy pasy są w komplecie). */
    fun pendingIds(): List<String>? = restore?.ids

    /** Odbiera zapisany stan do odtworzenia (raz). */
    fun takeRestore(): Workbench.Station? = restore.also { restore = null }

    private fun applyMode(s: Workbench.Station) { if (s.mode >= 0 && on) setDirection(s.mode != 0) }

    /** Zmienia kierunek całego stanowiska (menu „Układ”); zeszyty nie zapamiętują go jako swojego układu. */
    fun setDirection(h: Boolean) {
        horizontal = h
        for (e in rows) e.forceLayout(if (h) 1 else 0)
    }

    /** Dokłada zeszyt [id] jako rząd na dole; false, gdy już jest albo jest otwarty w innym oknie. */
    fun add(id: String): Boolean {
        if (id == main.nbId || extra.any { it.nbId == id }) return false
        if (main.repo.meta(id) == null) return false
        if (!main.app.openIds.add(id)) { main.say(T.t("Ten zeszyt jest otwarty w innym oknie", "This notebook is open in another window")); return false }
        val e = Editor(ctx, id)
        e.station = this
        e.tool = main.tool
        // Kierunek jak pierwszy dokument: pionowy → kolumny, poziomy i „strona po stronie” → rzędy.
        if (extra.isEmpty()) { horizontal = main.ownLayoutMode != 0; main.forceLayout(if (horizontal) 1 else 0) }
        e.forceLayout(if (horizontal) 1 else 0)
        extra += e
        return true
    }

    /** Zamyka rząd [e] (zapis jak przy zamknięciu zeszytu); ostatni dołożony rząd przywraca zwykły układ głównego. */
    fun remove(e: Editor) {
        if (e === main || e !in extra) return
        if (active === e) activate(main)
        extra.remove(e)
        e.close()
        main.app.openIds.remove(e.nbId)
        if (extra.isEmpty()) main.forceLayout(null)
    }

    /** Przy wyjściu z edytora: zamyka wszystkie dołożone rzędy. */
    fun closeAll() {
        for (e in extra.toList()) { e.close(); main.app.openIds.remove(e.nbId) }
        extra.clear()
        active = main
    }

    /** Rząd [e] staje się aktywny i dostaje narzędzie poprzedniego (pióro i narzędzie są wspólne dla pola). */
    fun activate(e: Editor) {
        if (active === e) return
        val t = active.tool
        active = e
        e.tool = t
    }

    // — Zapisane stanowiska (lista w ustawieniach) —

    fun saved(): List<Workbench.Station> = Workbench.decode(main.settings.stations)
        .map { s -> Workbench.alive(s) { id -> main.repo.meta(id)?.let { !it.isTrashed } == true } }
        .filter { it.ids.isNotEmpty() }

    fun save(name: String) {
        val p = view?.snapshot()
        val s = Workbench.Station(
            name.trim().ifBlank { T.t("Stanowisko", "Workspace") }, rows.map { it.nbId },
            scroll = p?.let { -it.cross } ?: 0f, zoom = p?.zoom ?: 0f, mode = if (horizontal) 1 else 0,
            offsets = p?.main?.toList().orEmpty(), active = rows.indexOf(active).coerceAtLeast(0),
        )
        main.settings.stations = Workbench.encode(Workbench.upsert(Workbench.decode(main.settings.stations), s))
        main.say(T.t("Zapisano stanowisko „${s.name}”", "Workspace “${s.name}” saved"))
    }

    fun delete(name: String) {
        main.settings.stations = Workbench.encode(Workbench.decode(main.settings.stations).filter { it.name != name })
    }

    /**
     * Otwiera zapisane stanowisko. Gdy zaczyna się od bieżącego zeszytu — tylko wymienia rzędy pod nim; inaczej
     * [openNotebook] otwiera jego pierwszy zeszyt, a reszta dochodzi przy starcie edytora (przez `pendingStation`).
     */
    fun open(s: Workbench.Station, openNotebook: (String) -> Unit) {
        val first = s.ids.firstOrNull() ?: return
        if (first == main.nbId) {
            for (e in extra.toList()) if (e.nbId !in s.ids) remove(e)
            s.ids.drop(1).forEach { add(it) }
            applyMode(s); restore = s
            view?.post { view?.restorePending() }
        } else {
            main.app.pendingStation = s
            openNotebook(first)
        }
    }
}
