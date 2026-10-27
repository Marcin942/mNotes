package com.mdev.mnotes.core.nearby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SyncCryptoTest {
    @Test
    fun bothSidesGetTheSamePairKeyAndDigits() {
        val tablet = SyncCrypto.generateKeys(); val phone = SyncCrypto.generateKeys()
        val k1 = SyncCrypto.pairKey(tablet, phone.publicKey); val k2 = SyncCrypto.pairKey(phone, tablet.publicKey)
        assertArrayEquals(k1, k2)
        assertEquals(32, k1.size)
        assertEquals(SyncCrypto.sas(k1), SyncCrypto.sas(k2))
        assertTrue(Regex("\\d{3} \\d{3}").matches(SyncCrypto.sas(k1)))
        assertEquals(SyncCrypto.nearbyName(k1), SyncCrypto.nearbyName(k2))
        assertTrue(SyncCrypto.nearbyName(k1).startsWith("mn-"))
    }

    @Test
    fun manInTheMiddleChangesTheDigits() {
        val tablet = SyncCrypto.generateKeys(); val phone = SyncCrypto.generateKeys(); val evil = SyncCrypto.generateKeys()
        // podmieniony klucz: tablet liczy z kluczem napastnika, telefon też
        val onTablet = SyncCrypto.pairKey(tablet, evil.publicKey); val onPhone = SyncCrypto.pairKey(phone, evil.publicKey)
        assertNotEquals(SyncCrypto.sas(onTablet), SyncCrypto.sas(onPhone))
    }

    @Test
    fun sealOpenAndTamperDetection() {
        val key = ByteArray(32) { it.toByte() }
        val blob = SyncCrypto.seal(key, "zdjęcie".toByteArray(), "aad".toByteArray())
        assertEquals("zdjęcie", String(SyncCrypto.open(key, blob, "aad".toByteArray())))
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        try { SyncCrypto.open(key, blob, "aad".toByteArray()); fail("uszkodzona ramka przeszła") } catch (_: CryptoException) {}
        val ok = SyncCrypto.seal(key, byteArrayOf(1), "a".toByteArray())
        try { SyncCrypto.open(key, ok, "b".toByteArray()); fail("inne AAD przeszło") } catch (_: CryptoException) {}
    }

    @Test
    fun hkdfMatchesRfc5869Case1() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = SyncCrypto.hkdf(ikm, salt, info, 42)
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", SyncCrypto.hex(okm))
    }

    @Test
    fun badPublicKeyIsReported() {
        try { SyncCrypto.pairKey(SyncCrypto.generateKeys(), ByteArray(10)); fail() } catch (_: CryptoException) {}
    }
}

class LinkChunksTest {
    @Test
    fun splitAndJoinLargePhoto() {
        val data = ByteArray(2_500_000) { (it * 31).toByte() }
        val parts = LinkChunks.split(data)
        assertEquals(84, parts.size)
        assertTrue(parts.all { it.size <= LinkChunks.CHUNK + 1 })
        val j = ChunkJoiner()
        var out: ByteArray? = null
        for (p in parts) out = j.feed(p) ?: out
        assertArrayEquals(data, out)
    }

    @Test
    fun emptyMessageAndLimits() {
        assertArrayEquals(ByteArray(0), ChunkJoiner().feed(LinkChunks.split(ByteArray(0)).single()))
        val small = ChunkJoiner(max = 10)
        try { small.feed(byteArrayOf(1) + ByteArray(11)); fail() } catch (_: IOException) {}
        try { ChunkJoiner().feed(byteArrayOf(7, 1)); fail() } catch (_: IOException) {}
    }
}

class SyncCodecTest {
    private val key = ByteArray(32) { (it * 7).toByte() }

    private fun pair(): Pair<SyncCodec, SyncCodec> {
        val host = SyncCodec(key, SyncRole.HOST); val client = SyncCodec(key, SyncRole.CLIENT)
        host.acceptHello(client.hello()); client.acceptHello(host.hello())
        return host to client
    }

