package com.mdev.mnotes.core.prefs

/**
 * Wartość ustawienia z pliku (import ustawień) albo z pamięci w innym typie niż ustawienie. Androidowy org.json zapisuje
 * całkowitą liczbę zmiennoprzecinkową bez „.0” (20f → `20`), więc po wczytaniu wraca jako Int; zapisana tak liczba
 * w miejscu Float wywracała aplikację przy każdym starcie (`getFloat` na wartości Int).
 */
object PrefValue {
    /**
     * [v] w typie domyślnej wartości [def]; null, gdy się nie da (wartość pomijana). Nieznany klucz ([def] == null):
     * typ z pliku, liczba z częścią ułamkową jako Float.
     */
    fun coerce(def: Any?, v: Any?): Any? = when (def) {
        null -> when (v) { is Int, is Long, is Boolean, is String -> v; is Double -> v.toFloat(); is Float -> v; else -> null }
        is Float -> (v as? Number)?.toFloat()
        is Int -> (v as? Number)?.takeIf { it.toDouble() == it.toInt().toDouble() }?.toInt()
        is Long -> (v as? Number)?.takeIf { it.toDouble() == it.toLong().toDouble() }?.toLong()
        is Boolean -> v as? Boolean
        is String -> v as? String
        else -> null
    }
}
