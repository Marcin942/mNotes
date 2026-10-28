package com.mdev.mnotes.ui

import android.view.KeyEvent.*
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import com.mdev.mnotes.data.Settings
import com.mdev.mnotes.editor.T
import org.json.JSONObject
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

/** Skrót klawiszowy: kod klawisza (po normalizacji klawiatury numerycznej) i modyfikatory. Tekst: „Ctrl+Shift+N”. */
class Chord(val code: Int, val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false) {
    fun isFunctionKey() = code in android.view.KeyEvent.KEYCODE_F1..android.view.KeyEvent.KEYCODE_F12

    fun label(): String = buildString {
        if (ctrl) append("Ctrl+"); if (alt) append("Alt+"); if (shift) append("Shift+")
        append(NAMES[code] ?: "?")
    }

    /** `=` bywa wpisywane jako `+` (z Shiftem) — wtedy Shift nie przeszkadza w dopasowaniu domyślnego Ctrl+=. */
    fun matches(e: ComposeKeyEvent): Boolean {
        if (norm(e.key.nativeKeyCode) != code || e.isCtrlPressed != ctrl || e.isAltPressed != alt) return false
        return e.isShiftPressed == shift || (code == KEYCODE_EQUALS && !shift)
    }

    override fun equals(other: Any?) = other is Chord && other.code == code && other.ctrl == ctrl && other.shift == shift && other.alt == alt
    override fun hashCode() = ((code * 2 + (if (ctrl) 1 else 0)) * 2 + (if (shift) 1 else 0)) * 2 + (if (alt) 1 else 0)

    companion object {
        private val NAMES: Map<Int, String> = buildMap {
            for (i in 0..25) put(KEYCODE_A + i, ('A' + i).toString())
            for (i in 0..9) put(KEYCODE_0 + i, i.toString())
            for (i in 1..12) put(KEYCODE_F1 + i - 1, "F$i")
            put(KEYCODE_PAGE_UP, "PgUp"); put(KEYCODE_PAGE_DOWN, "PgDn"); put(KEYCODE_MOVE_HOME, "Home"); put(KEYCODE_MOVE_END, "End")
            put(KEYCODE_FORWARD_DEL, "Del"); put(KEYCODE_DEL, "Backspace"); put(KEYCODE_ENTER, "Enter"); put(KEYCODE_TAB, "Tab")
            put(KEYCODE_SPACE, "Space"); put(KEYCODE_INSERT, "Insert")
            put(KEYCODE_DPAD_LEFT, "←"); put(KEYCODE_DPAD_RIGHT, "→"); put(KEYCODE_DPAD_UP, "↑"); put(KEYCODE_DPAD_DOWN, "↓")
            put(KEYCODE_LEFT_BRACKET, "["); put(KEYCODE_RIGHT_BRACKET, "]"); put(KEYCODE_EQUALS, "="); put(KEYCODE_MINUS, "-")
            put(KEYCODE_COMMA, ","); put(KEYCODE_PERIOD, "."); put(KEYCODE_SLASH, "/"); put(KEYCODE_BACKSLASH, "\\")
            put(KEYCODE_SEMICOLON, ";"); put(KEYCODE_APOSTROPHE, "'"); put(KEYCODE_GRAVE, "`")
        }
        private val CODES: Map<String, Int> = NAMES.entries.associate { it.value.lowercase() to it.key }

        /** `+` i klawisze numeryczne traktujemy jak ich odpowiedniki z rzędu głównego. */
        fun norm(c: Int): Int = when (c) {
            KEYCODE_PLUS, KEYCODE_NUMPAD_ADD -> KEYCODE_EQUALS
            KEYCODE_NUMPAD_SUBTRACT -> KEYCODE_MINUS
            KEYCODE_NUMPAD_ENTER -> KEYCODE_ENTER
            KEYCODE_NUMPAD_DOT -> KEYCODE_PERIOD
            KEYCODE_NUMPAD_DIVIDE -> KEYCODE_SLASH
            in KEYCODE_NUMPAD_0..KEYCODE_NUMPAD_9 -> KEYCODE_0 + (c - KEYCODE_NUMPAD_0)
            else -> c
        }

        fun parse(s: String): Chord? {
            if (s.isBlank()) return null
            var ctrl = false; var shift = false; var alt = false
            val parts = s.trim().split('+')
            for (p in parts.dropLast(1)) when (p.trim().lowercase()) { "ctrl" -> ctrl = true; "shift" -> shift = true; "alt" -> alt = true; else -> return null }
            val code = CODES[parts.last().trim().lowercase()]
            return code?.let { Chord(it, ctrl, shift, alt) }
        }

        /** Skrót z naciśnięcia; samo naciśnięcie modyfikatora albo nieznany klawisz → null. */
        fun of(e: ComposeKeyEvent): Chord? {
            val c = norm(e.key.nativeKeyCode)
            return if (NAMES.containsKey(c)) Chord(c, e.isCtrlPressed, e.isShiftPressed, e.isAltPressed) else null
        }
    }
}

