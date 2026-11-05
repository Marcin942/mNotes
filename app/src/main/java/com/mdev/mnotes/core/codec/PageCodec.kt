package com.mdev.mnotes.core.codec

import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageCrop
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.ImageFx
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TapeEl
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.LineStyle
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.roundToInt

/**
 * Format zapisu strony „MNP1”:
 *
 *     "MNP1" | wersja u8 | flagi u8 (bit0 = deflate) | crc32 danych i32 | długość surowa varint | dane
 *
 * Dane: liczba rekordów, potem rekordy `tag u8 | długość varint | treść`. Nieznane tagi są pomijane,
 * dzięki czemu nowsze wersje mogą dopisywać rodzaje elementów bez łamania starszych.
 * Współrzędne kreski są zapisywane w 1/100 pt jako przyrosty (zigzag), współczynnik grubości w jednym bajcie.
 * Czytnik zawsze przeskakuje na koniec rekordu, więc nowe pola na końcu rekordu są bezpieczne dla starszych wersji.
 */
object PageCodec {

    private const val VERSION = 1
    private const val FLAG_DEFLATE = 1
    private const val TAG_STROKE = 1
    private const val TAG_TEXT = 2
    private const val TAG_IMAGE = 3
    private const val TAG_TAPE = 4
    private const val TAG_FILL = 5

    /**
     * Flagi elementu (varint): bit0 zarezerwowany (mnożenie), bit1 łamana z uchwytami wierzchołków (kształt z autokorekty),
     * bit2 zablokowany (w wypełnieniu bit0 = jak zakreślacz), bit3 „ma grupę” (varint na końcu rekordu — dopisywane bez zmiany wersji).
     */
    private const val FLAG_MULTIPLY = 1
    private const val FLAG_POLY = 2
    private const val FLAG_LOCKED = 4
    private const val FLAG_ELLIPSE = 8

    // Bity flag są osobne dla każdego rodzaju rekordu: 64 to krzywa w kresce i tryb „auto” w polu tekstu, 128 —
    // wypełnienie kreski i stały rozmiar pola tekstu. Czytnik sprawdza je dopiero po tagu rekordu, więc się nie mylą.

    /** Styl linii kreski; starsze wersje ignorują te bity i rysują linię ciągłą. */
    private const val FLAG_DASHED = 16
    private const val FLAG_DOTTED = 32
    /**
     * Kreska z punktami kontrolnymi krzywej Béziera (F17), dopisanymi za punktami. Starsza wersja pomija koniec
     * rekordu (rekordy mają długość) i rysuje samą gęstą łamaną, która wygląda tak samo.
     */
    private const val FLAG_BEZIER = 64
    /** Wypełnienie zamkniętej krzywej (2b-A10): i32 ARGB na samym końcu rekordu kreski; starsza wersja go pomija. */
    private const val FLAG_STROKE_FILL = 128
    /**
     * Pamięć krzywej w zwykłej kresce (LP-3): punkty kontrolne jak przy [FLAG_BEZIER], dopisane po wypełnieniu, na samym
     * końcu rekordu. Starsza wersja pomija ten ogon i ma zwykłą kreskę — wygląda tak samo, tylko nie pamięta krzywej.
     */
    private const val FLAG_CURVE_MEMO = 256
    // Tryb rozmiaru pola tekstowego; brak obu bitów = TextBox.FIT_HEIGHT, czyli zapis sprzed trybów.
    private const val FLAG_TEXT_AUTO = 64
    private const val FLAG_TEXT_FIXED = 128
    // Interlinia ≠ 1: f32 po tekście. Rekordy mają długość, więc starsza wersja pomija ten ogon i rysuje interlinię 1.
    private const val FLAG_TEXT_LINES = 256
    private const val FLAG_IMAGE_CROP = 512
    /** Po czterech ułamkach wycinka jeszcze kąt jego obrotu (f32, radiany): przycięcie z prostowaniem. */
    private const val FLAG_IMAGE_TILT = 1024
    /** Na końcu rekordu obrazu efekty: bajt flag (bit0 odwrócenie, bity 1..2 tryb), próg i wzmocnienia R, G, B (1/100). */
    private const val FLAG_IMAGE_FX = 2048
    // Pole tekstowe lub obraz odbite w osi lokalnej x. Bez ogona: starsza wersja pomija bit i rysuje bez odbicia.
    private const val FLAG_MIRROR = 4096
    /** LP-4: tekst rozciągnięty w osi x (f32 po odstępie wierszy); starsza wersja pominie ogon i pokaże litery bez rozciągnięcia. */
    private const val FLAG_TEXT_STRETCH = 8192
    private const val Q = 100f

