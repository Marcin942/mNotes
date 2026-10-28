package com.mdev.mnotes.core.guide

import com.mdev.mnotes.core.layout.Handedness
import com.mdev.mnotes.core.layout.MiniMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3c-A10: makiety próbek przewodnika jako proste kształty z rolami kolorów, w polu [W]×[H]. Tę samą scenę rysuje
 * aplikacja (Compose, kolory motywu) i podgląd (`tools/PreviewHarness`, AWT) — podgląd pokazuje to, co ekran.
 */
object SetupScenes {
    const val W = 320f
    const val H = 220f

    /** Rola koloru: tło ramki, powierzchnia ekranu, kartka, pasek, akcent, pismo, przygaszone, linie wzoru, dłoń, zakreślacz. */
    enum class Role { FRAME, SURFACE, PAPER, BAR, ACCENT, INK, MUTED, LINE, HAND, MARK }

    /** Kolor roli (ARGB) w motywie jasnym/ciemnym przy akcencie [accent]; wspólny dla aplikacji i podglądu. */
    fun color(role: Role, dark: Boolean, accent: Int): Int = when (role) {
        Role.FRAME -> if (dark) 0xFF55585E.toInt() else 0xFFD0D3D8.toInt()
        Role.SURFACE -> if (dark) 0xFF1C1D1F.toInt() else 0xFFDDE0E4.toInt()
        Role.PAPER -> if (dark) 0xFF2A2C30.toInt() else 0xFFFFFFFF.toInt()
        Role.BAR -> if (dark) 0xFF383A3E.toInt() else 0xFFF2F3F5.toInt()
        Role.ACCENT -> accent
        Role.INK -> if (dark) 0xFFE8E9EB.toInt() else 0xFF1B1D21.toInt()
        Role.MUTED -> if (dark) 0xFF8A8F96.toInt() else 0xFF9AA0A8.toInt()
        Role.LINE -> if (dark) 0xFF3E4652.toInt() else 0xFFC9D6E6.toInt()
        Role.HAND -> if (dark) 0xB3A08470.toInt() else 0xB3E2BFA3.toInt()
        Role.MARK -> if (dark) 0x80B59A2A.toInt() else 0x80FFE14D.toInt()
    }

    sealed class Shape(val role: Role)
    class Box(val l: Float, val t: Float, val r: Float, val b: Float, val radius: Float, role: Role, val outline: Boolean = false) : Shape(role)
    class Dot(val cx: Float, val cy: Float, val rad: Float, role: Role, val outline: Boolean = false) : Shape(role)
    class Line(val pts: FloatArray, val width: Float, role: Role) : Shape(role)
    class Label(val x: Float, val y: Float, val size: Float, val text: String, role: Role) : Shape(role)

    /** Scena próbki dla strony [p] przy bieżących ustawieniach [s]. */
    fun of(p: SetupGuide.Page, s: SetupGuide.Store): List<Shape> = when (p.sample) {
        SetupGuide.Sample.WELCOME -> welcome(s.get(SetupGuide.USE))
        SetupGuide.Sample.HAND -> screen(s.get("input.hand"), s.get("toolbar.position"), 1, showHand = true)
        SetupGuide.Sample.INPUT -> input(s.get("input.finger"))
        SetupGuide.Sample.PAGE -> paper(s.get("new.template"))
        SetupGuide.Sample.THEME -> screen(s.get("input.hand"), 0, 1, showHand = false)
        SetupGuide.Sample.TOOLBAR -> screen(s.get("input.hand"), s.get("toolbar.position"), s.get("toolbar.size"), showHand = false)
        SetupGuide.Sample.DONE -> done()
        SetupGuide.Sample.WRITE -> if (p.id == "smoothing") smoothing(s.get("pen.smoothing")) else straighten(s.get("input.hold_to_shape") != 0)
        SetupGuide.Sample.ERASER -> if (p.id == "lasso") lasso(s.get("lasso.touch") != 0) else eraser(s.get("eraser.mode"))
        SetupGuide.Sample.GESTURES -> gestures(p.id)
        SetupGuide.Sample.MATH -> math(p.id, s)
        SetupGuide.Sample.VIEW -> if (p.id == "minimap") minimap(s.get("view.minimap") != 0) else margin(s.get("view.scroll_margin") != 0)
        SetupGuide.Sample.HINTS -> hints(p.id)
    }

