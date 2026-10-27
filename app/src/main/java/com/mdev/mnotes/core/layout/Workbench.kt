package com.mdev.mnotes.core.layout

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Stanowisko (2b-A7, zastępuje dwa płótna F4): kilka dokumentów na jednym polu roboczym. Każdy dokument to rząd stron
 * (strony obok siebie), rzędy leżą jeden pod drugim, przewijają się razem w pionie, każdy osobno w poziomie, przy wspólnym
 * powiększeniu. Tu czysta geometria pola (px) i zapis stanowisk; rysowanie robi widok każdego rzędu.
 */
object Workbench {
    /** Wysokość rzędu w px: najwyższa strona [maxPageH] (pt) × [zoom] (px/pt) i margines [margin] u góry i u dołu. */
    fun rowHeight(maxPageH: Float, zoom: Float, margin: Float): Float = maxPageH * zoom + 2f * margin

    /** Położenia rzędów na polu: [tops] i [heights] w px, przerwa [gap] między rzędami. */
    class Geometry(val tops: FloatArray, val heights: FloatArray, val gap: Float) {
        val size: Int get() = tops.size
        val total: Float get() = if (size == 0) 0f else tops[size - 1] + heights[size - 1]

        /** Rząd pod punktem pola [y] albo -1 (przerwa, poza polem). */
        fun rowAt(y: Float): Int {
            for (i in 0 until size) if (y >= tops[i] && y < tops[i] + heights[i]) return i
            return -1
        }

        /** Rząd najbliższy punktowi pola [y] (przerwa należy do bliższego rzędu); -1 dla pustego pola. */
        fun nearest(y: Float): Int {
            var best = -1; var bd = Float.MAX_VALUE
            for (i in 0 until size) {
                val d = if (y < tops[i]) tops[i] - y else if (y > tops[i] + heights[i]) y - tops[i] - heights[i] else 0f
                if (d < bd) { bd = d; best = i }
            }
            return best
        }

        /**
         * Widoczny pas rzędu [i] w jego własnych współrzędnych (od, do) przy przewinięciu [scroll] i wysokości okna [view];
         * null, gdy rząd jest poza ekranem. Rząd rysuje i zamawia kafelki tylko dla tego pasa.
         */
        fun band(i: Int, scroll: Float, view: Float): FloatArray? {
            val a = max(0f, scroll - tops[i]); val b = min(heights[i], scroll + view - tops[i])
            return if (b <= a) null else floatArrayOf(a, b)
        }
    }

    fun geometry(heights: List<Float>, gap: Float): Geometry {
        val tops = FloatArray(heights.size)
        var y = 0f
        for (i in heights.indices) { tops[i] = y; y += heights[i] + gap }
        return Geometry(tops, heights.toFloatArray(), gap)
    }

    fun clampScroll(scroll: Float, total: Float, view: Float): Float = scroll.coerceIn(0f, max(0f, total - view))

    /**
     * Gdzie na ekranie (y względem okna pola, [screenY]) leży punkt: rząd i y w jego współrzędnych; przerwa należy do
     * najbliższego rzędu (upuszczenie zaznaczenia tuż pod rzędem trafia do niego). Null dla pustego pola.
     */
    fun locate(g: Geometry, screenY: Float, scroll: Float): Pair<Int, Float>? {
        val y = screenY + scroll
        val i = g.nearest(y)
        if (i < 0) return null
        return i to (y - g.tops[i]).coerceIn(0f, g.heights[i])
    }

    /**
     * Przewinięcie po zmianie wysokości rzędów (powiększenie): punkt pola, który był pod [anchorY] ekranu, zostaje pod nim —
     * ten sam rząd, ten sam ułamek jego wysokości.
     */
    fun keepAnchor(old: Geometry, new: Geometry, scroll: Float, anchorY: Float): Float {
        val y = scroll + anchorY
        val i = old.nearest(y)
        if (i < 0 || i >= new.size) return scroll
        val f = ((y - old.tops[i]) / old.heights[i].coerceAtLeast(1f)).coerceIn(0f, 1f)
        return new.tops[i] + f * new.heights[i] - anchorY
    }

    /** Zapisane stanowisko: nazwa, dokumenty od góry, przewinięcie (px) i powiększenie (0 = jak przy otwarciu). */
    data class Station(val name: String, val ids: List<String>, val scroll: Float = 0f, val zoom: Float = 0f) {
        fun json(): JSONObject = JSONObject().put("n", name).put("ids", JSONArray(ids)).put("s", scroll.toDouble()).put("z", zoom.toDouble())

        companion object {
            fun of(o: JSONObject): Station? {
                val a = o.optJSONArray("ids") ?: return null
                val ids = (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.distinct()
                if (ids.isEmpty()) return null
                return Station(o.optString("n", ""), ids, o.optDouble("s", 0.0).toFloat(), o.optDouble("z", 0.0).toFloat())
            }
        }
    }

    fun encode(list: List<Station>): String = JSONArray(list.map { it.json() }).toString()

    /** Stanowiska z zapisu; uszkodzony zapis albo nieznane pola nie psują reszty. */
    fun decode(s: String): List<Station> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Station::of) }
    }.getOrDefault(emptyList())

    /** Lista po zapisaniu [s]: stanowisko o tej samej nazwie jest zastępowane, nowe trafia na początek. */
    fun upsert(list: List<Station>, s: Station): List<Station> = listOf(s) + list.filter { it.name != s.name }

    /** Dokumenty stanowiska bez tych, których już nie ma (usunięte, w koszu) — [exists] sprawdza id. */
    fun alive(s: Station, exists: (String) -> Boolean): Station = s.copy(ids = s.ids.filter(exists))
}
