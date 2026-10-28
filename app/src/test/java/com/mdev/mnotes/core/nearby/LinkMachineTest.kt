package com.mdev.mnotes.core.nearby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Symulowane powietrze dla [LinkMachine]: urządzenia ogłaszają się i szukają, połączenie wymaga akceptacji po obu
 * stronach (jak Nearby), ramki idą w kolejności przez kolejkę zdarzeń. [drop] gubi wybrane ramki, [cut] zrywa łącze.
 * Jak prawdziwe Nearby: drugie `discover` bez `stopSearch` → 8002 i zero ponownych `onFound`, drugie `advertise` →
 * 8001, a jedno szukanie zgłasza dany punkt raz (znowu dopiero, gdy ten przestał się ogłaszać i zaczął od nowa).
 */
class FakeAir {
    private val queue = ArrayDeque<() -> Unit>()
    val ends = ArrayList<End>()
    var drop: (from: End, bytes: ByteArray) -> Boolean = { _, _ -> false }

    fun end(id: String) = End(id).also { ends += it }

    /** Zdarzenie dla maszyny punktu [e]; zamknięty transport ([End.close]) już nic nie dostaje. */
    private fun to(e: End, call: LinkMachine.() -> Unit) { queue += { if (!e.closed) e.machine.call() } }

    inner class End(val id: String) : LinkTransport {
        lateinit var machine: LinkMachine
        var advertising: String? = null
        var discovering = false
        var peer: End? = null
        /** Ile najbliższych próśb o połączenie ten punkt odrzuci (druga strona zajęta albo „Odrzuć”). */
        var refuse = 0
        var closed = false
            private set
        private var acceptedFor: String? = null
        private var pendingWith: String? = null
        /** Punkty zgłoszone już w bieżącym szukaniu. */
        private val seen = HashSet<String>()

        private fun other(id: String) = ends.first { it.id == id }

        override fun advertise(name: String) {
            if (advertising != null) { to(this) { onFailed(LinkErrors.ALREADY_ADVERTISING) }; return }
            advertising = name
            for (e in ends) if (e !== this && e.discovering && e.seen.add(id)) to(e) { onFound(id, name) }
        }

        override fun discover() {
            if (discovering) { to(this) { onFailed(LinkErrors.ALREADY_DISCOVERING) }; return }
            discovering = true
            seen.clear()
            for (e in ends) if (e !== this) e.advertising?.let { n -> seen += e.id; to(this) { onFound(e.id, n) } }
        }

        override fun stopSearch() {
            if (advertising != null) for (e in ends) e.seen.remove(id)
            advertising = null; discovering = false
        }

        override fun request(endpoint: String, name: String) {
            val o = ends.firstOrNull { it.id == endpoint } ?: return  // punkt, który nigdy nie odpowie
            if (o.refuse > 0) { o.refuse--; to(this) { onConnectFailed(endpoint, LinkErrors.REJECTED) }; return }
            val adv = o.advertising
            if (adv == null) { to(this) { onConnectFailed(endpoint, 8011) }; return }
            pendingWith = endpoint; o.pendingWith = id
            to(o) { onInitiated(id, name) }
            to(this) { onInitiated(endpoint, adv) }
        }

        override fun accept(endpoint: String) {
            acceptedFor = endpoint
            val o = other(endpoint)
            if (o.acceptedFor == id) {
                peer = o; o.peer = this
                to(this) { onConnected(endpoint) }
                to(o) { onConnected(id) }
            }
        }

        override fun reject(endpoint: String) {
            val o = other(endpoint)
            if (o.pendingWith == id) to(o) { onConnectFailed(id, LinkErrors.REJECTED) }
        }

        override fun send(endpoint: String, bytes: ByteArray) {
            val o = peer ?: return
            if (drop(this, bytes)) return
            val from = this
            to(o) { if (o.peer === from) onReceived(id, bytes) }
        }

        override fun disconnect(endpoint: String) {
            val o = peer ?: return
            unlink(o)
            to(o) { onDisconnected(id) }
        }

        /**
         * Okno zamknięte (`NearbyTransport.close`): maszyna nic już nie dostaje, a druga strona połączenia w toku
         * nie dowiaduje się o tym (czeka do własnego limitu czasu), tak jak przy przerwanym `requestConnection`.
         */
        fun close() { stopSearch(); closed = true }

        fun unlink(o: End) { peer = null; o.peer = null; acceptedFor = null; o.acceptedFor = null; pendingWith = null; o.pendingWith = null }
    }