    fun encode(content: PageContent): ByteArray {
        val raw = ByteWriter(content.size * 96 + 16)
        raw.varint(content.size)
        val body = ByteWriter(512)
        for (e in content.elements) {
            body.reset()
            val tag = when (e) {
                is Stroke -> { writeStroke(body, e); TAG_STROKE }
                is TextBox -> { writeText(body, e); TAG_TEXT }
                is ImageEl -> { writeImage(body, e); TAG_IMAGE }
                is TapeEl -> { writeTape(body, e); TAG_TAPE }
                is FillEl -> { writeFill(body, e); TAG_FILL }
            }
            raw.u8(tag)
            raw.varint(body.size)
            raw.bytes(body.buf, 0, body.size)
        }

        val rawBytes = raw.buf
        val rawLen = raw.size
        val deflate = rawLen > 64
        val payload: ByteArray = if (deflate) deflate(rawBytes, rawLen) else rawBytes.copyOf(rawLen)
        val crc = CRC32().apply { update(payload) }.value.toInt()

        val out = ByteWriter(payload.size + 24)
        out.u8('M'.code); out.u8('N'.code); out.u8('P'.code); out.u8('1'.code)
        out.u8(VERSION)
        out.u8(if (deflate) FLAG_DEFLATE else 0)
        out.i32(crc)
        out.varint(rawLen)
        out.bytes(payload, 0, payload.size)
        return out.toByteArray()
    }

    fun decode(data: ByteArray): PageContent {
        if (data.isEmpty()) return PageContent.EMPTY
        val r = ByteReader(data)
        if (data.size < 12 || r.u8() != 'M'.code || r.u8() != 'N'.code || r.u8() != 'P'.code || r.u8() != '1'.code) {
            throw CodecException("To nie jest strona MNP1")
        }
        val version = r.u8()
        if (version > VERSION) throw CodecException("Zbyt nowa wersja strony: $version")
        val flags = r.u8()
        val crc = r.i32()
        val rawLen = r.varint()
        val payloadOff = r.pos
        val check = CRC32().apply { update(data, payloadOff, data.size - payloadOff) }.value.toInt()
        if (check != crc) throw CodecException("Błędna suma kontrolna strony")

        val raw: ByteArray = if (flags and FLAG_DEFLATE != 0) {
            inflate(data, payloadOff, data.size - payloadOff, rawLen)
        } else {
            data.copyOfRange(payloadOff, data.size)
        }
        return decodeRaw(raw)
    }

    private fun decodeRaw(raw: ByteArray): PageContent {
        val r = ByteReader(raw)
        val n = r.varint()
        val list = ArrayList<Element>(n.coerceIn(0, 1 shl 20))
        repeat(n) {
            val tag = r.u8()
            val len = r.varint()
            val start = r.pos
            if (len < 0 || start + len > raw.size) throw CodecException("Rekord wykracza poza dane")
            val sub = ByteReader(raw, start, start + len)
            when (tag) {
                TAG_STROKE -> list.add(readStroke(sub))
                TAG_TEXT -> list.add(readText(sub))
                TAG_IMAGE -> list.add(readImage(sub))
                TAG_TAPE -> list.add(readTape(sub))
                TAG_FILL -> list.add(readFill(sub))
            }
            r.pos = start + len
        }
        return PageContent.of(list)
    }

