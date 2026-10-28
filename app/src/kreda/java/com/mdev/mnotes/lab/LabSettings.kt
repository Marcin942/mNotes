package com.mdev.mnotes.lab

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.core.image.BoardOptions

/**
 * Ustawienia kredy w osobnym pliku preferencji (`mnotes.lab`, wspólnym z ustawieniami łącza), żeby wersja bez kredy
 * nie miała po nich śladu.
 * Każdy krok kredy da się wyłączyć (decyzja planu nocy: pełna customizacja); to są wartości domyślne panelu.
 */
class LabSettings(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("mnotes.lab", Context.MODE_PRIVATE)

    private inner class B(val key: String, def: Boolean) {
        var value by mutableStateOf(sp.getBoolean(key, def))
        fun set(v: Boolean) { value = v; sp.edit().putBoolean(key, v).apply() }
    }

    private inner class F(val key: String, def: Float) {
        var value by mutableStateOf(sp.getFloat(key, def))
        fun set(v: Float) { value = v; sp.edit().putFloat(key, v).apply() }
    }

    private inner class I(val key: String, def: Int) {
        var value by mutableStateOf(sp.getInt(key, def))
        fun set(v: Int) { value = v; sp.edit().putInt(key, v).apply() }
    }

    private val straightenP = B("board.straighten", true)
    private val cropP = B("board.crop", true)
    private val flattenP = B("board.flatten", true)
    private val sharpenP = B("board.sharpen", true)
    private val autoCutP = B("board.auto_cut", true)
    private val cutP = F("board.cut", 0.25f)
    private val solidP = F("board.solid", 0.85f)
    private val tintP = I("board.tint", 0)
    private val boardHintP = B("board.hint", true)
    private val faintP = B("board.faint", true)
    private val localCutP = B("board.localCut", false)
    private val framesP = B("board.frames", true)
    private val specksP = B("board.specks", true)
    private val curvesP = B("board.curves", true)
    private val lectureP = B("board.lecture", true)

    var straighten: Boolean get() = straightenP.value; set(v) = straightenP.set(v)
    var crop: Boolean get() = cropP.value; set(v) = cropP.set(v)
    var flatten: Boolean get() = flattenP.value; set(v) = flattenP.set(v)
    var sharpen: Boolean get() = sharpenP.value; set(v) = sharpenP.set(v)
    var faint: Boolean get() = faintP.value; set(v) = faintP.set(v)
    var localCut: Boolean get() = localCutP.value; set(v) = localCutP.set(v)
    var frames: Boolean get() = framesP.value; set(v) = framesP.set(v)
    var specks: Boolean get() = specksP.value; set(v) = specksP.set(v)

    /** Edytowalne kreski z kredy jako gładkie krzywe Béziera (2b-B4). */
    var curves: Boolean get() = curvesP.value; set(v) = curvesP.set(v)

    /** Zdjęcie z trybu wykładu od razu przez kredę (2b-B3); jedno cofnięcie wraca do zdjęcia. */
    var lecture: Boolean get() = lectureP.value; set(v) = lectureP.set(v)
    var autoCut: Boolean get() = autoCutP.value; set(v) = autoCutP.set(v)
    var cut: Float get() = cutP.value; set(v) = cutP.set(v)
    var solid: Float get() = solidP.value; set(v) = solidP.set(v)

    /** 0 kolory z oryginału, 1 biała kreda, 2 grafit (na jasną stronę zeszytu). */
    var tint: Int get() = tintP.value; set(v) = tintP.set(v)

    /** Podpowiedź „Wygląda jak tablica…” po wstawieniu zdjęcia ciemnej tablicy; gaśnie po pierwszym pokazaniu. */
    var boardHint: Boolean get() = boardHintP.value; set(v) = boardHintP.set(v)


    fun options(): BoardOptions = BoardOptions(
        straighten = straighten, crop = crop, flatten = flatten, sharpen = sharpen, autoCut = autoCut,
        cut = cut, solid = solid, tint = tintColor(tint), faint = faint, localCut = localCut, frames = frames, specks = specks,
        curves = curves,
    )

    companion object {
        private var instance: LabSettings? = null

        fun of(ctx: Context): LabSettings = instance ?: LabSettings(ctx.applicationContext).also { instance = it }

        fun tintColor(mode: Int): Int? = when (mode) {
            1 -> 0xFFF5F5F0.toInt()
            2 -> 0xFF37474F.toInt()
            else -> null
        }
    }
}
