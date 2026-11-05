package com.mdev.mnotes.core.color

import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Paper

/**
 * 2c-A9: ton strony i rodzaj obrazu dla trybu ciemnego, z małego podglądu (piksele ARGB).
 *
 * Strony PDF były zawsze „jasne”, więc slajdy z ciemnym tłem w trybie ciemnym odwracały się na jasne. Teraz przy
 * imporcie mediana jasności strony wyznacza jej papier. Obrazy: rysunki i zrzuty (przezroczystość albo mało kolorów)
 * zmieniają się jak atrament — czarna kreska na przezroczystym tle nie znika na ciemnej stronie; zdjęcia zostają.
 */
object PageTone {
    /** Piksel o medianie jasności (krycie pomijane: przezroczyste liczą się jak białe tło strony). */
    fun median(px: IntArray): Int {
        if (px.isEmpty()) return WHITE
        val opaque = IntArray(px.size) { i -> overWhite(px[i]) }
        val l = FloatArray(opaque.size) { i -> Paper.luminance(opaque[i]) }
        val idx = opaque.indices.sortedBy { l[it] }
        return opaque[idx[idx.size / 2]]
    }

    /** Papier strony PDF z podglądu [px]: kolor mediany, gdy strona jest ciemna; null — zwykła jasna strona. */
    fun pdfPaper(px: IntArray): Int? = median(px).takeIf { Paper.isDark(it) }

    /**
     * Czy obraz to rysunek albo zrzut (true), a nie zdjęcie. Rysunek: co najmniej [ALPHA_SHARE] pikseli wyraźnie
     * przezroczystych albo [COVER] pikseli mieści się w najwyżej [FEW_COLORS] kolorach (po 4 bity na kanał).
     */
    fun isDrawing(px: IntArray): Boolean {
        if (px.isEmpty()) return false
        val clear = px.count { (it ushr 24) < 200 }
        if (clear >= ALPHA_SHARE * px.size) return true
        val bins = HashMap<Int, Int>()
        for (c in px) { val k = (c shr 20 and 0xF shl 8) or (c shr 12 and 0xF shl 4) or (c shr 4 and 0xF); bins[k] = (bins[k] ?: 0) + 1 }
        val counts = bins.values.sortedDescending()
        var sum = 0
        for ((i, n) in counts.withIndex()) {
            sum += n
            if (sum >= COVER * px.size) return i + 1 <= FEW_COLORS
        }
        return false
    }

    /**
     * Ręczna korekta tonu strony (stare zeszyty i PDF-y, które pomiar przy imporcie uznał za jasne, choć są ciemne, albo
     * odwrotnie). Ton strony to jej papier ([PageSpec.darkPaper]), więc korekta zmienia papier: na ciemny — [measured]
     * (mediana podglądu PDF), gdy jest ciemna, inaczej [Paper.DARK]; na jasny — biały. Linie wzoru idą za papierem.
     * Strona już w tym tonie zostaje bez zmian (nie gubi wybranego koloru papieru).
     */
    fun withTone(spec: PageSpec, dark: Boolean, measured: Int? = null): PageSpec {
        if (spec.darkPaper == dark) return spec
        val paper = if (dark) measured?.takeIf { Paper.isDark(it) } ?: Paper.DARK else Paper.WHITE
        return spec.copy(paper = paper, line = Paper.autoLineColor(paper))
    }

    private const val WHITE = 0xFFFFFFFF.toInt()
    const val ALPHA_SHARE = 0.05f
    const val COVER = 0.95f
    const val FEW_COLORS = 24

    private fun overWhite(c: Int): Int {
        val a = (c ushr 24) / 255f
        fun ch(sh: Int) = ((c shr sh and 255) * a + 255 * (1 - a)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