    private fun writeStroke(w: ByteWriter, s: Stroke) {
        w.u8(s.pen.code)
        w.i32(s.color)
        val line = when (s.line) { LineStyle.DASHED -> FLAG_DASHED; LineStyle.DOTTED -> FLAG_DOTTED; LineStyle.SOLID -> 0 }
        val ctrl = s.ctrl
        val memo = if (ctrl == null) s.memo else null
        w.varint((if (s.locked) FLAG_LOCKED else 0) or (if (s.poly) FLAG_POLY else 0) or (if (s.ellipse) FLAG_ELLIPSE else 0) or line or (if (ctrl != null) FLAG_BEZIER else 0) or (if (s.fill != 0) FLAG_STROKE_FILL else 0) or (if (memo != null) FLAG_CURVE_MEMO else 0))
        w.varint((s.width * Q).roundToInt().coerceAtLeast(1))
        val n = s.count
        w.varint(n)
        var px = 0; var py = 0
        val p = s.pts
        for (i in 0 until n) {
            val x = (p[3 * i] * Q).roundToInt()
            val y = (p[3 * i + 1] * Q).roundToInt()
            w.zigzag(x - px)
            w.zigzag(y - py)
            w.u8((p[3 * i + 2].coerceIn(0f, 1f) * 255f).roundToInt())
            px = x; py = y
        }
        if (ctrl != null) writeCtrl(w, ctrl)
        if (s.fill != 0) w.i32(s.fill)
        if (memo != null) writeCtrl(w, memo)
    }

    private fun writeCtrl(w: ByteWriter, ctrl: FloatArray) {
        w.varint(ctrl.size / 2)
        var cx = 0; var cy = 0
        for (i in 0 until ctrl.size / 2) {
            val x = (ctrl[2 * i] * Q).roundToInt(); val y = (ctrl[2 * i + 1] * Q).roundToInt()
            w.zigzag(x - cx); w.zigzag(y - cy)
            cx = x; cy = y
        }
    }

    private fun readCtrl(r: ByteReader): FloatArray {
        val m = r.varint()
        if (m < 0 || m > r.remaining) throw CodecException("Zła liczba punktów kontrolnych")
        val c = FloatArray(2 * m)
        var cx = 0; var cy = 0
        for (i in 0 until m) { cx += r.zigzag(); cy += r.zigzag(); c[2 * i] = cx / Q; c[2 * i + 1] = cy / Q }
        return c
    }

    private fun readStroke(r: ByteReader): Stroke {
        val pen = PenKind.fromCode(r.u8())
        val color = r.i32()
        val flags = r.varint()
        val width = r.varint() / Q
        val n = r.varint()
        if (n < 0 || n > r.remaining) throw CodecException("Zła liczba punktów")
        val pts = FloatArray(n * 3)
        var x = 0; var y = 0
        for (i in 0 until n) {
            x += r.zigzag()
            y += r.zigzag()
            pts[3 * i] = x / Q
            pts[3 * i + 1] = y / Q
            pts[3 * i + 2] = r.u8() / 255f
        }
        val line = if (flags and FLAG_DASHED != 0) LineStyle.DASHED else if (flags and FLAG_DOTTED != 0) LineStyle.DOTTED else LineStyle.SOLID
        val ctrl = if (flags and FLAG_BEZIER != 0) readCtrl(r) else null
        val fill = if (flags and FLAG_STROKE_FILL != 0 && r.remaining >= 4) r.i32() else 0
        val memo = if (ctrl == null && flags and FLAG_CURVE_MEMO != 0 && r.remaining > 0) readCtrl(r) else null
        return Stroke(Ids.next(), pen, color, width, pts, flags and FLAG_LOCKED != 0, flags and FLAG_POLY != 0, flags and FLAG_ELLIPSE != 0, line, ctrl, fill, memo)
    }

    private fun writeText(w: ByteWriter, t: TextBox) {
        val fit = when (t.fit) { TextBox.FIT_AUTO -> FLAG_TEXT_AUTO; TextBox.FIT_FIXED -> FLAG_TEXT_FIXED; else -> 0 }
        val lines = t.lineSpacing != 1f
        val stretched = t.stretch != 1f
        w.varint((if (t.locked) FLAG_LOCKED else 0) or fit or (if (lines) FLAG_TEXT_LINES else 0) or (if (t.mirror) FLAG_MIRROR else 0) or
            (if (stretched) FLAG_TEXT_STRETCH else 0))
        w.f32(t.cx); w.f32(t.cy); w.f32(t.w); w.f32(t.h); w.f32(t.rot)
        w.f32(t.size)
        w.i32(t.color)
        w.u8(t.family); w.u8(t.style); w.u8(t.align)
        w.i32(t.fill)
        w.string(t.text)
        if (lines) w.f32(t.lineSpacing)
        if (stretched) w.f32(t.stretch)
    }

