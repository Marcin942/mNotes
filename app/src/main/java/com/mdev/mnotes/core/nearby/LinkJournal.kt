package com.mdev.mnotes.core.nearby

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Czyszczenie tekstu do dziennika łącza (3b-B1): wszystko, co mogłoby wskazać urządzenie, osobę albo treść, zamienia
 * się na znacznik w ‹›. Działa na tekście już złożonym (wpisy maszyny, komunikaty wyjątków), więc łapie też to, czego
 * wywołujący nie przewidział: adresy MAC, UUID, ścieżki i adresy `content://`, nazwy plików, tekst w cudzysłowie,
 * długie ciągi szesnastkowe i Base64 (klucze, nazwy punktów po parowaniu), adresy e-mail, 6 cyfr przy słowie „kod”.
 * [names]: nazwy urządzeń znane wywołującemu (własna, drugiej strony, znalezione), usuwane niezależnie od wielkości liter.
 */
object LinkScrub {
    const val DEVICE = "‹urządzenie›"

    // „x” też: Android bez uprawnienia podaje adres zamaskowany (XX:XX:XX:XX:9C:FF), ostatnie bajty wciąż wskazują urządzenie
    private val mac = Regex("""(?i)\b[0-9a-fx]{2}(?:[:-][0-9a-fx]{2}){5}\b""")
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val ipv6 = Regex("""(?i)(?<![\w:])(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}(?:%\w+)?""")
    private val direct = Regex("""\bDIRECT-\S*""")
    // ścieżka z katalogu urządzenia do końca członu, także ze spacjami (folder „Notatki Kowalskiego”), do „ (” albo „: ”
    private val rootPath = Regex("""(?<![\w.])/(?:storage|sdcard|data|mnt)/[^\n]*?(?=\s*\(|: |$)""")
    private val uuid = Regex("""(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""")
    private val uri = Regex("""(?i)\b(?:content|file|https?)://\S+""")
    private val path = Regex("""(?<![\w.])(?:/[^\s/:;,()]+){2,}/?""")
    private val file = Regex("""(?i)[^\s/\\:;,()„”"']+\.(?:jpe?g|png|heic|heif|webp|gif|bmp|pdf|mnotes|txt|zip)\b""")
    private val quoted = Regex("""„[^”]*”|"[^"]*"|“[^”]*”|'[^']{2,}'""")
    private val email = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
    private val hex = Regex("""(?i)\b(?:mn-)?[0-9a-f]{12,}\b""")
    private val base64 = Regex("""[A-Za-z0-9+/_-]{24,}={0,2}""")
    private val code = Regex("""(?i)(kod\w*|code|cyfr\w*|digits?|sas|pin|parowa\w*|pairing)([^0-9\n]{0,24})\b\d{3}[ -]?\d{3}\b""")

    fun clean(text: String, names: Collection<String> = emptyList()): String {
        var s = text.replace('\n', ' ').replace('\r', ' ')
        // nazwy przed zamianą „|”: nazwa urządzenia z „|” inaczej już by się nie zgadzała
        for (n in names.map { it.trim() }.filter { it.length >= 2 }.distinct().sortedByDescending { it.length }) {
            s = s.replace(n, DEVICE, ignoreCase = true)
        }
        s = s.replace('|', '/')
        s = uri.replace(s, "‹adres›")
        s = rootPath.replace(s, "‹ścieżka›")
        s = mac.replace(s, "‹MAC›")
        s = direct.replace(s, "‹Wi-Fi›")
        s = ipv4.replace(s, "‹IP›")
        s = ipv6.replace(s) { m -> if ("::" in m.value || m.value.count { it == ':' } >= 4 || m.value.any { it.lowercaseChar() in 'a'..'f' }) "‹IP›" else m.value }
        s = uuid.replace(s, "‹uuid›")
        s = email.replace(s, "‹e-mail›")
        s = path.replace(s, "‹ścieżka›")
        s = file.replace(s, "‹plik›")
        s = quoted.replace(s, "‹tekst›")
        s = code.replace(s) { it.groupValues[1] + it.groupValues[2] + "‹kod›" }
        s = hex.replace(s, "‹skrót›")
        s = base64.replace(s) { m -> if (m.value.any(Char::isDigit) && m.value.any(Char::isLetter)) "‹klucz›" else m.value }
        return s.take(MAX_TEXT)
    }

    /** Błąd jako „klasa: komunikat” po czyszczeniu (bez stosu wywołań). */
    fun error(e: Throwable, names: Collection<String> = emptyList()): String =
        e.javaClass.simpleName.ifEmpty { "Exception" } + (e.message?.let { ": " + clean(it, names) } ?: "")

    const val MAX_TEXT = 300
}

