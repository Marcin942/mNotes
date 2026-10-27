package com.mdev.mnotes.app

import com.mdev.mnotes.lab.Lab

/** Wariant laboratorium: kreda, rozpoznawanie, Nearby. Ta sama klasa w `src/core/` zwraca null. */
object Flavor {
    const val NAME = "lab"
    val lab: LabFeatures? = Lab
}