    // — pomocnicze —

    private fun frame(out: MutableList<Shape>) {
        out += Box(8f, 8f, W - 8f, H - 8f, 16f, Role.SURFACE)
        out += Box(8f, 8f, W - 8f, H - 8f, 16f, Role.FRAME, outline = true)
    }

    /** Pismo: fala jak linijka tekstu od x0 do x1 na wysokości y. */
    private fun scribble(x0: Float, x1: Float, y: Float, amp: Float = 3.2f, seed: Int = 0): FloatArray {
        val n = ((x1 - x0) / 2.5f).toInt().coerceAtLeast(2)
        val out = FloatArray(2 * n)
        for (i in 0 until n) {
            val x = x0 + (x1 - x0) * i / (n - 1)
            val k = i + seed * 7
            out[2 * i] = x
            out[2 * i + 1] = y - amp * (0.55f * sin(k * 0.9f) + 0.45f * sin(k * 2.3f + seed)).toFloat()
        }
        return out
    }

    private fun circle(cx: Float, cy: Float, r: Float, wobble: Float = 0f, n: Int = 48): FloatArray {
        val out = FloatArray(2 * (n + 1))
        for (i in 0..n) {
            val a = 2 * PI * i / n
            val rr = r + wobble * (sin(a * 3 + 0.7) * 0.6 + sin(a * 5) * 0.4).toFloat()
            out[2 * i] = cx + (rr * cos(a)).toFloat(); out[2 * i + 1] = cy + (rr * sin(a)).toFloat()
        }
        return out
    }

    private fun lines(out: MutableList<Shape>, l: Float, t: Float, r: Float, b: Float, template: Int) {
        when (template) {
            1 -> { var y = t + 18f; while (y < b - 4f) { out += Line(floatArrayOf(l + 6f, y, r - 6f, y), 0.8f, Role.LINE); y += 16f } }
            2 -> {
                var y = t + 10f; while (y < b) { out += Line(floatArrayOf(l, y, r, y), 0.6f, Role.LINE); y += 12f }
                var x = l + 10f; while (x < r) { out += Line(floatArrayOf(x, t, x, b), 0.6f, Role.LINE); x += 12f }
            }
            3 -> { var y = t + 10f; while (y < b) { var x = l + 10f; while (x < r) { out += Dot(x, y, 0.9f, Role.LINE); x += 12f }; y += 12f } }
        }
    }

    // — sceny —

