package com.mdev.mnotes.data

import com.mdev.mnotes.core.text.NameText
import com.mdev.mnotes.core.text.TagNames
import com.mdev.mnotes.editor.T
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.core.codec.LocalName
import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.codec.Streams
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSpec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Biblioteka na dysku (zwykłe pliki, otwarte formaty):
 *
 *     library/folders.json · tags.json
 *     library/nb/<id>/meta.json · p/<strona>.mnp · assets/<plik> · thumb.png
 *
 * Wszystkie zapisy są atomowe (plik tymczasowy + zmiana nazwy) i zostawiają kopię `.bak` (SEC-003).
 * Stan w pamięci jest stanem Compose; zapisy idą przez jeden wątek `io`, więc kolejność jest zachowana.
 */
class Repository(ctx: Context) {
    val root = File(ctx.filesDir, "library")
    /** Pliki tymczasowe (np. archiwum w trakcie importu); system może je sprzątnąć, nie trafiają do kopii. */
    val cache: File = ctx.cacheDir
    private val nbRoot = File(root, "nb")
    val io: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "mnotes-io").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())

    var notebooks by mutableStateOf<List<NotebookMeta>>(emptyList())
        private set
    var folders by mutableStateOf<List<Folder>>(emptyList())
        private set
    var tagColors by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var loaded by mutableStateOf(false)
        private set

    fun load(onDone: () -> Unit = {}) {
        io.execute {
            nbRoot.mkdirs()
            val nbs = ArrayList<NotebookMeta>()
            nbRoot.listFiles()?.forEach { d ->
                if (!d.isDirectory) return@forEach
                d.walkTopDown().filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
                // Identyfikator = nazwa folderu (3c-A16): przerwana kopia lub import zostawiały w nowym folderze meta.json
                // źródła; z jego identyfikatorem dwa zeszyty dzieliły jeden folder, a „Usuń na zawsze” kasowało oryginał.
                readText(File(d, "meta.json"))?.let { runCatching { NotebookMeta.fromJson(it) }.getOrNull() }
                    ?.let { nbs.add(if (it.id == d.name) it else it.copy(id = d.name)) }
            }
            val fl = ArrayList<Folder>()
            readText(File(root, "folders.json"))?.let { s ->
                runCatching { val a = JSONArray(s); for (i in 0 until a.length()) fl.add(Folder.fromJson(a.getJSONObject(i))) }
            }
            val tg = LinkedHashMap<String, Int>()
            readText(File(root, "tags.json"))?.let { s ->
                runCatching { val o = JSONObject(s); for (k in o.keys()) tg[k] = o.getInt(k) }
            }
            val cutoff = System.currentTimeMillis() - TRASH_DAYS * DAY
            val expired = nbs.filter { it.trashedAt in 1 until cutoff }
            expired.forEach { File(nbRoot, it.id).deleteRecursively() }
            main.post {
                notebooks = nbs - expired.toSet()
                folders = fl
                tagColors = tg
                loaded = true
                onDone()
            }
        }
    }

    fun meta(id: String): NotebookMeta? = notebooks.firstOrNull { it.id == id }
    fun folder(id: String?): Folder? = if (id == null) null else folders.firstOrNull { it.id == id }

    fun dir(id: String) = File(nbRoot, id)
    // Identyfikator strony i nazwa zasobu przychodzą z pliku zeszytu (także importowanego): bez `../` w ścieżce.
    fun pageFile(nb: String, page: String) = File(dir(nb), "p/${LocalName.plain(page)}.mnp")
    fun assetFile(nb: String, name: String) = File(dir(nb), "assets/${LocalName.plain(name)}")
    fun thumbFile(nb: String) = File(dir(nb), "thumb.png")

    // — Notatniki —

    fun update(id: String, f: (NotebookMeta) -> NotebookMeta) {
        var updated: NotebookMeta? = null
        notebooks = notebooks.map { if (it.id == id) f(it).let { n -> n.copy(title = NameText.oneLine(n.title)) }.also { n -> updated = n } else it }
        updated?.let { m -> val json = m.toJson(); io.execute { writeAtomic(File(dir(id), "meta.json"), json.toByteArray(), true) } }
    }

    fun createNotebook(title: String, spec: PageSpec, cover: Int, coverStyle: Int, folderId: String?, pageCount: Int = 1): NotebookMeta {
        val now = System.currentTimeMillis()
        val pages = List(pageCount.coerceAtLeast(1)) { PageMeta(newId(), spec) }
        return addNotebook(NotebookMeta(newId(), NameText.oneLine(title), folderId, now, now, now, cover, coverStyle, pages = pages))
    }

    fun addNotebook(m: NotebookMeta): NotebookMeta {
        notebooks = notebooks + m
        val json = m.toJson()
        io.execute { writeAtomic(File(dir(m.id), "meta.json"), json.toByteArray(), true) }
        return m
    }

    /**
     * 3c-A8: zeszyt z rozpakowanego archiwum [staged] (katalog w [cache]) — nowy, gdy [m] nie ma go w bibliotece, inaczej
     * podmiana treści tego samego identyfikatora. Stara treść odchodzi dopiero po udanym przeniesieniu nowej.
     * [done] dostaje wynik na wątku UI.
     */
    fun putFromDir(staged: File, m: NotebookMeta, done: (Boolean) -> Unit) {
        io.execute {
            val d = dir(m.id)
            val old = File(cache, "replaced-${m.id}-${System.nanoTime()}")
            val ok = runCatching {
                if (d.exists() && !move(d, old)) error("old")
                if (!move(staged, d)) { if (old.exists()) move(old, d); error("new") }
                writeAtomic(File(d, "meta.json"), m.toJson().toByteArray(), false)
            }.isSuccess
            old.deleteRecursively(); staged.deleteRecursively()
            main.post {
                if (ok) notebooks = if (notebooks.any { it.id == m.id }) notebooks.map { if (it.id == m.id) m else it } else notebooks + m
                done(ok)
            }
        }
    }

    /** Przeniesienie katalogu: zmiana nazwy (ten sam system plików), a gdy się nie da — kopia i usunięcie. */
    private fun move(from: File, to: File): Boolean =
        from.renameTo(to) || (runCatching { from.copyRecursively(to, overwrite = true) }.getOrDefault(false) && from.deleteRecursively())

    fun duplicate(id: String, suffix: String) {
        val src = meta(id) ?: return
        val now = System.currentTimeMillis()
        val copy = src.copy(id = newId(), title = src.title + suffix, created = now, modified = now, opened = now, favorite = false, trashedAt = 0L)
        io.execute {
            val dst = dir(copy.id)
            // Najpierw własny meta.json, potem reszta bez meta.json źródła: przerwana kopia (pełny dysk, zamknięcie
            // aplikacji) nie udaje oryginału. Nieudana — sprzątana, zamiast wywracać aplikację wyjątkiem w wątku io.
            val ok = runCatching {
                dst.mkdirs()
                writeAtomic(File(dst, "meta.json"), copy.toJson().toByteArray(), false)
                dir(id).listFiles()?.forEach { f -> if (!f.name.startsWith("meta.json")) f.copyRecursively(File(dst, f.name), overwrite = true) }
            }.isSuccess
            if (!ok) { dst.deleteRecursively(); notice?.invoke(T.t("Nie udało się skopiować zeszytu (brak miejsca?)", "Could not copy the notebook (out of space?)")); return@execute }
            main.post { notebooks = notebooks + copy }
        }
    }

    fun trash(ids: Collection<String>) { val now = System.currentTimeMillis(); ids.forEach { update(it) { m -> m.copy(trashedAt = now) } } }
    fun restore(ids: Collection<String>) { ids.forEach { update(it) { m -> m.copy(trashedAt = 0L) } } }

    fun deleteForever(id: String) {
        notebooks = notebooks.filter { it.id != id }
        io.execute { dir(id).deleteRecursively() }
    }

    fun emptyTrash() {
        val trashedFolders = folders.filter { it.trashedAt > 0 }.map { it.id }.toSet()
        notebooks.filter { it.isTrashed || isInside(it.folderId, trashedFolders) }.forEach { deleteForever(it.id) }
        folders = folders.filter { it.trashedAt == 0L && !isInside(it.id, trashedFolders) }
        saveFolders()
    }

    private fun isInside(folderId: String?, ancestors: Set<String>): Boolean {
        var f = folderId
        var guard = 0
        while (f != null && guard++ < 64) { if (f in ancestors) return true; f = folder(f)?.parentId }
        return false
    }

    /** Czy folder (lub któryś jego przodek) jest w koszu. */
    fun folderTrashed(id: String?): Boolean {
        var f = id
        var guard = 0
        while (f != null && guard++ < 64) { val fo = folder(f) ?: return false; if (fo.trashedAt > 0) return true; f = fo.parentId }
        return false
    }

    // — Foldery i tagi —

    fun createFolder(name: String, parentId: String?, color: Int): Folder {
        val f = Folder(newId(), NameText.oneLine(name), parentId, color)
        folders = folders + f
        saveFolders()
        return f
    }

    fun updateFolder(id: String, f: (Folder) -> Folder) {
        folders = folders.map { if (it.id == id) f(it).let { x -> x.copy(name = NameText.oneLine(x.name)) } else it }
        saveFolders()
    }

    fun deleteFolderForever(id: String) {
        val kids = folders.filter { it.parentId == id }.map { it.id }
        kids.forEach { deleteFolderForever(it) }
        notebooks.filter { it.folderId == id }.forEach { deleteForever(it.id) }
        folders = folders.filter { it.id != id }
        saveFolders()
    }

    fun isFolderDescendant(candidate: String?, ancestor: String): Boolean = isInside(candidate, setOf(ancestor))

    private fun saveFolders() {
        val a = JSONArray(); folders.forEach { a.put(it.toJson()) }
        val s = a.toString()
        io.execute { writeAtomic(File(root, "folders.json"), s.toByteArray(), true) }
    }

    private fun saveTags() {
        val o = JSONObject(); tagColors.forEach { (k, v) -> o.put(k, v) }
        val s = o.toString()
        io.execute { writeAtomic(File(root, "tags.json"), s.toByteArray(), false) }
    }

    fun setTag(rawName: String, color: Int?) {
        val name = NameText.oneLine(rawName)
        tagColors = if (color == null) tagColors - name else tagColors + (name to color)
        saveTags()
        if (color == null) notebooks.filter { name in it.tags }.forEach { n -> update(n.id) { it.copy(tags = it.tags - name) } }
    }

    /** Zmienia nazwę tagu w tabeli kolorów i we wszystkich zeszytach (także w koszu); nazwa istniejącego tagu zlewa oba. */
    fun renameTag(old: String, rawNew: String) {
        val new = TagNames.clean(rawNew)
        if (new.isEmpty() || new == old) return
        tagColors = TagNames.renamedColors(tagColors, old, new)
        saveTags()
        notebooks.filter { old in it.tags }.forEach { n -> update(n.id) { it.copy(tags = TagNames.renamed(it.tags, old, new)) } }
    }

    // — Strony i zasoby —

    /** Komunikat dla użytkownika (np. odzyskanie strony z kopii `.bak`); ustawiany przez aktywność, wołany z dowolnego wątku. */
    @Volatile var notice: ((String) -> Unit)? = null

    fun readPage(nb: String, page: String): PageContent {
        val main = pageFile(nb, page)
        val bak = File(main.path + ".bak")
        if (main.exists()) {
            runCatching { PageCodec.decode(main.readBytes()) }.getOrNull()?.let { return it }
            // Uszkodzony plik strony: zachowujemy go obok (nie zostanie nadpisany kopią), a stronę odtwarzamy z `.bak`.
            runCatching { main.copyTo(File(main.path + ".corrupt"), overwrite = true) }
            val c = if (bak.exists()) runCatching { PageCodec.decode(bak.readBytes()) }.getOrNull() else null
            if (c != null) {
                runCatching { bak.copyTo(main, overwrite = true) }
                notice?.invoke(T.t("Odzyskano stronę z kopii zapasowej", "A page was recovered from its backup copy"))
                return c
            }
            notice?.invoke(T.t("Strona jest uszkodzona i nie da się jej odzyskać", "A page is damaged and cannot be recovered"))
            return PageContent.EMPTY
        }
        if (bak.exists()) runCatching { PageCodec.decode(bak.readBytes()) }.getOrNull()?.let { return it }
        return PageContent.EMPTY
    }

    fun writePage(nb: String, page: String, bytes: ByteArray) = writeAtomic(pageFile(nb, page), bytes, true)

    fun deletePageFiles(nb: String, page: String) {
        val f = pageFile(nb, page)
        f.delete(); File(f.path + ".bak").delete()
    }

    /** Kopiuje strumień do zasobów zeszytu i zwraca nazwę pliku. Wywoływać poza wątkiem UI. */
    fun importAsset(nb: String, input: InputStream, ext: String): String {
        val name = newId() + "." + ext
        val f = assetFile(nb, name)
        f.parentFile?.mkdirs()
        // Strumień od innej aplikacji może nie mieć końca: bez limitu zapełniał dysk, a urwany plik zostawał w zeszycie.
        try {
            FileOutputStream(f).use { out -> Streams.copyAtMost(input, out, MAX_ASSET) }
        } catch (e: Throwable) {
            f.delete()
            throw e
        }
        return name
    }

    private fun readText(f: File): String? {
        for (c in listOf(f, File(f.path + ".bak"))) {
            if (c.exists()) runCatching { c.readText() }.getOrNull()?.let { if (it.isNotBlank()) return it }
        }
        return null
    }

    companion object {
        const val DAY = 24L * 3600_000L
        const val TRASH_DAYS = 30L
        /** Największy pojedynczy plik zasobu (PDF, obraz); tyle samo co cały import `.mnotes`. */
        const val MAX_ASSET = 2L * 1024 * 1024 * 1024

        fun writeAtomic(f: File, bytes: ByteArray, keepBak: Boolean) {
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
            if (keepBak && f.exists()) {
                val bak = File(f.path + ".bak")
                bak.delete()
                f.renameTo(bak)
            }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}
