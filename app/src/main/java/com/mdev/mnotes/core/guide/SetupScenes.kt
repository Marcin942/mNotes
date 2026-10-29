package com.mdev.mnotes.core.guide

import com.mdev.mnotes.core.layout.Handedness
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3c-A10: makiety próbek przewodnika jako proste kształty z rolami kolorów, w polu [W]×[H]. Tę samą scenę rysuje
 * aplikacja (Compose, kolory motywu) i podgląd (`tools/PreviewHarness`, AWT) — podgląd pokazuje to, co ekran.
 * Każda scena zależy od bieżącego wyboru na stronie, żeby różnicę między opcjami było widać, nie tylko czytać.
 */
object SetupScenes {
    const val W = 320f
    const val H = 220f

    /** Wnętrze ramki ekranu — tu przycinana jest scena (i przesuwana kartka w żywym polu). */
    const val INSET = 8f

    /**
     * Rola koloru: ramka, powierzchnia ekranu, kartka, pasek, akcent, pismo, przygaszone, linie wzoru, dłoń, zakreślacz;
     * `*_LIGHT`/`*_DARK` — kartka, pismo i linie niezależne od motywu (strona „Jasno czy ciemno?”).
     */
    enum class Role { FRAME, SURFACE, PAPER, BAR, ACCENT, INK, MUTED, LINE, HAND, MARK, PAPER_LIGHT, PAPER_DARK, INK_LIGHT, INK_DARK, LINE_LIGHT, LINE_DARK }

    /** Kolor roli (ARGB) w motywie jasnym/ciemnym przy akcencie [accent]; wspólny dla aplikacji i podglądu. */
    fun color(role: Role, dark: Boolean, accent: Int): Int = when (role) {
        Role.FRAME -> if (dark) 0xFF55585E.toInt() else 0xFFD0D3D8.toInt()
        Role.SURFACE -> if (dark) 0xFF1C1D1F.toInt() else 0xFFDDE0E4.toInt()
        Role.PAPER -> color(if (dark) Role.PAPER_DARK else Role.PAPER_LIGHT, dark, accent)
        Role.BAR -> if (dark) 0xFF383A3E.toInt() else 0xFFF2F3F5.toInt()
        Role.ACCENT -> accent
        Role.INK -> color(if (dark) Role.INK_DARK else Role.INK_LIGHT, dark, accent)
        Role.MUTED -> if (dark) 0xFF8A8F96.toInt() else 0xFF9AA0A8.toInt()
        Role.LINE -> color(if (dark) Role.LINE_DARK else Role.LINE_LIGHT, dark, accent)
        Role.HAND -> if (dark) 0xB3A08470.toInt() else 0xB3E2BFA3.toInt()
        Role.MARK -> if (dark) 0x80B59A2A.toInt() else 0x80FFE14D.toInt()
        Role.PAPER_LIGHT -> 0xFFFFFFFF.toInt()
        Role.PAPER_DARK -> 0xFF2A2C30.toInt()
        Role.INK_LIGHT -> 0xFF1B1D21.toInt()
        Role.INK_DARK -> 0xFFE8E9EB.toInt()
        Role.LINE_LIGHT -> 0xFFC9D6E6.toInt()
        Role.LINE_DARK -> 0xFF4A5361.toInt()
    }

    sealed class Shape(val role: Role)
    class Box(val l: Float, val t: Float, val r: Float, val b: Float, val radius: Float, role: Role, val outline: Boolean = false) : Shape(role)
    class Dot(val cx: Float, val cy: Float, val rad: Float, role: Role, val outline: Boolean = false) : Shape(role)
    class Line(val pts: FloatArray, val width: Float, role: Role) : Shape(role)
    /** Napis po polsku i angielsku (wybiera rysujący); [x], [y] — początek linii bazowej. */
    class Label(val x: Float, val y: Float, val size: Float, val pl: String, val en: String, role: Role) : Shape(role)

    /** Kartka w scenie (z ramką ekranu wokół): w żywym polu przesuwa się tylko to, co leży po [FRAME_SHAPES] kształtach. */
    const val FRAME_SHAPES = 2

