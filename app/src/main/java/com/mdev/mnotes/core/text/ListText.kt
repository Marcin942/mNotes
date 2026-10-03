package com.mdev.mnotes.core.text

/**
 * Listy w polu tekstowym (EDT-042): znaczniki są zwykłym tekstem („• ”, „1. ”, „☐ ”), więc działają w każdym
 * eksporcie i formacie bez zmian modelu. Logika czysta — testowana jednostkowo.
 */
object ListText {
    enum class Kind { BULLET, NUMBER, CHECK }

    class Result(val text: String, val selStart: Int, val selEnd: Int)

    private class Marker(val indent: Int, val len: Int, val kind: Kind, val number: Int)

    private val rx = Regex("^([ \\t]*)(•|(\\d{1,4})\\.|☐|☑) ")

    private fun parse(line: String): Marker? {
        val m = rx.find(line) ?: return null
        val tok = m.groupValues[2]
        val kind = when {
            tok == "•" -> Kind.BULLET
            tok == "☐" || tok == "☑" -> Kind.CHECK
            else -> Kind.NUMBER
        }
        return Marker(m.groupValues[1].length, m.value.length, kind, m.groupValues[3].toIntOrNull() ?: 0)
    }

    private fun marker(kind: Kind, n: Int) = when (kind) { Kind.BULLET -> "• "; Kind.NUMBER -> "$n. "; Kind.CHECK -> "☐ " }

    /** Włącza / wyłącza listę [kind] dla wszystkich wierszy dotkniętych zaznaczeniem [s, e). */
    fun toggle(text: String, s0: Int, e0: Int, kind: Kind): Result {
        val s = s0.coerceIn(0, text.length); val e = e0.coerceIn(s, text.length)
        val from = text.lastIndexOf('\n', s - 1) + 1
        val endIdx = text.indexOf('\n', if (e > s) e - 1 else e)
        val to = if (endIdx < 0) text.length else endIdx
        val lines = text.substring(from, to).split('\n')
        val targets = lines.indices.filter { lines[it].isNotBlank() }.ifEmpty { lines.indices.toList() }
        val allSame = targets.all { parse(lines[it])?.kind == kind }
        var n = 1
        var firstDelta = 0; var total = 0
        val out = lines.mapIndexed { i, line ->
            if (i !in targets) return@mapIndexed line
            val mk = parse(line)
            val indent = mk?.indent ?: (line.length - line.trimStart(' ', '\t').length)
            val body = if (mk != null) line.substring(mk.len) else line.substring(indent)
            val pre = line.substring(0, indent)
            val res = if (allSame) pre + body else pre + marker(kind, n++) + body
            val d = res.length - line.length
            if (i == targets.first()) firstDelta = d
            total += d
            res
        }
        val joined = out.joinToString("\n")
        val nt = text.substring(0, from) + joined + text.substring(to)
        val ns = (s + firstDelta).coerceAtLeast(from)
        val ne = (e + total).coerceAtLeast(ns).coerceAtMost(nt.length)
        return Result(nt, ns.coerceAtMost(nt.length), ne)
    }

    /**
     * Enter w wierszu listy: dopisuje następny znacznik; Enter w pustym punkcie kończy listę.
     * [old] — tekst przed edycją, [new] — po (z wstawionym „\n” tuż przed [cursor]). Zwraca null, gdy to nie Enter w liście.
     */
    fun onEnter(old: String, new: String, cursor: Int): Result? {
        val i = cursor - 1
        if (i < 0 || i >= new.length || new.length != old.length + 1 || new[i] != '\n' || new.removeRange(i, i + 1) != old) return null
        val ls = old.lastIndexOf('\n', i - 1) + 1
        val line = old.substring(ls, i)
        val mk = parse(line) ?: return null
        if (line.substring(mk.len).isBlank()) {
            val t = old.substring(0, ls) + old.substring(i)
            return Result(t, ls, ls)
        }
        val next = marker(mk.kind, mk.number + 1)
        val pre = "\n" + line.substring(0, mk.indent) + next
        val t = old.substring(0, i) + pre + old.substring(i)
        val c = i + pre.length
        return Result(t, c, c)
    }
}
