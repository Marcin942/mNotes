package com.mdev.mchochlik.feature.keyboard

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Nearby przyjmuje ładunek BYTES do 32 KB, a wycinek pola bywa większy. Każdy kawałek ma
 * bajt flagi: 1 — ciąg dalszy, 0 — ostatni. Nearby zachowuje kolejność ładunków.
 */
object LinkChunks {
    const val CHUNK = 30_000
    const val MAX_MESSAGE = 1 shl 20

    fun split(data: ByteArray, chunk: Int = CHUNK): List<ByteArray> {
        if (data.isEmpty()) return listOf(byteArrayOf(0))
        return (data.indices step chunk).map { from ->
            val to = minOf(from + chunk, data.size)
            byteArrayOf(if (to < data.size) 1 else 0) + data.copyOfRange(from, to)
        }
    }
}

/** Skleja kawałki z [LinkChunks.split]; zbyt długą wiadomość odrzuca (wyjątek zrywa łącze). */
class ChunkJoiner {
    private val buf = ByteArrayOutputStream()

    fun feed(part: ByteArray): ByteArray? {
        if (part.isEmpty()) throw IOException("pusty kawałek")
        buf.write(part, 1, part.size - 1)
        if (buf.size() > LinkChunks.MAX_MESSAGE) {
            buf.reset()
            throw IOException("wiadomość ponad ${LinkChunks.MAX_MESSAGE} B")
        }
        return when (part[0].toInt()) {
            0 -> buf.toByteArray().also { buf.reset() }
            1 -> null
            else -> throw IOException("zła flaga kawałka")
        }
    }
}