/**
 * Definicja polecenia edytora: stały identyfikator (klucz w ustawieniach), grupa menu, nazwa i domyślny skrót.
 * `textEdit` — polecenie, które w polu tekstowym ma zostać przy systemowym zachowaniu (Ctrl+Z, Ctrl+C…).
 */
class CmdDef(val id: String, val group: Int, private val pl: String, private val en: String, val key: String = "", val textEdit: Boolean = false) {
    val title get() = T.t(pl, en)
}

object CmdDefs {
    /** Kolejność = kolejność pozycji w menu i w palecie poleceń. */
    val groups: List<Pair<String, String>> = listOf(
        "Plik" to "File", "Edycja" to "Edit", "Widok" to "View", "Wstaw" to "Insert", "Strona" to "Page", "Narzędzia" to "Tools", "Pomoc" to "Help",
    )
    fun groupName(i: Int) = groups[i].let { T.t(it.first, it.second) }

    val list: List<CmdDef> = listOf(
        CmdDef("export", 0, "Eksport i udostępnianie…", "Export & share…", "Ctrl+E"),
        CmdDef("settings", 0, "Ustawienia aplikacji", "App settings", "Ctrl+,"),
        CmdDef("newWindow", 0, "Nowe okno (biblioteka)", "New window (library)", "Ctrl+Alt+N"),
        CmdDef("zoomWindow", 2, "Okno powiększenia", "Zoom window", "Ctrl+Alt+Z"),
        CmdDef("pagesDock", 2, "Zadokuj panel stron (szerokie okno)", "Dock page panel (wide window)", "Ctrl+F9"),
        CmdDef("close", 0, "Zamknij zeszyt (kartę)", "Close notebook (tab)", "Ctrl+W"),
        CmdDef("tabNext", 0, "Następna karta", "Next tab", "Ctrl+Tab"),
        CmdDef("tabPrev", 0, "Poprzednia karta", "Previous tab", "Ctrl+Shift+Tab"),

        CmdDef("undo", 1, "Cofnij", "Undo", "Ctrl+Z", true),
        CmdDef("redo", 1, "Ponów", "Redo", "Ctrl+Y", true),
        CmdDef("cut", 1, "Wytnij", "Cut", "Ctrl+X", true),
        CmdDef("copy", 1, "Kopiuj", "Copy", "Ctrl+C", true),
        CmdDef("paste", 1, "Wklej", "Paste", "Ctrl+V", true),
        CmdDef("duplicate", 1, "Duplikuj zaznaczenie", "Duplicate selection", "Ctrl+D", true),
        CmdDef("delete", 1, "Usuń zaznaczenie", "Delete selection", "Del"),
        CmdDef("selectAll", 1, "Zaznacz wszystko na stronie", "Select all on page", "Ctrl+A", true),
        CmdDef("find", 1, "Szukaj w zeszycie…", "Search in notebook…", "Ctrl+F"),
        CmdDef("findNext", 1, "Następne trafienie", "Next match", "F3"),
        CmdDef("findPrev", 1, "Poprzednie trafienie", "Previous match", "Shift+F3"),

        CmdDef("zoomIn", 2, "Powiększ", "Zoom in", "Ctrl+="),
        CmdDef("zoomOut", 2, "Pomniejsz", "Zoom out", "Ctrl+-"),
        CmdDef("fitWidth", 2, "Dopasuj do szerokości", "Fit width", "Ctrl+0"),
        CmdDef("layoutV", 2, "Układ: pionowo", "Layout: vertical"),
        CmdDef("layoutH", 2, "Układ: poziomo", "Layout: horizontal"),
        CmdDef("layoutS", 2, "Układ: pojedyncza strona", "Layout: single page"),
        CmdDef("pagesPanel", 2, "Panel stron", "Pages panel", "F9"),
        CmdDef("focusMode", 2, "Tryb skupienia (ukryj paski)", "Focus mode (hide bars)", "F11"),
        CmdDef("readOnly", 2, "Tryb tylko do odczytu", "Read-only mode"),
        CmdDef("tbTop", 2, "Pasek narzędzi: góra", "Toolbar: top"),
        CmdDef("tbBottom", 2, "Pasek narzędzi: dół", "Toolbar: bottom"),
        CmdDef("tbLeft", 2, "Pasek narzędzi: lewo", "Toolbar: left"),
        CmdDef("tbRight", 2, "Pasek narzędzi: prawo", "Toolbar: right"),

        CmdDef("insertImage", 3, "Wstaw obraz…", "Insert image…", "Ctrl+I"),
        CmdDef("pageBackground", 3, "Obraz jako tło strony…", "Image as page background…", ""),
        CmdDef("insertAxes", 3, "Układ współrzędnych…", "Coordinate axes…", ""),
        CmdDef("arrangeImages", 1, "Uporządkuj zaznaczone obrazy", "Arrange selected images", ""),
        CmdDef("dualOpen", 2, "Dołóż zeszyt poniżej (stanowisko)…", "Add notebook below (workspace)…", ""),
        CmdDef("stations", 2, "Stanowiska…", "Workspaces…", ""),
        CmdDef("profileNext", 2, "Przełącz profil pracy (kolejny / bez profilu)", "Switch work profile (next / none)", ""),
        CmdDef("lecture", 3, "Tryb wykładu: zdjęcie na nową stronę", "Lecture mode: photo on a new page", ""),
        CmdDef("boardPhoto", 3, "Zdjęcie tablicy (aparat → nowa strona)", "Board photo (camera → new page)", ""),
        CmdDef("stickers", 3, "Naklejki…", "Stickers…", ""),
        CmdDef("stickerSave", 3, "Zapisz zaznaczenie jako naklejkę…", "Save selection as sticker…", ""),
        CmdDef("snipImage", 3, "Wycinek zaznaczenia jako obraz", "Snip selection as image", ""),
        CmdDef("snipCopy", 3, "Kopiuj wycinek zaznaczenia do schowka", "Copy selection snip to clipboard", ""),
        CmdDef("addPage", 3, "Dodaj stronę", "Add page", "Ctrl+Shift+N"),
        CmdDef("bookmark", 3, "Przełącz zakładkę strony", "Toggle page bookmark", "Ctrl+B"),
        CmdDef("pageTags", 3, "Tagi tej strony… (#definicja, #zadanie)", "Tags of this page… (#definition, #exercise)", ""),
        CmdDef("notebookStats", 3, "Statystyki zeszytu… (strony, słowa, czas pisania)", "Notebook statistics… (pages, words, writing time)", ""),
        CmdDef("copyPage", 3, "Kopiuj stronę", "Copy page", "Ctrl+Shift+C"),
        CmdDef("copyPageImage", 3, "Kopiuj stronę jako obraz (do schowka)", "Copy page as image (to clipboard)", ""),
        CmdDef("cutPage", 3, "Wytnij stronę", "Cut page"),
        CmdDef("pastePage", 3, "Wklej stronę po bieżącej", "Paste page after current", "Ctrl+Shift+V"),

        CmdDef("goTo", 4, "Idź do strony…", "Go to page…", "Ctrl+G"),
        CmdDef("pageNext", 4, "Następna strona", "Next page", "PgDn"),
        CmdDef("pagePrev", 4, "Poprzednia strona", "Previous page", "PgUp"),
        CmdDef("pageFirst", 4, "Pierwsza strona", "First page", "Home"),
        CmdDef("pageLast", 4, "Ostatnia strona", "Last page", "End"),
        CmdDef("pageStyle", 4, "Styl strony…", "Page style…"),
        CmdDef("pageDup", 4, "Duplikuj stronę", "Duplicate page"),
        CmdDef("pageDelete", 4, "Usuń stronę", "Delete page"),
        CmdDef("pageClear", 4, "Wyczyść stronę", "Clear page"),

        CmdDef("toolPen", 5, "Narzędzie: pióro", "Tool: pen", "P"),
        CmdDef("toolEraser", 5, "Narzędzie: gumka", "Tool: eraser", "E"),
        CmdDef("toolPainter", 5, "Narzędzie: pędzel formatu", "Tool: format painter", "B"),
        CmdDef("toolFill", 5, "Narzędzie: wypełnienie (wiaderko)", "Tool: fill (paint bucket)", "F"),
        CmdDef("toolLasso", 5, "Narzędzie: lasso", "Tool: lasso", "L"),
        CmdDef("toolText", 5, "Narzędzie: tekst", "Tool: text", "T"),
        CmdDef("toolLaser", 5, "Narzędzie: laser", "Tool: laser", "O"),
        CmdDef("toolHand", 5, "Narzędzie: ręka", "Tool: hand", "H"),
        CmdDef("toolCurve", 5, "Narzędzie: krzywa Béziera", "Tool: Bézier curve", ""),
        CmdDef("attach", 5, "Przyczep do figur", "Attach to shapes", ""),
        CmdDef("toolSpace", 5, "Wstaw / usuń odstęp", "Insert / remove space", "X"),
        CmdDef("ruler", 5, "Linijka", "Ruler", "R"),
        CmdDef("protractor", 5, "Kątomierz", "Protractor", ""),
        CmdDef("toolGeo", 5, "Narzędzie: geometria (konstrukcje)", "Tool: geometry (constructions)", ""),
        CmdDef("toolTape", 5, "Taśma (zakryj fragment)", "Tape (cover an area)", "K"),
        CmdDef("tapesToggle", 5, "Zakryj / odkryj wszystkie taśmy", "Cover / reveal all tapes", ""),
        CmdDef("pipette", 5, "Pipeta (próbnik koloru)", "Eyedropper", "I"),
        CmdDef("presetNext", 5, "Następny kolor pióra", "Next pen colour", "]"),
        CmdDef("presetPrev", 5, "Poprzedni kolor pióra", "Previous pen colour", "["),
        CmdDef("penSettings", 5, "Ustawienia pióra", "Pen settings"),
        CmdDef("eraserSettings", 5, "Ustawienia gumki", "Eraser settings"),
        CmdDef("eraserObject", 5, "Gumka: obiektowa", "Eraser: object"),
        CmdDef("eraserPixel", 5, "Gumka: pikselowa", "Eraser: pixel"),

        CmdDef("palette", 6, "Paleta poleceń…", "Command palette…", "Ctrl+K"),
        CmdDef("shortcuts", 6, "Skróty klawiszowe…", "Keyboard shortcuts…"),
    )
    val byId: Map<String, CmdDef> = list.associateBy { it.id }
}

