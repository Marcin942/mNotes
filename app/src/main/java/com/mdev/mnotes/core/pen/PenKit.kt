package com.mdev.mnotes.core.pen

import com.mdev.mnotes.core.model.PenKind
import org.json.JSONArray
import org.json.JSONObject

/** Styl linii pióra. Kody są zapisywane, więc się nie zmieniają. */
enum class LineStyle(val code: Int) {
    SOLID(0), DASHED(1), DOTTED(2);

    companion object { fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: SOLID }
}

/** To, czym się w danej chwili pisze: narzędzie z kolorem już przeliczonym dla niego, grubością w pt i stylem. */
data class ActivePen(val kind: PenKind, val color: Int, val width: Float, val style: LineStyle = LineStyle.SOLID)

/** Przeliczanie barw między atramentem a zakreślaczem, żeby pożyczony kolor wyglądał na papierze podobnie. */
object InkColor {
    /** Jak zakreślacz o danej barwie wygląda na białym papierze (multiply, krycie 0,5). */
    fun look(c: Int): Int {
        fun ch(sh: Int) = (255 + ((c shr sh and 0xFF) - 255) / 2)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Pilnuje, by efekt zakreślacza na papierze pozostał jasnym pastelem: barwy tak ciemne, że po multiply miałyby
     * jasność poniżej ~0,68 (zasłaniałyby tekst), są rozjaśniane ku bieli.
     */
    fun soften(c: Int): Int {
        val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
        val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val min = 92f
        if (lum >= min) return c or (0xFF shl 24)
        val t = (min - lum) / (255f - lum)
        fun up(v: Int) = (v + (255 - v) * t + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (up(r) shl 16) or (up(g) shl 8) or up(b)
    }

    /** Barwa przeniesiona między zestawami różnego typu tak, żeby na papierze wyglądała podobnie. */
    fun between(c: Int, fromHl: Boolean, toHl: Boolean): Int = if (fromHl == toHl) c else if (toHl) soften(c) else look(c)

    /** Barwa z zestawu ([setHl]: zestaw zakreślaczy) w postaci, w jakiej użyje jej narzędzie [kind]. */
    fun forPen(c: Int, setHl: Boolean, kind: PenKind): Int = when {
        kind == PenKind.HIGHLIGHTER && !setHl -> soften(c)
        kind != PenKind.HIGHLIGHTER && setHl -> look(c)
        else -> c
    }

    /** Barwa z zestawu tak, jak narzędzie [kind] zostawi ją na białym papierze (do próbek w interfejsie). */
    fun onPaper(c: Int, setHl: Boolean, kind: PenKind): Int = forPen(c, setHl, kind).let { if (kind == PenKind.HIGHLIGHTER) look(it) else it }
}

/**
 * Zestaw kolorów, który może dzielić kilka narzędzi. Na listwie widać pierwsze [visible] barw, reszta czeka w panelu.
 * [hl]: barwy są atramentami zakreślacza (na papierze wychodzą jaśniejsze, patrz [InkColor.look]).
 * Pusta [name] oznacza wbudowaną nazwę zestawu o danym [id].
 */
data class ColorSet(val id: String, val name: String, val colors: List<Int>, val visible: Int, val hl: Boolean = false)

/** Pamięć jednego narzędzia: z którego zestawu bierze kolory, wybrany kolor, trzy grubości i styl linii. */
data class PenSlot(val setId: String, val color: Int, val widths: List<Float>, val widthIndex: Int = 1, val style: LineStyle = LineStyle.SOLID) {
    val width: Float get() = widths[widthIndex]
}

/**
 * Komplet piór: zestawy kolorów, pamięć każdego narzędzia piszącego i narzędzie aktywne. Wartość niezmienna;
 * każda zmiana zwraca nowy komplet, więc da się go testować bez Androida i zapisać jednym tekstem w ustawieniach.
 */
data class PenKit(val sets: List<ColorSet>, val slots: Map<PenKind, PenSlot>, val active: PenKind) {

    fun slot(kind: PenKind): PenSlot = slots.getValue(kind)
    fun set(id: String): ColorSet? = sets.firstOrNull { it.id == id }
    fun setOf(kind: PenKind): ColorSet = set(slot(kind).setId) ?: sets.first()

    fun pen(kind: PenKind): ActivePen {
        val s = slot(kind)
        return ActivePen(kind, InkColor.forPen(s.color, setOf(kind).hl, kind), s.width, s.style)
    }

    val current: ActivePen get() = pen(active)

    /** Kolory na listwie: widoczna część zestawu, a gdy wybrany jest spoza niej (ukryty albo dowolny), także on. */
    fun strip(kind: PenKind): List<Int> {
        val s = setOf(kind)
        val v = s.colors.take(s.visible)
        val c = slot(kind).color
        return if (c in v) v else v + c
    }

    fun withActive(kind: PenKind) = copy(active = kind)

    private fun edit(kind: PenKind, f: (PenSlot) -> PenSlot) = copy(slots = slots + (kind to f(slot(kind))))
    private fun editSet(id: String, f: (ColorSet) -> ColorSet) = copy(sets = sets.map { if (it.id == id) f(it) else it })

    fun withColor(kind: PenKind, color: Int) = edit(kind) { it.copy(color = color or OPAQUE) }
    fun withWidthIndex(kind: PenKind, i: Int) = edit(kind) { it.copy(widthIndex = i.coerceIn(0, WIDTHS - 1)) }
    /** Pióro „jak ten kreska”: rodzaj, kolor, styl linii i grubość (do aktualnie wybranej z trzech) stają się aktywne. */
    fun withTaken(kind: PenKind, color: Int, width: Float, style: LineStyle): PenKit =
        withActive(kind).withColor(kind, color).withStyle(kind, style).withWidth(kind, slot(kind).widthIndex, width)

    fun withStyle(kind: PenKind, style: LineStyle) = edit(kind) { it.copy(style = style) }

    fun withWidth(kind: PenKind, i: Int, w: Float): PenKit {
        val spec = PenSpec.of(kind)
        return edit(kind) { s -> s.copy(widths = s.widths.mapIndexed { j, old -> if (j == i) w.coerceIn(spec.minWidth, spec.maxWidth) else old }) }
    }

    /** Następny albo poprzedni kolor z listwy (w kółko). */
    fun stepColor(kind: PenKind, d: Int): PenKit {
        val st = strip(kind)
        val i = st.indexOf(slot(kind).color)
        return withColor(kind, st[((i + d) % st.size + st.size) % st.size])
    }

    /** Przypina narzędzie do innego zestawu. Wybrany kolor zostaje, jeśli nowy zestaw też go ma. */
    fun withSet(kind: PenKind, setId: String): PenKit {
        val ns = set(setId) ?: return this
        return edit(kind) { it.copy(setId = setId, color = if (it.color in ns.colors) it.color else ns.colors.first()) }
    }

    /**
     * Dodaje kolor tak, żeby od razu był widoczny na listwie: tuż za widocznymi, powiększając ich liczbę,
     * a przy pełnej listwie w miejsce ostatniego widocznego (ten przechodzi do ukrytych).
     */
    fun addColor(setId: String, color: Int): PenKit {
        val c = color or OPAQUE
        return editSet(setId) { s ->
            if (c in s.colors || s.colors.size >= MAX_COLORS) s
            else if (s.visible < MAX_VISIBLE) s.copy(colors = s.colors.take(s.visible) + c + s.colors.drop(s.visible), visible = s.visible + 1)
            else s.copy(colors = s.colors.take(s.visible - 1) + c + s.colors.drop(s.visible - 1))
        }
    }

    /** Usuwa kolor z zestawu (ostatniego się nie da). Narzędzie, które nim pisało, zachowuje go jako kolor spoza listy. */
    fun removeColor(setId: String, color: Int) = editSet(setId) { s ->
        if (color !in s.colors || s.colors.size <= 1) s
        else (s.colors - color).let { s.copy(colors = it, visible = s.visible.coerceAtMost(it.size)) }
    }

    fun moveColor(setId: String, from: Int, to: Int) = editSet(setId) { s ->
        if (from !in s.colors.indices || to !in s.colors.indices || from == to) s
        else s.copy(colors = s.colors.toMutableList().also { it.add(to, it.removeAt(from)) })
    }

    /** Wyciąga schowaną barwę na listwę: na jej koniec, a przy pełnej listwie w miejsce ostatniej widocznej. */
    fun showColor(setId: String, color: Int) = editSet(setId) { s ->
        if (s.colors.indexOf(color) < s.visible) s
        else (s.colors - color).let { rest ->
            if (s.visible < MAX_VISIBLE) s.copy(colors = rest.take(s.visible) + color + rest.drop(s.visible), visible = s.visible + 1)
            else s.copy(colors = rest.take(s.visible - 1) + color + rest.drop(s.visible - 1))
        }
    }

    /** Chowa barwę z listwy: zostaje w zestawie jako pierwsza ze schowanych. Ostatniej widocznej się nie chowa. */
    fun hideColor(setId: String, color: Int) = editSet(setId) { s ->
        val i = s.colors.indexOf(color)
        if (i < 0 || i >= s.visible || s.visible <= 1) s
        else (s.colors - color).let { rest -> s.copy(colors = rest.take(s.visible - 1) + color + rest.drop(s.visible - 1), visible = s.visible - 1) }
    }

    /** Zmienia barwę w zestawie; narzędzia z tego zestawu, które nią pisały, przechodzą na nową. */
    fun replaceColor(setId: String, old: Int, new: Int): PenKit {
        val c = new or OPAQUE
        val s = set(setId) ?: return this
        if (old !in s.colors || c in s.colors) return this
        return editSet(setId) { it.copy(colors = it.colors.map { x -> if (x == old) c else x }) }
            .copy(slots = slots.mapValues { (_, sl) -> if (sl.setId == setId && sl.color == old) sl.copy(color = c) else sl })
    }

    fun withVisible(setId: String, n: Int) = editSet(setId) { it.copy(visible = n.coerceIn(1, minOf(MAX_VISIBLE, it.colors.size))) }

    /** Narzędzia, które biorą kolory z danego zestawu. */
    fun users(setId: String): List<PenKind> = PenKind.entries.filter { slot(it).setId == setId }

    /**
     * Dokłada na koniec własny zestaw: kopię [from] (z jego typem) albo fabryczne barwy dla typu [hl]. Typ zostaje
     * na stałe, bo te same liczby znaczą co innego jako atrament i jako zakreślacz.
     */
    fun addSet(name: String, hl: Boolean, from: String? = null): PenKit {
        if (sets.size >= MAX_SETS) return this
        val src = from?.let { set(it) } ?: DEFAULT_SETS.first { it.id == if (hl) SET_HL else SET_INK }
        var n = 1
        while (sets.any { it.id == "u$n" }) n++
        return copy(sets = sets + src.copy(id = "u$n", name = name.trim().take(MAX_NAME)))
    }

    /** Pusta nazwa przywraca wbudowanemu zestawowi jego nazwę z tłumaczeń. */
    fun renameSet(id: String, name: String) = editSet(id) { it.copy(name = name.trim().take(MAX_NAME)) }

    /**
     * Usuwa własny zestaw. Narzędzia, które z niego brały, wracają do fabrycznego i piszą dalej tym samym kolorem
     * (jako kolorem spoza listy). Wbudowanych się nie usuwa, bo zapis i tak by je odtworzył; te się przywraca.
     */
    fun removeSet(id: String): PenKit {
        val gone = set(id) ?: return this
        if (builtIn(id)) return this
        val left = sets.filter { it.id != id }
        return copy(sets = left, slots = slots.mapValues { (k, sl) ->
            if (sl.setId != id) sl
            else defaultSet(k).let { d -> sl.copy(setId = d, color = InkColor.between(sl.color, gone.hl, left.first { it.id == d }.hl)) }
        })
    }

    /** Przywraca wbudowanemu zestawowi fabryczne barwy i ich liczbę na listwie. Nazwa nadana przez użytkownika zostaje. */
    fun resetSet(id: String): PenKit = factory(id)?.let { d -> editSet(id) { d.copy(name = it.name) } } ?: this

    /** Czy wbudowany zestaw różni się barwami albo ich liczbą na listwie od fabrycznego (jest co przywracać). */
    fun changed(id: String): Boolean = factory(id)?.let { d -> set(id)?.copy(name = "") != d } ?: false

    /** Kopiuje barwę do innego zestawu (przeliczoną, gdy jeden jest atramentowy, a drugi zakreślaczy). */
    fun copyColor(from: String, color: Int, to: String): PenKit {
        val a = set(from) ?: return this
        val b = set(to) ?: return this
        return addColor(to, InkColor.between(color, a.hl, b.hl))
    }

    /** Przenosi barwę do innego zestawu; ze źródłowego znika tylko wtedy, gdy docelowy ją ma (nie był pełny). */
    fun moveColorTo(from: String, color: Int, to: String): PenKit {
        val a = set(from) ?: return this
        val b = set(to) ?: return this
        if (from == to) return this
        val c = InkColor.between(color, a.hl, b.hl) or OPAQUE
        val k = addColor(to, c)
        return if (c in k.set(to)!!.colors) k.removeColor(from, color) else this
    }

    fun toJson(): String {
        val o = JSONObject().put("v", 1).put("a", active.code)
        val ss = JSONArray()
        for (s in sets) ss.put(JSONObject().put("id", s.id).put("n", s.name).put("c", JSONArray(s.colors)).put("vis", s.visible).put("hl", s.hl))
        val ps = JSONArray()
        for (k in PenKind.entries) slot(k).let { s ->
            ps.put(JSONObject().put("k", k.code).put("s", s.setId).put("c", s.color).put("w", JSONArray(s.widths.map { it.toDouble() })).put("i", s.widthIndex).put("st", s.style.code))
        }
        return o.put("sets", ss).put("pens", ps).toString()
    }

    companion object {
        const val WIDTHS = 3
        const val MAX_VISIBLE = 24
        const val MAX_COLORS = 24
        const val MAX_SETS = 8
        const val MAX_NAME = 24
        const val SET_INK = "ink"
        const val SET_BRUSH = "brush"
        const val SET_HL = "hl"
        private const val OPAQUE = 0xFF shl 24

        private fun ints(vararg v: Long) = v.map { it.toInt() }

        private val DEFAULT_SETS = listOf(
            ColorSet(SET_INK, "", ints(0xFF1B1D21, 0xFF454A52, 0xFF24407E, 0xFFC8433F, 0xFF3E8E5A, 0xFF2F7FC1, 0xFFE2742F, 0xFF6A3D8F, 0xFF868C95, 0xFF7A4A2B), 6),
            ColorSet(SET_BRUSH, "", ints(0xFF3E8E5A, 0xFFC8433F, 0xFF2F7FC1, 0xFFDDA11C, 0xFFD1568A, 0xFF1B1D21, 0xFF1E8C93, 0xFF5E6AD2, 0xFFE2742F, 0xFFFFFFFF), 6),
            ColorSet(SET_HL, "", ints(0xFFF5CF17, 0xFFFFA355, 0xFFFD614F, 0xFFF57BA5, 0xFFAB85F7, 0xFF5F97FF, 0xFF85CFFD, 0xFF57F3C1, 0xFF71DF77, 0xFFC7AD85), 6, hl = true),
        )

        /** Fabryczna postać wbudowanego zestawu; null dla zestawu użytkownika. */
        fun factory(id: String): ColorSet? = DEFAULT_SETS.firstOrNull { it.id == id }

        fun builtIn(id: String) = factory(id) != null

        /** Zestaw, do którego narzędzie jest przypięte fabrycznie: długopis, pióro i ołówek dzielą atramenty. */
        fun defaultSet(kind: PenKind) = when (kind) { PenKind.BRUSH -> SET_BRUSH; PenKind.HIGHLIGHTER -> SET_HL; else -> SET_INK }

        /** Fabryczne trzy grubości (pt): cienka, zwykła (domyślna z [PenSpec]) i gruba. */
        fun defaultWidths(kind: PenKind): List<Float> = when (kind) {
            PenKind.BALLPOINT -> listOf(0.85f, 1.42f, 2.27f)
            PenKind.FOUNTAIN -> listOf(1.7f, 2.83f, 4.54f)
            PenKind.BRUSH -> listOf(4.25f, 8.5f, 14.17f)
            PenKind.HIGHLIGHTER -> listOf(8.5f, 14.17f, 22.68f)
            PenKind.PENCIL -> listOf(1.13f, 1.98f, 3.4f)
        }

        private fun defaultColor(kind: PenKind) = when (kind) {
            PenKind.FOUNTAIN -> 0xFF24407E.toInt()
            PenKind.PENCIL -> 0xFF454A52.toInt()
            else -> DEFAULT_SETS.first { it.id == defaultSet(kind) }.colors.first()
        }

        val DEFAULT = PenKit(DEFAULT_SETS, PenKind.entries.associateWith { PenSlot(defaultSet(it), defaultColor(it), defaultWidths(it)) }, PenKind.BALLPOINT)

        /**
         * Przenosi dawne „zapisane pióra” do nowego układu: ich kolory trafiają na początek zestawu właściwego
         * narzędzia, ich grubości do trzech pozycji narzędzia (braki uzupełniają fabryczne), a aktywne pióro
         * wyznacza aktywne narzędzie razem z kolorem i grubością.
         */
        fun migrate(old: List<ActivePen>, activeIndex: Int): PenKit {
            if (old.isEmpty()) return DEFAULT
            val act = old[activeIndex.coerceIn(0, old.size - 1)]
            val sets = DEFAULT_SETS.map { s ->
                val mine = old.filter { defaultSet(it.kind) == s.id }.map { it.color or OPAQUE }.distinct().take(MAX_VISIBLE)
                s.copy(colors = (mine + s.colors).distinct().take(MAX_COLORS), visible = maxOf(s.visible, mine.size))
            }
            val slots = PenKind.entries.associateWith { k ->
                val spec = PenSpec.of(k)
                val mine = old.filter { it.kind == k }
                // Pióro, którym użytkownik pisał ostatnio, wygrywa z pierwszym na liście.
                val lead = if (act.kind == k) act else mine.firstOrNull()
                val ws = ArrayList<Float>()
                for (w in listOfNotNull(lead?.width) + mine.map { it.width } + defaultWidths(k)) {
                    val c = w.coerceIn(spec.minWidth, spec.maxWidth)
                    if (ws.size < WIDTHS && ws.none { kotlin.math.abs(it - c) < 0.05f }) ws.add(c)
                }
                ws.sort()
                val lw = lead?.width?.coerceIn(spec.minWidth, spec.maxWidth)
                PenSlot(defaultSet(k), lead?.color?.or(OPAQUE) ?: defaultColor(k), ws, if (lw == null) 1 else ws.indexOfFirst { kotlin.math.abs(it - lw) < 0.05f }.coerceAtLeast(0))
            }
            return PenKit(sets, slots, act.kind)
        }

        /** Czyta zapis z ustawień; brakujące lub zepsute części uzupełnia fabrycznymi, żeby pasek nigdy nie został pusty. */
        fun fromJson(json: String): PenKit? = try {
            val o = JSONObject(json)
            val sa = o.getJSONArray("sets")
            val sets = ArrayList<ColorSet>()
            for (i in 0 until sa.length()) {
                val s = sa.getJSONObject(i)
                val ca = s.getJSONArray("c")
                val colors = List(ca.length()) { ca.getInt(it) or OPAQUE }.distinct().take(MAX_COLORS)
                val id = s.getString("id")
                if (colors.isNotEmpty() && sets.none { it.id == id } && (builtIn(id) || sets.count { !builtIn(it.id) } < MAX_SETS - DEFAULT_SETS.size))
                    sets.add(ColorSet(id, s.optString("n", "").take(MAX_NAME), colors, s.optInt("vis", 6).coerceIn(1, minOf(MAX_VISIBLE, colors.size)), s.optBoolean("hl", false)))
            }
            for (d in DEFAULT_SETS) if (sets.none { it.id == d.id }) sets.add(d)
            val pa = o.optJSONArray("pens") ?: JSONArray()
            val read = HashMap<PenKind, PenSlot>()
            for (i in 0 until pa.length()) {
                val p = pa.getJSONObject(i)
                val k = PenKind.entries.firstOrNull { it.code == p.getInt("k") } ?: continue
                val spec = PenSpec.of(k)
                val wa = p.optJSONArray("w")
                val ws = List(WIDTHS) { j -> (wa?.optDouble(j, Double.NaN)?.toFloat()?.takeIf { !it.isNaN() } ?: defaultWidths(k)[j]).coerceIn(spec.minWidth, spec.maxWidth) }
                val setId = p.optString("s", "").takeIf { id -> sets.any { it.id == id } } ?: defaultSet(k)
                read[k] = PenSlot(setId, p.optInt("c", defaultColor(k)) or OPAQUE, ws, p.optInt("i", 1).coerceIn(0, WIDTHS - 1), LineStyle.fromCode(p.optInt("st", 0)))
            }
            PenKit(sets, PenKind.entries.associateWith { read[it] ?: DEFAULT.slot(it) }, PenKind.entries.firstOrNull { it.code == o.optInt("a", 0) } ?: PenKind.BALLPOINT)
        } catch (_: Exception) { null }
    }
}