    private fun readText(r: ByteReader): TextBox {
        val flags = r.varint()
        val cx = r.f32(); val cy = r.f32(); val w = r.f32(); val h = r.f32(); val rot = r.f32()
        val size = r.f32()
        val color = r.i32()
        val family = r.u8(); val style = r.u8(); val align = r.u8()
        val fill = r.i32()
        val text = r.string()
        val lineSpacing = if (flags and FLAG_TEXT_LINES != 0) r.f32().coerceIn(0.5f, 4f) else 1f
        val stretch = if (flags and FLAG_TEXT_STRETCH != 0 && r.remaining >= 4) r.f32().takeIf { it.isFinite() }?.coerceIn(TextBox.MIN_STRETCH, TextBox.MAX_STRETCH) ?: 1f else 1f
        val fit = when { flags and FLAG_TEXT_AUTO != 0 -> TextBox.FIT_AUTO; flags and FLAG_TEXT_FIXED != 0 -> TextBox.FIT_FIXED; else -> TextBox.FIT_HEIGHT }
        return TextBox(Ids.next(), cx, cy, w, h, rot, text, size, color, family, style, align, fill, flags and FLAG_LOCKED != 0, fit, lineSpacing, flags and FLAG_MIRROR != 0, stretch)
    }

    private fun writeImage(w: ByteWriter, im: ImageEl) {
        w.varint((if (im.locked) FLAG_LOCKED else 0) or (if (im.crop.isFull) 0 else FLAG_IMAGE_CROP) or (if (im.crop.a != 0f) FLAG_IMAGE_TILT else 0) or (if (im.fx.isNone) 0 else FLAG_IMAGE_FX) or (if (im.mirror) FLAG_MIRROR else 0))
        w.f32(im.cx); w.f32(im.cy); w.f32(im.w); w.f32(im.h); w.f32(im.rot)
        w.u8((im.alpha.coerceIn(0f, 1f) * 255f).roundToInt())
        w.string(im.asset)
        if (!im.crop.isFull) { w.f32(im.crop.l); w.f32(im.crop.t); w.f32(im.crop.r); w.f32(im.crop.b); if (im.crop.a != 0f) w.f32(im.crop.a) }
        if (!im.fx.isNone) {
            val fx = im.fx
            w.u8((if (fx.invert) 1 else 0) or (fx.mode.coerceIn(0, 2) shl 1))
            w.u8((fx.threshold.coerceIn(0f, 1f) * 255f).roundToInt())
            for (gain in floatArrayOf(fx.r, fx.g, fx.b)) w.u8((gain.coerceIn(0f, ImageFx.GAIN_MAX) * 100f).roundToInt())
        }
    }

    private fun readImage(r: ByteReader): ImageEl {
        val flags = r.varint()
        val cx = r.f32(); val cy = r.f32(); val w = r.f32(); val h = r.f32(); val rot = r.f32()
        val alpha = r.u8() / 255f
        val asset = r.string()
        val crop = if (flags and FLAG_IMAGE_CROP != 0) {
            // z kątem nieobrócony prostokąt może wystawać poza plik, więc nie obcinamy go do 0..1
            val tilted = flags and FLAG_IMAGE_TILT != 0
            val lo = if (tilted) -64f else 0f; val hi = if (tilted) 65f else 1f
            val l = r.f32().coerceIn(lo, hi); val t = r.f32().coerceIn(lo, hi)
            val rr = r.f32().coerceIn(lo, hi); val b = r.f32().coerceIn(lo, hi)
            val a = if (tilted) r.f32().takeIf { it.isFinite() } ?: 0f else 0f
            if (rr > l && b > t) ImageCrop(l, t, rr, b, a) else ImageCrop.FULL
        } else ImageCrop.FULL
        val fx = if (flags and FLAG_IMAGE_FX != 0) {
            val bits = r.u8()
            val threshold = r.u8() / 255f
            val gr = r.u8() / 100f; val gg = r.u8() / 100f; val gb = r.u8() / 100f
            ImageFx(bits and 1 != 0, ((bits shr 1) and 3).coerceAtMost(2), threshold, gr.coerceAtMost(ImageFx.GAIN_MAX), gg.coerceAtMost(ImageFx.GAIN_MAX), gb.coerceAtMost(ImageFx.GAIN_MAX))
        } else ImageFx.NONE
        return ImageEl(Ids.next(), asset, cx, cy, w, h, rot, alpha, flags and FLAG_LOCKED != 0, crop, fx, flags and FLAG_MIRROR != 0)
    }

