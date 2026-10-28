package com.mdev.mnotes.core.nearby

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Plik w łączu bez trzymania całości w pamięci (2c-B3): nadawca czyta część spod przesunięcia, odbiorca zapisuje ją pod
 * przesunięciem, a sumę kontrolną liczy się, czytając po kawałku. Zdjęcia z galerii mogą być w pamięci ([Memory]),
 * zeszyty do [LinkMachine.FILE_MAX] idą przez plik w cache ([Disk]); wcześniej zeszyt 64 MB zajmował u odbiorcy 128 MB.
 */
interface LinkFile {
    val length: Long
    fun read(at: Long, size: Int): ByteArray
    fun write(at: Long, bytes: ByteArray)
    fun input(): InputStream
    /** Po wstawieniu albo odrzuceniu; w pamięci nic nie robi. */
    fun delete() {}

    /** Bajty w pamięci; zapis za końcem powiększa bufor (części mogą przyjść nie po kolei). */
    class Memory(bytes: ByteArray = ByteArray(0)) : LinkFile {
        private var data = bytes
        override var length = bytes.size.toLong()
            private set

        override fun read(at: Long, size: Int): ByteArray = data.copyOfRange(at.toInt(), (at + size).toInt())

        override fun write(at: Long, bytes: ByteArray) {
            val end = at + bytes.size
            require(end <= Int.MAX_VALUE) { "plik za duży na pamięć" }
            if (end > data.size) data = data.copyOf(maxOf(end, minOf(2L * data.size, Int.MAX_VALUE.toLong())).toInt())
            bytes.copyInto(data, at.toInt())
            length = maxOf(length, end)
        }

        override fun input(): InputStream = data.inputStream(0, length.toInt())

        fun bytes(): ByteArray = if (data.size.toLong() == length) data else data.copyOf(length.toInt())
    }

    /** Plik na dysku; każdy odczyt i zapis otwiera go na chwilę, więc po zerwaniu łącza nic nie zostaje otwarte. */
    class Disk(val file: File) : LinkFile {
        override val length: Long get() = file.length()

        override fun read(at: Long, size: Int): ByteArray =
            RandomAccessFile(file, "r").use { f -> ByteArray(size).also { f.seek(at); f.readFully(it) } }

        override fun write(at: Long, bytes: ByteArray) { RandomAccessFile(file, "rw").use { f -> f.seek(at); f.write(bytes) } }

        override fun input(): InputStream = file.inputStream()

        override fun delete() { file.delete() }
    }
}

/** SHA-256 czytane strumieniem z buforem [HASH_BUFFER] (cały plik nie trafia do pamięci). */
fun LinkFile.sha256(): ByteArray {
    val d = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(HASH_BUFFER)
    input().use { s -> while (true) { val n = s.read(buf); if (n < 0) break; d.update(buf, 0, n) } }
    return d.digest()
}

const val HASH_BUFFER = 64 * 1024

/** Skąd odbiorca bierze miejsce na składany plik: pamięć (testy, domyślnie) albo katalog w cache aplikacji. */
fun interface LinkFiles {
    fun create(): LinkFile

    companion object {
        val MEMORY = LinkFiles { LinkFile.Memory() }

        /** Pliki w [dir]; resztki starsze niż godzina (przerwany odbiór, zamknięta aplikacja) są przy tym usuwane. */
        fun disk(dir: File, now: Long = System.currentTimeMillis()): LinkFiles {
            dir.mkdirs()
            dir.listFiles()?.filter { it.isFile && now - it.lastModified() > STALE_MS }?.forEach { it.delete() }
            return LinkFiles { LinkFile.Disk(File.createTempFile("odbior-", ".part", dir)) }
        }

        const val STALE_MS = 3_600_000L
    }
}

/** Złożony i sprawdzony (SHA-256) plik z łącza. Kto go dostał ([LinkMachine.Listener.file]), ten go usuwa po użyciu. */
class LinkReceived(val name: String, val mime: String, val file: LinkFile) {
    /** Cały plik w pamięci: dla zdjęć do wstawienia (i tak dekodowane w całości), nie dla zeszytów. */
    fun bytes(): ByteArray = (file as? LinkFile.Memory)?.bytes() ?: file.input().use { it.readBytes() }

    fun toPhoto() = SyncMsg.Photo(name, mime, bytes())
}
