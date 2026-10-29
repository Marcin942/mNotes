package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Construct
import com.mdev.mnotes.core.geom.ConstructOps
import com.mdev.mnotes.core.geom.GeoTool
import com.mdev.mnotes.core.geom.GeoTool.In
import com.mdev.mnotes.core.geom.GeoTool.Kind
import com.mdev.mnotes.core.geom.Live
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** NAU-3: żywa konstrukcja — przesunięcie wejścia przelicza wynik, ręczna zmiana wyniku albo brak wejścia zrywa zależność. */
class LiveTest {
    private val page = "p"

    private fun tri(id: Int, ax: Float, ay: Float) = Stroke(id, PenKind.BALLPOINT, 0, 1.2f,
        floatArrayOf(ax, ay, 1f, 60f, 0f, 1f, 0f, 40f, 1f, ax, ay, 1f), poly = true)
    private fun dot(x: Float, y: Float, id: Int = Ids.next()) = Stroke(id, PenKind.BALLPOINT, 0, 3f, floatArrayOf(x, y, 1f))
    private fun isDot(e: Element) = e is Stroke && e.count == 1
    private fun centre(e: Element) = (e as Stroke).let { floatArrayOf(it.bounds.cx, it.bounds.cy) }

    /** Okrąg opisany z menu Konstrukcja na trójkącie [t], z zależnością jak w edytorze. */
    private fun circum(live: Live, t: Stroke): Pair<PageContent, List<Element>> {
        val out = ConstructOps.ops(listOf(t)).first { it.id == "circum" }.build(0xFF000000.toInt(), 1.2f)
        live.add(page, listOf(t.id), out.map { it.id }) { cur -> ConstructOps.ops(cur).firstOrNull { it.id == "circum" }?.build?.invoke(0xFF000000.toInt(), 1.2f) }
        return PageContent.of(listOf(t) + out) to out
    }

    private fun replaced(c: PageContent, e: Element) = c.apply(ElementsEdit.replace(c, mapOf(e.id to e)))

    @Test fun movingTheVertexRecomputesTheCentre() {
        val live = Live(); val t = tri(Ids.next(), 0f, 0f)
        val (before, out) = circum(live, t)
        val o = out.first { isDot(it) }
        val after = replaced(before, tri(t.id, 0f, -20f))
        val edit = live.follow(page, before, after)!!
        val fin = after.apply(edit)
        val want = Construct.circumcenter(Construct.P(0f, -20f), Construct.P(60f, 0f), Construct.P(0f, 40f))!!
        val got = centre(fin.byId(o.id)!!)
        assertEquals(want.x, got[0], 0.05f); assertEquals(want.y, got[1], 0.05f)
        // Te same id i ten sam skład: zależność trwa i działa przy następnym ruchu.
        assertEquals(out.map { it.id }, live.links(page).single().outputs)
        val again = replaced(fin, tri(t.id, 0f, 10f))
        assertNotNull(live.follow(page, fin, again))
    }

    @Test fun oneUndoRestoresInputAndResult() {
        val live = Live(); val t = tri(Ids.next(), 0f, 0f)
        val (before, _) = circum(live, t)
        val user = ElementsEdit.replace(before, mapOf(t.id to tri(t.id, 10f, 5f)))
        val after = before.apply(user)
        val follow = live.follow(page, before, after)!!
        val back = after.apply(follow).apply(follow.inverse()).apply(user.inverse())
        assertEquals(before.elements.map { it.id }, back.elements.map { it.id })
        before.elements.zip(back.elements).forEach { (a, b) -> assertTrue(Live.sameShape(a, b)) }
    }

    @Test fun removingTheInputBreaksTheLink() {
        val live = Live(); val t = tri(Ids.next(), 0f, 0f)
        val (before, _) = circum(live, t)
        val after = before.apply(ElementsEdit.remove(before, setOf(t.id)))
        assertNull(live.follow(page, before, after))
        assertTrue(live.links(page).isEmpty())
    }

    @Test fun movingTheResultByHandBreaksTheLinkButRecolouringDoesNot() {
        val live = Live(); val t = tri(Ids.next(), 0f, 0f)
        val (before, out) = circum(live, t)
        val o = out.first { isDot(it) } as Stroke
        val red = Stroke(o.id, o.pen, 0xFFFF0000.toInt(), o.width, o.pts, poly = o.poly)
        val recoloured = replaced(before, red)
        assertNull(live.follow(page, before, recoloured))
        assertEquals(1, live.links(page).size)
        // Kolor ucznia zostaje po przeliczeniu.
        val moved = replaced(recoloured, tri(t.id, 0f, -20f))
        val fin = moved.apply(live.follow(page, recoloured, moved)!!)
        assertEquals(0xFFFF0000.toInt(), (fin.byId(o.id) as Stroke).color)
        // Przesunięcie samego wyniku zrywa zależność; wynik zostaje, gdzie go położono.
        val dragged = replaced(fin, dot(5f, 5f, o.id))
        assertNull(live.follow(page, fin, dragged))
        assertTrue(live.links(page).isEmpty())
    }

