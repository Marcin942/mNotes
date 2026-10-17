package com.mdev.mnotes.core.samsung

/**
 * Zakres formatowania tekstu płynącego: [kind] 1 = kolor (ARGB), 3 = rozmiar w dp (f32); inne rodzaje czytnik zachowuje
 * bez znaczenia. Zakres to znaki [start, end) w [SdFlow.text].
 */
class SdSpan(val kind: Int, val start: Int, val end: Int, val value: ByteArray) {
    fun intValue(): Int? = if (value.size >= 4) LeReader(value).i32() else null
    fun floatValue(): Float? = if (value.size >= 4) LeReader(value).f32() else null
}

/** Obraz wstawiony w tekst (znak U+FFFC): [image] niesie prostokąt na stronie i numer pliku. */
class SdInlineImage(val image: SdImage)

/**
 * Tekst płynący notatki (zapisany w `note.note`, nie na stronach): jeden ciąg znaków dla całego dokumentu, jak w edytorze
 * tekstu. [pageEnds] to zapisane przez Samsunga końce stron (indeks ostatniego znaku strony, włącznie), więc podział na
 * strony nie wymaga liczenia. [margins] to lewy, górny, prawy i dolny margines w dp.
 */
class SdFlow(
    val text: String,
    val spans: List<SdSpan>,
    val margins: FloatArray,
    val pageEnds: IntArray,
    val images: List<SdInlineImage>,
    /** Fałsz, gdy lista obrazów w tekście nie dała się odczytać (tekst jest wtedy czytany, a obrazy pomijane). */
    val imagesComplete: Boolean,
) {
    /** Zakres znaków strony [page] (włącznie); pusty, gdy strona nie ma tekstu. */
    fun pageRange(page: Int): IntRange {
        if (page !in pageEnds.indices) return IntRange.EMPTY
        val start = if (page == 0) 0 else pageEnds[page - 1] + 1
        val end = minOf(pageEnds[page], text.length - 1)
        return if (start > end) IntRange.EMPTY else start..end
    }

    /** Pierwsza wartość zakresu rodzaju [kind] obejmującego znak [index], albo null. */
    fun spanAt(kind: Int, index: Int): SdSpan? {
        var best: SdSpan? = null
        for (s in spans) if (s.kind == kind && index >= s.start && index < s.end) best = s
        return best
    }
}

/**
 * Wyszukuje w `note.note` ramkę tekstu płynącego. Położenie nie jest stałe, więc czytnik skanuje plik i przyjmuje
 * pierwsze miejsce, w którym zgadza się cała struktura: długość ramki, tekst, listy zakresów i akapitów, marginesy,
 * tabela stron i obrazy kończą się dokładnie na końcu ramki.
 */
internal object SdFlowReader {
    private const val MAX_PAGES = 5000
    private const val MAX_RECORDS = 200_000
    private const val MAX_IMAGES = 2000
    private val OBJECT_REPLACEMENT = '￼'

    fun find(note: ByteArray): SdFlow? {
        var o = 0
        while (o + 12 <= note.size) {
            if (plausible(note, o)) {
                val f = attempt(note, o, withImages = true) ?: attempt(note, o, withImages = false)
                if (f != null) return f
            }
            o++
        }
        return null
    }

    private fun attempt(note: ByteArray, o: Int, withImages: Boolean): SdFlow? = try {
        parseAt(note, o, withImages)
    } catch (_: SamsungFormatException) {
        null
    }

    /** Tani test przed pełnym parsowaniem: rozmiar ramki mieści się w pliku i mieści w sobie podaną liczbę znaków. */
    private fun plausible(b: ByteArray, o: Int): Boolean {
        fun u32(i: Int) = (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or
            ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)
        val size = u32(o)
        val chars = u32(o + 4)
        return chars in 1..size / 2 && size >= 8 + 2 * chars + 8 && o + 4 + size <= b.size
    }

