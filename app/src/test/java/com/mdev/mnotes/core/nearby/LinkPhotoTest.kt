package com.mdev.mnotes.core.nearby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Zdjęcie w częściach z potwierdzeniami (2b-B1, `docs/NEARBY-PRZEBUDOWA.md` §3.4). */
class LinkPhotoTest {
    private val photo = ByteArray(700_000).also { Random(7).nextBytes(it) }

    private fun sender(bytes: ByteArray = photo) = PhotoSender("z1", "tablica.jpg", "image/jpeg", bytes)

    /** Przepuszcza części przez kodek (jak na łączu) i potwierdzenia z powrotem; [drop] gubi wybrane części raz. */
    private fun deliver(s: PhotoSender, r: PhotoReceiver, now: Long, drop: MutableSet<Int> = mutableSetOf()): SyncMsg.Photo? {
        var got: SyncMsg.Photo? = null
        for (p in s.due(now)) {
            if (drop.remove(p.index)) continue
            val res = r.accept(SyncMsg.decode(SyncMsg.encode(p)) as SyncMsg.PhotoPart)
            for (m in res.reply) if (m is SyncMsg.PhotoAck) s.ack(m.index)
            res.photo?.let { got = it }
        }
        return got
    }

    @Test
    fun photoGoesInPartsWithProgressAndArrivesWhole() {
        val s = sender(); val r = PhotoReceiver()
        assertEquals(3, s.count)  // 700 000 B po 256 KB
        var t = 0L; var got: SyncMsg.Photo? = null
        while (!s.done) { got = deliver(s, r, t) ?: got; t += 100 }
        assertNotNull(got)
        assertArrayEquals(photo, got!!.bytes)
        assertEquals("tablica.jpg", got.name)
        assertEquals(1f, s.progress, 1e-6f)
        assertEquals(3, s.confirmed)
    }

    @Test
    fun atMostTwoPartsInFlight() {
        val s = sender()
        assertEquals(listOf(0, 1), s.due(0).map { it.index })
        assertTrue(s.due(10).isEmpty())
        s.ack(0)
        assertEquals(listOf(2), s.due(20).map { it.index })
    }

    @Test
    fun lostPartIsResentAfterTheTimeout() {
        val s = sender(); val r = PhotoReceiver()
        assertNull(deliver(s, r, 0, mutableSetOf(1)))
        assertFalse(s.done)
        // przed limitem nic nie wraca, po limicie część 1 jeszcze raz
        assertTrue(s.due(PhotoSender.TIMEOUT_MS - 1).map { it.index }.none { it == 1 })
        var got: SyncMsg.Photo? = null; var t = PhotoSender.TIMEOUT_MS
        while (!s.done && t < 10 * PhotoSender.TIMEOUT_MS) { got = deliver(s, r, t) ?: got; t += PhotoSender.TIMEOUT_MS }
        assertArrayEquals(photo, got!!.bytes)
    }

    @Test
    fun givesUpAfterMaxTries() {
        val s = sender(ByteArray(10))
        var t = 0L
        repeat(PhotoSender.MAX_TRIES) { assertEquals(1, s.due(t).size); t += s.timeout() }
        assertTrue(s.due(t).isEmpty())
        assertTrue(s.failed)
    }

    @Test
    fun partTimeoutFollowsTheMeasuredLinkSpeed() {
        val s = sender()
        assertEquals("bez pomiaru: hojnie", PhotoSender.FIRST_TIMEOUT_MS, s.timeout())
        s.due(0); s.ack(0, 200)
        assertEquals("szybkie łącze: dolna granica", PhotoSender.TIMEOUT_MS, s.timeout())
        val slow = sender()
        slow.due(0); slow.ack(0, 50_000)
        assertEquals("bardzo wolne: górna granica", PhotoSender.MAX_TIMEOUT_MS, slow.timeout())
    }

