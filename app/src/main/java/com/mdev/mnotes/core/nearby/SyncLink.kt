package com.mdev.mnotes.core.nearby

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Uprawnienia łącza na danej wersji Androida (`docs/NEARBY-PRZEBUDOWA.md` §3.6). Dokładna lokalizacja jest wymagana
 * wszędzie: notatka z innej działającej aplikacji Nearby mówi, że szukanie bez niej zwraca 8034/8036 także na nowych
 * Androidach (B2-9 przyjmował przybliżoną: to był błąd). Przybliżona jest na liście, bo Android 12+ pyta o obie naraz.
 */
object LinkPermissions {
    const val FINE = "android.permission.ACCESS_FINE_LOCATION"
    const val COARSE = "android.permission.ACCESS_COARSE_LOCATION"

    fun required(sdk: Int): List<String> = buildList {
        if (sdk >= 31) { add("android.permission.BLUETOOTH_SCAN"); add("android.permission.BLUETOOTH_ADVERTISE"); add("android.permission.BLUETOOTH_CONNECT") }
        if (sdk >= 33) add("android.permission.NEARBY_WIFI_DEVICES")
        add(FINE); add(COARSE)
    }

    /** Wynik prośby: wszystkie wymagane przyznane (przybliżona sama nie wystarcza). */
    fun granted(result: Map<String, Boolean>): Boolean = result.isNotEmpty() && result.all { (k, v) -> v || k == COARSE && result[FINE] == true }

    /** Których jeszcze brakuje (do ekranu „Dlaczego” i diagnostyki); [has] mówi, czy uprawnienie jest przyznane. */
    fun missing(sdk: Int, has: (String) -> Boolean): List<String> = missing(required(sdk), has)

    fun missing(required: List<String>, has: (String) -> Boolean): List<String> = required.filter { !has(it) && (it != COARSE || FINE !in required) }

    /**
     * Uprawnienia pytane w czasie działania dla Bluetooth RFCOMM (2e-B1, `plan/NEARBY-OPEN-SOURCE.md` §3.2). Do Androida
     * 11 łączenie i nasłuch biorą `BLUETOOTH` z instalacji (bez pytania); lokalizacja tylko przy [discovery] („Szukaj w
     * pobliżu”): przybliżona do 9, dokładna 10–11. Od 12 jedno „Urządzenia w pobliżu”: CONNECT, SCAN przy szukaniu
     * (w manifeście `neverForLocation`, więc bez lokalizacji), ADVERTISE przy [visible] („Pokaż tablet na 2 min”).
     */
    fun rfcomm(sdk: Int, discovery: Boolean = false, visible: Boolean = false): List<String> = buildList {
        if (sdk >= 31) {
            add("android.permission.BLUETOOTH_CONNECT")
            if (discovery) add("android.permission.BLUETOOTH_SCAN")
            if (visible) add("android.permission.BLUETOOTH_ADVERTISE")
        } else if (discovery) {
            if (sdk >= 29) add(FINE)
            add(COARSE)
        }
    }
}

/** Kto się ogłasza (tablet: [HOST]) i kto szuka (telefon: [CLIENT]); od roli zależy kolejność w identyfikatorze sesji. */
enum class SyncRole { HOST, CLIENT; val opposite get() = if (this == HOST) CLIENT else HOST }

/**
 * Wiadomość w porcjach po [CHUNK] bajtów z bajtem flagi (1: ciąg dalszy, 0: ostatnia). Limit ładunku BYTES w
 * `play-services-nearby:19.3.0` to `ConnectionsClient.MAX_BYTES_DATA_SIZE` = 1 047 552 B (odczytane z jara; wcześniejszy
 * materiał zakładał 32 KB), więc porcja to 900 000 B i część zdjęcia (256 KB, [PhotoSender]) idzie jedną ramką.
 * Odbiornik skleja porcje dowolnej wielkości (stare 30 KB też). Nearby zachowuje kolejność ładunków.
 */
object LinkChunks {
    const val CHUNK = 900_000
    const val MAX_MESSAGE = 24 shl 20