    /** Makieta ekranu edytora: pasek w położeniu [bar], listwa pióra i mini-mapa zależnie od ręki, opcjonalnie dłoń. */
    fun screen(handSide: Int, bar: Int, size: Int, showHand: Boolean): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        val th = 14f + 4f * size                 // grubość paska jak przy rozmiarze przycisków
        var l = 16f; var t = 16f; var r = W - 16f; var b = H - 16f
        val barBox = when (bar) {
            1 -> Box(l, b - th, r, b, 7f, Role.BAR).also { b -= th + 6f }
            2 -> Box(l, t, l + th, b, 7f, Role.BAR).also { l += th + 6f }
            3 -> Box(r - th, t, r, b, 7f, Role.BAR).also { r -= th + 6f }
            else -> Box(l, t, r, t + th, 7f, Role.BAR).also { t += th + 6f }
        }
        out += barBox
        // przyciski na pasku
        val n = 6
        for (i in 0 until n) {
            val f = (i + 0.5f) / n
            val (cx, cy) = if (bar == 2 || bar == 3) (barBox.l + barBox.r) / 2 to barBox.t + (barBox.b - barBox.t) * f * 0.6f + 8f
                else barBox.l + (barBox.r - barBox.l) * f * 0.5f + 10f to (barBox.t + barBox.b) / 2
            out += Dot(cx, cy, th * 0.22f, if (i == 0) Role.ACCENT else Role.MUTED)
        }
        // kartka
        out += Box(l + 26f, t, r - 26f, b, 4f, Role.PAPER)
        lines(out, l + 26f, t, r - 26f, b, 1)
        var y = t + 18f
        var k = 0
        while (y < b - 30f && k < 4) { out += Line(scribble(l + 36f, r - 50f - 18f * (k % 2), y - 3f, seed = k), 1.4f, Role.INK); y += 16f; k++ }
        // listwa pióra: z dala od dłoni (praworęczny — z lewej)
        val stripLeft = handSide != Handedness.LEFT
        val sx = if (stripLeft) l + 4f else r - 22f
        out += Box(sx, t + 10f, sx + 18f, t + 92f, 9f, Role.BAR)
        for (i in 0 until 4) out += Dot(sx + 9f, t + 24f + i * 18f, 5f, if (i == 0) Role.INK else if (i == 1) Role.ACCENT else Role.MUTED)
        // mini-mapa: domyślny róg prawy dolny, odbity dla lewej ręki
        val corner = if (handSide == Handedness.LEFT) Handedness.miniMap(MiniMap.BOTTOM_RIGHT) else MiniMap.BOTTOM_RIGHT
        val mx = if (corner == MiniMap.BOTTOM_LEFT) l + 30f else r - 30f - 34f
        out += Box(mx, b - 30f, mx + 34f, b - 6f, 4f, Role.MUTED, outline = true)
        out += Box(mx + 6f, b - 26f, mx + 20f, b - 14f, 2f, Role.ACCENT, outline = true)
        if (showHand) {
            // Dłoń piszącego obok i poniżej rysika, po stronie ręki; rysik od dłoni do końca pisma.
            val right = handSide != Handedness.LEFT
            val hx = if (right) r - 40f else l + 66f
            out += Dot(hx, b - 4f, 38f, Role.HAND)
            out += Line(floatArrayOf(hx + (if (right) -18f else 18f), b - 28f, if (right) r - 104f else l + 120f, t + 70f), 4f, Role.INK)
        }
        return out
    }

    fun input(finger: Int): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(24f, 22f, W - 24f, H - 22f, 6f, Role.PAPER)
        lines(out, 24f, 22f, W - 24f, H - 22f, 1)
        out += Line(scribble(40f, 190f, 52f, seed = 1), 1.6f, Role.INK)
        out += Line(scribble(40f, 150f, 68f, seed = 3), 1.6f, Role.INK)
        // rysik
        out += Line(floatArrayOf(196f, 46f, 236f, 6f), 5f, Role.MUTED)
        when (finger) {
            0 -> { // palec przesuwa kartkę: strzałki
                out += Dot(200f, 150f, 12f, Role.HAND)
                out += Line(floatArrayOf(170f, 150f, 230f, 150f), 2f, Role.ACCENT)
                out += Line(floatArrayOf(200f, 120f, 200f, 180f), 2f, Role.ACCENT)
                out += Line(floatArrayOf(224f, 144f, 230f, 150f, 224f, 156f), 2f, Role.ACCENT)
                out += Line(floatArrayOf(176f, 144f, 170f, 150f, 176f, 156f), 2f, Role.ACCENT)
            }
            1 -> { // palec też pisze
                out += Line(scribble(90f, 190f, 150f, amp = 6f, seed = 5), 3f, Role.ACCENT)
                out += Dot(194f, 150f, 12f, Role.HAND)
            }
            else -> { // palec nic nie robi
                out += Dot(200f, 150f, 12f, Role.HAND)
                out += Line(floatArrayOf(184f, 134f, 216f, 166f), 2.5f, Role.MUTED)
            }
        }
        return out
    }

    fun paper(template: Int): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(70f, 16f, W - 70f, H - 16f, 4f, Role.PAPER)
        lines(out, 70f, 16f, W - 70f, H - 16f, template)
        out += Line(scribble(86f, 220f, 46f, seed = 2), 1.5f, Role.INK)
        out += Line(scribble(86f, 190f, 62f, seed = 4), 1.5f, Role.INK)
        return out
    }

    fun welcome(use: Int): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(60f, 16f, W - 60f, H - 16f, 4f, Role.PAPER)
        when (use) {
            SetupGuide.USE_MATH -> {
                lines(out, 60f, 16f, W - 60f, H - 16f, 2)
                out += Line(floatArrayOf(80f, 170f, 240f, 170f), 1.2f, Role.INK)
                out += Line(floatArrayOf(100f, 196f, 100f, 40f), 1.2f, Role.INK)
                val par = FloatArray(2 * 30)
                for (i in 0 until 30) { val x = 110f + i * 4.5f; par[2 * i] = x; par[2 * i + 1] = 165f - (x - 175f) * (x - 175f) / 34f }
                out += Line(par, 2f, Role.ACCENT)
                out += Label(196f, 66f, 14f, "y = x²", Role.INK)
            }
            SetupGuide.USE_PDF -> {
                var y = 34f
                repeat(9) { i -> out += Box(76f, y, W - 76f - (i % 3) * 18f, y + 5f, 2f, Role.MUTED); y += 17f }
                out += Box(74f, 65f, 220f, 77f, 3f, Role.MARK)
                out += Line(scribble(80f, 170f, 158f, seed = 6), 1.5f, Role.ACCENT)
            }
            SetupGuide.USE_DRAW -> {
                out += Line(circle(130f, 110f, 40f, wobble = 2f), 2f, Role.INK)
                out += Line(floatArrayOf(160f, 150f, 200f, 70f, 230f, 160f, 160f, 150f), 2f, Role.ACCENT)
                out += Line(scribble(90f, 230f, 190f, amp = 5f, seed = 8), 1.4f, Role.MUTED)
            }
            else -> {
                lines(out, 60f, 16f, W - 60f, H - 16f, 1)
                out += Label(76f, 42f, 15f, "mNotes", Role.ACCENT)
                var y = 66f
                repeat(5) { i -> out += Line(scribble(76f, W - 80f - (i % 2) * 40f, y - 3f, seed = i), 1.4f, Role.INK); y += 16f }
            }
        }
        return out
    }

    fun done(): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Dot(W / 2, H / 2, 46f, Role.ACCENT)
        out += Line(floatArrayOf(W / 2 - 22f, H / 2 + 2f, W / 2 - 6f, H / 2 + 18f, W / 2 + 24f, H / 2 - 16f), 7f, Role.PAPER)
        return out
    }

    fun straighten(on: Boolean): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        out += Line(circle(90f, 110f, 46f, wobble = 5f), 2f, Role.INK)
        out += Line(floatArrayOf(148f, 110f, 172f, 110f), 2f, Role.MUTED)
        out += Line(floatArrayOf(164f, 102f, 172f, 110f, 164f, 118f), 2f, Role.MUTED)
        out += Line(if (on) circle(230f, 110f, 46f) else circle(230f, 110f, 46f, wobble = 5f), 2f, if (on) Role.ACCENT else Role.INK)
        return out
    }

    fun smoothing(level: Int): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        val amp = when (level) { 0 -> 7f; 1 -> 4f; else -> 1.6f }
        for (k in 0 until 4) out += Line(scribble(40f, W - 40f, 60f + k * 32f, amp = amp, seed = k), 2f, Role.INK)
        return out
    }

    fun eraser(mode: Int): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        val a = scribble(40f, 280f, 80f, amp = 6f, seed = 1)
        val b = scribble(40f, 280f, 140f, amp = 6f, seed = 2)
        when (mode) {
            0 -> { out += Line(a, 2f, Role.MUTED); out += Line(b, 2f, Role.INK); out += Line(floatArrayOf(40f, 80f, 280f, 80f), 1f, Role.ACCENT) }
            1 -> {
                out += Line(a.copyOfRange(0, 40), 2f, Role.INK); out += Line(a.copyOfRange(60, a.size), 2f, Role.INK); out += Line(b, 2f, Role.INK)
            }
            else -> {
                out += Line(a, 2f, Role.INK); out += Line(b, 2f, Role.INK)
                out += Line(circle(160f, 80f, 30f, wobble = 3f), 1.5f, Role.ACCENT)
            }
        }
        out += Box(150f, 60f, 176f, 96f, 6f, Role.BAR)
        return out
    }

    fun lasso(touch: Boolean): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        out += Line(circle(100f, 110f, 28f), 2f, Role.ACCENT)
        out += Line(circle(210f, 110f, 28f), 2f, if (touch) Role.ACCENT else Role.INK)
        out += Line(circle(135f, 110f, 70f, wobble = 4f), 1.5f, Role.MUTED)
        return out
    }

    fun gestures(id: String): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        when (id) {
            "two_finger" -> { out += Dot(140f, 110f, 14f, Role.HAND); out += Dot(180f, 104f, 14f, Role.HAND) }
            "pen_button" -> {
                out += Line(floatArrayOf(110f, 170f, 220f, 50f), 10f, Role.MUTED)
                out += Dot(175f, 100f, 5f, Role.ACCENT)
            }
            else -> { out += Dot(160f, 110f, 14f, Role.HAND); out += Dot(160f, 110f, 24f, Role.ACCENT, outline = true); out += Dot(160f, 110f, 34f, Role.ACCENT, outline = true) }
        }
        return out
    }

    fun math(id: String, s: SetupGuide.Store): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        if (id == "math_text") {
            val on = s.get("text.math_unicode") != 0
            out += Label(40f, 80f, 16f, "\\alpha + x^2 + a_1", Role.MUTED)
            out += Label(40f, 140f, 22f, if (on) "α + x² + a₁" else "\\alpha + x^2 + a_1", Role.INK)
        } else {
            val on = s.get("construct.names") != 0
            out += Line(floatArrayOf(90f, 170f, 230f, 170f, 160f, 50f, 90f, 170f), 2f, Role.INK)
            out += Line(circle(160f, 134f, 80f), 1.5f, Role.ACCENT)
            out += Dot(160f, 134f, 3f, Role.ACCENT)
            if (on) { out += Label(80f, 188f, 13f, "A", Role.INK); out += Label(234f, 188f, 13f, "B", Role.INK); out += Label(164f, 46f, 13f, "C", Role.INK); out += Label(166f, 132f, 13f, "O", Role.ACCENT) }
        }
        return out
    }

    fun margin(on: Boolean): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        val dx = if (on) 110f else 0f
        out += Box(30f + dx, 24f, 200f + dx, H - 24f, 4f, Role.PAPER)
        lines(out, 30f + dx, 24f, 200f + dx, H - 24f, 1)
        out += Line(scribble(40f + dx, 180f + dx, 50f, seed = 3), 1.5f, Role.INK)
        if (on) out += Dot(64f, H - 30f, 40f, Role.HAND)
        return out
    }

    fun minimap(on: Boolean): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(16f, 16f, W - 16f, H - 16f, 4f, Role.PAPER)
        lines(out, 16f, 16f, W - 16f, H - 16f, 2)
        out += Line(scribble(30f, 250f, 60f, amp = 9f, seed = 2), 3f, Role.INK)
        if (on) {
            out += Box(W - 82f, H - 72f, W - 26f, H - 26f, 4f, Role.SURFACE)
            out += Box(W - 82f, H - 72f, W - 26f, H - 26f, 4f, Role.MUTED, outline = true)
            out += Box(W - 74f, H - 66f, W - 56f, H - 52f, 2f, Role.ACCENT, outline = true)
        }
        return out
    }

    fun hints(id: String): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(16f, 16f, W - 16f, 46f, 8f, Role.BAR)
        for (i in 0 until 6) out += Dot(40f + i * 30f, 31f, 7f, if (i == 2) Role.ACCENT else Role.MUTED)
        out += Box(16f, 54f, W - 16f, H - 16f, 4f, Role.PAPER)
        when (id) {
            "haptics" -> { out += Dot(100f, 31f, 13f, Role.ACCENT, outline = true); out += Dot(100f, 31f, 18f, Role.ACCENT, outline = true) }
            else -> {
                out += Box(70f, 58f, 210f, 84f, 8f, Role.INK)
                out += Label(80f, 76f, 12f, if (id == "tooltips") "Gumka" else "Przeciągnij po tekście", Role.PAPER)
            }
        }
        return out
    }
}
