package com.mdev.mnotes.core.nearby

/**
 * Zdjęcie przez łącze w częściach z potwierdzeniami (`docs/NEARBY-PRZEBUDOWA.md` §3.4): nadawca wie, ile doszło
 * („2 z 3 · 64 %”), część bez potwierdzenia wraca po [TIMEOUT_MS], a po zerwaniu wysyłanie wznawia się od pierwszej
 * niepotwierdzonej. Każda część niesie nagłówek całości (nazwa, typ, liczba części, SHA-256), więc odbiorca może
 * zacząć składać od dowolnej. Szyfrowanie robi [SyncCodec], tu tylko porządek części. Części czyta się z [source]
 * dopiero przy wysyłaniu, więc zeszyt z pliku nie trafia w całości do pamięci.
 */
class PhotoSender(
    val id: String,
    private val name: String,
    private val mime: String,
    private val source: LinkFile,
    partSize: Int = PART,
) {
    constructor(id: String, name: String, mime: String, bytes: ByteArray, partSize: Int = PART) : this(id, name, mime, LinkFile.Memory(bytes), partSize)

    /** Długość całego pliku w bajtach (do paska partii). */
    val length: Long = source.length
    val count: Int = maxOf(1L, (length + partSize - 1) / partSize).toInt()
    private val size = partSize
    private val sha = source.sha256()
    private val acked = BooleanArray(count)
    private val sentAt = LongArray(count) { -1L }
    private val tries = IntArray(count)

    val done: Boolean get() = acked.all { it }

    /** Część przekroczyła [MAX_TRIES] wysłań bez potwierdzenia: dalej nie ma sensu. */
    var failed = false
        private set

    /** Ułamek bajtów potwierdzonych przez odbiorcę. */
    val progress: Float get() = if (length == 0L) (if (done) 1f else 0f) else (0 until count).filter { acked[it] }.sumOf { partLength(it).toLong() }.toFloat() / length

    /** Ile części już potwierdzono (do zdania „część 2 z 3”). */
    val confirmed: Int get() = acked.count { it }

    private fun partLength(i: Int): Int = minOf(size.toLong(), length - i.toLong() * size).coerceAtLeast(0).toInt()

    fun part(i: Int): SyncMsg.PhotoPart =
        SyncMsg.PhotoPart(id, i, count, sha, name, mime, source.read(i.toLong() * size, partLength(i)))

    /**
     * Części do wysłania w chwili [now]: najwyżej [WINDOW] w drodze; niepotwierdzona po [TIMEOUT_MS] idzie jeszcze raz.
     * Po [MAX_TRIES] wysłaniach jednej części [failed] = true i nic już nie wychodzi.
     */
    fun due(now: Long): List<SyncMsg.PhotoPart> {
        if (failed || done) return emptyList()
        val out = ArrayList<SyncMsg.PhotoPart>()
        var inFlight = (0 until count).count { !acked[it] && sentAt[it] >= 0 && now - sentAt[it] < TIMEOUT_MS }
        for (i in 0 until count) {
            if (acked[i] || inFlight >= WINDOW) continue
            val waiting = sentAt[i] >= 0 && now - sentAt[i] < TIMEOUT_MS
            if (waiting) continue
            if (tries[i] >= MAX_TRIES) { failed = true; return emptyList() }
            tries[i]++
            sentAt[i] = now
            inFlight++
            out += part(i)
        }
        return out
    }

    /** Która to próba wysłania części [index] (1 = pierwsza). */
    fun tries(index: Int): Int = tries.getOrElse(index) { 0 }

    /** Ile ms minęło od ostatniego wysłania części [index]; null, gdy jeszcze nie wyszła albo już potwierdzona. */
    fun sinceSent(index: Int, now: Long): Long? =
        if (index !in 0 until count || acked[index] || sentAt[index] < 0) null else now - sentAt[index]

    /** Potwierdzenie części [index]; false dla obcego albo powtórzonego. */
    fun ack(index: Int): Boolean {
        if (index !in 0 until count || acked[index]) return false
        acked[index] = true
        return true
    }

    /** Po zerwaniu łącza: wszystko niepotwierdzone wyjdzie od nowa przy następnym [due] (bez liczenia jako próba). */
    fun reset() {
        for (i in 0 until count) if (!acked[i]) { sentAt[i] = -1L; tries[i] = 0 }
    }

    /** Ile razy zdjęcie utknęło ([failed]) i wróciło do kolejki. */
    var rounds = 0
        private set

    /** Zdjęcie po [failed] wraca do gry: niepotwierdzone części dostają znów [MAX_TRIES] prób, potwierdzone zostają. */
    fun rearm() {
        failed = false
        rounds++
        reset()
    }

    companion object {
        /** 256 KB: z zapasem pod `ConnectionsClient.MAX_BYTES_DATA_SIZE` (1 047 552 B) po szyfrowaniu i nagłówku. */
        const val PART = 256 * 1024
        const val WINDOW = 2
        const val TIMEOUT_MS = 15_000L
        const val MAX_TRIES = 4
    }
}

