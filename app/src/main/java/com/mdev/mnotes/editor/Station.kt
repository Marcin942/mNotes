package com.mdev.mnotes.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.core.layout.Workbench

/**
 * Stanowisko (2b-A7, zastępuje dwa płótna F4): zeszyt otwarty w edytorze ([main]) i dokumenty dołożone pod nim — każdy
 * to rząd stron na wspólnym polu ([com.mdev.mnotes.core.layout.Workbench]). Trzyma edytory rzędów, aktywny rząd (ten,
 * którego ostatnio dotknięto: jego zaznaczenie, panele i menu), wspólne powiększenie i przewinięcie pola oraz zapisane
 * stanowiska. Pióro i narzędzie są wspólne: aktywny rząd przejmuje narzędzie poprzedniego.
 */
class Station(private val ctx: Context, val main: Editor) {
    val extra = mutableStateListOf<Editor>()
    var active by mutableStateOf(main)
        private set
    /** Wspólne powiększenie rzędów (px/pt); 0 — jeszcze nieustalone (pole ustala je przy pierwszym układzie). */
    var zoom by mutableFloatStateOf(0f)
    var scroll by mutableFloatStateOf(0f)
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
            zoom = s.zoom; scroll = s.scroll
        }
    }

    /** Dokłada zeszyt [id] jako rząd na dole; false, gdy już jest albo jest otwarty w innym oknie. */
    fun add(id: String): Boolean {
        if (id == main.nbId || extra.any { it.nbId == id }) return false
        if (main.repo.meta(id) == null) return false
        if (!main.app.openIds.add(id)) { main.say(T.t("Ten zeszyt jest otwarty w innym oknie", "This notebook is open in another window")); return false }
        val e = Editor(ctx, id)
        e.station = this
        e.tool = main.tool
        if (extra.isEmpty()) main.forceRowLayout(true)
        e.forceRowLayout(true)
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
        if (extra.isEmpty()) {
            scroll = 0f; zoom = 0f
            main.view?.leaveStation()
            main.forceRowLayout(false)
        }
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
        val s = Workbench.Station(name.trim().ifBlank { T.t("Stanowisko", "Workspace") }, rows.map { it.nbId }, scroll, zoom)
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
            if (s.zoom > 0f) zoom = s.zoom
            scroll = s.scroll
        } else {
            main.app.pendingStation = s
            openNotebook(first)
        }
    }
}