    /** Scena próbki dla strony [p] przy bieżących ustawieniach [s]. */
    fun of(p: SetupGuide.Page, s: SetupGuide.Store): List<Shape> = when (p.sample) {
        SetupGuide.Sample.WELCOME -> welcome(s.get(SetupGuide.USE))
        SetupGuide.Sample.HAND -> screen(s.get("input.hand"), s.get("toolbar.position"), 1, showHand = true)
        SetupGuide.Sample.INPUT -> input(s.get("input.finger"))
        SetupGuide.Sample.PAGE -> paper(s.get("new.template"))
        // „Jak w systemie” zostawia kartki takie, jakie powstały — nowe są jasne.
        SetupGuide.Sample.THEME -> screen(s.get("input.hand"), 0, 1, showHand = false, darkPage = s.get("pages.theme") == 2)
        SetupGuide.Sample.TOOLBAR -> screen(s.get("input.hand"), s.get("toolbar.position"), s.get("toolbar.size"), showHand = false)
        SetupGuide.Sample.DONE -> emptyList()
        SetupGuide.Sample.WRITE -> if (p.id == "smoothing") smoothing(s.get("pen.smoothing")) else straighten(s.get("input.hold_to_shape") != 0)
        SetupGuide.Sample.ERASER -> if (p.id == "lasso") lasso(s.get("lasso.touch") != 0) else eraser(s.get("eraser.mode"))
        SetupGuide.Sample.GESTURES -> gestures(p.id, s)
        SetupGuide.Sample.MATH -> math(p.id, s)
        SetupGuide.Sample.VIEW -> if (p.id == "minimap") minimap(s.get("view.minimap") != 0) else margin(s.get("view.scroll_margin") != 0)
        SetupGuide.Sample.HINTS -> hints(p.id, s)
    }

    // — pomocnicze —

    private fun frame(out: MutableList<Shape>) {
        out += Box(INSET, INSET, W - INSET, H - INSET, 16f, Role.SURFACE)
        out += Box(INSET, INSET, W - INSET, H - INSET, 16f, Role.FRAME, outline = true)
    }

