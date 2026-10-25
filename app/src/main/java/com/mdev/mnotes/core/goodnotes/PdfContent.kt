package com.mdev.mnotes.core.goodnotes

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Operator treści strony z argumentami; [start]..[end] to bajty od pierwszego argumentu do końca operatora. */
class PdfOp(val op: String, val args: List<Any?>, val start: Int, val end: Int)

object PdfContentLexer {
    /** Operatory po kolei. Obraz wpisany w treść (`BI … ID … EI`) to jeden operator `BI` ze słownikiem w argumencie. */
    fun ops(b: ByteArray): List<PdfOp> {
        val out = ArrayList<PdfOp>()
        val lx = PdfLexer(b)
        val args = ArrayList<Any?>()
        var start = -1
        while (true) {
            lx.skipWs()
            val at = lx.pos
            val t = try { lx.token() } catch (_: Exception) { break }
            if (t == PdfEof) break
            if (start < 0) start = at
            if (t is PdfKeyword && t.word != "[" && t.word != "<<" && t.word != "]" && t.word != ">>" && t.word != "true" &&
                t.word != "false" && t.word != "null"
            ) {
                if (t.word == "BI") {
                    val d = PdfDict()
                    while (true) {
                        val k = try { lx.token() } catch (_: Exception) { PdfEof }
                        if (k == PdfEof || (k is PdfKeyword && k.word == "ID")) break
                        if (k is PdfName) d[k.name] = try { lx.parseObject(false) } catch (_: Exception) { null }
                    }
                    var p = lx.pos + 1
                    var endAt = b.size
                    while (p + 1 < b.size) {
                        if (b[p] == 'E'.code.toByte() && b[p + 1] == 'I'.code.toByte() && PdfLexer.isWs(b[p - 1].toInt() and 0xFF) &&
                            (p + 2 >= b.size || PdfLexer.isWs(b[p + 2].toInt() and 0xFF))
                        ) { endAt = p + 2; break }
                        p++
                    }
                    lx.pos = endAt
                    out.add(PdfOp("BI", listOf(d), start, endAt))
                } else {
                    out.add(PdfOp(t.word, ArrayList(args), start, lx.pos))
                }
                args.clear()
                start = -1
                continue
            }
            val v = try { lx.objectFrom(t, false, 0) } catch (_: Exception) { null }
            args.add(v)
            if (args.size > 64) { args.clear(); start = -1 }
        }
        return out
    }
}

/** Macierz przekształcenia PDF `[a b c d e f]`: x' = a·x + c·y + e, y' = b·x + d·y + f. */
class Mat(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double) {
    fun x(x: Double, y: Double) = a * x + c * y + e
    fun y(x: Double, y: Double) = b * x + d * y + f

    /** Najpierw this, potem [m] (tak działa `cm`: CTM' = this × CTM). */
    fun then(m: Mat) = Mat(
        a * m.a + b * m.c, a * m.b + b * m.d, c * m.a + d * m.c, c * m.b + d * m.d,
        e * m.a + f * m.c + m.e, e * m.b + f * m.d + m.f,
    )

    val det: Double get() = a * d - b * c
    val scale: Double get() = sqrt(abs(det))