    private fun writeTape(w: ByteWriter, t: TapeEl) {
        w.varint(if (t.locked) FLAG_LOCKED else 0)
        w.f32(t.cx); w.f32(t.cy); w.f32(t.w); w.f32(t.h); w.f32(t.rot)
        w.i32(t.color)
        w.u8(if (t.hidden) 1 else 0)
    }

    private fun readTape(r: ByteReader): TapeEl {
        val flags = r.varint()
        val cx = r.f32(); val cy = r.f32(); val w = r.f32(); val h = r.f32(); val rot = r.f32()
        val color = r.i32()
        val hidden = r.u8() != 0
        return TapeEl(Ids.next(), cx, cy, w, h, rot, color, hidden, flags and FLAG_LOCKED != 0)
    }

    private fun writeFill(w: ByteWriter, f: FillEl) {
        w.varint((if (f.locked) FLAG_LOCKED else 0) or (if (f.hl) FLAG_MULTIPLY else 0))
        w.i32(f.color)
        w.varint(f.rings.size)
        var px = 0; var py = 0
        for (ring in f.rings) {
            val n = ring.size / 2
            w.varint(n)
            for (i in 0 until n) {
                val x = (ring[2 * i] * Q).roundToInt()
                val y = (ring[2 * i + 1] * Q).roundToInt()
                w.zigzag(x - px); w.zigzag(y - py)
                px = x; py = y
            }
        }
    }

    private fun readFill(r: ByteReader): FillEl {
        val flags = r.varint()
        val color = r.i32()
        val count = r.varint()
        if (count < 0 || count > r.remaining) throw CodecException("Zła liczba konturów")
        val rings = ArrayList<FloatArray>(count)
        var x = 0; var y = 0
        repeat(count) {
            val n = r.varint()
            if (n < 0 || n > r.remaining) throw CodecException("Zła liczba punktów")
            val ring = FloatArray(n * 2)
            for (i in 0 until n) {
                x += r.zigzag(); y += r.zigzag()
                ring[2 * i] = x / Q; ring[2 * i + 1] = y / Q
            }
            rings.add(ring)
        }
        return FillEl(Ids.next(), color, flags and FLAG_MULTIPLY != 0, rings, flags and FLAG_LOCKED != 0)
    }

    private fun deflate(src: ByteArray, len: Int): ByteArray {
        val d = Deflater(Deflater.BEST_SPEED)
        try {
            d.setInput(src, 0, len)
            d.finish()
            val out = ByteArrayOutputStream(len / 2 + 32)
            val tmp = ByteArray(16 * 1024)
            while (!d.finished()) {
                val n = d.deflate(tmp)
                out.write(tmp, 0, n)
            }
            return out.toByteArray()
        } finally {
            d.end()
        }
    }

    private fun inflate(src: ByteArray, off: Int, len: Int, rawLen: Int): ByteArray {
        // deflate rozpakowuje najwyżej ~1032 razy więcej, niż ma na wejściu: większa obietnica to plik wrogi albo
        // uszkodzony, a bufor poniżej przydzielamy z góry (bez tego 20 bajtów zajmowało 256 MB)
        if (rawLen < 0 || rawLen > (1 shl 28) || rawLen > len.toLong() * 1032 + 1024) throw CodecException("Zła długość danych")
        val inf = Inflater()
        try {
            inf.setInput(src, off, len)
            val out = ByteArray(rawLen)
            var pos = 0
            while (pos < rawLen) {
                val n = inf.inflate(out, pos, rawLen - pos)
                if (n == 0 && (inf.finished() || inf.needsInput() || inf.needsDictionary())) break
                pos += n
            }
            if (pos != rawLen) throw CodecException("Dane skompresowane są niekompletne")
            return out
        } catch (e: DataFormatException) {
            throw CodecException("Uszkodzone dane skompresowane", e)
        } finally {
            inf.end()
        }
    }
}
