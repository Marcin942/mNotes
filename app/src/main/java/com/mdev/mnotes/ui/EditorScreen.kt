package com.mdev.mnotes.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlignHorizontalCenter
import androidx.compose.material.icons.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.rounded.AlignHorizontalRight
import androidx.compose.material.icons.rounded.AlignVerticalBottom
import androidx.compose.material.icons.rounded.AlignVerticalCenter
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mdev.mnotes.app.app
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke as InkStroke
import androidx.compose.ui.graphics.toArgb
import com.mdev.mnotes.core.pen.PenSpec
import com.mdev.mnotes.render.PageRenderer
import com.mdev.mnotes.core.pen.StrokeBuilder
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import com.mdev.mnotes.data.PenPreset
import com.mdev.mnotes.editor.Clip
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.NoteView
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.editor.Tool
import kotlinx.coroutines.delay

enum class Panel { PEN, ERASER, LASSO, TAPE, PAGES, STYLE, MORE, SELCOLOR, ALIGN }

/** Własne ikony tam, gdzie Material Icons nie ma odpowiednika (gumka). */
object MIcons {
    private fun make(name: String, d: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black),
    ).build()

    val Eraser: ImageVector by lazy {
        make("Eraser", "M16.24 3.56l4.95 4.94c.78.79.78 2.05 0 2.84L12 20.53a4.008 4.008 0 0 1-5.66 0L2.81 17c-.78-.79-.78-2.05 0-2.84l10.6-10.6c.79-.78 2.05-.78 2.83 0zM4.22 15.58l3.54 3.53c.78.79 2.04.79 2.83 0l3.53-3.53-4.95-4.95-4.95 4.95z")
    }
}

fun penName(k: PenKind) = when (k) {
    PenKind.BALLPOINT -> T.t("Długopis", "Ballpoint"); PenKind.FOUNTAIN -> T.t("Pióro", "Fountain")
    PenKind.BRUSH -> T.t("Pędzel", "Brush"); PenKind.HIGHLIGHTER -> T.t("Zakreślacz", "Highlighter"); PenKind.PENCIL -> T.t("Ołówek", "Pencil")
}

/**
 * Cały ekran edytora. Paski są zadokowane (nie pływają): [pasek menu] → [nagłówek z kartami i narzędziami] → [strona] →
 * [pasek dolny], a szyny boczne stoją obok strony. Strona jest mierzona, więc żaden pasek nie zasłania jej fragmentu
 * (także górnego), a panele i okna kontekstowe kotwiczą się do krawędzi strony.
 */
