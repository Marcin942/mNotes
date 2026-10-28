package com.mdev.mnotes.core.layout

/**
 * 3c-A13: jedno ustawienie „Piszę prawą/lewą ręką”. Wszystko, co stoi po którejś stronie ekranu, ma domyślne położenie
 * dobrane pod prawą rękę (listwa pióra z lewej, z dala od dłoni); zmiana ręki odbija te położenia lustrzanie — także
 * ustawione ręcznie, bo ktoś, kto przestawił listwę na prawo, po zmianie ręki chce jej po drugiej stronie.
 * Góra/dół i środek zostają. Leworęczny dostaje też zapas przewijania (3c-A4), żeby odsunąć początek kartki spod dłoni.
 */
object Handedness {
    const val RIGHT = 0
    const val LEFT = 1

    /** Położenia zależne od strony: pasek (0 góra, 1 dół, 2 lewo, 3 prawo), listwa (0 lewo, 1 prawo, 2 przy pasku),
     *  kapsuła Cofnij (0 na pasku, 1–4 rogi: lewy górny, prawy górny, lewy dolny, prawy dolny), róg mini-mapy ([MiniMap]). */
    data class Sides(val toolbar: Int, val options: Int, val undoDock: Int, val miniMap: Int, val scrollMargin: Boolean)

    fun toolbar(p: Int) = when (p) { 2 -> 3; 3 -> 2; else -> p }
    fun options(o: Int) = when (o) { 0 -> 1; 1 -> 0; else -> o }
    fun undoDock(d: Int) = when (d) { 1 -> 2; 2 -> 1; 3 -> 4; 4 -> 3; else -> d }
    fun miniMap(c: Int) = when (c) {
        MiniMap.BOTTOM_RIGHT -> MiniMap.BOTTOM_LEFT; MiniMap.BOTTOM_LEFT -> MiniMap.BOTTOM_RIGHT
        MiniMap.TOP_RIGHT -> MiniMap.TOP_LEFT; MiniMap.TOP_LEFT -> MiniMap.TOP_RIGHT
        else -> c
    }

    /** Tak długo po ostatnim dotyku rysika palec po stronie dłoni jest jeszcze traktowany jako dłoń (ms). */
    const val PALM_SIDE_MS = 1000L

    /**
     * Odrzucanie dłoni po stronie ręki: piszący opiera dłoń obok i poniżej rysika, po stronie ręki. Dłoń, która dotknie
     * ekranu chwilę po podniesieniu rysika (zwykłe odrzucanie trwa ok. 0,5 s), pada w tę stronę; palec z drugiej strony
     * (przewijanie drugą ręką) — nie. ([px], [py]) dotyk, ([sx], [sy]) ostatni punkt rysika, [dp] gęstość ekranu.
     */
    fun palmSide(hand: Int, px: Float, py: Float, sx: Float, sy: Float, dp: Float): Boolean {
        val toHand = if (hand == LEFT) sx - px else px - sx
        return toHand > -20f * dp && py > sy - 60f * dp
    }

    /** Położenia po zmianie ręki z [from] na [to]; ta sama ręka — bez zmian. Zapas przewijania leworęczny dostaje zawsze. */
    fun switch(s: Sides, from: Int, to: Int): Sides {
        if (from == to) return s
        val m = Sides(toolbar(s.toolbar), options(s.options), undoDock(s.undoDock), miniMap(s.miniMap), s.scrollMargin)
        return if (to == LEFT) m.copy(scrollMargin = true) else m
    }
}
