package com.mdev.mnotes.core.cache

/**
 * Łączy prośby o ten sam klucz: praca rusza raz, a wynik dostają wszyscy czekający. Dawniej druga prośba o miniaturę
 * w trakcie renderu była odrzucana bez odpowiedzi, więc nowo złożony kafel listy stron zostawał pusty (B2).
 * Nie jest bezpieczny wątkowo — używany z wątku UI.
 */
class Coalescer<K, V> {
    private val waiting = HashMap<K, MutableList<(V) -> Unit>>()

    /** Dopisuje [done] do czekających na [key]; true = to pierwsza prośba i wołający ma uruchomić pracę. */
    fun join(key: K, done: (V) -> Unit): Boolean {
        val list = waiting[key]
        if (list != null) { list.add(done); return false }
        waiting[key] = mutableListOf(done)
        return true
    }

    /** Oddaje [value] wszystkim czekającym na [key]. */
    fun finish(key: K, value: V) {
        val list = waiting.remove(key) ?: return
        for (d in list) d(value)
    }

    fun isPending(key: K): Boolean = key in waiting
}
