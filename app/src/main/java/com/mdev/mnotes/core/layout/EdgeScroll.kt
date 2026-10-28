package com.mdev.mnotes.core.layout

/** Autoprzewijanie przy krawędzi okna podczas przeciągania (2c-A7). */
object EdgeScroll {
    /**
     * Ułamek najwyższej prędkości dla palca w [at] na odcinku [0, [len]] z pasem [band] przy obu końcach: rośnie
     * liniowo od 0 na granicy pasa do ±1 na krawędzi. Dodatni — treść jedzie w stronę rosnących współrzędnych
     * (odsłania to, co przed początkiem okna).
     */
    fun factor(at: Float, len: Float, band: Float): Float = when {
        band <= 0f || len <= 2f * band -> 0f
        at < band -> ((band - at) / band).coerceAtMost(1f)
        at > len - band -> -((at - (len - band)) / band).coerceAtMost(1f)
        else -> 0f
    }
}
