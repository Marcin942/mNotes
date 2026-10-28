package com.mdev.mnotes.core.layout

import org.json.JSONArray
import org.json.JSONObject

/**
 * Zapis stanowisk (2b-A7): nazwa, dokumenty i stan widoku. Geometria i ruch stanowiska są w [Workspace] (2d-A1).
 * Format JSON zgodny wstecz: stare pola `n`, `ids`, `s`, `z` bez zmian, nowe (`m`, `o`, `a`, `p`) opcjonalne.
 */
object Workbench {
    /**
     * Zapisane stanowisko: nazwa, dokumenty (pierwszy = główny), przewinięcie wspólne (px ≥ 0) i powiększenie (0 = jak
     * przy otwarciu); 2d-A1: kierunek [mode] (0 kolumny, 1 rzędy, −1 — jak pierwszy dokument), przesunięcia pasów w osi
     * stron [offsets] (px; brak — od początku) i indeks aktywnego pasa [active]; 2c-A11: przypięty profil [profile] ("" — żaden).
     */
    data class Station(
        val name: String, val ids: List<String>, val scroll: Float = 0f, val zoom: Float = 0f,
        val mode: Int = -1, val offsets: List<Float> = emptyList(), val active: Int = 0, val profile: String = "",
    ) {
        fun json(): JSONObject = JSONObject().put("n", name).put("ids", JSONArray(ids)).put("s", scroll.toDouble()).put("z", zoom.toDouble()).apply {
            if (mode >= 0) put("m", mode)
            if (offsets.isNotEmpty()) put("o", JSONArray(offsets.map { it.toDouble() }))
            if (active > 0) put("a", active)
            if (profile.isNotEmpty()) put("p", profile)
        }

        companion object {
            fun of(o: JSONObject): Station? {
                val a = o.optJSONArray("ids") ?: return null
                val ids = (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.distinct()
                if (ids.isEmpty()) return null
                val off = o.optJSONArray("o")?.let { arr -> (0 until arr.length()).map { arr.optDouble(it, 0.0).toFloat() } }.orEmpty()
                val m = o.optInt("m", -1).takeIf { it == 0 || it == 1 } ?: -1
                return Station(o.optString("n", ""), ids, o.optDouble("s", 0.0).toFloat(), o.optDouble("z", 0.0).toFloat(), m, off, o.optInt("a", 0).coerceIn(0, ids.size - 1), o.optString("p", ""))
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
    fun alive(s: Station, exists: (String) -> Boolean): Station {
        // Przesunięcia i aktywny pas idą za dokumentami, które zostały.
        val keep = s.ids.map(exists)
        val ids = s.ids.filterIndexed { i, _ -> keep[i] }
        if (ids.size == s.ids.size) return s
        val off = if (s.offsets.size == s.ids.size) s.offsets.filterIndexed { i, _ -> keep[i] } else emptyList()
        val act = if (keep.getOrElse(s.active) { false }) (0 until s.active).count { keep[it] } else 0
        return s.copy(ids = ids, offsets = off, active = act)
    }
}
