package com.mdev.mnotes.core.nearby

/**
 * Transport łącza: Nearby Connections (`NearbyTransport`), Bluetooth RFCOMM ([RfcommTransport]) albo symulacja w testach.
 * Same wywołania, zero decyzji; zdarzenia wracają do [LinkMachine] (`onFound`, `onInitiated`, `onConnected`,
 * `onReceived`…), zawsze później niż wywołanie, które je spowodowało (nie z jego wnętrza).
 */
interface LinkTransport {
    /**
     * Czy znalezione urządzenia to na pewno mNotes (Nearby: tak, nazwa punktu to mówi). W Bluetooth lista sparowanych
     * to też słuchawki i zegarki, więc w parowaniu nie wybieramy samemu jedynego znalezionego.
     */
    val autoPick: Boolean get() = true

    fun advertise(name: String)
    /** [name]: nazwa tego urządzenia w łączu (jak w [advertise]); Bluetooth z niej wie, czego szuka. */
    fun discover(name: String)
    fun stopSearch()
    fun request(endpoint: String, name: String)
    fun accept(endpoint: String)
    fun reject(endpoint: String)
    fun send(endpoint: String, bytes: ByteArray)
    fun disconnect(endpoint: String)
}

/** Po co otwarto łącze: parowanie (oba urządzenia), odbiór zdjęć (tablet), wysyłanie zdjęć (telefon). */
enum class LinkMode { PAIR, RECEIVE, SEND }

data class LinkPeer(val id: String, val name: String)

/** Wpis dziennika do „Diagnostyki”: czas, co się stało, kod Nearby (bez treści zdjęć i kluczy). */
data class LinkEvent(val at: Long, val text: String, val code: Int? = null)

/** Stan łącza dla UI: jedno zdanie i jedna rzecz pod nim (`docs/NEARBY-PRZEBUDOWA.md` §3.5). */
sealed interface LinkState {
    data object Idle : LinkState
    /** [slow]: szukanie trwa dłużej niż [LinkMachine.SEARCH_MS], UI pokazuje listę „co sprawdzić”. */
    data class Searching(val found: List<LinkPeer>, val slow: Boolean) : LinkState
    data class Connecting(val peer: String) : LinkState
    data class Securing(val peer: String) : LinkState
    data class ConfirmDigits(val sas: String, val peer: String) : LinkState
    data class Ready(val peer: String, val sent: Int, val received: Int) : LinkState
    /**
     * Bieżące zdjęcie: potwierdzone części [part] z [parts], [progress] ∈ [0, 1], ile zdjęć jeszcze w kolejce; w partii
     * (zdjęcia dodane, zanim kolejka się opróżniła) to zdjęcie [photo] z [photos], a [batchProgress] liczy bajty całej partii.
     */
    data class Sending(
        val peer: String, val part: Int, val parts: Int, val progress: Float, val queued: Int,
        val photo: Int = 1, val photos: Int = 1, val batchProgress: Float = progress,
    ) : LinkState
    /**
     * [retrying]: łącze samo spróbuje ponownie; inaczej trzeba coś włączyć albo nacisnąć „Spróbuj ponownie”.
     * [cause]: ostatni wpis dziennika (krótka przyczyna do głównego okna, obok kodu z [problem]).
     */
    data class Problem(val problem: LinkProblem, val retrying: Boolean, val cause: String = "") : LinkState
}

/**
 * Cała logika łącza telefon ↔ tablet poza Nearby (2b-B1): role z czynności ([SyncRole.HOST] ogłasza się, [SyncRole.CLIENT]
 * szuka), lista znalezionych w parowaniu, parowanie z zobowiązaniem ([PairingSession]), sesja szyfrowana ([SyncCodec]),
 * zdjęcia w częściach z potwierdzeniami ([PhotoSender]/[PhotoReceiver]), limity czasu i ponowienia, dziennik.
 * Wszystkie wywołania z jednego wątku (w aplikacji: głównego); [clock] i [tick] zamiast własnych zegarów, więc testy
 * przechodzą bez czekania. Klucz pary: null w trybie [LinkMode.PAIR], w pozostałych wymagany.
 */
