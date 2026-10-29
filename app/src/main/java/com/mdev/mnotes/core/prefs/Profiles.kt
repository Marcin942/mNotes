package com.mdev.mnotes.core.prefs

/** Wartość ustawienia w profilu — te same typy co w SharedPreferences. */
sealed class PVal {
    data class I(val v: Int) : PVal()
    data class B(val v: Boolean) : PVal()
    data class F(val v: Float) : PVal()
    data class S(val v: String) : PVal()

    fun encode(): String = when (this) {
        is I -> "i:$v"
        is B -> "b:$v"
        is F -> "f:$v"
        is S -> "s:" + Profiles.esc(v)
    }

    companion object {
        fun decode(s: String): PVal? {
            if (s.length < 2 || s[1] != ':') return null
            val r = s.substring(2)
            return when (s[0]) {
                'i' -> r.toIntOrNull()?.let { I(it) }
                'b' -> when (r) { "true" -> B(true); "false" -> B(false); else -> null }
                'f' -> r.toFloatOrNull()?.let { F(it) }
                's' -> S(Profiles.unesc(r))
                else -> null
            }
        }
    }
}

/** Profil pracy („Wykład”, „Rysowanie”, „Tylko rysik”): nazwa i nadpisywane ustawienia. */
data class Profile(val id: String, val name: String, val values: Map<String, PVal>)

/**
 * Profile ustawień (2b-A12): przełączenie profilu nadpisuje wybrany podzbiór ustawień, a wyłączenie wraca dokładnie do
 * stanu sprzed profilu (kopia nadpisanych wartości). Profil obejmuje tylko „jak pracuję” — dotyk, gesty, paski, widok;
 * nigdy dane (pióra, stanowiska, synchronizacja, blokada).
 */
object Profiles {
    /** Ustawienie, które profil może nadpisać; [group] — sekcja w oknie profilu ([GROUPS]). */
    class Key(val key: String, val group: Int)

    /** Sekcje kluczy: nazwa PL i EN. */
    val GROUPS = listOf(
        "Dotyk palcem i dłoń" to "Finger touch and palm",
        "Gesty i przycisk rysika" to "Gestures and stylus button",
        "Pasek narzędzi (widoczne narzędzia, położenie, rozmiar)" to "Toolbar (visible tools, position, size)",
        "Pasek przy gumce, lassie i laserze" to "Strip with eraser, lasso and laser",
        "Widok i przyciąganie" to "View and snapping",
    )

    val KEYS: List<Key> = listOf(
        Key("input.finger", 0), Key("input.palm_rejection", 0),
        Key("input.two_finger_undo", 1), Key("input.double_tap", 1), Key("input.triple_tap", 1), Key("input.pull_add_page", 1),
        Key("input.pen_button", 1), Key("input.hold_to_shape", 1), Key("input.hold_curve", 1), Key("tools.return_to_pen", 1),
        Key("toolbar.order", 2), Key("toolbar.modes", 2), Key("toolbar.position", 2), Key("toolbar.size", 2), Key("toolbar.options", 2), Key("ui.undo_dock", 2),
        Key("toolbar.helper_strip", 3), Key("toolbar.helper_menu", 3),
        Key("view.minimap", 4), Key("view.scroll_margin", 4),Key("pages.theme", 4), Key("ui.compact", 4), Key("snap.attach", 4), Key("axes.snap", 4),
        Key(Reader.KEY, 4),
    )

    private val known = KEYS.map { it.key }.toSet()

    fun known(key: String) = key in known

    fun keysOf(group: Int): List<String> = KEYS.filter { it.group == group }.map { it.key }

    // — Dotyk: najważniejsza różnica między profilami, więc ma własne skróty —
    const val FINGER = "input.finger"
    const val FINGER_SCROLL = 0
    const val FINGER_DRAW = 1
    const val FINGER_OFF = 2

    // — Plik: wiersze z tabulatorami, bez JSON (czytelny ręcznie i w testach na JVM) —

    private const val HEADER = "mnotes-profiles 1"

    fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    fun unesc(s: String): String {
        val b = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                b.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] }); i += 2
            } else { b.append(c); i++ }
        }
        return b.toString()
    }

    fun encode(list: List<Profile>): String {
        val b = StringBuilder(HEADER).append('\n')
        for (p in list) {
            b.append("P\t").append(esc(p.id)).append('\t').append(esc(p.name)).append('\n')
            for ((k, v) in p.values) if (known(k)) b.append("\t").append(k).append('\t').append(v.encode()).append('\n')
        }
        return b.toString()
    }

    /** Odczyt pliku profili; nieznane klucze i złe wiersze są pomijane (plik z nowszej wersji też się wczyta). */
    fun decode(s: String): List<Profile> {
        val out = ArrayList<Profile>()
        var id: String? = null; var name = ""; var vals = LinkedHashMap<String, PVal>()
        fun flush() { id?.let { out.add(Profile(it, name, vals)) } }
        for (line in s.lineSequence()) {
            if (line.startsWith("P\t")) {
                flush()
                val parts = line.split('\t')
                id = parts.getOrNull(1)?.let(::unesc)?.takeIf { it.isNotEmpty() }
                name = parts.getOrNull(2)?.let(::unesc) ?: ""
                vals = LinkedHashMap()
            } else if (line.startsWith("\t") && id != null) {
                val parts = line.split('\t')
                val k = parts.getOrNull(1) ?: continue
                val v = parts.getOrNull(2)?.let(PVal::decode) ?: continue
                if (known(k)) vals[k] = v
            }
        }
        flush()
        return out
    }

    /** Kopia wartości (zapis w jednym ustawieniu): ten sam format co profil bez nazwy. */
    fun encodeValues(m: Map<String, PVal>): String = if (m.isEmpty()) "" else encode(listOf(Profile("_", "", m)))
    fun decodeValues(s: String): Map<String, PVal> = decode(s).firstOrNull()?.values ?: emptyMap()

    /** Wynik przełączenia: wartości do zapisania i nowa kopia stanu sprzed profilu. */
    class Switch(val write: Map<String, PVal>, val backup: Map<String, PVal>)

    /**
     * Przełącza na [target] (null = bez profilu). [backup] — kopia sprzed aktywnego profilu (pusta, gdy żaden nie
     * działa), [current] — bieżąca wartość klucza. Najpierw powrót, potem nowy profil, więc profile się nie sklejają:
     * A → B → brak daje dokładnie stan sprzed A.
     */
    fun switch(target: Profile?, backup: Map<String, PVal>, current: (String) -> PVal?): Switch {
        val write = LinkedHashMap<String, PVal>(backup)
        val next = LinkedHashMap<String, PVal>()
        if (target != null) for ((k, v) in target.values) if (known(k)) {
            (backup[k] ?: current(k))?.let { next[k] = it }
            write[k] = v
        }
        return Switch(write, next)
    }

    /** Profil z bieżących ustawień dla kluczy [keys] („zapamiętaj, jak teraz pracuję”). */
    fun capture(id: String, name: String, keys: Collection<String>, current: (String) -> PVal?): Profile =
        Profile(id, name, LinkedHashMap<String, PVal>().also { m -> for (k in keys) if (known(k)) current(k)?.let { m[k] = it } })

    fun newId(existing: Collection<String>): String {
        var n = existing.size + 1
        while ("p$n" in existing) n++
        return "p$n"
    }

    /** Import: profile z pliku dopisane na końcu; przy kolizji id dostają nowe, nazwy zostają. */
    fun merge(existing: List<Profile>, incoming: List<Profile>): List<Profile> {
        val out = existing.toMutableList()
        for (p in incoming) {
            val ids = out.map { it.id }
            out.add(if (p.id in ids) p.copy(id = newId(ids)) else p)
        }
        return out
    }

    /** Następny profil w kolejce przełączania (skrót, paleta): brak → pierwszy → … → ostatni → brak. */
    fun next(list: List<Profile>, active: String?): Profile? {
        val i = list.indexOfFirst { it.id == active }
        return list.getOrNull(i + 1)
    }

    /** Gotowe profile na start (użytkownik może je zmienić albo usunąć). */
    fun builtIns(pl: Boolean): List<Profile> {
        fun t(p: String, e: String) = if (pl) p else e
        return listOf(
            Profile("wyklad", t("Wykład", "Lecture"), linkedMapOf(
                FINGER to PVal.I(FINGER_SCROLL), "input.palm_rejection" to PVal.B(true), "input.hold_curve" to PVal.B(false),
                "view.minimap" to PVal.B(false), "toolbar.helper_strip" to PVal.I(0),
            )),
            Profile("rysowanie", t("Rysowanie", "Drawing"), linkedMapOf(
                FINGER to PVal.I(FINGER_SCROLL), "input.hold_to_shape" to PVal.B(true), "snap.attach" to PVal.B(true),
                "axes.snap" to PVal.B(false), "view.minimap" to PVal.B(true), "toolbar.helper_strip" to PVal.I(1),
            )),
            Profile("rysik", t("Tylko rysik", "Stylus only"), linkedMapOf(
                FINGER to PVal.I(FINGER_OFF), "input.palm_rejection" to PVal.B(true), "input.two_finger_undo" to PVal.B(false),
                "input.double_tap" to PVal.I(3), "input.triple_tap" to PVal.I(3), "input.pull_add_page" to PVal.B(false),
            )),
            // 3c-A9: przeglądanie notatek (telefon, powtórka): bez pasków, palec przewija, nic się nie zmieni przypadkiem.
            Profile(READER_ID, t("Do odczytu", "Reading"), linkedMapOf(
                Reader.KEY to PVal.B(true), FINGER to PVal.I(FINGER_SCROLL), "input.double_tap" to PVal.I(2), "input.triple_tap" to PVal.I(3),
                "view.minimap" to PVal.B(false),
            )),
        )
    }

    const val READER_ID = "odczyt"

    /**
     * Gotowe profile dodane w nowszej wersji trafiają też do istniejącej listy, ale tylko raz: [seen] — id już
     * zaproponowanych. Usunięty przez użytkownika profil nie wraca. Zwraca nową listę i nowy zbiór [seen].
     */
    fun addNewBuiltIns(list: List<Profile>, seen: Set<String>, builtIns: List<Profile>): Pair<List<Profile>, Set<String>> {
        val ids = list.map { it.id }.toSet()
        val add = builtIns.filter { it.id !in seen && it.id !in ids }
        return (list + add) to (seen + builtIns.map { it.id })
    }

    // — 2c-A11: ręczne zmiany przy włączonym profilu i przypięcie na czas pracy —

    /**
     * Ręczne zmiany [edits] (klucz → wartość przed i po) zapisane w profilu [p]. Kopia stanu sprzed profilu dostaje
     * wartość „przed” kluczy, których profil dotąd nie miał, więc wyłączenie profilu cofa i tę zmianę.
     */
    fun adopt(p: Profile, backup: Map<String, PVal>, edits: Map<String, Pair<PVal, PVal>>): Pair<Profile, Map<String, PVal>> {
        val vals = LinkedHashMap(p.values)
        val b = LinkedHashMap(backup)
        for ((k, e) in edits) if (known(k)) {
            if (k !in p.values && k !in b) b[k] = e.first
            vals[k] = e.second
        }
        return p.copy(values = vals) to b
    }

    /**
     * „Nie zapisuj w profilu”: zmiany działają do wyłączenia profilu. Klucze spoza profilu dostają w kopii wartość sprzed
     * zmiany, więc wyłączenie je cofa (3c-A16: zostawały na stałe, a „Tak” — przez [adopt] — cofało je poprawnie).
     */
    fun keepUntilOff(p: Profile, backup: Map<String, PVal>, edits: Map<String, Pair<PVal, PVal>>): Map<String, PVal> {
        val b = LinkedHashMap(backup)
        for ((k, e) in edits) if (known(k) && k !in p.values && k !in b) b[k] = e.first
        return b
    }

    /**
     * Zmiana ręki (3c-A13) odbija położenia zależne od strony także w profilach i w kopii sprzed profilu. 3c-A16: bez tego
     * po wyłączeniu profilu pasek i listwa wracały na stronę dla poprzedniej ręki, a ręka zostawała nowa.
     */
    fun mirrorHand(values: Map<String, PVal>, from: Int, to: Int): Map<String, PVal> {
        if (from == to) return values
        val out = LinkedHashMap(values)
        fun int(k: String, f: (Int) -> Int) { (out[k] as? PVal.I)?.let { out[k] = PVal.I(f(it.v)) } }
        int("toolbar.position", com.mdev.mnotes.core.layout.Handedness::toolbar)
        int("toolbar.options", com.mdev.mnotes.core.layout.Handedness::options)
        int("ui.undo_dock", com.mdev.mnotes.core.layout.Handedness::undoDock)
        int("view.minimap_corner", com.mdev.mnotes.core.layout.Handedness::miniMap)
        if (to == com.mdev.mnotes.core.layout.Handedness.LEFT && "view.scroll_margin" in out) out["view.scroll_margin"] = PVal.B(true)
        return out
    }

    /** Profil przypięty na czas pracy: [pinned] włączony przy otwarciu, [before] — aktywny wcześniej ("" — żaden). */
    data class Pin(val pinned: String, val before: String)

    /** Przypięcie [target] przy aktywnym [active]; kolejne (stanowisko po zeszycie) zachowuje pierwotne „przed”. */
    fun pin(session: Pin?, target: String, active: String) = Pin(target, session?.before ?: active)

    /** Profil do przywrócenia przy wyjściu ("" — bez profilu); null, gdy w trakcie zmieniono go ręcznie albo nie ma czego cofać. */
    fun unpin(session: Pin?, active: String): String? = session?.takeIf { it.pinned == active && it.before != active }?.before

    // — Przypięcia: zeszyt → profil („nb=id;nb2=id2”) —

    fun pinsDecode(s: String): Map<String, String> =
        s.split(';').mapNotNull { e -> val i = e.indexOf('='); if (i <= 0 || i == e.length - 1) null else e.substring(0, i) to e.substring(i + 1) }.toMap()

    fun pinsEncode(m: Map<String, String>): String = m.entries.joinToString(";") { "${it.key}=${it.value}" }
}
