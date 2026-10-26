package com.mdev.mchochlik.feature.keyboard


import com.mdev.chochlik.core.crypto.CryptoException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardCryptoTest {
    private val a = KeyboardCrypto.generateKeys()
    private val b = KeyboardCrypto.generateKeys()
    private val key = KeyboardCrypto.pairKey(a, b.publicKey)

    @Test fun pairKeyIsSymmetricAndSasMatches() {
        val other = KeyboardCrypto.pairKey(b, a.publicKey)
        assertArrayEquals(key, other)
        assertEquals(KeyboardCrypto.sas(key), KeyboardCrypto.sas(other))
        assertTrue(Regex("""\d{3} \d{3}""").matches(KeyboardCrypto.sas(key)))
        assertEquals(KeyboardCrypto.nearbyName(key), KeyboardCrypto.nearbyName(other))
    }

    @Test fun substitutedKeyGivesDifferentSas() {
        val mitm = KeyboardCrypto.generateKeys()
        val k1 = KeyboardCrypto.pairKey(a, mitm.publicKey)
        val k2 = KeyboardCrypto.pairKey(b, mitm.publicKey)
        assertFalse(k1.contentEquals(k2))
    }

    @Test(expected = CryptoException::class) fun badPublicKeyThrows() {
        KeyboardCrypto.pairKey(a, byteArrayOf(1, 2, 3))
    }

    @Test fun hkdfMatchesRfc5869Case3() {
        val okm = KeyboardCrypto.hkdf(ByteArray(22) { 0x0b }, ByteArray(0), "", 42)
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            okm.joinToString("") { "%02x".format(it) },
        )
    }
}