    @Test
    fun messagesTravelBothWays() {
        val (host, client) = pair()
        val photo = client.seal(SyncMsg.Photo("IMG_1.jpg", "image/jpeg", ByteArray(5000) { it.toByte() }))
        val got = host.open(photo) as SyncMsg.Photo
        assertEquals("IMG_1.jpg", got.name)
        assertEquals(5000, got.bytes.size)
        assertEquals(SyncMsg.Note("wstawiono"), client.open(host.seal(SyncMsg.Note("wstawiono"))))
    }

    @Test
    fun replayAndWrongRoleAreRejected() {
        val (host, client) = pair()
        val frame = client.seal(SyncMsg.Note("raz"))
        host.open(frame)
        try { host.open(frame); fail("powtórka przeszła") } catch (_: CryptoException) {}
        val other = SyncCodec(key, SyncRole.HOST)
        try { other.acceptHello(SyncCodec(key, SyncRole.HOST).hello()); fail("hello od tej samej roli przeszło") } catch (_: CryptoException) {}
    }

    @Test
    fun frameFromAnotherSessionIsRejected() {
        val (host, _) = pair()
        val (_, client2) = pair()
        try { host.open(client2.seal(SyncMsg.Note("stara sesja"))); fail() } catch (_: CryptoException) {}
    }

    @Test
    fun wrongKeyFailsHello() {
        val host = SyncCodec(key, SyncRole.HOST)
        try { host.acceptHello(SyncCodec(ByteArray(32), SyncRole.CLIENT).hello()); fail() } catch (_: CryptoException) {}
    }
}

class SyncMsgTest {
    @Test
    fun everyMessageRoundTrips() {
        val list = SyncMsg.PageList("nb1", "Fizyka", listOf(PageMeta("p1", 10L, "aa"), PageMeta("p2", 20L, "bb")))
        assertEquals(list, SyncMsg.decode(SyncMsg.encode(list)))
        val req = SyncMsg.PageRequest("nb1", listOf("p2"))
        assertEquals(req, SyncMsg.decode(SyncMsg.encode(req)))
        val data = SyncMsg.decode(SyncMsg.encode(SyncMsg.PageData("nb1", PageMeta("p2", 20L, "bb"), byteArrayOf(9, 8, 7)))) as SyncMsg.PageData
        assertEquals("p2", data.meta.id)
        assertArrayEquals(byteArrayOf(9, 8, 7), data.bytes)
    }

    @Test
    fun unknownOrBrokenMessageIsNull() {
        assertNull(SyncMsg.decode(byteArrayOf(99, 1, 2)))
        assertNull(SyncMsg.decode(byteArrayOf(1, 0)))
    }
}

class SyncPlanTest {
    @Test
    fun newerWinsMissingIsCopiedAndTiesAreKept() {
        val local = listOf(PageMeta("a", 5, "x"), PageMeta("b", 9, "y1"), PageMeta("c", 3, "z1"), PageMeta("d", 7, "w1"), PageMeta("e", 1, "same"))
        val remote = listOf(PageMeta("b", 4, "y2"), PageMeta("c", 8, "z2"), PageMeta("d", 7, "w2"), PageMeta("e", 2, "same"), PageMeta("f", 1, "v"))
        val plan = SyncPlan.of(local, remote)
        assertEquals(listOf("a", "b"), plan.send)
        assertEquals(listOf("c", "f"), plan.request)
        assertEquals(listOf("d"), plan.conflicts)
        assertTrue(SyncPlan.of(local, local).isEmpty)
    }
}

class PairingTest {
    @Test
    fun offerRoundTripsAsBytesAndCode() {
        val k = SyncCrypto.generateKeys()
        val o = Pairing.Offer(k.publicKey, "Tablet Marcina")
        val back = Pairing.decode(o.encode())!!
        assertArrayEquals(k.publicKey, back.publicKey)
        assertEquals("Tablet Marcina", back.deviceName)
        val fromCode = Pairing.fromCode(o.toCode())!!
        assertArrayEquals(k.publicKey, fromCode.publicKey)
        assertNull(Pairing.decode("śmieci".toByteArray()))
        assertNull(Pairing.fromCode("mnotes-para:1:"))
    }
}
