package com.mdev.mnotes.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatListBulleted
import com.mdev.mnotes.core.text.ListText
import com.mdev.mnotes.core.search.CommandRank
import com.mdev.mnotes.core.text.MathText
import com.mdev.mnotes.core.text.TextFit
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mdev.mnotes.app.app
import com.mdev.mnotes.app.openNewWindow
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.editor.Clip
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.editor.TextRequest
import com.mdev.mnotes.editor.Tool
import com.mdev.mnotes.render.PageRenderer

// ─────────────────────────────── Polecenia (paleta Ctrl+K) ───────────────────────────────

/** Polecenie edytora w czasie działania: definicja + skrót użytkownika + warunek dostępności liczony w chwili użycia. */
class Cmd(val def: CmdDef, val keyText: String, private val isEnabled: () -> Boolean, val run: () -> Unit) {
    val title get() = def.title
    val chord: Chord? = Chord.parse(keyText)
    val enabled get() = isEnabled()
}

/** Wszystkie polecenia edytora (paleta Ctrl+K, pasek menu i skróty). Dostępność i widok są odczytywane leniwie. */
fun commandList(
    ed: Editor, set: com.mdev.mnotes.data.Settings, pickTool: (Tool, Panel?) -> Unit, setPanel: (Panel?) -> Unit,
    pickImage: () -> Unit, goTo: () -> Unit, applyLayout: (Int) -> Unit, onSettings: () -> Unit, onShortcuts: () -> Unit,
    openPalette: () -> Unit, onExit: () -> Unit, tabs: TabsCtl,
): List<Cmd> {
    val keys = Keymap.overrides(set)
    val acts: Map<String, Pair<() -> Boolean, () -> Unit>> = mapOf(
        "export" to ({ true } to { ed.exportOpen = true }),
        "settings" to ({ true } to { onSettings() }),
        "newWindow" to ({ true } to { ed.ctx.openNewWindow(null) }),
        "zoomWindow" to ({ true } to { ed.toggleZoomWin() }),
        "pagesDock" to ({ true } to { set.dockPages = !set.dockPages }),
        "close" to ({ true } to { if (tabs.on) tabs.close(ed.nbId) else onExit() }),
        "tabNext" to ({ tabs.tabs.size > 1 } to { tabs.step(1) }),
        "tabPrev" to ({ tabs.tabs.size > 1 } to { tabs.step(-1) }),
        "undo" to ({ ed.canUndo && !ed.readOnly } to { ed.undo() }),
        "redo" to ({ ed.canRedo && !ed.readOnly } to { ed.redo() }),
        "cut" to ({ ed.sel != null && !ed.readOnly } to { ed.cutSel() }),
        "copy" to ({ ed.sel != null } to { ed.copySel() }),
        "paste" to ({ !ed.readOnly } to { ed.paste() }),
        "duplicate" to ({ ed.sel != null && !ed.readOnly } to { ed.duplicateSel() }),
        "delete" to ({ ed.sel != null && !ed.readOnly } to { ed.deleteSel() }),
        "selectAll" to ({ !ed.readOnly } to { ed.setToolKey(Tool.LASSO); ed.selectAllOnPage() }),
        "find" to ({ true } to { ed.openSearch() }),
        "findNext" to ({ true } to { if (ed.searchOpen) ed.searchStep(1) else ed.openSearch() }),
        "findPrev" to ({ ed.searchOpen } to { ed.searchStep(-1) }),
        "zoomIn" to ({ true } to { ed.view?.zoomBy(1.2f) }),
        "zoomOut" to ({ true } to { ed.view?.zoomBy(1f / 1.2f) }),
        "fitWidth" to ({ true } to { ed.view?.fitWidth() }),
        "layoutV" to ({ true } to { applyLayout(0) }),
        "layoutH" to ({ true } to { applyLayout(1) }),
        "layoutS" to ({ true } to { applyLayout(2) }),
        "pagesPanel" to ({ true } to { setPanel(Panel.PAGES) }),
        "focusMode" to ({ true } to { ed.chrome = !ed.chrome }),
        "readOnly" to ({ true } to { ed.readOnly = !ed.readOnly }),
        "tbTop" to ({ true } to { set.toolbarPos = 0 }),
        "tbBottom" to ({ true } to { set.toolbarPos = 1 }),
        "tbLeft" to ({ true } to { set.toolbarPos = 2 }),
        "tbRight" to ({ true } to { set.toolbarPos = 3 }),
        "insertImage" to ({ !ed.readOnly } to { pickImage() }),
        "pageBackground" to ({ !ed.readOnly } to { ed.imageAsBackground = true; pickImage() }),
        "insertAxes" to ({ !ed.readOnly } to { ed.axesOpen = true }),
        "arrangeImages" to ({ ed.canArrangeSel() } to { ed.arrangeSel() }),
        "dualOpen" to ({ true } to { ed.station?.picking = true }),
        "stations" to ({ true } to { ed.station?.listing = true }),
        "profileNext" to ({ true } to { switchProfile(ed, com.mdev.mnotes.core.prefs.Profiles.next(set.profiles, set.profileActive)) }),
        "lecture" to ({ !ed.readOnly } to { ed.lectureMode = true; ed.lectureShootTick++ }),
        "boardPhoto" to ({ !ed.readOnly } to { ed.lectureShootTick++ }),
        "stickers" to ({ !ed.readOnly } to { ed.stickersOpen = true }),
        "stickerSave" to ({ ed.sel != null } to { ed.stickerSaveOpen = true }),
        "snipImage" to ({ ed.sel != null && !ed.readOnly } to { ed.snipSel(false) }),
        "snipCopy" to ({ ed.sel != null } to { ed.snipSel(true) }),
        "addPage" to ({ !ed.readOnly } to { ed.addPage() }),
        "bookmark" to ({ true } to { ed.toggleBookmark() }),
        "pageTags" to ({ !ed.readOnly } to { ed.doc.pages.getOrNull(ed.pageIndex)?.let { ed.tagsFor = it.id } }),
        "notebookStats" to ({ true } to { ed.statsOpen = true }),
        "copyPage" to ({ true } to { ed.copyPage() }),
        "copyPageImage" to ({ true } to { ed.copyPageAsImage(ed.pageIndex) }),
        "cutPage" to ({ !ed.readOnly && ed.pageCount > 1 } to { ed.cutPage() }),
        "pastePage" to ({ !ed.readOnly && Clip.page != null } to { ed.pastePage() }),
        "goTo" to ({ true } to { goTo() }),
        "pageNext" to ({ true } to { ed.view?.goToPage(ed.pageIndex + 1) }),
        "pagePrev" to ({ true } to { ed.view?.goToPage(ed.pageIndex - 1) }),
        "pageFirst" to ({ true } to { ed.view?.goToPage(0) }),
        "pageLast" to ({ true } to { ed.view?.goToPage(ed.pageCount - 1) }),
        "pageStyle" to ({ true } to { setPanel(Panel.STYLE) }),
        "pageDup" to ({ !ed.readOnly } to { ed.duplicatePage() }),
        "pageDelete" to ({ !ed.readOnly && ed.pageCount > 1 } to { ed.deletePage() }),
        "pageClear" to ({ !ed.readOnly } to { ed.clearPage() }),
        "toolPen" to ({ !ed.readOnly } to { pickTool(Tool.PEN, null) }),
        "toolEraser" to ({ !ed.readOnly } to { pickTool(Tool.ERASER, null) }),
        "toolPainter" to ({ !ed.readOnly } to { pickTool(Tool.PAINTER, Panel.PAINTER) }),
        "toolFill" to ({ !ed.readOnly } to { pickTool(Tool.FILL, Panel.FILL) }),
        "toolLasso" to ({ !ed.readOnly } to { pickTool(Tool.LASSO, null) }),
        "toolText" to ({ !ed.readOnly } to { pickTool(Tool.TEXT, null) }),
        "toolLaser" to ({ true } to { pickTool(Tool.LASER, null) }),
        "toolHand" to ({ true } to { pickTool(Tool.HAND, null) }),
        "toolCurve" to ({ !ed.readOnly } to { pickTool(Tool.CURVE, null) }),
        "attach" to ({ true } to { set.attach = !set.attach; ed.say(if (set.attach) T.t("Przyczepianie do figur włączone", "Attach to shapes on") else T.t("Przyczepianie do figur wyłączone", "Attach to shapes off")) }),
        "toolSpace" to ({ !ed.readOnly } to { pickTool(Tool.SPACE, null) }),
        "ruler" to ({ !ed.readOnly } to { ed.toggleRuler() }),
        "protractor" to ({ !ed.readOnly } to { ed.toggleProtractor() }),
        "toolGeo" to ({ !ed.readOnly } to { pickTool(Tool.GEO, null) }),
        "toolTape" to ({ !ed.readOnly } to { pickTool(Tool.TAPE, Panel.TAPE) }),
        "tapesToggle" to ({ !ed.readOnly } to { ed.toggleAllTapes() }),
        "pipette" to ({ !ed.readOnly } to { ed.pipetteForCurrent() }),
        "presetNext" to ({ !ed.readOnly } to { ed.stepColor(1) }),
        "presetPrev" to ({ !ed.readOnly } to { ed.stepColor(-1) }),
        "penSettings" to ({ true } to { pickTool(Tool.PEN, null); setPanel(Panel.PEN) }),
        "eraserSettings" to ({ true } to { pickTool(Tool.ERASER, null); setPanel(Panel.ERASER) }),
        "eraserObject" to ({ true } to { set.eraserMode = 0; pickTool(Tool.ERASER, null) }),
        "eraserPixel" to ({ true } to { set.eraserMode = 1; pickTool(Tool.ERASER, null) }),
        "palette" to ({ true } to { openPalette() }),
        "shortcuts" to ({ true } to { onShortcuts() }),
    )
    return CmdDefs.list.map { d ->
        val a = acts.getValue(d.id)
        Cmd(d, keys[d.id] ?: d.key, a.first, a.second)
    } + com.mdev.mnotes.app.Flavor.lab?.commands(ed).orEmpty().map { c -> Cmd(CmdDef(c.id, c.group, c.pl, c.en), "", c.enabled, c.run) }
}

