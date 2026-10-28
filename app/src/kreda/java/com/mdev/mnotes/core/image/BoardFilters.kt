// Algorytmy kredy na podstawie programu kreda — autor i licencja do potwierdzenia przed publikacją (app/src/kreda/NOTICE.md).

package com.mdev.mnotes.core.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Filtry na płaskich kanałach (FloatArray w×h): rozmycie pudełkowe, min/max (erozja, dylatacja), otwarcie i domknięcie,
 * próg Otsu, składowe spójne. Wzorowane na tym, co `kreda.py` robi OpenCV-em, przepisane od zera na tablicach.
 */
object BoardFilters {

    /** Rozmycie pudełkowe o promieniu [r], oddzielnie w poziomie i pionie; [passes] = 2 daje kształt bliski Gaussowi. */
    fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int, passes: Int = 2): FloatArray {
        if (r <= 0) return src.copyOf()
        var a = src.copyOf()
        val tmp = FloatArray(a.size)
        repeat(passes) {
            for (y in 0 until h) line(a, tmp, y * w, 1, w, r)
            for (x in 0 until w) line(tmp, a, x, w, h, r)
        }
        return a
    }

    /** Średnia ruchoma jednej linii (wiersza albo kolumny) z odbiciem na brzegach. */
    private fun line(src: FloatArray, dst: FloatArray, start: Int, step: Int, n: Int, r: Int) {
        fun at(i: Int): Float = src[start + step * (if (i < 0) -i - 1 else if (i >= n) 2 * n - i - 1 else i).coerceIn(0, n - 1)]
        var sum = 0f
        for (i in -r..r) sum += at(i)
        val k = 2 * r + 1
        for (i in 0 until n) {
            dst[start + step * i] = sum / k
            sum += at(i + r + 1) - at(i - r)
        }
    }

    /** Minimum (erozja) albo maksimum (dylatacja) w kwadracie (2r+1)², rozdzielnie: najpierw wiersze, potem kolumny. */
    fun minMax(src: FloatArray, w: Int, h: Int, r: Int, takeMax: Boolean): FloatArray {
        if (r <= 0) return src.copyOf()
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var v = src[y * w + x]
            for (xx in max(0, x - r)..min(w - 1, x + r)) {
                val s = src[y * w + xx]
                v = if (takeMax) max(v, s) else min(v, s)
            }
            tmp[y * w + x] = v
        }
        for (x in 0 until w) for (y in 0 until h) {
            var v = tmp[y * w + x]
            for (yy in max(0, y - r)..min(h - 1, y + r)) {
                val s = tmp[yy * w + x]
                v = if (takeMax) max(v, s) else min(v, s)
            }
            out[y * w + x] = v
        }
        return out
    }

    /** Otwarcie (erozja, potem dylatacja): usuwa jasne cienkie kreski, zostawia tło ciemnej tablicy. */
    fun open(src: FloatArray, w: Int, h: Int, r: Int) = minMax(minMax(src, w, h, r, false), w, h, r, true)

    /** Domknięcie: usuwa ciemne cienkie kreski (pisak na białej tablicy). */
    fun close(src: FloatArray, w: Int, h: Int, r: Int) = minMax(minMax(src, w, h, r, true), w, h, r, false)

    /** Próg Otsu dla wartości 0..1 (256 przedziałów). */
    fun otsu(v: FloatArray): Float {
        val hist = IntArray(256)
        for (x in v) hist[(x.coerceIn(0f, 1f) * 255f).toInt()]++
        val total = v.size.toDouble()
        var sumAll = 0.0
        for (i in 0..255) sumAll += i * hist[i].toDouble()
        var wB = 0.0; var sumB = 0.0; var best = -1.0; var thr = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB; val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; thr = t }
        }
        return (thr + 0.5f) / 255f
    }

    /** Największa składowa spójna (4-sąsiedztwo) maski, z wypełnionymi dziurami; null, gdy maska jest pusta. */
    fun largestFilled(mask: BooleanArray, w: Int, h: Int): BooleanArray? {
        val label = IntArray(mask.size)
        var best = 0; var bestSize = 0; var next = 0
        val queue = IntArray(mask.size)
        for (s in mask.indices) {
            if (!mask[s] || label[s] != 0) continue
            next++
            var head = 0; var tail = 0
            queue[tail++] = s; label[s] = next
            while (head < tail) {
                val p = queue[head++]
                val x = p % w; val y = p / w
                if (x > 0 && mask[p - 1] && label[p - 1] == 0) { label[p - 1] = next; queue[tail++] = p - 1 }
                if (x < w - 1 && mask[p + 1] && label[p + 1] == 0) { label[p + 1] = next; queue[tail++] = p + 1 }
                if (y > 0 && mask[p - w] && label[p - w] == 0) { label[p - w] = next; queue[tail++] = p - w }
                if (y < h - 1 && mask[p + w] && label[p + w] == 0) { label[p + w] = next; queue[tail++] = p + w }
            }
            if (tail > bestSize) { bestSize = tail; best = next }
        }
        if (best == 0) return null
        val blob = BooleanArray(mask.size) { label[it] == best }
        // Dziury (osoba przed tablicą, odblask) to piksele spoza plamy nieosiągalne od brzegu obrazu.
        val outside = BooleanArray(mask.size)
        var head = 0; var tail = 0
        fun seed(p: Int) { if (!blob[p] && !outside[p]) { outside[p] = true; queue[tail++] = p } }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        while (head < tail) {
            val p = queue[head++]
            val x = p % w; val y = p / w
            if (x > 0) seed(p - 1)
            if (x < w - 1) seed(p + 1)
            if (y > 0) seed(p - w)
            if (y < h - 1) seed(p + w)
        }
        return BooleanArray(mask.size) { blob[it] || !outside[it] }
    }

    /** Mediana tablicy (kopia sortowana; do szacowania szumu i jasności). */
    fun median(v: FloatArray): Float {
        if (v.isEmpty()) return 0f
        val s = v.copyOf().also { it.sort() }
        return s[s.size / 2]
    }

    /** Odporne odchylenie: 1.4826 × mediana |v − mediana| (MAD). */
    fun madSigma(v: FloatArray): Float {
        val med = median(v)
        return 1.4826f * median(FloatArray(v.size) { abs(v[it] - med) })
    }

    /** Powiększenie płaskiego kanału dwuliniowo z [sw]×[sh] do [dw]×[dh]. */
    fun upscale(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val out = FloatArray(dw * dh)
        val kx = sw.toFloat() / dw; val ky = sh.toFloat() / dh
        for (y in 0 until dh) {
            val fy = ((y + 0.5f) * ky - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(sh - 1, y0 + 1); val ty = fy - y0
            for (x in 0 until dw) {
                val fx = ((x + 0.5f) * kx - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(sw - 1, x0 + 1); val tx = fx - x0
                val a = src[y0 * sw + x0] * (1 - tx) + src[y0 * sw + x1] * tx
                val b = src[y1 * sw + x0] * (1 - tx) + src[y1 * sw + x1] * tx
                out[y * dw + x] = a * (1 - ty) + b * ty
            }
        }
        return out
    }
}