@Composable
fun EditorScreen(ed: Editor, tabs: TabsCtl, onExit: () -> Unit, onSettings: () -> Unit, onShortcuts: () -> Unit) {
    val ctx = LocalContext.current
    val set = ed.settings
    val m = mint
    var panel by remember { mutableStateOf<Panel?>(null) }
    var palette by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val dialogOpen = palette || goTo || ed.exportOpen || ed.textReq != null
    val searchTyping = ed.searchOpen && fieldFocused
    val widthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val showMenu = ed.chrome && menuBarVisible(set.menuBar, widthDp)
    // Układ „biurkowy” (LNX-016): przy szerokim oknie panel stron może być zadokowany z lewej, obok strony.
    val dock = ed.chrome && set.dockPages && widthDp >= 840
    val pos = set.toolbarPos
    // Na szerokim ekranie narzędzia mieszczą się w jednej linii z nagłówkiem (i kartami); inaczej dostają własny wiersz.
    val inlineBar = pos == 0 && widthDp - (if (dock) 244 else 0) >= 960

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ed.insertFromUri(uri) else ed.imageAsBackground = false
    }
    fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun toggle(p: Panel) { if (p == Panel.PAGES && dock) set.dockPages = false else panel = if (panel == p) null else p }
    // Pipeta chowa panele, żeby strona była widoczna.
    LaunchedEffect(ed.pipette != null) { if (ed.pipette != null) panel = null }
    fun applyLayout(mode: Int) { set.layoutMode = mode; ed.view?.applyLayoutMode() }
    fun pickTool(t: Tool, p: Panel?) {
        if (ed.tool == t && p != null) toggle(p) else { ed.setToolKey(t); panel = null }
    }

    val cmds = remember(set.keyMap, tabs.on) {
        commandList(ed, set, ::pickTool, { panel = it }, ::pickImage, { goTo = true }, ::applyLayout, onSettings, onShortcuts, { palette = true }, onExit, tabs)
    }

    BackHandler {
        when {
            panel != null -> panel = null
            ed.searchOpen -> ed.closeSearch()
            ed.ctxMenu != null -> ed.ctxMenu = null
            ed.sel != null -> ed.clearSel()
            !ed.chrome -> ed.chrome = true
            else -> onExit()
        }
    }
    LaunchedEffect(dialogOpen, fieldFocused, ed.searchOpen) { if (!dialogOpen && !searchTyping) runCatching { focus.requestFocus() } }

    val onDrop = { uris: List<android.net.Uri> ->
        if (!ed.readOnly) {
            var skipped = 0
            uris.forEach { if (!ed.insertFromUri(it)) skipped++ }
            if (skipped > 0) ed.say(T.t("Do zeszytu można upuścić obrazy; PDF i .mnotes upuść w bibliotece", "Only images can be dropped into a notebook; drop PDF and .mnotes files in the library"))
        }
    }
    Box(
        Modifier.fillMaxSize().background(m.canvasBg).fileDrop(onDrop).focusRequester(focus).focusable()
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && !dialogOpen) editorKey(e, ed, cmds) { palette = true } else false },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
            if (showMenu) MenuBar(ed, cmds, { runCatching { focus.requestFocus() } }, Modifier.fillMaxWidth())
            if (ed.chrome) TopChrome(ed, set, tabs, panel, { toggle(it) }, { goTo = true }, onExit, ::pickImage, ::pickTool, inlineBar, widthDp)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (dock) PagesPanel(ed, { set.dockPages = false }, ::pickImage, docked = true)
                if (ed.chrome && pos == 2) SideRail(ed, set, panel, ::pickTool, { toggle(it) }, right = false)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AndroidView(
                        factory = { c -> NoteView(c, ed).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) } },
                        update = { v -> v.accent = m.accent.toArgb(); v.viewBg = m.canvasBg.toArgb(); v.darkUi = m.dark },
                        // Pierwsze dotknięcie strony (pióro lub palec) zamyka otwarty panel, więc nigdy nie zasłania pisania.
                        modifier = Modifier.fillMaxSize().clipToBounds().pointerInput(Unit) {
                            awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); panel = null }
                        },
                    )
                    // Panele i okienka kotwiczą się do krawędzi strony, tuż przy pasku, z którego wyszły.
                    Box(Modifier.fillMaxSize().imePadding()) {
                        if (ed.chrome) {
                            panel?.takeIf { it == Panel.PEN || it == Panel.ERASER || it == Panel.LASSO || it == Panel.TAPE }?.let { p ->
                                val pa = when (pos) { 1 -> Alignment.BottomCenter; 2 -> Alignment.CenterStart; 3 -> Alignment.CenterEnd; else -> Alignment.TopCenter }
                                Box(Modifier.align(pa).padding(8.dp).panelEnter()) { ToolPanel(ed, set, p) { panel = null } }
                            }
                            if (panel == Panel.STYLE) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).panelEnter()) { StylePanel(ed) { panel = null } }
                            if (panel == Panel.PAGES && !dock) Box(Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp).panelEnter()) { PagesPanel(ed, { panel = null }, ::pickImage) }
                            if (panel == Panel.MORE) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).panelEnter()) { MoreMenu(ed, set, { panel = null }, ::applyLayout, { panel = Panel.STYLE }, { palette = true }, onSettings) }
                        } else {
                            IconBtn(
                                Icons.Rounded.Fullscreen, T.t("Pokaż pasek narzędzi", "Show toolbar"), { ed.chrome = true }, shortcut = "F11",
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(m.chrome.copy(alpha = 0.7f)),
                            )
                        }
                        SelectionBar(ed, panel, { toggle(it) }, { textReq(ed) })
                        if (ed.searchOpen) SearchBar(ed)
                        EdToast(ed)
                    }
                    CtxMenuOverlay(ed, { panel = Panel.STYLE })
                }
                if (ed.chrome && pos == 3) SideRail(ed, set, panel, ::pickTool, { toggle(it) }, right = true)
            }
            if (ed.chrome && pos == 1) BottomBar(ed, set, panel, ::pickTool) { toggle(it) }
        }

        ed.textReq?.let { TextDialog(ed, it) }
        if (goTo) GoToDialog(ed) { goTo = false }
        if (ed.exportOpen) ExportDialog(ed.nbId, ed.pageIndex, { ed.doc.flush() }) { ed.exportOpen = false }
        if (palette) PaletteDialog(cmds) { palette = false }
    }
}

private fun textReq(ed: Editor) {
    val s = ed.sel ?: return
    val el = ed.selElements().singleOrNull() as? TextBox ?: return
    ed.editText(s.pageId, el)
}

// ─────────────────────────────── Nagłówek ───────────────────────────────

@Composable
internal fun FloatingCard(modifier: Modifier = Modifier, radius: Int = 14, content: @Composable () -> Unit) {
    val m = mint
    val shape = RoundedCornerShape(radius.dp)
    // Miękki, rozproszony cień (mocniejszy kierunkowy, słabszy otaczający) i włosowata ramka — unosi kartę nad papierem jak w systemowych aplikacjach.
    Box(
        modifier.shadow(14.dp, shape, ambientColor = Color.Black.copy(if (m.dark) 0.5f else 0.14f), spotColor = Color.Black.copy(if (m.dark) 0.6f else 0.26f))
            .clip(shape).background(m.chrome).border(0.5.dp, m.line, shape),
    ) { content() }
}

/** Wejście panelu: krótkie rozjaśnienie z minimalnym „wyskoczeniem” spod paska (tylko przy pojawieniu się). */
@Composable
internal fun Modifier.panelEnter(): Modifier {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(170, easing = FastOutSlowInEasing)) }
    return graphicsLayer {
        val k = t.value
        alpha = k; scaleX = 0.96f + 0.04f * k; scaleY = scaleX; translationY = (1f - k) * -6.dp.toPx()
    }
}

@Composable
private fun Hairline(vertical: Boolean = false) {
    val m = mint
    Box((if (vertical) Modifier.fillMaxHeight().width(0.5.dp) else Modifier.fillMaxWidth().height(0.5.dp)).background(m.line))
}

/**
 * Zadokowany nagłówek: [biblioteka] [karty albo tytuł] ([narzędzia], jeśli się mieszczą) [strona/zoom/zakładka/więcej].
 * Gdy narzędzia się nie mieszczą w linii (węższe okno), dostają własny wiersz poniżej.
 */