    fun split(data: ByteArray, chunk: Int = CHUNK): List<ByteArray> {
        if (data.isEmpty()) return listOf(byteArrayOf(0))
        return (data.indices step chunk).map { from ->
            val to = minOf(from + chunk, data.size)
            byteArrayOf(if (to < data.size) 1 else 0) + data.copyOfRange(from, to)
        }
    }
}

/** Skleja porcje z [LinkChunks.split]; za długą wiadomość albo złą flagę odrzuca (wyjątek zrywa łącze). */
class ChunkJoiner(private val max: Int = LinkChunks.MAX_MESSAGE) {
    private val buf = ByteArrayOutputStream()

    fun feed(part: ByteArray): ByteArray? {
        if (part.isEmpty()) throw IOException("pusta porcja")
        buf.write(part, 1, part.size - 1)
        if (buf.size() > max) { buf.reset(); throw IOException("wiadomość ponad $max B") }
        return when (part[0].toInt()) {
            0 -> buf.toByteArray().also { buf.reset() }
            1 -> null
            else -> throw IOException("zła flaga porcji")
        }
    }
}

/**
 * Szyfrowanie ramek jednej sesji: każda strona zaczyna od `hello` z losowym nonce, identyfikator sesji to skrót obu
 * nonce (nagranie z innego połączenia nie przejdzie), a licznik `seq` w AAD odrzuca powtórki w obrębie sesji.
 */
class SyncCodec(private val pairKey: ByteArray, private val myRole: SyncRole) {
    private val myNonce = SyncCrypto.randomNonce()
    private var sessionId: String? = null
    private var sendSeq = 0L
    private var recvSeq = 0L

    val ready: Boolean get() = sessionId != null

    fun hello(): ByteArray = frame(0, SyncCrypto.seal(pairKey, myNonce, helloAad(myRole)))

    /** Przyjmuje `hello` drugiej strony; fałszywe albo od tej samej roli rzuca [CryptoException]. */
    fun acceptHello(raw: ByteArray) {
        val (seq, blob) = unframe(raw)
        if (seq != 0L) throw CryptoException("pierwsza ramka nie jest hello")
        val peerNonce = SyncCrypto.open(pairKey, blob, helloAad(myRole.opposite))
        val digest = MessageDigest.getInstance("SHA-256")
        if (myRole == SyncRole.HOST) { digest.update(myNonce); digest.update(peerNonce) } else { digest.update(peerNonce); digest.update(myNonce) }
        sessionId = SyncCrypto.hex(digest.digest()).take(32)
        sendSeq = 0
        recvSeq = 0
    }

    fun seal(msg: SyncMsg): ByteArray {
        val sid = sessionId ?: throw CryptoException("sesja bez hello")
        val seq = ++sendSeq
        return frame(seq, SyncCrypto.seal(pairKey, SyncMsg.encode(msg), aad(sid, myRole, seq)))
    }

    /** Ramka drugiej strony; zła kryptografia albo powtórka rzuca, nieznany typ wiadomości (nowsza wersja) daje null. */
    fun open(raw: ByteArray): SyncMsg? {
        val sid = sessionId ?: throw CryptoException("sesja bez hello")
        val (seq, blob) = unframe(raw)
        if (seq <= recvSeq) throw CryptoException("powtórzona albo spóźniona ramka $seq")
        val plain = SyncCrypto.open(pairKey, blob, aad(sid, myRole.opposite, seq))
        recvSeq = seq
        return SyncMsg.decode(plain)
    }

    private fun helloAad(role: SyncRole) = "mnotes-sync/1|hello|${role.name}".toByteArray()
    private fun aad(sid: String, from: SyncRole, seq: Long) = "mnotes-sync/1|$sid|${from.name}|$seq".toByteArray()
    private fun frame(seq: Long, blob: ByteArray): ByteArray = ByteBuffer.allocate(8 + blob.size).putLong(seq).put(blob).array()
    private fun unframe(raw: ByteArray): Pair<Long, ByteArray> {
        if (raw.size < 8) throw CryptoException("za krótka ramka")
        return ByteBuffer.wrap(raw).long to raw.copyOfRange(8, raw.size)
    }
}

/** Metadane strony do synchronizacji: [stamp] = czas ostatniej zmiany (ms), [hash] = skrót treści strony. */
data class PageMeta(val id: String, val stamp: Long, val hash: String)

