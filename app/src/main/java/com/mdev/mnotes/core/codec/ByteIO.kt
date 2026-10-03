package com.mdev.mnotes.core.codec

/** Rosnący bufor bajtów z kodowaniem zmiennej długości; zero zależności od Androida. */
class ByteWriter(initial: Int = 256) {
    var buf = ByteArray(maxOf(initial, 16))
        private set
    var size = 0
        private set

    private fun ensure(extra: Int) {
        val need = size + extra
        if (need > buf.size) {
            var n = buf.size * 2
            while (n < need) n *= 2
            buf = buf.copyOf(n)
        }
    }

    fun reset() { size = 0 }

    fun u8(v: Int) {
        ensure(1)
        buf[size++] = v.toByte()
    }

    fun i32(v: Int) {
        ensure(4)
        buf[size++] = (v ushr 24).toByte()
        buf[size++] = (v ushr 16).toByte()
        buf[size++] = (v ushr 8).toByte()
        buf[size++] = v.toByte()
    }

    fun f32(v: Float) = i32(java.lang.Float.floatToRawIntBits(v))

    /** Liczba bez znaku, 7 bitów na bajt. */
    fun varint(v: Int) {
        ensure(5)
        var x = v
        while (x and 0x7F.inv() != 0) {
            buf[size++] = ((x and 0x7F) or 0x80).toByte()
            x = x ushr 7
        }
        buf[size++] = x.toByte()
    }

    fun zigzag(v: Int) = varint((v shl 1) xor (v shr 31))

    fun string(s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        varint(b.size)
        bytes(b, 0, b.size)
    }

    fun bytes(src: ByteArray, off: Int, len: Int) {
        ensure(len)
        System.arraycopy(src, off, buf, size, len)
        size += len
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

class CodecException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ByteReader(private val buf: ByteArray, var pos: Int = 0, val end: Int = buf.size) {

    val remaining: Int get() = end - pos

    private fun need(n: Int) {
        if (pos + n > end) throw CodecException("Nieoczekiwany koniec danych")
    }

    fun u8(): Int {
        need(1)
        return buf[pos++].toInt() and 0xFF
    }

    fun i32(): Int {
        need(4)
        val v = ((buf[pos].toInt() and 0xFF) shl 24) or ((buf[pos + 1].toInt() and 0xFF) shl 16) or
            ((buf[pos + 2].toInt() and 0xFF) shl 8) or (buf[pos + 3].toInt() and 0xFF)
        pos += 4
        return v
    }

    fun f32(): Float = java.lang.Float.intBitsToFloat(i32())

    fun varint(): Int {
        var shift = 0
        var result = 0
        while (true) {
            need(1)
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 35) throw CodecException("Zbyt długi varint")
        }
    }

    fun zigzag(): Int {
        val v = varint()
        return (v ushr 1) xor -(v and 1)
    }

    fun string(): String {
        val n = varint()
        if (n < 0) throw CodecException("Ujemna długość napisu")
        need(n)
        val s = String(buf, pos, n, Charsets.UTF_8)
        pos += n
        return s
    }

    fun skip(n: Int) {
        need(n)
        pos += n
    }
}
