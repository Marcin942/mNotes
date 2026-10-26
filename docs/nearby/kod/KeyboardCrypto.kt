package com.mdev.mchochlik.feature.keyboard

import com.mdev.chochlik.core.crypto.ChochlikCrypto
import com.mdev.chochlik.core.crypto.CryptoException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Kryptografia pary telefon–tablet (D-033). Klucz pary powstaje z jednorazowego ECDH P-256
 * przy parowaniu; serwer widział tylko klucze publiczne, a podmianę wykrywa porównanie
 * sześciu cyfr na obu ekranach. Potem ten sam klucz szyfruje sygnały i łącze lokalne.
 */
object KeyboardCrypto {
    class EphemeralKeys(val publicKey: ByteArray, internal val privateKey: ByteArray)

    fun generateKeys(): EphemeralKeys {
        val gen = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val pair = gen.generateKeyPair()
        return EphemeralKeys(pair.public.encoded, pair.private.encoded)
    }

    /** Klucz pary; ta sama wartość po obu stronach niezależnie od tego, kto zaczął. */
    fun pairKey(mine: EphemeralKeys, peerPublic: ByteArray): ByteArray {
        val factory = KeyFactory.getInstance("EC")
        val shared = try {
            KeyAgreement.getInstance("ECDH").run {
                init(factory.generatePrivate(PKCS8EncodedKeySpec(mine.privateKey)))
                doPhase(factory.generatePublic(X509EncodedKeySpec(peerPublic)), true)
                generateSecret()
            }
        } catch (e: java.security.GeneralSecurityException) {
            throw CryptoException("nieprawidłowy klucz publiczny partnera", e)
        }
        return hkdf(shared, salt = sortedConcat(mine.publicKey, peerPublic), info = "chochlik-kbd-pair-v1", len = 32)
    }

    /** Kod do porównania wzrokiem: „123 456”. */
    fun sas(pairKey: ByteArray): String {
        val raw = hkdf(pairKey, salt = ByteArray(0), info = "chochlik-kbd-sas-v1", len = 4)
        val n = ((raw[0].toLong() and 0xFF) shl 24 or ((raw[1].toLong() and 0xFF) shl 16) or
            ((raw[2].toLong() and 0xFF) shl 8) or (raw[3].toLong() and 0xFF)) % 1_000_000
        val digits = n.toString().padStart(6, '0')
        return "${digits.take(3)} ${digits.drop(3)}"
    }

    /** Potwierdzenie „akceptuję” dowodzące znajomości klucza pary. */
    fun confirmMac(pairKey: ByteArray, deviceId: String): String =
        ChochlikCrypto.hex(hmac(pairKey, "chochlik-kbd-ok|$deviceId".toByteArray()))

    /** Nazwa ogłaszana w Nearby: obcy nie odgadnie jej bez klucza pary, a my ją rozpoznamy. */
    fun nearbyName(pairKey: ByteArray): String =
        "kbd-" + ChochlikCrypto.hex(hmac(pairKey, "chochlik-kbd-nearby".toByteArray())).take(16)

    /** Krótki identyfikator pary do zapisu i porównań (nie zdradza klucza). */
    fun pairId(pairKey: ByteArray): String =
        ChochlikCrypto.hex(hmac(pairKey, "chochlik-kbd-id".toByteArray())).take(16)

    fun seal(pairKey: ByteArray, plaintext: ByteArray, aad: String): ByteArray =
        ChochlikCrypto.seal(pairKey, plaintext, aad.toByteArray())

    fun open(pairKey: ByteArray, blob: ByteArray, aad: String): ByteArray =
        ChochlikCrypto.open(pairKey, blob, aad.toByteArray())

    internal fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(if (key.isEmpty()) ByteArray(32) else key, "HmacSHA256"))
            doFinal(data)
        }

    /** HKDF-SHA256 (RFC 5869). */
    internal fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, len: Int): ByteArray {
        val prk = hmac(salt, ikm)
        val out = ByteArray(len)
        var prev = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < len) {
            prev = hmac(prk, prev + info.toByteArray() + byteArrayOf(counter.toByte()))
            val n = minOf(prev.size, len - pos)
            prev.copyInto(out, pos, 0, n)
            pos += n
            counter++
        }
        return out
    }

    private fun sortedConcat(a: ByteArray, b: ByteArray): ByteArray =
        if (compareUnsigned(a, b) <= 0) a + b else b + a

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}
