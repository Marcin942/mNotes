package com.mdev.mchochlik.feature.keyboard

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkChunksTest {
    @Test fun splitAndJoinRoundTrip() {
        val data = ByteArray(70_001) { it.toByte() }
        val parts = LinkChunks.split(data)
        assertEquals(3, parts.size)
        val j = ChunkJoiner()
        assertNull(j.feed(parts[0]))
        assertNull(j.feed(parts[1]))
        assertArrayEquals(data, j.feed(parts[2]))
        // Po złożeniu bufor jest pusty i gotowy na następną wiadomość.
        assertArrayEquals(byteArrayOf(5), j.feed(LinkChunks.split(byteArrayOf(5)).single()))
    }

    @Test fun emptyMessageIsOneChunk() {
        assertArrayEquals(ByteArray(0), ChunkJoiner().feed(LinkChunks.split(ByteArray(0)).single()))
    }

    @Test(expected = IOException::class) fun oversizedMessageIsRejected() {
        val j = ChunkJoiner()
        val part = byteArrayOf(1) + ByteArray(LinkChunks.CHUNK)
        repeat(LinkChunks.MAX_MESSAGE / LinkChunks.CHUNK + 2) { j.feed(part) }
    }
}
