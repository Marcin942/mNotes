package com.mdev.mnotes.core.nearby

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Kto się ogłasza (tablet: [HOST]) i kto szuka (telefon: [CLIENT]); od roli zależy kolejność w identyfikatorze sesji. */
enum class SyncRole { HOST, CLIENT; val opposite get() = if (this == HOST) CLIENT else HOST }

/**
 * Nearby przyjmuje ładunek BYTES do ok. 32 KB, więc wiadomość idzie w porcjach po [CHUNK] bajtów z bajtem flagi
 * (1: ciąg dalszy, 0: ostatnia). Limit wiadomości podniesiony względem klawiatury (1 MiB) do [MAX_MESSAGE], bo zdjęcie
 * z telefonu ma kilka MB. Nearby zachowuje kolejność ładunków.
 */
object LinkChunks {
    const val CHUNK = 30_000
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

    companion object {
        private const val PHOTO = 1; private const val NOTE = 2; private const val LIST = 3; private const val REQUEST = 4; private const val DATA = 5

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
 * Parowanie bez serwera: w trybie parowania obie strony wymieniają jawnie [Offer] (klucz publiczny i nazwę
 * urządzenia), liczą klucz pary i pokazują sześć cyfr [SyncCrypto.sas]; użytkownik potwierdza, że są takie same.
 * Tekst [Offer.toCode] może też iść kodem QR (gdy w aplikacji będzie skaner).
 */
object Pairing {
    const val PAIRING_NAME = "mn-parowanie"
    private const val PREFIX = "mnotes-para:1:"

    class Offer(val publicKey: ByteArray, val deviceName: String) {
        fun encode(): ByteArray = ByteArrayOutputStream().also { bo ->
            DataOutputStream(bo).use { it.writeUTF(PREFIX); it.writeInt(publicKey.size); it.write(publicKey); it.writeUTF(deviceName.take(40)) }
        }.toByteArray()

        fun toCode(): String = PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey) + ":" + deviceName.take(40)
    }

    fun decode(b: ByteArray): Offer? = try {
        DataInputStream(ByteArrayInputStream(b)).use { i ->
            if (i.readUTF() != PREFIX) return null
            val n = i.readInt()
            if (n !in 60..200) return null
            val key = ByteArray(n).also { i.readFully(it) }
            Offer(key, i.readUTF())
        }
    } catch (_: IOException) {
        null
    }

    fun fromCode(code: String): Offer? {
        if (!code.startsWith(PREFIX)) return null
        val rest = code.removePrefix(PREFIX)
        val cut = rest.indexOf(':')
        if (cut <= 0) return null
        val key = runCatching { java.util.Base64.getUrlDecoder().decode(rest.substring(0, cut)) }.getOrNull() ?: return null
        return Offer(key, rest.substring(cut + 1))
    }
}
