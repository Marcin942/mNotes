package com.mdev.mnotes.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.core.layout.Workbench
import com.mdev.mnotes.core.prefs.Profiles
import com.mdev.mnotes.core.model.LinkedUndo
import com.mdev.mnotes.core.model.UndoStack

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
    var view: WorkspaceView? by mutableStateOf(null)
    /** Zapisane stanowisko czekające na ułożenie pasów (powiększenie, przewinięcie, przesunięcia, aktywny pas). */
    private var restore: Workbench.Station? = null
    /** Otwarte okno wyboru zeszytu do dołożenia i okno zapisanych stanowisk. */
    var picking by mutableStateOf(false)
    var listing by mutableStateOf(false)

    /** LP-1: miejsce głównego zeszytu wśród rzędów (dokument dołożony „powyżej” staje przed nim). */
    var mainAt by mutableStateOf(0)
        private set
    val rows: List<Editor> get() = extra.take(mainAt) + main + extra.drop(mainAt)

    /** LP-1: czy [id] to karta tego okna (jej zeszyt jest „nasz” w `openIds`, więc może stanąć na stanowisku). */
    var ownTab: (String) -> Boolean = { false }
    val on: Boolean get() = extra.isNotEmpty()

    /** 2c-A11: profil przypięty do zeszytu albo stanowiska — włączony przy otwarciu, zdejmowany przy wyjściu. */
    private var pin: Profiles.Pin? = null
    private var pinChecked = false
    private var stationProfile = ""

    /** Pary kroków cofania przeniesień między dokumentami (2c-A5). */
    val linked = LinkedUndo<Editor>()

    /** Stos cofania dokumentu, który nadal jest na stanowisku (zamknięty nie bierze udziału w cofaniu pary). */
    fun stackOf(e: Editor): UndoStack? = if (e === main || e in extra) e.doc.undo else null

    init {
        main.station = this
        // Przełączenie na zapisane stanowisko: otwarto jego pierwszy zeszyt, reszta dochodzi pod nim.
        main.app.pendingStation?.takeIf { it.ids.firstOrNull() == main.nbId }?.let { s ->
            main.app.pendingStation = null
            s.ids.drop(1).forEach { add(it) }
            applyMode(s); restore = present(s)
            stationProfile = s.profile
        }
    }

    /**
     * 3c-B3: stan do odtworzenia tylko dla dokumentów, które naprawdę stanęły (któryś mógł być otwarty w innym oknie);
     * dawniej lista się nie zgadzała i stan czekał bez końca — aż przypadkiem pasował do innego układu.
     */
    private fun present(s: Workbench.Station): Workbench.Station {
        val ids = rows.map { it.nbId }.toSet()
        return Workbench.alive(s) { it in ids }
    }

    /** Przy pierwszym pokazaniu edytora: profil stanowiska albo zeszytu. Powrót z ustawień nie włącza go drugi raz. */
    fun pinOnOpen() {
        if (pinChecked) return
        pinChecked = true
        pinProfile(stationProfile.ifEmpty { Profiles.pinsDecode(main.settings.profilePins)[main.nbId] } ?: return)
    }

    private fun pinProfile(id: String) {
        val set = main.settings
        val p = set.profiles.firstOrNull { it.id == id } ?: return
        pin = Profiles.pin(pin, id, set.profileActive)
        if (set.profileActive != id) main.useProfile(p, pinned = true)
    }

    /** Wyjście: przypięty profil ustępuje temu, co działało przed nim — chyba że w trakcie zmieniono profil ręcznie. */
    fun unpinProfile() {
        val back = Profiles.unpin(pin, main.settings.profileActive)
        pin = null
        if (back != null) main.settings.switchProfile(main.settings.profiles.firstOrNull { it.id == back })
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

    /**
     * Dokłada zeszyt [id] jako rząd na pozycji [at] wśród [rows] (domyślnie na dole); false, gdy już jest albo jest
     * otwarty w innym oknie. Karta z tego okna (LP-1) nie jest „innym oknem”: przechodzi na stanowisko.
     */
    fun add(id: String, at: Int = rows.size): Boolean {
        if (id == main.nbId || extra.any { it.nbId == id }) return false
        if (main.repo.meta(id) == null) return false
        if (!ownTab(id) && !main.app.openIds.add(id)) { main.say(T.t("Ten zeszyt jest otwarty w innym oknie", "This notebook is open in another window")); return false }
        val e = Editor(ctx, id)
        e.station = this
        e.tool = main.tool
        // Kierunek jak pierwszy dokument: pionowy → kolumny, poziomy i „strona po stronie” → rzędy.
        if (extra.isEmpty()) { horizontal = main.ownLayoutMode != 0; main.forceLayout(if (horizontal) 1 else 0) }
        e.forceLayout(if (horizontal) 1 else 0)
        val pos = at.coerceIn(0, rows.size)
        if (pos <= mainAt) { extra.add(pos, e); mainAt++ } else extra.add(pos - 1, e)
        return true
    }

    /** Zamyka rząd [e] (zapis jak przy zamknięciu zeszytu); ostatni dołożony rząd przywraca zwykły układ głównego. */
    fun remove(e: Editor) {
        if (e === main || e !in extra) return
        if (active === e) activate(main)
        if (extra.indexOf(e) < mainAt) mainAt--
        extra.remove(e)
        e.close()
        if (!ownTab(e.nbId)) main.app.openIds.remove(e.nbId)
        if (extra.isEmpty()) main.forceLayout(null)
    }

    /** Przy wyjściu z edytora: zamyka wszystkie dołożone rzędy. */
    fun closeAll() {
        for (e in extra.toList()) { e.close(); if (!ownTab(e.nbId)) main.app.openIds.remove(e.nbId) }
        extra.clear()
        mainAt = 0
        active = main
    }

    /** LP-1: indeks rzędu najbliżej środka widoku (podświetlona karta); bez widoku — aktywny. */
    fun centerIndex(): Int = view?.centerLane()?.takeIf { it >= 0 } ?: rows.indexOf(active)

    /** LP-1: przewija stanowisko do rzędu [e] i robi go aktywnym (dotknięcie jego karty). */
    fun focus(e: Editor) {
        activate(e)
        view?.scrollToLane(rows.indexOf(e))
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
            profile = main.settings.profileActive,
        )
        main.settings.stations = Workbench.encode(Workbench.upsert(Workbench.decode(main.settings.stations), s))
        val prof = main.settings.activeProfile?.name
        main.say(if (prof == null) T.t("Zapisano stanowisko „${s.name}”", "Workspace “${s.name}” saved")
            else T.t("Zapisano stanowisko „${s.name}” z profilem „$prof”", "Workspace “${s.name}” saved with profile “$prof”"))
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
            // 3c-B3: zostają tylko rzędy stojące już w kolejności zapisu; reszta wychodzi i dochodzi na swoje miejsce
            // (dawniej stare rzędy zostawały wyżej, kolejność się nie zgadzała i zapisany stan nigdy się nie stosował).
            val want = s.ids.drop(1)
            // zapis zawsze zaczyna się od głównego; rzędy nad nim wychodzą i dochodzą pod nim
            val keep = if (mainAt > 0) 0 else Workbench.keptPrefix(extra.map { it.nbId }, want)
            for (e in extra.drop(keep)) remove(e)
            want.drop(keep).forEach { add(it) }
            applyMode(s); restore = present(s)
            if (s.profile.isNotEmpty()) pinProfile(s.profile)
            view?.post { view?.restorePending() }
        } else {
            // 3c-B3: dołożone rzędy zwalniamy od razu — nowe stanowisko może je zawierać, a do zamknięcia tego
            // edytora wciąż byłyby „otwarte w innym oknie”.
            closeAll()
            main.app.pendingStation = s
            openNotebook(first)
        }
    }
}