/**
 * Składa części od [PhotoSender]: każdą potwierdza (także powtórzoną), a po ostatniej sprawdza SHA-256. Zdjęcie już
 * złożone nie wraca drugi raz (nadawca, który nie dostał `PhotoDone`, dostaje je ponownie, bez drugiego wstawienia).
 * Części idą od razu do pliku z [files] pod przesunięciem `index × długość części`; w pamięci zostaje najwyżej ostatnia
 * część, która przyszła przed pozostałymi (jej przesunięcie wynika z długości innej części).
 */
class PhotoReceiver(private val maxBytes: Int = LinkChunks.MAX_MESSAGE, private val files: LinkFiles = LinkFiles.MEMORY) {
    /** [reply]: wiadomości do odesłania; [file]: gotowy plik do wstawienia (raz); [bad]: suma kontrolna się nie zgadza. */
    class Result(val reply: List<SyncMsg>, val file: LinkReceived? = null, val bad: Boolean = false) {
        /** Cały plik w pamięci (testy, małe zdjęcia). */
        val photo: SyncMsg.Photo? by lazy { file?.toPhoto() }
    }

    private class Assembly(val count: Int, val sha: ByteArray, val name: String, val mime: String, val file: LinkFile) {
        val got = BooleanArray(count)
        var left = count
        var bytes = 0L
        /** Długość części (z dowolnej poza ostatnią); bez niej ostatnia czeka w [tail]. */
        var step = 0
        var tail: ByteArray? = null
    }

    private val open = LinkedHashMap<String, Assembly>()
    private val finished = LinkedHashSet<String>()
    /** Więcej części nie zmieści się w [maxBytes] przy części [PhotoSender.PART]; `count` z ramki nie przydziela tablic na ślepo. */
    private val maxParts = maxBytes / PhotoSender.PART + 1

    /** Najwięcej bajtów zdjęć naraz w pamięci odbiorcy poza plikiem z [files]: bieżąca część, odłożone ostatnie, bufor sumy. */
    var peak = 0L
        private set

    fun accept(p: SyncMsg.PhotoPart): Result {
        val ack = SyncMsg.PhotoAck(p.id, p.index)
        if (p.id in finished) return Result(listOf(ack, SyncMsg.PhotoDone(p.id, true)))
        if (p.count <= 0 || p.count > maxParts || p.index !in 0 until p.count) return Result(emptyList(), bad = true)
        // Najwyżej OPEN_MAX zdjęć składanych naraz (nadawca wysyła po kolei): najstarsze porzucone składanie wypada.
        if (p.id !in open && open.size >= OPEN_MAX) open.remove(open.keys.first())?.file?.delete()
        val a = open.getOrPut(p.id) { Assembly(p.count, p.sha, p.name, p.mime, files.create()) }
        if (a.count != p.count || !SyncCrypto.same(a.sha, p.sha)) return Result(emptyList(), bad = true)
        held(p.bytes.size)
        if (!a.got[p.index]) {
            a.bytes += p.bytes.size
            if (a.bytes > maxBytes || !place(a, p)) {
                open.remove(p.id); a.file.delete()
                return Result(listOf(SyncMsg.PhotoDone(p.id, false)), bad = true)
            }
            a.got[p.index] = true
            a.left--
        }
        if (a.left > 0) return Result(listOf(ack))
        open.remove(p.id)
        held(p.bytes.size + HASH_BUFFER)
        if (!SyncCrypto.same(a.file.sha256(), a.sha)) { a.file.delete(); return Result(listOf(ack, SyncMsg.PhotoDone(p.id, false)), bad = true) }
        finished += p.id
        while (finished.size > 64) finished.remove(finished.first())
        return Result(listOf(ack, SyncMsg.PhotoDone(p.id, true)), LinkReceived(a.name, a.mime, a.file))
    }