/** Wiadomości łącza (po `hello`). Kodowanie binarne z bajtem typu; nieznany typ = null (zgodność w przód). */
sealed interface SyncMsg {
    /** Zdjęcie z telefonu do wstawienia na bieżącą stronę tabletu. */
    class Photo(val name: String, val mime: String, val bytes: ByteArray) : SyncMsg
    /** Potwierdzenie albo krótki komunikat dla użytkownika („wstawiono”, „zeszyt nie istnieje”). */
    data class Note(val text: String) : SyncMsg
    /** Lista stron zeszytu (sync): kto ma co i w jakiej wersji. */
    data class PageList(val notebookId: String, val title: String, val pages: List<PageMeta>) : SyncMsg
    data class PageRequest(val notebookId: String, val ids: List<String>) : SyncMsg
    class PageData(val notebookId: String, val meta: PageMeta, val bytes: ByteArray) : SyncMsg
    /** Część zdjęcia ([PhotoSender]): nagłówek całości w każdej części, żeby odbiorca mógł zacząć od dowolnej. */
    class PhotoPart(val id: String, val index: Int, val count: Int, val sha: ByteArray, val name: String, val mime: String, val bytes: ByteArray) : SyncMsg
    data class PhotoAck(val id: String, val index: Int) : SyncMsg
    /** Zdjęcie złożone ([ok]) albo odrzucone (zła suma kontrolna, za duże). */
    data class PhotoDone(val id: String, val ok: Boolean) : SyncMsg

    companion object {
        private const val PHOTO = 1; private const val NOTE = 2; private const val LIST = 3; private const val REQUEST = 4; private const val DATA = 5
        private const val PART = 6; private const val ACK = 7; private const val DONE = 8

        fun encode(m: SyncMsg): ByteArray {
            val bo = ByteArrayOutputStream()
            DataOutputStream(bo).use { o ->
                fun bytes(b: ByteArray) { o.writeInt(b.size); o.write(b) }
                fun meta(p: PageMeta) { o.writeUTF(p.id); o.writeLong(p.stamp); o.writeUTF(p.hash) }
                when (m) {
                    is Photo -> { o.writeByte(PHOTO); o.writeUTF(m.name); o.writeUTF(m.mime); bytes(m.bytes) }
                    is Note -> { o.writeByte(NOTE); o.writeUTF(m.text) }
                    is PageList -> { o.writeByte(LIST); o.writeUTF(m.notebookId); o.writeUTF(m.title); o.writeInt(m.pages.size); m.pages.forEach(::meta) }
                    is PageRequest -> { o.writeByte(REQUEST); o.writeUTF(m.notebookId); o.writeInt(m.ids.size); m.ids.forEach(o::writeUTF) }
                    is PageData -> { o.writeByte(DATA); o.writeUTF(m.notebookId); meta(m.meta); bytes(m.bytes) }
                    is PhotoPart -> { o.writeByte(PART); o.writeUTF(m.id); o.writeInt(m.index); o.writeInt(m.count); bytes(m.sha); o.writeUTF(m.name); o.writeUTF(m.mime); bytes(m.bytes) }
                    is PhotoAck -> { o.writeByte(ACK); o.writeUTF(m.id); o.writeInt(m.index) }
                    is PhotoDone -> { o.writeByte(DONE); o.writeUTF(m.id); o.writeBoolean(m.ok) }
                }
            }
            return bo.toByteArray()
        }

        fun decode(b: ByteArray): SyncMsg? = try {
            DataInputStream(ByteArrayInputStream(b)).use { i ->
                fun bytes(): ByteArray { val n = i.readInt(); if (n < 0 || n > LinkChunks.MAX_MESSAGE) throw IOException("zła długość"); return ByteArray(n).also { i.readFully(it) } }
                fun meta() = PageMeta(i.readUTF(), i.readLong(), i.readUTF())
                fun count(): Int = i.readInt().also { if (it < 0 || it > 100_000) throw IOException("zła liczba") }
                when (i.readUnsignedByte()) {
                    PHOTO -> Photo(i.readUTF(), i.readUTF(), bytes())
                    NOTE -> Note(i.readUTF())
                    LIST -> PageList(i.readUTF(), i.readUTF(), List(count()) { meta() })
                    REQUEST -> PageRequest(i.readUTF(), List(count()) { i.readUTF() })
                    DATA -> PageData(i.readUTF(), meta(), bytes())
                    PART -> PhotoPart(i.readUTF(), i.readInt(), i.readInt(), bytes(), i.readUTF(), i.readUTF(), bytes())
                    ACK -> PhotoAck(i.readUTF(), i.readInt())
                    DONE -> PhotoDone(i.readUTF(), i.readBoolean())
                    else -> null
                }
            }
        } catch (_: IOException) {
            null
        }
    }
}