    /** Zerwanie radia: obie strony dostają `onDisconnected` (jak zanik Bluetootha). */
    fun cut(a: End) {
        val b = a.peer ?: return
        a.unlink(b)
        to(a) { onDisconnected(b.id) }
        to(b) { onDisconnected(a.id) }
    }

    fun pump(max: Int = 100_000) {
        var n = 0
        while (queue.isNotEmpty() && n++ < max) queue.removeFirst()()
    }
}

class LinkMachineTest {
    private var now = 1_000L
    private val air = FakeAir()
    private val photo = ByteArray(700_000).also { Random(3).nextBytes(it) }

    private class Seen : LinkMachine.Listener {
        val states = ArrayList<LinkState>()
        val photos = ArrayList<SyncMsg.Photo>()
        val sent = ArrayList<String>()
        val rejected = ArrayList<String>()
        override fun changed(state: LinkState) { states += state }
        override fun photo(p: SyncMsg.Photo) { photos += p }
        override fun sent(id: String) { sent += id }
        override fun rejected(peer: String) { rejected += peer }
    }

    private fun machine(end: FakeAir.End, mode: LinkMode, role: SyncRole, name: String, key: ByteArray?, seen: Seen = Seen()) =
        LinkMachine(end, mode, role, name, key, peerLabel = "druga strona", clock = { now }, listener = seen).also { end.machine = it }

    /** Pompuje zdarzenia i tyka zegarem po [step] ms, aż [done] albo [limit] kroków. */
    private fun run(vararg ms: LinkMachine, step: Long = 500, limit: Int = 400, done: () -> Boolean) {
        var i = 0
        air.pump()
        while (!done() && i++ < limit) { now += step; ms.forEach { it.tick() }; air.pump() }
    }

    private fun pairKey(): ByteArray {
        val t = machine(air.end("tablet"), LinkMode.PAIR, SyncRole.HOST, "Galaxy Tab S10+", null)
        val p = machine(air.end("telefon"), LinkMode.PAIR, SyncRole.CLIENT, "Pixel 8", null)
        t.start(); p.start()
        run(t, p) { t.pairResult != null && p.pairResult != null }
        val key = t.pairResult!!.pairKey
        t.stop(); p.stop(); air.pump()
        air.ends.clear()
        return key
    }

    @Test
    fun pairingFindsTheTabletByNameAndBothSidesShowTheSameDigits() {
        val tSeen = Seen(); val pSeen = Seen()
        val t = machine(air.end("tablet"), LinkMode.PAIR, SyncRole.HOST, "Galaxy Tab S10+", null, tSeen)
        val p = machine(air.end("telefon"), LinkMode.PAIR, SyncRole.CLIENT, "Pixel 8", null, pSeen)
        t.start(); p.start(); air.pump()
        val searching = p.state as LinkState.Searching
        assertEquals(listOf("Galaxy Tab S10+"), searching.found.map { it.name })
        // jedno znalezione urządzenie: łączy się samo po chwili
        run(t, p) { t.state is LinkState.ConfirmDigits && p.state is LinkState.ConfirmDigits }
        val a = t.state as LinkState.ConfirmDigits; val b = p.state as LinkState.ConfirmDigits
        assertEquals(a.sas, b.sas)
        assertEquals("Pixel 8", a.peer); assertEquals("Galaxy Tab S10+", b.peer)
        assertArrayEquals(t.pairResult!!.pairKey, p.pairResult!!.pairKey)
        assertTrue(pSeen.states.any { it is LinkState.Connecting })
    }

