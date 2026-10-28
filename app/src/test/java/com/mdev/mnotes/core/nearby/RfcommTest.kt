package com.mdev.mnotes.core.nearby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Random
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Ramki strumienia, identyfikatory usług, uprawnienia i kody łącza Bluetooth (2e-B1). */
class RfcommBasicsTest {
    /** Strumień, który oddaje dane kawałkami po 1…[most] B (jak gniazdo Bluetooth). */
    private class Choppy(bytes: ByteArray, private val most: Int, seed: Long) : InputStream() {
        private val src = ByteArrayInputStream(bytes)
        private val rnd = Random(seed)
        override fun read(): Int = src.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = src.read(b, off, minOf(len, 1 + rnd.nextInt(most)))
    }

    @Test
    fun framesSurviveAnyCutOfTheStream() {
        val rnd = Random(3)
        val msgs = listOf(ByteArray(0), ByteArray(1) { 7 }, ByteArray(70_000).also(rnd::nextBytes), ByteArray(300).also(rnd::nextBytes))
        val out = ByteArrayOutputStream()
        msgs.forEach { StreamFramer.write(out, it) }
        for (most in listOf(1, 3, 1000, 100_000)) {
            val inp = Choppy(out.toByteArray(), most, most.toLong())
            for (m in msgs) assertArrayEquals(m, StreamFramer.read(inp))
            assertNull("koniec strumienia na granicy ramki", StreamFramer.read(inp))
        }
    }

    @Test
    fun badOrCutFramesAreErrors() {
        fun bad(bytes: ByteArray) = try { StreamFramer.read(ByteArrayInputStream(bytes)); fail("bez błędu dla ${bytes.toList()}") } catch (e: IOException) {}
        bad(byteArrayOf(0x7f, 0, 0, 0))                     // 2 GB
        bad(byteArrayOf(-1, -1, -1, -1))                    // ujemna
        bad(byteArrayOf(0, 0))                              // urwana długość
        bad(byteArrayOf(0, 0, 0, 5, 1, 2))                  // urwana treść
        val big = ByteArrayOutputStream().also { StreamFramer.write(it, ByteArray(LinkChunks.CHUNK + 2)) }.toByteArray()
        bad(big)                                            // ponad porcję z LinkChunks
    }

    @Test
    fun serviceIdsSeparatePairingAndEveryPair() {
        assertEquals(RfcommIds.PAIR, RfcommIds.uuidFor(LinkMachine.PAIR_PREFIX + "Galaxy Tab S10+"))
        assertEquals(RfcommIds.PAIR, RfcommIds.uuidFor(LinkMachine.PAIR_PREFIX + "Pixel 8"))
        val a = SyncCrypto.nearbyName(ByteArray(32) { it.toByte() })
        val b = SyncCrypto.nearbyName(ByteArray(32) { (it + 1).toByte() })
        assertEquals(RfcommIds.uuidFor(a), RfcommIds.uuidFor(a))
        assertNotEquals(RfcommIds.uuidFor(a), RfcommIds.uuidFor(b))
        assertNotEquals(RfcommIds.PAIR, RfcommIds.uuidFor(a))
    }

    @Test
    fun rfcommPermissionsFollowTheTableForEveryAndroid() {
        val connect = "android.permission.BLUETOOTH_CONNECT"
        val scan = "android.permission.BLUETOOTH_SCAN"
        val adv = "android.permission.BLUETOOTH_ADVERTISE"
        // Para z listy sparowanych: na 8–11 bez pytania, na 12+ jedno „Urządzenia w pobliżu”.
        assertEquals(emptyList<String>(), LinkPermissions.rfcomm(26))
        assertEquals(emptyList<String>(), LinkPermissions.rfcomm(30))
        assertEquals(listOf(connect), LinkPermissions.rfcomm(31))
        assertEquals(listOf(connect), LinkPermissions.rfcomm(36))
        // „Szukaj w pobliżu”: lokalizacja tylko do 11 (przybliżona do 9, dokładna 10–11), od 12 SCAN bez lokalizacji.
        assertEquals(listOf(LinkPermissions.COARSE), LinkPermissions.rfcomm(26, discovery = true))
        assertEquals(listOf(LinkPermissions.FINE, LinkPermissions.COARSE), LinkPermissions.rfcomm(29, discovery = true))
        assertEquals(listOf(connect, scan), LinkPermissions.rfcomm(33, discovery = true))
        assertEquals(listOf(connect, adv), LinkPermissions.rfcomm(36, visible = true))
        assertFalse(LinkPermissions.rfcomm(36, discovery = true).any { "LOCATION" in it })
        // przybliżona jest brakiem tylko tam, gdzie wystarcza sama
        assertEquals(listOf(LinkPermissions.COARSE), LinkPermissions.missing(LinkPermissions.rfcomm(26, discovery = true)) { false })
        assertEquals(listOf(LinkPermissions.FINE), LinkPermissions.missing(LinkPermissions.rfcomm(29, discovery = true)) { false })
    }