    /** Porzuca składane pliki (okno zamknięte): pliki w cache nie zostają. */
    fun clear() {
        open.values.forEach { it.file.delete() }
        open.clear()
    }

    /** Zapis części pod przesunięciem; false, gdy długość nie pasuje do pozostałych części (ramka niezgodna z resztą). */
    private fun place(a: Assembly, p: SyncMsg.PhotoPart): Boolean {
        val last = p.index == a.count - 1
        when {
            a.count == 1 -> a.file.write(0, p.bytes)
            !last -> {
                if (a.step == 0) {
                    if (p.bytes.isEmpty()) return false
                    a.step = p.bytes.size
                    a.tail?.let { t -> if (t.size > a.step) return false; a.file.write((a.count - 1).toLong() * a.step, t); a.tail = null }
                } else if (p.bytes.size != a.step) return false
                a.file.write(p.index.toLong() * a.step, p.bytes)
            }
            a.step == 0 -> a.tail = p.bytes
            p.bytes.size > a.step -> return false
            else -> a.file.write(p.index.toLong() * a.step, p.bytes)
        }
        return true
    }

    private fun held(now: Int) { peak = maxOf(peak, now + open.values.sumOf { (it.tail?.size ?: 0).toLong() }) }

    companion object {
        /** Nadawca kończy zdjęcie przed następnym (utknięte wraca na początek kolejki), więc 4 składania to zapas. */
        const val OPEN_MAX = 4
    }
}

/** Objaśnienie kodu błędu Nearby dla użytkownika: co się stało i co zrobić (PL i EN). */
class LinkProblem(val code: Int, val pl: String, val en: String, val setup: Boolean)

/** Kody `ConnectionsStatusCodes` z `play-services-nearby:19.3.0` (odczytane z jara, `docs/NEARBY-PRZEBUDOWA.md` §1). */
object LinkErrors {
    const val STATUS_ERROR = 13
    const val ALREADY_ADVERTISING = 8001
    const val ALREADY_DISCOVERING = 8002
    const val ALREADY_CONNECTED = 8003
    const val REJECTED = 8004
    const val RADIO_ERROR = 8007
    const val OTHER_STRATEGY = 8008
    const val ENDPOINT_IO = 8012
    const val LOCATION_OFF = 8025
    const val NO_NEARBY_WIFI = 8029
    const val NO_BLUETOOTH = 8030
    const val NO_BLUETOOTH_ADMIN = 8031
    const val NO_WIFI_STATE = 8032
    const val NO_CHANGE_WIFI = 8033
    const val NO_COARSE_LOCATION = 8034
    const val NO_FINE_LOCATION = 8036
    const val NO_BT_SCAN = 8037
    const val NO_BT_ADVERTISE = 8038
    const val NO_BT_CONNECT = 8039
    const val IN_USE = 8050

    // Kody łącza bez Nearby (2e-B1, Bluetooth RFCOMM). Zerwanie, odmowa i lokalizacja używają kodów wyżej, bo ich
    // teksty nie mówią o Nearby (ENDPOINT_IO, REJECTED, LOCATION_OFF).
    const val BT_OFF = 9001
    const val NO_PERMISSION = 9002
    /** Druga strona nie słucha (okno zamknięte albo inny klucz pary): zwykłe ponowienie po chwili. */
    const val PEER_NOT_LISTENING = 9003
    const val NO_BT_HARDWARE = 9004

    /** Kody, które znaczą „już robię to, o co prosisz”: nie są błędami. */
    val HARMLESS = setOf(ALREADY_ADVERTISING, ALREADY_DISCOVERING, ALREADY_CONNECTED)

    /** Brak uprawnienia albo wyłączone ustawienie: ponawianie nic nie da, trzeba coś włączyć. */
    fun isSetup(code: Int): Boolean =
        code == LOCATION_OFF || code in 8029..8039 || code == RADIO_ERROR || code == BT_OFF || code == NO_PERMISSION || code == NO_BT_HARDWARE

