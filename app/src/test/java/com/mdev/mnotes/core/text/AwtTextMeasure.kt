package com.mdev.mnotes.core.text

import com.mdev.mnotes.core.model.TextBox
import java.awt.Font
import java.awt.font.FontRenderContext

/**
 * Pomiar tekstu w testach prawdziwą metryką czcionki Java2D (bezszeryfowa, zwykle DejaVu Sans — szersza od Roboto, więc
 * test jest ostrzejszy niż urządzenie). Zawija jak StaticLayout: po słowach, a słowo dłuższe od wiersza — po znakach;
 * wiersz ma wysokość wznoszenia i zejścia czcionki (bez dopełnienia), razy mnożnik odstępu pola.
 */
object AwtTextMeasure : TextMeasure {
    init { System.setProperty("java.awt.headless", "true") }

    private val frc = FontRenderContext(null, true, true)

    fun font(t: TextBox): Font {
        val style = (if (t.style and TextBox.STYLE_BOLD != 0) Font.BOLD else 0) or (if (t.style and TextBox.STYLE_ITALIC != 0) Font.ITALIC else 0)
        return Font(Font.SANS_SERIF, style, 1).deriveFont(t.size)
    }

    private fun width(s: String, f: Font): Float = f.getStringBounds(s, frc).width.toFloat()

    fun ascent(t: TextBox): Float = font(t).getLineMetrics("Ąg", frc).ascent

    fun lineHeight(t: TextBox): Float {
        val lm = font(t).getLineMetrics("Ąg", frc)
        return (lm.ascent + lm.descent) * t.lineSpacing
    }

    /** Wiersze tekstu pola zawiniętego na szerokość [width]. */
    fun lines(t: TextBox, width: Float): List<String> {
        val f = font(t)
        val out = ArrayList<String>()
        for (para in t.text.split('\n')) {
            var cur = ""
            for (word in para.split(' ')) {
                val next = if (cur.isEmpty()) word else "$cur $word"
                if (cur.isNotEmpty() && width(next, f) > width) { out.add(cur); cur = word } else cur = next
                while (cur.length > 1 && width(cur, f) > width) {
                    var k = cur.length - 1
                    while (k > 1 && width(cur.substring(0, k), f) > width) k--
                    out.add(cur.substring(0, k)); cur = cur.substring(k)
                }
            }
            out.add(cur)
        }
        return out
    }

    override fun naturalWidth(t: TextBox): Float {
        val f = font(t)
        return t.text.split('\n').maxOf { width(it, f) }
    }

    override fun textHeight(t: TextBox, width: Float): Float = lines(t, width).size * lineHeight(t)
}