/**
 * Stan urządzenia na początku sesji łącza (bez nazw i adresów): wersja Androida i nakładki, model, radio, zgody.
 * null w polu znaczy „nie dało się sprawdzić”. Składa go aplikacja, tu tylko zapis do nagłówka.
 */
data class LinkEnv(
    val android: String = "",
    val sdk: Int = 0,
    val maker: String = "",
    val model: String = "",
    val oneUi: String? = null,
    val bluetooth: Boolean? = null,
    val wifi: Boolean? = null,
    val location: Boolean? = null,
    val playServices: Boolean? = null,
    /** Krótka nazwa zgody (np. `BLUETOOTH_CONNECT`) → przyznana. */
    val permissions: Map<String, Boolean> = emptyMap(),
) {
    fun fields(): List<Pair<String, String>> = buildList {
        add("android" to "$android (API $sdk)")
        add("model" to "$maker $model".trim())
        oneUi?.let { add("one_ui" to it) }
        add("bluetooth" to yn(bluetooth)); add("wifi" to yn(wifi)); add("lokalizacja" to yn(location))
        playServices?.let { add("uslugi_google" to yn(it)) }
        for ((k, v) in permissions) add("zgoda_$k" to yn(v))
    }

    companion object {
        fun yn(b: Boolean?) = when (b) { true -> "tak"; false -> "nie"; null -> "?" }

        /**
         * Wersja One UI z `Build.VERSION.SEM_PLATFORM_INT` Samsunga (np. 150100 → „6.1”, 140500 → „5.5”): od wartości
         * odejmuje się 90000, reszta to główna×10000 + podrzędna×100. Poza Samsungiem pola nie ma (null).
         */
        fun oneUi(semPlatformInt: Int?): String? {
            val v = semPlatformInt ?: return null
            if (v < 100_000) return null
            val r = v - 90_000
            return "${r / 10_000}.${r % 10_000 / 100}"
        }
    }
}

/**
 * Dziennik łącza z anonimizacją (3b-B1), po obu stronach: sesje (jedno otwarcie okna łącza) z nagłówkiem
 * ([LinkEnv], transport, tryb, rola) i wpisami z czasem w ms od startu sesji. Każdy tekst przechodzi przez
 * [LinkScrub]; identyfikator punktu (Nearby: losowy, Bluetooth: adres MAC) trafia tu tylko jako krótki skrót z solą
 * ([peer]), a przy eksporcie skróty dostają nową sól, więc dwóch eksportów nie da się połączyć ze sobą ani z adresem.
 * Bufor w pamięci najwyżej [maxBytes] (najstarsze sesje wypadają), zapis do pliku przez [encode]/[decode].
 * Nie wątkobezpieczny: wołany z wątku maszyny łącza (w aplikacji głównego).
 */
