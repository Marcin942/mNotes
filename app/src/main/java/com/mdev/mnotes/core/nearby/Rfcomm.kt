package com.mdev.mnotes.core.nearby

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue

/**
 * Identyfikatory usług Bluetooth RFCOMM (2e-B1). W parowaniu stały [PAIR]; po
 * parowaniu UUID z nazwy łącza ([SyncCrypto.nearbyName], skrót klucza pary), więc obcy telefon nie trafi w nasłuch
 * tabletu — to odpowiednik nazwy punktu w Nearby.
 */
object RfcommIds {
    val PAIR: UUID = UUID.fromString("8b5e2c4a-3f71-4d09-b6a2-71c9e04d5f18")
    const val SERVICE = "mNotes"

    fun uuidFor(name: String): UUID =
        if (name.startsWith(LinkMachine.PAIR_PREFIX)) PAIR else UUID.nameUUIDFromBytes("mnotes-rfcomm|$name".toByteArray())
}

/**
 * RFCOMM to strumień bez granic wiadomości: każda porcja z [LinkChunks] idzie jako długość u32 + bajty. Odczyt
 * składa ramkę z dowolnie pociętych kawałków; długość ponad [max] albo urwana ramka to [IOException].
 */
object StreamFramer {
    fun write(out: OutputStream, msg: ByteArray) {
        val n = msg.size
        out.write(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()))
        out.write(msg)
        out.flush()
    }

    /** Następna ramka albo null, gdy strumień skończył się równo na granicy ramek (druga strona zamknęła). */
    fun read(inp: InputStream, max: Int = LinkChunks.CHUNK + 1): ByteArray? {
        val first = inp.read()
        if (first < 0) return null
        val d = DataInputStream(inp)
        val n = try { (first shl 24) or (d.readUnsignedByte() shl 16) or (d.readUnsignedShort()) } catch (e: EOFException) { throw IOException("urwana długość ramki") }
        if (n < 0 || n > max) throw IOException("zła długość ramki: $n")
        return ByteArray(n).also { try { d.readFully(it) } catch (e: EOFException) { throw IOException("urwana ramka") } }
    }
}

/** [major]: główna klasa urządzenia Bluetooth (`BluetoothClass.Device.Major`), -1 gdy nieznana. */
class BtDevice(val address: String, val name: String, val major: Int = -1)

/**
 * Wybór z listy sparowanych (3b-B3): słuchawki, głośniki, zegarki, myszy, drukarki i opaski nie mogą być tabletem,
 * więc nie trafiają na listę parowania ani do prób łączenia (każda próba to kilka sekund). Telefony i komputery
 * (tablety Samsung zgłaszają się jako jedno z nich) idą pierwsze, nieznana klasa na końcu. Gdyby filtr wyciął
 * wszystko, zostaje pełna lista (lepiej za dużo niż nic).
 */
object BtPick {
    const val COMPUTER = 0x0100
    const val PHONE = 0x0200
    private val NOT_PEER = setOf(0x0300, 0x0400, 0x0500, 0x0600, 0x0700, 0x0800, 0x0900)

    fun peers(list: List<BtDevice>): List<BtDevice> {
        val ok = list.filter { it.major !in NOT_PEER }.sortedBy { if (it.major == COMPUTER || it.major == PHONE) 0 else 1 }
        return ok.ifEmpty { list }
    }
}

/** Otwarte gniazdo RFCOMM: [peer] to druga strona. [close] przerywa czekające odczyty i zapisy (także z innych wątków). */
interface BtPipe {
    val peer: BtDevice
    val input: InputStream
    val output: OutputStream
    fun close()
}

/** Nasłuch na jednym UUID; [accept] czeka na połączenie, po [close] rzuca [IOException]. */
interface BtServer {
    fun accept(): BtPipe
    fun close()
}

/**
 * Cienka warstwa nad `BluetoothAdapter` (w aplikacji `AndroidBtPort`, w testach symulator). Metody mogą rzucić
 * [SecurityException] (brak zgody) albo [IOException]; [listen] i [connect] blokują, więc [RfcommTransport] woła je
 * z własnych wątków.
 */
interface BtPort {
    fun available(): Boolean = true
    fun enabled(): Boolean
    fun bonded(): List<BtDevice>
    fun listen(uuid: UUID, name: String): BtServer
    fun connect(address: String, uuid: UUID): BtPipe
    /** „Szukaj w pobliżu”: [found] dla każdego urządzenia (z wątku systemu); false, gdy szukanie nie ruszyło. */
    fun startDiscovery(found: (BtDevice) -> Unit): Boolean = false
    fun cancelDiscovery() {}
}

