package com.mdev.mnotes.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.core.edit.AngleSnap
import com.mdev.mnotes.core.layout.BarConfig
import com.mdev.mnotes.core.layout.ViewMode
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.pen.ActivePen
import com.mdev.mnotes.core.pen.PenKit
import org.json.JSONArray
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Ustawienia aplikacji. Klucze płaskie `przestrzeń.nazwa` w SharedPreferences (zgodnie z README specyfikacji),
 * każde ustawienie jest stanem Compose, więc UI odświeża się samo.
 */
class Settings(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("mnotes", Context.MODE_PRIVATE)

    private inner class Pref<T>(val key: String, val def: T) : ReadWriteProperty<Any?, T> {
        private var state by mutableStateOf(read())

        @Suppress("UNCHECKED_CAST")
        private fun read(): T = when (def) {
            is Int -> sp.getInt(key, def) as T
            is Boolean -> sp.getBoolean(key, def) as T
            is Float -> sp.getFloat(key, def) as T
            is String -> (sp.getString(key, def) ?: def) as T
            else -> def
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state = value
            val e = sp.edit()
            when (value) {
                is Int -> e.putInt(key, value)
                is Boolean -> e.putBoolean(key, value)
                is Float -> e.putFloat(key, value)
                is String -> e.putString(key, value)
            }
            e.apply()
        }
    }

    // — Wygląd (LNX/CUS) —
    var theme by Pref("ui.theme", 0)                 // 0 system, 1 jasny, 2 ciemny, 3 AMOLED
    var accent by Pref("ui.accent", 0)               // indeks w Accents
    var compact by Pref("ui.compact", false)
    var haptics by Pref("ui.haptics", true)          // delikatne wibracje przy dotknięciu przycisków paska narzędzi
    var tooltips by Pref("ui.tooltips", true)        // podpowiedź z nazwą przycisku po najechaniu (mysz, rysik) albo przytrzymaniu
    var pageAnim by Pref("anim.pages", true)          // strony dojeżdżają na nowe miejsce po zmianie kolejności (F6)
    var pageAnimMs by Pref("anim.pages_ms", com.mdev.mnotes.core.layout.PageShift.DEFAULT_MS)  // czas tej animacji (ms), A2-7: 180 zamiast 260
    var toolHints by Pref("ui.tool_hints", true)     // dymek z opisem po wybraniu narzędzia (taśma, pędzel formatu, wiaderko, odstęp)
    var toolbarPos by Pref("toolbar.position", 0)    // 0 góra, 1 dół, 2 lewo, 3 prawo
    var toolbarSize by Pref("toolbar.size", 1)       // 0 mały, 1 normalny, 2 duży
    var optionsPos by Pref("toolbar.options", 0)     // listwa grubości i kolorów: 0 z lewej, 1 z prawej, 2 przy pasku narzędzi
    private var barOrder by Pref("toolbar.order", "")   // własna kolejność pozycji paska: id po przecinku
    private var barModes by Pref("toolbar.modes", "")   // tryby pozycji: „id:1” zawsze na pasku, „id:2” tylko pod „⋯”

    /** Układ paska narzędzi wybrany przez użytkownika (kolejność i tryby pozycji). */
    var barConfig: BarConfig
        get() = BarConfig.parse(barOrder, barModes)
        set(v) { barOrder = if (v.isDefault) "" else v.orderString(); barModes = v.modesString() }
    var undoDock by Pref("ui.undo_dock", 0)         // gdzie stoją Cofnij i Ponów: 0 na pasku narzędzi, 1–4 kapsuła w rogu strony (lewy górny, prawy górny, lewy dolny, prawy dolny)
    var tabs by Pref("ui.tabs", false)               // otwieraj zeszyty jako karty (pasek kart pojawia się dopiero przy ≥ 2 kartach)
    var menuBar by Pref("ui.menubar", 1)            // pasek menu w edytorze: 0 nigdy, 1 na dużych ekranach, 2 zawsze
    var keyMap by Pref("keys.map", "")               // własne skróty: JSON {id polecenia: „Ctrl+J”}, pusty tekst = bez skrótu
    var unitsMm by Pref("units.mm", true)
    private var langPref by Pref("ui.lang", 0)           // 0 system, 1 polski, 2 angielski

    /** Język interfejsu; zapis od razu przełącza [com.mdev.mnotes.editor.T]. */
    var lang: Int
        get() = langPref
        set(v) { langPref = v; com.mdev.mnotes.editor.T.mode = v }

    init { com.mdev.mnotes.editor.T.mode = langPref }

    // — Wejście —
    var fingerMode by Pref("input.finger", 0)        // 0 przewijaj, 1 rysuj, 2 ignoruj
    var palmRejection by Pref("input.palm_rejection", true)
    var penButton by Pref("input.pen_button", 0)     // 0 gumka, 1 lasso, 2 cofnij, 3 laser, 4 brak
    var doubleTap by Pref("input.double_tap", 0)     // podwójne dotknięcie palcem: 0 cofnij, 1 ponów, 2 przybliż/oddal, 3 brak
    var tripleTap by Pref("input.triple_tap", 1)     // potrójne dotknięcie palcem: te same wartości co przy podwójnym
    var holdToShape by Pref("input.hold_to_shape", true)
    var holdMs by Pref("input.hold_ms", 550)
    var holdToCurve by Pref("input.hold_curve", false)   // F18: nierozpoznana kreska trzymana dalej staje się gładką krzywą; domyślnie wył. (A2-3: pismo)
    var holdCurveHinted by Pref("input.hold_curve_hinted", false)  // dymek „wyłączysz to w Ustawieniach” pokazany już raz
    var curveHoldMs by Pref("input.hold_curve_ms", 350)  // ile dłużej trzymać (ms) po nieudanym rozpoznaniu kształtu
    var curveMode by Pref("curve.mode", 0)               // 2b-A10: tryb narzędzia Krzywa (core/shapes/CurveMode)
    var curveFill by Pref("curve.fill", false)           // 2b-A10: zamknięta krzywa dostaje wypełnienie w kolorze pisma
    var twoFingerUndo by Pref("input.two_finger_undo", true)
    var pullAddPage by Pref("input.pull_add_page", true)
    var autoTextPage by Pref("pages.auto_text", true)  // nowa strona, gdy tekst wyjdzie za ostatnią albo zacznie się za nią (F10)
    var renderQuality by Pref("render.quality", 0)   // jakość renderowania strony: 0 zrównoważona, 1 ostra, 2 oszczędna, 3 klasyczna (obraz) — patrz RenderProfile
    var smoothing by Pref("pen.smoothing", 1f)       // mnożnik wygładzania piór 0..2

    // — Narzędzia —
    var eraserMode by Pref("eraser.mode", 0)         // 0 obiektowa, 1 pikselowa, 2 lasso gumki, 3 wycinanie (F12)
    var painterMode by Pref("painter.mode", 0)       // pędzel formatu: 0 obiekty pod rysikiem, 1 lasso
    var painterQuick by Pref("painter.quick", true)  // pierwsze dotknięcie pędzla pokazuje pod nim szybki przycisk „Pobierz format”
    var painterQuickFadeUse by Pref("painter.quick_fade_use", true)    // przycisk gaśnie, gdy zaczynasz malować pędzlem
    var painterQuickFadeTake by Pref("painter.quick_fade_take", true)  // przycisk gaśnie po pobraniu formatu
    var painterQuickSec by Pref("painter.quick_sec", 6)                // przycisk gaśnie po tylu sekundach bez dotknięcia; 0 = nigdy
    var fillCloseGaps by Pref("fill.close_gaps", false) // wiaderko domyka szpary węższe niż ok. 2 pt
    var pipetteKeep by Pref("pipette.keep", false)   // pobrany pipetą kolor zostaje w zestawie, gdy listwa nie jest pełna; domyślnie znika po zmianie koloru
    /** Pipeta na zakreślaczu: false = kolor widoczny na stronie (po multiply), true = kolor pierwotny kreski. */
    var pipetteOriginal by Pref("pipette.original", false)
    var eraserSize by Pref("eraser.size", 14f)       // promień w pt
    var eraserKeepHighlighter by Pref("eraser.ignore_highlighter", false)
    var lassoRect by Pref("lasso.rect", false)
    var lassoTouch by Pref("lasso.touch", false)     // lasso „tylko zahacz”: wystarczy musnąć obiekt pętlą; domyślnie zbalansowane (połowa obiektu w pętli)
    // 2b-A2 (zastępuje przełącznik F7 „toolbar.tool_strip”): HelperStrip.WRITE_AND_MENU albo TOOL_OPTIONS
    var helperStrip by Pref("toolbar.helper_strip", com.mdev.mnotes.core.layout.HelperStrip.WRITE_AND_MENU)
    private var helperMenuPref by Pref("toolbar.helper_menu", "")   // wyłączone pozycje menu (HelperStrip.Config)
    var helperMenu: com.mdev.mnotes.core.layout.HelperStrip.Config
        get() = com.mdev.mnotes.core.layout.HelperStrip.Config.parse(helperMenuPref)
        set(v) { helperMenuPref = v.encode() }
    var laserColor by Pref("laser.color", 0xFF3B30)    // RGB śladu lasera (LaserColors.PALETTE)
    var axesLast by Pref("axes.last", "")            // ostatnio wstawiony układ współrzędnych (AxesSpec.encode), F8
    var axesSnap by Pref("axes.snap", true)          // 2b-A6: układ na kratce i kreski w nim dociskane do siatki
    var axesSnapDp by Pref("axes.snap_dp", 3f)       // próg docisku kresek do linii siatki układu (dp ekranu; 0 = bez)
    var guideOffered by Pref("guide.offered", false) // zeszyt-przewodnik był już proponowany (F20)
    var attach by Pref("snap.attach", false)         // F22: końce linii i przeciągane punkty przyczepiają się do innych figur
    var zoomMaxFactor by Pref("zoom.max_factor", 6f)     // F25: największe powiększenie = mnożnik „100 %” (6 = dawne)
    var returnToPen by Pref("tools.return_to_pen", false) // F26: ponowne dotknięcie lasera/ręki/lassa albo puste miejsce wraca do pisania
    // F28: auto-korekta symboli po przytrzymaniu pióra (każdy osobno); w zeszycie Marcina wybrane pierwiastek i suma.
    var mathRoot by Pref("math.root", true)
    var mathSum by Pref("math.sum", true)
    var mathIntegral by Pref("math.integral", false)
    var mathHinted by Pref("math.hinted", false)          // dymek „gdzie to wyłączyć” po pierwszej zamianie (A2-4) już był
    var stations by Pref("station.saved", "")            // 2b-A7: zapisane stanowiska (Workbench.encode)
    // 2b-A9: tryb stron (ThemeMap.MIXED / LIGHT / DARK), odwracanie zdjęć, eksport PDF jak na ekranie (domyślnie oryginał)
    var pageTheme by Pref("pages.theme", com.mdev.mnotes.core.color.ThemeMap.MIXED)
    var pageThemePhotos by Pref("pages.theme_photos", false)
    var exportAsScreen by Pref("export.as_screen", false)
    var mathText by Pref("text.math_unicode", true)      // L2: \alpha, x^2, a_1 → α, x², a₁ w polach tekstu
    var constructNames by Pref("construct.names", true)  // L1 (A2-9): konstrukcje podpisują punkty A, B, C, O, H…
    var paletteRecent by Pref("palette.recent", "")      // pomysł Fable 2: ostatnio użyte polecenia palety (id po przecinku)
    var miniMap by Pref("view.minimap", false)           // pomysł Fable 3: mini-mapa strony przy dużym powiększeniu; domyślnie wył. (A2-2)
    var miniMapSize by Pref("view.minimap_size", com.mdev.mnotes.core.layout.MiniMap.DEFAULT_SIZE_DP)   // 2b-A3: szerokość pola w dp (80–200)
    var miniMapCorner by Pref("view.minimap_corner", com.mdev.mnotes.core.layout.MiniMap.BOTTOM_RIGHT)  // 2b-A3: narożnik widoku
    var imageGridFit by Pref("images.grid_fit", false)   // pomysł Fable 11: wstawione zdjęcie wyrównane do kratki
    var dockPages by Pref("ui.dockpages", false)    // panel stron zadokowany z lewej na szerokim oknie (LNX-016)
    var samsungMode by Pref("import.samsung_mode", 0) // ostatni wybór w oknie importu Samsung Notes: 0 wygląd oryginału, 1 standard mNotes
    var zoomMag by Pref("zoomwin.mag", 3f)          // powiększenie okna powiększenia (EXT-003): 2–5×
    var tapeColor by Pref("tape.color", 0xFFFFD54F.toInt())
    var snapGrid by Pref("ruler.snap", false)
    var rotSnap by Pref("rotate.snap", true)         // obrót lassem przyciąga do równych kątów
    private var rotAnglesRaw by Pref("rotate.angles", AngleSnap.format(AngleSnap.every(AngleSnap.DEFAULT_STEP)))   // kąty przyciągania: „0;15;30…” w stopniach
    var rotTolerance by Pref("rotate.tolerance", AngleSnap.DEFAULT_TOLERANCE)   // zasięg przyciągania w stopniach
    private var rotAnglesCache: Pair<String, List<Float>>? = null
    /** Kąty przyciągania obrotu; zapis normalizuje zbiór (zakres, kolejność, bez powtórzeń). */
    var rotAngles: List<Float>
        get() {
            val raw = rotAnglesRaw
            rotAnglesCache?.let { if (it.first == raw) return it.second }
            return AngleSnap.parse(raw).also { rotAnglesCache = raw to it }
        }
        set(v) { rotAnglesRaw = AngleSnap.format(v) }

    // — Strony —
    var newFormat by Pref("new.format", "a4")
    var newLandscape by Pref("new.landscape", false)
    var newTemplate by Pref("new.template", 1)
    var newPaper by Pref("new.paper", 0xFFFFFFFF.toInt())
    /** Układ stron dla dokumentów bez własnego wyboru ([com.mdev.mnotes.core.layout.ViewMode]): osobno zeszyty (też .mnotes i Samsung Notes) i zaimportowane PDF-y. */
    var viewNotebook by Pref("view.notebook", ViewMode.DEFAULT_NOTEBOOK)
    var viewPdf by Pref("view.pdf", ViewMode.DEFAULT_PDF)
    var textSize by Pref("text.size", 20f)
    var textColor by Pref("text.color", 0xFF202124.toInt())
    var textFamily by Pref("text.family", 0)

    // — Biblioteka —
    var libView by Pref("lib.view", 0)               // 0 ikony, 1 lista, 2 kompaktowy
    var libIcon by Pref("lib.icon", 168f)            // dp
    var libSort by Pref("lib.sort", 1)               // 0 nazwa, 1 zmiana, 2 utworzenie
    var libSortDesc by Pref("lib.sort_desc", true)
    var libPreview by Pref("lib.preview", false)     // podgląd pierwszej strony zamiast okładki

    // — Kopie i bezpieczeństwo —
    var syncUri by Pref("sync.uri", "")
    var syncAuto by Pref("sync.auto", false)
    var appLock by Pref("lock.app", false)
    var secureFlag by Pref("lock.secure_flag", false)
    var syncPdf by Pref("sync.pdf", false)           // obok .mnotes kopiuj też PDF
    var syncMd by Pref("sync.md", false)             // …i Markdown
    var lastSync by Pref("sync.last_min", 0)         // minuty od epoki (Int wystarczy do roku 6000+)

    private var kitJson by Pref("pen.kit", "")

    // Dawne „zapisane pióra”: tylko do odczytu, żeby przy pierwszym uruchomieniu przenieść je do kompletu piór.
    private val oldPresets by Pref("pen.presets", "")
    private val oldActive by Pref("pen.active", 0)
    private var kitCache: Pair<String, PenKit>? = null

    /** Komplet piór: zestawy kolorów oraz kolor, trzy grubości i styl każdego narzędzia piszącego. */
    var penKit: PenKit
        get() {
            val j = kitJson
            kitCache?.let { if (it.first == j) return it.second }
            val k = (if (j.isEmpty()) null else PenKit.fromJson(j)) ?: PenKit.migrate(parseOldPresets(oldPresets), oldActive)
            kitCache = j to k
            return k
        }
        set(v) {
            val j = v.toJson()
            kitCache = j to v
            kitJson = j
        }

    private fun parseOldPresets(s: String): List<ActivePen> {
        if (s.isEmpty()) return emptyList()
        return try {
            val a = JSONArray(s)
            List(a.length()) { i -> a.getJSONObject(i).let { ActivePen(PenKind.fromCode(it.getInt("k")), it.getInt("c"), it.getDouble("w").toFloat()) } }
        } catch (_: Exception) { emptyList() }
    }

    /** Klucze związane z urządzeniem (uprawnienia do folderu, blokada) nie wędrują w pliku ustawień. */
    private fun deviceBound(k: String) = k.startsWith("sync.") || k.startsWith("lock.")

    /** Eksport ustawień do JSON (dotfile — CUS-004). */
    fun exportJson(): String {
        val o = JSONObject()
        for ((k, v) in sp.all) if (!deviceBound(k)) when (v) {
            is Int, is Boolean, is String, is Float, is Long -> o.put(k, v)
        }
        return o.toString(2)
    }

    fun importJson(json: String) {
        val o = JSONObject(json)
        val e = sp.edit()
        for (k in o.keys()) if (!deviceBound(k)) when (val v = o.get(k)) {
            is Int -> e.putInt(k, v)
            is Boolean -> e.putBoolean(k, v)
            is String -> e.putString(k, v)
            is Double -> e.putFloat(k, v.toFloat())
        }
        e.apply()
    }
}