    /** Pismo: fala jak linijka tekstu od x0 do x1 na wysokości y; [freq] < 1 — łagodniejsza (wygładzona) linia. */
    private fun scribble(x0: Float, x1: Float, y: Float, amp: Float = 3.2f, seed: Int = 0, freq: Float = 1f): FloatArray {
        val n = ((x1 - x0) / 2.5f).toInt().coerceAtLeast(2)
        val out = FloatArray(2 * n)
        for (i in 0 until n) {
            val x = x0 + (x1 - x0) * i / (n - 1)
            val k = (i + seed * 7) * freq
            out[2 * i] = x
            out[2 * i + 1] = y - amp * (0.55f * sin(k * 0.9f) + 0.45f * sin(k * 2.3f + seed))
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

    private fun lines(out: MutableList<Shape>, l: Float, t: Float, r: Float, b: Float, template: Int, line: Role = Role.LINE) {
        when (template) {
            1 -> { var y = t + 18f; while (y < b - 4f) { out += Line(floatArrayOf(l + 6f, y, r - 6f, y), 0.8f, line); y += 16f } }
            2 -> {
                var y = t + 10f; while (y < b) { out += Line(floatArrayOf(l, y, r, y), 0.6f, line); y += 12f }
                var x = l + 10f; while (x < r) { out += Line(floatArrayOf(x, t, x, b), 0.6f, line); x += 12f }
            }
            3 -> { var y = t + 10f; while (y < b) { var x = l + 10f; while (x < r) { out += Dot(x, y, 1.4f, line); x += 12f }; y += 12f } }
        }
    }

    /** Strzałka z grotem od (x0, y0) do (x1, y1). */
    private fun arrow(out: MutableList<Shape>, x0: Float, y0: Float, x1: Float, y1: Float, role: Role, w: Float = 2f) {
        out += Line(floatArrayOf(x0, y0, x1, y1), w, role)
        val a = kotlin.math.atan2(y1 - y0, x1 - x0); val h = 7f
        out += Line(floatArrayOf(x1 - h * cos(a - 0.5f), y1 - h * sin(a - 0.5f), x1, y1, x1 - h * cos(a + 0.5f), y1 - h * sin(a + 0.5f)), w, role)
    }

    /** Zakrzywiona strzałka cofania (w lewo) albo ponawiania (w prawo) wokół (cx, cy). */
    private fun undoArrow(out: MutableList<Shape>, cx: Float, cy: Float, redo: Boolean, role: Role) {
        val d = if (redo) -1f else 1f
        val pts = FloatArray(2 * 13)
        for (i in 0..12) { val a = PI * i / 12; pts[2 * i] = cx + d * (14f * cos(a)).toFloat(); pts[2 * i + 1] = cy - (14f * sin(a)).toFloat() }
        out += Line(pts, 2.4f, role)
        val ex = cx - d * 14f
        out += Line(floatArrayOf(ex - 6f, cy - 6f, ex, cy, ex + 6f, cy - 6f), 2.4f, role)
    }

    private fun cross(out: MutableList<Shape>, cx: Float, cy: Float, r: Float, role: Role) {
        out += Line(floatArrayOf(cx - r, cy - r, cx + r, cy + r), 2.4f, role)
        out += Line(floatArrayOf(cx + r, cy - r, cx - r, cy + r), 2.4f, role)
    }

    // — sceny —

    /**
     * Makieta ekranu edytora: pasek w położeniu [bar], listwa pióra zależnie od ręki, opcjonalnie dłoń przy piśmie.
     * [darkPage]: kartka jasna/ciemna niezależnie od motywu; null — jak motyw.
     */
    fun screen(handSide: Int, bar: Int, size: Int, showHand: Boolean, darkPage: Boolean? = null): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        val paperRole = when (darkPage) { null -> Role.PAPER; true -> Role.PAPER_DARK; false -> Role.PAPER_LIGHT }
        val inkRole = when (darkPage) { null -> Role.INK; true -> Role.INK_DARK; false -> Role.INK_LIGHT }
        val lineRole = when (darkPage) { null -> Role.LINE; true -> Role.LINE_DARK; false -> Role.LINE_LIGHT }
        val th = 14f + 4f * size                 // grubość paska jak przy rozmiarze przycisków
        var l = 16f; var t = 16f; var r = W - 16f; var b = H - 16f
        val barBox = when (bar) {
            1 -> Box(l, b - th, r, b, 7f, Role.BAR).also { b -= th + 6f }
            2 -> Box(l, t, l + th, b, 7f, Role.BAR).also { l += th + 6f }
            3 -> Box(r - th, t, r, b, 7f, Role.BAR).also { r -= th + 6f }
            else -> Box(l, t, r, t + th, 7f, Role.BAR).also { t += th + 6f }
        }
        out += barBox
        val n = 6
        for (i in 0 until n) {
            val f = (i + 0.5f) / n
            val (cx, cy) = if (bar == 2 || bar == 3) (barBox.l + barBox.r) / 2 to barBox.t + (barBox.b - barBox.t) * f * 0.6f + 8f
                else barBox.l + (barBox.r - barBox.l) * f * 0.5f + 10f to (barBox.t + barBox.b) / 2
            out += Dot(cx, cy, th * 0.22f, if (i == 0) Role.ACCENT else Role.MUTED)
        }
        out += Box(l + 26f, t, r - 26f, b, 4f, paperRole)
        lines(out, l + 26f, t, r - 26f, b, 1, lineRole)
        var y = t + 18f
        var k = 0
        while (y < b - 30f && k < 4) { out += Line(scribble(l + 36f, r - 50f - 18f * (k % 2), y - 3f, seed = k), 1.4f, inkRole); y += 16f; k++ }
        // Listwa pióra z dala od piszącej ręki: praworęczny — z lewej, leworęczny — z prawej.
        val stripLeft = handSide != Handedness.LEFT
        val sx = if (stripLeft) l + 4f else r - 22f
        out += Box(sx, t + 10f, sx + 18f, t + 92f, 9f, Role.BAR)
        for (i in 0 until 4) out += Dot(sx + 9f, t + 24f + i * 18f, 5f, if (i == 0) inkRole else if (i == 1) Role.ACCENT else Role.MUTED)
        if (showHand) {
            val right = handSide != Handedness.LEFT
            val tipX = if (right) r - 74f else l + 70f
            val tipY = y - 14f
            val d = if (right) 1f else -1f
            out += Line(floatArrayOf(tipX, tipY, tipX + d * 30f, tipY + 40f), 4f, Role.MUTED)
            out += Dot(tipX + d * 40f, tipY + 50f, 20f, Role.HAND)
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
        out += Line(floatArrayOf(194f, 50f, 230f, 26f), 5f, Role.MUTED)   // rysik przy końcu pisma
        when (finger) {
            0 -> { // palec przewija (strzałki) i powiększa (lupa)
                out += Dot(200f, 150f, 12f, Role.HAND)
                arrow(out, 200f, 136f, 200f, 104f, Role.ACCENT); arrow(out, 200f, 164f, 200f, 196f, Role.ACCENT)
                arrow(out, 186f, 150f, 160f, 150f, Role.ACCENT); arrow(out, 214f, 150f, 240f, 150f, Role.ACCENT)
                out += Dot(270f, 112f, 10f, Role.ACCENT, outline = true)
                out += Line(floatArrayOf(277f, 119f, 286f, 128f), 2.4f, Role.ACCENT)
            }
            1 -> { // palec też pisze
                out += Line(scribble(90f, 190f, 150f, amp = 6f, seed = 5), 3f, Role.ACCENT)
                out += Dot(194f, 150f, 12f, Role.HAND)
            }
            else -> { // palec nic nie robi
                out += Dot(200f, 150f, 12f, Role.HAND)
                cross(out, 230f, 132f, 7f, Role.MUTED)
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
                out += Label(196f, 66f, 14f, "y = x²", "y = x²", Role.INK)
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
                out += Label(76f, 42f, 15f, "mNotes", "mNotes", Role.ACCENT)
                var y = 66f
                repeat(5) { i -> out += Line(scribble(76f, W - 80f - (i % 2) * 40f, y - 3f, seed = i), 1.4f, Role.INK); y += 16f }
            }
        }
        return out
    }

    fun straighten(on: Boolean): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        out += Line(circle(90f, 110f, 46f, wobble = 5f), 2f, Role.INK)
        arrow(out, 146f, 110f, 174f, 110f, Role.MUTED)
        out += Line(if (on) circle(230f, 110f, 46f) else circle(230f, 110f, 46f, wobble = 5f), 2f, if (on) Role.ACCENT else Role.INK)
        return out
    }

    fun smoothing(level: Int): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        // Więcej wygładzania: mniejsze i rzadsze drgania, aż po łagodną falę.
        val (amp, freq) = when (level) { 0 -> 6f to 1f; 1 -> 4f to 0.55f; else -> 4f to 0.22f }
        for (k in 0 until 4) out += Line(scribble(40f, W - 40f, 56f + k * 34f, amp = amp, seed = k, freq = freq), 2f, Role.INK)
        return out
    }

