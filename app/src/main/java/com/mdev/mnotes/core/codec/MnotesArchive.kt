package com.mdev.mnotes.core.codec

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Rozpakowanie archiwum `.mnotes` (ZIP: `meta.json`, `p/<strona>.mnp`, `assets/<plik>`) do katalogu zeszytu.
 *
 * Dlaczego osobno i tak ostrożnie (B5, „import .mnotes wczytuje tylko pierwszą stronę”): archiwum z innego urządzenia
 * bywa przepakowane — z folderem na wierzchu (`Zeszyt/meta.json`), ze ścieżkami z `\`, z wpisami STORED z deskryptorem
 * danych (strumieniowy [ZipInputStream] ich nie czyta i import przerywał się w połowie). Dlatego archiwum trafia najpierw
 * do pliku tymczasowego, a wpisy czyta [ZipFile] z katalogu centralnego; strumień jest tylko zapasem.
 */
object MnotesArchive {

    /** Wynik: tekst `meta.json` i identyfikatory stron, których pliki były w archiwum. */
    class Result(val metaJson: String, val pageIds: Set<String>)

    /** Ujednolica nazwę wpisu: `\` → `/`, bez wiodących `./` i `/`. */
    fun normalize(name: String): String {
        var n = name.replace('\\', '/')
        while (n.startsWith("./") || n.startsWith("/")) n = n.removePrefix("./").removePrefix("/")
        return n
    }

    /** Folder nad `meta.json` (pusty, gdy leży w korzeniu); null, gdy archiwum nie ma `meta.json`. */
    fun rootOf(names: Collection<String>): String? {
        val metas = names.map { normalize(it) }.filter { it == "meta.json" || it.endsWith("/meta.json") }
        val best = metas.minByOrNull { it.length } ?: return null
        return best.removeSuffix("meta.json")
    }

    /** Ścieżka wewnątrz zeszytu dla wpisu [name] (względem [root]) albo null, gdy wpis jest obcy lub niebezpieczny. */
    fun target(name: String, root: String): String? {
        val n = normalize(name)
        if (!n.startsWith(root)) return null
        val rel = n.removePrefix(root)
        if (rel.split('/').any { it == ".." }) return null
        if (rel == "meta.json") return rel
        if ((rel.startsWith("p/") || rel.startsWith("assets/")) && rel.length > 2 && !rel.endsWith("/")) return rel
        return null
    }

    /**
     * Rozpakowuje [input] do [dst]; null, gdy to nie jest `.mnotes` albo przekracza [maxBytes]. Archiwum leży na czas
     * rozpakowania w [tmpDir] (A2-11: dawniej w samym [dst] — po awarii w połowie zostawało w katalogu zeszytu).
     */
    fun extract(input: InputStream, dst: File, maxBytes: Long, tmpDir: File? = null): Result? {
        dst.mkdirs()
        val tmp = if (tmpDir != null) { tmpDir.mkdirs(); File.createTempFile("import", ".zip", tmpDir) } else File(dst, ".import.zip")
        try {
            var total = 0L
            FileOutputStream(tmp).use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val r = input.read(buf); if (r < 0) break
                    total += r
                    if (total > maxBytes) return null
                    o.write(buf, 0, r)
                }
            }
            return runCatching { fromZipFile(tmp, dst, maxBytes) }.getOrNull() ?: runCatching { fromStream(tmp, dst, maxBytes) }.getOrNull()
        } finally {
            tmp.delete()
        }
    }

    private fun fromZipFile(zip: File, dst: File, maxBytes: Long): Result? = ZipFile(zip).use { z ->
        val entries = z.entries().toList().filter { !it.isDirectory }
        val root = rootOf(entries.map { it.name }) ?: return null
        var total = 0L
        for (e in entries) {
            val rel = target(e.name, root) ?: continue
            total += z.getInputStream(e).use { write(it, dst, rel, maxBytes - total) ?: return null }
        }
        finish(dst)
    }

    private fun fromStream(zip: File, dst: File, maxBytes: Long): Result? {
        val names = ArrayList<String>()
        ZipInputStream(zip.inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; if (!e.isDirectory) names.add(e.name) } }
        val root = rootOf(names) ?: return null
        var total = 0L
        ZipInputStream(zip.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val rel = target(e.name, root) ?: continue
                total += write(z, dst, rel, maxBytes - total) ?: return null
            }
        }
        return finish(dst)
    }

    /** Zapisuje wpis; zwraca liczbę bajtów albo null przy przekroczeniu limitu lub ścieżce spoza [dst] (zip-slip). */
    private fun write(input: InputStream, dst: File, rel: String, budget: Long): Long? {
        val f = File(dst, rel)
        if (!f.canonicalPath.startsWith(dst.canonicalPath + File.separator)) return 0L
        f.parentFile?.mkdirs()
        var n = 0L
        FileOutputStream(f).use { o ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf); if (r < 0) break
                n += r
                if (n > budget) return null
                o.write(buf, 0, r)
            }
        }
        return n
    }

    private fun finish(dst: File): Result? {
        val mf = File(dst, "meta.json")
        if (!mf.exists()) return null
        val pages = File(dst, "p").listFiles()?.filter { it.name.endsWith(".mnp") }?.map { it.name.removeSuffix(".mnp") }?.toSet() ?: emptySet()
        return Result(mf.readText(), pages)
    }
}