class LinkJournal(
    private val maxBytes: Int = MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    class Entry(val ms: Long, val text: String, val code: Int? = null)

    class Session(val startedAt: Long, val header: List<Pair<String, String>>) {
        internal val list = ArrayList<Entry>()
        val entries: List<Entry> get() = list
        internal var bytes = header.sumOf { it.first.length + it.second.length + 2 } + 32
        val title: String get() = header.filter { it.first in TITLE }.joinToString(" · ") { it.second }
    }

    private val all = ArrayDeque<Session>()
    val sessions: List<Session> get() = all.toList()
    private var salt = ByteArray(16).also(random::nextBytes)
    private var names: List<String> = emptyList()

    /** Zmiana od ostatniego [encode] (aplikacja zapisuje plik tylko wtedy). */
    var dirty = false
        private set

    /** Nowa sesja; [names] to nazwy urządzeń do usuwania z każdego wpisu tej sesji (uzupełniane przez [redact]). */
    fun begin(transport: String, mode: LinkMode, role: SyncRole, env: LinkEnv, names: Collection<String> = emptyList()): Session {
        this.names = names.toList()
        val h = listOf("transport" to transport, "tryb" to mode.name.lowercase(), "rola" to if (role == SyncRole.HOST) "tablet" else "telefon") +
            env.fields().map { (k, v) -> k to LinkScrub.clean(v, this.names) }
        val s = Session(clock(), h)
        all.addLast(s)
        dirty = true
        trim()
        return s
    }

    /** Dodatkowe nazwy do usuwania (np. znalezione urządzenia w parowaniu). */
    fun redact(name: String) { if (name.length >= 2 && name !in names) names = names + name }

    /** Wpis w bieżącej sesji (bez sesji: nic). */
    fun add(text: String, code: Int? = null) {
        val s = all.lastOrNull() ?: return
        if (s.list.size >= SESSION_ENTRIES) {
            if (s.list.size == SESSION_ENTRIES) s.list += Entry(clock() - s.startedAt, "… dalsze wpisy pominięte (limit sesji)")
            return
        }
        val e = Entry(clock() - s.startedAt, LinkScrub.clean(text, names), code)
        s.list += e
        s.bytes += e.text.length + 16
        dirty = true
        trim()
    }

    fun error(what: String, e: Throwable) = add("$what: ${LinkScrub.error(e, names)}")

    /** Skrót identyfikatora punktu: `#` + 6 znaków HMAC z solą tej instancji (nigdy sam identyfikator). */
    fun peer(id: String): String = "#" + SyncCrypto.hex(SyncCrypto.hmac(salt, id.toByteArray())).take(6)

    fun clear() { all.clear(); dirty = true }

    private fun trim() {
        while (all.size > 1 && all.sumOf { it.bytes } > maxBytes) all.removeFirst()
        val s = all.lastOrNull() ?: return
        while (s.bytes > maxBytes && s.list.isNotEmpty()) { val e = s.list.removeAt(0); s.bytes -= e.text.length + 16 }
    }

    /** Zapis do pliku prywatnego (format wierszowy, tekst już wyczyszczony). */
    fun encode(): String = buildString {
        for (s in all) {
            append("S|").append(s.startedAt).append('|').append(s.header.joinToString(";") { "${esc(it.first)}=${esc(it.second)}" }).append('\n')
            for (e in s.list) append("E|").append(e.ms).append('|').append(e.code ?: "").append('|').append(e.text).append('\n')
        }
    }.also { dirty = false }

    /**
     * Plik do wysłania agentowi: nagłówek z listą tego, czego w pliku nie ma, sesje od najstarszej, czasy względne.
     * Skróty punktów `#xxxxxx` przechodzą przez HMAC z nową, niezapisywaną solą, a cały tekst jeszcze raz przez [LinkScrub].
     */
    fun export(app: String = "mNotes"): String {
        val fresh = ByteArray(16).also(random::nextBytes)
        val token = Regex("#[0-9a-f]{6}\\b")
        fun out(t: String) = token.replace(LinkScrub.clean(t)) { "#" + SyncCrypto.hex(SyncCrypto.hmac(fresh, it.value.toByteArray())).take(6) }
        return buildString {
            appendLine("$app — dziennik łącza telefon–tablet, eksport ${date(clock())}")
            appendLine("Jest: etapy z czasem od startu sesji (ms), transport, stan Bluetooth/Wi-Fi/lokalizacji i zgód, model i wersja")
            appendLine("Androida, rozmiary i czasy części, ponowienia, błędy (klasa i komunikat po czyszczeniu).")
            appendLine("Nie ma: nazw urządzeń, adresów MAC, identyfikatorów punktów (tylko skrót z nową solą przy każdym eksporcie),")
            appendLine("kodów parowania, kluczy, nazw plików, treści zdjęć i zeszytów, tytułów, kont, położenia, ścieżek.")
            all.forEachIndexed { i, s ->
                appendLine()
                appendLine("== Sesja ${i + 1}: ${date(s.startedAt)}")
                for ((k, v) in s.header) appendLine("$k: ${out(v)}")
                for (e in s.list) appendLine("+${e.ms} ms  ${out(e.text)}${e.code?.let { "  (kod $it)" } ?: ""}")
            }
        }
    }

    companion object {
        const val MAX_BYTES = 500_000
        const val SESSION_ENTRIES = 3_000
        private val TITLE = setOf("transport", "tryb", "rola")

        fun date(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }.format(Date(ms))

        private fun esc(s: String) = s.replace("%", "%25").replace(";", "%3B").replace("=", "%3D").replace("\n", " ")
        private fun unesc(s: String) = s.replace("%3D", "=").replace("%3B", ";").replace("%25", "%")

        /** Odczyt z [encode]; uszkodzone wiersze są pomijane (dziennik nie może zablokować łącza). */
        fun decode(text: String, maxBytes: Int = MAX_BYTES, clock: () -> Long = System::currentTimeMillis): LinkJournal {
            val j = LinkJournal(maxBytes, clock)
            for (line in text.lineSequence()) {
                val p = line.split('|', limit = 4)
                when {
                    p[0] == "S" && p.size >= 3 -> {
                        val at = p[1].toLongOrNull() ?: continue
                        val h = if (p[2].isEmpty()) emptyList() else p[2].split(';').mapNotNull { kv ->
                            val i = kv.indexOf('='); if (i <= 0) null else unesc(kv.substring(0, i)) to unesc(kv.substring(i + 1))
                        }
                        j.all.addLast(Session(at, h))
                    }
                    p[0] == "E" && p.size == 4 -> {
                        val s = j.all.lastOrNull() ?: continue
                        val ms = p[1].toLongOrNull() ?: continue
                        val e = Entry(ms, p[3].take(LinkScrub.MAX_TEXT), p[2].toIntOrNull())
                        s.list += e; s.bytes += e.text.length + 16
                    }
                }
            }
            j.trim()
            j.dirty = false
            return j
        }
    }
}