    fun explain(code: Int): LinkProblem = when (code) {
        BT_OFF -> p(code, "Bluetooth jest wyłączony: włącz go w szybkich ustawieniach na obu urządzeniach.", "Bluetooth is off: turn it on in quick settings on both devices.")
        NO_PERMISSION -> p(code, "Pozwól na „Urządzenia w pobliżu” (Ustawienia aplikacji → Uprawnienia). mNotes łączy się tylko z Twoim telefonem albo tabletem.", "Allow “Nearby devices” (App settings → Permissions). mNotes connects only to your own phone or tablet.")
        NO_BT_HARDWARE -> p(code, "To urządzenie nie ma Bluetooth. Przełącz łącze na Nearby w Ustawieniach.", "This device has no Bluetooth. Switch the link to Nearby in Settings.")
        PEER_NOT_LISTENING -> p(code, "Tablet nie odpowiada. Otwórz na nim „Zdjęcie z telefonu” i trzymaj urządzenia blisko.", "The tablet does not answer. Open “Photo from phone” on it and keep the devices close.", setup = false)
        LOCATION_OFF -> p(code, "Włącz lokalizację w szybkich ustawieniach: Android wymaga jej do szukania urządzeń przez Bluetooth.", "Turn on location in quick settings: Android needs it to look for devices over Bluetooth.")
        NO_FINE_LOCATION, NO_COARSE_LOCATION -> p(code, "Pozwól na dokładną lokalizację (Ustawienia aplikacji → Uprawnienia). mNotes nie odczytuje położenia.", "Allow precise location (App settings → Permissions). mNotes never reads your location.")
        NO_BT_SCAN, NO_BT_ADVERTISE, NO_BT_CONNECT, NO_BLUETOOTH, NO_BLUETOOTH_ADMIN -> p(code, "Pozwól na „Urządzenia w pobliżu” (Ustawienia aplikacji → Uprawnienia).", "Allow “Nearby devices” (App settings → Permissions).")
        NO_NEARBY_WIFI, NO_WIFI_STATE, NO_CHANGE_WIFI -> p(code, "Pozwól na „Urządzenia Wi-Fi w pobliżu” (Ustawienia aplikacji → Uprawnienia).", "Allow “Nearby Wi-Fi devices” (App settings → Permissions).")
        RADIO_ERROR -> p(code, "Bluetooth jest wyłączony albo zajęty: włącz go i spróbuj ponownie.", "Bluetooth is off or busy: turn it on and try again.")
        REJECTED -> p(code, "Drugie urządzenie odrzuciło połączenie. Otwórz na nim to samo okno.", "The other device rejected the connection. Open the same window there.", setup = false)
        IN_USE -> p(code, "Inna aplikacja używa teraz Nearby. Zamknij ją i spróbuj ponownie.", "Another app is using Nearby right now. Close it and try again.", setup = false)
        OTHER_STRATEGY -> p(code, "Łącze jeszcze się zamyka. Spróbuj ponownie za chwilę.", "The link is still closing. Try again in a moment.", setup = false)
        ENDPOINT_IO -> p(code, "Połączenie się urwało. Zbliż urządzenia i spróbuj ponownie.", "The connection dropped. Bring the devices closer and try again.", setup = false)
        else -> p(code, "Nearby nie działa (kod $code). Sprawdź w „Diagnostyce”, czy są Usługi Google Play, Bluetooth i lokalizacja.", "Nearby is not working (code $code). Check in “Diagnostics” for Google Play services, Bluetooth and location.", setup = false)
    }

    private fun p(code: Int, pl: String, en: String, setup: Boolean = true) = LinkProblem(code, pl, en, setup)
}

/**
 * Zmniejszanie zdjęć z telefonu do dłuższego boku [LONGEST] (2e-B1): `inSampleSize`
 * daje tylko potęgi dwójki, więc najpierw [sample] (dekodowanie nie mniejsze niż cel), potem skalowanie do [size].
 * 2400 px to zwykle 0,6–1,2 MB w JPEG 88, a to wciąż dość dla zdjęcia tablicy.
 */
object PhotoScale {
    const val LONGEST = 2400

    fun sample(w: Int, h: Int, longest: Int = LONGEST): Int {
        var s = 1
        while (maxOf(w, h) / (s * 2) >= longest) s *= 2
        return s
    }

    fun size(w: Int, h: Int, longest: Int = LONGEST): Pair<Int, Int> {
        val m = maxOf(w, h)
        if (m <= longest) return w to h
        val k = longest.toDouble() / m
        return maxOf(1, Math.round(w * k).toInt()) to maxOf(1, Math.round(h * k).toInt())
    }
}