/** Własne skróty użytkownika: w ustawieniach (`keys.map`) trzymamy tylko różnice względem domyślnych; pusty tekst = brak skrótu. */
object Keymap {
    private fun parse(s: String): Map<String, String> = runCatching {
        val o = JSONObject(s); o.keys().asSequence().associateWith { o.optString(it) }
    }.getOrDefault(emptyMap())

    fun overrides(set: Settings): Map<String, String> = parse(set.keyMap)

    fun keyOf(set: Settings, d: CmdDef): String = overrides(set)[d.id] ?: d.key

    fun isCustom(set: Settings, d: CmdDef) = overrides(set).containsKey(d.id)

    private fun save(set: Settings, m: Map<String, String>) { set.keyMap = if (m.isEmpty()) "" else JSONObject(m).toString() }

    /** Przypisuje [chord] („” = bez skrótu); polecenie, które miało ten skrót, zostaje bez skrótu. */
    fun assign(set: Settings, id: String, chord: String) {
        val m = LinkedHashMap(overrides(set))
        if (chord.isNotEmpty()) owner(set, chord, id)?.let { m[it.id] = "" }
        val def = CmdDefs.byId[id]
        if (def != null && chord == def.key) m.remove(id) else m[id] = chord
        save(set, m)
    }

    fun reset(set: Settings, id: String) { save(set, overrides(set).toMutableMap().also { it.remove(id) }) }
    fun resetAll(set: Settings) { set.keyMap = "" }

    /** Które inne polecenie ma teraz ten skrót. */
    fun owner(set: Settings, chord: String, exceptId: String): CmdDef? {
        val want = Chord.parse(chord) ?: return null
        val m = overrides(set)
        return CmdDefs.list.firstOrNull { it.id != exceptId && Chord.parse(m[it.id] ?: it.key) == want }
    }
}
