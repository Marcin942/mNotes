package com.mdev.mnotes.core

import com.mdev.mnotes.core.pen.SliderStep
import org.junit.Assert.assertEquals
import org.junit.Test

class SliderStepTest {
    @Test fun wholeSteps() {
        assertEquals(2f, SliderStep.next(1f, 1, 1f, 1f, 60f), 0f)
        assertEquals(1f, SliderStep.next(2f, -1, 1f, 1f, 60f), 0f)
    }

    @Test fun clampsToRange() {
        assertEquals(1f, SliderStep.next(1f, -1, 1f, 1f, 60f), 0f)
        assertEquals(60f, SliderStep.next(60f, 1, 1f, 1f, 60f), 0f)
        assertEquals(0.3f, SliderStep.next(0.35f, -1, 0.1f, 0.3f, 24f), 1e-6f)
    }

    @Test fun offGridGoesToNearestNodeInDirection() {
        assertEquals(2.4f, SliderStep.next(2.37f, 1, 0.1f, 0.3f, 24f), 1e-6f)
        assertEquals(2.3f, SliderStep.next(2.37f, -1, 0.1f, 0.3f, 24f), 1e-6f)
        assertEquals(13f, SliderStep.next(12.4f, 1, 1f, 1f, 60f), 0f)
    }

    @Test fun tenthStepsHaveNoFloatTail() {
        var v = 0.3f
        repeat(7) { v = SliderStep.next(v, 1, 0.1f, 0.3f, 24f) }
        assertEquals(1.0f, v, 0f)
    }
}

class PageToneManualTest {
    private val white = com.mdev.mnotes.core.model.PageSpec()
    private val P = com.mdev.mnotes.core.model.Paper
    private val tone = com.mdev.mnotes.core.color.PageTone

    @Test fun lightToDarkUsesMeasuredDarkPaper() {
        val s = tone.withTone(white, true, 0xFF102030.toInt())
        assertEquals(0xFF102030.toInt(), s.paper)
        assertEquals(true, s.darkPaper)
    }

    @Test fun lightMeasurementFallsBackToDarkPreset() {
        assertEquals(P.DARK, tone.withTone(white, true, 0xFFEEEEEE.toInt()).paper)
        assertEquals(P.DARK, tone.withTone(white, true).paper)
    }

    @Test fun darkToLightIsWhiteAndSameToneUnchanged() {
        val dark = white.copy(paper = P.BLACK)
        assertEquals(P.WHITE, tone.withTone(dark, false).paper)
        assertEquals(P.autoLineColor(P.WHITE), tone.withTone(dark, false).line)
        val ivory = white.copy(paper = P.IVORY)
        assertEquals(ivory, tone.withTone(ivory, false))
    }
}
