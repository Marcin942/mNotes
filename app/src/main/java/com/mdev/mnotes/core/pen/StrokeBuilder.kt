package com.mdev.mnotes.core.pen

import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import kotlin.math.hypot
import kotlin.math.min

/**
 * Buduje kreskę z surowych próbek rysika. Trzyma trójki x, y, p (p = współczynnik grubości) w jednej tablicy,
 * więc to samo wejście obsługuje i rysowanie na żywo ([StrokeOutline]), i końcowy [Stroke].
 */
class StrokeBuilder {
    var pts = FloatArray(3 * 256)
        private set
    var count = 0
        private set

    private var spec: PenSpec = PenSpec.of(PenKind.BALLPOINT)
    private var kind = PenKind.BALLPOINT
    private var color = 0xFF000000.toInt()
    var width = 1.42f
        private set
    var line = LineStyle.SOLID
        private set
    private var k = 1f
    private var minDist = 0.2f
    private var lastT = 0L
    private var rawX = 0f
    private var rawY = 0f
    private var rawP = 0f
    private var haveRaw = false

    /**
     * Surowe (niewygładzone) próbki x, y — wejście rozpoznawania kształtów: filtr kreski zaokrągla rogi i spóźnia się
     * za piórem, a rozpoznanie potrzebuje ostrych rogów. Dopisywane co ≥ 0,4 pt, więc nie puchną przy staniu w miejscu.
     */
    var raw = FloatArray(2 * 256)
        private set
    var rawCount = 0
        private set

    val penKind: PenKind get() = kind
    val penColor: Int get() = color
    val penSpec: PenSpec get() = spec

    /** [smoothing] 0..1; [minDistPt] odrzuca próbki bliższe niż tyle pt od poprzedniej. */
    fun begin(kind: PenKind, color: Int, width: Float, smoothing: Float, minDistPt: Float = 0.2f, line: LineStyle = LineStyle.SOLID) {
        this.kind = kind
        this.line = line
        this.spec = PenSpec.of(kind)
        this.color = color
        this.width = width
        this.k = 1f - smoothing.coerceIn(0f, 1f) * 0.72f
        this.minDist = minDistPt
        count = 0
        rawCount = 0
        haveRaw = false
        lastT = 0L
    }

    fun reset() { count = 0; rawCount = 0; haveRaw = false }

    /** Zwraca true, gdy dopisano nowy punkt. */
    fun add(x: Float, y: Float, pressure: Float, tMs: Long): Boolean {
        rawX = x; rawY = y; rawP = pressure; haveRaw = true
        pushRaw(x, y)
        if (count == 0) {
            push(x, y, spec.factor(pressure, 0f))
            lastT = tMs
            return true
        }
        val px = pts[3 * count - 3]; val py = pts[3 * count - 2]
        val sx = px + (x - px) * k
        val sy = py + (y - py) * k
        val d = hypot(sx - px, sy - py)
        if (d < minDist) return false
        val dt = (tMs - lastT).coerceAtLeast(1L)
        lastT = tMs
        val speed = d / dt
        val f = spec.factor(pressure, speed)
        // Wygładzenie współczynnika, żeby grubość nie skakała między próbkami.
        val prev = pts[3 * count - 1]
        push(sx, sy, prev + (f - prev) * 0.5f)
        return true
    }

    private fun pushRaw(x: Float, y: Float) {
        if (rawCount > 0 && hypot(x - raw[2 * rawCount - 2], y - raw[2 * rawCount - 1]) < 0.4f) return
        if (2 * rawCount + 2 > raw.size) raw = raw.copyOf(raw.size * 2)
        raw[2 * rawCount] = x; raw[2 * rawCount + 1] = y
        rawCount++
    }

    private fun push(x: Float, y: Float, p: Float) {
        if (3 * count + 3 > pts.size) pts = pts.copyOf(pts.size * 2)
        pts[3 * count] = x; pts[3 * count + 1] = y; pts[3 * count + 2] = p
        count++
    }

    /** Kończy kreskę: domyka opóźnienie filtra, nakłada zbieganie końcówek. Null, gdy nie ma żadnej próbki. */
    fun finish(id: Int): Stroke? {
        if (count == 0) return null
        if (haveRaw && count > 1) {
            val lx = pts[3 * count - 3]; val ly = pts[3 * count - 2]
            if (hypot(rawX - lx, rawY - ly) > 0.05f) push(rawX, rawY, pts[3 * count - 1])
        }
        if (count == 1) {
            // Kropka: dwa punkty w tym samym miejscu dają okrągłą kropkę dzięki zaokrąglonym końcom.
            val x = pts[0]; val y = pts[1]; val p = maxOf(pts[2], 0.6f)
            return Stroke(id, kind, color, width, floatArrayOf(x, y, p, x + 0.01f, y, p), line = line)
        }
        applyTaper()
        return Stroke(id, kind, color, width, pts.copyOf(3 * count), line = line)
    }

    private fun applyTaper() {
        val ts = spec.taperStart * width
        val te = spec.taperEnd * width
        if (ts <= 0f && te <= 0f) return
        var total = 0f
        for (i in 1 until count) total += hypot(pts[3 * i] - pts[3 * i - 3], pts[3 * i + 1] - pts[3 * i - 2])
        if (total <= 0f) return
        // Krótkie kreski nie mogą zniknąć: zbieganie dostaje najwyżej 45% długości z każdej strony.
        val sMax = min(ts, total * 0.45f)
        val eMax = min(te, total * 0.45f)
        var s = 0f
        for (i in 0 until count) {
            if (i > 0) s += hypot(pts[3 * i] - pts[3 * i - 3], pts[3 * i + 1] - pts[3 * i - 2])
            var m = 1f
            if (sMax > 0f && s < sMax) m = min(m, ease(s / sMax))
            val rest = total - s
            if (eMax > 0f && rest < eMax) m = min(m, ease(rest / eMax))
            pts[3 * i + 2] = (pts[3 * i + 2] * m).coerceIn(0.04f, 1f)
        }
    }

    private fun ease(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return 0.22f + 0.78f * (c * (2f - c))
    }
}
