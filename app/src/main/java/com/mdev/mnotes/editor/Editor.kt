package com.mdev.mnotes.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.app.MNotesApp
import com.mdev.mnotes.core.geom.Bounds
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
import com.mdev.mnotes.core.model.ImageEl
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
import com.mdev.mnotes.data.NotebookDoc
import com.mdev.mnotes.data.PageState
import com.mdev.mnotes.data.newId
import com.mdev.mnotes.render.Assets
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

enum class Tool { PEN, ERASER, LASSO, TEXT, LASER, HAND, SPACE, TAPE, PAINTER, FILL }

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

    var tool by mutableStateOf(Tool.PEN)
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

    /** Fraza (bez wielkości liter i diakrytyków) w polach tekstowych i nazwach zakładek; wołać poza wątkiem UI. */
    fun runSearch(q: String, pages: List<PageState>, bookmarks: List<Bookmark>): List<SearchHit> {
        val phrase = TextSearch.fold(q.trim())
        if (phrase.isEmpty()) return emptyList()
        val out = ArrayList<SearchHit>()
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
    val penTool: Boolean get() = tool == Tool.PEN || tool == Tool.PAINTER || tool == Tool.FILL

    /** Wybór narzędzia piszącego z paska: staje się aktywnym piórem i narzędziem, a zaznaczenie znika. */
    fun selectPen(kind: PenKind) {
        editKit { it.withActive(kind) }
        tool = Tool.PEN; clearSelIfNotLasso()
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
        if (!penTool) { tool = Tool.PEN; clearSelIfNotLasso() }
    }

    fun say(msg: String) { toast = msg }

    // — Cofanie —
    fun undo() { cancelCut(); cancelCrop(); clearSel(); val e = doc.doUndo(); undoTick++; e?.pageIds?.firstOrNull()?.let { view?.ensureVisible(it) } }
    fun redo() { cancelCut(); cancelCrop(); clearSel(); val e = doc.doRedo(); undoTick++; e?.pageIds?.firstOrNull()?.let { view?.ensureVisible(it) } }

    /** Wykonanie z zapisem na stosie cofania. */
    fun exec(edit: DocEdit) { doc.exec(edit); undoTick++ }
    fun record(edit: DocEdit) { doc.record(edit); undoTick++ }

    // — Zaznaczenie —
    fun clearSel() { if (sel != null) { sel = null; transformMode = false; selTick++ } }

    fun select(pageId: String, ids: Set<Int>) {
        val old = sel
        if (old == null || old.pageId != pageId || old.ids != ids) transformMode = false
        sel = if (ids.isEmpty()) null else Sel(pageId, ids)
        selTick++
    }

    fun selElements(): List<Element> {
        val s = sel ?: return emptyList()
        val p = doc.page(s.pageId) ?: return emptyList()
        return doc.content(p).elements.filter { it.id in s.ids }
    }

    private fun selPage() = sel?.let { doc.page(it.pageId) }

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
        val v = view ?: return
        val (pi, cx, cy) = at ?: v.viewCenterOnPage() ?: return
        val page = doc.pages[pi]
        var b = Bounds.EMPTY
        for (e in Clip.els) b = b.union(e.bounds)
        val dx = cx - b.cx; val dy = cy - b.cy
        val copies = Clip.els.map { e ->
            var n = dup(e, dx, dy)
            if (n is ImageEl && Clip.nb != nbId) {
                val src = repo.assetFile(Clip.nb, n.asset)
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

    fun setSelAlpha(a: Float) = replaceSel { e -> if (e is ImageEl) e.copy(alpha = a) else e }

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
            exec(DocEdit.Elements(p.id, ElementsEdit.add(content, listOf(t))))
            select(p.id, setOf(t.id))
        } else {
            if (t == null || t.text.isBlank()) { exec(DocEdit.Elements(p.id, ElementsEdit.remove(content, setOf(req.existing.id)))); clearSel() }
            else exec(DocEdit.Elements(p.id, ElementsEdit.replace(content, mapOf(req.existing.id to t))))
        }
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
                val el = ImageEl(Ids.next(), name, cx, cy, size.first * k, size.second * k)
                exec(DocEdit.Elements(page.id, ElementsEdit.add(doc.content(page), listOf(el))))
                tool = Tool.LASSO
                select(page.id, setOf(el.id))
                onDone()
            }
        }
    }

    // — Strony —
    private fun curIndex() = pageIndex.coerceIn(0, doc.pages.size - 1)

    fun addPage(after: Int = curIndex(), spec: PageSpec? = null) {
        val base = spec ?: doc.pages.getOrNull(after)?.spec ?: PageSpec()
        val snap = PageSnapshot(newId(), base.copy(pdf = null), PageContent.EMPTY)
        exec(DocEdit.InsertPages(listOf((after + 1) to snap)))
        view?.goToPage(after + 1)
    }

    fun duplicatePage(i: Int = curIndex()) {
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
        val frame = CropGeom.frameOf(img.crop, iw, ih)
        crop = CropSession(s.pageId, img.id, img, iw, ih, frame, CropGeom.placeOf(img, iw, ih))
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
        val out = CropGeom.applied(img, c.frame, c.place, c.iw, c.ih)
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

    fun close() {
        doc.flush()
        doc.setLastPage(pageIndex, settings.layoutMode)
        view?.dispose()
        view = null
    }

    // — Skróty klawiszowe wspólne z paletą poleceń —
    fun setToolKey(t: Tool) {
        tool = t; clearSelIfNotLasso()
        quickFormat = t == Tool.PAINTER && settings.painterQuick
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
            val rings = runCatching { FloodFill.fill(els, w, h, x, y, closeGaps) }.getOrNull()
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