@Composable
private fun TopChrome(
    ed: Editor, set: com.mdev.mnotes.data.Settings, tabs: TabsCtl, panel: Panel?, toggle: (Panel) -> Unit, goTo: () -> Unit,
    onExit: () -> Unit, pickImage: () -> Unit, pickTool: (Tool, Panel?) -> Unit, inlineBar: Boolean, widthDp: Int,
) {
    val m = mint
    val title = ed.repo.meta(ed.nbId)?.title ?: ""
    ed.structTick
    Column(Modifier.fillMaxWidth().background(m.chrome)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, T.t("Biblioteka", "Library"), onExit, shortcut = "Esc")
            if (tabs.showStrip) {
                TabStrip(tabs, Modifier.weight(if (inlineBar) 0.38f else 1f).padding(horizontal = 4.dp))
            } else {
                Row(Modifier.then(if (inlineBar) Modifier.widthIn(max = 220.dp) else Modifier.weight(1f)), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    if (ed.readOnly) Icon(Icons.Rounded.Lock, null, Modifier.padding(end = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (inlineBar) Spacer(Modifier.width(4.dp))
            }
            if (inlineBar) Box(Modifier.weight(if (tabs.showStrip) 0.62f else 1f), contentAlignment = Alignment.Center) { ToolBar(ed, set, false, panel, pickTool, toggle) }
            PageCluster(ed, panel, toggle, goTo, pickImage, widthDp >= 600)
        }
        if (set.toolbarPos == 0 && !inlineBar) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ToolBar(ed, set, false, panel, pickTool, toggle) }
        }
        Hairline()
    }
}

/** Prawa strona nagłówka: strona n/N (klik → „Idź do”), powiększenie, zakładka, panel stron, obraz, „Więcej”. */
@Composable
private fun PageCluster(ed: Editor, panel: Panel?, toggle: (Panel) -> Unit, goTo: () -> Unit, pickImage: () -> Unit, showZoom: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${ed.pageIndex + 1} / ${ed.pageCount}", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { goTo() }.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        if (showZoom) Text("${ed.zoomPct}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
        val bm = ed.isBookmarked(ed.pageIndex)
        IconBtn(if (bm) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, T.t("Zakładka", "Bookmark"), { ed.toggleBookmark() }, selected = bm, shortcut = "Ctrl+B")
        IconBtn(Icons.Rounded.ViewSidebar, T.t("Strony", "Pages"), { toggle(Panel.PAGES) }, selected = panel == Panel.PAGES, shortcut = "F9")
        IconBtn(Icons.Rounded.Image, T.t("Wstaw obraz", "Insert image"), pickImage, enabled = !ed.readOnly)
        IconBtn(Icons.Rounded.MoreVert, T.t("Więcej", "More"), { toggle(Panel.MORE) }, selected = panel == Panel.MORE)
    }
}

/** Szyna narzędzi z boku strony (pozycje „lewo” i „prawo”): zadokowana, przewijana pionowo, nie zasłania strony. */
@Composable
private fun SideRail(ed: Editor, set: com.mdev.mnotes.data.Settings, panel: Panel?, pickTool: (Tool, Panel?) -> Unit, toggle: (Panel) -> Unit, right: Boolean) {
    val m = mint
    Row(Modifier.fillMaxHeight()) {
        if (right) Hairline(vertical = true)
        Box(Modifier.fillMaxHeight().background(m.chrome), contentAlignment = Alignment.Center) { ToolBar(ed, set, true, panel, pickTool, toggle) }
        if (!right) Hairline(vertical = true)
    }
}

/** Pasek narzędzi na dole (pozycja „dół”): zadokowany pod stroną. */
@Composable
private fun BottomBar(ed: Editor, set: com.mdev.mnotes.data.Settings, panel: Panel?, pickTool: (Tool, Panel?) -> Unit, toggle: (Panel) -> Unit) {
    val m = mint
    Column(Modifier.fillMaxWidth().background(m.chrome)) {
        Hairline()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ToolBar(ed, set, false, panel, pickTool, toggle) }
    }
}

// ─────────────────────────────── Pasek narzędzi ───────────────────────────────

