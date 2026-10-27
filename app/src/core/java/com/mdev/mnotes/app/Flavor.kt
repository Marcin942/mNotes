package com.mdev.mnotes.app

/** Wariant otwarty: bez funkcji laboratorium. Ta sama klasa w `src/lab/` zwraca prawdziwe [LabFeatures]. */
object Flavor {
    const val NAME = "core"
    val lab: LabFeatures? = null
}