    fun eraser(mode: Int): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        val a = scribble(40f, 280f, 80f, amp = 6f, seed = 1)
        val b = scribble(40f, 280f, 140f, amp = 6f, seed = 2)
        when (mode) {
            0 -> { out += Line(a, 2f, Role.LINE); out += Line(b, 2f, Role.INK) }   // dotknięta kreska znika cała — blady ślad po niej
            1 -> { out += Line(a.copyOfRange(0, 76), 2f, Role.INK); out += Line(a.copyOfRange(96, a.size), 2f, Role.INK); out += Line(b, 2f, Role.INK) }
            else -> {
                out += Line(b, 2f, Role.INK)
                out += Line(circle(160f, 80f, 34f, wobble = 3f), 1.6f, Role.ACCENT)   // obrys: to, co w nim, znika
            }
        }
        if (mode != 2) out += Box(150f, 62f, 176f, 98f, 6f, Role.ACCENT, outline = true)   // gumka w miejscu dotyku
        return out
    }

    fun lasso(touch: Boolean): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        out += Line(circle(100f, 110f, 28f), 2f, Role.ACCENT)
        // Drugi okrąg pętla tylko zahacza: wybrany dopiero przy „wszystko, czego dotknie”.
        out += Line(circle(210f, 110f, 28f), 2f, if (touch) Role.ACCENT else Role.INK)
        out += Line(circle(135f, 110f, 70f, wobble = 4f), 1.5f, Role.MUTED)
        return out
    }

    fun gestures(id: String, s: SetupGuide.Store): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        when (id) {
            "two_finger" -> {
                out += Dot(120f, 116f, 14f, Role.HAND); out += Dot(156f, 108f, 14f, Role.HAND)
                if (s.get("input.two_finger_undo") != 0) undoArrow(out, 230f, 118f, false, Role.ACCENT) else cross(out, 230f, 110f, 9f, Role.MUTED)
            }
            "pen_button" -> {
                out += Line(floatArrayOf(60f, 190f, 130f, 100f), 10f, Role.MUTED)
                out += Dot(104f, 133f, 5f, Role.ACCENT)
                when (s.get("input.pen_button")) {
                    0 -> out += Box(196f, 92f, 236f, 128f, 6f, Role.ACCENT, outline = true)
                    1 -> out += Line(circle(216f, 110f, 34f, wobble = 4f), 1.8f, Role.ACCENT)
                    3 -> for (i in 0 until 6) out += Dot(180f + i * 12f, 110f - 14f * sin(i * 0.9f), 2.5f + i * 0.6f, Role.ACCENT)
                    else -> cross(out, 216f, 110f, 9f, Role.MUTED)
                }
            }
            else -> {
                out += Dot(120f, 110f, 14f, Role.HAND); out += Dot(120f, 110f, 24f, Role.ACCENT, outline = true); out += Dot(120f, 110f, 34f, Role.ACCENT, outline = true)
                when (s.get("input.double_tap")) {
                    0 -> undoArrow(out, 224f, 118f, false, Role.ACCENT)
                    1 -> undoArrow(out, 224f, 118f, true, Role.ACCENT)
                    2 -> { out += Dot(220f, 104f, 16f, Role.ACCENT, outline = true); out += Line(floatArrayOf(232f, 116f, 246f, 130f), 3f, Role.ACCENT) }
                    else -> cross(out, 224f, 110f, 9f, Role.MUTED)
                }
            }
        }
        return out
    }

    fun math(id: String, s: SetupGuide.Store): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(20f, 20f, W - 20f, H - 20f, 6f, Role.PAPER)
        if (id == "math_text") {
            val on = s.get("text.math_unicode") != 0
            out += Label(40f, 80f, 16f, "\\alpha + x^2 + a_1", "\\alpha + x^2 + a_1", Role.MUTED)
            arrow(out, 60f, 98f, 60f, 118f, Role.MUTED)
            val t = if (on) "α + x² + a₁" else "\\alpha + x^2 + a_1"
            out += Label(40f, 150f, 22f, t, t, Role.INK)
        } else {
            val on = s.get("construct.names") != 0
            // Trójkąt z okręgiem opisanym (środek (160; 128,9), r ≈ 58,9) — cały w kartce.
            out += Line(floatArrayOf(110f, 160f, 210f, 160f, 160f, 70f, 110f, 160f), 2f, Role.INK)
            out += Line(circle(160f, 128.9f, 58.9f), 1.5f, Role.ACCENT)
            out += Dot(160f, 128.9f, 3f, Role.ACCENT)
            if (on) {
                out += Label(96f, 176f, 13f, "A", "A", Role.INK); out += Label(214f, 176f, 13f, "B", "B", Role.INK)
                out += Label(166f, 66f, 13f, "C", "C", Role.INK); out += Label(166f, 132f, 13f, "O", "O", Role.ACCENT)
            }
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
        if (on) { out += Dot(70f, H - 56f, 30f, Role.HAND); arrow(out, 112f, 110f, 136f, 110f, Role.ACCENT) }
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

    fun hints(id: String, s: SetupGuide.Store): List<Shape> {
        val out = ArrayList<Shape>()
        frame(out)
        out += Box(16f, 16f, W - 16f, 46f, 8f, Role.BAR)
        for (i in 0 until 6) out += Dot(40f + i * 30f, 31f, 7f, if (i == 2) Role.ACCENT else Role.MUTED)
        out += Box(16f, 54f, W - 16f, H - 16f, 4f, Role.PAPER)
        out += Dot(100f, 31f, 11f, Role.ACCENT, outline = true)   // dotknięty przycisk
        when (id) {
            "haptics" -> if (s.get("ui.haptics") != 0) {
                out += Dot(100f, 31f, 15f, Role.ACCENT, outline = true); out += Dot(100f, 31f, 19f, Role.ACCENT, outline = true)
            }
            "tooltips" -> if (s.get("ui.tooltips") != 0) {
                out += Box(70f, 60f, 140f, 84f, 8f, Role.INK)
                out += Label(80f, 77f, 12f, "Gumka", "Eraser", Role.PAPER)
            }
            else -> if (s.get("ui.tool_hints") != 0) {
                out += Box(70f, 60f, 250f, 84f, 8f, Role.INK)
                out += Label(80f, 77f, 12f, "Przeciągnij, by zakryć", "Drag to cover", Role.PAPER)
            }
        }
        return out
    }
}