@Composable
private fun Lin(vertical: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (vertical) Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    else Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

@Composable
private fun BarSep(vertical: Boolean) {
    val m = mint
    if (vertical) Box(Modifier.padding(vertical = 4.dp).width(24.dp).height(1.dp).background(m.line))
    else Box(Modifier.padding(horizontal = 4.dp).height(24.dp).width(1.dp).background(m.line))
}

@Composable
private fun ToolBar(ed: Editor, set: com.mdev.mnotes.data.Settings, vertical: Boolean, panel: Panel?, pickTool: (Tool, Panel?) -> Unit, toggle: (Panel) -> Unit) {
    val m = mint
    val sz = when (set.toolbarSize) { 0 -> 36.dp; 2 -> 46.dp; else -> 40.dp }
    val isz = when (set.toolbarSize) { 0 -> 20.dp; 2 -> 26.dp; else -> 22.dp }
    val ro = ed.readOnly
    run {
        Lin(vertical, Modifier.padding(4.dp).then(if (vertical) Modifier.verticalScroll(rememberScrollState()) else Modifier.horizontalScroll(rememberScrollState()))) {
            IconBtn(Icons.AutoMirrored.Rounded.Undo, T.t("Cofnij", "Undo"), { ed.undo() }, enabled = ed.canUndo && !ro, size = sz, iconSize = isz, shortcut = "Ctrl+Z")
            IconBtn(Icons.AutoMirrored.Rounded.Redo, T.t("Ponów", "Redo"), { ed.redo() }, enabled = ed.canRedo && !ro, size = sz, iconSize = isz, shortcut = "Ctrl+Y")
            BarSep(vertical)
            val pk = ed.preset.kind
            ToolBtn(penName(pk), { pickTool(Tool.PEN, Panel.PEN) }, enabled = !ro, selected = ed.tool == Tool.PEN, size = sz, shortcut = "P") { c ->
                PenGlyph(pk, Color(ed.preset.color).let { if (it.alpha > 0f) it else c }, c, Modifier.size(isz + 2.dp))
            }
            IconBtn(MIcons.Eraser, T.t("Gumka", "Eraser"), { pickTool(Tool.ERASER, Panel.ERASER) }, enabled = !ro, selected = ed.tool == Tool.ERASER, size = sz, iconSize = isz, shortcut = "E")
            IconBtn(if (set.lassoRect) Icons.Rounded.HighlightAlt else Icons.Rounded.Gesture, T.t("Lasso", "Lasso"), { pickTool(Tool.LASSO, Panel.LASSO) }, enabled = !ro, selected = ed.tool == Tool.LASSO, size = sz, iconSize = isz, shortcut = "L")
            IconBtn(Icons.Rounded.TextFields, T.t("Tekst", "Text"), { pickTool(Tool.TEXT, null) }, enabled = !ro, selected = ed.tool == Tool.TEXT, size = sz, iconSize = isz, shortcut = "T")
            IconBtn(Icons.Rounded.TrackChanges, T.t("Laser", "Laser"), { pickTool(Tool.LASER, null) }, selected = ed.tool == Tool.LASER, size = sz, iconSize = isz, shortcut = "O")
            IconBtn(Icons.Rounded.PanTool, T.t("Ręka (przewijanie)", "Hand (pan)"), { pickTool(Tool.HAND, null) }, selected = ed.tool == Tool.HAND, size = sz, iconSize = isz, shortcut = "H")
            IconBtn(Icons.Rounded.VisibilityOff, T.t("Taśma — zakryj fragment (do nauki)", "Tape — cover an area (for study)"), { pickTool(Tool.TAPE, Panel.TAPE) }, enabled = !ro, selected = ed.tool == Tool.TAPE, size = sz, iconSize = isz, shortcut = "K")
            IconBtn(Icons.Rounded.UnfoldMore, T.t("Wstaw / usuń odstęp", "Insert / remove space"), { pickTool(Tool.SPACE, null) }, enabled = !ro, selected = ed.tool == Tool.SPACE, size = sz, iconSize = isz, shortcut = "X")
            IconBtn(Icons.Rounded.Straighten, T.t("Linijka", "Ruler"), { ed.toggleRuler() }, enabled = !ro, selected = ed.ruler, size = sz, iconSize = isz, shortcut = "R")
            BarSep(vertical)
            ed.presets.forEachIndexed { i, p ->
                val active = i == ed.presetIndex && ed.tool == Tool.PEN
                PresetDot(p, active, sz) { if (!ro) { if (active) toggle(Panel.PEN) else { ed.selectPreset(i); if (ed.tool != Tool.PEN) ed.setToolKey(Tool.PEN) } } }
            }
            IconBtn(Icons.Rounded.Add, T.t("Dodaj pióro", "Add pen"), { ed.addPreset(ed.preset); toggle(Panel.PEN) }, enabled = !ro && ed.presets.size < 10, size = sz, iconSize = isz)
        }
    }
}

@Composable
private fun PresetDot(p: PenPreset, active: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val m = mint
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val tick = rememberTick()
    val ring by animateColorAsState(if (active) m.accent else Color.Transparent, tween(150), label = "presetRing")
    val halo by animateColorAsState(if (active) m.accentSoft else Color.Transparent, tween(150), label = "presetHalo")
    val scale by animateFloatAsState(if (pressed) 0.84f else if (active) 1.05f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "presetScale")
    Box(
        Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
            .clickable(interactionSource = src, indication = null, role = Role.Button) { tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        val d = (14 + (p.width.coerceIn(0.5f, 14f) / 14f) * 8).dp
        Box(
            Modifier.size(d + 8.dp).clip(CircleShape).background(halo).border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(d).clip(CircleShape).background(Color(p.color)).border(1.dp, m.line, CircleShape)) }
    }
}

// ─────────────────────────────── Panele narzędzi ───────────────────────────────

@Composable
private fun ToolPanel(ed: Editor, set: com.mdev.mnotes.data.Settings, panel: Panel, close: () -> Unit) {
    val m = mint
    FloatingCard(Modifier.width(320.dp)) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (panel) {
                Panel.PEN -> {
                    val i = ed.presetIndex
                    val p = ed.preset
                    val spec = PenSpec.of(p.kind)
                    SectionLabel(T.t("Pióro", "Pen"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (k in PenKind.entries) Chip(penName(k), k == p.kind, {
                            val ns = PenSpec.of(k)
                            ed.updatePreset(i, p.copy(kind = k, color = if (k == PenKind.HIGHLIGHTER && p.kind != PenKind.HIGHLIGHTER) softenForHighlighter(p.color) else p.color, width = if (k == PenKind.HIGHLIGHTER || p.kind == PenKind.HIGHLIGHTER) ns.defaultWidth else p.width.coerceIn(ns.minWidth, ns.maxWidth)))
                        }, glyph = { c -> PenGlyph(k, Color(p.color).let { if (it.alpha > 0f) it else c }, c, Modifier.size(20.dp)) })
                    }
                    Spacer(Modifier.height(4.dp))
                    PenTester(p, set.smoothing)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(T.t("Grubość", "Width")); Spacer(Modifier.weight(1f))
                        Text(String.format("%.1f pt", p.width), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MSlider(p.width, spec.minWidth, spec.maxWidth) { ed.updatePreset(i, p.copy(width = it)) }
                    SectionLabel(T.t("Kolor", "Color"))
                    ColorPicker(p.color, { ed.updatePreset(i, p.copy(color = it)) }, highlighter = p.kind == PenKind.HIGHLIGHTER, recent = ed.presets.filter { (it.kind == PenKind.HIGHLIGHTER) == (p.kind == PenKind.HIGHLIGHTER) }.map { it.color }.distinct(), onPipette = { ed.startPipette { c -> ed.presets.getOrNull(i)?.let { q -> ed.updatePreset(i, q.copy(color = c)) } } })
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = m.line)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(T.t("Wygładzanie", "Smoothing")); Spacer(Modifier.weight(1f))
                        Text("${(set.smoothing * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MSlider(set.smoothing, 0f, 2f) { set.smoothing = it }
                    ToggleRow(T.t("Przytrzymaj, aby wyprostować kształt", "Hold to snap to shape"), set.holdToShape) { set.holdToShape = it }
                    if (ed.presets.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (i > 0) Chip(T.t("Przesuń wcześniej", "Move earlier"), false, { ed.movePreset(i, i - 1) }, icon = Icons.AutoMirrored.Rounded.ArrowBack)
                        if (i < ed.presets.size - 1) Chip(T.t("Przesuń później", "Move later"), false, { ed.movePreset(i, i + 1) }, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                    }
                    if (ed.presets.size > 1) Chip(T.t("Usuń to pióro", "Remove this pen"), false, { ed.removePreset(i) }, icon = Icons.Rounded.Delete)
                }
                Panel.ERASER -> {
                    SectionLabel(T.t("Tryb gumki", "Eraser mode"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(T.t("Obiektowa", "Object"), set.eraserMode == 0, { set.eraserMode = 0 })
                        Chip(T.t("Pikselowa", "Pixel"), set.eraserMode == 1, { set.eraserMode = 1 })
                        Chip(T.t("Lasso", "Lasso"), set.eraserMode == 2, { set.eraserMode = 2 })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(T.t("Rozmiar", "Size")); Spacer(Modifier.weight(1f))
                        Text(String.format("%.0f pt", set.eraserSize), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MSlider(set.eraserSize, 3f, 60f) { set.eraserSize = it }
                    ToggleRow(T.t("Nie ruszaj zakreślacza", "Keep highlighter"), set.eraserKeepHighlighter) { set.eraserKeepHighlighter = it }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = m.line)
                    Chip(T.t("Wyczyść stronę", "Clear page"), false, { ed.clearPage(); close() }, icon = Icons.Rounded.Delete)
                }
                Panel.TAPE -> {
                    SectionLabel(T.t("Kolor taśmy", "Tape colour"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in intArrayOf(0xFFFFD54F.toInt(), 0xFFFF8A80.toInt(), 0xFF80D8FF.toInt(), 0xFFB9F6CA.toInt(), 0xFFE1BEE7.toInt(), 0xFF90A4AE.toInt()))
                            Dot(Color(col), 30.dp, selected = set.tapeColor == col, onClick = { set.tapeColor = col })
                    }
                    Chip(T.t("Zakryj / odkryj wszystkie", "Cover / reveal all"), false, { ed.toggleAllTapes() }, icon = Icons.Rounded.VisibilityOff)
                    Text(T.t("Taśma zakrywa to, co pod nią. Dotknij jej, by odkryć lub zakryć. Gumka jej nie usuwa — zaznacz lassem i usuń.", "A tape hides what is under it. Tap it to reveal or cover. The eraser leaves it alone — select with lasso and delete."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    SectionLabel(T.t("Kształt lassa", "Lasso shape"))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(T.t("Odręczne", "Freehand"), !set.lassoRect, { set.lassoRect = false }, icon = Icons.Rounded.Gesture)
                        Chip(T.t("Prostokąt", "Rectangle"), set.lassoRect, { set.lassoRect = true }, icon = Icons.Rounded.HighlightAlt)
                    }
                    ToggleRow(T.t("Przyciągaj do siatki", "Snap to grid"), ed.gridSnap) { ed.gridSnap = it; set.snapGrid = it }
                    Text(T.t("Ctrl+A zaznacza wszystko na stronie.", "Ctrl+A selects everything on the page."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun MSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    val m = mint
    Slider(
        value.coerceIn(min, max), onChange, valueRange = min..max, modifier = Modifier.fillMaxWidth().height(32.dp),
        colors = SliderDefaults.colors(thumbColor = m.accent, activeTrackColor = m.accent, inactiveTrackColor = m.line),
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val m = mint
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked, onChange, modifier = Modifier.height(28.dp),
            colors = SwitchDefaults.colors(checkedTrackColor = m.accent, checkedThumbColor = m.onAccent),
        )
    }
}

/**
 * Próbnik pióra (EDT-009): małe pole, w którym można popisać bieżącym piórem (te same wygładzanie, nacisk i
 * kształt kreski co na stronie) bez dotykania zeszytu. Ślady zostają do wyczyszczenia lub zamknięcia panelu.
 */
@Composable
private fun PenTester(p: PenPreset, smoothing: Float) {
    val m = mint
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val renderer = remember { PageRenderer(null) }
    val builder = remember { StrokeBuilder() }
    val strokes = remember { androidx.compose.runtime.mutableStateListOf<com.mdev.mnotes.core.model.Stroke>() }
    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val ghost = if (strokes.isEmpty() && builder.count == 0)
        T.t("Wypróbuj pióro tutaj", "Try the pen here") else null
    Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFDFDFB)).border(1.dp, m.line, RoundedCornerShape(8.dp))) {
        Canvas(
            Modifier.matchParentSize().pointerInput(p.kind, p.color, p.width, smoothing) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val spec = PenSpec.of(p.kind)
                    builder.begin(p.kind, p.color, p.width, (spec.smoothing * smoothing).coerceIn(0f, 1f), 0.5f)
                    fun pr(c: androidx.compose.ui.input.pointer.PointerInputChange) =
                        if (c.type == androidx.compose.ui.input.pointer.PointerType.Stylus) c.pressure else 0.5f
                    fun feed(c: androidx.compose.ui.input.pointer.PointerInputChange) {
                        for (h in c.historical) builder.add(h.position.x / dens, h.position.y / dens, pr(c), h.uptimeMillis)
                        builder.add(c.position.x / dens, c.position.y / dens, pr(c), c.uptimeMillis)
                    }
                    feed(down); tick++
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        c.consume()
                        if (!c.pressed) break
                        feed(c); tick++
                    }
                    builder.finish(com.mdev.mnotes.core.model.Ids.next())?.let { strokes.add(it); if (strokes.size > 10) strokes.removeAt(0) }
                    builder.reset(); tick++
                }
            },
        ) {
            tick
            drawIntoCanvas { cv ->
                val c = cv.nativeCanvas
                c.save(); c.scale(dens, dens)
                for (st in strokes) renderer.drawStrokeData(c, st.pen, st.color, st.width, st.pts, st.count, dens)
                if (builder.count > 0) renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, builder.pts, builder.count, dens)
                c.restore()
            }
        }
        if (ghost != null) Text(ghost, Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA0A6))
        if (strokes.isNotEmpty()) IconBtn(Icons.Rounded.Delete, T.t("Wyczyść próbnik", "Clear scratch pad"), { strokes.clear(); tick++ }, size = 28.dp, iconSize = 16.dp, modifier = Modifier.align(Alignment.TopEnd))
    }
}

// ─────────────────────────────── Pasek zaznaczenia ───────────────────────────────

@Composable
private fun SelectionBar(ed: Editor, panel: Panel?, toggle: (Panel) -> Unit, editText: () -> Unit) {
    val sel = ed.sel ?: return
    ed.selTick; ed.viewTick; ed.undoTick
    val v = ed.view ?: return
    val r = v.selScreenRect() ?: return
    val els = ed.selElements()
    if (els.isEmpty()) return
    val locked = els.all { it.locked }
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    Box(
        Modifier.fillMaxSize().layout { mm, c ->
            val p = mm.measure(c.copy(minWidth = 0, minHeight = 0))
            layout(c.maxWidth, c.maxHeight) {
                val x = (r.centerX() - p.width / 2f).toInt().coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0))
                // przy włączonym skalowaniu/obrocie uchwyt obrotu wisi nad ramką — pasek leży nad nim
                val above = r.top - p.height - (if (ed.transformMode && !locked) NoteView.ROTATE_OFFSET_DP + 22 else 14f) * dens
                val y = (if (above > 4) above else r.bottom + 14 * dens).toInt().coerceIn(0, (c.maxHeight - p.height).coerceAtLeast(0))
                p.place(x, y)
            }
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FloatingCard {
                Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.ContentCut, T.t("Wytnij", "Cut"), { ed.cutSel() }, size = 36.dp, iconSize = 20.dp, shortcut = "Ctrl+X")
                    IconBtn(Icons.Rounded.ContentCopy, T.t("Kopiuj", "Copy"), { ed.copySel() }, size = 36.dp, iconSize = 20.dp, shortcut = "Ctrl+C")
                    IconBtn(Icons.Rounded.ContentCopy, T.t("Duplikuj", "Duplicate"), { ed.duplicateSel() }, size = 36.dp, iconSize = 20.dp, shortcut = "Ctrl+D", tint = mint.accentText)
                    BarSep(false)
                    IconBtn(Icons.Rounded.Palette, T.t("Kolor i grubość", "Color & width"), { toggle(Panel.SELCOLOR) }, selected = panel == Panel.SELCOLOR, size = 36.dp, iconSize = 20.dp)
                    if (!locked) IconBtn(Icons.Rounded.CropRotate, T.t("Skaluj i obracaj", "Resize & rotate"), { ed.transformMode = !ed.transformMode }, selected = ed.transformMode, size = 36.dp, iconSize = 20.dp)
                    IconBtn(Icons.Rounded.FlipToFront, T.t("Na wierzch", "Bring to front"), { ed.frontSel() }, size = 36.dp, iconSize = 20.dp)
                    IconBtn(Icons.Rounded.FlipToBack, T.t("Na spód", "Send to back"), { ed.backSel() }, size = 36.dp, iconSize = 20.dp)
                    IconBtn(if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, if (locked) T.t("Odblokuj", "Unlock") else T.t("Zablokuj", "Lock"), { ed.lockSel(!locked) }, size = 36.dp, iconSize = 20.dp)
                    if (els.size > 1) IconBtn(Icons.Rounded.AlignHorizontalLeft, T.t("Wyrównaj", "Align"), { toggle(Panel.ALIGN) }, selected = panel == Panel.ALIGN, size = 36.dp, iconSize = 20.dp)
                    if (els.size == 1 && els[0] is TextBox) IconBtn(Icons.Rounded.Edit, T.t("Edytuj tekst", "Edit text"), editText, size = 36.dp, iconSize = 20.dp)
                    BarSep(false)
                    IconBtn(Icons.Rounded.Delete, T.t("Usuń", "Delete"), { ed.deleteSel() }, size = 36.dp, iconSize = 20.dp, shortcut = "Del", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (panel == Panel.SELCOLOR) FloatingCard(Modifier.width(300.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val colors = els.mapNotNull { colorOf(it) }.map { it and 0xFFFFFF }.distinct()
                    val first = els.firstOrNull { colorOf(it) != null }
                    val hasStroke = els.any { it is InkStroke }
                    val img = els.firstOrNull { it is ImageEl } as? ImageEl
                    SectionLabel(T.t("Kolor", "Color"))
                    ColorPicker(first?.let { colorOf(it) } ?: 0xFF000000.toInt(), { ed.recolorSel(it) }, mixed = colors.size > 1, recent = ed.presets.map { it.color }.distinct(), onPipette = { ed.startPipette { c -> ed.recolorSel(c) } })
                    if (hasStroke) {
                        val w = (els.firstOrNull { it is InkStroke } as InkStroke).width
                        SectionLabel(T.t("Grubość", "Width"))
                        MSlider(w, 0.3f, 24f) { ed.setSelWidth(it) }
                    }
                    if (img != null) { SectionLabel(T.t("Krycie obrazu", "Image opacity")); MSlider(img.alpha, 0.1f, 1f) { ed.setSelAlpha(it) } }
                }
            }
            if (panel == Panel.ALIGN) FloatingCard {
                Row(Modifier.padding(3.dp)) {
                    val icons = listOf(Icons.Rounded.AlignHorizontalLeft, Icons.Rounded.AlignHorizontalCenter, Icons.Rounded.AlignHorizontalRight, Icons.Rounded.AlignVerticalTop, Icons.Rounded.AlignVerticalCenter, Icons.Rounded.AlignVerticalBottom)
                    val names = listOf(T.t("Do lewej", "Left"), T.t("Środek poziomo", "Center H"), T.t("Do prawej", "Right"), T.t("Do góry", "Top"), T.t("Środek pionowo", "Center V"), T.t("Do dołu", "Bottom"))
                    icons.forEachIndexed { i, ic -> IconBtn(ic, names[i], { ed.alignSel(i) }, size = 36.dp, iconSize = 20.dp) }
                }
            }
        }
    }
}

/** Kolor elementu, albo null, gdy element nie ma koloru (obraz, taśma). */
private fun colorOf(e: com.mdev.mnotes.core.model.Element?): Int? = when (e) {
    is InkStroke -> e.color
    is TextBox -> e.color
    else -> null
}

// ─────────────────────────────── Menu kontekstowe, toast ───────────────────────────────

@Composable
private fun CtxMenuOverlay(ed: Editor, openStyle: () -> Unit) {
    val cm = ed.ctxMenu ?: return
    fun close() { ed.ctxMenu = null }
    @Composable
    fun Item(text: String, hint: String = "", enabled: Boolean = true, onClick: () -> Unit) = DropdownMenuItem(
        text = { Text(text) }, enabled = enabled,
        trailingIcon = if (hint.isNotEmpty()) { { Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } else null,
        onClick = { close(); onClick() },
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.offset { IntOffset(cm.x.toInt(), cm.y.toInt()) }) {
            DropdownMenu(true, { close() }) {
                if (cm.hasSel) {
                    Item(T.t("Wytnij", "Cut"), "Ctrl+X") { ed.cutSel() }
                    Item(T.t("Kopiuj", "Copy"), "Ctrl+C") { ed.copySel() }
                    Item(T.t("Duplikuj", "Duplicate"), "Ctrl+D") { ed.duplicateSel() }
                }
                Item(T.t("Wklej", "Paste"), "Ctrl+V", ed.canPaste() && !ed.readOnly) { ed.paste(if (cm.pageIndex >= 0) Triple(cm.pageIndex, cm.lx, cm.ly) else null) }
                if (cm.hasSel) {
                    Item(T.t("Na wierzch", "Bring to front")) { ed.frontSel() }
                    Item(T.t("Na spód", "Send to back")) { ed.backSel() }
                    Item(T.t("Usuń", "Delete"), "Del") { ed.deleteSel() }
                }
                HorizontalDivider()
                Item(T.t("Zaznacz wszystko na stronie", "Select all on page"), "Ctrl+A") { ed.setToolKey(Tool.LASSO); ed.selectAllOnPage() }
                Item(T.t("Dodaj stronę po tej", "Add page after this"), "Ctrl+Shift+N") { ed.addPage(cm.pageIndex) }
                Item(T.t("Styl strony…", "Page style…")) { openStyle() }
                HorizontalDivider()
                Item(T.t("Cofnij", "Undo"), "Ctrl+Z", ed.canUndo) { ed.undo() }
                Item(T.t("Ponów", "Redo"), "Ctrl+Y", ed.canRedo) { ed.redo() }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.EdToast(ed: Editor) {
    val msg = ed.toast
    LaunchedEffect(msg) { if (msg != null) { delay(2200); if (ed.toast == msg) ed.toast = null } }
    if (msg == null) return
    Text(
        msg, color = Color.White, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xE6222428)).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

// ─────────────────────────────── Menu „Więcej”, styl strony ───────────────────────────────

@Composable
private fun MoreMenu(ed: Editor, set: com.mdev.mnotes.data.Settings, close: () -> Unit, applyLayout: (Int) -> Unit, openStyle: () -> Unit, palette: () -> Unit, onSettings: () -> Unit) {
    val m = mint
    FloatingCard(Modifier.width(280.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel(T.t("Układ stron", "Page layout"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(T.t("Pionowo", "Vertical"), set.layoutMode == 0, { applyLayout(0) })
                Chip(T.t("Poziomo", "Horizontal"), set.layoutMode == 1, { applyLayout(1) })
                Chip(T.t("Pojedyncza", "Single"), set.layoutMode == 2, { applyLayout(2) })
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = m.line)
            MenuRow(Icons.Rounded.FitScreen, T.t("Dopasuj do szerokości", "Fit width"), "Ctrl+0") { ed.view?.fitWidth(); close() }
            MenuRow(Icons.Rounded.Palette, T.t("Styl strony…", "Page style…"), "") { openStyle() }
            MenuRow(Icons.Rounded.Fullscreen, T.t("Ukryj paski (tryb skupienia)", "Hide bars (focus mode)"), "F11") { ed.chrome = false; close() }
            ToggleRow(T.t("Tylko do odczytu", "Read-only"), ed.readOnly) { ed.readOnly = it }
            ToggleRow(T.t("Okno powiększenia (pisz w powiększeniu)", "Zoom window (write magnified)"), ed.zoomWin) { ed.toggleZoomWin(); close() }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = m.line)
            MenuRow(Icons.Rounded.Search, T.t("Szukaj w zeszycie…", "Search in notebook…"), "Ctrl+F") { close(); ed.openSearch() }
            MenuRow(Icons.Rounded.IosShare, T.t("Eksport i udostępnianie…", "Export & share…"), "Ctrl+E") { close(); ed.exportOpen = true }
            MenuRow(Icons.Rounded.Search, T.t("Paleta poleceń", "Command palette"), "Ctrl+K") { close(); palette() }
            MenuRow(Icons.Rounded.Settings, T.t("Ustawienia", "Settings"), "") { close(); onSettings() }
        }
    }
}

@Composable
fun MenuRow(icon: ImageVector, text: String, hint: String, onClick: () -> Unit) {
    val m = mint
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StylePanel(ed: Editor, close: () -> Unit) {
    val m = mint
    ed.structTick
    val idx = ed.pageIndex.coerceIn(0, (ed.doc.pages.size - 1).coerceAtLeast(0))
    val spec = ed.doc.pages.getOrNull(idx)?.spec ?: return
    var all by remember { mutableStateOf(false) }
    val isPdf = spec.pdf != null
    fun apply(f: (com.mdev.mnotes.core.model.PageSpec) -> com.mdev.mnotes.core.model.PageSpec) {
        if (all) ed.restyleAll { if (it.pdf != null) it else f(it) } else ed.setSpec(idx, f(spec))
    }
    FloatingCard(Modifier.width(320.dp)) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(T.t("Styl strony", "Page style"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconBtn(Icons.Rounded.Close, T.t("Zamknij", "Close"), close, size = 32.dp, iconSize = 18.dp)
            }
            ToggleRow(T.t("Zastosuj do całego zeszytu", "Apply to whole notebook"), all) { all = it }
            if (isPdf) Text(T.t("Strona PDF — wzór i papier pochodzą z dokumentu.", "PDF page — template and paper come from the document."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                SectionLabel(T.t("Wzór", "Template"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in com.mdev.mnotes.core.model.Template.entries) Chip(templateName(t), t == spec.template, { apply { it.withTemplate(t) } })
                }
                SectionLabel(T.t("Papier", "Paper"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (p in com.mdev.mnotes.core.model.Paper.presets) Dot(Color(p), 30.dp, selected = p == spec.paper, onClick = { apply { s -> s.copy(paper = p, line = com.mdev.mnotes.core.model.Paper.autoLineColor(p)) } })
                }
                if (spec.template.defaultSpacing > 0f) {
                    SectionLabel(T.t("Rozstaw wzoru", "Pattern spacing"))
                    MSlider(spec.spacing, 4f, 60f) { v -> apply { it.copy(spacing = v) } }
                }
                SectionLabel(T.t("Rozmiar", "Size"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in com.mdev.mnotes.core.model.PageFormat.entries) Chip(formatName(f), false, {
                        val land = spec.w > spec.h
                        val (w, h) = if (land) f.landscape() else f.w to f.h
                        apply { it.withSize(w, h) }
                    })
                }
            }
        }
    }
}

// ─────────────────────────────── Skróty klawiszowe ───────────────────────────────

private fun editorKey(e: androidx.compose.ui.input.key.KeyEvent, ed: Editor, cmds: List<Cmd>, openPalette: () -> Unit): Boolean {
    val shift = e.isShiftPressed; val k = e.key
    // Skróty stałe (nieedytowalne): alternatywy i klawisze kontekstowe.
    if (e.isCtrlPressed) {
        if (k == Key.Z && shift && !fieldFocused) { ed.redo(); return true }
        if (k == Key.P && shift) { openPalette(); return true }
    } else if (!fieldFocused) {
        when (k) {
            Key.Backspace -> if (ed.sel != null && !ed.readOnly) { ed.deleteSel(); return true }
            Key.Escape -> {
                if (ed.pipette != null) ed.cancelPipette() else if (ed.searchOpen) ed.closeSearch() else if (ed.sel != null) ed.clearSel() else if (!ed.chrome) ed.chrome = true else return false
                return true
            }
            Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> if (ed.sel != null && !ed.readOnly) {
                val step = if (shift) 10f else 1f
                ed.nudgeSel(when (k) { Key.DirectionLeft -> -step; Key.DirectionRight -> step; else -> 0f }, when (k) { Key.DirectionUp -> -step; Key.DirectionDown -> step; else -> 0f })
                return true
            }
            else -> {}
        }
    }
    val hit = cmds.firstOrNull { it.chord?.matches(e) == true } ?: return false
    val c = hit.chord!!
    // W polu tekstowym działają tylko skróty z Ctrl/Alt lub klawisze funkcyjne, a edycja tekstu zostaje po stronie pola.
    if (fieldFocused && (hit.def.textEdit || !(c.ctrl || c.alt || c.isFunctionKey()))) return false
    if (!hit.enabled) return false
    hit.run()
    return true
}
