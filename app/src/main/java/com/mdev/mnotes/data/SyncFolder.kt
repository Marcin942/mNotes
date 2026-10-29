package com.mdev.mnotes.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.core.export.SyncNames
import com.mdev.mnotes.render.Assets
import com.mdev.mnotes.render.PdfPool
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Wynik ostatniego przebiegu: zapisane / bez zmian / zablokowane (pominięte) / błędy. */
class SyncReport(val written: Int, val unchanged: Int, val locked: Int, val failed: Int, val at: Long)

/**
 * Lustro biblioteki w folderze wybranym przez użytkownika (SAF) — pod Syncthing, Nextcloud, rsync itp.
 * Każdy zeszyt trafia jako `Folder/Zeszyt.mnotes` (pełna wierność, da się zaimportować z powrotem) oraz opcjonalnie PDF i Markdown.
 * Zeszyty bez zmian od ostatniego razu są pomijane; zeszyty zablokowane nie są kopiowane (plik byłby jawny).
 * Aplikacja niczego w tym folderze nie kasuje — po zmianie nazwy zeszytu stary plik zostaje jako archiwum.
 * Bez zależności od `documentfile`: tylko `DocumentsContract`.
 */
class SyncFolder(private val ctx: Context, private val repo: Repository, private val settings: Settings, assets: Assets, pdf: PdfPool) {
    var running by mutableStateOf(false); private set
    var done by mutableIntStateOf(0); private set
    var total by mutableIntStateOf(0); private set
    var report by mutableStateOf<SyncReport?>(null); private set
    /** 3c-A8: zeszyty pomijane — kopie z folderu wymiany nie wracają do lustra (byłyby cudzym plikiem pod naszą nazwą). */
    var skip: (String) -> Boolean = { false }

