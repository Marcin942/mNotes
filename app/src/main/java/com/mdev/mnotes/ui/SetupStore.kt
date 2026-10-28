package com.mdev.mnotes.ui

import com.mdev.mnotes.core.guide.SetupGuide
import com.mdev.mnotes.data.Settings

/**
 * 3c-A10: ustawienia widziane przez przewodnik ([SetupGuide.Store]) — te same pola co w Ustawieniach, więc wybór na
 * stronie przewodnika jest zwykłym ustawieniem i działa od razu. Nieznany klucz to błąd przy pisaniu przewodnika
 * (pilnuje go test na źródle), więc tutaj po cichu nic nie robi.
 */
class SetupStore(private val s: Settings) : SetupGuide.Store {
    private fun b(v: Boolean) = if (v) 1 else 0

    override fun get(key: String): Int = when (key) {
        SetupGuide.USE -> s.setupUse
        "input.hand" -> s.hand
        "input.finger" -> s.fingerMode
        "new.template" -> s.newTemplate
        "ui.theme" -> s.theme
        "pages.theme" -> s.pageTheme
        "toolbar.position" -> s.toolbarPos
        "toolbar.size" -> s.toolbarSize
        "input.hold_to_shape" -> b(s.holdToShape)
        // Wygładzanie jest mnożnikiem 0..2; przewodnik pokazuje trzy stopnie.
        "pen.smoothing" -> when { s.smoothing < 0.75f -> 0; s.smoothing < 1.3f -> 1; else -> 2 }
        "eraser.mode" -> s.eraserMode
        "lasso.touch" -> b(s.lassoTouch)
        "input.double_tap" -> s.doubleTap
        "input.two_finger_undo" -> b(s.twoFingerUndo)
        "input.pen_button" -> s.penButton
        "text.math_unicode" -> b(s.mathText)
        "construct.names" -> b(s.constructNames)
        "view.scroll_margin" -> b(s.scrollMargin)
        "view.minimap" -> b(s.miniMap)
        "ui.tool_hints" -> b(s.toolHints)
        "ui.tooltips" -> b(s.tooltips)
        "ui.haptics" -> b(s.haptics)
        else -> 0
    }

    override fun set(key: String, v: Int) {
        when (key) {
            SetupGuide.USE -> s.setupUse = v
            "input.hand" -> s.hand = v
            "input.finger" -> s.fingerMode = v
            "new.template" -> s.newTemplate = v
            "ui.theme" -> s.theme = v
            "pages.theme" -> s.pageTheme = v
            "toolbar.position" -> s.toolbarPos = v
            "toolbar.size" -> s.toolbarSize = v
            "input.hold_to_shape" -> s.holdToShape = v != 0
            "pen.smoothing" -> s.smoothing = when (v) { 0 -> 0.5f; 1 -> 1f; else -> 1.6f }
            "eraser.mode" -> s.eraserMode = v
            "lasso.touch" -> s.lassoTouch = v != 0
            "input.double_tap" -> s.doubleTap = v
            "input.two_finger_undo" -> s.twoFingerUndo = v != 0
            "input.pen_button" -> s.penButton = v
            "text.math_unicode" -> s.mathText = v != 0
            "construct.names" -> s.constructNames = v != 0
            "view.scroll_margin" -> s.scrollMargin = v != 0
            "view.minimap" -> s.miniMap = v != 0
            "ui.tool_hints" -> s.toolHints = v != 0
            "ui.tooltips" -> s.tooltips = v != 0
            "ui.haptics" -> s.haptics = v != 0
        }
    }
}
