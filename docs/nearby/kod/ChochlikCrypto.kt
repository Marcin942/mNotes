package com.mdev.chochlik.core.crypto

import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Szyfrowanie kopert AES-256-GCM (PROTOCOL §3.1 i §3.2).
 *
 * Lustro `server/chochlik/crypto.py`; zgodności pilnują wspólne wektory
 * z `protocol/testvectors/`.
 */
object ChochlikCrypto {
    const val KEY_LEN = 32
    const val NONCE_LEN = 12
    const val TAG_LEN = 16

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = TAG_LEN * 8
    private const val HEX = "0123456789abcdef"
    private val random = SecureRandom()

    fun randomNonce(): ByteArray = ByteArray(NONCE_LEN).also { random.nextBytes(it) }

    fun randomKey(): ByteArray = ByteArray(KEY_LEN).also { random.nextBytes(it) }

    /** AAD koperty; odtwarzany z pól sparsowanej koperty, nigdy z surowego tekstu. */
    fun aad(dir: String, dev: String, ep: Long, seq: Long, kind: String): ByteArray =
        "chochlik/1|$dir|$dev|$ep|$seq|$kind".toByteArray(Charsets.US_ASCII)

    /** Zwraca `nonce ‖ ct ‖ tag`. */
    fun seal(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        nonce: ByteArray = randomNonce(),
    ): ByteArray {
        requireKey(key)
        if (nonce.size != NONCE_LEN) {
            throw CryptoException("nonce ma ${nonce.size} bajtów zamiast $NONCE_LEN")
        }
        val body = try {
            gcm(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext)
        } catch (e: GeneralSecurityException) {
            throw CryptoException("nie udało się zaszyfrować koperty", e)
        }
        return nonce + body
    }

    /** Odwrotność [seal]; błąd uwierzytelnienia zgłasza [CryptoException]. */
    fun open(key: ByteArray, blob: ByteArray, aad: ByteArray): ByteArray {
        requireKey(key)
        if (blob.size < NONCE_LEN + TAG_LEN) {
            throw CryptoException("szyfrogram krótszy niż nonce + tag")
        }
        val nonce = blob.copyOfRange(0, NONCE_LEN)
        return try {
            gcm(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(blob, NONCE_LEN, blob.size - NONCE_LEN)
        } catch (e: AEADBadTagException) {
            throw CryptoException("nie udało się uwierzytelnić koperty", e)
        } catch (e: GeneralSecurityException) {
            throw CryptoException("nie udało się odszyfrować koperty", e)
        }
    }

    /** Odcisk klucza do porównania wzrokiem przy parowaniu: `xxxx-xxxx`. */
    fun fingerprint(key: ByteArray): String {
        val head = hex(MessageDigest.getInstance("SHA-256").digest(key)).take(8)
        return "${head.take(4)}-${head.drop(4)}"
    }

    internal fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad)
        }

    internal fun requireKey(key: ByteArray) {
        if (key.size != KEY_LEN) {
            throw CryptoException("klucz ma ${key.size} bajtów zamiast $KEY_LEN")
        }
    }

    fun hex(raw: ByteArray): String {
        val sb = StringBuilder(raw.size * 2)
        for (b in raw) {
            sb.append(HEX[(b.toInt() shr 4) and 0xF])
            sb.append(HEX[b.toInt() and 0xF])
        }
        return sb.toString()
    }
}