    @Test
    fun foreignDevicesAreIgnored() {
        val key = pairKey()
        val stranger = machine(air.end("obcy"), LinkMode.RECEIVE, SyncRole.HOST, "Obcy", SyncCrypto.hkdf(ByteArray(32), ByteArray(0), "x", 32))
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "Pixel 8", key)
        stranger.start(); phone.start(); air.pump()
        assertTrue(phone.state is LinkState.Searching)
        assertTrue(air.ends.first { it.id == "telefon" }.peer == null)
    }

    @Test
    fun photoFromThePhoneArrivesOnTheTabletWithProgress() {
        val key = pairKey()
        val tSeen = Seen(); val pSeen = Seen()
        val tablet = machine(air.end("tablet"), LinkMode.RECEIVE, SyncRole.HOST, "Galaxy Tab S10+", key, tSeen)
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "Pixel 8", key, pSeen)
        tablet.start(); phone.start()
        val id = phone.sendPhoto("tablica.jpg", "image/jpeg", photo)
        run(tablet, phone) { tSeen.photos.isNotEmpty() && pSeen.sent.isNotEmpty() }
        assertArrayEquals(photo, tSeen.photos.single().bytes)
        assertEquals(listOf(id), pSeen.sent)
        assertTrue(pSeen.states.any { it is LinkState.Sending && it.parts == 3 })
        assertTrue(phone.state is LinkState.Ready)
        assertEquals(1, (tablet.state as LinkState.Ready).received)
    }

    @Test
    fun notebookFileGoesTheSameWayAndArrivesWithItsMime() {
        val key = pairKey()
        val tSeen = Seen()
        val tablet = machine(air.end("tablet"), LinkMode.RECEIVE, SyncRole.HOST, "T", key, tSeen)
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", key)
        val notebook = ByteArray(3_000_000).also { Random(9).nextBytes(it) }  // ponad dawne porcje i limity zdjęcia
        tablet.start(); phone.start()
        phone.sendPhoto("Fizyka.mnotes", "application/x-mnotes", notebook)
        run(tablet, phone) { tSeen.photos.isNotEmpty() }
        val got = tSeen.photos.single()
        assertEquals("application/x-mnotes", got.mime)
        assertArrayEquals(notebook, got.bytes)
    }

    @Test
    fun lostPartIsResentAfterTheTimeout() {
        val key = pairKey()
        val tSeen = Seen()
        val tablet = machine(air.end("tablet"), LinkMode.RECEIVE, SyncRole.HOST, "T", key, tSeen)
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", key)
        var dropped = 0
        air.drop = { from, bytes -> from.id == "telefon" && bytes.size > 100_000 && dropped++ == 0 }
        tablet.start(); phone.start()
        phone.sendPhoto("a.jpg", "image/jpeg", photo)
        run(tablet, phone, step = 2_000) { tSeen.photos.isNotEmpty() }
        assertEquals(1, dropped.coerceAtMost(1))
        assertTrue(dropped >= 1)
        assertArrayEquals(photo, tSeen.photos.single().bytes)
    }

    @Test
    fun linkDroppedInTheMiddleOfAPhotoReconnectsAndResumes() {
        val key = pairKey()
        val tSeen = Seen(); val pSeen = Seen()
        val tEnd = air.end("tablet")
        val tablet = machine(tEnd, LinkMode.RECEIVE, SyncRole.HOST, "T", key, tSeen)
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", key, pSeen)
        tablet.start(); phone.start()
        phone.sendPhoto("a.jpg", "image/jpeg", photo)
        // po pierwszej potwierdzonej części zrywamy radio
        run(tablet, phone) { (phone.state as? LinkState.Sending)?.part == 1 }
        air.cut(tEnd)
        run(tablet, phone) { tSeen.photos.isNotEmpty() && pSeen.sent.isNotEmpty() }
        assertArrayEquals(photo, tSeen.photos.single().bytes)
        assertTrue(phone.log.any { it.text.startsWith("rozłączono") })
        assertEquals(2, phone.log.count { it.text.startsWith("połączono") })
    }

    private fun threePhotos() = (1..3).map { n -> ByteArray(700_000).also { Random(10L + n).nextBytes(it) } }

    private fun hashes(b: List<ByteArray>) = b.map { SyncCrypto.sha256(it).toList() }

    @Test
    fun stuckPhotoStaysFirstInTheQueueAndTryAgainResendsOverTheOpenLink() {
        val key = pairKey()
        val tSeen = Seen(); val pSeen = Seen()
        val tablet = machine(air.end("tablet"), LinkMode.RECEIVE, SyncRole.HOST, "T", key, tSeen)
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", key, pSeen)
        val photos = threePhotos()
        // W połowie zdjęcia 2 z 3 eter milknie: po 4 częściach (3 zdjęcia 1 + pierwsza zdjęcia 2) części z telefonu
        // giną, a łącze formalnie trwa. Tak wygląda zawieszone Nearby: brak potwierdzeń, brak onDisconnected.
        var parts = 0
        var silent = true
        air.drop = { from, bytes -> from.id == "telefon" && bytes.size > 100_000 && ++parts > 4 && silent }
        tablet.start(); phone.start()
        photos.forEachIndexed { i, b -> phone.sendPhoto("t$i.jpg", "image/jpeg", b) }
        run(tablet, phone, step = 2_000) { phone.state is LinkState.Problem }
        assertTrue(!(phone.state as LinkState.Problem).retrying)
        assertEquals(1, tSeen.photos.size)
        // bez „Spróbuj ponownie” nic nie rusza: zdjęcie 3 nie wyprzedza utkniętego 2
        now += 60_000; phone.tick(); tablet.tick(); air.pump()
        assertTrue(phone.state is LinkState.Problem)
        silent = false
        phone.retry()
        assertTrue(phone.state !is LinkState.Searching)
        run(tablet, phone) { pSeen.sent.size == 3 }
        assertEquals(hashes(photos), hashes(tSeen.photos.map { it.bytes }))
        assertEquals(1, phone.log.count { it.text.startsWith("połączono") })  // ta sama sesja, bez szukania
        assertTrue(phone.log.any { it.text.contains("runda 1") })
    }

    @Test
    fun cutInTheMiddleOfPhotoTwoOfThreeDeliversEachPhotoOnce() {
        val key = pairKey()
        val tSeen = Seen(); val pSeen = Seen()
        val tEnd = air.end("tablet")
        val tablet = machine(tEnd, LinkMode.RECEIVE, SyncRole.HOST, "T", key, tSeen)
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", key, pSeen)
        val photos = threePhotos()
        var parts = 0
        air.drop = { from, bytes -> if (from.id == "telefon" && bytes.size > 100_000 && ++parts == 5) air.cut(tEnd); false }
        tablet.start(); phone.start()
        photos.forEachIndexed { i, b -> phone.sendPhoto("t$i.jpg", "image/jpeg", b) }
        run(tablet, phone) { pSeen.sent.size == 3 }
        assertEquals(hashes(photos), hashes(tSeen.photos.map { it.bytes }))
        assertEquals(2, phone.log.count { it.text.startsWith("połączono") })
    }

    @Test
    fun tabletRejectsTheFirstConnectionAndThePhoneConnectsByItselfOnTheSecond() {
        val key = pairKey()
        val tEnd = air.end("tablet")
        val tablet = machine(tEnd, LinkMode.RECEIVE, SyncRole.HOST, "T", key)
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", key)
        tEnd.refuse = 1
        tablet.start(); phone.start()
        run(tablet, phone) { phone.state is LinkState.Ready && tablet.state is LinkState.Ready }
        assertTrue(phone.state is LinkState.Ready)
        assertEquals(LinkErrors.REJECTED, phone.log.mapNotNull { it.code }.first())
    }

    @Test
    fun closingThePhoneWindowWhileConnectingAndOpeningItAgainConnects() {
        val key = pairKey()
        val tablet = machine(air.end("tablet"), LinkMode.RECEIVE, SyncRole.HOST, "T", key)
        val firstEnd = air.end("telefon")
        val first = machine(firstEnd, LinkMode.SEND, SyncRole.CLIENT, "P", key)
        tablet.start(); first.start()
        air.pump(max = 2)  // telefon znalazł tablet i poprosił; tablet przyjął prośbę
        assertTrue(first.state is LinkState.Connecting)
        assertTrue(tablet.state is LinkState.Connecting)
        first.stop(); firstEnd.close()  // okno zamknięte w trakcie „Łączę…”
        // ponowne otwarcie: nowy transport, nowy identyfikator punktu (jak po stopAllEndpoints)
        val second = machine(air.end("telefon-2"), LinkMode.SEND, SyncRole.CLIENT, "P", key)
        second.start()
        run(tablet, second) { second.state is LinkState.Ready && tablet.state is LinkState.Ready }
        assertTrue(second.state is LinkState.Ready)
    }

    @Test
    fun alreadyDiscoveringStartsTheSearchOverOnce() {
        val key = pairKey()
        val tablet = machine(air.end("tablet"), LinkMode.RECEIVE, SyncRole.HOST, "T", key)
        val pEnd = air.end("telefon")
        val phone = machine(pEnd, LinkMode.SEND, SyncRole.CLIENT, "P", key)
        // Szukanie włączone wcześniej (np. przez poprzednie okno) zgłosiło tablet, zanim maszyna ruszyła; drugi raz go
        // nie zgłosi, a drugie discover dostaje 8002.
        pEnd.discover()
        tablet.start(); air.pump()
        phone.start()
        run(tablet, phone) { phone.state is LinkState.Ready }
        assertTrue(phone.state is LinkState.Ready)
    }

    @Test
    fun progressCountsPhotosOfTheBatchNotPartsOfOnePhoto() {
        val key = pairKey()
        val pSeen = Seen()
        val tablet = machine(air.end("tablet"), LinkMode.RECEIVE, SyncRole.HOST, "T", key)
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", key, pSeen)
        tablet.start(); phone.start()
        threePhotos().forEachIndexed { i, b -> phone.sendPhoto("t$i.jpg", "image/jpeg", b) }
        run(tablet, phone) { pSeen.sent.size == 3 }
        val sending = pSeen.states.filterIsInstance<LinkState.Sending>()
        assertEquals(setOf(1, 2, 3), sending.map { it.photo }.toSet())
        assertTrue(sending.all { it.photos == 3 })
        val bar = sending.map { it.batchProgress }
        assertEquals(bar.sorted(), bar)  // pasek całej partii nie cofa się przy następnym zdjęciu
        assertTrue(sending.any { it.photo == 2 && it.batchProgress in 0.33f..0.67f })
        // następna partia liczy się od nowa
        phone.sendPhoto("d.jpg", "image/jpeg", photo)
        run(tablet, phone) { pSeen.sent.size == 4 }
        assertTrue(pSeen.states.filterIsInstance<LinkState.Sending>().last().photos == 1)
    }

    @Test
    fun rejectionIsVisibleOnBothSides() {
        val key = pairKey()
        val tSeen = Seen(); val pSeen = Seen()
        val tEnd = air.end("tablet")
        val tablet = machine(tEnd, LinkMode.RECEIVE, SyncRole.HOST, "T", key, tSeen)
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", key, pSeen)
        tEnd.refuse = 1
        tablet.start(); phone.start()
        run(tablet, phone) { phone.state is LinkState.Ready && tablet.state is LinkState.Ready }
        // telefon: zdanie o odmowie z kodem i przyczyną, potem ponowienie samo
        val p = pSeen.states.filterIsInstance<LinkState.Problem>().single()
        assertEquals(LinkErrors.REJECTED, p.problem.code)
        assertTrue(p.retrying)
        assertTrue(p.cause, p.cause.startsWith("połączenie nieudane"))
        // tablet: zajęty połączeniem odrzuca drugą prośbę i mówi o tym; obce urządzenie też
        air.end("telefon-2"); air.end("obcy")
        tablet.onInitiated("telefon-2", SyncCrypto.nearbyName(key))
        tablet.onInitiated("obcy", "ktoś inny")
        assertEquals(listOf("druga strona", "?"), tSeen.rejected)
        assertTrue(tablet.state is LinkState.Ready)
    }

    @Test
    fun locationServiceOffIsASetupProblemNotARetryLoop() {
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", ByteArray(32))
        phone.start()
        phone.onFailed(LinkErrors.LOCATION_OFF)
        val s = phone.state as LinkState.Problem
        assertTrue(!s.retrying)
        assertTrue(s.problem.pl.contains("lokalizację"))
        now += 60_000; phone.tick()
        assertTrue(phone.state is LinkState.Problem)  // bez samoczynnego ponawiania
        phone.retry()
        assertTrue(phone.state is LinkState.Searching)
        assertEquals(LinkErrors.LOCATION_OFF, phone.log.mapNotNull { it.code }.single())
    }

    @Test
    fun otherErrorsRetryByThemselves() {
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", ByteArray(32))
        phone.start()
        phone.onFailed(LinkErrors.IN_USE)
        assertTrue((phone.state as LinkState.Problem).retrying)
        now += LinkMachine.RETRY_MS + 1; phone.tick()
        assertTrue(phone.state is LinkState.Searching)
        phone.onFailed(LinkErrors.ALREADY_DISCOVERING)
        assertTrue(phone.state is LinkState.Searching)
    }

    @Test
    fun longSearchShowsTheChecklistAndStuckConnectingStartsOver() {
        val key = ByteArray(32)
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", key)
        phone.start()
        now += LinkMachine.SEARCH_MS + 1; phone.tick()
        assertTrue((phone.state as LinkState.Searching).slow)
        // tablet znaleziony, ale połączenie nigdy nie dochodzi do skutku (druga strona milczy)
        phone.onFound("cichy", SyncCrypto.nearbyName(key))
        assertTrue(phone.state is LinkState.Connecting)
        now += LinkMachine.CONNECT_MS + 1; phone.tick()
        assertTrue(phone.state is LinkState.Searching)
        assertTrue(phone.log.any { it.text.contains("za długo") })
    }

    @Test
    fun logKeepsTheLastTwentyEvents() {
        val phone = machine(air.end("telefon"), LinkMode.SEND, SyncRole.CLIENT, "P", ByteArray(32))
        phone.start()
        repeat(30) { phone.onFailed(LinkErrors.IN_USE) }
        assertEquals(LinkMachine.LOG_SIZE, phone.log.size)
        assertNotNull(phone.log.last().code)
    }
}