    companion object {
        val ID = Mat(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        fun of(v: List<Any?>): Mat? {
            if (v.size < 6) return null
            val n = v.take(6).map { (it as? Double) ?: return null }
            return Mat(n[0], n[1], n[2], n[3], n[4], n[5])
        }
    }
}

enum class CsKind { GRAY, RGB, CMYK, LAB, INDEXED, PATTERN, UNSUPPORTED }

/** Przestrzeń barw w stopniu wystarczającym do odczytu kolorów pisma i tła. */
class ColorSpace(val kind: CsKind, val n: Int, val base: ColorSpace? = null, val hival: Int = 0, val lookup: ByteArray? = null) {
    /** Kolor ARGB (kanał alfa pełny) albo null, gdy nie da się go wyznaczyć. */
    fun argb(c: DoubleArray): Int? {
        fun ch(v: Double) = (v.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
        fun pack(r: Double, g: Double, b: Double) = (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
        return when (kind) {
            CsKind.GRAY -> c.getOrNull(0)?.let { pack(it, it, it) }
            CsKind.RGB -> if (c.size >= 3) pack(c[0], c[1], c[2]) else null
            CsKind.CMYK -> if (c.size >= 4) pack((1 - c[0]) * (1 - c[3]), (1 - c[1]) * (1 - c[3]), (1 - c[2]) * (1 - c[3])) else null
            CsKind.LAB -> if (c.size >= 3) lab(c[0], c[1], c[2]) else null
            CsKind.INDEXED -> {
                val i = (c.getOrNull(0) ?: return null).toInt().coerceIn(0, hival)
                val bs = base ?: return null
                val lk = lookup ?: return null
                if ((i + 1) * bs.n > lk.size) return null
                bs.argb(DoubleArray(bs.n) { (lk[i * bs.n + it].toInt() and 0xFF) / 255.0 })
            }
            else -> null
        }
    }

    private fun lab(l: Double, a: Double, b: Double): Int {
        fun f(t: Double) = if (t > 6.0 / 29) t * t * t else 3 * (6.0 / 29) * (6.0 / 29) * (t - 4.0 / 29)
        val fy = (l + 16) / 116
        val x = 0.9505 * f(fy + a / 500); val y = f(fy); val z = 1.089 * f(fy - b / 200)
        fun g(v: Double) = if (v <= 0.0031308) 12.92 * v else 1.055 * Math.pow(v, 1 / 2.4) - 0.055
        val r = g(3.2406 * x - 1.5372 * y - 0.4986 * z)
        val gg = g(-0.9689 * x + 1.8758 * y + 0.0415 * z)
        val bb = g(0.0557 * x - 0.2040 * y + 1.0570 * z)
        fun ch(v: Double) = (v.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(gg) shl 8) or ch(bb)
    }

    fun initial(): DoubleArray = when (kind) {
        CsKind.CMYK -> doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        CsKind.LAB -> doubleArrayOf(0.0, 0.0, 0.0)
        else -> DoubleArray(n)
    }

    companion object {
        val GRAY = ColorSpace(CsKind.GRAY, 1)
        val RGB = ColorSpace(CsKind.RGB, 3)
        val CMYK = ColorSpace(CsKind.CMYK, 4)
        val PATTERN = ColorSpace(CsKind.PATTERN, 0)

        fun resolve(doc: PdfDocument, v: Any?, res: PdfDict?, depth: Int = 0): ColorSpace {
            if (depth > 8) return ColorSpace(CsKind.UNSUPPORTED, 1)
            val r = doc.resolve(v)
            if (r is PdfName) {
                when (r.name) {
                    "DeviceGray", "G", "CalGray" -> return GRAY
                    "DeviceRGB", "RGB", "CalRGB" -> return RGB
                    "DeviceCMYK", "CMYK" -> return CMYK
                    "Pattern" -> return PATTERN
                    "Indexed", "I" -> return ColorSpace(CsKind.UNSUPPORTED, 1)
                }
                val named = doc.dict(res?.get("ColorSpace"))?.get(r.name) ?: return ColorSpace(CsKind.UNSUPPORTED, 1)
                return resolve(doc, named, null, depth + 1)
            }
            val arr = r as? List<*> ?: return ColorSpace(CsKind.UNSUPPORTED, 1)
            val head = (doc.resolve(arr.firstOrNull()) as? PdfName)?.name
            return when (head) {
                "DeviceGray", "CalGray" -> GRAY
                "DeviceRGB", "CalRGB" -> RGB
                "DeviceCMYK" -> CMYK
                "Lab" -> ColorSpace(CsKind.LAB, 3)
                "ICCBased" -> {
                    val s = doc.resolve(arr.getOrNull(1)) as? PdfStream
                    when ((doc.num(s?.dict?.get("N")) ?: 3.0).toInt()) {
                        1 -> GRAY
                        4 -> CMYK
                        else -> RGB
                    }
                }
                "Indexed", "I" -> {
                    val base = resolve(doc, arr.getOrNull(1), res, depth + 1)
                    val hival = (doc.num(arr.getOrNull(2)) ?: 0.0).toInt().coerceIn(0, 255)
                    val lk = when (val l = doc.resolve(arr.getOrNull(3))) {
                        is PdfString -> l.bytes
                        is PdfStream -> try { doc.decodeStream(l) } catch (_: Exception) { null }
                        else -> null
                    }
                    if (base.kind in setOf(CsKind.GRAY, CsKind.RGB, CsKind.CMYK, CsKind.LAB) && lk != null) ColorSpace(CsKind.INDEXED, 1, base, hival, lk)
                    else ColorSpace(CsKind.UNSUPPORTED, 1)
                }
                "Pattern" -> PATTERN
                "Separation" -> ColorSpace(CsKind.UNSUPPORTED, 1)
                "DeviceN" -> ColorSpace(CsKind.UNSUPPORTED, (arr.getOrNull(1) as? List<*>)?.size ?: 1)
                else -> ColorSpace(CsKind.UNSUPPORTED, 1)
            }
        }
    }
}

/** Stan graficzny w chwili malowania (kopie, bo interpreter zmienia swój na bieżąco). */
class GState(
    val ctm: Mat,
    val lineWidth: Double,
    val cap: Int,
    val join: Int,
    val dash: DoubleArray,
    val fillCs: ColorSpace,
    val fillC: DoubleArray,
    val strokeCs: ColorSpace,
    val strokeC: DoubleArray,
    val fillAlpha: Double,
    val strokeAlpha: Double,
    val blend: String,
    val softMask: Boolean,
    val clipped: Boolean,
    val textRender: Int,
    /** Prostokąt otaczający przecięcie ścieżek przycinania (x0, y0, x1, y1 na stronie PDF); null = bez przycinania. */
    val clipBox: DoubleArray? = null,
) {
    val fillArgb: Int? get() = fillCs.argb(fillC)
    val strokeArgb: Int? get() = strokeCs.argb(strokeC)
}

enum class SegKind { MOVE, LINE, CURVE, CLOSE }

/** Odcinek ścieżki we współrzędnych strony PDF (po CTM). CURVE ma 3 punkty (dwa kontrolne i końcowy), reszta jeden. */
class Seg(val kind: SegKind, val pts: DoubleArray)

/** Podścieżka: zaczyna się od MOVE. */
class SubPath(val segs: List<Seg>) {
    val closed: Boolean get() = segs.any { it.kind == SegKind.CLOSE }
    val hasCurves: Boolean get() = segs.any { it.kind == SegKind.CURVE }
    val drawSegments: Int get() = segs.count { it.kind == SegKind.LINE || it.kind == SegKind.CURVE }
}

sealed class ContentItem {
    abstract val start: Int
    abstract val end: Int
    /** Numer bloku `q … Q` najwyższego poziomu, w którym leży element (−1 poza nimi). */
    abstract val group: Int
}

class PathItem(
    val subs: List<SubPath>,
    val stroke: Boolean,
    val fill: Boolean,
    val evenOdd: Boolean,
    val clip: Boolean,
    val state: GState,
    /** Prostokąt `re` jako jedyna podścieżka: x, y, w, h w przestrzeni użytkownika (przed CTM). */
    val rect: DoubleArray?,
    override val start: Int,
    override val end: Int,
    override val group: Int,
) : ContentItem()

class ImageItem(val name: String, val stream: PdfStream, val state: GState, override val start: Int, override val end: Int, override val group: Int) : ContentItem()

class FormItem(val name: String, override val start: Int, override val end: Int, override val group: Int) : ContentItem()

class TextItem(val visible: Boolean, override val start: Int, override val end: Int, override val group: Int) : ContentItem()

/** Coś widocznego, czego nie zamieniamy (cieniowanie, obraz wpisany w treść, nieznany XObject). */
class OtherItem(val what: String, override val start: Int, override val end: Int, override val group: Int) : ContentItem()

/**
 * Interpreter treści jednej strony: śledzi stan graficzny i zwraca wszystko, co maluje, z położeniem w strumieniu.
 * Tekst nie jest rozkładany na glify (tylko widoczny/niewidoczny), formularze nie są rozwijane.
 */
class PdfInterpreter(private val doc: PdfDocument, private val resources: PdfDict) {
    private class Mut(
        var ctm: Mat = Mat.ID, var lw: Double = 1.0, var cap: Int = 0, var join: Int = 0, var dash: DoubleArray = DoubleArray(0),
        var fillCs: ColorSpace = ColorSpace.GRAY, var fillC: DoubleArray = doubleArrayOf(0.0),
        var strokeCs: ColorSpace = ColorSpace.GRAY, var strokeC: DoubleArray = doubleArrayOf(0.0),
        var ca: Double = 1.0, var CA: Double = 1.0, var blend: String = "Normal", var smask: Boolean = false,
        var clipped: Boolean = false, var tr: Int = 0, var clipBox: DoubleArray? = null,
    ) {
        fun copy() = Mut(ctm, lw, cap, join, dash, fillCs, fillC, strokeCs, strokeC, ca, CA, blend, smask, clipped, tr, clipBox)
        fun freeze() = GState(ctm, lw, cap, join, dash, fillCs, fillC.copyOf(), strokeCs, strokeC.copyOf(), ca, CA, blend, smask, clipped, tr, clipBox)
    }

    fun run(content: ByteArray): List<ContentItem> {
        val items = ArrayList<ContentItem>()
        val stack = ArrayList<Mut>()
        var g = Mut()
        var group = -1
        var groupCount = 0
        val subs = ArrayList<SubPath>()
        var cur = ArrayList<Seg>()
        var pathStart = -1
        var rect: DoubleArray? = null
        var rectCount = 0
        var clipPending = false
        var cx = 0.0; var cy = 0.0
        var sx = 0.0; var sy = 0.0
        var textStart = -1
        var textVisible = false

        fun flushSub() { if (cur.size > 1 || (cur.size == 1 && cur[0].kind != SegKind.MOVE)) subs.add(SubPath(cur)); cur = ArrayList() }
        fun pt(x: Double, y: Double) = doubleArrayOf(g.ctm.x(x, y), g.ctm.y(x, y))
        fun nums(a: List<Any?>) = a.map { (it as? Double) ?: 0.0 }
        fun resetPath() { subs.clear(); cur = ArrayList(); pathStart = -1; rect = null; rectCount = 0; clipPending = false }

        for (op in PdfContentLexer.ops(content)) {
            val a = op.args
            when (op.op) {
                "q" -> { stack.add(g.copy()); if (stack.size == 1) { groupCount++; group = groupCount - 1 } }
                "Q" -> { if (stack.isNotEmpty()) g = stack.removeAt(stack.size - 1); if (stack.isEmpty()) group = -1 }
                "cm" -> Mat.of(a)?.let { g.ctm = it.then(g.ctm) }
                "w" -> (a.getOrNull(0) as? Double)?.let { g.lw = it }
                "J" -> (a.getOrNull(0) as? Double)?.let { g.cap = it.toInt() }
                "j" -> (a.getOrNull(0) as? Double)?.let { g.join = it.toInt() }
                "d" -> g.dash = ((a.getOrNull(0) as? List<*>)?.mapNotNull { it as? Double } ?: emptyList()).toDoubleArray()
                "gs" -> applyExtGState((a.getOrNull(0) as? PdfName)?.name, g)
                "CS" -> { g.strokeCs = ColorSpace.resolve(doc, a.getOrNull(0), resources); g.strokeC = g.strokeCs.initial() }
                "cs" -> { g.fillCs = ColorSpace.resolve(doc, a.getOrNull(0), resources); g.fillC = g.fillCs.initial() }
                "SC", "SCN" -> g.strokeC = nums(a.filter { it is Double }).toDoubleArray()
                "sc", "scn" -> g.fillC = nums(a.filter { it is Double }).toDoubleArray()
                "G" -> { g.strokeCs = ColorSpace.GRAY; g.strokeC = nums(a).toDoubleArray() }
                "g" -> { g.fillCs = ColorSpace.GRAY; g.fillC = nums(a).toDoubleArray() }
                "RG" -> { g.strokeCs = ColorSpace.RGB; g.strokeC = nums(a).toDoubleArray() }
                "rg" -> { g.fillCs = ColorSpace.RGB; g.fillC = nums(a).toDoubleArray() }
                "K" -> { g.strokeCs = ColorSpace.CMYK; g.strokeC = nums(a).toDoubleArray() }
                "k" -> { g.fillCs = ColorSpace.CMYK; g.fillC = nums(a).toDoubleArray() }
                "m" -> {
                    if (pathStart < 0) pathStart = op.start
                    flushSub()
                    val n = nums(a); if (n.size < 2) continue
                    cx = n[0]; cy = n[1]; sx = cx; sy = cy
                    cur.add(Seg(SegKind.MOVE, pt(cx, cy)))
                    rect = null; rectCount += 2
                }
                "l" -> {
                    val n = nums(a); if (n.size < 2) continue
                    if (cur.isEmpty()) cur.add(Seg(SegKind.MOVE, pt(cx, cy)))
                    cx = n[0]; cy = n[1]
                    cur.add(Seg(SegKind.LINE, pt(cx, cy)))
                }
                "c", "v", "y" -> {
                    val n = nums(a)
                    val need = if (op.op == "c") 6 else 4
                    if (n.size < need) continue
                    if (cur.isEmpty()) cur.add(Seg(SegKind.MOVE, pt(cx, cy)))
                    val (x1, y1, x2, y2, x3, y3) = when (op.op) {
                        "c" -> listOf(n[0], n[1], n[2], n[3], n[4], n[5])
                        "v" -> listOf(cx, cy, n[0], n[1], n[2], n[3])
                        else -> listOf(n[0], n[1], n[2], n[3], n[2], n[3])
                    }.let { Six(it) }
                    cur.add(Seg(SegKind.CURVE, pt(x1, y1) + pt(x2, y2) + pt(x3, y3)))
                    cx = x3; cy = y3
                }
                "h" -> { if (cur.isNotEmpty()) { cur.add(Seg(SegKind.CLOSE, pt(sx, sy))); cx = sx; cy = sy } }
                "re" -> {
                    if (pathStart < 0) pathStart = op.start
                    flushSub()
                    val n = nums(a); if (n.size < 4) continue
                    val (x, y, w, h) = n
                    cur.add(Seg(SegKind.MOVE, pt(x, y)))
                    cur.add(Seg(SegKind.LINE, pt(x + w, y)))
                    cur.add(Seg(SegKind.LINE, pt(x + w, y + h)))
                    cur.add(Seg(SegKind.LINE, pt(x, y + h)))
                    cur.add(Seg(SegKind.CLOSE, pt(x, y)))
                    flushSub()
                    cx = x; cy = y; sx = x; sy = y
                    rectCount++
                    rect = if (rectCount == 1) doubleArrayOf(x, y, w, h) else null
                }
                "W", "W*" -> clipPending = true
                "S", "s", "f", "F", "f*", "B", "B*", "b", "b*", "n" -> {
                    if (op.op == "s" || op.op == "b" || op.op == "b*") {
                        if (cur.isNotEmpty() && cur.last().kind != SegKind.CLOSE) cur.add(Seg(SegKind.CLOSE, pt(sx, sy)))
                    }
                    flushSub()
                    val stroke = op.op in setOf("S", "s", "B", "B*", "b", "b*")
                    val fill = op.op in setOf("f", "F", "f*", "B", "B*", "b", "b*")
                    val eo = op.op.endsWith("*")
                    if (subs.isNotEmpty() || clipPending) {
                        items.add(
                            PathItem(
                                ArrayList(subs), stroke, fill, eo, clipPending, g.freeze(), rect,
                                if (pathStart >= 0) pathStart else op.start, op.end, group,
                            ),
                        )
                    }
                    if (clipPending) {
                        g.clipped = true
                        val b = if (subs.isEmpty()) doubleArrayOf(0.0, 0.0, 0.0, 0.0) else PathMath.bbox(subs)
                        val c = g.clipBox
                        g.clipBox = if (c == null) b else doubleArrayOf(maxOf(c[0], b[0]), maxOf(c[1], b[1]), minOf(c[2], b[2]), minOf(c[3], b[3]))
                    }
                    resetPath()
                }
                "BT" -> { textStart = op.start; textVisible = false }
                "Tr" -> (a.getOrNull(0) as? Double)?.let { g.tr = it.toInt() }
                "Tj", "TJ", "'", "\"" -> { if (g.tr != 3 && g.tr != 7) textVisible = true }
                "ET" -> {
                    if (textStart >= 0) items.add(TextItem(textVisible, textStart, op.end, group))
                    textStart = -1
                }
                "Do" -> {
                    val name = (a.getOrNull(0) as? PdfName)?.name ?: continue
                    val xo = doc.resolve(doc.dict(resources["XObject"])?.get(name)) as? PdfStream
                    when (xo?.dict?.name("Subtype")) {
                        "Image" -> items.add(ImageItem(name, xo, g.freeze(), op.start, op.end, group))
                        "Form" -> items.add(FormItem(name, op.start, op.end, group))
                        else -> items.add(OtherItem("xobject", op.start, op.end, group))
                    }
                }
                "sh" -> items.add(OtherItem("shading", op.start, op.end, group))
                "BI" -> items.add(OtherItem("inline-image", op.start, op.end, group))
            }
        }
        return items
    }

    private class Six(v: List<Double>) {
        val x1 = v[0]; val y1 = v[1]; val x2 = v[2]; val y2 = v[3]; val x3 = v[4]; val y3 = v[5]
        operator fun component1() = x1
        operator fun component2() = y1
        operator fun component3() = x2
        operator fun component4() = y2
        operator fun component5() = x3
        operator fun component6() = y3
    }

    private fun applyExtGState(name: String?, g: Mut) {
        val d = doc.dict(doc.dict(resources["ExtGState"])?.get(name ?: return)) ?: return
        doc.num(d["LW"])?.let { g.lw = it }
        doc.num(d["LC"])?.let { g.cap = it.toInt() }
        doc.num(d["LJ"])?.let { g.join = it.toInt() }
        doc.num(d["ca"])?.let { g.ca = it.coerceIn(0.0, 1.0) }
        doc.num(d["CA"])?.let { g.CA = it.coerceIn(0.0, 1.0) }
        when (val bm = doc.resolve(d["BM"])) {
            is PdfName -> g.blend = bm.name
            is List<*> -> (doc.resolve(bm.firstOrNull()) as? PdfName)?.let { g.blend = it.name }
        }
        when (val sm = doc.resolve(d["SMask"])) {
            is PdfName -> if (sm.name == "None") g.smask = false
            is PdfDict -> g.smask = true
        }
        (doc.array(d["D"]))?.let { arr -> (doc.array(arr.firstOrNull()))?.let { g.dash = it.mapNotNull { v -> doc.num(v) }.toDoubleArray() } }
    }
}

/** Pomocnicze rachunki na ścieżkach. */
object PathMath {
    /** Spłaszczenie podścieżki do łamanej (x, y, x, y, …) z tolerancją [tol] w jednostkach współrzędnych. */
    fun flatten(sp: SubPath, tol: Double): DoubleArray {
        val out = ArrayList<Double>()
        var px = 0.0; var py = 0.0
        for (s in sp.segs) {
            when (s.kind) {
                SegKind.MOVE -> { px = s.pts[0]; py = s.pts[1]; out.add(px); out.add(py) }
                SegKind.LINE, SegKind.CLOSE -> {
                    val x = s.pts[0]; val y = s.pts[1]
                    if (s.kind == SegKind.CLOSE && out.size >= 2 && out[out.size - 2] == x && out[out.size - 1] == y) continue
                    out.add(x); out.add(y); px = x; py = y
                }
                SegKind.CURVE -> {
                    cubic(px, py, s.pts[0], s.pts[1], s.pts[2], s.pts[3], s.pts[4], s.pts[5], tol, out, 0)
                    px = s.pts[4]; py = s.pts[5]
                }
            }
        }
        return out.toDoubleArray()
    }

    private fun cubic(
        x0: Double, y0: Double, x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double,
        tol: Double, out: ArrayList<Double>, depth: Int,
    ) {
        val d1 = distToLine(x1, y1, x0, y0, x3, y3)
        val d2 = distToLine(x2, y2, x0, y0, x3, y3)
        if (depth >= 16 || max(d1, d2) <= tol) { out.add(x3); out.add(y3); return }
        val ax = (x0 + x1) / 2; val ay = (y0 + y1) / 2
        val bx = (x1 + x2) / 2; val by = (y1 + y2) / 2
        val cx = (x2 + x3) / 2; val cy = (y2 + y3) / 2
        val dx = (ax + bx) / 2; val dy = (ay + by) / 2
        val ex = (bx + cx) / 2; val ey = (by + cy) / 2
        val mx = (dx + ex) / 2; val my = (dy + ey) / 2
        cubic(x0, y0, ax, ay, dx, dy, mx, my, tol, out, depth + 1)
        cubic(mx, my, ex, ey, cx, cy, x3, y3, tol, out, depth + 1)
    }

    fun distToLine(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax; val dy = by - ay
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-12) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        return abs((px - ax) * dy - (py - ay) * dx) / len
    }

    /** Prostokąt otaczający punkty kontrolne (minX, minY, maxX, maxY). */
    fun bbox(subs: List<SubPath>): DoubleArray {
        var l = Double.POSITIVE_INFINITY; var t = Double.POSITIVE_INFINITY
        var r = Double.NEGATIVE_INFINITY; var b = Double.NEGATIVE_INFINITY
        for (sp in subs) for (s in sp.segs) {
            var i = 0
            while (i + 1 < s.pts.size) {
                l = min(l, s.pts[i]); r = max(r, s.pts[i]); t = min(t, s.pts[i + 1]); b = max(b, s.pts[i + 1]); i += 2
            }
        }
        return doubleArrayOf(l, t, r, b)
    }
}
