package com.mdev.mnotes.core.codec

import java.io.InputStream
import java.io.OutputStream

object Streams {
    /** Kopiuje [input] do [out]; więcej niż [max] bajtów to [CodecException] (strumień bez końca od innej aplikacji). */
    fun copyAtMost(input: InputStream, out: OutputStream, max: Long): Long {
        val buf = ByteArray(64 * 1024)
        var n = 0L
        while (true) {
            val r = input.read(buf)
            if (r < 0) return n
            n += r
            if (n > max) throw CodecException("plik większy niż $max B")
            out.write(buf, 0, r)
        }
    }
}