/**
 * Plan synchronizacji jednego zeszytu między dwoma urządzeniami. Bez wspólnej historii, więc reguła jest prosta i
 * bezpieczna: strona tylko u nas → wyślij; tylko u nich → poproś; różna treść → nowsza wygrywa, a przy tym samym
 * czasie zmiany zostają obie wersje (konflikt: druga jako kopia strony). Format `.mnotes` się nie zmienia.
 */
data class SyncPlan(val send: List<String>, val request: List<String>, val conflicts: List<String>) {
    val isEmpty get() = send.isEmpty() && request.isEmpty() && conflicts.isEmpty()

    companion object {
        fun of(local: List<PageMeta>, remote: List<PageMeta>): SyncPlan {
            val mine = local.associateBy { it.id }; val theirs = remote.associateBy { it.id }
            val send = ArrayList<String>(); val request = ArrayList<String>(); val conflicts = ArrayList<String>()
            for (p in local) {
                val r = theirs[p.id]
                when {
                    r == null -> send += p.id
                    r.hash == p.hash -> Unit
                    p.stamp > r.stamp -> send += p.id
                    p.stamp < r.stamp -> request += p.id
                    else -> conflicts += p.id
                }
            }
            for (r in remote) if (r.id !in mine) request += r.id
            return SyncPlan(send, request, conflicts)
        }
    }
}

/**
 * Parowanie bez serwera, wersja 2 (commit-then-reveal, jak porównanie liczb w Bluetooth SSP): każda strona najpierw
 * wysyła [Commit] = SHA-256 swojej [Offer] (klucz publiczny, losowy nonce, nazwa), a samą ofertę dopiero po otrzymaniu
 * zobowiązania drugiej strony. Napastnik w środku musi więc zobowiązać się do swojego klucza, zanim pozna nonce
 * ofiary, i nie dobierze klucza pod sześć cyfr [SyncCrypto.sas]. Wersja 1 (jawna oferta od razu) nie jest przyjmowana:
 * obie aplikacje muszą być w tej samej wersji. Tekst [Offer.toCode] może też iść kodem QR (gdy będzie skaner).
 */
object Pairing {
    const val PAIRING_NAME = "mn-parowanie"
    private const val PREFIX = "mnotes-para:2:"
    private const val COMMIT_PREFIX = "mnotes-zobowiazanie:2:"
    const val NONCE_LEN = 16

    /** Zobowiązanie do oferty: tylko skrót, nic nie zdradza. */
    class Commit(val hash: ByteArray) {
        fun encode(): ByteArray = ByteArrayOutputStream().also { bo ->
            DataOutputStream(bo).use { it.writeUTF(COMMIT_PREFIX); it.writeInt(hash.size); it.write(hash) }
        }.toByteArray()
    }

    class Offer(val publicKey: ByteArray, val nonce: ByteArray, val deviceName: String) {
        fun encode(): ByteArray = ByteArrayOutputStream().also { bo ->
            DataOutputStream(bo).use {
                it.writeUTF(PREFIX); it.writeInt(publicKey.size); it.write(publicKey); it.writeInt(nonce.size); it.write(nonce); it.writeUTF(deviceName.take(40))
            }
        }.toByteArray()

        /** Skrót całej zakodowanej oferty (klucz, nonce i nazwa: żadnego z nich nie da się zmienić po zobowiązaniu). */
        fun commitment(): ByteArray = SyncCrypto.sha256(encode())

        fun toCode(): String = PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().run { encodeToString(publicKey) + ":" + encodeToString(nonce) } + ":" + deviceName.take(40)
    }

