package com.mdev.mnotes.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.core.model.PenKind
import org.json.JSONArray
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Gotowe pióro w pasku (preset): rodzaj, kolor ARGB, grubość w pt. */
data class PenPreset(val kind: PenKind, val color: Int, val width: Float)

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
    var toolbarPos by Pref("toolbar.position", 0)    // 0 góra, 1 dół, 2 lewo, 3 prawo
    var toolbarSize by Pref("toolbar.size", 1)       // 0 mały, 1 normalny, 2 duży
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
    var holdToShape by Pref("input.hold_to_shape", true)
    var holdMs by Pref("input.hold_ms", 550)
    var twoFingerUndo by Pref("input.two_finger_undo", true)
    var pullAddPage by Pref("input.pull_add_page", true)
    var renderQuality by Pref("render.quality", 0)   // jakość renderowania strony: 0 zrównoważona, 1 ostra, 2 oszczędna, 3 klasyczna (obraz) — patrz RenderProfile
    var smoothing by Pref("pen.smoothing", 1f)       // mnożnik wygładzania piór 0..2

    // — Narzędzia —
    var eraserMode by Pref("eraser.mode", 0)         // 0 obiektowa, 1 pikselowa
    var eraserSize by Pref("eraser.size", 14f)       // promień w pt
    var eraserKeepHighlighter by Pref("eraser.ignore_highlighter", false)
    var lassoRect by Pref("lasso.rect", false)
    var dockPages by Pref("ui.dockpages", false)    // panel stron zadokowany z lewej na szerokim oknie (LNX-016)
    var zoomMag by Pref("zoomwin.mag", 3f)          // powiększenie okna powiększenia (EXT-003): 2–5×
    var tapeColor by Pref("tape.color", 0xFFFFD54F.toInt())
    var snapGrid by Pref("ruler.snap", false)

    // — Strony —
    var newFormat by Pref("new.format", "a4")
    var newLandscape by Pref("new.landscape", false)
    var newTemplate by Pref("new.template", 1)
    var newPaper by Pref("new.paper", 0xFFFFFFFF.toInt())
    var layoutMode by Pref("view.layout", 0)
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

    private var presetsJson by Pref("pen.presets", "")

    var presets: List<PenPreset>
        get() = parsePresets(presetsJson)
        set(v) {
            val a = JSONArray()
            for (p in v) a.put(JSONObject().put("k", p.kind.code).put("c", p.color).put("w", p.width.toDouble()))
            presetsJson = a.toString()
        }

    /** Ostatnio używany preset (indeks) — po restarcie wracamy do tego samego pióra. */
    var activePreset by Pref("pen.active", 0)

    private fun parsePresets(s: String): List<PenPreset> {
        if (s.isEmpty()) return DEFAULT_PRESETS
        return try {
            val a = JSONArray(s)
            List(a.length()) { i -> a.getJSONObject(i).let { PenPreset(PenKind.fromCode(it.getInt("k")), it.getInt("c"), it.getDouble("w").toFloat()) } }
                .ifEmpty { DEFAULT_PRESETS }
        } catch (_: Exception) { DEFAULT_PRESETS }
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

    companion object {
        val DEFAULT_PRESETS = listOf(
            PenPreset(PenKind.BALLPOINT, 0xFF1B1D21.toInt(), 1.42f),
            PenPreset(PenKind.FOUNTAIN, 0xFF24407E.toInt(), 2.83f),
            PenPreset(PenKind.BALLPOINT, 0xFFC8433F.toInt(), 1.42f),
            PenPreset(PenKind.PENCIL, 0xFF454A52.toInt(), 1.98f),
            PenPreset(PenKind.BRUSH, 0xFF3E8E5A.toInt(), 8.5f),
            PenPreset(PenKind.HIGHLIGHTER, 0xFFF5CF17.toInt(), 14.17f),
            PenPreset(PenKind.HIGHLIGHTER, 0xFF71DF77.toInt(), 14.17f),
        )
    }
}
