package com.mdev.mnotes.app

/** Wariant bez niewolnych bibliotek (F-Droid): bez łącza Nearby; kreda tylko, gdy kompilacja ma `src/kreda/`. */
object Flavor {
    const val NAME = "foss"
    val lab: LabFeatures? = Extensions.of(null)
}
