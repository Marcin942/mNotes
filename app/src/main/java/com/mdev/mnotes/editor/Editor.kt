package com.mdev.mnotes.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.mdev.mnotes.core.layout.DocLayout
import com.mdev.mnotes.core.layout.PageOverflow
import com.mdev.mnotes.core.layout.ViewMode
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.app.MNotesApp
import com.mdev.mnotes.core.geom.Axes
import com.mdev.mnotes.core.geom.AxesSnap
import com.mdev.mnotes.core.geom.AxesSpec
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.geom.ConstructOps
import com.mdev.mnotes.core.geom.GeoTool
import com.mdev.mnotes.core.pages.LecturePages
import com.mdev.mnotes.core.pages.PageTags
import com.mdev.mnotes.core.model.DocEdit
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.edit.CropFrame
import com.mdev.mnotes.core.edit.CropGeom
import com.mdev.mnotes.core.edit.CropHandle
import com.mdev.mnotes.core.edit.CropPlace
import com.mdev.mnotes.core.edit.CutLine
import com.mdev.mnotes.core.edit.FloodFill
import com.mdev.mnotes.core.edit.ImageCut
import com.mdev.mnotes.core.edit.ImageArrange
import com.mdev.mnotes.core.edit.GridFit
import com.mdev.mnotes.core.edit.Snip
import com.mdev.mnotes.core.edit.Sticker
import com.mdev.mnotes.core.shapes.PointEdit
import com.mdev.mnotes.core.model.ImageCrop
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.ImageFx
import com.mdev.mnotes.core.model.WhiteBalance
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.Placed
import com.mdev.mnotes.core.model.PageSnapshot
import com.mdev.mnotes.core.model.PageSpace
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.pen.ActivePen
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.pen.PenKit
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.TapeEl
import com.mdev.mnotes.data.Bookmark
import com.mdev.mnotes.core.search.TextSearch
import com.mdev.mnotes.core.stats.ActiveTime
import com.mdev.mnotes.core.export.TikzExport
import com.mdev.mnotes.data.NotebookDoc
import com.mdev.mnotes.data.PageState
import com.mdev.mnotes.data.newId
import com.mdev.mnotes.render.Assets
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

enum class Tool {
    PEN, ERASER, LASSO, TEXT, LASER, HAND, SPACE, TAPE, PAINTER, FILL, CURVE, GEO;

    /** Narzędzie pracuje aktywnym piórem (kolor, grubość), więc zmiana pióra z listwy go nie zmienia. */
    val usesPen: Boolean get() = this == PEN || this == PAINTER || this == FILL || this == CURVE

    /** Narzędzie po zmianie pióra z listwy ([Editor.tunePen]): gumka, lasso i reszta ustępują ostatniemu piszącemu. */
    fun afterTune(lastWrite: Tool): Tool = if (usesPen) this else lastWrite
}

class Sel(val pageId: String, val ids: Set<Int>)

/** Trwające przecinanie obrazu: linie czekają na zatwierdzenie, dopiero wtedy powstają kawałki (jedno cofnięcie). */
class CutSession(val pageId: String, val imageId: Int) {
    val lines = mutableStateListOf<CutLine>()
    var spread by mutableStateOf(true)
}

/** Proporcje ramki przycinania; [ratio] to szerokość/wysokość pliku w poziomie (null: dowolne). */
enum class CropRatio(val ratio: Float?) { FREE(null), ORIGINAL(null), SQUARE(1f), R43(4f / 3f), R32(3f / 2f), R169(16f / 9f) }

/**
 * Trwające przycinanie obrazu. Ramka stoi na stronie w jednym miejscu i pod jednym kątem, a plik pod nią się przesuwa
 * i obraca. Dopiero zatwierdzenie zapisuje wycinek w obrazie (jedno cofnięcie), anulowanie niczego nie zmienia.
 * [iw], [ih] to wymiary pliku po obrocie z EXIF; [want] to ramka, jakiej chce użytkownik, zanim prostowanie ją zmniejszy.
 */
class CropSession(val pageId: String, val imageId: Int, val original: ImageEl, val iw: Float, val ih: Float, frame: CropFrame, place: CropPlace) {
    var frame by mutableStateOf(frame)
    var place by mutableStateOf(place)
    var want: CropFrame = frame
    var ratio by mutableStateOf(CropRatio.FREE)
    /** Czy proporcje są odwrócone (pion zamiast poziomu). */
    var portrait by mutableStateOf(false)
    /** Ćwierć obrotu, wokół której działa suwak prostowania (0..3); zmieniają ją tylko przyciski 90°. */
    var quarter by mutableStateOf(CropGeom.quarterOf(frame.a))

    /** Szerokość/wysokość ramki, jaką wymusza wybrany wybór, albo null. */
    val lockedRatio: Float?
        get() {
            val base = if (ratio == CropRatio.ORIGINAL) iw / ih else ratio.ratio ?: return null
            return if (portrait) 1f / base else base
        }
}

class TextRequest(val pageId: String, val existing: TextBox?, val x: Float, val y: Float, val initial: String = "")

/** Prosty schowek wewnętrzny (elementy + zeszyt, z którego pochodzą — dla kopiowania zasobów obrazów). */
object Clip {
    var nb: String = ""
    var els: List<Element> = emptyList()

    /** Skopiowana strona (PAG-008) i zeszyt źródłowy — do kopiowania obrazów oraz tła PDF przy wklejaniu do innego zeszytu. */
    var pageNb: String = ""
    var page: Pair<PageSpec, PageContent>? = null
}

/**
 * Kontroler edytora jednego zeszytu: stan narzędzi dla UI (Compose) i wszystkie operacje na dokumencie.
 * Warstwa wejścia/rysowania (NoteView) pyta go o stan i woła jego operacje.
 */
class Editor(val ctx: Context, val nbId: String) {
    val app = ctx.applicationContext as MNotesApp
    val repo = app.repo
    val settings = app.settings
    val doc = NotebookDoc(repo, nbId)
    var view: NoteView? = null

    /** Układ stron tego dokumentu ([com.mdev.mnotes.core.layout.ViewMode]): własny wybór zapisany w zeszycie albo domyślny dla jego rodzaju. */
    var layoutMode by mutableIntStateOf(doc.meta.let { ViewMode.resolve(it.viewMode, it.pdfDoc, settings.viewNotebook, settings.viewPdf) })
        private set

    // — Stanowisko (2b-A7, dawniej dwa zeszyty F4) —
    /** Stanowisko, do którego należy ten edytor (główny zeszyt albo dołożony rząd); null poza edytorem. */
    var station: Station? = null
    private var rowSaved: Int? = null

    /**
     * 2d-A1: na stanowisku wszystkie dokumenty mają jeden kierunek ([mode]: 0 kolumny, 1 rzędy), ale wybór nie trafia do
     * zeszytu — po zamknięciu dołożonych dokumentów ([mode] = null) wraca układ sprzed.
     */
    fun forceLayout(mode: Int?) {
        if (mode != null) { if (rowSaved == null) rowSaved = layoutMode; layoutMode = mode }
        else { rowSaved?.let { layoutMode = it }; rowSaved = null }
        view?.applyLayoutMode()
    }

    /** Własny układ dokumentu (ten sprzed stanowiska). */
    val ownLayoutMode: Int get() = rowSaved ?: layoutMode

    /** Zmiana układu przez użytkownika: pamiętana w tym dokumencie, więc przy następnym otwarciu wraca. */
    fun chooseLayoutMode(mode: Int) {
        if (!ViewMode.valid(mode)) return
        // 2d-A1: na stanowisku menu „Układ” zmienia kierunek całego stanowiska (strona po stronie = rzędy), nie zeszyt.
        station?.takeIf { it.on }?.let { it.setDirection(mode != 0); return }
        layoutMode = mode
        doc.setViewMode(mode)
        view?.applyLayoutMode()
    }

    private var toolState by mutableStateOf(Tool.PEN)
    var tool: Tool
        get() = toolState
        // 2b-A10: odejście od Krzywej zapisuje rozpoczętą krzywą (punkty postawione palcem się nie gubią).
        set(v) { if (toolState == Tool.CURVE && v != Tool.CURVE) curveCommit(); toolState = v }
    var ruler by mutableStateOf(false)
    /** Okno powiększenia (EXT-003): pisanie w powiększonej ramce u dołu, zapis na stronie w skali 1:1. */
    var zoomWin by mutableStateOf(false)
    fun toggleZoomWin() { zoomWin = !zoomWin; view?.zoomWinChanged() }
    /** Pipeta: gdy niepusta, następne dotknięcie strony pobiera kolor i przekazuje go do tej funkcji. */
    var pipette by mutableStateOf<((Int) -> Unit)?>(null)
    /** Pędzel formatu w trybie „Pobierz format”: następne dotknięcie obiektu ustawia aktywne pióro według niego. */
    var formatPick by mutableStateOf(false)
    /** Szybki przycisk „Pobierz format” pod ikoną pędzla: pokazuje go wybranie pędzla, gaszą go użycie, czas i zmiana narzędzia. */
    var quickFormat by mutableStateOf(false)
    var sel by mutableStateOf<Sel?>(null)
    var cut by mutableStateOf<CutSession?>(null)
    var crop by mutableStateOf<CropSession?>(null)
    var selTick by mutableIntStateOf(0)
    /** Uchwyty skali i obrotu są widoczne i aktywne tylko po włączeniu przyciskiem na pasku zaznaczenia; nowe zaznaczenie je wyłącza. */
    var transformMode: Boolean
        get() = transformModeState
        set(v) { if (transformModeState != v) { transformModeState = v; view?.invalidate() } }
    private var transformModeState by mutableStateOf(false)
    var undoTick by mutableIntStateOf(0)
    var structTick by mutableIntStateOf(0)
    var pageIndex by mutableIntStateOf(0)
    var pageCount by mutableIntStateOf(doc.pages.size)
    var zoomPct by mutableIntStateOf(100)
    var viewTick by mutableIntStateOf(0)
    var readOnly by mutableStateOf(false)
    var chrome by mutableStateOf(true)
    var textReq by mutableStateOf<TextRequest?>(null)
    var exportOpen by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    var ctxMenu by mutableStateOf<CtxMenu?>(null)
    var gridSnap by mutableStateOf(false)

    // — Szukanie w zeszycie (Ctrl+F) —
    class SearchHit(val pageId: String, val pageIndex: Int, val box: Bounds?, val snippet: String)
    var searchOpen by mutableStateOf(false)
    var searchFocusTick by mutableIntStateOf(0)
    fun openSearch() { searchOpen = true; searchFocusTick++ }
    var searchQ by mutableStateOf("")
    var searchHits by mutableStateOf<List<SearchHit>>(emptyList())
    var searchCur by mutableIntStateOf(0)
    var searchBusy by mutableStateOf(false)

    init { app.pendingSearch?.let { searchQ = it; searchOpen = true; app.pendingSearch = null } }

    fun pagesSnapshot(): List<PageState> = ArrayList(doc.pages)
    fun bookmarksSnapshot(): List<Bookmark> = repo.meta(nbId)?.bookmarks ?: emptyList()
    fun pageTagsSnapshot(): Map<String, List<String>> = repo.meta(nbId)?.pageTags ?: emptyMap()

    /**
     * Fraza (bez wielkości liter i diakrytyków) w polach tekstowych i nazwach zakładek; zapytanie z „#” szuka stron
     * z tagami (FB8, [PageTags.matchesPage]). Wołać poza wątkiem UI.
     */
    fun runSearch(q: String, pages: List<PageState>, bookmarks: List<Bookmark>, tags: Map<String, List<String>> = emptyMap()): List<SearchHit> {
        val phrase = TextSearch.fold(q.trim())
        if (phrase.isEmpty()) return emptyList()
        val out = ArrayList<SearchHit>()
        val tq = PageTags.query(q)
        if (tq.isTag) {
            for ((i, p) in pages.withIndex()) {
                val pt = tags[p.id].orEmpty()
                val text = TextSearch.fold(TextSearch.pageText(doc.peekContent(p)))
                if (PageTags.matchesPage(pt, text, tq)) out.add(SearchHit(p.id, i, null, PageTags.label(pt)))
            }
            return out
        }
        for ((i, p) in pages.withIndex()) {
            for (b in bookmarks) if (b.pageId == p.id && TextSearch.fold(b.title).contains(phrase)) out.add(SearchHit(p.id, i, null, b.title))
            for (tb in TextSearch.textBoxes(doc.peekContent(p))) {
                val at = TextSearch.fold(tb.text).indexOf(phrase)
                if (at >= 0) out.add(SearchHit(p.id, i, tb.bounds, TextSearch.snippet(tb.text, at, phrase.length)))
            }
        }
        return out
    }