    @Test
    fun bluetoothCodesHaveTextsWithoutNearby() {
        for (c in listOf(LinkErrors.BT_OFF, LinkErrors.NO_PERMISSION, LinkErrors.PEER_NOT_LISTENING, LinkErrors.NO_BT_HARDWARE)) {
            val p = LinkErrors.explain(c)
            assertTrue(p.pl.isNotBlank() && p.en.isNotBlank())
            assertFalse("$c: ${p.pl}", "Nearby" in p.pl.replace("na Nearby", "") || "Google" in p.pl)
        }
        assertTrue(LinkErrors.isSetup(LinkErrors.BT_OFF) && LinkErrors.isSetup(LinkErrors.NO_PERMISSION))
        assertFalse("tablet bez otwartego okna: ponawiamy", LinkErrors.isSetup(LinkErrors.PEER_NOT_LISTENING))
    }
}

/**
 * [RfcommTransport] z [LinkMachine] na symulatorze Bluetooth: nasłuch po UUID bez akceptacji, łączenie z kimś, kto nie
 * słucha, kończy się błędem, odczyty cięte na losowe kawałki, zerwanie wszystkich gniazd naraz. Wątki transportu są
 * prawdziwe; zdarzenia idą przez [post] do kolejki, którą test wykonuje po kolei (jak główny wątek), a zegar maszyn
 * przesuwa się o 0,5 s, gdy kolejka stoi.
 */
class RfcommTransportTest {
    private val tablet = BtDevice("AA:00:00:00:00:01", "Galaxy Tab S10+")
    private val phone = BtDevice("AA:00:00:00:00:02", "Pixel 8")
    private val buds = BtDevice("AA:00:00:00:00:03", "Galaxy Buds")
    private val watch = BtDevice("AA:00:00:00:00:04", "Galaxy Watch")
    private val key = ByteArray(32) { it.toByte() }
    private val otherKey = ByteArray(32) { (it * 7 + 1).toByte() }

    private var now = 1_000_000L
    private val main = LinkedBlockingQueue<() -> Unit>()
    private val post: (() -> Unit) -> Unit = { main.put(it) }
    private val air = Air()

    // --- symulator ---------------------------------------------------------------------------------------------

    private class Stream {
        private val q = LinkedBlockingQueue<ByteArray>()
        @Volatile private var closed = false
        private var cur = ByteArray(0)
        private var pos = 0
        private val rnd = Random(q.hashCode().toLong())