    @Test fun renamedLabelKeepsItsName() {
        val live = Live(); val t = tri(Ids.next(), 0f, 0f)
        val (made, out) = circum(live, t)
        val label = out.filterIsInstance<TextBox>().first { it.text == "O" }
        val before = replaced(made, label.copy(text = "O₁"))
        val after = replaced(before, tri(t.id, 0f, -20f))
        val fin = after.apply(live.follow(page, before, after)!!)
        assertEquals("O₁", (fin.byId(label.id) as TextBox).text)
    }

    /** 3c-B3: podpis z miarą kąta zostawał stary („90°”) po przeliczeniu; nazwa zmieniona przez ucznia nadal zostaje. */
    @Test fun measureLabelFollowsTheShape() {
        val live = Live(); val t = tri(Ids.next(), 0f, 0f)
        val build = { cur: List<Element> -> ConstructOps.ops(cur).firstOrNull { it.id == "angles" }?.build?.invoke(0xFF000000.toInt(), 1.2f) }
        val out = build(listOf(t))!!
        live.add(page, listOf(t.id), out.map { it.id }, build)
        val before = PageContent.of(listOf(t) + out)
        val right = out.filterIsInstance<TextBox>().first { it.text == "90°" }
        val after = replaced(before, tri(t.id, 0f, -20f))
        val fin = after.apply(live.follow(page, before, after)!!)
        val now = (fin.byId(right.id) as TextBox).text
        assertTrue("podpis przeliczony: $now", now != "90°" && now.endsWith("°"))
    }

    @Test fun chainRecomputesInOrder() {
        // Geometria: okrąg przez 3 kropki, potem środek tego okręgu; ruch kropki przesuwa oba wyniki.
        val live = Live()
        val a = dot(0f, 0f); val b = dot(60f, 0f); val c = dot(0f, 40f)
        var content = PageContent.of(listOf(a, b, c))
        fun geo(kind: Kind, taps: List<Pair<Float, Float>>): List<Element> {
            val picks = kind.inputs.mapIndexed { i, w -> GeoTool.pickFrom(content.elements, taps[i].first, taps[i].second, 8f, w)!! }
            val out = GeoTool.build(kind, picks.map { it.input }, 0, 1.2f, names = false)
            content = content.apply(ElementsEdit.add(content, out))
            live.add(page, picks.mapNotNull { it.id }, out.map { it.id }) { cur ->
                val byId = cur.associateBy { it.id }
                GeoTool.build(kind, picks.mapIndexed { i, pk -> pk.id?.let { GeoTool.resolve(kind.inputs[i], byId.getValue(it), pk.part)!! } ?: pk.input }, 0, 1.2f, names = false)
            }
            return out
        }
        val first = geo(Kind.CIRCLE3, listOf(0f to 0f, 60f to 0f, 0f to 40f))
        val circle = first.first { it is Stroke && it.ellipse }
        val r = (circle.bounds.w / 2f)
        val second = geo(Kind.CENTER, listOf(circle.bounds.cx + r to circle.bounds.cy))
        val o2 = second.first { isDot(it) }
        val before = content
        val after = replaced(before, dot(0f, -20f, a.id))
        val fin = after.apply(live.follow(page, before, after)!!)
        val want = Construct.circumcenter(Construct.P(0f, -20f), Construct.P(60f, 0f), Construct.P(0f, 40f))!!
        val got = centre(fin.byId(o2.id)!!)
        assertEquals(want.x, got[0], 0.2f); assertEquals(want.y, got[1], 0.2f)
        assertEquals(2, live.links(page).size)
    }

    @Test fun pickRemembersWhereTheInputCameFrom() {
        val t = tri(Ids.next(), 0f, 0f); val d = dot(100f, 100f)
        val els = listOf(t, d)
        val v = GeoTool.pickFrom(els, 59f, 1f, 8f, In.POINT)!!
        assertEquals(t.id, v.id); assertEquals(1, v.part)
        assertEquals(d.id, GeoTool.pickFrom(els, 101f, 99f, 8f, In.POINT)!!.id)
        assertNull(GeoTool.pickFrom(els, 300f, 300f, 8f, In.POINT)!!.id)
        val moved = tri(t.id, 0f, 0f).let { Stroke(it.id, it.pen, it.color, it.width, floatArrayOf(0f, 0f, 1f, 70f, 5f, 1f, 0f, 40f, 1f, 0f, 0f, 1f), poly = true) }
        val p = GeoTool.resolve(In.POINT, moved, v.part) as GeoTool.Input.Point
        assertEquals(70f, p.p.x, 1e-4f); assertEquals(5f, p.p.y, 1e-4f)
        assertNull(GeoTool.resolve(In.TRIANGLE, d, -1))
    }

    @Test fun unrelatedEditsAreIgnoredAndCyclesCannotBeAdded() {
        val live = Live(); val t = tri(Ids.next(), 0f, 0f)
        val (before, out) = circum(live, t)
        val after = before.apply(ElementsEdit.add(before, listOf(dot(300f, 300f))))
        assertNull(live.follow(page, before, after))
        // Wynik nie może być własnym wejściem; wejścia starsze od wyników, więc kolejność dodawania jest topologiczna.
        live.add(page, listOf(out[0].id), listOf(out[0].id)) { it }
        assertEquals(1, live.links(page).size)
        assertEquals(t.id, live.links(page).single().inputs.single())
    }
}