    fun closeSearch() { searchOpen = false; searchHits = emptyList(); view?.invalidate() }

    fun showSearchHit() { searchHits.getOrNull(searchCur)?.let { view?.showHit(it.pageIndex, it.box) }; view?.invalidate() }

    fun searchStep(d: Int) {
        val n = searchHits.size
        if (n == 0) return
        searchCur = ((searchCur + d) % n + n) % n
        showSearchHit()
    }

    class CtxMenu(val x: Float, val y: Float, val pageIndex: Int, val hasSel: Boolean, val lx: Float, val ly: Float)

    val canUndo: Boolean get() { undoTick; return doc.undo.canUndo }
    val canRedo: Boolean get() { undoTick; return doc.undo.canRedo }

    // — Pióra —
    val kit: PenKit get() = settings.penKit

    /** Pióro, którym się teraz pisze: aktywne narzędzie ze swoim kolorem, grubością i stylem. */
    val pen: ActivePen get() = kit.current

    fun editKit(f: (PenKit) -> PenKit) { settings.penKit = f(kit) }

    /** Narzędzia, które biorą kolor i styl z aktywnego pióra (pióro, pędzel formatu, wypełnienie). */
    val penTool: Boolean get() = tool.usesPen

    /** Wybór narzędzia piszącego z paska: staje się aktywnym piórem i narzędziem, a zaznaczenie znika. */
    fun selectPen(kind: PenKind) {
        editKit { it.withActive(kind) }
        tool = Tool.PEN; clearSelIfNotLasso(); noteTool(Tool.PEN)
    }

    /** Pobiera format obiektu do aktywnego pióra; false, gdy obiekt (obraz, taśma) nie ma formatu. */
    fun takeFormat(e: Element): Boolean {
        when (e) {
            is Stroke -> editKit { it.withTaken(e.pen, e.color, e.width, e.line) }
            is FillEl -> editKit { it.withColor(it.active, e.color) }
            is TextBox -> editKit { it.withColor(it.active, e.color) }
            else -> return false
        }
        return true
    }

    fun setPenColor(c: Int) = editKit { it.withColor(it.active, c) }

    /** Zmiana z listwy (grubość, kolor): gdy w ręku jest gumka albo lasso, wraca do pisania, bo po to się ją dotyka. */
    fun tunePen(f: (PenKit) -> PenKit) {
        editKit(f)
        // 2b-A2: wraca do ostatniego narzędzia pisania (także krzywej), a nie zawsze do pióra.
        if (tool.afterTune(lastWriteTool) != tool) returnToWriting()
    }

    fun say(msg: String) { toast = msg }

    /** Przyciski pod dymkiem (np. „Cofnij do oryginału”, 2c-B4): ważne tylko dla komunikatu, z którym przyszły. */
    var toastActions by mutableStateOf<Pair<String, List<Pair<String, () -> Unit>>>?>(null)
        private set

    /** Dymek z przyciskami; wisi dłużej niż zwykły, przycisk zamyka go i robi swoje. */
    fun say(msg: String, actions: List<Pair<String, () -> Unit>>) { toast = msg; toastActions = msg to actions }

    // — Cofanie —
    // 2c-A1: cofnięcie wzrostu tablicy przesuwa treść z powrotem, więc widok idzie za nią (bez skoku na ekranie)
    /** Cofnięcie; przeniesienie między dokumentami stanowiska cofa się w obu naraz (2c-A5). */
    fun undo() { val st = station; if (st == null) undoHere() else st.linked.undo(this, st::stackOf) { it.undoHere() } }
    fun redo() { val st = station; if (st == null) redoHere() else st.linked.redo(this, st::stackOf) { it.redoHere() } }
    private fun undoHere() { commitSelPreview(); cancelCut(); cancelCrop(); clearSel(); val e = doc.doUndo(); undoTick++; e?.let { DocEdit.shiftOf(it) }?.let { (dx, dy) -> view?.boardShifted(-dx, -dy) }; e?.pageIds?.firstOrNull()?.let { view?.ensureVisible(it) } }
    private fun redoHere() { commitSelPreview(); cancelCut(); cancelCrop(); clearSel(); val e = doc.doRedo(); undoTick++; e?.let { DocEdit.shiftOf(it) }?.let { (dx, dy) -> view?.boardShifted(dx, dy) }; e?.pageIds?.firstOrNull()?.let { view?.ensureVisible(it) } }

    /** Wykonanie z zapisem na stosie cofania. */
    fun exec(edit: DocEdit) {
        val follow = liveFollow(edit, after = false)
        val grown = boardGrowth(edit, follow)
        doc.exec(grown?.first ?: follow?.let { DocEdit.Group(listOf(edit, it)) } ?: edit); undoTick++
        grown?.second?.let { (dx, dy) -> view?.boardShifted(dx, dy) }
    }

    // — Żywa konstrukcja (NAU-3) —
    /** Z czego powstały wyniki Konstrukcji i Geometrii w tej sesji; w pliku zostają zwykłe elementy. */
    val live = com.mdev.mnotes.core.geom.Live()

    /**
     * Przeliczone wyniki konstrukcji po edycji [edit] ([after]: edycja już wykonana, jak w [record]); idą w tym samym
     * wpisie cofania co zmiana ucznia, więc jedno cofnięcie wraca wejście i wynik naraz.
     */
    private fun liveFollow(edit: DocEdit, after: Boolean): DocEdit.Elements? {
        if (edit !is DocEdit.Elements || live.links(edit.pageId).isEmpty()) return null
        val page = doc.page(edit.pageId) ?: return null
        val cur = doc.content(page)
        val (before, next) = if (after) cur.apply(edit.edit.inverse()) to cur else cur to cur.apply(edit.edit)
        return live.follow(page.id, before, next)?.let { DocEdit.Elements(page.id, it) }
    }

    /** Wynik konstrukcji wypada daleko poza stroną [pageId] — przeliczenie go nie wstawia (jak [constructed]). */
    private fun liveTooFar(pageId: String, els: List<Element>): Boolean {
        val spec = doc.page(pageId)?.spec ?: return true
        return ConstructOps.tooFar(els, spec.w, spec.h)
    }

    /**
     * 2b-A13: zmiana treści tablicy blisko brzegu dokłada płótna w tym samym wpisie cofania; przy wzroście w lewo/w górę
     * cała treść przesuwa się o (dx, dy) — wtedy zwraca też to przesunięcie, żeby widok poszedł za treścią.
     */
    private fun boardGrowth(edit: DocEdit, follow: DocEdit.Elements? = null): Pair<DocEdit, Pair<Float, Float>?>? {
        if (edit !is DocEdit.Elements) return null
        val page = doc.page(edit.pageId) ?: return null
        if (!page.spec.board) return null
        val after = doc.content(page).apply(edit.edit).let { c -> follow?.let { c.apply(it.edit) } ?: c }
        val (parts, shift) = boardParts(page, after) ?: return null
        return DocEdit.Group(listOfNotNull(edit, follow) + parts) to shift
    }

    /** Edycje powiększające tablicę [page] dla treści [after] (rozmiar, ewentualnie przesunięcie całej treści). */
    private fun boardParts(page: com.mdev.mnotes.data.PageState, after: com.mdev.mnotes.core.model.PageContent): Pair<List<DocEdit>, Pair<Float, Float>?>? {
        val parts = com.mdev.mnotes.core.layout.Board.growthEdits(page.id, page.spec, after.bounds) ?: return null
        return parts to DocEdit.shiftOf(DocEdit.Group(parts))
    }

    /** Edycja już wykonana (przeciąganie lassem, zapis przez [record]): tablica rośnie osobnym wpisem cofania. */
    private fun boardAfter(edit: DocEdit) {
        val pid = when (edit) {
            is DocEdit.Elements -> edit.pageId
            is DocEdit.Group -> edit.edits.firstNotNullOfOrNull { (it as? DocEdit.Elements)?.pageId }
            else -> null
        } ?: return
        val page = doc.page(pid) ?: return
        if (!page.spec.board) return
        val (parts, shift) = boardParts(page, doc.content(page)) ?: return
        doc.exec(DocEdit.Group(parts)); undoTick++
        shift?.let { (dx, dy) -> view?.boardShifted(dx, dy) }
    }
    fun record(edit: DocEdit) {
        val follow = liveFollow(edit, after = true)
        follow?.apply(doc)
        doc.record(follow?.let { DocEdit.Group(listOf(edit, it)) } ?: edit); undoTick++; boardAfter(edit)
    }

    // — Zaznaczenie —
    fun clearSel() { if (sel != null) { sel = null; transformMode = false; pointEdit = false; selTick++ } }

    fun select(pageId: String, ids: Set<Int>) {
        val old = sel
        if (old == null || old.pageId != pageId || old.ids != ids) { transformMode = false; pointEdit = false }
        sel = if (ids.isEmpty()) null else Sel(pageId, ids)
        selTick++
    }

    fun selElements(): List<Element> {
        val s = sel ?: return emptyList()
        val p = doc.page(s.pageId) ?: return emptyList()
        return doc.content(p).elements.filter { it.id in s.ids }
    }

    private fun selPage() = sel?.let { doc.page(it.pageId) }

    /** Dodaje [els] na stronę bieżącego zaznaczenia (na wierzch) i zaznacza je; jeden wpis cofania. */
    fun addOnSelPage(els: List<Element>) {
        val p = selPage() ?: return
        if (els.isEmpty()) return
        exec(DocEdit.Elements(p.id, ElementsEdit.add(doc.content(p), els)))
        select(p.id, els.map { it.id }.toSet())
    }

    fun deleteSel() {
        val s = sel ?: return; val p = selPage() ?: return
        exec(DocEdit.Elements(p.id, ElementsEdit.remove(doc.content(p), s.ids)))
        clearSel()
    }

    fun duplicateSel() {
        val s = sel ?: return; val p = selPage() ?: return
        val copies = selElements().map { dup(it, 14f, 14f) }
        if (copies.isEmpty()) return
        exec(DocEdit.Elements(p.id, ElementsEdit.add(doc.content(p), copies)))
        select(p.id, copies.map { it.id }.toSet())
    }

    fun copySel(): Boolean {
        val els = selElements()
        if (els.isEmpty()) return false
        Clip.nb = nbId; Clip.els = els
        say(T.t("Skopiowano", "Copied"))
        return true
    }

    fun cutSel() { if (copySel()) deleteSel() }

    /** [at] = (indeks strony, x, y) miejsca wklejenia (np. punkt przytrzymania); domyślnie środek widoku. */
    fun paste(at: Triple<Int, Float, Float>? = null) {
        if (Clip.els.isEmpty()) { pasteSystem(at); return }
        pasteElements(Clip.els, Clip.nb, at)
    }

    /**
     * Wkleja [els] z zeszytu [fromNb] środkiem w [at] (indeks strony, x, y; domyślnie środek widoku), z kopiami obrazów
     * i nowymi id; zaznacza wynik. Też przeniesienie zaznaczenia z innego rzędu stanowiska (2b-A7).
     */
    fun pasteElements(els: List<Element>, fromNb: String, at: Triple<Int, Float, Float>? = null): DocEdit? {
        val v = view ?: return null
        val (pi, cx, cy) = at ?: v.viewCenterOnPage() ?: return null
        val page = doc.pages.getOrNull(pi) ?: return null
        var b = Bounds.EMPTY
        for (e in els) b = b.union(e.bounds)
        var dx = cx - b.cx; var dy = cy - b.cy
        // 3c-A2: wklejone albo upuszczone z innego dokumentu leży całe na stronie (wystające strona przycinała, jakby
        // obiekt był pod nią); tablica rośnie sama, więc tam bez dosuwania.
        if (!page.spec.board) {
            val (fx, fy) = com.mdev.mnotes.core.layout.PageDrop.pushInside(Bounds(b.l + dx, b.t + dy, b.r + dx, b.b + dy), page.spec.w, page.spec.h)
            dx += fx; dy += fy
        }
        val copies = els.map { e ->
            var n = dup(e, dx, dy)
            if (n is ImageEl && fromNb != nbId) {
                val src = repo.assetFile(fromNb, n.asset)
                if (src.exists()) {
                    val name = newId() + "." + src.extension.ifEmpty { "jpg" }
                    runCatching { src.copyTo(repo.assetFile(nbId, name), true) }
                    n = n.copy(asset = name)
                }
            }
            n
        }
        exec(DocEdit.Elements(page.id, ElementsEdit.add(doc.content(page), copies)))
        select(page.id, copies.map { it.id }.toSet())
        // Na stosie leży krok po ewentualnym dołożeniu płótna tablicy, nie sam „add”.
        return doc.undo.top
    }

