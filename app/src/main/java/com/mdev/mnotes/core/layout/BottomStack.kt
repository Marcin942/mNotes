package com.mdev.mnotes.core.layout

/**
 * Dolne nakładki płótna leżą jedna nad drugą, a nie na sobie (A2-8, przegląd Fable): kapsuła trybu wykładu na dole,
 * nad nią pasek szukania, nad nimi dymek z komunikatem. Wcześniej „Brak aplikacji aparatu” rysował się na kapsule.
 * Wartości w dp, liczone od dolnej krawędzi płótna.
 */
object BottomStack {
    const val CAPSULE_BOTTOM = 16f
    /** Kapsuła: chip 32–36 dp + odstępy karty. */
    const val CAPSULE_H = 48f
    const val SEARCH_BOTTOM = 14f
    /** Pasek szukania: pole 40 dp + odstępy karty. */
    const val SEARCH_H = 50f
    const val TOAST_BOTTOM = 28f
    const val GAP = 8f

    /** Dolne krawędzie: [0] pasek szukania, [1] dymek — przy widocznej kapsule [capsule] i pasku szukania [search]. */
    fun bottoms(capsule: Boolean, search: Boolean): FloatArray {
        val searchAt = if (capsule) CAPSULE_BOTTOM + CAPSULE_H + GAP else SEARCH_BOTTOM
        var toastAt = TOAST_BOTTOM
        if (capsule) toastAt = maxOf(toastAt, CAPSULE_BOTTOM + CAPSULE_H + GAP)
        if (search) toastAt = maxOf(toastAt, searchAt + SEARCH_H + GAP)
        return floatArrayOf(searchAt, toastAt)
    }
}
