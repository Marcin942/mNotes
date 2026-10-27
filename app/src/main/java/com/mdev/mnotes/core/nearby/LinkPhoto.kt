package com.mdev.mnotes.core.nearby

/**
 * Zdjęcie przez łącze w częściach z potwierdzeniami (`docs/NEARBY-PRZEBUDOWA.md` §3.4): nadawca wie, ile doszło
 * („2 z 3 · 64 %”), część bez potwierdzenia wraca po [TIMEOUT_MS], a po zerwaniu wysyłanie wznawia się od pierwszej
 * niepotwierdzonej. Każda część niesie nagłówek całości (nazwa, typ, liczba części, SHA-256), więc odbiorca może
 * zacząć składać od dowolnej. Szyfrowanie robi [SyncCodec], tu tylko porządek części.
 */
class PhotoSender(
    val id: String,
    private val name: String,
    private val mime: String,
    private val bytes: ByteArray,
    partSize: Int = PART,
) {
    val count: Int = maxOf(1, (bytes.size + partSize - 1) / partSize)
    private val size = partSize
    private val sha = SyncCrypto.sha256(bytes)
    private val acked = BooleanArray(count)
    private val sentAt = LongArray(count) { -1L }
    private val tries = IntArray(count)

    val done: Boolean get() = acked.all { it }

    /** Część przekroczyła [MAX_TRIES] wysłań bez potwierdzenia: dalej nie ma sensu. */
    var failed = false
        private set

    /** Ułamek bajtów potwierdzonych przez odbiorcę. */
    val progress: Float get() = if (bytes.isEmpty()) (if (done) 1f else 0f) else (0 until count).filter { acked[it] }.sumOf { partLength(it) }.toFloat() / bytes.size

    /** Ile części już potwierdzono (do zdania „część 2 z 3”). */
    val confirmed: Int get() = acked.count { it }

    private fun partLength(i: Int) = minOf(size, bytes.size - i * size).coerceAtLeast(0)

    fun part(i: Int): SyncMsg.PhotoPart =
        SyncMsg.PhotoPart(id, i, count, sha, name, mime, bytes.copyOfRange(i * size, i * size + partLength(i)))

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
 */
class PhotoReceiver(private val maxBytes: Int = LinkChunks.MAX_MESSAGE) {
    /** [reply]: wiadomości do odesłania; [photo]: gotowe zdjęcie do wstawienia (raz); [bad]: suma kontrolna się nie zgadza. */
    class Result(val reply: List<SyncMsg>, val photo: SyncMsg.Photo? = null, val bad: Boolean = false)

    private class Assembly(val count: Int, val sha: ByteArray, val name: String, val mime: String) {
        val parts = arrayOfNulls<ByteArray>(count)
        var bytes = 0L
    }

    private val open = HashMap<String, Assembly>()
    private val finished = LinkedHashSet<String>()

    fun accept(p: SyncMsg.PhotoPart): Result {
        val ack = SyncMsg.PhotoAck(p.id, p.index)
        if (p.id in finished) return Result(listOf(ack, SyncMsg.PhotoDone(p.id, true)))
        if (p.count <= 0 || p.index !in 0 until p.count) return Result(emptyList(), bad = true)
        val a = open.getOrPut(p.id) { Assembly(p.count, p.sha, p.name, p.mime) }
        if (a.count != p.count || !SyncCrypto.same(a.sha, p.sha)) return Result(emptyList(), bad = true)
        if (a.parts[p.index] == null) {
            a.bytes += p.bytes.size
            if (a.bytes > maxBytes) { open.remove(p.id); return Result(listOf(SyncMsg.PhotoDone(p.id, false)), bad = true) }
            a.parts[p.index] = p.bytes
        }
        if (a.parts.any { it == null }) return Result(listOf(ack))
        open.remove(p.id)
        val all = ByteArray(a.bytes.toInt())
        var at = 0
        for (b in a.parts) { b!!.copyInto(all, at); at += b.size }
        if (!SyncCrypto.same(SyncCrypto.sha256(all), a.sha)) return Result(listOf(ack, SyncMsg.PhotoDone(p.id, false)), bad = true)
        finished += p.id
        while (finished.size > 64) finished.remove(finished.first())
        return Result(listOf(ack, SyncMsg.PhotoDone(p.id, true)), SyncMsg.Photo(a.name, a.mime, all))
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

    /** Kody, które znaczą „już robię to, o co prosisz”: nie są błędami. */
    val HARMLESS = setOf(ALREADY_ADVERTISING, ALREADY_DISCOVERING, ALREADY_CONNECTED)

    /** Brak uprawnienia albo wyłączone ustawienie: ponawianie nic nie da, trzeba coś włączyć. */
    fun isSetup(code: Int): Boolean = code == LOCATION_OFF || code in 8029..8039 || code == RADIO_ERROR

    fun explain(code: Int): LinkProblem = when (code) {
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
