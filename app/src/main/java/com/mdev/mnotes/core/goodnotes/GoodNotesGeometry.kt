package com.mdev.mnotes.core.goodnotes

import com.mdev.mnotes.core.samsung.Level
import com.mdev.mnotes.core.samsung.ReportItem
import kotlin.math.max
import kotlin.math.min

/**
 * Przejście z przestrzeni PDF-a (oś y w górę, początek w rogu CropBox) na przestrzeń strony mNotes (oś y w dół)
 * z uwzględnieniem /Rotate. Dla obrotu 90 i 270 wymiary strony zamieniają się miejscami.
 */
internal class PageMap(box: DoubleArray, val rotate: Int) {
    val x0 = box[0]
    val y0 = box[1]
    val x1 = box[2]
    val y1 = box[3]
    private val turned = rotate == 90 || rotate == 270
    val w: Double = if (turned) y1 - y0 else x1 - x0
    val h: Double = if (turned) x1 - x0 else y1 - y0

    fun x(px: Double, py: Double): Double = when (rotate) {
        90 -> py - y0
        180 -> x1 - px
        270 -> y1 - py
        else -> px - x0
    }

    fun y(px: Double, py: Double): Double = when (rotate) {
        90 -> px - x0
        180 -> py - y0
        270 -> x1 - px
        else -> y1 - py
    }

    /** Obwiednia (minX, minY, maxX, maxY) z przestrzeni PDF-a przeniesiona na stronę. */
    fun box(b: DoubleArray): DoubleArray {
        val xs = doubleArrayOf(x(b[0], b[1]), x(b[2], b[1]), x(b[0], b[3]), x(b[2], b[3]))
        val ys = doubleArrayOf(y(b[0], b[1]), y(b[2], b[1]), y(b[0], b[3]), y(b[2], b[3]))
        return doubleArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }
}

/** Raport odstępstw: wpisy z kluczem łączą się w jeden z listą stron, więc ponowna konwersja strony niczego nie dubluje. */
internal class Report {
    class Entry(val level: Level, val pl: String, val en: String, val pages: MutableSet<Int> = sortedSetOf())

    val entries = ArrayList<Entry>()
    private val byKey = HashMap<String, Entry>()

    fun note(level: Level, pl: String, en: String, page: Int? = null) {
        entries.add(Entry(level, pl, en).also { e -> page?.let { e.pages.add(it) } })
    }

    fun once(key: String, level: Level, pl: String, en: String, page: Int?) {
        val e = byKey.getOrPut(key) { Entry(level, pl, en).also { entries.add(it) } }
        if (page != null) e.pages.add(page)
    }

    fun items(): List<ReportItem> = entries.map { ReportItem(it.level, it.pl, it.en, it.pages.toList()) }
}

internal fun clampSize(v: Double, lo: Float, hi: Float): Float = max(lo, min(hi, v.toFloat()))