    /** 3b-B3: łącze, na którym część idzie ~25 s (Bluetooth bez Wi-Fi, BLE): żadnych ponowień, zdjęcie dochodzi. */
    @Test
    fun slowLinkIsNotFloodedWithResends() {
        val s = sender(); val r = PhotoReceiver()
        val arrive = HashMap<Int, Long>()  // część → chwila, w której dotrze (kolejno, 25 s każda)
        var busyUntil = 0L
        var got: SyncMsg.Photo? = null
        var t = 0L
        var sends = 0
        while (!s.done && t < 600_000) {
            for (p in s.due(t)) { sends++; busyUntil = maxOf(busyUntil, t) + 25_000; arrive[p.index] = busyUntil }
            for ((i, at) in arrive.toMap()) if (at <= t) {
                arrive.remove(i)
                val res = r.accept(SyncMsg.decode(SyncMsg.encode(s.part(i))) as SyncMsg.PhotoPart)
                for (m in res.reply) if (m is SyncMsg.PhotoAck) s.ack(m.index, t)
                res.photo?.let { got = it }
            }
            t += 500
        }
        assertArrayEquals(photo, got!!.bytes)
        assertEquals("każda część wysłana raz", 3, sends)
        assertFalse(s.failed)
    }

    @Test
    fun afterADroppedLinkSendingResumesFromTheFirstUnconfirmedPart() {
        val s = sender(); val r = PhotoReceiver()
        deliver(s, r, 0, mutableSetOf(1))  // 0 potwierdzona, 1 zgubiona w zerwaniu
        s.reset()
        // od razu po ponownym połączeniu (bez czekania na limit): 1 i 2, nie 0
        assertEquals(listOf(1, 2), s.due(1).map { it.index })
    }

    @Test
    fun repeatedPartsAreAckedButThePhotoComesOutOnce() {
        val s = sender(ByteArray(1000) { it.toByte() }); val r = PhotoReceiver()
        val part = s.due(0).single()
        val first = r.accept(part)
        assertNotNull(first.photo)
        assertTrue(first.reply.any { it is SyncMsg.PhotoDone && it.ok })
        val again = r.accept(part)
        assertNull(again.photo)
        assertTrue(again.reply.any { it is SyncMsg.PhotoAck } && again.reply.any { it is SyncMsg.PhotoDone && it.ok })
    }

    @Test
    fun wrongChecksumIsRejected() {
        val s = sender(ByteArray(1000) { 1 })
        val p = s.due(0).single()
        val forged = SyncMsg.PhotoPart(p.id, p.index, p.count, p.sha, p.name, p.mime, ByteArray(1000) { 2 })
        val res = PhotoReceiver().accept(forged)
        assertNull(res.photo)
        assertTrue(res.bad)
        assertTrue(res.reply.any { it is SyncMsg.PhotoDone && !it.ok })
    }

    @Test
    fun tooBigPhotoIsRefused() {
        val s = PhotoSender("duze", "a.jpg", "image/jpeg", ByteArray(3000), partSize = 1000)
        val r = PhotoReceiver(maxBytes = 2500)
        var bad = false
        for (p in s.due(0) + s.also { it.ack(0); it.ack(1) }.due(1)) bad = bad || r.accept(p).bad
        assertTrue(bad)
    }

    @Test
    fun absurdPartCountIsRefusedWithoutAllocating() {
        // dawniej arrayOfNulls(count) z ramki: Int.MAX_VALUE kończył się OutOfMemoryError
        val p = SyncMsg.PhotoPart("x", 0, Int.MAX_VALUE, ByteArray(32), "a.jpg", "image/jpeg", ByteArray(10))
        val res = PhotoReceiver().accept(p)
        assertTrue(res.bad)
        assertNull(res.photo)
    }

    @Test
    fun atMostFourPhotosAreAssembledAtOnce() {
        val r = PhotoReceiver()
        val senders = (1..5).map { PhotoSender("z$it", "a.jpg", "image/jpeg", ByteArray(2000) { b -> (b * it).toByte() }, partSize = 1000) }
        for (s in senders) assertNull(r.accept(s.due(0).first()).photo)  // pierwsza część każdego
        // najstarsze składanie (z1) wypadło: jego druga część go nie domyka; najnowsze (z5) się domyka
        assertNull(r.accept(senders[0].part(1)).photo)
        assertNotNull(r.accept(senders[4].part(1)).photo)
    }

