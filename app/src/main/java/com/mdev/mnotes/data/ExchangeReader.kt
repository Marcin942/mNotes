package com.mdev.mnotes.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.core.codec.MnotesArchive
import com.mdev.mnotes.core.export.ExchangeIn
import com.mdev.mnotes.editor.T
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Wynik ostatniego wczytania: nowe kopie / podmienione / czekające (otwarte) / błędy. */
class ExchangeReport(val added: Int, val replaced: Int, val waiting: Int, val failed: Int, val at: Long)

/**
 * Folder wymiany po stronie czytającej (3c-A8): drugie urządzenie (telefon) wczytuje nowsze wersje zeszytów, które tablet
 * zapisuje do folderu ([SyncFolder]). Kopie lądują w folderze biblioteki „Folder wymiany” i otwierają się tylko do odczytu
 * ([com.mdev.mnotes.core.prefs.Reader]); niczego nie odsyłamy. Plan — [ExchangeIn]; tu tylko SAF, rozpakowanie i stan.
 * Synchronizację folderu z tabletem robi zewnętrzna aplikacja (mNotes nie ma uprawnienia INTERNET).
 */
class ExchangeReader(private val ctx: Context, private val repo: Repository, private val settings: Settings) {
    var running by mutableStateOf(false); private set
    var report by mutableStateOf<ExchangeReport?>(null); private set
    /** Zeszyty, które są kopiami z folderu (stan Compose: znaczek w bibliotece, blokada w edytorze). */
    var mirrors by mutableStateOf<Set<String>>(emptySet()); private set
    /** Zeszyty otwarte teraz w edytorze — ich podmiana czeka do następnego przebiegu. */
    var busy: () -> Set<String> = { emptySet() }

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "mnotes-exchange").apply { priority = Thread.MIN_PRIORITY } }
    private val main = Handler(Looper.getMainLooper())
    private val stateFile = File(ctx.filesDir, "exchange_in.json")
    private val resolver get() = ctx.contentResolver

    init { mirrors = runCatching { copiesOf(JSONObject(stateFile.readText())).values.map { it.nbId }.toSet() }.getOrDefault(emptySet()) }

    fun isMirror(id: String) = id in mirrors

    /** Czy dostęp do folderu nadal obowiązuje (wystarczy odczyt). */
    fun accessible(uri: String): Boolean = runCatching { Uri.parse(uri).let { u -> resolver.persistedUriPermissions.any { it.uri == u && it.isReadPermission } } }.getOrDefault(false)

    /** Wołać z wątku UI; przebieg trwa w tle. Zwraca false, gdy nic nie uruchomiono. */
    fun start(): Boolean {
        val uri = settings.exchangeUri
        if (uri.isEmpty() || running || !repo.loaded) return false
        running = true
        val alive = repo.notebooks.filter { !it.isTrashed && !repo.folderTrashed(it.folderId) }.map { it.id }.toSet()
        val own = alive - mirrors
        val open = busy()
        worker.execute {
            runCatching { scan(Uri.parse(uri), alive, own, open) }.onFailure {
                main.post { report = ExchangeReport(0, 0, 0, 1, System.currentTimeMillis()); running = false }
            }
        }
        return true
    }

    /** Przy otwarciu aplikacji (i powrocie do niej), gdy włączone. */
    fun auto() { if (settings.exchangeAuto && settings.exchangeUri.isNotEmpty()) start() }

    private class Found(val entry: ExchangeIn.Entry, val docId: String)

    /** Rozpakowany plik: katalog w cache i `meta.json` archiwum. */
    private class Staged(val dir: File, val meta: NotebookMeta)

    private fun scan(tree: Uri, alive: Set<String>, own: Set<String>, open: Set<String>) {
        val found = list(tree)
        val state = loadState(tree)
        val seen = seenOf(state)
        val staging = File(repo.cache, "exchange-in").apply { deleteRecursively(); mkdirs() }
        val staged = HashMap<String, Staged>()
        val docs = found.associate { it.entry.path to it.docId }
        var failed = 0

        fun fetch(e: ExchangeIn.Entry): Staged? {
            staged[e.path]?.let { return it }
            val dir = File(staging, "f${staged.size}-${System.nanoTime()}")
            val s = runCatching {
                resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, docs.getValue(e.path)))?.use { input ->
                    MnotesArchive.extract(input, dir, Importer.MAX_IMPORT, repo.cache)?.let { Staged(dir, NotebookMeta.fromJson(it.metaJson)) }
                }
            }.getOrNull()
            if (s == null) dir.deleteRecursively() else staged[e.path] = s
            return s
        }

        // Źródło nowych i zmienionych plików poznajemy, rozpakowując je; nieudane dostają puste źródło (bez ponawiania).
        for (e in ExchangeIn.unknown(found.map { it.entry }, seen)) {
            val s = fetch(e)
            if (s == null) failed++
            seen[e.path] = ExchangeIn.Seen(e.modified, e.size, s?.meta?.id.orEmpty())
        }
        seen.keys.retainAll(docs.keys)
        val copies = copiesOf(state)
        val plan = ExchangeIn.plan(found.map { it.entry }, seen, copies, alive, open, own)
        val ready = plan.acts.mapNotNull { a -> fetch(a.entry)?.let { a to it } ?: run { failed++; null } }
        // Rozpakowane, ale niepotrzebne (starsze archiwa tego samego zeszytu) — od razu sprzątane.
        val used = ready.map { it.second.dir }.toSet()
        staged.values.filter { it.dir !in used }.forEach { it.dir.deleteRecursively() }
        main.post { apply(tree, state, seen, copies, ready, plan.busy.size, failed) }
    }

    /** Na wątku UI: foldery biblioteki i stan zeszytów są stanem Compose. */
    private fun apply(tree: Uri, state: JSONObject, seen: Map<String, ExchangeIn.Seen>, copies: Map<String, ExchangeIn.Copy>, ready: List<Pair<ExchangeIn.Act, Staged>>, waiting: Int, failedBefore: Int) {
        val next = HashMap(copies)
        var added = 0; var replaced = 0; var failed = failedBefore
        var left = ready.size
        fun finish() {
            mirrors = next.values.map { it.nbId }.toSet()
            report = ExchangeReport(added, replaced, waiting, failed, System.currentTimeMillis())
            val json = encodeState(tree, state.optString("folder"), seen, next)
            worker.execute { runCatching { Repository.writeAtomic(stateFile, json.toByteArray(), false) }; main.post { running = false } }
        }
        if (left == 0) return finish()
        val now = System.currentTimeMillis()
        for ((act, s) in ready) {
            val local = (act as? ExchangeIn.Act.Replace)?.let { repo.meta(it.nbId) }
            val a = s.meta
            val m = a.copy(
                id = local?.id ?: newId(),
                title = a.title.ifBlank { ExchangeIn.titleOf(act.entry.path) },
                folderId = local?.folderId ?: folderFor(state, ExchangeIn.dirs(act.entry.path)),
                created = local?.created ?: a.created, modified = now, opened = local?.opened ?: now,
                favorite = local?.favorite ?: false, trashedAt = 0L, locked = false,
                // Miejsce czytania zostaje po podmianie.
                lastPage = (local?.lastPage ?: 0).coerceIn(0, (a.pages.size - 1).coerceAtLeast(0)),
            )
            repo.putFromDir(s.dir, m) { ok ->
                if (ok) { next[act.src] = ExchangeIn.Copy(m.id, act.entry.modified); if (local == null) added++ else replaced++ } else failed++
                if (--left == 0) finish()
            }
        }
    }

    /** Folder biblioteki dla katalogów [dirs] pod folderem „Folder wymiany” (tworzony w razie potrzeby). */
    private fun folderFor(state: JSONObject, dirs: List<String>): String {
        val live = repo.folders.filter { it.trashedAt == 0L }
        var parent = state.optString("folder").takeIf { id -> live.any { it.id == id } }
            ?: repo.createFolder(T.t("Folder wymiany", "Exchange folder"), null, COLOR).id.also { state.put("folder", it) }
        for (d in dirs) {
            val name = com.mdev.mnotes.core.text.NameText.oneLine(d)
            parent = repo.folders.firstOrNull { it.trashedAt == 0L && it.parentId == parent && it.name == name }?.id
                ?: repo.createFolder(name, parent, COLOR).id
        }
        return parent
    }

    /** Pliki `.mnotes` w drzewie (do [MAX_DEPTH] poziomów, najwyżej [MAX_FILES]). */
    private fun list(tree: Uri): List<Found> {
        val out = ArrayList<Found>()
        fun walk(parent: String, prefix: String, depth: Int) {
            if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE)
            val dirs = ArrayList<Pair<String, String>>()
            // Brak listy w korzeniu = dostawca nie obsługuje drzewa (np. bywa tak z Dyskiem Google): błąd, nie „pusty folder”.
            (resolver.query(uri, cols, null, null, null) ?: if (depth == 0) error("listing") else return).use { c ->
                while (c.moveToNext() && out.size < MAX_FILES) {
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == Document.MIME_TYPE_DIR) { if (!name.startsWith(".")) dirs.add(c.getString(0) to name); continue }
                    if (!ExchangeIn.candidate(name)) continue
                    out.add(Found(ExchangeIn.Entry(prefix + name, if (c.isNull(3)) 0L else c.getLong(3), if (c.isNull(4)) -1L else c.getLong(4)), c.getString(0)))
                }
            }
            for ((id, name) in dirs) walk(id, "$prefix$name/", depth + 1)
        }
        walk(DocumentsContract.getTreeDocumentId(tree), "", 0)
        return out
    }

    // — Stan: {uri, folder, seen: {ścieżka: [czas, rozmiar, źródło]}, copies: {źródło: [zeszyt, czas]}} —

    private fun loadState(tree: Uri): JSONObject {
        val fresh = JSONObject().put("uri", tree.toString())
        return runCatching { JSONObject(stateFile.readText()).takeIf { it.optString("uri") == tree.toString() } }.getOrNull() ?: fresh
    }

    private fun seenOf(o: JSONObject): HashMap<String, ExchangeIn.Seen> {
        val out = HashMap<String, ExchangeIn.Seen>()
        val s = o.optJSONObject("seen") ?: return out
        for (k in s.keys()) runCatching { val a = s.getJSONArray(k); out[k] = ExchangeIn.Seen(a.getLong(0), a.getLong(1), a.getString(2)) }
        return out
    }

    private fun copiesOf(o: JSONObject): Map<String, ExchangeIn.Copy> {
        val out = HashMap<String, ExchangeIn.Copy>()
        val s = o.optJSONObject("copies") ?: return out
        for (k in s.keys()) runCatching { val a = s.getJSONArray(k); out[k] = ExchangeIn.Copy(a.getString(0), a.getLong(1)) }
        return out
    }

    private fun encodeState(tree: Uri, folder: String, seen: Map<String, ExchangeIn.Seen>, copies: Map<String, ExchangeIn.Copy>): String {
        val s = JSONObject().also { o -> seen.forEach { (k, v) -> o.put(k, org.json.JSONArray().put(v.modified).put(v.size).put(v.src)) } }
        val c = JSONObject().also { o -> copies.forEach { (k, v) -> o.put(k, org.json.JSONArray().put(v.nbId).put(v.modified)) } }
        return JSONObject().put("uri", tree.toString()).put("folder", folder).put("seen", s).put("copies", c).toString()
    }

    /** Próba odczytu (poza wątkiem UI): liczba plików `.mnotes` albo null, gdy dostawca nie daje przejrzeć folderu. */
    fun probe(uri: String): Int? = runCatching { list(Uri.parse(uri)).size }.getOrNull()

    /** Nazwa folderu do pokazania w ustawieniach (poza wątkiem UI). */
    fun folderName(uri: String): String? = runCatching {
        val tree = Uri.parse(uri)
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(doc, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** Po odłączeniu folderu: kopie zostają w bibliotece jako zwykłe zeszyty (już nie blokowane). */
    fun forget() { runCatching { stateFile.delete() }; mirrors = emptySet(); report = null }

    companion object {
        const val MAX_DEPTH = 8
        const val MAX_FILES = 2000
        private val COLOR = 0xFF5B7DB1.toInt()
    }
}