    private fun dup(e: Element, dx: Float, dy: Float): Element =
        e.transformed(com.mdev.mnotes.core.geom.Affine.translation(dx, dy)).withId(Ids.next())

    private fun replaceSel(f: (Element) -> Element) {
        val s = sel ?: return; val p = selPage() ?: return
        val map = selElements().associate { it.id to f(it) }
        exec(DocEdit.Elements(p.id, ElementsEdit.replace(doc.content(p), map)))
        selTick++
    }

    fun recolorSel(color: Int) = replaceSel { e ->
        when (e) { is Stroke -> e.withStyle(color = if (e.pen == PenKind.HIGHLIGHTER) com.mdev.mnotes.ui.softenForHighlighter(color) else color); is TextBox -> e.copy(color = color); is FillEl -> e.copy(color = if (e.hl) com.mdev.mnotes.ui.softenForHighlighter(color) else color); else -> e }
    }

    fun setSelWidth(w: Float) = replaceSel { e -> if (e is Stroke) e.withStyle(width = w) else e }

    fun setSelLine(line: LineStyle) = replaceSel { e -> if (e is Stroke) e.withStyle(line = line) else e }

    /** Zmienia efekty kolorystyczne zaznaczonych obrazów; [f] dostaje bieżące efekty danego obrazu. */
    fun setSelImageFx(f: (ImageFx) -> ImageFx) = replaceSel { e -> if (e is ImageEl) e.copy(fx = f(e.fx)) else e }

    /**
     * Podmienia plik zaznaczonego obrazu [imageId] na nowy PNG (np. oczyszczona tablica w wariancie lab): środek i
     * szerokość zostają, wysokość idzie za proporcjami nowego pliku, wycinek i efekty się zerują. Jeden wpis cofania.
     */
    fun replaceSelImageAsset(imageId: Int, png: ByteArray, pxW: Int, pxH: Int, onDone: (Boolean) -> Unit = {}) {
        val v = view ?: return onDone(false)
        repo.io.execute {
            val name = runCatching { repo.importAsset(nbId, png.inputStream(), "png") }.getOrNull()
            v.post {
                if (name == null || pxW <= 0 || pxH <= 0) { say(T.t("Nie udało się zapisać obrazu", "Could not save the image")); onDone(false); return@post }
                replaceSel { e -> if (e is ImageEl && e.id == imageId) e.copy(asset = name, h = e.w * pxH / pxW, crop = ImageCrop.FULL, fx = ImageFx.NONE) else e }
                onDone(true)
            }
        }
    }

    /**
     * F14: wycinek strony pod zaznaczeniem jako obraz — z papierem, wzorem, tłem PDF i wszystkim, co leży w ramce.
     * [toClipboard] = PNG do schowka systemu (wklejanie w innych aplikacjach), inaczej nowy obraz obok zaznaczenia.
     */
    fun snipSel(toClipboard: Boolean) {
        val p = selPage() ?: return
        var b = Bounds.EMPTY
        for (e in selElements()) b = b.union(e.bounds)
        val plan = Snip.plan(b, p.spec.w, p.spec.h) ?: return
        val spec = p.spec
        val content = doc.content(p)
        val pageId = p.id
        repo.io.execute {
            val png = renderPng(spec, content, plan)
            if (png == null) { view?.post { say(T.t("Nie udało się utworzyć wycinka", "Could not create the snip")) }; return@execute }
            if (toClipboard) {
                val ok = pngToClipboard(png, "wycinek")
                view?.post { say(if (ok) T.t("Wycinek skopiowany jako obraz", "Snip copied as an image") else T.t("Nie udało się skopiować wycinka", "Could not copy the snip")) }
                return@execute
            }
            val name = runCatching { repo.importAsset(nbId, java.io.ByteArrayInputStream(png), "png") }.getOrNull()
            view?.post {
                val page = doc.page(pageId) ?: return@post
                if (name == null) { say(T.t("Nie udało się zapisać wycinka", "Could not save the snip")); return@post }
                val at = Snip.placement(plan.rect, page.spec.w, page.spec.h)
                val el = ImageEl(Ids.next(), name, at[0], at[1], plan.rect.w, plan.rect.h)
                exec(DocEdit.Elements(page.id, ElementsEdit.add(doc.content(page), listOf(el))))
                tool = Tool.LASSO
                select(page.id, setOf(el.id))
            }
        }
    }

    /**
     * FB6: strona [i] jako PNG do schowka systemu (wklejenie do czatu grupy albo pracy bez eksportu pliku). Strona w
     * oryginale, jak w eksporcie; tablica w kadrze treści.
     */
    fun copyPageAsImage(i: Int) {
        val p = doc.pages.getOrNull(i) ?: return
        val spec = p.spec
        val content = doc.content(p)
        val plan = Snip.pagePlan(spec.w, spec.h, spec.board, content.bounds)
            ?: return say(T.t("Tablica jest pusta — nie ma czego kopiować", "The board is empty — nothing to copy"))
        repo.io.execute {
            val ok = renderPng(spec, content, plan)?.let { pngToClipboard(it, "strona-${i + 1}") } ?: false
            view?.post { say(if (ok) T.t("Strona ${i + 1} skopiowana jako obraz", "Page ${i + 1} copied as an image") else T.t("Nie udało się skopiować strony", "Could not copy the page")) }
        }
    }

