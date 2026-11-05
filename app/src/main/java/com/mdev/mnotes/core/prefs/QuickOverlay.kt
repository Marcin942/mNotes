package com.mdev.mnotes.core.prefs

/**
 * LP-6: wybory z szybkiego menu narzędzia (gumka, lasso, laser, wycinek), które obowiązują tylko do odłożenia narzędzia.
 * Leżą nad zapisanymi ustawieniami jako nadpisania po kluczu; odczyt ustawienia bierze nadpisanie zamiast zapisanej
 * wartości, a zmiana narzędzia czyści całość, więc wraca domyślne. Zapis domyślnej wartości (pełny panel, Ustawienia)
 * zdejmuje nadpisanie tego klucza, żeby nie zasłaniało świeżo wybranego domyślnego.
 */
data class QuickOverlay(val values: Map<String, Any> = emptyMap()) {

    val isEmpty: Boolean get() = values.isEmpty()

    operator fun contains(key: String): Boolean = key in values

    /** Wartość ustawienia [key]: nadpisanie, gdy jest tego samego typu co [saved]; inaczej zapisana. */
    @Suppress("UNCHECKED_CAST")
    fun <T> read(key: String, saved: T): T {
        val v = values[key] ?: return saved
        return if (saved != null && v::class == (saved as Any)::class) v as T else saved
    }

    /**
     * Wybór [value] z szybkiego menu. Wybór równy zapisanej wartości [saved] zdejmuje nadpisanie (to już domyślne),
     * inny typ niż [saved] jest pomijany.
     */
    fun pick(key: String, value: Any, saved: Any?): QuickOverlay = when {
        value == saved -> without(key)
        saved != null && value::class != saved::class -> this
        else -> QuickOverlay(values + (key to value))
    }

    fun without(key: String): QuickOverlay = if (key in values) QuickOverlay(values - key) else this

    companion object {
        val EMPTY = QuickOverlay()
    }
}