    private val exporter = Exporter(repo, assets, pdf)
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "mnotes-sync").apply { priority = Thread.MIN_PRIORITY } }
    private val stateFile = File(ctx.filesDir, "sync_state.json")
    private val resolver get() = ctx.contentResolver

    /** Nazwa wybranego folderu do pokazania w ustawieniach (zapytanie do dostawcy — poza wątkiem UI). */
    fun folderName(uri: String): String? = runCatching {
        val tree = Uri.parse(uri)
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(doc, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** Czy dostęp do folderu nadal obowiązuje (użytkownik mógł go cofnąć w systemie). */
    fun accessible(uri: String): Boolean = runCatching { Uri.parse(uri).let { u -> resolver.persistedUriPermissions.any { it.uri == u && it.isWritePermission } } }.getOrDefault(false)

    /** Wołać z wątku UI; przebieg trwa w tle. Zwraca false, gdy nic nie uruchomiono. */
    fun start(): Boolean {
        val uri = settings.syncUri
        if (uri.isEmpty() || running) return false
        running = true; done = 0; total = 0
        val books = repo.notebooks.filter { !it.isTrashed && !repo.folderTrashed(it.folderId) && !skip(it.id) }
        val folders = repo.folders
        val formats = buildList {
            add(ExportFormat.MNOTES)
            if (settings.syncPdf) add(ExportFormat.PDF)
            if (settings.syncMd) add(ExportFormat.MD)
        }
        worker.execute { runCatching { run(Uri.parse(uri), books, folders, formats) }.onFailure { running = false } }
        return true
    }

    /** Kopia automatyczna (np. po zejściu aplikacji w tło). */
    fun auto() { if (settings.syncAuto && settings.syncUri.isNotEmpty()) start() }

    private class Child(val id: String, val dir: Boolean)

    private fun run(tree: Uri, books: List<NotebookMeta>, folders: List<Folder>, formats: List<ExportFormat>) {
        var written = 0; var unchanged = 0; var failed = 0
        val lockedCount = books.count { it.locked }
        val todo = books.filter { !it.locked }
        total = todo.size * formats.size
        try {
            exporter.awaitWrites()
            val state = loadState(tree)
            val items = state.getJSONObject("items")
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val listing = HashMap<String, Map<String, Child>>()

            fun children(parent: String): Map<String, Child> = listing.getOrPut(parent) {
                val out = LinkedHashMap<String, Child>()
                val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
                resolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                    while (c.moveToNext()) out[c.getString(1)] = Child(c.getString(0), c.getString(2) == Document.MIME_TYPE_DIR)
                }
                out
            }

            fun ensureDir(parent: String, name: String): String? {
                children(parent)[name]?.let { if (it.dir) return it.id }
                val created = DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, parent), Document.MIME_TYPE_DIR, name) ?: return null
                val id = DocumentsContract.getDocumentId(created)
                listing[parent] = children(parent) + (name to Child(id, true))
                return id
            }

            val byId = folders.associateBy { it.id }
            fun dirNames(folderId: String?): List<String> {
                val chain = ArrayList<String>()
                var f = folderId; var guard = 0
                while (f != null && guard++ < 64) { val fo = byId[f] ?: break; chain.add(0, safe(fo.name)); f = fo.parentId }
                return chain
            }

            // Nazwy plików w obrębie jednego katalogu muszą być unikatowe, a plik innego zeszytu z poprzednich przebiegów
            // nie przechodzi na nowego właściciela (SyncNames): powtórzone tytuły dostają końcówkę z identyfikatora.
            // Wszystkie kiedykolwiek zapisane ścieżki (także sprzed zmiany tytułu — stary plik zostaje archiwum) → zeszyt.
            val owned = state.optJSONObject("owned") ?: JSONObject().also { state.put("owned", it) }
            val prev = HashMap<String, String>().also { m -> for (k in items.keys()) m[k] = items.optString(k) }
            val owners = SyncNames.owners(prev) + HashMap<String, String>().also { m -> for (k in owned.keys()) m[k] = owned.optString(k) }
            val baseOf = SyncNames.assign(
                todo.map { SyncNames.Book(it.id, dirNames(it.folderId).joinToString("/"), safe(it.title), it.created) },
                owners, formats.map { it.ext },
            )
            val scratch = File(repo.cache, "sync-part").apply { mkdirs() }

            for (nb in todo) {
                var dirId: String? = rootId
                for (n in dirNames(nb.folderId)) { dirId = ensureDir(dirId ?: break, n) }
                for (fmt in formats) {
                    val name = "${baseOf[nb.id]}.${fmt.ext}"
                    val key = "${nb.id}|${fmt.ext}"
                    // Etykiety, okładka, ulubione i zakładki zmieniają plik .mnotes, choć nie ruszają `modified` (3c-A16).
                    val sig = listOf(nb.tags, nb.cover, nb.coverStyle, nb.favorite, nb.bookmarks, nb.pageTags).hashCode()
                    val stamp = "${nb.modified}:$sig|" + SyncNames.path(dirNames(nb.folderId).joinToString("/"), baseOf.getValue(nb.id), fmt.ext)
                    val part = File(scratch, "$key.part".replace('|', '.'))
                    try {
                        val dir = dirId ?: throw IllegalStateException("no dir")
                        val existing = children(dir)[name]
                        if (existing != null && !existing.dir && items.optString(key) == stamp) { unchanged++; continue }
                        // Najpierw do pliku tymczasowego: nieudany eksport (zeszyt usunięty w trakcie, strona podmieniana
                        // podczas pakowania) nie obcina dobrej kopii w folderze do zera (3c-A16).
                        val ok = part.outputStream().buffered(1 shl 16).use { exporter.write(nb.id, fmt, null, it) }
                        if (!ok) throw IllegalStateException("write")
                        val target = if (existing != null && !existing.dir) DocumentsContract.buildDocumentUriUsingTree(tree, existing.id)
                        else DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, dir), mime(fmt), name)
                            ?: throw IllegalStateException("create")
                        if (existing == null) listing[dir] = children(dir) + (name to Child(DocumentsContract.getDocumentId(target), false))
                        resolver.openOutputStream(target, "wt")?.use { out -> part.inputStream().use { it.copyTo(out, 1 shl 16) } }
                            ?: throw IllegalStateException("open")
                        items.put(key, stamp); owned.put(stamp.substringAfterLast('|').lowercase(), nb.id); written++
                    } catch (_: Throwable) { failed++ } finally { part.delete(); done++ }
                }
            }
            saveState(state)
            report = SyncReport(written, unchanged, lockedCount, failed, System.currentTimeMillis())
            if (failed == 0) settings.lastSync = (System.currentTimeMillis() / 60000L).toInt()
        } finally { running = false }
    }

    private fun mime(f: ExportFormat) = if (f == ExportFormat.MNOTES) "application/octet-stream" else f.mime

    private fun safe(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.').ifEmpty { "mNotes" }.take(80)

    private fun loadState(tree: Uri): JSONObject {
        val fresh = JSONObject().put("uri", tree.toString()).put("items", JSONObject())
        return runCatching {
            val o = JSONObject(stateFile.readText())
            if (o.optString("uri") == tree.toString() && o.has("items")) o else fresh
        }.getOrDefault(fresh)
    }

    private fun saveState(o: JSONObject) { runCatching { Repository.writeAtomic(stateFile, o.toString().toByteArray(), false) } }

    /** Po odłączeniu folderu zapominamy, co już skopiowano. */
    fun forgetState() { runCatching { stateFile.delete() } }
}