        val input = object : InputStream() {
            override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 255 }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                while (pos >= cur.size) {
                    val x = q.take()
                    if (x === EOF) { q.put(EOF); return -1 }
                    cur = x; pos = 0
                }
                val n = minOf(len, cur.size - pos, 1 + rnd.nextInt(5000))
                System.arraycopy(cur, pos, b, off, n)
                pos += n
                return n
            }
        }
        val output = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (closed) throw IOException("gniazdo zamknięte")
                q.put(b.copyOfRange(off, off + len))
            }
        }
        fun close() { if (!closed) { closed = true; q.put(EOF) } }

        companion object { val EOF = ByteArray(0) }
    }

    private class Pipe(override val peer: BtDevice, private val inS: Stream, private val outS: Stream) : BtPipe {
        override val input get() = inS.input
        override val output get() = outS.output
        override fun close() { inS.close(); outS.close() }
    }

    private class Air {
        val devices = HashMap<String, BtDevice>()
        val servers = HashMap<Pair<String, UUID>, Server>()
        val pipes = ArrayList<Pipe>()
        var connects = 0

        @Synchronized fun count() = connects
        fun cut() = synchronized(this) { pipes.toList().also { pipes.clear() } }.forEach { it.close() }
    }

    private class Server(val air: Air, val key: Pair<String, UUID>) : BtServer {
        val q = LinkedBlockingQueue<Any>()
        override fun accept(): BtPipe {
            val p = q.take()
            if (p === CLOSED) { q.put(CLOSED); throw IOException("nasłuch zamknięty") }
            return p as BtPipe
        }
        override fun close() {
            synchronized(air) { if (air.servers[key] === this) air.servers.remove(key) }
            q.put(CLOSED)
        }
        companion object { val CLOSED = Any() }
    }

    private inner class Port(val me: BtDevice, val bondedList: List<BtDevice>) : BtPort {
        init { synchronized(air) { air.devices[me.address] = me } }
        override fun enabled() = true
        override fun bonded() = bondedList
        override fun listen(uuid: UUID, name: String): BtServer = synchronized(air) {
            Server(air, me.address to uuid).also { air.servers[me.address to uuid] = it }
        }
        override fun connect(address: String, uuid: UUID): BtPipe = synchronized(air) {
            air.connects++
            val s = air.servers[address to uuid] ?: throw IOException("nikt nie słucha")
            val there = Stream(); val back = Stream()
            val mine = Pipe(air.devices.getValue(address), back, there)
            val theirs = Pipe(me, there, back)
            air.pipes += mine; air.pipes += theirs
            s.q.put(theirs)
            mine
        }
    }

    private class Seen : LinkMachine.Listener {
        val photos = ArrayList<SyncMsg.Photo>()
        override fun photo(p: SyncMsg.Photo) { photos += p }
    }

    private fun machine(t: RfcommTransport, mode: LinkMode, role: SyncRole, name: String, k: ByteArray?, seen: Seen = Seen()) =
        LinkMachine(t, mode, role, name, k, peerLabel = "drugie", clock = { now }, listener = seen).also { t.machine = it }

    private fun runUntil(vararg ms: LinkMachine, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!cond()) {
            if (System.currentTimeMillis() > end) fail("nie doczekano się: $what; stany ${ms.map { it.state }}")
            val job = main.poll(10, TimeUnit.MILLISECONDS)
            if (job != null) job() else { now += 500; ms.forEach { it.tick() } }
        }
    }

    private fun photo(size: Int, seed: Long) = ByteArray(size).also { Random(seed).nextBytes(it) }

    // --- scenariusze -------------------------------------------------------------------------------------------

    @Test
    fun pairingFromBondedListNeedsATapAndGivesTheSameDigits() {
        val tt = RfcommTransport(Port(tablet, listOf(phone)), post)
        val pt = RfcommTransport(Port(phone, listOf(buds, watch, tablet)), post)
        val host = machine(tt, LinkMode.PAIR, SyncRole.HOST, tablet.name, null)
        val client = machine(pt, LinkMode.PAIR, SyncRole.CLIENT, phone.name, null)
        host.start(); client.start()
        runUntil(host, client, what = "lista trzech urządzen") { (client.state as? LinkState.Searching)?.found?.size == 3 }
        val t0 = now
        runUntil(host, client, what = "5 s bez autowyboru") { now > t0 + 5_000 }
        assertTrue("słuchawki, zegarek i tablet: nic nie wybrane samo", client.state is LinkState.Searching)
        assertEquals(0, air.count())
        // pomyłka: słuchawki nie słuchają na usłudze mNotes
        client.choose(buds.address)
        runUntil(host, client, what = "odmowa słuchawek") { client.state is LinkState.Searching && air.count() == 1 }
        runUntil(host, client, what = "słuchawki znowu na liście") { (client.state as? LinkState.Searching)?.found?.any { it.id == buds.address } == true }
        client.choose(tablet.address)
        runUntil(host, client, what = "cyfry") { host.state is LinkState.ConfirmDigits && client.state is LinkState.ConfirmDigits }
        val a = host.state as LinkState.ConfirmDigits; val b = client.state as LinkState.ConfirmDigits
        assertEquals(a.sas, b.sas)
        assertEquals(phone.name, a.peer); assertEquals(tablet.name, b.peer)
        assertArrayEquals(host.pairResult!!.pairKey, client.pairResult!!.pairKey)
        assertEquals(tablet.address, pt.lastPeer?.address)
        assertEquals(phone.address, tt.lastPeer?.address)
        tt.close(); pt.close()
    }

    @Test
    fun photoInPartsGoesStraightToTheSavedTablet() {
        val seen = Seen()
        val tt = RfcommTransport(Port(tablet, emptyList()), post)
        val pt = RfcommTransport(Port(phone, listOf(buds, tablet)), post, known = tablet.address)
        val host = machine(tt, LinkMode.RECEIVE, SyncRole.HOST, tablet.name, key, seen)
        val client = machine(pt, LinkMode.SEND, SyncRole.CLIENT, phone.name, key)
        host.start(); client.start()
        val data = photo(700_000, 1)  // 3 części po 256 KB
        client.sendPhoto("tablica.jpg", "image/jpeg", data)
        runUntil(host, client, what = "zdjęcie na tablecie") { seen.photos.size == 1 && client.state is LinkState.Ready }
        assertArrayEquals(data, seen.photos[0].bytes)
        assertEquals("tablica.jpg", seen.photos[0].name)
        assertEquals("ze znanym adresem bez prób na słuchawkach", 1, air.count())
        tt.close(); pt.close()
    }

    @Test
    fun phoneOpenedFirstRetriesEveryThreeSecondsUntilTheTabletListens() {
        val seen = Seen()
        val tt = RfcommTransport(Port(tablet, emptyList()), post)
        val pt = RfcommTransport(Port(phone, listOf(tablet)), post, known = tablet.address)
        val host = machine(tt, LinkMode.RECEIVE, SyncRole.HOST, tablet.name, key, seen)
        val client = machine(pt, LinkMode.SEND, SyncRole.CLIENT, phone.name, key)
        client.start()
        client.sendPhoto("a.jpg", "image/jpeg", photo(10_000, 2))
        val t0 = now
        runUntil(client, what = "12 s bez tabletu") { now > t0 + 12_000 }
        val tries = air.count()
        assertTrue("próby $tries w 12 s: nie częściej niż co 3 s", tries in 2..5)
        host.start()
        runUntil(host, client, what = "zdjęcie po otwarciu okna na tablecie") { seen.photos.size == 1 }
        tt.close(); pt.close()
    }

    @Test
    fun droppedLinkResumesThePhotoAndDeliversItOnce() {
        val seen = Seen()
        val tt = RfcommTransport(Port(tablet, emptyList()), post)
        val pt = RfcommTransport(Port(phone, listOf(tablet)), post, known = tablet.address)
        val host = machine(tt, LinkMode.RECEIVE, SyncRole.HOST, tablet.name, key, seen)
        val client = machine(pt, LinkMode.SEND, SyncRole.CLIENT, phone.name, key)
        host.start(); client.start()
        val data = photo(2_100_000, 3)  // 9 części
        client.sendPhoto("duze.jpg", "image/jpeg", data)
        runUntil(host, client, what = "pół zdjęcia") { (client.state as? LinkState.Sending)?.let { it.part >= 3 } == true }
        air.cut()  // Bluetooth na chwilę zniknął
        runUntil(host, client, what = "zdjęcie po zerwaniu") { seen.photos.isNotEmpty() && client.state is LinkState.Ready }
        assertEquals(1, seen.photos.size)
        assertArrayEquals(data, seen.photos[0].bytes)
        assertTrue("było ponowne łączenie", air.count() >= 2)
        tt.close(); pt.close()
    }

    @Test
    fun phoneWithAnotherPairKeyNeverReachesTheTablet() {
        val seen = Seen()
        val tt = RfcommTransport(Port(tablet, emptyList()), post)
        val pt = RfcommTransport(Port(phone, listOf(tablet)), post, known = tablet.address)
        val host = machine(tt, LinkMode.RECEIVE, SyncRole.HOST, tablet.name, key, seen)
        val client = machine(pt, LinkMode.SEND, SyncRole.CLIENT, phone.name, otherKey)
        host.start(); client.start()
        client.sendPhoto("x.jpg", "image/jpeg", photo(5_000, 4))
        val t0 = now
        runUntil(host, client, what = "20 s") { now > t0 + 20_000 }
        assertTrue(seen.photos.isEmpty())
        assertTrue("inny UUID usługi: tablet nie słucha", host.state is LinkState.Searching)
        assertFalse(client.state is LinkState.Ready || client.state is LinkState.Sending)
        tt.close(); pt.close()
    }

    @Test
    fun pairMadeOverNearbyFindsTheTabletAmongBondedDevices() {
        val seen = Seen()
        val tt = RfcommTransport(Port(tablet, emptyList()), post)
        val pt = RfcommTransport(Port(phone, listOf(buds, watch, tablet)), post)  // bez zapisanego adresu
        val host = machine(tt, LinkMode.RECEIVE, SyncRole.HOST, tablet.name, key, seen)
        val client = machine(pt, LinkMode.SEND, SyncRole.CLIENT, phone.name, key)
        host.start(); client.start()
        val data = photo(50_000, 5)
        client.sendPhoto("b.jpg", "image/jpeg", data)
        runUntil(host, client, what = "zdjęcie przez listę sparowanych") { seen.photos.size == 1 }
        assertArrayEquals(data, seen.photos[0].bytes)
        assertEquals("adres do zapisania przy parze", tablet.address, pt.lastPeer?.address)
        tt.close(); pt.close()
    }
}
