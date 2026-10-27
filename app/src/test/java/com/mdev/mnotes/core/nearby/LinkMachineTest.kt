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
 */
class FakeAir {
    private val queue = ArrayDeque<() -> Unit>()
    val ends = ArrayList<End>()
    var drop: (from: End, bytes: ByteArray) -> Boolean = { _, _ -> false }

    fun end(id: String) = End(id).also { ends += it }

    inner class End(val id: String) : LinkTransport {
        lateinit var machine: LinkMachine
        var advertising: String? = null
        var discovering = false
        var peer: End? = null
        private var acceptedFor: String? = null
        private var pendingWith: String? = null

        private fun other(id: String) = ends.first { it.id == id }

        override fun advertise(name: String) {
            advertising = name
            for (e in ends) if (e !== this && e.discovering) queue += { e.machine.onFound(id, name) }
        }

        override fun discover() {
            discovering = true
            for (e in ends) if (e !== this) e.advertising?.let { n -> queue += { machine.onFound(e.id, n) } }
        }

        override fun stopSearch() { advertising = null; discovering = false }

        override fun request(endpoint: String, name: String) {
            val o = ends.firstOrNull { it.id == endpoint } ?: return  // punkt, który nigdy nie odpowie
            val adv = o.advertising
            if (adv == null) { queue += { machine.onConnectFailed(endpoint, 8011) }; return }
            pendingWith = endpoint; o.pendingWith = id
            queue += { o.machine.onInitiated(id, name) }
            queue += { machine.onInitiated(endpoint, adv) }
        }

        override fun accept(endpoint: String) {
            acceptedFor = endpoint
            val o = other(endpoint)
            if (o.acceptedFor == id) {
                peer = o; o.peer = this
                queue += { machine.onConnected(endpoint) }
                queue += { o.machine.onConnected(id) }
            }
        }

        override fun reject(endpoint: String) {
            val o = other(endpoint)
            if (o.pendingWith == id) queue += { o.machine.onConnectFailed(id, LinkErrors.REJECTED) }
        }

        override fun send(endpoint: String, bytes: ByteArray) {
            val o = peer ?: return
            if (drop(this, bytes)) return
            queue += { if (o.peer === this) o.machine.onReceived(id, bytes) }
        }

        override fun disconnect(endpoint: String) {
            val o = peer ?: return
            unlink(o)
            queue += { o.machine.onDisconnected(id) }
        }

        fun unlink(o: End) { peer = null; o.peer = null; acceptedFor = null; o.acceptedFor = null; pendingWith = null; o.pendingWith = null }
    }

    /** Zerwanie radia: obie strony dostają `onDisconnected` (jak zanik Bluetootha). */
    fun cut(a: End) {
        val b = a.peer ?: return
        a.unlink(b)
        queue += { a.machine.onDisconnected(b.id) }
        queue += { b.machine.onDisconnected(a.id) }
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
        override fun changed(state: LinkState) { states += state }
        override fun photo(p: SyncMsg.Photo) { photos += p }
        override fun sent(id: String) { sent += id }
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
