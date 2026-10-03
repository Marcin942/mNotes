package com.mdev.mnotes.core.geom

/** Minimalna rosnąca tablica floatów (bez pudełkowania), do budowania wielokątów i ścieżek. */
class FloatList(capacity: Int = 64) {
    @JvmField var a = FloatArray(capacity)
    @JvmField var size = 0

    fun clear() { size = 0 }

    fun add(v: Float) {
        if (size == a.size) a = a.copyOf(maxOf(16, size * 2))
        a[size++] = v
    }

    fun add(x: Float, y: Float) { add(x); add(y) }

    fun add(x: Float, y: Float, p: Float) { add(x); add(y); add(p) }

    operator fun get(i: Int): Float = a[i]

    fun toArray(): FloatArray = a.copyOf(size)
}
