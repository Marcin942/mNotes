package com.mdev.mnotes.app

import com.mdev.mnotes.lab.PhoneLink

/** Wariant z Usługami Google Play: łącze z telefonem przez Nearby; kreda tylko, gdy kompilacja ma `src/kreda/`. */
object Flavor {
    const val NAME = "gms"
    val lab: LabFeatures? = Extensions.of(PhoneLink)
}