    /** [Commit] albo [Offer]; null dla obcych bajtów (także oferty wersji 1). */
    fun decode(b: ByteArray): Any? = try {
        DataInputStream(ByteArrayInputStream(b)).use { i ->
            when (i.readUTF()) {
                COMMIT_PREFIX -> { val n = i.readInt(); if (n != 32) null else Commit(ByteArray(n).also { i.readFully(it) }) }
                PREFIX -> {
                    val n = i.readInt()
                    if (n !in 60..200) return null
                    val key = ByteArray(n).also { i.readFully(it) }
                    val m = i.readInt()
                    if (m != NONCE_LEN) return null
                    Offer(key, ByteArray(m).also { i.readFully(it) }, i.readUTF())
                }
                else -> null
            }
        }
    } catch (_: IOException) {
        null
    }

    fun fromCode(code: String): Offer? {
        if (!code.startsWith(PREFIX)) return null
        val parts = code.removePrefix(PREFIX).split(":", limit = 3)
        if (parts.size != 3) return null
        val dec = java.util.Base64.getUrlDecoder()
        val key = runCatching { dec.decode(parts[0]) }.getOrNull() ?: return null
        val nonce = runCatching { dec.decode(parts[1]) }.getOrNull()?.takeIf { it.size == NONCE_LEN } ?: return null
        return Offer(key, nonce, parts[2])
    }
}

/**
 * Parowanie jednej strony (czysta logika, bez Nearby): [start] daje zobowiązanie do wysłania od razu po połączeniu,
 * [receive] przyjmuje wiadomość drugiej strony i mówi, co odesłać i czy jest już wynik. Własna oferta wychodzi tylko
 * w odpowiedzi na zobowiązanie drugiej strony; oferta bez wcześniejszego zobowiązania albo niezgodna z nim (podmieniony
 * klucz, nonce, nazwa) rzuca [CryptoException] i parowanie trzeba zacząć od nowa.
 */
class PairingSession(
    deviceName: String,
    private val keys: SyncCrypto.EphemeralKeys = SyncCrypto.generateKeys(),
    nonce: ByteArray = ByteArray(Pairing.NONCE_LEN).also { java.security.SecureRandom().nextBytes(it) },
) {
    class Result(val sas: String, val peerName: String, val pairKey: ByteArray)

    /** [send]: wiadomości do wysłania w tej kolejności; [result]: wynik, gdy oferta drugiej strony się zgadza. */
    class Step(val send: List<ByteArray> = emptyList(), val result: Result? = null)

    private val mine = Pairing.Offer(keys.publicKey, nonce, deviceName)
    private var committed = false
    private var revealed = false
    private var peerCommit: ByteArray? = null
    var result: Result? = null
        private set

    /** Zobowiązanie do własnej oferty (pierwsza wiadomość po połączeniu). */
    fun start(): ByteArray { committed = true; return Pairing.Commit(mine.commitment()).encode() }

    fun receive(raw: ByteArray): Step {
        if (result != null) throw CryptoException("parowanie już zakończone")
        return when (val m = Pairing.decode(raw)) {
            is Pairing.Commit -> {
                if (peerCommit != null) throw CryptoException("drugie zobowiązanie w jednym parowaniu")
                peerCommit = m.hash
                val out = ArrayList<ByteArray>()
                if (!committed) out += start()
                if (!revealed) { revealed = true; out += mine.encode() }
                Step(out)
            }
            is Pairing.Offer -> {
                val commit = peerCommit ?: throw CryptoException("oferta przed zobowiązaniem")
                if (!SyncCrypto.same(commit, m.commitment())) throw CryptoException("oferta nie zgadza się ze zobowiązaniem (podmieniony klucz?)")
                if (SyncCrypto.same(m.publicKey, mine.publicKey)) throw CryptoException("własny klucz odbity z powrotem")
                val key = SyncCrypto.pairKey(keys, m.publicKey)
                Result(SyncCrypto.sas(key, mine.nonce, m.nonce), m.deviceName, key).let { result = it; Step(result = it) }
            }
            else -> throw CryptoException("to nie jest wiadomość parowania")
        }
    }
}