@Composable
fun PaletteDialog(cmds: List<Cmd>, onDismiss: () -> Unit) {
    val m = mint
    var q by remember { mutableStateOf("") }
    var cur by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val set = LocalContext.current.app.settings
    // Pomysł Fable 2: ostatnio użyte na górze, wyszukiwanie bez wielkości liter i polskich znaków, przedrostki słów wyżej.
    val recent = remember { CommandRank.decode(set.paletteRecent) }
    val list = remember(q, cmds) {
        val items = cmds.map { c -> CommandRank.Item(c.def.id, c.title, c.keyText + " " + CmdDefs.groupName(c.def.group)) }
        CommandRank.rank(items, q, recent).map { cmds[it] }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q) { cur = 0 }
    fun run(c: Cmd?) { if (c != null && c.enabled) { set.paletteRecent = CommandRank.encode(CommandRank.used(recent, c.def.id)); onDismiss(); c.run() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().widthMax().shadow(18.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface).border(1.dp, m.line, RoundedCornerShape(14.dp)).padding(12.dp),
        ) {
            MField(
                q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus), hint = T.t("Wpisz polecenie…", "Type a command…"),
                leading = { androidx.compose.material3.Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                onDone = { run(list.getOrNull(cur)) },
            )
            Spacer(Modifier.height(8.dp))
            val ls = rememberLazyListState()
            LazyColumn(Modifier.heightIn(max = 380.dp), state = ls) {
                itemsIndexed(list) { i, c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (i == cur) m.accentSoft else Color.Transparent)
                            .clickable(enabled = c.enabled) { run(c) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            c.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                            color = if (c.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        if (c.keyText.isNotEmpty()) Text(c.keyText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (list.isEmpty()) Text(T.t("Brak wyników", "No results"), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Modifier.widthMax(): Modifier = this.then(Modifier.widthIn480())
private fun Modifier.widthIn480(): Modifier = this.width(480.dp)

// ─────────────────────────────── Idź do strony ───────────────────────────────

@Composable
fun GoToDialog(ed: Editor, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf("") }
    val n = t.trim().toIntOrNull()
    val ok = n != null && n in 1..ed.pageCount
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun go() { if (ok) { ed.view?.goToPage(n!! - 1); onDismiss() } }
    MDialog(T.t("Idź do strony", "Go to page"), onDismiss, T.t("Idź", "Go"), ::go, ok) {
        MField(t, { t = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().focusRequester(focus), hint = "1 – ${ed.pageCount}", number = true, onDone = ::go)
    }
}

// ─────────────────────────────── Pole tekstowe (TextBox) ───────────────────────────────

@Composable
fun TextDialog(ed: Editor, req: TextRequest) {
    val set = ed.settings
    val ex = req.existing
    var field by remember(req) { (ex?.text ?: req.initial).let { mutableStateOf(TextFieldValue(it, TextRange(it.length))) } }
    val text = field.text
    var size by remember(req) { mutableStateOf(ex?.size ?: set.textSize) }
    var color by remember(req) { mutableIntStateOf(ex?.color ?: set.textColor) }
    var family by remember(req) { mutableIntStateOf(ex?.family ?: set.textFamily) }
    var style by remember(req) { mutableIntStateOf(ex?.style ?: 0) }
    var align by remember(req) { mutableIntStateOf(ex?.align ?: TextBox.ALIGN_START) }
    var fill by remember(req) { mutableIntStateOf(ex?.fill ?: 0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val renderer = remember { PageRenderer(ed.app.assets) }

    // Nowe pole dopasowuje się do tekstu; istniejące zachowuje swój tryb (okno edycji go nie zmienia, chyba że przełącznik)
    var autoFit by remember(req) { mutableStateOf((ex?.fit ?: TextBox.FIT_AUTO) == TextBox.FIT_AUTO) }

    fun commit() {
        val spec = ed.doc.page(req.pageId)?.spec
        val pageW = spec?.w ?: 595f; val pageH = spec?.h ?: 842f
        val fit = when {
            autoFit -> TextBox.FIT_AUTO
            ex != null && ex.fit == TextBox.FIT_FIXED -> TextBox.FIT_FIXED
            else -> TextBox.FIT_HEIGHT
        }
        val w = ex?.w ?: 240f
        val x = ex?.let { TextFit.corner(it).first } ?: TextFit.placeX(req.x, pageW)
        // L2: „x^10” na samym końcu nie ma już po czym czekać — „Gotowe” kończy znacznik.
        val body = if (set.mathText) MathText.convert(text, atEnd = true) else text
        val base = TextBox(ex?.id ?: Ids.next(), x + w / 2f, req.y + 10f, w, 20f, ex?.rot ?: 0f, body, size, color, family, style, align, fill, ex?.locked ?: false, fit, ex?.lineSpacing ?: 1f, ex?.mirror ?: false)
        // nowe pole: lewy-górny róg w klikniętym miejscu; istniejące: róg zostaje tam, gdzie był
        val placed = if (ex != null) base.copy(cx = ex.cx, cy = ex.cy, w = ex.w, h = ex.h) else base
        val fitted = TextFit.refit(placed, pageW, pageH, renderer.textMeasure)
        set.textSize = size; set.textColor = color; set.textFamily = family
        ed.commitText(req, fitted)
    }
    MDialog(
        if (ex == null) T.t("Nowy tekst", "New text") else T.t("Edytuj tekst", "Edit text"), { ed.textReq = null },
        T.t("Gotowe", "Done"), ::commit, true,
    ) {
        MAreaField(field, { nv ->
            // Enter w wierszu listy dopisuje kolejny znacznik (EDT-042)
            val r = if (nv.selection.collapsed) ListText.onEnter(field.text, nv.text, nv.selection.start) else null
            var next = if (r != null) TextFieldValue(r.text, TextRange(r.selStart, r.selEnd)) else nv
            // L2: \alpha → α, x^2 → x², a_1 → a₁ przy pisaniu; kursor zostaje w tej samej odległości od końca.
            if (ed.settings.mathText && next.selection.collapsed && next.text.length > field.text.length) {
                val conv = MathText.convert(next.text)
                if (conv != next.text) {
                    val pos = (conv.length - (next.text.length - next.selection.start)).coerceIn(0, conv.length)
                    next = TextFieldValue(conv, TextRange(pos))
                }
            }
            field = next
        }, Modifier.fillMaxWidth().focusRequester(focus), hint = T.t("Wpisz tekst…", "Type text…"))
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            fun st(bit: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, tip: String) =
                @Composable { IconBtn(icon, tip, { style = style xor bit }, selected = style and bit != 0, size = 36.dp, iconSize = 20.dp) }
            st(TextBox.STYLE_BOLD, Icons.Rounded.FormatBold, T.t("Pogrubienie", "Bold"))()
            st(TextBox.STYLE_ITALIC, Icons.Rounded.FormatItalic, T.t("Kursywa", "Italic"))()
            st(TextBox.STYLE_UNDERLINE, Icons.Rounded.FormatUnderlined, T.t("Podkreślenie", "Underline"))()
            st(TextBox.STYLE_STRIKE, Icons.Rounded.FormatStrikethrough, T.t("Przekreślenie", "Strikethrough"))()
            Box(Modifier.padding(horizontal = 4.dp).height(22.dp).width(1.dp).background(mint.line))
            fun list(k: ListText.Kind) {
                val r = ListText.toggle(field.text, field.selection.min, field.selection.max, k)
                field = TextFieldValue(r.text, TextRange(r.selStart, r.selEnd))
                runCatching { focus.requestFocus() }
            }
            IconBtn(Icons.Rounded.FormatListBulleted, T.t("Lista punktowana", "Bulleted list"), { list(ListText.Kind.BULLET) }, size = 36.dp, iconSize = 20.dp)
            IconBtn(Icons.Rounded.FormatListNumbered, T.t("Lista numerowana", "Numbered list"), { list(ListText.Kind.NUMBER) }, size = 36.dp, iconSize = 20.dp)
            IconBtn(Icons.Rounded.Checklist, T.t("Lista kontrolna", "Checklist"), { list(ListText.Kind.CHECK) }, size = 36.dp, iconSize = 20.dp)
            Box(Modifier.padding(horizontal = 4.dp).height(22.dp).width(1.dp).background(mint.line))
            IconBtn(Icons.Rounded.FormatAlignLeft, T.t("Do lewej", "Left"), { align = TextBox.ALIGN_START }, selected = align == TextBox.ALIGN_START, size = 36.dp, iconSize = 20.dp)
            IconBtn(Icons.Rounded.FormatAlignCenter, T.t("Do środka", "Center"), { align = TextBox.ALIGN_CENTER }, selected = align == TextBox.ALIGN_CENTER, size = 36.dp, iconSize = 20.dp)
            IconBtn(Icons.Rounded.FormatAlignRight, T.t("Do prawej", "Right"), { align = TextBox.ALIGN_END }, selected = align == TextBox.ALIGN_END, size = 36.dp, iconSize = 20.dp)
            IconBtn(Icons.Rounded.FormatAlignJustify, T.t("Wyjustuj", "Justify"), { align = TextBox.ALIGN_JUSTIFY }, selected = align == TextBox.ALIGN_JUSTIFY, size = 36.dp, iconSize = 20.dp)
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(T.t("Bezszeryfowa", "Sans"), family == TextBox.FAMILY_SANS, { family = TextBox.FAMILY_SANS })
            Chip(T.t("Szeryfowa", "Serif"), family == TextBox.FAMILY_SERIF, { family = TextBox.FAMILY_SERIF })
            Chip(T.t("Mono", "Mono"), family == TextBox.FAMILY_MONO, { family = TextBox.FAMILY_MONO })
            Chip(T.t("Pismo ręczne", "Handwriting"), family == TextBox.FAMILY_HAND, { family = TextBox.FAMILY_HAND })
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(T.t("Rozmiar", "Size")); Spacer(Modifier.weight(1f))
            Text("${size.toInt()} pt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MSlider(size, 8f, 96f) { size = it }
        SectionLabel(T.t("Kolor", "Color"))
        ColorPicker(color, { color = it }, recent = ed.kit.sets.filter { !it.hl }.flatMap { it.colors.take(it.visible) }.distinct())
        ToggleRow(T.t("Dopasuj rozmiar do tekstu", "Fit size to text"), autoFit) { autoFit = it }
        ToggleRow(T.t("Tło pola", "Box background"), fill != 0) { fill = if (it) 0xFFFFF59D.toInt() else 0 }
        if (fill != 0) ColorPicker(fill, { fill = it }, withAlpha = true)
    }
}
