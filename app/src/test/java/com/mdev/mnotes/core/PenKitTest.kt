package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.pen.ActivePen
import com.mdev.mnotes.core.pen.InkColor
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.pen.PenKit
import com.mdev.mnotes.core.pen.PenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PenKitTest {
    private val black = 0xFF1B1D21.toInt()
    private val red = 0xFFC8433F.toInt()
    private val navy = 0xFF24407E.toInt()
    private val yellow = 0xFFF5CF17.toInt()
    private val custom = 0xFF123456.toInt()
    private val kit = PenKit.DEFAULT

    @Test fun defaultsShareInkBetweenPenFountainAndPencil() {
        assertEquals(PenKit.SET_INK, kit.slot(PenKind.BALLPOINT).setId)
        assertEquals(PenKit.SET_INK, kit.slot(PenKind.FOUNTAIN).setId)
        assertEquals(PenKit.SET_INK, kit.slot(PenKind.PENCIL).setId)
        assertEquals(PenKit.SET_BRUSH, kit.slot(PenKind.BRUSH).setId)
        assertEquals(PenKit.SET_HL, kit.slot(PenKind.HIGHLIGHTER).setId)
        assertTrue(kit.setOf(PenKind.HIGHLIGHTER).hl)
    }

    @Test fun defaultsAreConsistent() {
        for (k in PenKind.entries) {
            val s = kit.slot(k); val spec = PenSpec.of(k)
            assertEquals(PenKit.WIDTHS, s.widths.size)
            assertEquals(s.widths.sorted(), s.widths)
            assertTrue(s.widths.all { it in spec.minWidth..spec.maxWidth })
            assertEquals(spec.defaultWidth, s.width, 0.001f)
            assertTrue(s.color in kit.strip(k))
        }
        for (s in kit.sets) assertTrue(s.visible in 1..s.colors.size)
    }

    @Test fun eachPenRemembersItsOwnColorWidthAndStyle() {
        val k = kit.withColor(PenKind.BALLPOINT, red).withWidthIndex(PenKind.BALLPOINT, 2).withStyle(PenKind.BALLPOINT, LineStyle.DASHED)
        assertEquals(ActivePen(PenKind.BALLPOINT, red, 2.27f, LineStyle.DASHED), k.current)
        assertEquals(navy, k.withActive(PenKind.FOUNTAIN).current.color)
        assertEquals(LineStyle.SOLID, k.pen(PenKind.PENCIL).style)
        assertEquals(red, k.withActive(PenKind.FOUNTAIN).withActive(PenKind.BALLPOINT).current.color)
    }

    @Test fun widthIsClampedToPenRange() {
        val k = kit.withWidth(PenKind.BALLPOINT, 0, 99f).withWidth(PenKind.BALLPOINT, 2, 0f)
        assertEquals(PenSpec.of(PenKind.BALLPOINT).maxWidth, k.slot(PenKind.BALLPOINT).widths[0], 0f)
        assertEquals(PenSpec.of(PenKind.BALLPOINT).minWidth, k.slot(PenKind.BALLPOINT).widths[2], 0f)
        assertEquals(1.42f, k.slot(PenKind.BALLPOINT).widths[1], 0f)
        assertEquals(2, kit.withWidthIndex(PenKind.BRUSH, 7).slot(PenKind.BRUSH).widthIndex)
    }

    @Test fun stripShowsVisibleColorsPlusSelectedOutsider() {
        assertEquals(6, kit.strip(PenKind.BALLPOINT).size)
        val k = kit.withColor(PenKind.BALLPOINT, custom)
        assertEquals(7, k.strip(PenKind.BALLPOINT).size)
        assertEquals(custom, k.strip(PenKind.BALLPOINT).last())
        // Narzędzia dzielą zestaw, ale nie wybrany kolor.
        assertEquals(6, k.strip(PenKind.FOUNTAIN).size)
    }

    @Test fun stepColorWrapsAroundStrip() {
        val st = kit.strip(PenKind.BALLPOINT)
        assertEquals(st[1], kit.stepColor(PenKind.BALLPOINT, 1).slot(PenKind.BALLPOINT).color)
        assertEquals(st.last(), kit.stepColor(PenKind.BALLPOINT, -1).slot(PenKind.BALLPOINT).color)
    }

    @Test fun addColorLandsOnStripAndIsSharedBySet() {
        val k = kit.addColor(PenKit.SET_INK, custom)
        assertEquals(7, k.set(PenKit.SET_INK)!!.visible)
        assertEquals(custom, k.strip(PenKind.PENCIL)[6])
        assertEquals(11, k.set(PenKit.SET_INK)!!.colors.size)
        assertEquals(k, k.addColor(PenKit.SET_INK, custom))
        assertFalse(custom in k.strip(PenKind.BRUSH))
    }

    @Test fun addColorOnFullStripReplacesLastVisible() {
        var k = kit.withVisible(PenKit.SET_INK, 10)
        k = k.addColor(PenKit.SET_INK, 0xFF000001.toInt()).addColor(PenKit.SET_INK, 0xFF000002.toInt())
        assertEquals(PenKit.MAX_VISIBLE, k.set(PenKit.SET_INK)!!.visible)
        k = k.addColor(PenKit.SET_INK, custom)
        assertEquals(PenKit.MAX_VISIBLE, k.set(PenKit.SET_INK)!!.visible)
        assertEquals(custom, k.strip(PenKind.BALLPOINT).last())
        assertEquals(13, k.set(PenKit.SET_INK)!!.colors.size)
    }

    @Test fun removeMoveAndReplaceColor() {
        val k = kit.removeColor(PenKit.SET_INK, black)
        assertFalse(black in k.set(PenKit.SET_INK)!!.colors)
        // Długopis dalej pisze czarnym, tyle że jako kolorem spoza listy.
        assertEquals(black, k.current.color)
        assertEquals(black, k.strip(PenKind.BALLPOINT).last())

        val m = kit.moveColor(PenKit.SET_INK, 0, 2)
        assertEquals(black, m.set(PenKit.SET_INK)!!.colors[2])
        assertEquals(kit, kit.moveColor(PenKit.SET_INK, 0, 99))

        val r = kit.replaceColor(PenKit.SET_INK, black, custom)
        assertEquals(custom, r.set(PenKit.SET_INK)!!.colors[0])
        assertEquals(custom, r.current.color)
        assertEquals(navy, r.pen(PenKind.FOUNTAIN).color)
    }

    @Test fun lastColorCannotBeRemoved() {
        var k = kit
        for (c in kit.set(PenKit.SET_HL)!!.colors) k = k.removeColor(PenKit.SET_HL, c)
        assertEquals(1, k.set(PenKit.SET_HL)!!.colors.size)
        assertEquals(1, k.set(PenKit.SET_HL)!!.visible)
    }

    @Test fun visibleCountIsClamped() {
        assertEquals(1, kit.withVisible(PenKit.SET_INK, 0).set(PenKit.SET_INK)!!.visible)
        assertEquals(10, kit.withVisible(PenKit.SET_INK, 99).set(PenKit.SET_INK)!!.visible)
    }

    @Test fun borrowedSetIsConvertedForThePen() {
        // Zakreślacz na atramentach: ciemny atrament jest rozjaśniany, żeby nie zasłaniał tekstu.
        val h = kit.withSet(PenKind.HIGHLIGHTER, PenKit.SET_INK)
        assertEquals(black, h.slot(PenKind.HIGHLIGHTER).color)
        assertEquals(InkColor.soften(black), h.pen(PenKind.HIGHLIGHTER).color)
        // Długopis na barwach zakreślacza: pisze tym, co zakreślacz zostawia na papierze.
        val b = kit.withSet(PenKind.BALLPOINT, PenKit.SET_HL)
        assertEquals(yellow, b.slot(PenKind.BALLPOINT).color)
        assertEquals(InkColor.look(yellow), b.pen(PenKind.BALLPOINT).color)
        // Własny zestaw: bez przeliczania.
        assertEquals(yellow, kit.pen(PenKind.HIGHLIGHTER).color)
        assertEquals(kit, kit.withSet(PenKind.BRUSH, "nie ma"))
    }

    @Test fun switchingSetKeepsColorWhenPresent() {
        val k = kit.withColor(PenKind.BRUSH, black).withSet(PenKind.BRUSH, PenKit.SET_INK)
        assertEquals(black, k.slot(PenKind.BRUSH).color)
    }

    @Test fun jsonRoundTrip() {
        val k = kit.withActive(PenKind.BRUSH).withColor(PenKind.PENCIL, custom).withWidth(PenKind.BRUSH, 0, 3.5f)
            .withStyle(PenKind.HIGHLIGHTER, LineStyle.DOTTED).addColor(PenKit.SET_BRUSH, custom).withSet(PenKind.FOUNTAIN, PenKit.SET_HL)
        assertEquals(k, PenKit.fromJson(k.toJson()))
    }

    @Test fun brokenJsonIsRepairedOrRejected() {
        assertNull(PenKit.fromJson("nie json"))
        assertNull(PenKit.fromJson("{}"))
        val k = PenKit.fromJson("""{"sets":[{"id":"ink","c":[-1],"vis":40}],"pens":[{"k":0,"s":"zniknął","c":5,"w":[900],"i":9,"st":7},{"k":99}]}""")!!
        assertEquals(3, k.sets.size)
        assertEquals(1, k.set(PenKit.SET_INK)!!.visible)
        val s = k.slot(PenKind.BALLPOINT)
        assertEquals(PenKit.SET_INK, s.setId)
        assertEquals(0xFF000005.toInt(), s.color)
        assertEquals(listOf(PenSpec.of(PenKind.BALLPOINT).maxWidth, 1.42f, 2.27f), s.widths)
        assertEquals(2, s.widthIndex)
        assertEquals(LineStyle.SOLID, s.style)
        assertEquals(kit.slot(PenKind.BRUSH), k.slot(PenKind.BRUSH))
    }

    @Test fun migrationKeepsOldPensColorsAndWidths() {
        val old = listOf(
            ActivePen(PenKind.BALLPOINT, black, 1.42f), ActivePen(PenKind.FOUNTAIN, navy, 2.83f), ActivePen(PenKind.BALLPOINT, red, 1.0f),
            ActivePen(PenKind.PENCIL, custom, 1.98f), ActivePen(PenKind.HIGHLIGHTER, 0xFF71DF77.toInt(), 20f),
        )
        val k = PenKit.migrate(old, 2)
        assertEquals(PenKind.BALLPOINT, k.active)
        assertEquals(ActivePen(PenKind.BALLPOINT, red, 1.0f), k.current)
        assertEquals(listOf(black, navy, red, custom), k.set(PenKit.SET_INK)!!.colors.take(4))
        assertEquals(k.set(PenKit.SET_INK)!!.colors.distinct(), k.set(PenKit.SET_INK)!!.colors)
        // Grubości dawnych piór mają pierwszeństwo, brak uzupełnia fabryczna.
        assertEquals(listOf(0.85f, 1.0f, 1.42f), k.slot(PenKind.BALLPOINT).widths)
        assertEquals(1, k.slot(PenKind.BALLPOINT).widthIndex)
        assertEquals(custom, k.pen(PenKind.PENCIL).color)
        assertEquals(20f, k.pen(PenKind.HIGHLIGHTER).width, 0f)
        assertEquals(0xFF71DF77.toInt(), k.set(PenKit.SET_HL)!!.colors.first())
        // Narzędzie bez dawnego pióra dostaje ustawienia fabryczne.
        assertEquals(kit.slot(PenKind.BRUSH), k.slot(PenKind.BRUSH))
        assertEquals(kit, PenKit.migrate(emptyList(), 0))
    }

    @Test fun migrationSurvivesManyPens() {
        val old = List(10) { ActivePen(PenKind.BALLPOINT, 0xFF000000.toInt() + it * 17, 0.5f + it * 0.5f) }
        val k = PenKit.migrate(old, 9)
        val s = k.set(PenKit.SET_INK)!!
        assertEquals(10, s.visible)
        assertTrue(s.colors.size <= PenKit.MAX_COLORS)
        assertEquals(PenKit.WIDTHS, k.slot(PenKind.BALLPOINT).widths.size)
        assertEquals(old[9].color, k.current.color)
        assertEquals(old[9].width, k.current.width, 0f)
    }

    @Test fun ownSetStartsFromFactoryColoursOrACopy() {
        val k = kit.addSet("  Do rysunków  ", hl = false)
        val s = k.sets.last()
        assertEquals("u1", s.id)
        assertEquals("Do rysunków", s.name)
        assertFalse(s.hl)
        assertEquals(kit.set(PenKit.SET_INK)!!.colors, s.colors)
        // Kopia bierze barwy, liczbę widocznych i typ ze źródła, nawet gdy poproszono o inny typ.
        val c = k.withVisible(PenKit.SET_HL, 3).addSet("Pastele", hl = false, from = PenKit.SET_HL).sets.last()
        assertEquals("u2", c.id)
        assertTrue(c.hl)
        assertEquals(3, c.visible)
        // Zwolnione id wraca do użycia, a zajętego się nie dubluje.
        assertEquals("u1", k.addSet("B", false).removeSet("u1").addSet("C", false).sets.last().id)
        assertEquals(PenKit.MAX_NAME, kit.addSet("x".repeat(100), false).sets.last().name.length)
    }

    @Test fun setCountIsLimited() {
        var k = kit
        repeat(20) { k = k.addSet("Z$it", false) }
        assertEquals(PenKit.MAX_SETS, k.sets.size)
        assertEquals(k.sets.size, k.sets.map { it.id }.distinct().size)
        assertEquals(k, PenKit.fromJson(k.toJson()))
    }

    @Test fun removingOwnSetSendsToolsHomeWithTheirColour() {
        val k = kit.addSet("Moje", hl = true).withSet(PenKind.BALLPOINT, "u1").withSet(PenKind.HIGHLIGHTER, "u1").withColor(PenKind.BALLPOINT, yellow)
        assertEquals(listOf(PenKind.BALLPOINT, PenKind.HIGHLIGHTER), k.users("u1"))
        val before = k.pen(PenKind.BALLPOINT).color
        val r = k.removeSet("u1")
        assertNull(r.set("u1"))
        assertEquals(PenKit.SET_INK, r.slot(PenKind.BALLPOINT).setId)
        assertEquals(PenKit.SET_HL, r.slot(PenKind.HIGHLIGHTER).setId)
        // Długopis pisał barwą zakreślacza widzianą na papierze i pisze nią dalej, już jako kolorem spoza listy.
        assertEquals(before, r.pen(PenKind.BALLPOINT).color)
        assertTrue(before in r.strip(PenKind.BALLPOINT))
        assertEquals(k.pen(PenKind.HIGHLIGHTER).color, r.pen(PenKind.HIGHLIGHTER).color)
        assertEquals(r, PenKit.fromJson(r.toJson()))
    }

    @Test fun builtInSetsCannotBeRemovedButCanBeReset() {
        assertEquals(kit, kit.removeSet(PenKit.SET_INK))
        assertEquals(kit, kit.removeSet("nie ma"))
        val k = kit.renameSet(PenKit.SET_INK, "Biuro").addColor(PenKit.SET_INK, custom).withVisible(PenKit.SET_INK, 2).withColor(PenKind.BALLPOINT, custom)
        assertEquals("Biuro", k.set(PenKit.SET_INK)!!.name)
        assertTrue(k.changed(PenKit.SET_INK))
        assertFalse(kit.renameSet(PenKit.SET_INK, "Biuro").changed(PenKit.SET_INK))
        val r = k.resetSet(PenKit.SET_INK)
        // Reset dotyczy barw; nazwa, którą użytkownik nadał zestawowi, zostaje.
        assertEquals(PenKit.factory(PenKit.SET_INK)!!.copy(name = "Biuro"), r.set(PenKit.SET_INK))
        assertFalse(r.changed(PenKit.SET_INK))
        // Wybrany kolor nie znika razem z zestawem: zostaje na listwie jako dodatkowy.
        assertEquals(custom, r.pen(PenKind.BALLPOINT).color)
        assertTrue(custom in r.strip(PenKind.BALLPOINT))
        val own = kit.addSet("Moje", false)
        assertEquals(own, own.resetSet("u1"))
        assertFalse(own.changed("u1"))
        assertTrue(PenKit.builtIn(PenKit.SET_HL))
        assertFalse(PenKit.builtIn("u1"))
        assertEquals("", k.renameSet(PenKit.SET_INK, " ").set(PenKit.SET_INK)!!.name)
    }

    @Test fun coloursTravelBetweenSets() {
        // Ten sam typ: barwa bez zmian; do zakreślaczy ciemna barwa jest rozjaśniana, z zakreślaczy wychodzi to, co widać na papierze.
        assertTrue(navy in kit.copyColor(PenKit.SET_INK, navy, PenKit.SET_BRUSH).set(PenKit.SET_BRUSH)!!.colors)
        val toHl = kit.copyColor(PenKit.SET_INK, black, PenKit.SET_HL)
        assertTrue(InkColor.soften(black) in toHl.set(PenKit.SET_HL)!!.colors.take(toHl.set(PenKit.SET_HL)!!.visible))
        assertTrue(black in toHl.set(PenKit.SET_INK)!!.colors)
        val toInk = kit.copyColor(PenKit.SET_HL, yellow, PenKit.SET_INK)
        assertTrue(InkColor.look(yellow) in toInk.set(PenKit.SET_INK)!!.colors)
        assertEquals(kit, kit.copyColor(PenKit.SET_INK, black, "nie ma"))

        val moved = kit.moveColorTo(PenKit.SET_INK, navy, PenKit.SET_BRUSH)
        assertFalse(navy in moved.set(PenKit.SET_INK)!!.colors)
        assertTrue(navy in moved.set(PenKit.SET_BRUSH)!!.colors)
        // Pióro pisało granatem i pisze nim dalej, choć zestaw już go nie ma.
        assertEquals(navy, moved.pen(PenKind.FOUNTAIN).color)
        // Barwa, którą cel już ma, po prostu znika ze źródła; przy pełnym celu nic się nie dzieje.
        val dup = kit.moveColorTo(PenKit.SET_INK, red, PenKit.SET_BRUSH)
        assertFalse(red in dup.set(PenKit.SET_INK)!!.colors)
        assertEquals(kit.set(PenKit.SET_BRUSH), dup.set(PenKit.SET_BRUSH))
        var full = kit
        for (i in 0 until PenKit.MAX_COLORS) full = full.addColor(PenKit.SET_BRUSH, 0xFF000100.toInt() + i)
        assertEquals(PenKit.MAX_COLORS, full.set(PenKit.SET_BRUSH)!!.colors.size)
        assertEquals(full, full.moveColorTo(PenKit.SET_INK, navy, PenKit.SET_BRUSH))
        assertEquals(kit, kit.moveColorTo(PenKit.SET_INK, navy, PenKit.SET_INK))
    }

    @Test fun coloursCanBeHiddenFromTheStripAndBroughtBack() {
        val ink = kit.set(PenKit.SET_INK)!!
        val hidden = ink.colors[8]
        val shown = kit.showColor(PenKit.SET_INK, hidden).set(PenKit.SET_INK)!!
        assertEquals(7, shown.visible)
        assertEquals(hidden, shown.colors[6])
        assertEquals(ink.colors.toSet(), shown.colors.toSet())
        // Widocznej nie trzeba pokazywać, obcej się nie da.
        assertEquals(kit, kit.showColor(PenKit.SET_INK, black))
        assertEquals(kit, kit.showColor(PenKit.SET_INK, custom))

        val h = kit.hideColor(PenKit.SET_INK, red).set(PenKit.SET_INK)!!
        assertEquals(5, h.visible)
        assertEquals(red, h.colors[5])
        assertFalse(red in h.colors.take(h.visible))
        assertEquals(kit, kit.hideColor(PenKit.SET_INK, hidden))
        assertEquals(kit.withVisible(PenKit.SET_INK, 1), kit.withVisible(PenKit.SET_INK, 1).hideColor(PenKit.SET_INK, black))
        // Narzędzie, które pisało schowanym kolorem, nadal ma go na listwie.
        assertTrue(navy in kit.hideColor(PenKit.SET_INK, navy).strip(PenKind.FOUNTAIN))

        // Pełna listwa: pokazany kolor zajmuje ostatnie miejsce, a dotychczasowy ostatni się chowa.
        var full = kit
        for (i in 0 until 8) full = full.addColor(PenKit.SET_INK, 0xFF000100.toInt() + i)
        val fs = full.set(PenKit.SET_INK)!!
        assertEquals(PenKit.MAX_VISIBLE, fs.visible)
        val last = fs.colors[PenKit.MAX_VISIBLE - 1]
        val back = fs.colors.last()
        val swapped = full.showColor(PenKit.SET_INK, back).set(PenKit.SET_INK)!!
        assertEquals(PenKit.MAX_VISIBLE, swapped.visible)
        assertEquals(back, swapped.colors[PenKit.MAX_VISIBLE - 1])
        assertEquals(last, swapped.colors[PenKit.MAX_VISIBLE])
    }

    @Test fun savedKitCannotBringTooManySets() {
        var k = kit
        repeat(PenKit.MAX_SETS) { k = k.addSet("Z$it", false) }
        val extra = k.copy(sets = k.sets + k.sets.last().copy(id = "x1") + k.sets.last().copy(id = "x2"))
        assertEquals(PenKit.MAX_SETS, PenKit.fromJson(extra.toJson())!!.sets.size)
        // Zestawy fabryczne wracają, nawet gdy zapis ich nie ma.
        val none = k.copy(sets = k.sets.filter { !PenKit.builtIn(it.id) })
        assertEquals(PenKit.MAX_SETS, PenKit.fromJson(none.toJson())!!.sets.size)
    }

    @Test fun swatchShowsWhatLandsOnPaper() {
        // Zakreślacz zostawia na papierze ślad jaśniejszy niż jego atrament; pozostałe narzędzia piszą barwą wprost.
        assertEquals(InkColor.look(yellow), InkColor.onPaper(yellow, true, PenKind.HIGHLIGHTER))
        assertEquals(InkColor.look(InkColor.soften(black)), InkColor.onPaper(black, false, PenKind.HIGHLIGHTER))
        assertEquals(black, InkColor.onPaper(black, false, PenKind.BALLPOINT))
        assertEquals(InkColor.look(yellow), InkColor.onPaper(yellow, true, PenKind.BALLPOINT))
    }
}
