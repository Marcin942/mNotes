package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PdfRef
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.model.TemplateGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateTest {

    /** Tablica par (x, y) — dla linii i pól to po dwie pary na element. */
    private fun within(arr: FloatArray, w: Float, h: Float) {
        var i = 0
        while (i < arr.size) {
            assertTrue("x=${arr[i]} poza stroną", arr[i] >= -0.5f && arr[i] <= w + 0.5f)
            assertTrue("y=${arr[i + 1]} poza stroną", arr[i + 1] >= -0.5f && arr[i + 1] <= h + 0.5f)
            i += 2
        }
    }

    @Test
    fun everyTemplateStaysInsidePage() {
        for (t in Template.entries) for (fmt in listOf(PageFormat.A4, PageFormat.A6, PageFormat.LEGAL)) {
            val spec = PageSpec.of(fmt, template = t)
            val g = TemplateGeometry.build(spec)
            within(g.lines, spec.w, spec.h)
            within(g.heavy, spec.w, spec.h)
            within(g.dots, spec.w, spec.h)
            within(g.boxes, spec.w, spec.h)
            if (t == Template.BLANK) assertTrue(g.isEmpty) else assertFalse("$t pusty", g.isEmpty)
        }
    }

    @Test
    fun linedHasMarginAndRegularRows() {
        val spec = PageSpec.of(PageFormat.A4, template = Template.LINED)
        val g = TemplateGeometry.build(spec)
        assertEquals(4, g.heavy.size)
        val ys = (0 until g.lines.size / 4).map { g.lines[4 * it + 1] }
        for (i in 1 until ys.size) assertEquals(spec.spacing, ys[i] - ys[i - 1], 1e-3f)
        val noMargin = TemplateGeometry.build(spec.copy(opts = 0))
        assertEquals(0, noMargin.heavy.size)
    }

    @Test
    fun gridIsCenteredAndDotsMatchGrid() {
        val spec = PageSpec.of(PageFormat.A4, template = Template.GRID)
        val g = TemplateGeometry.build(spec)
        val verticals = (0 until g.lines.size / 4).map { g.lines[4 * it] to g.lines[4 * it + 2] }
            .filter { it.first == it.second }.map { it.first }
        assertEquals(verticals.min(), spec.w - verticals.max(), 0.1f)
        val d = TemplateGeometry.build(spec.withTemplate(Template.DOTS))
        assertEquals(0, d.lines.size)
        assertTrue(d.dots.size >= 2 * 40 * 58)
    }

    @Test
    fun checklistHasBoxes() {
        val g = TemplateGeometry.build(PageSpec.of(PageFormat.A5, template = Template.CHECKLIST))
        assertTrue(g.boxes.size >= 4 * 10)
        assertEquals(g.boxes.size, g.lines.size)
    }

    @Test
    fun jsonRoundTrip() {
        val spec = PageSpec(
            w = 612f, h = 792.5f, paper = Paper.IVORY, template = Template.CORNELL, line = 0x80123456.toInt(),
            spacing = 19.5f, lineWidth = 0.8f, opts = 0, pdf = PdfRef("a b.pdf", 7),
        )
        val back = PageSpec.fromJson(spec.toJson())
        assertEquals(spec, back)
        assertEquals(PageSpec(), PageSpec.fromJson("to nie jest json"))
    }

    @Test
    fun jsonClampsAbsurdSizes() {
        val s = PageSpec.fromJson("""{"w":99999,"h":3}""")
        assertEquals(PageSpec.MAX_SIZE, s.w, 0f)
        assertEquals(PageSpec.MIN_SIZE, s.h, 0f)
    }

    @Test
    fun paperLineColorContrast() {
        assertTrue(Paper.luminance(Paper.autoLineColor(Paper.WHITE)) < Paper.luminance(Paper.WHITE))
        assertTrue(Paper.luminance(Paper.autoLineColor(Paper.DARK)) > Paper.luminance(Paper.DARK))
        assertTrue(Paper.isDark(Paper.BLACK))
        assertFalse(Paper.isDark(Paper.YELLOW))
    }

    @Test
    fun legacyAutoLineColorIsMigratedButCustomColorIsKept() {
        // Strona zapisana przed AES-003 z dawnym kolorem automatycznym dostaje delikatniejszy kolor obecny…
        val legacy = Paper.mix(Paper.WHITE, 0xFF3F5F8F.toInt(), 0.30f)
        val migrated = PageSpec.fromJson("""{"p":${Paper.WHITE},"t":1,"l":$legacy}""")
        assertEquals(Paper.autoLineColor(Paper.WHITE), migrated.line)
        assertTrue(Paper.luminance(migrated.line) > Paper.luminance(legacy))
        // …a kolor wybrany przez użytkownika zostaje bez zmian.
        val custom = 0xFFD04040.toInt()
        assertEquals(custom, PageSpec.fromJson("""{"p":${Paper.WHITE},"t":1,"l":$custom}""").line)
        // Obecny kolor automatyczny jest stabilny po zapisie i odczycie.
        val spec = PageSpec(template = Template.LINED)
        assertEquals(spec.line, PageSpec.fromJson(spec.toJson()).line)
    }
}