    /** Prostokąt strony z planu [plan] jako PNG: papier, wzór, tło PDF i elementy. Wołane z wątku tła; null przy błędzie. */
    private fun renderPng(spec: PageSpec, content: PageContent, plan: Snip.Plan): ByteArray? = runCatching {
        val bmp = android.graphics.Bitmap.createBitmap(plan.wPx, plan.hPx, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val r = plan.rect; val sc = plan.scale
        c.scale(sc, sc); c.translate(-r.l, -r.t)
        val renderer = com.mdev.mnotes.render.PageRenderer(app.assets)
        renderer.drawPaper(c, spec, sc, null)
        spec.pdf?.let { pdf ->
            app.pdf.renderTile(nbId, pdf.asset, pdf.page, sc, Math.round(r.l * sc), Math.round(r.t * sc), plan.wPx, plan.hPx)?.let { bg ->
                c.drawBitmap(bg, null, android.graphics.RectF(r.l, r.t, r.l + plan.wPx / sc, r.t + plan.hPx / sc), null)
            }
        }
        renderer.drawElements(c, nbId, content, 0, r, sc, true, spec.darkPaper)
        java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it); bmp.recycle() }.toByteArray()
    }.getOrNull()

    /** PNG do schowka przez FileProvider: inne aplikacje wklejają obraz, a nie ścieżkę. Wołane z wątku tła. */
    /** FB13: zaznaczenie jako kod TikZ w schowku (tekst, więc wkleja się w każdym edytorze LaTeX-a, też w przeglądarce). */
    fun copySelAsTikz() {
        val els = selElements()
        if (els.isEmpty()) return
        val r = TikzExport.picture(els)
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(clip(android.content.ClipData.newPlainText("TikZ", r.code)))
        say(
            if (r.skipped > 0) T.t("Kod TikZ w schowku (pominięte: ${r.skipped}) — wklej do LaTeX-a", "TikZ code copied (skipped: ${r.skipped}) — paste into LaTeX")
            else T.t("Kod TikZ w schowku — wklej do LaTeX-a", "TikZ code copied — paste into LaTeX"),
        )
    }

    private fun pngToClipboard(png: ByteArray, name: String): Boolean = runCatching {
        // obrazy ze schowka zostawały w cache/share do następnego eksportu (dostępne pod starym adresem schowka)
        val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
        val old = System.currentTimeMillis() - 24 * 3600_000L
        dir.listFiles()?.filter { it.name.endsWith(".png") && it.lastModified() < old }?.forEach { it.delete() }
        val f = java.io.File(dir, "$name-${System.currentTimeMillis()}.png").apply { writeBytes(png) }
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        view?.post { cm.setPrimaryClip(clip(android.content.ClipData.newUri(ctx.contentResolver, "mNotes", uri))) }
    }.isSuccess

    /** Przy blokadzie aplikacji system nie pokazuje treści schowka w podglądzie (Android 13+): notatka jest prywatna. */
    private fun clip(c: android.content.ClipData) = c.apply {
        if (settings.appLock && android.os.Build.VERSION.SDK_INT >= 33) {
            description.extras = android.os.PersistableBundle().apply { putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
    }

    /**
     * F15: autobalans bieli zaznaczonych obrazów — piksele pomniejszonego obrazu (najwyżej 256 px) liczone w tle,
     * wynik jako wzmocnienia R/G/B (jeden wpis cofania; dalej da się je poprawić suwakami).
     */
    fun autoWhiteBalanceSel(whitePatch: Boolean) {
        val imgs = selElements().filterIsInstance<ImageEl>()
        if (imgs.isEmpty()) return
        repo.io.execute {
            val gains = imgs.associate { im ->
                val bmp = app.assets.load(nbId, im.asset, 256)
                val g = if (bmp == null) null else {
                    val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                    bmp.recycle()
                    if (whitePatch) WhiteBalance.whitePatch(px) else WhiteBalance.grayWorld(px)
                }
                im.id to g
            }
            view?.post {
                replaceSel { e -> val g = gains[e.id]; if (e is ImageEl && g != null) e.copy(fx = e.fx.copy(mode = ImageFx.MODE_COLOR, r = g[0], g = g[1], b = g[2])) else e }
                if (gains.values.all { it == null }) say(T.t("Nie udało się odczytać obrazu", "Could not read the image"))
            }
        }
    }

    private class ElPreview(val pageId: String, val before: Map<Int, ImageEl>)
    private var elPreview: ElPreview? = null

    /**
     * Podgląd zmiany obrazów na żywo (suwaki efektów): stosuje [f] do stanu sprzed gestu bez wpisu cofania i bez
     * przemalowania kafelków na wątku UI. Gest kończy [commitSelPreview] (jeden wpis cofania na cały gest).
     */
    fun previewSelImages(f: (ImageEl) -> ImageEl) {
        val p = selPage() ?: return
        val prev = elPreview ?: ElPreview(p.id, selElements().filterIsInstance<ImageEl>().associateBy { it.id }).also { elPreview = it }
        if (prev.pageId != p.id || prev.before.isEmpty()) return
        val map = prev.before.mapValues { f(it.value) }
        val edit = ElementsEdit.replace(doc.content(p), map)
        if (edit.isEmpty) return
        val v = view
        if (v != null) v.applyElementsInBackground(p.id, map.values.toList()) { doc.applyElements(p.id, edit) }
        else doc.applyElements(p.id, edit)
    }

    fun commitSelPreview() {
        val prev = elPreview ?: return
        elPreview = null
        val p = doc.page(prev.pageId) ?: return
        val rem = ArrayList<Placed>(); val add = ArrayList<Placed>()
        doc.content(p).elements.forEachIndexed { i, e ->
            val b = prev.before[e.id] ?: return@forEachIndexed
            if (b != e && (e !is ImageEl || b.fx != e.fx || b.alpha != e.alpha)) { rem.add(Placed(i, b)); add.add(Placed(i, e)) }
        }
        if (rem.isNotEmpty()) record(DocEdit.Elements(p.id, ElementsEdit(rem, add)))
        selTick++
    }

    fun lockSel(lock: Boolean) = replaceSel { it.withLocked(lock) }

    fun frontSel() {
        val s = sel ?: return; val p = selPage() ?: return
        exec(DocEdit.Elements(p.id, ElementsEdit.bringToFront(doc.content(p), s.ids)))
    }

    fun backSel() {
        val s = sel ?: return; val p = selPage() ?: return
        exec(DocEdit.Elements(p.id, ElementsEdit.sendToBack(doc.content(p), s.ids)))
    }

    fun selectAllOnPage() {
        val v = view ?: return
        val p = doc.pages.getOrNull(pageIndex) ?: return
        select(p.id, doc.content(p).elements.filter { !it.locked }.map { it.id }.toSet())
        tool = Tool.LASSO
        v.invalidate()
    }

    fun alignSel(mode: Int) {
        val s = sel ?: return; val p = selPage() ?: return
        val els = selElements(); if (els.size < 2) return
        var b = Bounds.EMPTY
        for (e in els) b = b.union(e.bounds)
        val map = els.associate { e ->
            val eb = e.bounds
            val (dx, dy) = when (mode) {
                0 -> (b.l - eb.l) to 0f
                1 -> (b.cx - eb.cx) to 0f
                2 -> (b.r - eb.r) to 0f
                3 -> 0f to (b.t - eb.t)
                4 -> 0f to (b.cy - eb.cy)
                else -> 0f to (b.b - eb.b)
            }
            e.id to e.transformed(com.mdev.mnotes.core.geom.Affine.translation(dx, dy))
        }
        exec(DocEdit.Elements(p.id, ElementsEdit.replace(doc.content(p), map)))
        selTick++
    }

    /** Przesuwa zaznaczenie o (dx, dy) pt — strzałki (Shift = 10 pt). */
    fun nudgeSel(dx: Float, dy: Float) = replaceSel { it.transformed(com.mdev.mnotes.core.geom.Affine.translation(dx, dy)) }

    /** Czy zaznaczenie da się odbić: jak przy obrocie, żaden zaznaczony element nie może być zablokowany. */
    fun canFlipSel(): Boolean = !readOnly && selElements().let { it.isNotEmpty() && it.none { e -> e.locked } }

    /**
     * Odbija zaznaczenie jak jedną całość: względem pionowej ([horizontal]) albo poziomej osi przez środek wspólnej ramki.
     * Jedna zmiana w historii; dwa odbicia dają dokładnie stan wyjściowy.
     */
    fun flipSel(horizontal: Boolean) {
        if (!canFlipSel()) return
        var b = Bounds.EMPTY
        for (e in selElements()) b = b.union(e.bounds)
        if (b.isEmpty) return
        val m = if (horizontal) com.mdev.mnotes.core.geom.Affine.scale(-1f, 1f, b.cx, b.cy) else com.mdev.mnotes.core.geom.Affine.scale(1f, -1f, b.cx, b.cy)
        replaceSel { it.transformed(m) }
    }

    // — Tekst i obrazy —
    private val textMeasure by lazy { com.mdev.mnotes.render.PageRenderer(app.assets).textMeasure }

    /**
     * Auto-rozmiar zaznaczonego pola: włącza go (pole rozciąga się do tekstu, najwyżej do prawej krawędzi strony)
     * albo, gdy już działa, zamraża obecną szerokość. Cofanie przywraca poprzedni rozmiar i tryb.
     */
    fun toggleTextFit() {
        val p = selPage() ?: return
        val spec = p.spec
        replaceSel { e ->
            if (e is TextBox) com.mdev.mnotes.core.text.TextFit.withFit(e, if (e.fit == TextBox.FIT_AUTO) TextBox.FIT_HEIGHT else TextBox.FIT_AUTO, spec.w, spec.h, textMeasure) else e
        }
    }

    fun textMeasurer(): com.mdev.mnotes.core.text.TextMeasure = textMeasure

    fun newText(pageId: String, x: Float, y: Float) { textReq = TextRequest(pageId, null, x, y) }
    fun editText(pageId: String, t: TextBox) { textReq = TextRequest(pageId, t, t.cx, t.cy) }

    /** Zatwierdza okno edycji tekstu: nowy element albo podmiana istniejącego. [t] ma już wymiary zgodne z układem. */
    fun commitText(req: TextRequest, t: TextBox?) {
        textReq = null
        val p = doc.page(req.pageId) ?: return
        val content = doc.content(p)
        if (req.existing == null) {
            if (t == null || t.text.isBlank()) return
            exec(withOverflowPage(p, t, DocEdit.Elements(p.id, ElementsEdit.add(content, listOf(t)))))
            select(p.id, setOf(t.id))
        } else {
            if (t == null || t.text.isBlank()) { exec(DocEdit.Elements(p.id, ElementsEdit.remove(content, setOf(req.existing.id)))); clearSel() }
            else exec(withOverflowPage(p, t, DocEdit.Elements(p.id, ElementsEdit.replace(content, mapOf(req.existing.id to t)))))
        }
    }

    /**
     * F10: tekst, który wychodzi poza dół ostatniej strony, dostaje za nią nową, pustą stronę w tym samym stylu; obie
     * zmiany cofa jedno cofnięcie. Wyłączane w Ustawieniach („Nowa strona, gdy tekst wyjdzie za ostatnią”).
     */
    private fun withOverflowPage(p: PageState, t: TextBox, edit: DocEdit): DocEdit {
        val last = doc.pages.lastOrNull() ?: return edit
        if (!canAddPage) return edit   // tablica rośnie sama, drugiej strony nie dostaje
        if (!settings.autoTextPage || !PageOverflow.textOverflows(last.id == p.id, t.bounds.b, p.spec.h)) return edit
        say(T.t("Tekst wychodzi poza ostatnią stronę — dodano nową", "Text runs past the last page — a new page was added"))
        val snap = PageSnapshot(newId(), p.spec.copy(pdf = null), PageContent.EMPTY)
        return DocEdit.Group(listOf(edit, DocEdit.InsertPages(listOf(doc.pages.size to snap))))
    }

    /** F10: dotknięcie narzędziem Tekst tuż za ostatnią stroną zakłada nową stronę i tam zaczyna pole tekstowe. */
    fun newTextAfterEnd(x: Float, y: Float, layout: DocLayout) {
        if (refuseBoardPage()) return
        val last = doc.pages.lastOrNull() ?: return
        val spec = last.spec.copy(pdf = null)
        val at = PageOverflow.onNewPage(layout, x, y, spec.w, spec.h)
        val snap = PageSnapshot(newId(), spec, PageContent.EMPTY)
        exec(DocEdit.InsertPages(listOf(doc.pages.size to snap)))
        newText(snap.id, at[0], at[1])
    }

    /** Czy jest co wkleić: schowek wewnętrzny albo systemowy. */
    fun canPaste(): Boolean = Clip.els.isNotEmpty() ||
        ((ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.hasPrimaryClip() == true)

    /** Wkleja ze schowka systemu: obraz (URI) albo tekst jako nowe pole tekstowe — gdy schowek wewnętrzny jest pusty. */
    private fun pasteSystem(at: Triple<Int, Float, Float>? = null) {
        val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.primaryClip
        val item = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val uri = item?.uri
        val isImage = uri != null && (0 until clip.description.mimeTypeCount).any { clip.description.getMimeType(it).startsWith("image/") }
        if (isImage && uri != null) { if (insertFromUri(uri)) return }
        val text = runCatching { item?.coerceToText(ctx)?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
        val v = view
        val target = at ?: v?.viewCenterOnPage()
        if (text == null || target == null) { say(T.t("Schowek jest pusty", "Clipboard is empty")); return }
        val page = doc.pages.getOrNull(target.first) ?: return
        textReq = TextRequest(page.id, null, target.second - 120f, target.third - 10f, text)
    }

    /** Wstawia obraz wskazany przez URI (galeria, schowek, przeciągnięcie); zwraca false, gdy to nie obraz lub nie da się go otworzyć. */
    /** Następny wstawiany obraz zostanie podkładem bieżącej strony (PAG-009). */
    var imageAsBackground = false

    fun insertFromUri(uri: android.net.Uri): Boolean {
        val cr = ctx.contentResolver
        val mime = runCatching { cr.getType(uri) }.getOrNull().orEmpty()
        if (mime.isNotEmpty() && !mime.startsWith("image/")) return false
        val ext = when { "png" in mime -> "png"; "webp" in mime -> "webp"; "gif" in mime -> "gif"; else -> "jpg" }
        val input = runCatching { cr.openInputStream(uri) }.getOrNull() ?: return false
        insertImage(input, ext)
        return true
    }

    /** Wstawia obraz ze strumienia (poza wątkiem UI), na środku widoku. */
    fun insertImage(input: InputStream, ext: String, onDone: () -> Unit = {}) {
        val v = view ?: return
        val asBackground = imageAsBackground
        imageAsBackground = false
        val target = v.viewCenterOnPage() ?: return
        repo.io.execute {
            val name = runCatching { repo.importAsset(nbId, input, ext) }.getOrNull()
            val size = name?.let { Assets.imageSize(repo.assetFile(nbId, it)) }
            v.post {
                if (name == null || size == null) { say(T.t("Nie udało się wczytać obrazu", "Could not load the image")); return@post }
                val (pi, cx, cy) = target
                val page = doc.pages.getOrNull(pi) ?: return@post
                if (asBackground) {
                    // PAG-009: obraz wpisany w stronę, zablokowany i na samym spodzie (pod pismem)
                    val k = min(page.spec.w / size.first, page.spec.h / size.second)
                    val el = ImageEl(Ids.next(), name, page.spec.w / 2f, page.spec.h / 2f, size.first * k, size.second * k, locked = true)
                    val cur = doc.content(page)
                    exec(DocEdit.Elements(page.id, ElementsEdit.diff(cur, PageContent.of(listOf<Element>(el) + cur.elements))))
                    say(T.t("Ustawiono obraz jako tło strony (zablokowany; odblokuj w menu zaznaczenia)", "Image set as page background (locked; unlock from the selection menu)"))
                    onDone()
                    return@post
                }
                val maxW = page.spec.w * 0.6f; val maxH = page.spec.h * 0.6f
                val k = min(maxW / size.first, maxH / size.second)
                var el = ImageEl(Ids.next(), name, cx, cy, size.first * k, size.second * k)
                // Pomysł Fable 11 (opcja): zdjęcie od razu na węzłach kratki i szerokością w pełnych oczkach.
                val sp = page.spec.spacing
                if (settings.imageGridFit && page.spec.pdf == null && page.spec.template != Template.BLANK && sp > 0f) {
                    val r = GridFit.fit(el.bounds, el.w / el.h, sp, page.spec.w, page.spec.h)
                    el = el.copy(cx = r.cx, cy = r.cy, w = r.w, h = r.h)
                }
                exec(DocEdit.Elements(page.id, ElementsEdit.add(doc.content(page), listOf(el))))
                tool = Tool.LASSO
                select(page.id, setOf(el.id))
                com.mdev.mnotes.app.Flavor.lab?.imageInserted(this, el)
                onDone()
            }
        }
    }

    /** F27: wyrównanie wszystkich zaznaczonych pól tekstu (jedno cofnięcie). */
    fun setSelTextAlign(a: Int) = replaceSel { e -> if (e is TextBox && !e.locked) e.copy(align = a) else e }

    /** Ostatnie narzędzie piszące (pióro albo krzywa), do którego wraca [returnToWriting] (F26) i listwa (2b-A2). */
    private val writeMemory = com.mdev.mnotes.core.layout.LastOf(Tool.PEN) { it == Tool.PEN || it == Tool.CURVE }
    val lastWriteTool: Tool get() = writeMemory.value

    /** F26: odznaczenie narzędzia chwilowego (laser, ręka, lasso) — wraca do ostatniego narzędzia piszącego. */
    fun returnToWriting() { tool = lastWriteTool; clearSelIfNotLasso() }

    /** Zapamiętuje narzędzie piszące; wołane przy każdej zmianie narzędzia. */
    fun noteTool(t: Tool) = writeMemory.note(t)

    // — Tryb wykładu (pomysł Fable nr 1) —
    var lectureMode by mutableStateOf(false)
    /** Rośnie, gdy trzeba otworzyć aparat (kapsuła trybu wykładu go obserwuje). */
    var lectureShootTick by mutableIntStateOf(0)
    /** Ostatnie obsłużone żądanie zdjęcia: ponowne wejście ekranu w kompozycję nie otwiera aparatu drugi raz (2b-A14). */
    var lectureShootDone = 0
    private var lectureN = 0

    fun lectureFile(): java.io.File = java.io.File(ctx.cacheDir, "share/wyklad-${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }

    /** Zdjęcie z aparatu na nowej stronie za bieżącą: nagłówek z datą i numerem, zdjęcie u góry, miejsce na notatki niżej. */
    fun lectureShot(f: java.io.File) {
        val after = curIndex()
        repo.io.execute {
            val name = runCatching { f.inputStream().use { repo.importAsset(nbId, it, "jpg") } }.getOrNull()
            f.delete()
            val size = name?.let { com.mdev.mnotes.render.Assets.imageSize(repo.assetFile(nbId, it)) }
            view?.post {
                if (name == null || size == null) { say(T.t("Nie udało się wczytać zdjęcia", "Could not load the photo")); return@post }
                if (!canAddPage) return@post lectureOnBoard(name, size)
                val spec = (doc.pages.getOrNull(after)?.spec ?: PageSpec()).copy(pdf = null)
                val head = LecturePages.header(System.currentTimeMillis(), ++lectureN, T.pl)
                val m = LecturePages.MARGIN
                val title = TextBox(Ids.next(), spec.w / 2f, m + 9f, spec.w - 2 * m, 18f, 0f, head, 11f, 0xFF607D8B.toInt())
                val r = LecturePages.photoRect(size.first, size.second, spec.w, spec.h)
                val img = ImageEl(Ids.next(), name, r.cx, r.cy, r.w, r.h)
                val pid = newId()
                exec(DocEdit.InsertPages(listOf((after + 1) to PageSnapshot(pid, spec, PageContent.of(listOf(title, img))))))
                view?.goToPage(after + 1)
                // A2-8: wariant lab może od razu oczyścić zdjęcie tablicy (osobna edycja, cofnięcie wraca do zdjęcia).
                com.mdev.mnotes.app.Flavor.lab?.onLecturePhoto(this, pid, img)
            }
        }
    }

    /**
     * 2c-A1: na tablicy zdjęcie z trybu wykładu trafia na tę samą stronę przy środku widoku (bez nowej strony); płótno
     * rośnie zwykłym mechanizmem, gdy zdjęcie sięga brzegu. Szerokość jak na stronie A4 w trybie wykładu.
     */
    private fun lectureOnBoard(name: String, size: Pair<Int, Int>) {
        val page = doc.pages.firstOrNull() ?: return
        val (_, cx, cy) = view?.viewCenterOnPage() ?: Triple(0, page.spec.w / 2f, page.spec.h / 2f)
        val r = LecturePages.photoRect(size.first, size.second, PageSpec().w, PageSpec().h)
        val img = ImageEl(Ids.next(), name, cx, cy, r.w, r.h)
        exec(DocEdit.Elements(page.id, ElementsEdit.add(doc.content(page), listOf(img))))
        com.mdev.mnotes.app.Flavor.lab?.onLecturePhoto(this, page.id, img)
    }

    // — Konstrukcje geometryczne (L1) —
    /** Konstrukcje możliwe dla bieżącego zaznaczenia (trójkąt, proste, okręgi, punkty). */
    fun constructionOps(): List<ConstructOps.Op> = if (readOnly) emptyList() else ConstructOps.ops(selElements(), settings.constructNames, T.pl)

    /** Zaznaczenie nie daje konstrukcji, ale ma odręczne kreski — menu podpowiada, jak narysować kształt (A2-9). */
    fun constructionHint(): Boolean = !readOnly && ConstructOps.needsHint(selElements())

    /**
     * 2c-A8: wynik konstrukcji gotowy do wstawienia na [page] — nazwy punktów zajęte na stronie dostają kolejne litery.
     * Brak wyniku albo wynik daleko poza stroną (proste prawie równoległe, punkty prawie współliniowe) mówi dymkiem,
     * czemu, i zwraca pustą listę; null — brak rozwiązania bez znanego powodu (dymek ogólny u wołającego).
     */
    private fun constructed(page: PageState, els: List<Element>, why: ConstructOps.Fail): List<Element>? {
        val bad = els.isEmpty() || ConstructOps.tooFar(els, page.spec.w, page.spec.h)
        if (bad) {
            when (why) {
                ConstructOps.Fail.PARALLEL -> say(T.t("Proste prawie równoległe — przecięcie wypada daleko poza stroną", "The lines are almost parallel — the intersection is far off the page"))
                ConstructOps.Fail.COLLINEAR -> say(T.t("Punkty prawie współliniowe — okrąg byłby ogromny", "The points are almost collinear — the circle would be huge"))
                ConstructOps.Fail.NONE -> return if (els.isEmpty()) null else emptyList<Element>().also { say(T.t("Wynik wypada daleko poza stroną", "The result is far off the page")) }
            }
            return emptyList()
        }
        return ConstructOps.renameTaken(els, ConstructOps.pageNames(doc.content(page).elements))
    }

    /** Dopisuje wynik konstrukcji w kolorze i grubości aktywnego pióra; zaznacza wynik (jedno cofnięcie). */
    fun applyConstruction(op: ConstructOps.Op) {
        val p = selPage() ?: return
        val ink = (if (pen.kind == PenKind.HIGHLIGHTER) 0xFF37474F.toInt() else pen.color) or (0xFF shl 24)
        val els = constructed(p, op.build(ink, pen.width.coerceIn(0.6f, 3f)), ConstructOps.failOf(op.id))
            ?: return say(T.t("Ta konstrukcja nie ma rozwiązania dla zaznaczonych kształtów", "This construction has no solution for the selected shapes"))
        if (els.isEmpty()) return
        val ins = selElements()
        exec(DocEdit.Elements(p.id, ElementsEdit.add(doc.content(p), els)))
        val names = settings.constructNames; val pl = T.pl; val w = pen.width.coerceIn(0.6f, 3f); val id = op.id; val pid = p.id
        live.add(pid, ins.map { it.id }, els.map { it.id }) { cur ->
            ConstructOps.ops(cur, names, pl).firstOrNull { it.id == id }?.build?.invoke(ink, w)?.takeUnless { liveTooFar(pid, it) }
        }
        select(p.id, els.map { it.id }.toSet())
    }

    // — Narzędzie Geometria (2b-A8) —
    /** Wybrana konstrukcja i wejścia wskazane dotąd (na jednej stronie [geoPage]). */
    var geoKind by mutableStateOf(GeoTool.Kind.COMPASS)
        private set
    val geoInputs = androidx.compose.runtime.mutableStateListOf<GeoTool.Input>()
    /** Skąd wzięto [geoInputs] (te same pozycje) — dla żywej konstrukcji (NAU-3). */
    private val geoPicks = ArrayList<GeoTool.Picked>()
    var geoPage: String? = null
        private set

    val geoHint: String get() = GeoTool.hint(geoKind, geoInputs.size, T.pl)

    /** Kąt obrotu narzędzia Geometria w stopniach (2c-A8: chipy 30–180° i własna wartość; dawniej tylko 90°). */
    var geoAngle by mutableStateOf(90f)

    fun geoChoose(k: GeoTool.Kind) { geoKind = k; geoReset() }
    fun geoReset() { geoInputs.clear(); geoPicks.clear(); geoPage = null; view?.invalidate() }

    /**
     * Dotknięcie strony [pageId] w ([x], [y]) narzędziem Geometria: następne wejście wybranej konstrukcji; po ostatnim —
     * wynik w kolorze pióra (jedno cofnięcie). Wejścia z innej strony zaczynają konstrukcję od nowa.
     */
    fun geoTap(pageId: String, x: Float, y: Float, tol: Float) {
        if (readOnly) return
        val page = doc.page(pageId) ?: return
        if (geoPage != null && geoPage != pageId) { geoInputs.clear(); geoPicks.clear() }
        geoPage = pageId
        val want = geoKind.inputs[geoInputs.size]
        val picked = GeoTool.pickFrom(doc.content(page).elements, x, y, tol, want)
        if (picked == null) { say(GeoTool.missHint(geoKind, geoInputs.size, T.pl)); return }
        geoInputs += picked.input; geoPicks += picked
        if (geoInputs.size < geoKind.inputs.size) { view?.invalidate(); return }
        val ink = (if (pen.kind == PenKind.HIGHLIGHTER) 0xFF37474F.toInt() else pen.color) or (0xFF shl 24)
        val kind = geoKind; val w = pen.width.coerceIn(0.6f, 3f); val names = settings.constructNames; val pl = T.pl; val angle = geoAngle
        val picks = geoPicks.toList()
        val built = GeoTool.build(kind, geoInputs.toList(), ink, w, names, pl, angle)
        geoReset()
        val els = constructed(page, built, GeoTool.failOf(kind))
            ?: return say(T.t("Ta konstrukcja nie ma rozwiązania dla wskazanych kształtów", "This construction has no solution for the picked shapes"))
        if (els.isEmpty()) return
        exec(DocEdit.Elements(page.id, ElementsEdit.add(doc.content(page), els)))
        // NAU-3: wejście z wolnego miejsca strony jest stałe; wskazane kropki, wierzchołki i kształty ciągną wynik za sobą.
        val pid = page.id
        live.add(pid, picks.mapNotNull { it.id }, els.map { it.id }) { cur ->
            val byId = cur.associateBy { it.id }
            val ins = picks.mapIndexed { i, pk -> if (pk.id == null) pk.input else GeoTool.resolve(kind.inputs[i], byId[pk.id] ?: return@add null, pk.part) ?: return@add null }
            GeoTool.build(kind, ins, ink, w, names, pl, angle).takeUnless { it.isEmpty() || liveTooFar(pid, it) }
        }
    }

    // — Krzywa krok po kroku (2b-A10): przez punkty, kontur, piórko —

    val curveMode: com.mdev.mnotes.core.shapes.CurveMode get() = com.mdev.mnotes.core.shapes.CurveMode.of(settings.curveMode)
    /** Rozpoczęta krzywa (na stronie [curvePage]); null, gdy nic nie postawiono. */
    var curveDraft: com.mdev.mnotes.core.shapes.CurveDraft? = null
        private set
    var curvePage: String? = null
    /** Zmienia się z każdym węzłem — podpowiedź nad płótnem się przelicza. */
    var curveTick by mutableIntStateOf(0)
    val curveHint: String get() { curveTick; return com.mdev.mnotes.core.shapes.CurveDraft.hint(curveMode, curveDraft?.count ?: 0, T.pl) }

    /** Szkic dla bieżącego trybu; zmiana trybu zaczyna nowy (stary zapisany wcześniej przez [curveChoose]). */
    fun curveDraftFor(pageId: String): com.mdev.mnotes.core.shapes.CurveDraft {
        if (curvePage != null && curvePage != pageId) curveCommit()
        val d = curveDraft?.takeIf { it.mode == curveMode } ?: com.mdev.mnotes.core.shapes.CurveDraft(curveMode).also { curveDraft = it }
        curvePage = pageId
        return d
    }

    fun curveChoose(m: com.mdev.mnotes.core.shapes.CurveMode) { curveCommit(); settings.curveMode = m.ordinal; curveTick++ }

    fun curveCancel() { curveDraft = null; curvePage = null; curveTick++; view?.invalidate() }

    /** Cofa ostatni postawiony węzeł szkicu (2c-A10: ↶ w kapsule i Backspace); ostatni węzeł — porzuca szkic. */
    fun curveUndoNode() {
        val d = curveDraft ?: return
        d.dropLast()
        if (d.isEmpty) curveCancel() else { curveTick++; view?.invalidate() }
    }

    /** Zapisuje rozpoczętą krzywą jako kreskę z uchwytami (zaznaczoną, jak po F17); [close] = domknij (kontur zawsze). */
    fun curveCommit(close: Boolean = false) {
        val d = curveDraft
        val page = curvePage?.let { doc.page(it) }
        curveDraft = null; curvePage = null; curveTick++
        view?.invalidate()
        if (d == null || page == null || readOnly) return
        val c = d.ctrl(close || d.mode == com.mdev.mnotes.core.shapes.CurveMode.CLOSED) ?: return
        val shut = com.mdev.mnotes.core.shapes.CurveFit.closed(c)
        val p = pen
        val s = Stroke(Ids.next(), p.kind, p.color, p.width, com.mdev.mnotes.core.shapes.Bezier.sample(c), line = p.style, ctrl = c, fill = if (shut && settings.curveFill) p.color or (0xFF shl 24) else 0)
        exec(DocEdit.Elements(page.id, ElementsEdit.add(doc.content(page), listOf(s))))
        select(page.id, setOf(s.id))
    }

    /** Pomysł Fable 12: jedyny zaznaczony obraz staje się tłem strony — zablokowany i na spodzie, pod pismem. */
    fun canStickBackground(): Boolean = !readOnly && selElements().singleOrNull().let { it is ImageEl && !it.locked }

    fun stickBackground() {
        val p = selPage() ?: return
        val im = selElements().singleOrNull() as? ImageEl ?: return
        val c = doc.content(p)
        val toBack = ElementsEdit.sendToBack(c, setOf(im.id))
        val lock = ElementsEdit.replace(c.apply(toBack), mapOf(im.id to im.withLocked(true)))
        exec(DocEdit.Group(listOf(DocEdit.Elements(p.id, toBack), DocEdit.Elements(p.id, lock))))
        clearSel()
        say(T.t("Obraz przyklejony jako tło strony (odblokujesz go w menu zaznaczenia)", "Image stuck as page background (unlock it from the selection menu)"))
    }

    // — Porządkowanie obrazów (F23) —
    fun canArrangeSel(): Boolean = !readOnly && selElements().count { it is ImageEl && !it.locked } >= 2

    /** Zaznaczone obrazy w równych wierszach w prostokącie, który razem zajmowały (bez obrotu, proporcje bez zmian). */
    fun arrangeSel() {
        val p = selPage() ?: return
        val imgs = selElements().filterIsInstance<ImageEl>().filter { !it.locked }
        if (imgs.size < 2) return
        var area = Bounds.EMPTY
        for (e in imgs) area = area.union(e.bounds)
        // Kolejność czytania: od góry, w wierszu od lewej.
        val sorted = imgs.sortedWith(compareBy({ (it.cy / 30f).toInt() }, { it.cx }))
        val slots = ImageArrange.arrange(sorted.map { it.w / it.h.coerceAtLeast(1e-3f) }, area)
        val map = HashMap<Int, Element>()
        for ((i, im) in sorted.withIndex()) { val s = slots[i]; map[im.id] = im.copy(cx = s.cx, cy = s.cy, w = s.w, h = s.h, rot = 0f) }
        exec(DocEdit.Elements(p.id, ElementsEdit.replace(doc.content(p), map)))
        selTick++
    }

    // — Edycja punktów (F21) —
    /** Zaznaczony prostokąt pokazuje wierzchołki zamiast ramki (po podwójnym dotknięciu). Gaśnie przy zmianie zaznaczenia. */
    var pointEdit by mutableStateOf(false)

    /** Menu punktu po przytrzymaniu uchwytu: [index] wierzchołka łamanej albo punktu kontrolnego krzywej ([curve]). */
    class PointMenu(val x: Float, val y: Float, val pageId: String, val elementId: Int, val index: Int, val curve: Boolean)
    var pointMenu by mutableStateOf<PointMenu?>(null)

    /**
     * Podwójne dotknięcie zaznaczonej kreski: elipsa i kreska odręczna stają się krzywą z kotwicami i uchwytami,
     * prostokąt pokazuje wierzchołki zamiast ramki. Jedno cofnięcie.
     */
    fun editPoints() {
        val p = selPage() ?: return
        val st = selElements().singleOrNull() as? Stroke ?: return
        if (st.locked || readOnly) return
        if (st.poly) { pointEdit = !pointEdit; view?.invalidate(); return }
        val c = PointEdit.toCurve(st) ?: return
        exec(DocEdit.Elements(p.id, ElementsEdit.replace(doc.content(p), mapOf(st.id to c))))
        selTick++
        say(T.t("Edycja punktów: przeciągnij kotwice i uchwyty, przytrzymaj kotwicę po więcej opcji", "Point editing: drag anchors and handles, long-press an anchor for more"))
    }

    /** Akcja z menu punktu: zamienia element [m] wynikiem [f] (null = bez zmian). */
    fun pointAction(m: PointMenu, f: (Stroke) -> Stroke?) {
        val p = doc.page(m.pageId) ?: return
        val st = doc.content(p).byId(m.elementId) as? Stroke ?: return
        val out = f(st) ?: return
        exec(DocEdit.Elements(p.id, ElementsEdit.replace(doc.content(p), mapOf(st.id to out))))
        selTick++
    }

    // — Naklejki (F19) —
    val stickers by lazy { com.mdev.mnotes.data.StickerStore(repo.root) }
    var stickersOpen by mutableStateOf(false)
    /** Otwarte okno nazwy nowej naklejki (z bieżącego zaznaczenia). */
    var stickerSaveOpen by mutableStateOf(false)
    /** Rośnie po zapisie/usunięciu naklejki, żeby okno naklejek odświeżyło listę. */
    var stickerTick by mutableIntStateOf(0)

    fun saveSelAsSticker(name: String) {
        val n = Sticker.normalize(selElements()) ?: return
        repo.io.execute {
            val ok = runCatching { stickers.save(name, n) { repo.assetFile(nbId, it) } }.isSuccess
            view?.post { stickerTick++; say(if (ok) T.t("Zapisano naklejkę „$name”", "Sticker “$name” saved") else T.t("Nie udało się zapisać naklejki", "Could not save the sticker")) }
        }
    }

    /** Wstawia naklejkę na środku widoku (obrazy kopiowane do zeszytu), zaznaczoną, żeby od razu dało się ją przesunąć. */
    fun insertSticker(id: String) {
        val (pi, cx, cy) = view?.viewCenterOnPage() ?: return
        val pageId = doc.pages.getOrNull(pi)?.id ?: return
        repo.io.execute {
            val n = stickers.load(id)
            val names = HashMap<String, String>()
            if (n != null) for (a in Sticker.assets(n.elements)) {
                val f = stickers.assetFile(id, a)
                if (f.exists()) runCatching { f.inputStream().use { names[a] = repo.importAsset(nbId, it, a.substringAfterLast('.', "png")) } }
            }
            view?.post {
                val page = doc.page(pageId) ?: return@post
                if (n == null || n.elements.isEmpty()) { say(T.t("Nie udało się wczytać naklejki", "Could not load the sticker")); return@post }
                val els = Sticker.place(n, cx, cy, Sticker.fitScale(n.w, n.h, page.spec.w, page.spec.h)) { names[it] ?: it }
                exec(DocEdit.Elements(page.id, ElementsEdit.add(doc.content(page), els)))
                tool = Tool.LASSO
                select(page.id, els.map { it.id }.toSet())
            }
        }
    }

    // — Układ współrzędnych (F8) —
    var axesOpen by mutableStateOf(false)

    /**
     * Wstawia układ współrzędnych na środku widoku: siatka, osie ze strzałkami, podziałka i opisy jako zwykłe łamane
     * i pola tekstu (bez nowego typu w pliku, więc działa też eksport i starsze wersje). Wszystko zostaje zaznaczone
     * lassem, żeby od razu dało się przesunąć albo przeskalować; jedno cofnięcie usuwa całość.
     */
    fun insertAxes(spec: AxesSpec) {
        val (pi, cx, cy) = view?.viewCenterOnPage() ?: return
        val page = doc.pages.getOrNull(pi) ?: return
        var s = spec.normalized()
        // 2b-A6: na kratce podziałka to wielokrotność oczka, a początek układu leży na węźle — osie i siatka układu
        // pokrywają się z kratką strony, więc wykres rysuje się „po kratkach”.
        val grid = if (settings.axesSnap) AxesSnap.pageGrid(page.spec) else null
        if (grid != null) {
            val u = AxesSnap.alignInsert(s.unit, s.xStep, 0f, 0f, grid)[0]
            // 2c-A3: jednostka zmieniona do kratki (np. 7 mm → 5 mm) nie może przejść po cichu
            if (AxesSnap.unitChanged(s.unit, u)) {
                fun mm(v: Float) = String.format(java.util.Locale.ROOT, "%.1f", v * s.xStep * 25.4f / 72f).removeSuffix(".0")
                say(T.t("Podziałka dopasowana do kratki: ${mm(u)} mm zamiast ${mm(s.unit)} mm", "Scale fitted to the grid: ${mm(u)} mm instead of ${mm(s.unit)} mm"))
            }
            s = s.copy(unit = u)
        }
        val w = (s.xMax - s.xMin) * s.unit; val h = (s.yMax - s.yMin) * s.unit
        val m = Axes.OVERHANG + Axes.ARROW + 12f
        var left = (cx - w / 2f).coerceIn(m, maxOf(m, page.spec.w - w - m))
        var top = (cy - h / 2f).coerceIn(m, maxOf(m, page.spec.h - h - m))
        if (grid != null) {
            val ox = left + ((if (s.xMin > 0f) s.xMin else if (s.xMax < 0f) s.xMax else 0f) - s.xMin) * s.unit
            val oy = top + (s.yMax - (if (s.yMin > 0f) s.yMin else if (s.yMax < 0f) s.yMax else 0f)) * s.unit
            left += grid.nodeX(ox) - ox; top += grid.nodeY(oy) - oy
        }
        val d = Axes.build(s, left, top)
        // Kolor aktywnego pióra (zakreślacz dałby blade osie, więc wtedy grafit); siatka to ten sam kolor, przygaszony.
        val ink = (if (pen.kind == PenKind.HIGHLIGHTER) 0xFF37474F.toInt() else pen.color) or (0xFF shl 24)
        val faint = (ink and 0xFFFFFF) or (0x48 shl 24)
        fun line(xy: FloatArray, width: Float, color: Int): Stroke {
            val pts = FloatArray(xy.size / 2 * 3)
            for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
            return Stroke(Ids.next(), PenKind.BALLPOINT, color, width, pts, poly = true)
        }
        val els = ArrayList<Element>()
        d.grid.forEach { els.add(line(it, 0.45f, faint)) }
        d.axes.forEach { els.add(line(it, 1.1f, ink)) }
        d.ticks.forEach { els.add(line(it, 0.9f, ink)) }
        val fs = (s.unit * 0.42f).coerceIn(5.5f, 10f)
        for (l in d.labels) {
            val big = l.text == s.xName || l.text == s.yName
            val size = if (big) fs * 1.25f else fs
            val tw = l.text.length * size * 0.62f + 3f
            val th = size * 1.3f
            val bx = when (l.ax) { -1 -> l.x - tw / 2f; 1 -> l.x + tw / 2f; else -> l.x }
            val by = when (l.ay) { -1 -> l.y - th / 2f; 1 -> l.y + th / 2f; else -> l.y }
            val align = when (l.ax) { -1 -> TextBox.ALIGN_END; 1 -> TextBox.ALIGN_START; else -> TextBox.ALIGN_CENTER }
            els.add(TextBox(Ids.next(), bx, by, tw, th, 0f, l.text, size, ink, style = if (big) TextBox.STYLE_ITALIC else 0, align = align))
        }
        exec(DocEdit.Elements(page.id, ElementsEdit.add(doc.content(page), els)))
        tool = Tool.LASSO
        select(page.id, els.map { it.id }.toSet())
    }

    // — Strony —
    private fun curIndex() = pageIndex.coerceIn(0, doc.pages.size - 1)

    /** 2c-A1: tablica ma zawsze jedną stronę (panel stron wyszarza „Dodaj stronę”, reszta dostaje dymek). */
    val canAddPage: Boolean get() = com.mdev.mnotes.core.layout.Board.canAddPage(doc.pages.map { it.spec })

    private fun refuseBoardPage(): Boolean {
        if (canAddPage) return false
        say(T.t("Tablica ma jedną stronę", "A board has one page"))
        return true
    }

    fun addPage(after: Int = curIndex(), spec: PageSpec? = null) {
        if (refuseBoardPage()) return
        val base = spec ?: doc.pages.getOrNull(after)?.spec ?: PageSpec()
        val snap = PageSnapshot(newId(), base.copy(pdf = null), PageContent.EMPTY)
        exec(DocEdit.InsertPages(listOf((after + 1) to snap)))
        view?.goToPage(after + 1)
    }

    fun duplicatePage(i: Int = curIndex()) {
        if (refuseBoardPage()) return
        val p = doc.pages.getOrNull(i) ?: return
        val snap = PageSnapshot(newId(), p.spec, doc.content(p))
        exec(DocEdit.InsertPages(listOf((i + 1) to snap)))
        view?.goToPage(i + 1)
    }

    // — Przecinanie obrazu —

    /** Obraz przecinany w bieżącej sesji; gdy zniknął (cofnięcie, usunięcie), sesja się kończy. */
    fun cutImage(): ImageEl? {
        val c = cut ?: return null
        val img = doc.page(c.pageId)?.let { p -> doc.content(p).elements.firstOrNull { it.id == c.imageId } } as? ImageEl
        if (img == null || img.locked) { cut = null; view?.invalidate() }
        return img
    }

    /** Zaznaczony pojedynczy, niezablokowany obraz można przeciąć. */
    fun canCutSel(): Boolean {
        val el = selElements().singleOrNull() as? ImageEl ?: return false
        return !el.locked && !readOnly && el.crop.a == 0f
    }

    fun startCut() {
        val s = sel ?: return
        val img = selElements().singleOrNull() as? ImageEl
        if (readOnly || img == null) return
        if (img.locked) { say(T.t("Obraz jest zablokowany: odblokuj go, by go przeciąć", "The image is locked: unlock it to cut it")); return }
        transformMode = false
        cut = CutSession(s.pageId, img.id)
        view?.invalidate()
    }

    fun cancelCut() { if (cut != null) { cut = null; view?.invalidate() } }

    /** Linia z paska (bez przeciągania): w środku największej luki; po przeciągnięciu w [pos]. */
    fun addCutLine(vertical: Boolean, pos: Float? = null) {
        val c = cut ?: return; val img = cutImage() ?: return
        val p = pos ?: ImageCut.nextPos(img, vertical, c.lines)
        if (p == null || !ImageCut.fits(p, if (vertical) img.w else img.h)) {
            say(T.t("Za mało miejsca na kolejną linię", "No room for another line")); return
        }
        c.lines.add(CutLine(vertical, p))
        view?.invalidate()
    }

    fun undoCutLine() { cut?.lines?.let { if (it.isNotEmpty()) it.removeAt(it.lastIndex) }; view?.invalidate() }

    /** Liczba kawałków, jakie dadzą obecne linie. */
    fun cutPieceCount(): Int {
        val c = cut ?: return 0; val img = cutImage() ?: return 0
        val n = ImageCut.normalize(img, c.lines)
        return (n.count { it.vertical } + 1) * (n.count { !it.vertical } + 1)
    }

    fun applyCut() {
        val c = cut ?: return; val img = cutImage() ?: return
        val page = doc.page(c.pageId) ?: return
        val pieces = ImageCut.split(img, c.lines.toList(), if (c.spread) ImageCut.DEFAULT_GAP_PT else 0f) { Ids.next() }
        if (pieces.isEmpty()) { say(T.t("Najpierw narysuj linię cięcia", "Draw a cut line first")); return }
        val at = doc.content(page).elements.indexOfFirst { it.id == img.id }
        if (at < 0) return
        exec(DocEdit.Elements(page.id, ElementsEdit(listOf(Placed(at, img)), pieces.mapIndexed { i, e -> Placed(at + i, e) })))
        cut = null
        tool = Tool.LASSO
        select(page.id, pieces.map { it.id }.toSet())
        say(T.t("Przecięto na ${pieces.size} części", "Cut into ${pieces.size} pieces"))
        view?.invalidate()
    }

    // — Przycinanie obrazu —

    /** Przycinany obraz; gdy zniknął albo go zablokowano (cofnięcie, usunięcie), sesja się kończy. */
    fun cropImage(): ImageEl? {
        val c = crop ?: return null
        val img = doc.page(c.pageId)?.let { p -> doc.content(p).elements.firstOrNull { it.id == c.imageId } } as? ImageEl
        if (img == null || img.locked) { crop = null; view?.invalidate() }
        return img
    }

    fun canCropSel(): Boolean {
        val el = selElements().singleOrNull() as? ImageEl ?: return false
        return !el.locked && !readOnly
    }

    fun startCrop() {
        val s = sel ?: return
        val img = selElements().singleOrNull() as? ImageEl
        if (readOnly || img == null) return
        if (img.locked) { say(T.t("Obraz jest zablokowany: odblokuj go, by go przyciąć", "The image is locked: unlock it to crop it")); return }
        val size = Assets.imageSize(repo.assetFile(nbId, img.asset))
        if (size == null) { say(T.t("Nie można odczytać obrazu", "Cannot read the image")); return }
        val iw = size.first.toFloat(); val ih = size.second.toFloat()
        cancelCut()
        transformMode = false
        // odbity obraz przycina się w odwróconym pliku (patrz CropGeom.mirrorCrop); rysowanie odwraca bitmapę tak samo
        val flat = CropGeom.unmirrored(img)
        val frame = CropGeom.frameOf(flat.crop, iw, ih)
        crop = CropSession(s.pageId, img.id, img, iw, ih, frame, CropGeom.placeOf(flat, iw, ih))
        view?.invalidate()
    }

    fun cancelCrop() { if (crop != null) { crop = null; view?.invalidate() } }

    /** Najmniejsza połowa boku ramki w pikselach pliku (24 pt na stronie). */
    private fun CropSession.minHalf() = CropGeom.MIN_SIZE_PT * 0.5f / place.s

    private fun CropSession.set(f: CropFrame, keepWant: Boolean = false) {
        frame = f
        if (!keepWant) want = f
        view?.invalidate()
    }

    /** Przeciągnięcie uchwytu do punktu pliku ([fx], [fy]); plik stoi w miejscu. */
    fun cropResize(h: CropHandle, fx: Float, fy: Float) {
        val c = crop ?: return
        c.set(CropGeom.resized(c.frame, h, fx, fy, c.iw, c.ih, c.minHalf(), c.lockedRatio))
    }

    /** Ramka [base] przesunięta po pliku o ([dx], [dy]) pikseli pliku (liczone od początku gestu, więc brzeg pliku nie gubi palca). */
    fun cropMove(base: CropFrame, dx: Float, dy: Float) {
        val c = crop ?: return
        val g = CropGeom.moved(base, dx, dy, c.iw, c.ih)
        c.frame = g; c.want = c.want.copy(cx = g.cx, cy = g.cy)
        view?.invalidate()
    }

    /** Kąt ramki względem pliku (prostowanie plus ćwierć obrotu); plik obraca się pod ramką wokół jej środka. */
    fun cropSetAngle(a: Float) {
        val c = crop ?: return
        val f = c.frame
        val na = CropGeom.normAngle(a)
        if (na == f.a) return
        val page = f.a + c.place.phi
        c.place = c.place.turned(page - na, f.cx, f.cy, c.iw, c.ih)
        c.set(CropGeom.fitted(c.want.copy(cx = f.cx, cy = f.cy, a = na), c.iw, c.ih), keepWant = true)
    }

    /** Ćwierć obrotu: ten sam fragment, zawartość obraca się o 90°. */
    fun cropQuarter(clockwise: Boolean) {
        val c = crop ?: return
        val q = CropGeom.quarter(c.frame, clockwise)
        val page = c.frame.a + c.place.phi
        c.place = c.place.turned(page - q.a, c.frame.cx, c.frame.cy, c.iw, c.ih)
        c.portrait = !c.portrait
        c.quarter = (c.quarter + if (clockwise) -1 else 1).mod(4)
        c.want = CropGeom.quarter(c.want, clockwise).copy(cx = q.cx, cy = q.cy)
        c.set(q, keepWant = true)
    }

    fun cropSetRatio(r: CropRatio) {
        val c = crop ?: return
        c.ratio = r
        c.portrait = false
        val k = c.lockedRatio
        if (k != null) c.set(CropGeom.withRatio(c.frame, k, c.iw, c.ih)) else view?.invalidate()
    }

    fun cropSwapRatio() {
        val c = crop ?: return
        c.portrait = !c.portrait
        val k = c.lockedRatio ?: run {
            // swobodna ramka: po prostu zamień boki
            val f = c.frame
            c.set(CropGeom.fitted(f.copy(hw = f.hh, hh = f.hw), c.iw, c.ih)); return
        }
        c.set(CropGeom.withRatio(c.frame, k, c.iw, c.ih))
    }

    /** Cały plik, bez kąta ramki: plik stoi w miejscu (jak przy ręcznym rozciągnięciu uchwytów), a przy pochyleniu obraca się wokół środka ramki. */
    fun cropReset() {
        val c = crop ?: return
        val f = c.frame
        c.place = c.place.turned(f.a + c.place.phi, f.cx, f.cy, c.iw, c.ih)
        c.ratio = CropRatio.FREE; c.portrait = false; c.quarter = 0
        c.set(CropGeom.full(c.iw, c.ih))
    }

    fun cropIsFull(): Boolean {
        val c = crop ?: return true
        return c.frame.a == 0f && CropGeom.cropOf(c.frame, c.iw, c.ih).isFull
    }

    fun applyCrop() {
        val c = crop ?: return; val img = cropImage() ?: return
        val page = doc.page(c.pageId) ?: return
        val out = CropGeom.remirrored(CropGeom.applied(CropGeom.unmirrored(img), c.frame, c.place, c.iw, c.ih), img.mirror)
        crop = null
        if (out.crop == img.crop && out.cx == img.cx && out.cy == img.cy && out.w == img.w && out.h == img.h && out.rot == img.rot) { view?.invalidate(); return }
        val at = doc.content(page).elements.indexOfFirst { it.id == img.id }
        if (at < 0) return
        view?.releaseCropHide()
        exec(DocEdit.Elements(page.id, ElementsEdit(listOf(Placed(at, img)), listOf(Placed(at, out)))))
        select(page.id, setOf(img.id))
        view?.invalidate()
    }

    fun startPipette(onPick: (Int) -> Unit) { pipette = onPick; view?.invalidate() }
    fun cancelPipette() { pipette = null; view?.invalidate() }

    /** Pipeta z paska/skrótu: kolor trafia do zaznaczenia albo do aktywnego pióra. */
    fun pipetteForCurrent() = startPipette { c ->
        if (sel != null && !readOnly) recolorSel(c)
        else {
            if (settings.pipetteKeep) keepPicked(c)
            setPenColor(c); if (!penTool && tool != Tool.TEXT) tool = Tool.PEN
        }
    }

    /** Dopisuje pobrany kolor do zestawu aktywnego pióra, ale tylko gdy listwa ma jeszcze miejsce (inaczej wypchnąłby cudzy kolor). */
    private fun keepPicked(c: Int) {
        val cs = kit.setOf(kit.active)
        if ((c or 0xFF000000.toInt()) in cs.colors || cs.visible >= PenKit.MAX_VISIBLE || cs.colors.size >= PenKit.MAX_COLORS) return
        editKit { it.addColor(cs.id, c) }
    }

    fun toggleRuler() { ruler = !ruler; if (ruler) view?.rulerShown() else view?.invalidate() }

    /** Kątomierz na stronie (FB4, A2-13): przesuwany i obracany jak linijka; kreska ze środka idzie po promieniu. */
    var protractor by mutableStateOf(false)
    fun toggleProtractor() { protractor = !protractor; if (protractor) view?.protractorShown() else view?.invalidate() }

    fun copyPage(i: Int = curIndex()): Boolean {
        val p = doc.pages.getOrNull(i) ?: return false
        Clip.pageNb = nbId; Clip.page = p.spec to doc.content(p)
        say(T.t("Skopiowano stronę ${i + 1}", "Copied page ${i + 1}"))
        return true
    }

    fun cutPage(i: Int = curIndex()) {
        if (doc.pages.size <= 1) { say(T.t("Zeszyt musi mieć co najmniej jedną stronę", "A notebook needs at least one page")); return }
        if (copyPage(i)) deletePage(i)
    }

    /** Wkleja skopiowaną stronę po stronie [after]; przy wklejaniu do innego zeszytu dokłada pliki obrazów i tła PDF. */
    fun pastePage(after: Int = curIndex()) {
        val (spec0, content0) = Clip.page ?: run { say(T.t("Nie skopiowano żadnej strony", "No page has been copied")); return }
        var spec = spec0; var content = content0
        if (Clip.pageNb != nbId) {
            fun carry(asset: String): String {
                val src = repo.assetFile(Clip.pageNb, asset)
                if (!src.exists()) return asset
                val name = newId() + "." + src.extension.ifEmpty { "bin" }
                return if (runCatching { src.copyTo(repo.assetFile(nbId, name), true) }.isSuccess) name else asset
            }
            spec.pdf?.let { ref -> spec = spec.copy(pdf = ref.copy(asset = carry(ref.asset))) }
            if (content.elements.any { it is ImageEl }) {
                content = PageContent.of(content.elements.map { e -> if (e is ImageEl) e.copy(asset = carry(e.asset)) else e })
            }
        }
        exec(DocEdit.InsertPages(listOf((after + 1) to PageSnapshot(newId(), spec, content))))
        view?.goToPage(after + 1)
    }

    fun deletePage(i: Int = curIndex()) {
        if (doc.pages.size <= 1) { say(T.t("Zeszyt musi mieć co najmniej jedną stronę", "A notebook needs at least one page")); return }
        val p = doc.pages.getOrNull(i) ?: return
        clearSel()
        exec(DocEdit.RemovePages(listOf(i to doc.snapshot(p))))
    }

    fun clearPage(i: Int = curIndex()) {
        val p = doc.pages.getOrNull(i) ?: return
        val c = doc.content(p)
        if (c.isEmpty) return
        clearSel()
        exec(DocEdit.Elements(p.id, ElementsEdit.remove(c, c.elements.map { it.id }.toSet())))
    }

    /** Przestawia stronę; bieżąca strona zostaje na ekranie (także gdy to ona jest przenoszona), bez skoku widoku. */
    fun movePage(from: Int, to: Int) {
        val t = to.coerceIn(0, doc.pages.size - 1)
        if (from == t) return
        val p = doc.pages.getOrNull(from) ?: return
        val edit = DocEdit.MovePage(p.id, from, t)
        val keep = doc.pages.getOrNull(curIndex())?.id
        val v = view
        if (keep != null && v != null) v.keepPageInPlace(keep) { exec(edit) } else exec(edit)
    }

    fun setSpec(i: Int, spec: PageSpec) {
        val p = doc.pages.getOrNull(i) ?: return
        if (p.spec != spec) exec(DocEdit.Spec(p.id, p.spec, spec))
    }

    private var specPreview: Map<String, PageSpec>? = null

    /**
     * Podgląd zmiany stylu na żywo (suwak): stosuje [f] do stanu sprzed gestu, bez wpisu na stosie cofania.
     * [all] = wszystkie strony, inaczej tylko strona [i]. Gest kończy [commitSpecPreview] (jeden wpis cofania).
     */
    fun previewSpec(all: Boolean, i: Int, f: (PageSpec) -> PageSpec) {
        val before = specPreview ?: (if (all) doc.pages else listOfNotNull(doc.pages.getOrNull(i)))
            .associate { it.id to it.spec }.also { specPreview = it }
        for ((id, b) in before) { val n = f(b); if (doc.spec(id) != n) doc.setSpec(id, n) }
    }

    fun commitSpecPreview() {
        val before = specPreview ?: return
        specPreview = null
        val edits = before.mapNotNull { (id, b) -> if (doc.page(id) == null) null else doc.spec(id).takeIf { it != b }?.let { DocEdit.Spec(id, b, it) } }
        if (edits.isNotEmpty()) record(if (edits.size == 1) edits[0] else DocEdit.Group(edits))
    }

    /** Zmiana wzoru/papieru dla wszystkich stron (zachowuje rozmiary stron). */
    fun restyleAll(f: (PageSpec) -> PageSpec) {
        val edits = doc.pages.mapNotNull { p -> val n = f(p.spec); if (n != p.spec) DocEdit.Spec(p.id, p.spec, n) else null }
        if (edits.isNotEmpty()) exec(DocEdit.Group(edits))
    }

    fun toggleBookmark(i: Int = curIndex()) {
        val p = doc.pages.getOrNull(i) ?: return
        repo.update(nbId) { m ->
            if (m.bookmarks.any { it.pageId == p.id }) m.copy(bookmarks = m.bookmarks.filter { it.pageId != p.id })
            else m.copy(bookmarks = m.bookmarks + Bookmark(p.id, "${T.t("Strona", "Page")} ${i + 1}"))
        }
        structTick++
    }

    /** Zmienia tytuł zakładki strony [pageId]; pusty tytuł wraca do nazwy domyślnej („Strona N”). */
    fun renameBookmark(pageId: String, title: String) {
        repo.update(nbId) { m -> m.copy(bookmarks = m.bookmarks.map { if (it.pageId == pageId) it.copy(title = title.trim()) else it }) }
        structTick++
    }

    fun isBookmarked(i: Int): Boolean { structTick; return doc.pages.getOrNull(i)?.let { p -> repo.meta(nbId)?.bookmarks?.any { it.pageId == p.id } } ?: false }

    // — Tagi stron (FB8) — jak zakładki: dane zeszytu poza treścią stron, bez wpisu cofania.
    fun tagsOf(pageId: String): List<String> { structTick; return repo.meta(nbId)?.pageTags?.get(pageId).orEmpty() }

    fun setPageTags(pageId: String, tags: List<String>) {
        repo.update(nbId) { m -> m.copy(pageTags = PageTags.with(m.pageTags, pageId, tags)) }
        structTick++
    }

    /** Tagi zeszytu z numerami stron (najczęstsze najpierw). */
    fun tagUsage(): List<PageTags.Usage> { structTick; return PageTags.usage(pageTagsSnapshot(), doc.pages.map { it.id }) }

    /** Strona do okna tagów (z menu miniatury albo „⋮” edytora); null = okno zamknięte. */
    var tagsFor by mutableStateOf<String?>(null)

    // — Statystyki zeszytu (FB7) —
    var statsOpen by mutableStateOf(false)
    private var lastInkAt = 0L
    private var writingPending = 0L

    /** Kreska skończona: dolicza czas pisania; do preferencji co minutę i przy zamknięciu, nie przy każdej kresce. */
    fun noteWriting(now: Long = System.currentTimeMillis()) {
        writingPending += ActiveTime.credit(lastInkAt, now)
        lastInkAt = now
        if (writingPending >= 60_000L) flushWriting()
    }

    fun flushWriting() {
        settings.addWriting(nbId, ActiveTime.weekKey(java.time.LocalDate.now()), writingPending)
        writingPending = 0L
    }

    fun close() {
        // 2c-A10: szkic krzywej przy zamknięciu zeszytu staje się krzywą, zamiast zniknąć.
        if (curveDraft != null) curveCommit()
        // 2c-A11: wyjście z zeszytu zdejmuje przypięty profil (dołożone rzędy stanowiska tego nie robią).
        station?.takeIf { it.main === this }?.unpinProfile()
        flushWriting()
        doc.flush()
        doc.setLastPage(pageIndex)
        view?.dispose()
        view = null
    }

    /** Przełącza profil pracy i mówi, co się stało (dotyk to najczęstsze zaskoczenie, więc jest w dymku). */
    fun useProfile(p: com.mdev.mnotes.core.prefs.Profile?, pinned: Boolean = false) {
        settings.switchProfile(p)
        val finger = when (settings.fingerMode) {
            com.mdev.mnotes.core.prefs.Profiles.FINGER_DRAW -> T.t("palec rysuje", "finger draws")
            com.mdev.mnotes.core.prefs.Profiles.FINGER_OFF -> T.t("palec wyłączony", "finger off")
            else -> T.t("palec przewija", "finger scrolls")
        }
        say(
            if (p == null) T.t("Bez profilu — ustawienia jak przed profilem ($finger)", "No profile — settings as before ($finger)")
            else T.t("Profil „${p.name}”${if (pinned) " (przypięty)" else ""} — $finger", "Profile “${p.name}”${if (pinned) " (pinned)" else ""} — $finger"),
        )
        (station?.rows ?: listOf(this)).forEach { it.view?.invalidate() }
    }

    // — Skróty klawiszowe wspólne z paletą poleceń —
    fun setToolKey(t: Tool) {
        tool = t; clearSelIfNotLasso(); noteTool(t)
        quickFormat = t == Tool.PAINTER && settings.painterQuick
        // Dymki z opisem narzędzia można wyłączyć (F2): po kilku razach już tylko zasłaniają kartkę.
        if (!settings.toolHints) return
        if (t == Tool.TAPE) toast = T.t("Przeciągnij, aby zakryć fragment · dotknij taśmy, aby odkryć", "Drag to cover an area · tap a tape to reveal it")
        if (t == Tool.PAINTER) toast = if (settings.painterMode == 1) T.t("Obrysuj obiekty — przejmą kolor i styl aktywnego pióra", "Circle objects to give them the active pen's colour and style")
            else T.t("Przeciągnij po obiektach — przejmą kolor i styl aktywnego pióra", "Swipe over objects to give them the active pen's colour and style")
        if (t == Tool.FILL) toast = T.t("Dotknij obszaru ograniczonego liniami, aby go wypełnić kolorem aktywnego pióra", "Tap an area enclosed by lines to fill it with the active pen's colour")
        if (t == Tool.SPACE) toast = T.t("Przeciągnij od linii w dół — wstawisz odstęp, w górę — usuniesz puste miejsce", "Drag down from a line to insert space, up to remove empty space")
    }

    /**
     * Pędzel formatu: kopia elementu w kolorze i stylu pióra [p]; null, gdy nie ma czego zmienić (obraz, taśma,
     * element zablokowany, ten sam styl). Zakreślacz nie przefarbowuje tekstu (byłby nieczytelny).
     */
    fun painted(e: Element, p: ActivePen = pen): Element? {
        if (e.locked) return null
        val hl = p.kind == PenKind.HIGHLIGHTER
        return when (e) {
            is Stroke -> if (e.pen == p.kind && e.color == p.color && e.width == p.width && e.line == p.style) null else e.withStyle(p.color, p.width, p.kind, p.style)
            is FillEl -> if (e.color == p.color && e.hl == hl) null else e.copy(color = p.color, hl = hl)
            is TextBox -> if (hl || e.color == p.color) null else e.copy(color = p.color)
            else -> null
        }
    }

    private var filling = false

    /**
     * Wiaderko: liczy obszar wokół (x, y) poza wątkiem UI i wstawia wypełnienie w kolorze aktywnego pióra na spód
     * strony — nad zablokowanym obrazem tła i starszymi wypełnieniami, pod całym pismem.
     */
    fun fillAt(pageId: String, x: Float, y: Float) {
        if (readOnly || filling) return
        val v = view ?: return
        val page = doc.pages.firstOrNull { it.id == pageId } ?: return
        val els = doc.content(page).elements
        val w = page.spec.w; val h = page.spec.h
        val p = pen
        val closeGaps = settings.fillCloseGaps
        filling = true
        repo.io.execute {
            val rings = runCatching { FloodFill.fill(els, w, h, x, y, closeGaps, page.spec.darkPaper) }.getOrNull()
            v.post {
                filling = false
                if (rings.isNullOrEmpty()) { say(T.t("Dotknij wolnego miejsca między liniami", "Tap an empty spot between lines")); return@post }
                val pg = doc.pages.firstOrNull { it.id == pageId } ?: return@post
                val cur = doc.content(pg).elements
                var at = 0
                while (at < cur.size && (cur[at] is FillEl || (cur[at] is ImageEl && cur[at].locked))) at++
                val hl = p.kind == PenKind.HIGHLIGHTER
                val el = FillEl(Ids.next(), if (hl) p.color else p.color or (0xFF shl 24), hl, rings)
                val after = cur.subList(0, at) + el + cur.subList(at, cur.size)
                exec(DocEdit.Elements(pg.id, ElementsEdit.diff(doc.content(pg), PageContent.of(after))))
            }
        }
    }

    /** Przesuwa treść strony poniżej linii [y] o [dy] pt (EDT-065); [dy] jest już ograniczone przez [PageSpace.clamp]. */
    fun shiftBelow(pageId: String, y: Float, dy: Float) {
        if (readOnly) return
        val p = doc.pages.firstOrNull { it.id == pageId } ?: return
        val e = PageSpace.edit(doc.content(p), y, dy)
        if (e.isEmpty) return
        exec(DocEdit.Elements(p.id, e))
    }
    /** Nowa taśma na stronie (EDT-064): prostokąt między punktami (w pt), zakrywająca. */
    fun addTape(pageId: String, x0: Float, y0: Float, x1: Float, y1: Float) {
        if (readOnly) return
        val p = doc.pages.firstOrNull { it.id == pageId } ?: return
        val w = kotlin.math.abs(x1 - x0); val h = kotlin.math.abs(y1 - y0)
        if (w < 6f || h < 6f) return
        val t = TapeEl(Ids.next(), (x0 + x1) / 2, (y0 + y1) / 2, w, h, 0f, settings.tapeColor, true)
        exec(DocEdit.Elements(p.id, ElementsEdit.add(doc.content(p), listOf(t))))
    }

    /** Odkrywa / zakrywa jedną taśmę i przenosi ją na wierzch (żeby na pewno zakrywała). */
    fun toggleTape(pageId: String, id: Int) {
        val p = doc.pages.firstOrNull { it.id == pageId } ?: return
        val c = doc.content(p)
        val t = c.elements.firstOrNull { it.id == id } as? TapeEl ?: return
        exec(DocEdit.Elements(p.id, ElementsEdit.replaceOnTop(c, mapOf(id to t.copy(hidden = !t.hidden)))))
    }

    /** Zakrywa wszystkie taśmy w zeszycie; jeśli już wszystkie zakryte — odkrywa. Zwraca liczbę taśm. */
    fun toggleAllTapes(): Int {
        val per = doc.pages.map { it to doc.content(it).elements.filterIsInstance<TapeEl>() }.filter { it.second.isNotEmpty() }
        val n = per.sumOf { it.second.size }
        if (n == 0) { toast = T.t("Brak taśm — narzędzie Taśma (K) zakrywa fragmenty", "No tapes — the Tape tool (K) covers parts of the page"); return 0 }
        val hide = per.any { (_, l) -> l.any { !it.hidden } } // choć jedna odkryta → zakryj wszystkie, inaczej odkryj
        val edits = per.map { (p, l) ->
            val c = doc.content(p)
            DocEdit.Elements(p.id, ElementsEdit.replaceOnTop(c, l.associate { it.id to it.copy(hidden = hide) }))
        }
        exec(if (edits.size == 1) edits[0] else DocEdit.Group(edits))
        toast = if (hide) T.t("Zakryto $n taśm", "Covered $n tapes") else T.t("Odkryto $n taśm", "Revealed $n tapes")
        return n
    }
    private fun clearSelIfNotLasso() { if (tool != Tool.LASSO && tool != Tool.TEXT) clearSel() }

    fun stepColor(d: Int) {
        editKit { it.stepColor(it.active, d) }
        if (!penTool) tool = Tool.PEN
    }

    fun fileFor(name: String): File = repo.assetFile(nbId, name)
    fun maxOf2(a: Float, b: Float) = max(a, b)
}
