package com.mdev.mnotes.render

import android.graphics.Paint
import android.text.TextPaint
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.samsung.SdFlowMeasure
import com.mdev.mnotes.core.text.TextMeasure

/**
 * Pomiar tekstu płynącego Samsunga czcionką i zawijaniem urządzenia, takimi samymi jak przy rysowaniu pola tekstowego
 * ([PageRenderer.layoutFor]), więc wysokość pola i liczba wierszy zgadzają się z tym, co potem zobaczy edytor.
 */
class SdAndroidMeasure(private val renderer: PageRenderer = PageRenderer(null)) : SdFlowMeasure, TextMeasure by renderer.textMeasure {
    private fun metrics(sizePt: Float): Paint.FontMetricsInt {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = sizePt
        p.typeface = renderer.typeface(TextBox.FAMILY_SANS, 0)
        return p.fontMetricsInt
    }

    // te same, całkowite wartości, których używa StaticLayout przy wysokości wiersza
    override fun ascent(sizePt: Float): Float = -metrics(sizePt).ascent.toFloat()

    override fun descent(sizePt: Float): Float = metrics(sizePt).descent.toFloat()

    override fun breakLines(text: String, sizePt: Float, widthPt: Float): IntArray {
        if (text.isEmpty()) return intArrayOf(0)
        val l = renderer.layoutFor(TextBox(0, 0f, 0f, widthPt, 0f, 0f, text, sizePt, 0), widthPt)
        return IntArray(l.lineCount) { l.getLineEnd(it) }
    }
}