    @Test
    fun partsInReverseOrderLandInTheRightPlaceOfTheFile() {
        val dir = java.nio.file.Files.createTempDirectory("mnotes-czesci").toFile()
        try {
            val bytes = ByteArray(2500) { (it * 7).toByte() }
            val s = PhotoSender("w", "Z.mnotes", "application/x-mnotes", bytes, partSize = 1000)
            val r = PhotoReceiver(files = LinkFiles.disk(dir))
            assertNull(r.accept(s.part(2)).file)  // ostatnia (500 B) pierwsza: czeka, aż wiadomo, gdzie ją zapisać
            assertNull(r.accept(s.part(1)).file)
            val done = r.accept(s.part(0)).file!!
            assertTrue(done.file is LinkFile.Disk)
            assertArrayEquals(bytes, done.bytes())
            done.file.delete()
            assertEquals(0, dir.list()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun partOfAnotherLengthIsRefused() {
        val s = PhotoSender("w", "a.jpg", "image/jpeg", ByteArray(3000), partSize = 1000)
        val r = PhotoReceiver()
        assertFalse(r.accept(s.part(0)).bad)
        val odd = s.part(1).run { SyncMsg.PhotoPart(id, index, count, sha, name, mime, ByteArray(700)) }
        val res = r.accept(odd)
        assertTrue(res.bad)
        assertTrue(res.reply.any { it is SyncMsg.PhotoDone && !it.ok })
    }

    @Test
    fun lengthBeyondTheMessageAllocatesNothing() {
        // audyt (docs/BEZPIECZENSTWO.md): 6 bajtów deklarujących 24 MB nie przydzielają 24 MB
        val msg = byteArrayOf(1, 0, 0, 0, 0, 0x01, 0x70, 0, 0)
        val mx = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val t = Thread.currentThread().id
        val before = mx.getThreadAllocatedBytes(t)
        assertNull(SyncMsg.decode(msg))
        assertTrue(mx.getThreadAllocatedBytes(t) - before < 1_000_000)
    }

    @Test
    fun hugeFirstPartCannotPushTheLastOneGigabytesAway() {
        // audyt: część 0 duża (step), ostatnia nr count-1 pod index·step — rzadki plik wielu GB na dysku tabletu
        var far = 0L
        val files = LinkFiles { object : LinkFile {
            override val length = 0L
            override fun read(at: Long, size: Int) = ByteArray(0)
            override fun write(at: Long, bytes: ByteArray) { far = maxOf(far, at + bytes.size) }
            override fun input() = java.io.ByteArrayInputStream(ByteArray(0))
        } }
        val r = PhotoReceiver(files = files)
        val count = LinkChunks.MAX_MESSAGE / PhotoSender.PART + 1
        fun part(i: Int, n: Int) = SyncMsg.PhotoPart("w", i, count, ByteArray(32), "a.jpg", "image/jpeg", ByteArray(n))
        r.accept(part(count - 1, 10))
        r.accept(part(0, 8 shl 20))
        assertTrue("zapis do $far", far <= LinkChunks.MAX_MESSAGE)
    }

    @Test
    fun newMessagesRoundTrip() {
        val ack = SyncMsg.decode(SyncMsg.encode(SyncMsg.PhotoAck("x", 4)))
        assertEquals(SyncMsg.PhotoAck("x", 4), ack)
        assertEquals(SyncMsg.PhotoDone("x", false), SyncMsg.decode(SyncMsg.encode(SyncMsg.PhotoDone("x", false))))
        val p = sender().part(2)
        val back = SyncMsg.decode(SyncMsg.encode(p)) as SyncMsg.PhotoPart
        assertEquals(2, back.index); assertEquals(3, back.count)
        assertArrayEquals(p.sha, back.sha); assertArrayEquals(p.bytes, back.bytes)
    }
}

class LinkErrorsTest {
    @Test
    fun everyKnownCodeHasAnActionableSentence() {
        val codes = listOf(13, 8004, 8007, 8008, 8012, 8025, 8029, 8030, 8031, 8032, 8033, 8034, 8036, 8037, 8038, 8039, 8050)
        for (c in codes) {
            val e = LinkErrors.explain(c)
            assertTrue("$c: ${e.pl}", e.pl.length in 30..200 && e.en.isNotBlank())
        }
        // 8025 to wyłączona usługa lokalizacji, nie Usługi Google (główny błąd starego łącza)
        assertTrue(LinkErrors.explain(LinkErrors.LOCATION_OFF).pl.contains("lokalizację"))
        assertTrue(LinkErrors.isSetup(LinkErrors.LOCATION_OFF) && LinkErrors.isSetup(LinkErrors.NO_FINE_LOCATION))
        assertFalse(LinkErrors.isSetup(LinkErrors.REJECTED))
        assertTrue(LinkErrors.ALREADY_DISCOVERING in LinkErrors.HARMLESS)
    }
}
