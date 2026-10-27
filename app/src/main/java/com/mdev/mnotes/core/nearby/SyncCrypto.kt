package com.mdev.mnotes.core.nearby

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Kryptografia łącza telefon ↔ tablet bez serwera (wzór: klawiatura z mChochlika, `docs/nearby/`). Klucz pary
 * powstaje z jednorazowego ECDH P-256. Podmianę kluczy w trakcie parowania wykrywa porównanie sześciu cyfr na obu
 * ekranach, a zobowiązanie przed ujawnieniem klucza ([PairingSession], commit-then-reveal) sprawia, że napastnik w
 * środku nie może dobrać swojego klucza pod te cyfry: każda próba to szansa 1 na 10⁶, a nie 10⁶ obliczeń w tle.
 * Każda ramka jest szyfrowana AES-256-GCM (Nearby samo nie uwierzytelnia). Ciągi „info” są własne mNotes.
 *
 * Świadomie bez tajności w przód: klucz pary jest stały (zapisany zaszyfrowany kluczem z Keystore), więc kto go
 * wykradnie z urządzenia, odczyta też nagrane wcześniej ramki. Dla zdjęć z wykładu między własnym telefonem i tabletem
 * to rozsądna cena za brak kolejnej wymiany kluczy przy każdym połączeniu.
 */
object SyncCrypto {
    const val KEY_LEN = 32
    const val NONCE_LEN = 12
    const val TAG_LEN = 16
    private const val HEX = "0123456789abcdef"
    private val random = SecureRandom()

    class EphemeralKeys(val publicKey: ByteArray, internal val privateKey: ByteArray)

    fun randomNonce(): ByteArray = ByteArray(NONCE_LEN).also { random.nextBytes(it) }

    fun generateKeys(): EphemeralKeys {
        val gen = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val pair = gen.generateKeyPair()
        return EphemeralKeys(pair.public.encoded, pair.private.encoded)
    }

    /** Klucz pary: ta sama wartość po obu stronach niezależnie od tego, kto zaczął. */
    fun pairKey(mine: EphemeralKeys, peerPublic: ByteArray): ByteArray {
        val factory = KeyFactory.getInstance("EC")
        val shared = try {
            KeyAgreement.getInstance("ECDH").run {
                init(factory.generatePrivate(PKCS8EncodedKeySpec(mine.privateKey)))
                doPhase(factory.generatePublic(X509EncodedKeySpec(peerPublic)), true)
                generateSecret()
            }
        } catch (e: GeneralSecurityException) {
            throw CryptoException("nieprawidłowy klucz publiczny drugiego urządzenia", e)
        }
        return hkdf(shared, salt = sortedConcat(mine.publicKey, peerPublic), info = "mnotes-sync-pair-v1", len = KEY_LEN)
    }

    /**
     * Kod do porównania wzrokiem na obu ekranach: „123 456”. Zależy od klucza pary (więc od obu kluczy publicznych) i od
     * obu nonce ujawnionych dopiero po zobowiązaniach; kolejność nonce bez znaczenia.
     */
    fun sas(pairKey: ByteArray, nonceA: ByteArray, nonceB: ByteArray): String {
        val raw = hkdf(pairKey, salt = sortedConcat(nonceA, nonceB), info = "mnotes-sync-sas-v2", len = 4)
        val n = ((raw[0].toLong() and 0xFF) shl 24 or ((raw[1].toLong() and 0xFF) shl 16) or
            ((raw[2].toLong() and 0xFF) shl 8) or (raw[3].toLong() and 0xFF)) % 1_000_000
        val digits = n.toString().padStart(6, '0')
        return "${digits.take(3)} ${digits.drop(3)}"
    }

    /** Nazwa ogłaszana w Nearby po sparowaniu: obcy jej nie odgadnie, my ją rozpoznamy. */
    fun nearbyName(pairKey: ByteArray): String = "mn-" + hex(hmac(pairKey, "mnotes-sync-nearby".toByteArray())).take(16)

    /** Krótki identyfikator pary do zapisu i porównań (nie zdradza klucza). */
    fun pairId(pairKey: ByteArray): String = hex(hmac(pairKey, "mnotes-sync-id".toByteArray())).take(16)

    /** AES-256-GCM; wynik `nonce ‖ szyfrogram ‖ tag`. */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray, nonce: ByteArray = randomNonce()): ByteArray {
        requireKey(key)
        if (nonce.size != NONCE_LEN) throw CryptoException("nonce ma ${nonce.size} bajtów zamiast $NONCE_LEN")
        val body = try { gcm(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext) }
        catch (e: GeneralSecurityException) { throw CryptoException("nie udało się zaszyfrować", e) }
        return nonce + body
    }

    /** Odwrotność [seal]; zły klucz, AAD albo uszkodzenie daje [CryptoException]. */
    fun open(key: ByteArray, blob: ByteArray, aad: ByteArray): ByteArray {
        requireKey(key)
        if (blob.size < NONCE_LEN + TAG_LEN) throw CryptoException("szyfrogram krótszy niż nonce + tag")
        val nonce = blob.copyOfRange(0, NONCE_LEN)
        return try { gcm(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(blob, NONCE_LEN, blob.size - NONCE_LEN) }
        catch (e: GeneralSecurityException) { throw CryptoException("ramka nie przeszła uwierzytelnienia", e) }
    }

    fun hex(b: ByteArray): String = buildString(b.size * 2) { for (x in b) { append(HEX[(x.toInt() shr 4) and 15]); append(HEX[x.toInt() and 15]) } }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(if (key.isEmpty()) ByteArray(32) else key, "HmacSHA256"))
        doFinal(data)
    }

    /** HKDF-SHA256 (RFC 5869) z tekstowym [info] (UTF-8). */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, len: Int): ByteArray = hkdf(ikm, salt, info.toByteArray(), len)

    /** HKDF-SHA256 (RFC 5869). */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, len: Int): ByteArray {
        val prk = hmac(salt, ikm)
        val out = ByteArray(len)
        var prev = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < len) {
            prev = hmac(prk, prev + info + byteArrayOf(counter.toByte()))
            val n = minOf(prev.size, len - pos)
            prev.copyInto(out, pos, 0, n)
            pos += n
            counter++
        }
        return out
    }

    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LEN * 8, nonce))
            updateAAD(aad)
        }

    private fun requireKey(key: ByteArray) { if (key.size != KEY_LEN) throw CryptoException("klucz ma ${key.size} bajtów zamiast $KEY_LEN") }

    /** Porównanie w stałym czasie (zobowiązania, znaczniki). */
    fun same(a: ByteArray, b: ByteArray): Boolean = java.security.MessageDigest.isEqual(a, b)

    fun sha256(vararg parts: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").run { parts.forEach(::update); digest() }

    private fun sortedConcat(a: ByteArray, b: ByteArray): ByteArray = if (compareUnsigned(a, b) <= 0) a + b else b + a

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}
