package com.mdev.mchochlik.feature.keyboard

import com.mdev.chochlik.core.crypto.ChochlikCrypto
import com.mdev.chochlik.core.crypto.CryptoException
import com.mdev.chochlik.core.kbd.KbdLimits
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Szyfrowanie ramek łącza lokalnego (PROTOCOL §11.3). Każda strona zaczyna od `hello`
 * z losowym nonce; identyfikator sesji to skrót obu nonce, więc nagrane ramki z
 * poprzedniego połączenia nie przejdą, a licznik w AAD odrzuca powtórki w obrębie sesji.
 */
class LocalCodec(private val pairKey: ByteArray, private val myRole: KbdRole) {
    private val myNonce = ChochlikCrypto.randomNonce()
    private var sessionId: String? = null
    private var sendSeq = 0L
    private var recvSeq = 0L

    val ready: Boolean get() = sessionId != null

    fun hello(): ByteArray = frame(0, KeyboardCrypto.seal(pairKey, myNonce, helloAad(myRole)))

    /** Przyjmuje `hello` partnera; fałszywe albo od tej samej roli rzuca [CryptoException]. */
    fun acceptHello(raw: ByteArray) {
        val (seq, blob) = unframe(raw)
        if (seq != 0L) throw CryptoException("pierwsza ramka nie jest hello")
        val peerNonce = KeyboardCrypto.open(pairKey, blob, helloAad(myRole.opposite))
        val digest = MessageDigest.getInstance("SHA-256")
        // Kolejność nonce ustalona rolą, żeby obie strony policzyły to samo.
        if (myRole == KbdRole.SCREEN) {
            digest.update(myNonce); digest.update(peerNonce)
        } else {
            digest.update(peerNonce); digest.update(myNonce)
        }
        sessionId = ChochlikCrypto.hex(digest.digest()).take(32)
        sendSeq = 0
        recvSeq = 0
    }

    fun seal(msg: LocalMsg): ByteArray {
        val sid = sessionId ?: throw CryptoException("sesja bez hello")
        val seq = ++sendSeq
        val plain = kbdJson.encodeToString(LocalMsg.serializer(), msg).toByteArray()
        return frame(seq, KeyboardCrypto.seal(pairKey, plain, aad(sid, myRole, seq)))
    }

    /**
     * Rozpakowuje ramkę. Błędy kryptografii, `seq` i porcji rzucają (łącze jest do zerwania); uwierzytelniona,
     * ale nieznana albo niedekodowalna ramka (nowszy poziom protokołu) daje `null` i ma być pominięta (§11.6).
     */
    fun open(raw: ByteArray): LocalMsg? {
        val sid = sessionId ?: throw CryptoException("sesja bez hello")
        val (seq, blob) = unframe(raw)
        if (seq <= recvSeq) throw CryptoException("powtórzona albo spóźniona ramka $seq")
        val plain = KeyboardCrypto.open(pairKey, blob, aad(sid, myRole.opposite, seq))
        recvSeq = seq
        return decodeLocalMsg(plain)
    }

    private fun helloAad(role: KbdRole) = "chochlik-kbd/1|hello|${role.name}"

    private fun aad(sid: String, from: KbdRole, seq: Long) = "chochlik-kbd/1|$sid|${from.name}|$seq"

    private fun frame(seq: Long, blob: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + blob.size).putLong(seq).put(blob).array()

    private fun unframe(raw: ByteArray): Pair<Long, ByteArray> {
        if (raw.size < 8) throw CryptoException("za krótka ramka")
        val buf = ByteBuffer.wrap(raw)
        return buf.long to raw.copyOfRange(8, raw.size)
    }
}

/** `null` dla ramki, której ta wersja nie zna albo która przekracza limit poziomu 2 (§11.6). */
internal fun decodeLocalMsg(plain: ByteArray): LocalMsg? {
    if (plain.size > KbdLimits.FRAME_BYTES) return null
    return try {
        kbdJson.decodeFromString(LocalMsg.serializer(), plain.decodeToString())
    } catch (e: IllegalArgumentException) {
        // SerializationException dziedziczy po IllegalArgumentException.
        null
    }
}
