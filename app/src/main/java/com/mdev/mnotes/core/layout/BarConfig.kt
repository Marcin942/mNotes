package com.mdev.mnotes.core.layout

/** Jak pozycja zachowuje się na pasku narzędzi. */
object BarMode {
    /** Na pasku, gdy jest miejsce; przy ciasnocie wędruje pod „⋯” według ważności. */
    const val AUTO = 0

    /** Zawsze na pasku, także gdy jest ciasno. */
    const val ALWAYS = 1

    /** Tylko pod „⋯”. */
    const val MENU = 2

    /**
     * LP-5: wyłączone — narzędzia nie ma nigdzie (pasek, „⋯”, paleta, skróty, przycisk rysika); wraca tylko przez
     * Ustawienia. Starsza wersja nie zna tej wartości i czyta ją jak [AUTO], więc zapis się nie psuje.
     */
    const val OFF = 3
}

/**
 * Układ paska narzędzi wybrany przez użytkownika: kolejność pozycji i tryb każdej z nich. Zapisuje się jako dwa
 * krótkie napisy w ustawieniach; nieznane id (np. po starszej wersji) są pomijane, a nowe pozycje dopisują się na
 * końcu, więc układ zapisany dawniej nie psuje się po aktualizacji.
 */
class BarConfig private constructor(val order: List<String>, private val modes: Map<String, Int>) {
    fun mode(id: String): Int = modes[id] ?: BarMode.AUTO

    /** LP-5: narzędzie wyłączone w Ustawieniach. */
    fun isOff(id: String): Boolean = mode(id) == BarMode.OFF

    /** Czy polecenie palety/skrótu [cmdId] jest dostępne: nie należy do wyłączonego narzędzia. */
    fun allows(cmdId: String): Boolean = COMMANDS[cmdId]?.let { !isOff(it) } ?: true

    /** Pierwsze niewyłączone pióro w kolejności paska; tu wraca aktywne pióro, gdy zostanie wyłączone. */
    fun firstPen(): String = order.first { it in PENS && !isOff(it) }

    /** Czy pozycję wolno wyłączyć: nie Cofnij/Ponów (to nie narzędzia) i nie ostatnie włączone pióro. */
    fun canTurnOff(id: String): Boolean =
        id in DEFAULT_ORDER && id !in KEEP && (id !in PENS || PENS.any { it != id && !isOff(it) })

    val isDefault: Boolean get() = order == DEFAULT_ORDER && modes.isEmpty()

    /** Przesuwa [id] o [delta] miejsc wśród pozycji, które nie są pod „⋯”; na krańcu nic się nie dzieje. */
    fun move(id: String, delta: Int): BarConfig {
        val at = order.indexOf(id)
        if (at < 0 || delta == 0) return this
        val step = if (delta > 0) 1 else -1
        var to = at
        var left = Math.abs(delta)
        var i = at + step
        while (i in order.indices && left > 0) {
            if (onBar(order[i]) || !onBar(id)) { to = i; left-- }
            i += step
        }
        if (to == at) return this
        val next = order.toMutableList()
        next.removeAt(at); next.add(to, id)
        return BarConfig(next, modes)
    }

    private fun onBar(id: String) = mode(id) == BarMode.AUTO || mode(id) == BarMode.ALWAYS

    fun withMode(id: String, mode: Int): BarConfig {
        if (id !in DEFAULT_ORDER || mode == mode(id)) return this
        if (mode == BarMode.OFF && !canTurnOff(id)) return this
        val next = modes.toMutableMap()
        if (mode == BarMode.AUTO) next.remove(id) else next[id] = mode
        return BarConfig(order, next)
    }

    fun reset(): BarConfig = DEFAULT

    fun orderString(): String = order.joinToString(",")

    fun modesString(): String = modes.entries.sortedBy { order.indexOf(it.key) }.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        /** Stabilne id pozycji paska w kolejności domyślnej. */
        val DEFAULT_ORDER = listOf(
            "undo", "redo", "ballpoint", "fountain", "pencil", "brush", "highlighter", "eraser", "painter", "fill",
            "pipette", "lasso", "text", "laser", "hand", "tape", "space", "ruler", "protractor", "geometry", "curve", "snip",
        )

        /** Pióra na pasku (id jak `PenKind.name` małymi literami); co najmniej jedno zostaje włączone. */
        val PENS = listOf("ballpoint", "fountain", "pencil", "brush", "highlighter")

        /** Pozycje, których nie da się wyłączyć: to nie narzędzia, a skróty Ctrl+Z/Ctrl+Y muszą działać zawsze. */
        private val KEEP = setOf("undo", "redo")

        /** Narzędzie edytora (nazwa `Tool`) → id pozycji paska. Pióro (PEN) zależy od rodzaju, więc go tu nie ma. */
        val TOOLS = mapOf(
            "ERASER" to "eraser", "LASSO" to "lasso", "TEXT" to "text", "LASER" to "laser", "HAND" to "hand",
            "SPACE" to "space", "TAPE" to "tape", "PAINTER" to "painter", "FILL" to "fill", "CURVE" to "curve",
            "GEO" to "geometry", "SNIP" to "snip",
        )

        /**
         * Polecenie palety i skrótów → narzędzie, które je wyłącza. Polecenia działające na tym, co już jest w zeszycie
         * (zaznacz wszystko, odkryj taśmy, wycinek zaznaczenia), zostają: dotyczą treści, nie narzędzia.
         */
        val COMMANDS = mapOf(
            "toolEraser" to "eraser", "eraserSettings" to "eraser", "eraserObject" to "eraser", "eraserPixel" to "eraser",
            "toolPainter" to "painter", "toolFill" to "fill", "toolLasso" to "lasso", "toolText" to "text",
            "toolLaser" to "laser", "toolHand" to "hand", "toolCurve" to "curve", "toolSnip" to "snip",
            "snipClipboard" to "snip", "toolSpace" to "space", "ruler" to "ruler", "protractor" to "protractor",
            "toolGeo" to "geometry", "toolTape" to "tape", "pipette" to "pipette",
        )

        val DEFAULT = BarConfig(DEFAULT_ORDER, emptyMap())

        fun parse(order: String, modes: String): BarConfig {
            val seen = LinkedHashSet<String>()
            for (p in order.split(',')) { val id = p.trim(); if (id in DEFAULT_ORDER) seen.add(id) }
            for (id in DEFAULT_ORDER) seen.add(id)
            val m = HashMap<String, Int>()
            for (p in modes.split(',')) {
                val kv = p.split(':')
                if (kv.size != 2) continue
                val id = kv[0].trim(); val v = kv[1].trim().toIntOrNull() ?: continue
                if (id in DEFAULT_ORDER && (v == BarMode.ALWAYS || v == BarMode.MENU || (v == BarMode.OFF && id !in KEEP))) m[id] = v
            }
            // Wszystkie pióra wyłączone (np. zapis ręczny) — pierwsze wraca, bo pisać trzeba czymś.
            if (PENS.all { m[it] == BarMode.OFF }) seen.first { it in PENS }.let { m.remove(it) }
            return BarConfig(seen.toList(), m)
        }
    }
}
