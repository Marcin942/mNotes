package com.mdev.mnotes.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.app.MNotesApp
import com.mdev.mnotes.core.samsung.ConvertOptions
import com.mdev.mnotes.core.samsung.ConvertedNote
import com.mdev.mnotes.core.samsung.ImportMode
import com.mdev.mnotes.core.samsung.SamsungConvert
import com.mdev.mnotes.render.SdAndroidMeasure
import com.mdev.mnotes.core.samsung.SdDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SdItemState { IDLE, LOADING, READY, FAILED, SAVED }

/**
 * Jeden plik w oknie importu. Wczytany dokument i wynik konwersji żyją tylko, dopóki plik jest blisko oglądanego
 * (pliki z całego folderu nie mieszczą się naraz w pamięci), reszta jest czytana ponownie, gdy użytkownik do niej wróci.
 */
class SamsungItem(val source: ImportSource) {
    var title by mutableStateOf(SamsungConvert.defaultTitle(source.name))
    var include by mutableStateOf(true)
    /** Własny wybór tego pliku; null = bierze ustawienie wspólne. Liczy się tylko przy „każdy osobno”. */
    var mode by mutableStateOf<ImportMode?>(null)
    var dark by mutableStateOf<Boolean?>(null)
    var state by mutableStateOf(SdItemState.IDLE)
    var error by mutableStateOf<String?>(null)
    var note by mutableStateOf<ConvertedNote?>(null)
    /** Liczba stron i pociągnięć znana po pierwszym odczycie, także gdy [note] został zwolniony. */
    var pages by mutableIntStateOf(0)
    var warnings by mutableIntStateOf(0)
    /** Trwa (ponowna) konwersja po zmianie ustawień; podgląd pokazuje wtedy jeszcze poprzedni wynik. */
    var busy by mutableStateOf(false)
    internal var doc: SdDocument? = null
    internal var noteOpts: ConvertOptions? = null
    internal val lock = Mutex()
}

/**
 * Stan okna importu Samsung Notes: lista plików, ustawienia wspólne i osobne oraz zapis. Ustawienie „ciemny” ma wartość
 * null, dopóki użytkownik go nie ruszył, i wtedy podąża za motywem aplikacji (przekazywanym przy każdym wywołaniu).
 */