    private fun <T> list(r: LeReader, end: Int, minLen: Int, make: (LeReader, Int) -> T): List<T> {
        val n = r.u32()
        if (n > MAX_RECORDS || r.p + n * 2 > end) throw SamsungFormatException("zła lista w tekście płynącym")
        val out = ArrayList<T>(n.toInt())
        repeat(n.toInt()) {
            val len = r.u16()
            if (len < minLen || r.p + len > end) throw SamsungFormatException("zły rekord w tekście płynącym")
            val rr = LeReader(r.b, r.p)
            out.add(make(rr, len))
            r.p += len
        }
        return out
    }

    private fun parseAt(note: ByteArray, o: Int, withImages: Boolean): SdFlow? {
        val r = LeReader(note, o)
        val size = r.u32().toInt()
        val end = o + 4 + size
        val n = r.u32().toInt()
        val text = String(r.raw(2 * n), Charsets.UTF_16LE)

        val spans = list(r, end, 16) { rr, len ->
            val kind = rr.i32(); val s = rr.i32(); val e = rr.i32()
            rr.skip(4)
            if (s < 0 || e < s || e > n + 1) throw SamsungFormatException("zły zakres tekstu")
            SdSpan(kind, s, minOf(e, n), rr.raw(len - 16))
        }
        list(r, end, 12) { rr, _ ->
            val kind = rr.i32(); val s = rr.i32(); val e = rr.i32()
            if (kind < 0 || s < 0 || e < s || e > n + 1) throw SamsungFormatException("zły zakres akapitu")
        }

        val margins = FloatArray(4) { r.f32() }
        if (margins.any { !it.isFinite() || it < 0f || it > 400f }) throw SamsungFormatException("złe marginesy")
        r.u8()
        val pages = r.u32()
        if (pages < 1 || pages > MAX_PAGES) throw SamsungFormatException("zła liczba stron w tekście")
        r.u16()
        val ends = IntArray(pages.toInt())
        // Para (długość, koniec włącznie): niepusta strona kończy się nie wcześniej niż poprzednia, a ostatnia przy końcu
        // tekstu. Pusta strona (długość 0) niesie w polu końca nieistotną wartość.
        var lastEnd = 0L
        var maxEnd = 0L
        for (i in ends.indices) {
            val len = r.u32()
            val e = r.u32()
            if (e > n + 1) throw SamsungFormatException("zły koniec strony tekstu")
            if (len > 0) {
                if (e < lastEnd) throw SamsungFormatException("końce stron tekstu nie rosną")
                lastEnd = e
                maxEnd = maxOf(maxEnd, e)
                ends[i] = e.toInt()
            } else {
                ends[i] = if (i == 0) -1 else ends[i - 1]
            }
        }
        if (maxEnd < n - 1L) throw SamsungFormatException("strony tekstu nie obejmują całego tekstu")
        if (r.p > end) throw SamsungFormatException("tekst płynący wychodzi poza ramkę")

        if (r.p == end) return SdFlow(text, spans, margins, ends, emptyList(), imagesComplete = true)
        if (!withImages) return if (r.p + 8 <= end) SdFlow(text, spans, margins, ends, emptyList(), imagesComplete = false) else null

        // Obrazy w tekście: u32 0, u32 liczba, potem podobiekty [u32 rozmiar][u32][u32 3][ramki obrazu].
        r.u32()
        val cnt = r.u32()
        if (cnt > MAX_IMAGES) throw SamsungFormatException("zbyt wiele obrazów w tekście")
        val images = ArrayList<SdInlineImage>()
        repeat(cnt.toInt()) {
            val at = r.p
            val sz = r.u32()
            if (sz < 12 || at + 4 + sz > end) throw SamsungFormatException("zły obraz w tekście")
            images.add(SdInlineImage(SdObjects.image(note, at + 12)))
            r.p = at + 4 + sz.toInt()
        }
        if (r.p != end) throw SamsungFormatException("tekst płynący nie kończy się na końcu ramki")
        if (images.size != text.count { it == OBJECT_REPLACEMENT }) {
            // liczba obrazów inna niż liczba znaków obiektu: nie wiadomo, który obraz gdzie stoi
            return SdFlow(text, spans, margins, ends, emptyList(), imagesComplete = false)
        }
        return SdFlow(text, spans, margins, ends, images, imagesComplete = true)
    }
}
