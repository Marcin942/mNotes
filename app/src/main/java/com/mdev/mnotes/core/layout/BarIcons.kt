package com.mdev.mnotes.core.layout

import com.mdev.mnotes.core.model.PenKind

/**
 * Czym jest narysowana ikona pozycji paska (2b-A1): pióra, gumka, lasso, krzywa i kątomierz mają własne glify w stylu
 * piór, reszta — ikony Material. Ten sam wybór na pasku i w Ustawieniach → układ paska (dawniej tam lasso i krzywa
 * miały inne ikony niż na pasku).
 */
object BarIcons {
    enum class Kind { PEN, ERASER, LASSO, CURVE, PROTRACTOR, VECTOR }

    /** Pozycje z ikoną Material. */
    val VECTOR_IDS = setOf("undo", "redo", "painter", "fill", "pipette", "text", "laser", "hand", "tape", "space", "ruler", "geometry")

    fun kind(id: String): Kind? = when {
        PenKind.entries.any { it.name.lowercase() == id } -> Kind.PEN
        id == "eraser" -> Kind.ERASER
        id == "lasso" -> Kind.LASSO
        id == "curve" -> Kind.CURVE
        id == "protractor" -> Kind.PROTRACTOR
        id in VECTOR_IDS -> Kind.VECTOR
        else -> null
    }
}
