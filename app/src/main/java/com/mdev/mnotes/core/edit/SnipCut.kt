package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.TextBox
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Narzędzie „Wycinek” (3c-B4): obszar strony zaznaczony lassem albo prostokątem → obraz w wysokiej rozdzielczości
 * (render strony, nie piksele ekranu), który trafia do zeszytu jako zwykły obraz. Lasso daje maskę (przezroczysto
 * poza kształtem, wygładzony brzeg), prostokąt — ostre krawędzie bez maski. Logika czysta — testowana jednostkowo.
 */
object SnipCut {
    /** Najmniejszy bok wycinka w pt (krótsze pociągnięcie to stuknięcie, nie wycinek). */
    const val MIN_PT = 6f
    /** Skala renderu (px/pt): co najmniej [MIN_SCALE], 2× bieżącego powiększenia, najwyżej [MAX_SCALE]. */
    const val MIN_SCALE = 3f
    const val MAX_SCALE = 6f
    /** Dłuższy bok bitmapy (px) — powyżej skala spada; maska lassa to bajt na piksel, więc też ogranicza pamięć. */
    const val MAX_PX = 3600
    /** Wstawiany wycinek zajmuje najwyżej tyle szerokości/wysokości strony docelowej. */
    const val FIT = 0.9f

    /** Prostokąt z dwóch narożników (dowolna kolejność) jako wielokąt zgodny z ruchem wskazówek. */
    fun rect(x0: Float, y0: Float, x1: Float, y1: Float): FloatArray {
        val l = min(x0, x1); val r = max(x0, x1); val t = min(y0, y1); val b = max(y0, y1)
        return floatArrayOf(l, t, r, t, r, b, l, b)
    }

    fun bounds(poly: FloatArray): Bounds {
        var l = Float.POSITIVE_INFINITY; var t = Float.POSITIVE_INFINITY
        var r = Float.NEGATIVE_INFINITY; var b = Float.NEGATIVE_INFINITY
        var i = 0
        while (i + 1 < poly.size) {
            l = min(l, poly[i]); r = max(r, poly[i]); t = min(t, poly[i + 1]); b = max(b, poly[i + 1]); i += 2
        }
        return if (l > r) Bounds.EMPTY else Bounds(l, t, r, b)
    }

    /** Plan renderu: obrys wielokąta przycięty do strony, skala z powiększenia [zoom]; null — za mały albo poza stroną. */
    fun plan(poly: FloatArray, pageW: Float, pageH: Float, zoom: Float): Snip.Plan? {
        if (poly.size < 6) return null
        val b = bounds(poly)
        if (b.isEmpty || b.w < MIN_PT || b.h < MIN_PT) return null
        val p = Snip.plan(b, pageW, pageH, margin = 0f, scale = (2f * zoom).coerceIn(MIN_SCALE, MAX_SCALE), maxPx = MAX_PX - 1) ?: return null   // −1: zaokrąglenie w górę nie przekroczy limitu
        return if (p.rect.w < MIN_PT || p.rect.h < MIN_PT) null else p
    }

    /** Wielokąt w pikselach bitmapy planu [plan] (punkt strony → piksel). */
    fun toPixels(poly: FloatArray, plan: Snip.Plan): FloatArray =
        FloatArray(poly.size) { i -> if (i % 2 == 0) (poly[i] - plan.rect.l) * plan.scale else (poly[i] - plan.rect.t) * plan.scale }

    /**
     * Krycie maski (0..255) dla każdego piksela [w]×[h], reguła parzystości (pętla przecinająca się sama ze sobą działa
     * jak w lassie). Brzeg wygładzony: 4 podwiersze na piksel, w poziomie dokładne pokrycie odcinka.
     */
    fun coverage(px: FloatArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h)
        val n = px.size / 2
        if (n < 3 || w <= 0 || h <= 0) return out
        val acc = FloatArray(w)
        val xs = FloatArray(n)
        val sub = 4
        for (y in 0 until h) {
            java.util.Arrays.fill(acc, 0f)
            for (s in 0 until sub) {
                val sy = y + (s + 0.5f) / sub
                var k = 0
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    val y0 = px[2 * i + 1]; val y1 = px[2 * j + 1]
                    if ((y0 <= sy) != (y1 <= sy)) {
                        val x0 = px[2 * i]; val x1 = px[2 * j]
                        xs[k++] = x0 + (sy - y0) / (y1 - y0) * (x1 - x0)
                    }
                }
                java.util.Arrays.sort(xs, 0, k)
                var q = 0
                while (q + 1 < k) { span(acc, xs[q], xs[q + 1], w); q += 2 }
            }
            val row = y * w
            for (x in 0 until w) out[row + x] = (acc[x] / sub * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
        return out
    }

    /** Dodaje do [acc] pokrycie odcinka [a, b) (piksel x zajmuje [x, x+1)). */
    private fun span(acc: FloatArray, a0: Float, b0: Float, w: Int) {
        val a = a0.coerceIn(0f, w.toFloat()); val b = b0.coerceIn(0f, w.toFloat())
        if (b <= a) return
        val ia = floor(a).toInt(); val ib = min(ceil(b).toInt() - 1, w - 1)
        if (ia == ib) { acc[ia] += b - a; return }
        acc[ia] += (ia + 1) - a
        for (x in ia + 1 until ib) acc[x] += 1f
        acc[ib] += b - ib
    }

    /**
     * Rozmiar wstawianego obrazu (pt) na stronie [pageW]×[pageH]: jak wycięty (skala 1:1 z kartką źródła), a gdy nie
     * mieści się w [FIT] strony — zmniejszony z zachowaniem proporcji.
     */
    fun fit(w: Float, h: Float, pageW: Float, pageH: Float): FloatArray {
        val k = min(1f, min(pageW * FIT / w, pageH * FIT / h))
        return floatArrayOf(w * k, h * k)
    }

    /** Kolor i rozmiar (pt) podpisu źródła — szary, drobny, czytelny na jasnym i (po zamianie) ciemnym papierze. */
    const val CAPTION_COLOR = 0xFF78909C.toInt()
    const val CAPTION_PT = 9f

    /**
     * Elementy wstawianego wycinka: obraz [w]×[h] (pt) środkiem w ([cx], [cy]) i — gdy jest [caption] — podpis tuż pod
     * nim, nie węższy niż 120 pt. Wkleja się je razem (jedno cofnięcie), a wklejanie dosuwa całość do wnętrza strony.
     */
    fun elements(asset: String, w: Float, h: Float, cx: Float, cy: Float, caption: String?, nextId: () -> Int): List<Element> {
        val img = ImageEl(nextId(), asset, cx, cy, w, h)
        if (caption.isNullOrBlank()) return listOf(img)
        val boxH = CAPTION_PT * 1.6f
        return listOf(img, TextBox(nextId(), cx, cy + h / 2f + boxH / 2f + 2f, max(w, 120f), boxH, 0f, caption, CAPTION_PT, CAPTION_COLOR))
    }

    /** Podpis źródła pod wycinkiem: „slajd 12 · wykład.pdf” dla stron PDF, inaczej „str. 12 · Zeszyt”. */
    fun caption(pageNo: Int, title: String, pdf: Boolean, polish: Boolean = true): String {
        val kind = when {
            pdf && polish -> "slajd"
            pdf -> "slide"
            polish -> "str."
            else -> "p."
        }
        val t = title.trim()
        return if (t.isEmpty()) "$kind ${pageNo + 1}" else "$kind ${pageNo + 1} · $t"
    }
}