class LinkMachine(
    private val transport: LinkTransport,
    val mode: LinkMode,
    val role: SyncRole,
    private val deviceName: String,
    private val pairKey: ByteArray? = null,
    /** Nazwa sparowanego urządzenia do zdań w UI (po parowaniu nazwa punktu to skrót klucza, nie nazwa). */
    private val peerLabel: String = "",
    private val clock: () -> Long = System::currentTimeMillis,
    private val listener: Listener = object : Listener {},
    /** Gdzie składać odbierane pliki: w aplikacji katalog w cache ([LinkFiles.disk]), w testach pamięć. */
    files: LinkFiles = LinkFiles.MEMORY,
    /** Dziennik do eksportu (3b-B1): sesję zaczyna aplikacja ([LinkJournal.begin]), maszyna dopisuje etapy i części. */
    private val journal: LinkJournal? = null,
) {
    interface Listener {
        fun changed(state: LinkState) {}
        fun photo(p: SyncMsg.Photo) {}
        /** Odebrany i sprawdzony plik; domyślnie trafia w całości do [photo] i jest usuwany (UI zeszytów czyta go strumieniem). */
        fun file(f: LinkReceived) { photo(f.toPhoto()); f.file.delete() }
        fun sent(id: String) {}
        /** To urządzenie odrzuciło połączenie od [peer] (zajęte innym albo obce); UI pokazuje to zdaniem. */
        fun rejected(peer: String) {}
    }

    init {
        require(mode == LinkMode.PAIR || pairKey != null) { "praca po parowaniu wymaga klucza pary" }
        journal?.redact(deviceName)
        journal?.redact(peerLabel)
    }

    var state: LinkState = LinkState.Idle
        private set

    /** Wynik parowania po obliczeniu cyfr; zapisuje go UI dopiero po „Zgadza się” na tym urządzeniu. */
    var pairResult: PairingSession.Result? = null
        private set

    private val events = ArrayDeque<LinkEvent>()
    val log: List<LinkEvent> get() = events.toList()

    /** Nazwy urządzeń widziane w tej sesji (do czyszczenia [report]). */
    private val seenNames = LinkedHashSet<String>()

    /**
     * Zdarzenia do „Kopiuj raport” (schowek, wysyłane dalej): bez nazw urządzeń i adresów, jak dziennik ([LinkScrub]).
     * Na ekranie diagnostyki zostaje [log] z nazwami — to urządzenia użytkownika.
     */
    val report: List<LinkEvent> get() {
        val names = seenNames + deviceName + peerLabel
        return events.map { it.copy(text = LinkScrub.clean(it.text, names)) }
    }

    private val ownName = if (pairKey == null) PAIR_PREFIX + deviceName.take(40) else SyncCrypto.nearbyName(pairKey)
    private val found = LinkedHashMap<String, String>()
    private var endpoint: String? = null
    private var peerName = ""
    private var joiner = ChunkJoiner()
    private var pairing: PairingSession? = null
    private var codec: SyncCodec? = null
    /** Nasze `hello` już poszło: dopiero wtedy wolno wysyłać zaszyfrowane wiadomości (pierwsza ramka musi być hello). */
    private var helloSent = false
    private val outbox = ArrayDeque<PhotoSender>()
    private var current: PhotoSender? = null
    /** Zdjęcie utknęło: wysyłanie czeka na „Spróbuj ponownie” albo nowe połączenie (inaczej następne by je wyprzedziło). */
    private var held = false
    private val receiver = PhotoReceiver(FILE_MAX, files)
    /** Najwięcej bajtów odbieranych plików naraz w pamięci (poza plikiem w cache); do testu pamięci. */
    val receivePeak: Long get() = receiver.peak
    private var sentCount = 0
    private var receivedCount = 0
    /** Partia: zdjęcia dodane od chwili, gdy kolejka była pusta (do „zdjęcie 2 z 3” i paska bajtów całości). */
    private var batchCount = 0
    private var batchDone = 0
    private var batchBytes = 0L
    private var batchDoneBytes = 0L
    private var photoSeq = 0
    private var running = false
    private var searchStart = 0L
    private var linkStart = 0L
    private var retryAt = 0L
    private var autoPickAt = 0L
    /** Po 8002 szukanie zaczęte od nowa już raz w tym [search] (drugi raz nie, żeby nie krążyć). */
    private var rediscovered = false

    fun start() {
        running = true
        record("start: ${mode.name.lowercase()}, ${if (role == SyncRole.HOST) "ogłaszam się" else "szukam"}")
        search()
    }

    fun stop() {
        running = false
        endpoint?.let(transport::disconnect)
        transport.stopSearch()
        drop()
        receiver.clear()
        set(LinkState.Idle)
    }

    /** Telefon w parowaniu: stuknięcie w znalezione urządzenie. */
    fun choose(id: String) {
        if (role != SyncRole.CLIENT || endpoint != null || id !in found) return
        connect(id)
    }

    /**
     * „Spróbuj ponownie”: przy gotowym łączu wznawia wysyłkę utkniętego zdjęcia (bez szukania, które przy otwartym
     * łączu wisiałoby na „Szukam…”); w trakcie łączenia zaczyna od nowa; bez łącza szuka.
     */
    fun retry() {
        if (!running) return
        val c = codec
        when {
            endpoint != null && c != null && c.ready && helloSent -> { held = false; record("ponawiam wysyłkę"); ready() }
            endpoint != null -> restart("ponowienie na prośbę")
            else -> search()
        }
    }

    /**
     * Kolejka plików do wysłania (zdjęcia z telefonu, zeszyty `.mnotes`); wychodzą po kolei, gdy łącze jest gotowe.
     * Odbiorca dostaje każdy przez [Listener.file] z tym samym [mime] i sam decyduje, co z nim zrobić.
     */
    fun sendPhoto(name: String, mime: String, bytes: ByteArray): String = sendFile(name, mime, LinkFile.Memory(bytes))

    /** Jak [sendPhoto], ale części czytane z [file] w trakcie wysyłania (zeszyt z pliku w cache nie trafia do pamięci). */
    fun sendFile(name: String, mime: String, file: LinkFile): String {
        require(file.length <= FILE_MAX) { "plik ponad ${FILE_MAX shr 20} MB" }
        val id = "${clock()}-${++photoSeq}"
        if (current == null && outbox.isEmpty()) { batchCount = 0; batchDone = 0; batchBytes = 0; batchDoneBytes = 0 }
        val s = PhotoSender(id, name, mime, file)
        batchCount++
        batchBytes += s.length
        outbox.addLast(s)
        record("zdjęcie w kolejce: ${s.length / 1024} KB")
        pump()
        return id
    }

    /** Wołane co ok. sekundę: limity czasu, ponowienia części, samo-wybór jedynego znalezionego urządzenia. */
    fun tick() {
        if (!running) return
        val now = clock()
        if (retryAt in 1..now) { retryAt = 0; if (endpoint == null) search(); return }
        val s = state
        when {
            s is LinkState.Searching && !s.slow && now - searchStart > SEARCH_MS -> set(s.copy(slow = true))
            s is LinkState.Connecting && now - linkStart > CONNECT_MS -> restart("łączenie trwa za długo")
            s is LinkState.Securing && now - linkStart > SECURE_MS -> restart("zabezpieczanie trwa za długo")
        }
        if (autoPickAt in 1..now) {
            autoPickAt = 0
            if (endpoint == null && found.size == 1) connect(found.keys.first())
        }
        pump()
    }

    // --- zdarzenia transportu ---------------------------------------------------------------------------------

    fun onFound(id: String, name: String) {
        if (!running || role != SyncRole.CLIENT || !accepts(name)) return
        found[id] = label(name)
        journal?.redact(label(name))
        record("znaleziono: ${label(name)}", peer = id)
        if (mode == LinkMode.PAIR) {
            if (endpoint == null) {
                showSearching()
                autoPickAt = if (found.size == 1 && transport.autoPick) clock() + AUTO_PICK_MS else 0
            }
        } else if (endpoint == null) connect(id)
    }

    fun onLost(id: String) {
        if (found.remove(id) != null && endpoint == null && state is LinkState.Searching) showSearching()
    }

    fun onInitiated(id: String, name: String) {
        if (!running || !accepts(name) || (endpoint != null && endpoint != id)) {
            transport.reject(id)
            if (running) {
                val who = if (accepts(name)) label(name) else "?"
                record(if (accepts(name)) "odrzucono: $who (zajęte innym połączeniem)" else "odrzucono obce urządzenie")
                listener.rejected(who)
            }
            return
        }
        endpoint = id
        peerName = label(name)
        journal?.redact(peerName)
        trace("połączenie przychodzące", id)
        joiner = ChunkJoiner()
        linkStart = clock()
        // Sesja powstaje przed `onConnected`, bo pierwsza ramka drugiej strony może przyjść tuż po nim.
        if (pairKey == null) pairing = PairingSession(deviceName) else codec = SyncCodec(pairKey, role)
        transport.accept(id)
        set(LinkState.Connecting(peerName))
    }

    fun onConnected(id: String) {
        if (id != endpoint) return
        transport.stopSearch()
        linkStart = clock()
        record("połączono: $peerName")
        set(LinkState.Securing(peerName))
        pairing?.let { send(it.start()) }
        codec?.let { c -> send(c.hello()); helloSent = true; if (c.ready) ready() }
    }

    fun onConnectFailed(id: String, code: Int) {
        if (id != endpoint) return
        record("połączenie nieudane", code)
        endpoint = null
        // Punkt, który odmówił, nie jest „znany”: szukanie zgłosi go znowu, jeśli żyje. Ponowienie po chwili, bo
        // zajęta druga strona odrzuca od razu i bez przerwy kręcilibyśmy się w kółko.
        found.remove(id)
        if (LinkErrors.isSetup(code)) problem(code)
        else {
            retryAt = clock() + RETRY_MS
            // Odmowę widać („Drugie urządzenie odrzuciło…”, ponowienie samo); inne błędy łączenia to zwykłe szukanie.
            if (code == LinkErrors.REJECTED) set(problemState(code, retrying = true)) else showSearching()
        }
    }

    fun onDisconnected(id: String) {
        if (id != endpoint) return
        record("rozłączono: $peerName")
        val paired = pairResult
        drop()
        if (mode == LinkMode.PAIR && paired != null) return  // cyfry już są; zapis zależy tylko od użytkownika
        if (running) search()
    }

    fun onReceived(id: String, bytes: ByteArray) {
        if (id != endpoint) return
        try {
            // Do uwierzytelnienia (hello z kluczem pary) obcy, który zna ogłaszaną nazwę, mógłby słać 24 MB porcji.
            joiner.max = if (codec?.ready == true) LinkChunks.MAX_MESSAGE else LinkChunks.PRE_AUTH_MAX
            val raw = joiner.feed(bytes) ?: return
            handle(raw)
        } catch (e: Exception) {
            record("zła ramka: ${e.message}")
            transport.disconnect(id)
            onDisconnected(id)
        }
    }

    /** Błąd startu ogłaszania/szukania. Ustawienia i uprawnienia → czekamy na użytkownika; reszta → ponowienie. */
    fun onFailed(code: Int) {
        // 8002: Nearby nie zacznie drugiego szukania i nie zgłosi znanych punktów drugi raz. Raz: stop i od nowa.
        if (code == LinkErrors.ALREADY_DISCOVERING && running && endpoint == null && !rediscovered) {
            rediscovered = true
            record("szukanie od nowa", code)
            transport.stopSearch()
            transport.discover(ownName)
            return
        }
        if (code in LinkErrors.HARMLESS) return
        record("Nearby: błąd", code)
        transport.stopSearch()
        if (LinkErrors.isSetup(code)) problem(code)
        else { retryAt = clock() + RETRY_MS; set(problemState(code, retrying = true)) }
    }

    // --- środek ------------------------------------------------------------------------------------------------

    private fun handle(raw: ByteArray) {
        pairing?.let { p ->
            val step = p.receive(raw)
            step.send.forEach(::send)
            step.result?.let { r ->
                pairResult = r
                record("cyfry policzone")
                set(LinkState.ConfirmDigits(r.sas, r.peerName))
            }
            return
        }
        val c = codec ?: return
        if (!c.ready) {
            c.acceptHello(raw)
            if (helloSent) ready()
            return
        }
        when (val m = c.open(raw)) {
            is SyncMsg.PhotoPart -> {
                trace("odebrano część ${m.index + 1}/${m.count}: ${m.bytes.size / 1024} KB")
                val r = receiver.accept(m)
                for (m2 in r.reply) send(c.seal(m2))
                if (r.bad) record("zdjęcie odrzucone: zła suma kontrolna")
                r.file?.let { receivedCount++; record("odebrano plik: ${it.file.length / 1024} KB"); listener.file(it); ready() }
            }
            is SyncMsg.PhotoAck -> {
                current?.let { s ->
                    val ms = s.sinceSent(m.index, clock())
                    if (s.ack(m.index, clock())) trace("część ${m.index + 1}/${s.count} potwierdzona" + (ms?.let { " po $it ms" } ?: ""))
                }
                pump()
            }
            is SyncMsg.PhotoDone -> {
                val s = current
                if (s != null && s.id == m.id) {
                    current = null
                    batchDone++
                    batchDoneBytes += s.length
                    if (m.ok) { sentCount++; record("zdjęcie wstawione na tablecie"); listener.sent(m.id) }
                    else record("tablet odrzucił zdjęcie")
                }
                pump()
            }
            is SyncMsg.Photo -> { receivedCount++; listener.photo(m); ready() }  // starsza wersja telefonu
            else -> Unit
        }
    }

    private fun ready() {
        record("łącze gotowe")
        set(LinkState.Ready(peerName, sentCount, receivedCount))
        pump()
    }

    /** Wysyła należne części bieżącego zdjęcia i aktualizuje postęp; bierze następne zdjęcie z kolejki. */
    private fun pump() {
        val c = codec ?: return
        if (!c.ready || !helloSent || endpoint == null || held) return
        if (current == null) current = outbox.removeFirstOrNull()
        val s = current
        if (s == null) {
            if (state is LinkState.Sending) set(LinkState.Ready(peerName, sentCount, receivedCount))
            return
        }
        for (p in s.due(clock())) {
            val k = s.tries(p.index)
            trace("część ${p.index + 1}/${p.count} wysłana: ${p.bytes.size / 1024} KB" + if (k > 1) " (ponowienie, próba $k)" else "")
            send(c.seal(p))
        }
        if (s.failed) {
            // Nie porzucamy: zdjęcie wraca na początek kolejki, potwierdzone części zostają u odbiorcy.
            s.rearm()
            current = null
            outbox.addFirst(s)
            held = true
            record("zdjęcie nie doszło po ${PhotoSender.MAX_TRIES} próbach (runda ${s.rounds})")
            set(problemState(LinkErrors.ENDPOINT_IO, retrying = false))
            return
        }
        val batch = if (batchBytes > 0) ((batchDoneBytes + s.progress * s.length) / batchBytes).coerceIn(0f, 1f) else s.progress
        set(LinkState.Sending(peerName, s.confirmed, s.count, s.progress, outbox.size, batchDone + 1, maxOf(batchCount, batchDone + 1), batch))
    }

    private fun search() {
        retryAt = 0
        rediscovered = false
        searchStart = clock()
        showSearching()
        // Zawsze od czystego startu: Nearby na drugie startDiscovery odpowiada 8002 i nie zgłasza znanego punktu
        // ponownie, a telefon (SEND) łączy się tylko po onFound, więc bez tego wisiałby na „Szukam…”.
        transport.stopSearch()
        if (role == SyncRole.HOST) transport.advertise(ownName) else transport.discover(ownName)
        // Punkt znany z poprzedniego szukania (np. po zerwaniu łącza): próba od razu, bez czekania na onFound.
        if (role == SyncRole.CLIENT && mode != LinkMode.PAIR) found.keys.firstOrNull()?.let(::connect)
    }

    private fun connect(id: String) {
        endpoint = id
        peerName = found[id] ?: peerLabel
        linkStart = clock()
        autoPickAt = 0
        retryAt = 0
        record("łączę z: $peerName", peer = id)
        set(LinkState.Connecting(peerName))
        transport.request(id, ownName)
    }

    private fun restart(why: String) {
        record(why)
        endpoint?.let { transport.disconnect(it); found.remove(it) }  // milczący punkt nie jest „znany”
        drop()
        search()
    }

    /** Zrywa bieżące połączenie po swojej stronie; zdjęcie w drodze wznowi się po następnym połączeniu. */
    private fun drop() {
        endpoint = null
        codec = null
        helloSent = false
        held = false
        pairing = null
        joiner = ChunkJoiner()
        current?.reset()
    }

    private fun problem(code: Int) {
        transport.stopSearch()
        set(problemState(code, retrying = false))
    }

    private fun problemState(code: Int, retrying: Boolean) =
        LinkState.Problem(LinkErrors.explain(code), retrying, events.lastOrNull()?.text.orEmpty())

    private fun showSearching() = set(LinkState.Searching(found.map { LinkPeer(it.key, it.value) }, clock() - searchStart > SEARCH_MS))

    private fun send(raw: ByteArray) {
        val id = endpoint ?: return
        for (part in LinkChunks.split(raw)) transport.send(id, part)
    }

    private fun accepts(name: String) = if (pairKey == null) name.startsWith(PAIR_PREFIX) else name == ownName

    private fun label(name: String) = (if (pairKey == null) name.removePrefix(PAIR_PREFIX) else peerLabel).also { if (it.isNotBlank()) seenNames += it }

    private fun set(s: LinkState) {
        if (s == state) return
        if (s::class != state::class) trace("stan: ${s::class.simpleName}")
        state = s
        listener.changed(s)
    }

    private fun record(text: String, code: Int? = null, peer: String? = null) {
        events.addLast(LinkEvent(clock(), text, code))
        while (events.size > LOG_SIZE) events.removeFirst()
        journal?.add(if (peer == null) text else text + " " + journal.peer(peer), code)
    }

    /** Tylko do dziennika (części, stany): za drobne na 20 wpisów „Diagnostyki”. */
    private fun trace(text: String, peer: String? = null) { journal?.add(if (peer == null) text else text + " " + journal.peer(peer)) }

    /** Wpis transportu do dziennika (wyjątki, stan ładunków Nearby); tekst i tak przechodzi przez [LinkScrub]. */
    fun note(text: String) = trace(text)

    companion object {
        /** Nazwa punktu w parowaniu: przedrostek + nazwa urządzenia (lista „Znaleziono: Galaxy Tab S10+”). */
        const val PAIR_PREFIX = "mnp|"
        const val SEARCH_MS = 30_000L
        /**
         * 3b-B3: było 20 s i 10 s. Nearby po Bluetooth (zanim przejdzie na Wi-Fi) łączy się na części telefonów ponad
         * 20 s, a po `onConnected` potrafi wstrzymać ładunki na czas zmiany medium; przerwanie w tej chwili zaczynało
         * szukanie od nowa i dawało „wszystko trwa długo”. Dziennik pokaże prawdziwe czasy.
         */
        const val CONNECT_MS = 40_000L
        const val SECURE_MS = 20_000L
        const val RETRY_MS = 3_000L
        const val AUTO_PICK_MS = 1_000L
        const val LOG_SIZE = 20

        /** Największy plik przez łącze (zeszyt z PDF-em bywa duży); w aplikacji idzie przez plik w cache, nie przez pamięć. */
        const val FILE_MAX = 64 shl 20
    }
}