class SamsungSession(
    private val app: MNotesApp,
    private val scope: CoroutineScope,
    val folderId: String?,
) {
    val items = mutableStateListOf<SamsungItem>()
    var index by mutableIntStateOf(0)
        private set
    /** Jedno ustawienie dla wszystkich plików (domyślnie, gdy plik jest jeden, nie ma czego rozdzielać). */
    var sameForAll by mutableStateOf(true)
    var sharedMode by mutableStateOf(if (app.settings.samsungMode == 1) ImportMode.STANDARD else ImportMode.ORIGINAL)
    var sharedDark by mutableStateOf<Boolean?>(null)

    var saving by mutableStateOf(false)
        private set
    var savedCount by mutableIntStateOf(0)
        private set
    var failedCount by mutableIntStateOf(0)
        private set
    /** Zeszyty utworzone w tej sesji (okno otwiera zeszyt, gdy powstał dokładnie jeden). */
    val made = ArrayList<NotebookMeta>()

    val current: SamsungItem? get() = items.getOrNull(index)
    val selectedCount: Int get() = items.count { it.include && it.state != SdItemState.SAVED && it.state != SdItemState.FAILED }

    fun add(sources: List<ImportSource>) {
        val known = items.map { it.source.uri }.toSet()
        sources.filter { it.uri !in known }.forEach { items.add(SamsungItem(it)) }
    }

    fun modeOf(item: SamsungItem) = if (sameForAll) sharedMode else item.mode ?: sharedMode
    fun darkOf(item: SamsungItem, themeDark: Boolean) = (if (sameForAll) sharedDark else item.dark ?: sharedDark) ?: themeDark
    fun optionsOf(item: SamsungItem, themeDark: Boolean) = ConvertOptions(modeOf(item), darkOf(item, themeDark))

    fun setMode(item: SamsungItem, m: ImportMode) {
        if (sameForAll) sharedMode = m else item.mode = m
        app.settings.samsungMode = if (m == ImportMode.STANDARD) 1 else 0
    }

    fun setDark(item: SamsungItem, d: Boolean) {
        if (sameForAll) sharedDark = d else item.dark = d
    }

    /** Przenosi ustawienia oglądanego pliku na wszystkie i przełącza na „jedno dla wszystkich”. */
    fun applyCurrentToAll(themeDark: Boolean) {
        val cur = current ?: return
        sharedMode = modeOf(cur)
        sharedDark = darkOf(cur, themeDark)
        sameForAll = true
    }

    fun select(i: Int) {
        if (i !in items.indices) return
        index = i
        // Zwolnij dokumenty, które nie są sąsiadami oglądanego (podgląd następnego i poprzedniego zostaje szybki).
        items.forEachIndexed { k, it -> if (k < i - 1 || k > i + 1) release(it) }
    }

    fun step(delta: Int) = select((index + delta).coerceIn(0, (items.size - 1).coerceAtLeast(0)))

    private fun release(item: SamsungItem) {
        if (item.state == SdItemState.LOADING) return
        item.doc = null
        item.note = null
        item.noteOpts = null
        if (item.state == SdItemState.READY) item.state = SdItemState.IDLE
    }

    /** Wczytuje i przekształca plik według [opts], jeśli jeszcze tego nie zrobiono dla tych samych ustawień. */
    suspend fun prepare(item: SamsungItem, opts: ConvertOptions) {
        item.lock.withLock {
            if (item.state == SdItemState.SAVED) return
            if (item.note != null && item.noteOpts == opts) return
            if (item.state == SdItemState.FAILED && item.doc == null) return
            val first = item.note == null
            if (first) item.state = SdItemState.LOADING
            item.busy = true
            try {
                val doc = item.doc ?: withContext(Dispatchers.IO) { SamsungLoad.read(app, item.source) }.also { item.doc = it }
                val note = withContext(Dispatchers.Default) { SamsungConvert.convert(doc, item.source.name, opts, measure = SdAndroidMeasure()) }
                item.note = note
                item.noteOpts = opts
                item.pages = note.pages.size
                item.warnings = note.warnings
                item.error = null
                item.state = SdItemState.READY
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (first) item.state = SdItemState.IDLE
                throw e
            } catch (e: Throwable) {
                item.doc = null
                item.note = null
                item.noteOpts = null
                item.error = e.message ?: e.javaClass.simpleName
                item.state = SdItemState.FAILED
                item.include = false
            } finally {
                item.busy = false
            }
        }
    }

    private var saveJob: Job? = null

    /**
     * Zapisuje zaznaczone pliki po kolei jako zeszyty. [onFinished] dostaje liczbę zapisanych i nieudanych; okno zamyka
     * się samo tylko wtedy, gdy nic się nie udało (inaczej użytkownik widzi, które pliki zostały).
     */
    fun save(themeDark: Boolean, onFinished: (saved: Int, failed: Int) -> Unit) {
        if (saving) return
        saving = true
        savedCount = 0
        failedCount = 0
        saveJob = scope.launch {
            val importer = SamsungImporter(app.repo)
            for (item in items.toList()) {
                if (!item.include || item.state == SdItemState.SAVED) continue
                try {
                    prepare(item, optionsOf(item, themeDark))
                    val note = item.note ?: throw IllegalStateException(item.error ?: "brak wyniku konwersji")
                    val title = item.title.trim().ifEmpty { note.title }
                    val meta = withContext(Dispatchers.IO) { importer.save(note, title, folderId) }
                    app.repo.addNotebook(meta)
                    made.add(meta)
                    item.state = SdItemState.SAVED
                    item.include = false
                    savedCount++
                    release(item)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    item.error = e.message ?: e.javaClass.simpleName
                    item.state = SdItemState.FAILED
                    item.include = false
                    failedCount++
                }
            }
            saving = false
            onFinished(savedCount, failedCount)
        }
    }

    fun cancel() { saveJob?.cancel(); saving = false }
}
