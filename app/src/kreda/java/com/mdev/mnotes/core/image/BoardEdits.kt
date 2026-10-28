// Algorytmy kredy na podstawie programu kreda — autor i licencja do potwierdzenia przed publikacją (app/src/kreda/NOTICE.md).

package com.mdev.mnotes.core.image

/**
 * Ręczne poprawki w panelu „Oczyść tablicę” (2c-B5): narożniki tablicy i pętle gumki. Pętle są w ułamkach **wyniku**,
 * a wynik zależy od geometrii (narożniki, prostowanie, kadr), więc po jej zmianie stare pętle trafiałyby w inną kredę
 * niż ta, którą użytkownik obwiódł. Każda zmiana geometrii zeruje gumkę; ta sama geometria ją zostawia.
 * [corners]: ułamki zdjęcia (lewy górny, prawy górny, prawy dolny, lewy dolny); null = zgadnięte.
 */
class BoardEdits private constructor(
    val corners: FloatArray?,
    val straighten: Boolean,
    val crop: Boolean,
    val erase: List<FloatArray>,
) {
    constructor(straighten: Boolean, crop: Boolean) : this(null, straighten, crop, emptyList())

    /** Nowa geometria; gumka zostaje tylko wtedy, gdy nic się nie zmieniło. */
    fun withGeometry(corners: FloatArray?, straighten: Boolean = this.straighten, crop: Boolean = this.crop): BoardEdits {
        val same = straighten == this.straighten && crop == this.crop &&
            (corners == null && this.corners == null || corners != null && this.corners != null && corners.contentEquals(this.corners))
        return if (same) this else BoardEdits(corners?.copyOf(), straighten, crop, emptyList())
    }

    fun withLoop(loop: FloatArray) = BoardEdits(corners, straighten, crop, erase + listOf(loop))

    fun undoLoop() = BoardEdits(corners, straighten, crop, erase.dropLast(1))

    fun clearLoops() = BoardEdits(corners, straighten, crop, emptyList())
}
