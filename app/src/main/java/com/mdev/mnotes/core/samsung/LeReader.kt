package com.mdev.mnotes.core.samsung

/** Plik Samsung Notes jest uszkodzony albo ma układ, którego dekoder nie zna. */
class SamsungFormatException(message: String) : Exception(message)

/** Czytnik liczb little-endian z kontrolą granic: każdy odczyt poza buforem to [SamsungFormatException]. */
internal class LeReader(val b: ByteArray, var p: Int = 0) {
    private fun need(n: Int) {
        if (n < 0 || p < 0 || n > b.size - p) throw SamsungFormatException("odczyt poza danymi (pozycja $p, potrzeba $n, jest ${b.size})")
    }

    fun u8(): Int { need(1); return b[p++].toInt() and 0xFF }
    fun u16(): Int { need(2); val v = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8); p += 2; return v }
    fun u32(): Long = i32().toLong() and 0xFFFFFFFFL
    fun i32(): Int {
        need(4)
        val v = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or
            ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)
        p += 4
        return v
    }
    fun i64(): Long {
        val lo = u32()
        val hi = u32()
        return lo or (hi shl 32)
    }
    fun f32(): Float = Float.fromBits(i32())
    fun f64(): Double = Double.fromBits(i64())
    fun skip(n: Int) { need(n); p += n }
    fun raw(n: Int): ByteArray { need(n); val v = b.copyOfRange(p, p + n); p += n; return v }

    /** Napis UTF-16LE z 16-bitową długością (w znakach). */
    fun s16(): String = String(raw(2 * u16()), Charsets.UTF_16LE)

    /** Maska bitowa: bajt liczby bajtów, potem tyle bajtów little-endian (starsze niż 64 bity są pomijane). */
    fun mask(): Long {
        val n = u8()
        need(n)
        var v = 0L
        for (i in 0 until n) if (i < 8) v = v or ((b[p + i].toLong() and 0xFF) shl (8 * i))
        p += n
        return v
    }
}
