package com.mdev.mnotes.core.samsung

import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class SamsungConvertTest {
    private fun read(pages: List<SynthSdocx.Page>) = SdocxReader.read(ByteArrayInputStream(SynthSdocx.build(pages)))
    private val sample = read(SynthSdocx.samplePages())

    private fun ConvertedNote.has(fragment: String) = report.any { fragment in it.pl }

    // --- tytuł i strony ----------------------------------------------------------------------------

    @Test fun titleDropsExtensionCounterAndSamsungTimestamp() {
        assertEquals("Notes", SamsungConvert.defaultTitle("Notes_261017_171015.sdocx"))
        assertEquals("Zakupy", SamsungConvert.defaultTitle("Zakupy (2).sdocx"))
        assertEquals("Moja notatka", SamsungConvert.defaultTitle("/storage/Moja notatka.sdocx"))
        assertEquals("Notatka Samsung Notes", SamsungConvert.defaultTitle(".sdocx"))
    }

    @Test fun trailingEmptyPagesAreDroppedButOneStays() {
        val d = read(
            listOf(
                SynthSdocx.samplePages()[1],
                SynthSdocx.Page("cccccccc-0000-4000-8000-000000000001"),
                SynthSdocx.Page("cccccccc-0000-4000-8000-000000000002"),
            ),
        )
        val n = SamsungConvert.convert(d, "a.sdocx", ConvertOptions())
        assertEquals(1, n.pages.size)
        assertEquals(2, n.droppedTrailing)
        assertTrue(n.has("Pominięto 2"))

        val allEmpty = read(listOf(SynthSdocx.Page("dddddddd-0000-4000-8000-000000000001"), SynthSdocx.Page("dddddddd-0000-4000-8000-000000000002")))
        assertEquals(1, SamsungConvert.convert(allEmpty, "b.sdocx", ConvertOptions()).pages.size)
    }

    // --- tło ---------------------------------------------------------------------------------------

    @Test fun originalKeepsExactSizeAndDescribesPatternAsBackground() {
        val n = SamsungConvert.convert(sample, "Notes_261017_171015.sdocx", ConvertOptions(ImportMode.ORIGINAL))
        val p = n.pages[0]
        assertEquals(600f, p.spec.w, 1e-3f)
        assertEquals(848.25f, p.spec.h, 1e-3f)
        assertEquals(0xFFE6E6E6.toInt(), p.spec.paper)
        assertEquals(Template.BLANK, p.spec.template)
        assertEquals(0, p.spec.opts)
        val bg = p.background!!
        assertEquals(BgKind.GRID, bg.kind)
        assertEquals(102.29f * 0.375f, bg.pitchX, 1e-3f)
        assertEquals(102.07f * 0.375f, bg.pitchY, 1e-3f)
        assertEquals(0xFFC8C8C8.toInt(), bg.line)
        assertEquals(p.spec.w, bg.w, 1e-4f)
        assertEquals(p.spec.paper, bg.paper)
        assertEquals("Notes", n.title)
    }

    private fun withTemplate(t: Int) =
        SamsungConvert.convert(read(listOf(SynthSdocx.Page("eeeeeeee-0000-4000-8000-0000000000%02d".format(t), bg = 0xFFFCFCFC.toInt(), template = t))), "a.sdocx", ConvertOptions(ImportMode.ORIGINAL))

    @Test fun measuredPatternsHaveDistinctSpacing() {
        val pitch = (1..9).map { withTemplate(it).pages[0].background!! }
        assertEquals(listOf(BgKind.LINES, BgKind.LINES, BgKind.LINES, BgKind.GRID, BgKind.GRID, BgKind.GRID, BgKind.DOTS, BgKind.DOTS, BgKind.DOTS), pitch.map { it.kind })
        listOf(72.05f, 102.07f, 168.13f).forEachIndexed { i, v -> assertEquals(v, pitch[i].pitchY / 0.375f, 1e-3f) }
        // kropki: poziomo rzadziej niż w pionie i pierwszy rząd niżej niż pierwsza linia
        assertTrue(pitch[6].pitchX > pitch[6].pitchY)
        assertTrue(pitch[6].offsetY > pitch[0].offsetY)
        assertTrue(pitch[6].dot > 0f)
        assertTrue(pitch.all { it.margins.isEmpty() })
        assertFalse(withTemplate(2).has("nie był sprawdzony"))
    }

    @Test fun oxfordHasLinesAndTwoRedMargins() {
        val n = withTemplate(11)
        val bg = n.pages[0].background!!
        assertEquals(BgKind.LINES, bg.kind)
        assertEquals(66.04f * 0.375f, bg.pitchY, 1e-3f)
        assertEquals(2, bg.margins.size)
        assertEquals(235.5f * 0.375f, bg.margins[0].x, 1e-3f)
        assertTrue(SamsungColor.r(bg.margins[0].color) > 0xE0 && SamsungColor.g(bg.margins[0].color) < 0xB0)
        assertFalse(n.has("nie jest jeszcze odtwarzany"))
        val dark = SamsungConvert.convert(read(listOf(SynthSdocx.Page("eeeeeeee-0000-4000-8000-000000000011", template = 11))), "a.sdocx", ConvertOptions(ImportMode.ORIGINAL, dark = true))
        assertEquals(SamsungColor.invertArgb(0xFFF09E9C.toInt()), dark.pages[0].background!!.margins[0].color)
        val std = SamsungConvert.convert(read(listOf(SynthSdocx.Page("eeeeeeee-0000-4000-8000-000000000011", template = 11))), "a.sdocx", ConvertOptions(ImportMode.STANDARD))
        assertEquals(Template.LINED, std.pages[0].spec.template)
        assertTrue(std.has("Oxford"))
    }

    @Test fun plainPageHasNoBackgroundDescription() {
        val p = SamsungConvert.convert(sample, "a.sdocx", ConvertOptions(ImportMode.ORIGINAL)).pages[1]
        assertNull(p.background)
        assertEquals(0xFFFCFCFC.toInt(), p.spec.paper)
    }

    @Test fun standardMapsToNativeGridAndFitsSizeToCenteredGrid() {
        val p = SamsungConvert.convert(sample, "a.sdocx", ConvertOptions(ImportMode.STANDARD)).pages[0]
        assertNull(p.background)
        assertEquals(Template.GRID, p.spec.template)
        assertEquals(102.07f * 0.375f, p.spec.spacing, 1e-3f)
        assertEquals(1.7f * 0.375f, p.spec.lineWidth, 1e-3f)
        assertEquals(0, p.spec.opts)
        assertEquals(16 * 38.276f + 0.34f, p.spec.w, 0.05f)
        assertEquals(22 * 38.276f + 33.75f, p.spec.h, 0.05f)
    }

    @Test fun fitSizeNeverShrinksBelowOneCell() {
        assertEquals(38.25 + 0.2, SamsungConvert.fitSize(1.0, 0.1, 38.25), 1e-9)
        assertEquals(15 * 38.25 + 0.2, SamsungConvert.fitSize(15 * 38.25, 0.1, 38.25), 1e-9)
    }

    @Test fun darkFlipsPaperLinesAndInk() {
        val n = SamsungConvert.convert(sample, "a.sdocx", ConvertOptions(ImportMode.ORIGINAL, dark = true))
        val p = n.pages[0]
        assertEquals(SamsungColor.invertArgb(0xFFE6E6E6.toInt()), p.spec.paper)
        assertTrue(Paper.isDark(p.spec.paper))
        assertEquals(SamsungColor.invertArgb(0xFFC8C8C8.toInt()), p.background!!.line)
        // czarne pismo staje się jasne, a zakreślacz zostaje nieprzezroczysty (jego kolor liczy się osobno)
        assertTrue(SamsungColor.r(p.strokes[0].color) > 0xE0)
        assertEquals(0xFF, SamsungColor.a(p.strokes[3].color))
        assertTrue(n.has("ciemny"))
    }

    @Test fun lightKeepsInkAsIs() {
        val p = SamsungConvert.convert(sample, "a.sdocx", ConvertOptions()).pages[0]
        assertEquals(0xFF000000.toInt(), p.strokes[0].color)
        assertFalse(Paper.isDark(p.spec.paper))
    }

    // --- pióra -------------------------------------------------------------------------------------

    @Test fun pensMapToNativeKinds() {
        val s = SamsungConvert.convert(sample, "a.sdocx", ConvertOptions()).pages[0].strokes
        assertEquals(listOf(PenKind.FOUNTAIN, PenKind.FOUNTAIN, PenKind.BRUSH, PenKind.HIGHLIGHTER), s.map { it.pen })
        assertEquals(16.77f * 0.375f, s[0].width, 1e-3f)
        assertEquals(19.88f * 0.375f * 1.2f, s[2].width, 1e-3f)
        assertEquals(31f * 0.375f, s[3].width, 1e-3f)
    }

    @Test fun pointsAreInPtAsTriples() {
        val s = SamsungConvert.convert(sample, "a.sdocx", ConvertOptions()).pages[0].strokes[1]
        assertEquals(0, s.pts.size % 3)
        assertEquals(100f * 0.375f, s.pts[0], 1e-4f)
        assertEquals(400f * 0.375f, s.pts[1], 1e-4f)
        assertEquals(30, s.pts.size / 3) // kroki po 4,5 pt przekraczają próg 0,3 pt
    }

    @Test fun fountainWidthFollowsWritingSpeed() {
        val s = SamsungConvert.convert(sample, "a.sdocx", ConvertOptions()).pages[0].strokes
        val slow = s[0].pts.filterIndexed { i, _ -> i % 3 == 2 }
        val fast = s[1].pts.filterIndexed { i, _ -> i % 3 == 2 }
        assertTrue("wolne pismo = pełny rozmiar", slow.all { it > 0.99f })
        assertTrue("szybkie pismo ≈ 0,58", fast.all { it in 0.55f..0.62f })
    }

    @Test fun brushWidthFollowsPressureCurve() {
        val b = SamsungConvert.convert(sample, "a.sdocx", ConvertOptions()).pages[0].strokes[2]
        val p0 = b.pts[2]
        val pEnd = b.pts[b.pts.size - 1]
        assertEquals((0.09 + 1.17 * 0.1) / 1.2, p0.toDouble(), 0.01)
        assertTrue(pEnd > p0)
        assertTrue(pEnd <= 1f)
    }

    @Test fun highlighterHasFixedFullFactor() {
        val h = SamsungConvert.convert(sample, "a.sdocx", ConvertOptions()).pages[0].strokes[3]
        assertTrue(h.pts.filterIndexed { i, _ -> i % 3 == 2 }.all { it == 1f })
    }

    @Test fun simplifyThinsDensePointsButKeepsEndsAndPressureChanges() {
        val n = 100
        val x = DoubleArray(n) { it * 0.05 }
        val y = DoubleArray(n)
        val flat = DoubleArray(n) { 0.5 }
        val thin = SamsungConvert.simplify(x, y, flat, false)
        assertTrue(thin.size / 3 < n / 3)
        assertEquals(0f, thin[0], 1e-6f)
        assertEquals(x[n - 1].toFloat(), thin[thin.size - 3], 1e-4f)
        val ramp = DoubleArray(n) { it / 99.0 }
        assertTrue(SamsungConvert.simplify(x, y, ramp, true).size > thin.size)
    }

    // --- raport ------------------------------------------------------------------------------------

    @Test fun reportsUnsupportedBackgroundButNotMeasuredPattern() {
        val d = read(
            listOf(
                SynthSdocx.Page("eeeeeeee-0000-4000-8000-000000000001", template = 3, strokes = SynthSdocx.samplePages()[1].strokes),
                SynthSdocx.Page("eeeeeeee-0000-4000-8000-000000000002", template = 12, strokes = SynthSdocx.samplePages()[1].strokes),
            ),
        )
        val n = SamsungConvert.convert(d, "a.sdocx", ConvertOptions())
        assertFalse(n.has("nie był sprawdzony"))
        val unsupported = n.report.first { "własny obraz" in it.pl }
        assertEquals(Level.WARN, unsupported.level)
        assertEquals(listOf(2), unsupported.pages)
        assertTrue(n.warnings >= 1)
    }

    @Test fun repeatedWarningMergesPageNumbers() {
        val pg = SynthSdocx.samplePages()[1].strokes
        val d = read(List(3) { SynthSdocx.Page("ffffffff-0000-4000-8000-00000000000$it", template = 14, strokes = pg) })
        val items = SamsungConvert.convert(d, "a.sdocx", ConvertOptions()).report.filter { "miesięczny" in it.pl }
        assertEquals(1, items.size)
        assertEquals(listOf(1, 2, 3), items[0].pages)
    }

    @Test fun countsSkippedObjectsByType() {
        val page = SynthSdocx.Page(
            "99999999-0000-4000-8000-000000000001", strokes = SynthSdocx.samplePages()[1].strokes,
            extra = listOf(2 to ByteArray(4), 2 to ByteArray(4), 3 to ByteArray(4)),
        )
        val n = SamsungConvert.convert(read(listOf(page)), "a.sdocx", ConvertOptions())
        val text = n.report.first { "pole tekstowe" in it.pl }
        assertTrue("2 szt." in text.pl)
        assertEquals(listOf(1), text.pages)
        assertNotNull(n.report.firstOrNull { "nie do odczytania" in it.pl })
        assertEquals(1, n.strokeCount)
    }

    @Test fun unreadablePageStaysAsEmptySheetWithWarning() {
        val bad = SdPage("x", 0, 0, null, null, null, null, false, emptyList(), error = "brak pliku strony")
        val n = SamsungConvert.convert(SdDocument(emptyMap(), listOf(bad)), "a.sdocx", ConvertOptions())
        assertEquals(1, n.pages.size)
        assertTrue(n.pages[0].strokes.isEmpty())
        assertTrue(n.has("nie do odczytania"))
        assertEquals(Paper.WHITE, n.pages[0].spec.paper)
    }

    @Test fun reportTextExistsInBothLanguages() {
        val n = SamsungConvert.convert(sample, "a.sdocx", ConvertOptions(dark = true))
        assertTrue(n.report.isNotEmpty())
        assertTrue(n.report.all { it.pl.isNotBlank() && it.en.isNotBlank() })
        assertEquals(n.report[0].pl, n.report[0].text(true))
        assertEquals(n.report[0].en, n.report[0].text(false))
    }
}