/**
 * [LinkTransport] przez Bluetooth Classic RFCOMM (2e-B1): tablet słucha na UUID z [RfcommIds], telefon łączy się z
 * adresem tabletu. Kolejność zdarzeń jak w Nearby (połączenie → [LinkMachine.onInitiated] → `accept` →
 * [LinkMachine.onConnected]), więc [LinkMachine] jest ta sama. Gniazdo „insecure” bez systemowego bondingu: szyfruje
 * i uwierzytelnia [SyncCodec] z kluczem pary.
 *
 * Wątki: nasłuch, łączenie, odczyt i zapis na połączenie. Każde zdarzenie idzie przez [post] na wątek maszyny
 * (w aplikacji główny), tam też żyje cały stan transportu, więc bez blokad.
 * [known]: zapisany adres drugiej strony po parowaniu; bez niego (para z Nearby) telefon próbuje po kolei
 * urządzeń sparowanych w systemie. [lastPeer]: z kim ostatnio połączono (UI zapisuje adres przy parze).
 */
class RfcommTransport(
    private val port: BtPort,
    private val post: (() -> Unit) -> Unit,
    private val known: String? = null,
) : LinkTransport {
    lateinit var machine: LinkMachine

    override val autoPick: Boolean get() = false

    var lastPeer: BtDevice? = null
        private set

    private inner class Conn(val id: String, val pipe: BtPipe) {
        val out = LinkedBlockingQueue<ByteArray>()
        @Volatile var closed = false
        private var started = false

        fun start() {
            if (started) return
            started = true
            daemon("rfcomm-odczyt") {
                try {
                    while (true) {
                        val m = StreamFramer.read(pipe.input) ?: break
                        post { if (conns[id] === this) machine.onReceived(id, m) }
                    }
                } catch (e: IOException) {
                    post { if (conns[id] === this) machine.note("rfcomm: odczyt przerwany: ${LinkScrub.error(e)}") }
                } finally {
                    close()
                    post { if (conns[id] === this) { conns.remove(id); machine.onDisconnected(id) } }
                }
            }
            daemon("rfcomm-zapis") {
                try {
                    while (true) {
                        val b = out.take()
                        if (b === STOP) break
                        StreamFramer.write(pipe.output, b)
                    }
                } catch (e: IOException) {
                    post { machine.note("rfcomm: zapis przerwany: ${LinkScrub.error(e)}") }
                    close()  // odczyt to zauważy i zgłosi rozłączenie
                }
            }
        }

        fun close() {
            if (closed) return
            closed = true
            out.offer(STOP)
            runCatching { pipe.close() }
        }
    }

    private val conns = HashMap<String, Conn>()
    private var server: BtServer? = null
    /** Adres, z którym właśnie łączymy (wynik po [disconnect] albo nowym [request] jest wyrzucany). */
    private var dialing: String? = null
    private var searching = false

    private fun ready(): Boolean {
        val code = when {
            !port.available() -> LinkErrors.NO_BT_HARDWARE
            !runCatching { port.enabled() }.getOrDefault(false) -> LinkErrors.BT_OFF
            else -> return true
        }
        post { machine.onFailed(code) }
        return false
    }

    override fun advertise(name: String) {
        closeServer()
        if (!ready()) return
        val s = try { port.listen(RfcommIds.uuidFor(name), RfcommIds.SERVICE) } catch (e: SecurityException) {
            post { machine.note("rfcomm: nasłuch bez zgody: ${LinkScrub.error(e)}"); machine.onFailed(LinkErrors.NO_PERMISSION) }; return
        } catch (e: IOException) {
            post { machine.note("rfcomm: nasłuch nieudany: ${LinkScrub.error(e)}"); machine.onFailed(LinkErrors.RADIO_ERROR) }; return
        }
        server = s
        daemon("rfcomm-nasluch") {
            while (true) {
                val p = try { s.accept() } catch (e: IOException) { break }
                post { if (server === s) incoming(p, name) else p.close() }
            }
        }
    }

    private fun incoming(p: BtPipe, own: String) {
        val id = p.peer.address
        conns.remove(id)?.close()
        conns[id] = Conn(id, p)
        machine.onInitiated(id, peerName(own, p.peer))
    }

    override fun discover(name: String) {
        searching = true
        if (!ready()) return
        val pairing = name.startsWith(LinkMachine.PAIR_PREFIX)
        val list = try { if (pairing || known == null) BtPick.peers(port.bonded()) else emptyList() } catch (e: SecurityException) {
            post { machine.onFailed(LinkErrors.NO_PERMISSION) }; return
        }
        val found = if (pairing) list.map { it.address to LinkMachine.PAIR_PREFIX + it.name }
            else (known?.let(::listOf) ?: list.map { it.address }).map { it to name }
        post { if (searching) for ((id, n) in found) machine.onFound(id, n) }
    }

    /** „Szukaj w pobliżu” w parowaniu (zapas, gdy tabletu nie ma na liście sparowanych); false, gdy nie ruszyło. */
    fun searchNearby(): Boolean = try {
        port.startDiscovery { d -> post { if (searching) machine.onFound(d.address, LinkMachine.PAIR_PREFIX + d.name) } }
    } catch (e: SecurityException) { post { machine.onFailed(LinkErrors.NO_PERMISSION) }; false }

    override fun stopSearch() {
        searching = false
        closeServer()
        runCatching { port.cancelDiscovery() }
    }

    override fun request(endpoint: String, name: String) {
        runCatching { port.cancelDiscovery() }  // szukanie w tle bardzo spowalnia łączenie
        dialing = endpoint
        val uuid = RfcommIds.uuidFor(name)
        daemon("rfcomm-laczenie") {
            val t0 = System.currentTimeMillis()
            val p = try { port.connect(endpoint, uuid) } catch (e: SecurityException) {
                post { if (dialing == endpoint) { dialing = null; machine.note("rfcomm: łączenie bez zgody: ${LinkScrub.error(e)}"); machine.onConnectFailed(endpoint, LinkErrors.NO_PERMISSION) } }
                return@daemon
            } catch (e: IOException) {
                val ms = System.currentTimeMillis() - t0
                post { if (dialing == endpoint) { dialing = null; machine.note("rfcomm: łączenie nieudane po $ms ms: ${LinkScrub.error(e)}"); machine.onConnectFailed(endpoint, LinkErrors.PEER_NOT_LISTENING) } }
                return@daemon
            }
            val ms = System.currentTimeMillis() - t0
            post { machine.note("rfcomm: gniazdo otwarte po $ms ms") }
            post {
                if (dialing != endpoint) { p.close(); return@post }
                dialing = null
                conns.remove(endpoint)?.close()
                conns[endpoint] = Conn(endpoint, p)
                machine.onInitiated(endpoint, peerName(name, p.peer))
            }
        }
    }

    override fun accept(endpoint: String) {
        val c = conns[endpoint]
        if (c == null) { post { machine.onConnectFailed(endpoint, LinkErrors.ENDPOINT_IO) }; return }
        // dopiero połączenie przyjęte przez maszynę: gniazdo odrzucone (przed zamknięciem nasłuchu) nie trafi jako adres pary
        lastPeer = c.pipe.peer
        post { if (conns[endpoint] === c) machine.onConnected(endpoint) }
        c.start()
    }

    override fun reject(endpoint: String) { conns.remove(endpoint)?.close() }

    override fun send(endpoint: String, bytes: ByteArray) { conns[endpoint]?.out?.offer(bytes) }

    override fun disconnect(endpoint: String) {
        if (dialing == endpoint) dialing = null
        conns.remove(endpoint)?.close()
    }

    fun close() {
        stopSearch()
        dialing = null
        conns.values.forEach { it.close() }
        conns.clear()
    }

    private fun closeServer() { server?.let { server = null; runCatching { it.close() } } }

    /** W parowaniu druga strona to „mnp|” + jej nazwa Bluetooth; po parowaniu obie mają tę samą nazwę łącza. */
    private fun peerName(own: String, peer: BtDevice) =
        if (own.startsWith(LinkMachine.PAIR_PREFIX)) LinkMachine.PAIR_PREFIX + peer.name.ifBlank { peer.address } else own

    private fun daemon(name: String, body: () -> Unit) { Thread(body, name).apply { isDaemon = true }.start() }

    private companion object { val STOP = ByteArray(0) }
}

/**
 * Zeszyty przez Bluetooth (2e-B1, założenie z decyzji autora): do [NOTEBOOK_MAX], z czasem podanym przed wysłaniem.
 * [BYTES_PER_S]: środek 80–250 KB/s z analizy (§4.2) — **do zmierzenia na tablecie**.
 */
object BtLimits {
    const val NOTEBOOK_MAX = 15L shl 20
    const val BYTES_PER_S = 150_000L

    fun seconds(bytes: Long): Long = maxOf(1L, (bytes + BYTES_PER_S - 1) / BYTES_PER_S)
}
