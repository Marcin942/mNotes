package com.mdev.mnotes.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
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
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.HorizontalSplit
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
import androidx.compose.material.icons.rounded.WidthFull
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Flip
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
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.WbAuto
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mdev.mnotes.app.app
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.ImageFx
import com.mdev.mnotes.core.model.ImageTone
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.ui.unit.Dp
import com.mdev.mnotes.core.layout.BarEntry
import com.mdev.mnotes.core.layout.BarConfig
import com.mdev.mnotes.core.layout.BarFit
import com.mdev.mnotes.core.layout.BarMode
import com.mdev.mnotes.core.pen.ActivePen
import com.mdev.mnotes.core.pen.ColorSet
import com.mdev.mnotes.core.pen.InkColor
import com.mdev.mnotes.core.pen.LaserColors
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.pen.PenKit
import com.mdev.mnotes.editor.Clip
import com.mdev.mnotes.core.edit.CropGeom
import com.mdev.mnotes.core.edit.CardPlacement
import com.mdev.mnotes.core.edit.CardSide
import androidx.compose.foundation.layout.PaddingValues
import com.mdev.mnotes.editor.CropRatio
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.NoteView
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.editor.Tool
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate

enum class Panel { PEN, ERASER, LASSO, TAPE, PAINTER, FILL, PAGES, STYLE, MORE, SELCOLOR, IMAGEFX, ALIGN }

/** Własne ikony tam, gdzie Material Icons nie ma odpowiednika (gumka). */
object MIcons {
    private fun make(name: String, d: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black),
    ).build()

    val Eraser: ImageVector by lazy {
        make("Eraser", "M16.24 3.56l4.95 4.94c.78.79.78 2.05 0 2.84L12 20.53a4.008 4.008 0 0 1-5.66 0L2.81 17c-.78-.79-.78-2.05 0-2.84l10.6-10.6c.79-.78 2.05-.78 2.83 0zM4.22 15.58l3.54 3.53c.78.79 2.04.79 2.83 0l3.53-3.53-4.95-4.95-4.95 4.95z")
    }
}

/** Nazwa zestawu kolorów: wbudowane mają ją w tłumaczeniach, własne noszą tę nadaną przez użytkownika. */
fun setName(s: ColorSet) = s.name.ifEmpty {
    when (s.id) {
        PenKit.SET_INK -> T.t("Atramenty", "Inks"); PenKit.SET_BRUSH -> T.t("Pędzel", "Brush")
        PenKit.SET_HL -> T.t("Zakreślacze", "Highlighters"); else -> T.t("Własny zestaw", "Own set")
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
fun EditorScreen(ed: Editor, tabs: TabsCtl, onExit: () -> Unit, onSettings: () -> Unit, onShortcuts: () -> Unit, onColorSets: () -> Unit) {
    val ctx = LocalContext.current
    val set = ed.settings
    val m = mint
    var panel by remember { mutableStateOf<Panel?>(null) }
    var palette by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val dialogOpen = palette || goTo || ed.exportOpen || ed.axesOpen || ed.textReq != null
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
    LaunchedEffect(ed.tool) { if (ed.tool != Tool.PAINTER) { ed.formatPick = false; ed.quickFormat = false } }
    // Szybki przycisk „Pobierz format” gaśnie sam po czasie z ustawień; w trakcie pobierania formatu czas stoi.
    LaunchedEffect(ed.quickFormat, ed.formatPick) {
        if (ed.quickFormat && !ed.formatPick && set.painterQuickSec > 0) { delay(set.painterQuickSec * 1000L); ed.quickFormat = false }
    }
    LaunchedEffect(ed.pipette != null) { if (ed.pipette != null) panel = null }
    fun applyLayout(mode: Int) = ed.chooseLayoutMode(mode)
    fun pickTool(t: Tool, p: Panel?) {
        if (ed.tool == t && p != null) toggle(p) else { ed.setToolKey(t); panel = null }
    }
    // Przytrzymanie ikony narzędzia z panelem wybiera je i od razu otwiera jego panel.
    fun holdTool(t: Tool, p: Panel) { if (ed.tool != t) ed.setToolKey(t); panel = p }
    // Inne pióro przy otwartym panelu pióra zostawia panel na wierzchu: pokazuje wtedy ustawienia nowego.
    fun pickPen(k: PenKind) {
        when {
            // Pędzel formatu i wiaderko biorą styl z pióra, więc inne pióro tylko zmienia ich barwę; dopiero
            // dotknięcie tego, które już jest w użyciu, wraca do pisania.
            (ed.tool == Tool.PAINTER || ed.tool == Tool.FILL || ed.tool == Tool.CURVE) && ed.kit.active != k -> ed.editKit { it.withActive(k) }
            ed.tool == Tool.PEN && ed.kit.active == k -> toggle(Panel.PEN)
            else -> { ed.selectPen(k); if (panel != Panel.PEN) panel = null }
        }
    }
    // Listwa pióra stoi obok strony; na wąskim ekranie szyna boczna zabrałaby za dużo kartki, więc leży przy pasku.
    // „Przy pasku” dla paska bocznego znaczy obok niego, po tej samej stronie kartki.
    val strip = when {
        !ed.chrome || ed.readOnly -> -1
        widthDp < 600 -> 2
        set.optionsPos == 2 && pos == 2 -> 0
        set.optionsPos == 2 && pos == 3 -> 1
        else -> set.optionsPos
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
            if (ed.chrome) TopChrome(ed, set, tabs, panel, { toggle(it) }, { goTo = true }, onExit, ::pickImage, ::pickTool, ::holdTool, ::pickPen, inlineBar, widthDp)
            if (strip == 2 && pos != 1) PenStrip(ed, set, 2, pos, panel, onColorSets) { toggle(it) }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (dock) PagesPanel(ed, { set.dockPages = false }, ::pickImage, docked = true)
                if (ed.chrome && pos == 2) SideRail(ed, set, panel, ::pickTool, ::holdTool, ::pickPen, right = false)
                if (strip == 0) PenStrip(ed, set, 0, pos, panel, onColorSets) { toggle(it) }
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
                        // Pod panelami: otwarty panel zasłania kapsułę, a pierwsze dotknięcie strony go zamyka.
                        if (ed.pipette != null) Box(Modifier.align(Alignment.TopCenter).padding(top = 50.dp).panelEnter()) { PipetteCard(set) }
                        if (ed.cut != null) CutBarHost(ed)
                        if (ed.crop != null) CropBarHost(ed)
                        if (set.undoDock != 0) UndoCapsule(ed, set.undoDock, Modifier.align(undoCorner(set.undoDock)).padding(8.dp).padding(start = if (!ed.chrome && set.undoDock == 1) 48.dp else 0.dp))
                        if (ed.chrome) {
                            panel?.takeIf { it == Panel.PEN || it == Panel.ERASER || it == Panel.LASSO || it == Panel.TAPE || it == Panel.PAINTER || it == Panel.FILL }?.let { p ->
                                val pa = when {
                                    // Panel pióra otwiera się przy listwie, z której go wywołano.
                                    p == Panel.PEN && strip == 0 -> if (pos == 1) Alignment.BottomStart else Alignment.TopStart
                                    p == Panel.PEN && strip == 1 -> if (pos == 1) Alignment.BottomEnd else Alignment.TopEnd
                                    pos == 1 -> Alignment.BottomCenter
                                    pos == 2 -> Alignment.CenterStart
                                    pos == 3 -> Alignment.CenterEnd
                                    else -> Alignment.TopCenter
                                }
                                Box(Modifier.align(pa).padding(8.dp).panelEnter()) { ToolPanel(ed, set, p, onColorSets) { panel = null } }
                            }
                            if (panel == Panel.STYLE) StyleHost(ed) { panel = null }
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
                if (strip == 1) PenStrip(ed, set, 1, pos, panel, onColorSets) { toggle(it) }
                if (ed.chrome && pos == 3) SideRail(ed, set, panel, ::pickTool, ::holdTool, ::pickPen, right = true)
            }
            if (strip == 2 && pos == 1) PenStrip(ed, set, 3, pos, panel, onColorSets) { toggle(it) }
            if (ed.chrome && pos == 1) BottomBar(ed, set, panel, ::pickTool, ::holdTool, ::pickPen)
        }

        ed.textReq?.let { TextDialog(ed, it) }
        if (goTo) GoToDialog(ed) { goTo = false }
        if (ed.exportOpen) ExportDialog(ed.nbId, ed.pageIndex, { ed.doc.flush() }) { ed.exportOpen = false }
        if (ed.axesOpen) AxesDialog(ed) { ed.axesOpen = false }
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
    onExit: () -> Unit, pickImage: () -> Unit, pickTool: (Tool, Panel?) -> Unit, holdTool: (Tool, Panel) -> Unit, pickPen: (PenKind) -> Unit, inlineBar: Boolean, widthDp: Int,
) {
    val m = mint
    val title = ed.repo.meta(ed.nbId)?.title ?: ""
    ed.structTick
    BoxWithConstraints(Modifier.fillMaxWidth().background(m.chrome)) {
        // Przy jednym dokumencie pasek ma być cały na wierzchu: tytuł oddaje mu miejsce, a gdy i tak się nie mieści,
        // pasek dostaje własny wiersz. Przy kartach zostaje w linii i sam zwija najmniej ważne narzędzia pod „⋯”.
        val spare = maxWidth.value - HEADER_FIXED - (BarFit.length(barItemCount(set.barConfig, set.undoDock != 0), 3, barSize(set).value, true) + 8f)
        val inline = inlineBar && (tabs.showStrip || spare >= TITLE_MIN)
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, T.t("Biblioteka", "Library"), onExit, shortcut = "Esc")
                if (tabs.showStrip) {
                    TabStrip(tabs, Modifier.weight(if (inline) 0.38f else 1f).padding(horizontal = 4.dp))
                } else {
                    Row(Modifier.then(if (inline) Modifier.widthIn(max = spare.coerceIn(TITLE_MIN, 220f).dp) else Modifier.weight(1f)), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                        if (ed.readOnly) Icon(Icons.Rounded.Lock, null, Modifier.padding(end = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (inline) Spacer(Modifier.width(4.dp))
                }
                if (inline) Box(Modifier.weight(if (tabs.showStrip) 0.62f else 1f), contentAlignment = Alignment.Center) { ToolBar(ed, set, false, panel, pickTool, holdTool, pickPen) }
                PageCluster(ed, panel, toggle, goTo, pickImage, widthDp >= 600)
            }
            if (set.toolbarPos == 0 && !inline) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ToolBar(ed, set, false, panel, pickTool, holdTool, pickPen) }
            }
            Hairline()
        }
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

/** Szyna narzędzi z boku strony (pozycje „lewo” i „prawo”): zadokowana, nie zasłania strony. */
@Composable
private fun SideRail(ed: Editor, set: com.mdev.mnotes.data.Settings, panel: Panel?, pickTool: (Tool, Panel?) -> Unit, holdTool: (Tool, Panel) -> Unit, pickPen: (PenKind) -> Unit, right: Boolean) {
    val m = mint
    Row(Modifier.fillMaxHeight()) {
        if (right) Hairline(vertical = true)
        Box(Modifier.fillMaxHeight().background(m.chrome), contentAlignment = Alignment.Center) { ToolBar(ed, set, true, panel, pickTool, holdTool, pickPen) }
        if (!right) Hairline(vertical = true)
    }
}

/** Pasek narzędzi na dole (pozycja „dół”): zadokowany pod stroną. */
@Composable
private fun BottomBar(ed: Editor, set: com.mdev.mnotes.data.Settings, panel: Panel?, pickTool: (Tool, Panel?) -> Unit, holdTool: (Tool, Panel) -> Unit, pickPen: (PenKind) -> Unit) {
    val m = mint
    Column(Modifier.fillMaxWidth().background(m.chrome)) {
        Hairline()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ToolBar(ed, set, false, panel, pickTool, holdTool, pickPen) }
    }
}

// ─────────────────────────────── Pasek narzędzi ───────────────────────────────

@Composable
private fun Lin(vertical: Boolean, modifier: Modifier = Modifier, gap: Dp = 2.dp, content: @Composable () -> Unit) {
    if (vertical) Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) { content() }
    else Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) { content() }
}

/** [tight]: węższy margines, gdy pasek ściska się, żeby zmieścić wszystkie narzędzia (szerokości jak w [BarFit]). */
@Composable
private fun BarSep(vertical: Boolean, tight: Boolean = false) {
    val m = mint
    val pad = if (tight) 3.dp else 4.dp
    if (vertical) Box(Modifier.padding(vertical = pad).width(24.dp).height(1.dp).background(m.line))
    else Box(Modifier.padding(horizontal = pad).height(24.dp).width(1.dp).background(m.line))
}

private fun barSize(set: com.mdev.mnotes.data.Settings) = when (set.toolbarSize) { 0 -> 36.dp; 2 -> 46.dp; else -> 40.dp }
private fun barIcon(set: com.mdev.mnotes.data.Settings) = when (set.toolbarSize) { 0 -> 20.dp; 2 -> 26.dp; else -> 22.dp }

/** Pozycja paska. [rank]: co pierwsze trafia pod „⋯” (patrz [BarFit]); [pin]: narzędzie w ręku, zawsze na wierzchu. */
private class BarItem(
    val id: String, val group: Int, val rank: Int, val tip: String, val shortcut: String?, val enabled: Boolean, val selected: Boolean, val pin: Boolean,
    val onClick: () -> Unit, val glyph: @Composable (Color, Dp) -> Unit,
) {
    /** Przytrzymanie zamiast menu pozycji (np. panel ustawień narzędzia). */
    var onHold: (() -> Unit)? = null
}

/** Liczba przycisków paska w układzie [cfg]: nagłówek ocenia nią, czy pasek zmieści się obok tytułu. */
private fun barItemCount(cfg: BarConfig, undoDocked: Boolean): Int {
    // Cofnij i Ponów w rogu strony nie zajmują miejsca na pasku.
    val order = if (undoDocked) cfg.order.filter { it != "undo" && it != "redo" } else cfg.order
    val shown = order.count { cfg.mode(it) != BarMode.MENU }
    return if (shown < order.size) shown + 1 else shown
}

private fun undoCorner(dock: Int): Alignment = when (dock) {
    1 -> Alignment.TopStart; 2 -> Alignment.TopEnd; 3 -> Alignment.BottomStart; else -> Alignment.BottomEnd
}

/**
 * Cofnij i Ponów jako osobna kapsuła w rogu strony (Ustawienia → Edytor i pióro). Zamiast przycisków na pasku, żeby
 * kciuk sięgał po nie bez wędrowania po całym ekranie, a pasek zyskał dwa miejsca.
 */
@Composable
private fun UndoCapsule(ed: Editor, dock: Int, modifier: Modifier = Modifier) {
    val ro = ed.readOnly
    val isz = 22.dp
    // Niewidoczne pole dotyku jest większe od kapsuły i dzieli się w połowie na Cofnij i Ponów. Bez niego palec
    // celujący w przycisk łatwo trafiał w obiekt tuż obok i go zaznaczał. Pole zjada też dotknięcia, gdy przycisk
    // jest wyłączony. Przesunięcie o margines w stronę rogu zostawia kapsułę dokładnie tam, gdzie była.
    val dx = if (dock == 1 || dock == 3) -UNDO_HIT_MARGIN else UNDO_HIT_MARGIN
    val dy = if (dock == 1 || dock == 2) -UNDO_HIT_MARGIN else UNDO_HIT_MARGIN
    Box(modifier.offset(dx, dy)) {
        Row(Modifier.matchParentSize()) {
            listOf({ if (ed.canUndo && !ro) ed.undo() }, { if (ed.canRedo && !ro) ed.redo() }).forEach { act ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = act))
            }
        }
        Box(Modifier.padding(UNDO_HIT_MARGIN)) {
            FloatingCard(Modifier, radius = 26) {
                Row(Modifier.padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToolBtn(T.t("Cofnij", "Undo"), { ed.undo() }, enabled = ed.canUndo && !ro, size = 44.dp, shortcut = "Ctrl+Z") { c -> Icon(Icons.AutoMirrored.Rounded.Undo, null, Modifier.size(isz), tint = c) }
                    ToolBtn(T.t("Ponów", "Redo"), { ed.redo() }, enabled = ed.canRedo && !ro, size = 44.dp, shortcut = "Ctrl+Y") { c -> Icon(Icons.AutoMirrored.Rounded.Redo, null, Modifier.size(isz), tint = c) }
                }
            }
        }
    }
}

private val UNDO_HIT_MARGIN = 14.dp

/** Stała część nagłówka poza tytułem i paskiem: powrót, marginesy i prawa grupa (strona, powiększenie, cztery przyciski). */
private const val HEADER_FIXED = 352f
private const val TITLE_MIN = 96f

/** Pióra w kolejności na pasku (trzy pierwsze dzielą domyślnie kolory) z ważnością przy zwijaniu pod „⋯”. */
private val BAR_PENS = listOf(PenKind.BALLPOINT to 2, PenKind.FOUNTAIN to 6, PenKind.PENCIL to 8, PenKind.BRUSH to 9, PenKind.HIGHLIGHTER to 5)

/**
 * Pasek narzędzi: cofanie, pięć narzędzi piszących i reszta. Nie przewija się: gdy brakuje miejsca, najpierw znikają
 * odstępy, a potem najmniej ważne narzędzia przechodzą pod „⋯” (kolejność i rachunek: [BarFit]).
 */
@Composable
private fun ToolBar(ed: Editor, set: com.mdev.mnotes.data.Settings, vertical: Boolean, panel: Panel?, pickTool: (Tool, Panel?) -> Unit, holdTool: (Tool, Panel) -> Unit, pickPen: (PenKind) -> Unit) {
    val m = mint
    val sz = barSize(set)
    val isz = barIcon(set)
    val ro = ed.readOnly
    val kit = ed.kit
    val items = ArrayList<BarItem>()
    fun icon(id: String, group: Int, rank: Int, icon: ImageVector, tip: String, key: String, enabled: Boolean, selected: Boolean, pin: Boolean = selected, onHold: (() -> Unit)? = null, onClick: () -> Unit) {
        items += BarItem(id, group, rank, tip, key, enabled, selected, pin, onClick) { c, s -> Icon(icon, null, Modifier.size(s), tint = c) }.also { it.onHold = onHold }
    }
    fun tool(id: String, rank: Int, icon: ImageVector, tip: String, key: String, t: Tool, p: Panel?, enabled: Boolean = !ro, hold: Boolean = false) =
        icon(id, 2, rank, icon, tip, key, enabled, ed.tool == t, onHold = if (hold && p != null) ({ holdTool(t, p) }) else null) { pickTool(t, p) }

    icon("undo", 0, 0, Icons.AutoMirrored.Rounded.Undo, T.t("Cofnij", "Undo"), "Ctrl+Z", ed.canUndo && !ro, false) { ed.undo() }
    icon("redo", 0, 1, Icons.AutoMirrored.Rounded.Redo, T.t("Ponów", "Redo"), "Ctrl+Y", ed.canRedo && !ro, false) { ed.redo() }
    for ((k, rank) in BAR_PENS) {
        val mine = kit.active == k
        val on = mine && ed.tool == Tool.PEN
        // Pędzel formatu i wiaderko biorą kolor z aktywnego pióra, więc zostaje ono oznaczone kropką.
        val lent = mine && !on && ed.penTool
        val ink = Color(kit.pen(k).color)
        items += BarItem(k.name.lowercase(), 1, rank, penName(k), if (mine) "P" else null, !ro, on, on, { pickPen(k) }) { c, s ->
            Box(contentAlignment = Alignment.Center) {
                PenGlyph(k, ink, c, Modifier.size(s + 2.dp))
                if (lent) Box(Modifier.align(Alignment.BottomCenter).offset(y = 5.dp).size(4.dp).clip(CircleShape).background(m.accent))
            }
        }
    }
    tool("eraser", 3, MIcons.Eraser, T.t("Gumka", "Eraser"), "E", Tool.ERASER, Panel.ERASER)
    tool("painter", 11, Icons.Rounded.FormatPaint, T.t("Pędzel formatu — przenosi kolor i styl aktywnego pióra", "Format painter — applies the active pen's colour and style"), "B", Tool.PAINTER, Panel.PAINTER)
    tool("fill", 12, Icons.Rounded.FormatColorFill, T.t("Wypełnienie (wiaderko)", "Fill (paint bucket)"), "F", Tool.FILL, Panel.FILL, hold = true)
    icon("pipette", 2, 17, Icons.Rounded.Colorize, T.t("Pipeta — pobierz kolor ze strony", "Eyedropper — pick a colour from the page"), "I", !ro, ed.pipette != null, pin = false) { if (ed.pipette != null) ed.cancelPipette() else ed.pipetteForCurrent() }
    // Lasso z ikoną z przerywanej linii (F7), odręczne albo prostokątne — jak pętla na stronie.
    items += BarItem("lasso", 2, 4, T.t("Lasso", "Lasso"), "L", !ro, ed.tool == Tool.LASSO, ed.tool == Tool.LASSO, { pickTool(Tool.LASSO, Panel.LASSO) }) { c, s ->
        LassoGlyph(set.lassoRect, c, Modifier.size(s))
    }
    tool("text", 7, Icons.Rounded.TextFields, T.t("Tekst", "Text"), "T", Tool.TEXT, null)
    tool("laser", 14, Icons.Rounded.TrackChanges, T.t("Laser", "Laser"), "O", Tool.LASER, null, enabled = true)
    tool("hand", 10, Icons.Rounded.PanTool, T.t("Ręka (przewijanie)", "Hand (pan)"), "H", Tool.HAND, null, enabled = true)
    // F17: krzywa Béziera — kreska aktywnym piórem staje się gładką krzywą z uchwytami.
    items += BarItem("curve", 2, 18, T.t("Krzywa Béziera — rysuj, a potem popraw uchwytami", "Bézier curve — draw, then adjust with handles"), null, !ro, ed.tool == Tool.CURVE, ed.tool == Tool.CURVE, { pickTool(Tool.CURVE, null) }) { c, s ->
        BezierGlyph(c, Modifier.size(s))
    }
    tool("tape", 15, Icons.Rounded.VisibilityOff, T.t("Taśma — zakryj fragment (do nauki)", "Tape — cover an area (for study)"), "K", Tool.TAPE, Panel.TAPE)
    tool("space", 16, Icons.Rounded.UnfoldMore, T.t("Wstaw / usuń odstęp", "Insert / remove space"), "X", Tool.SPACE, null)
    icon("ruler", 2, 13, Icons.Rounded.Straighten, T.t("Linijka", "Ruler"), "R", !ro, ed.ruler, pin = false) { ed.toggleRuler() }

    // Kolejność i tryby wybiera użytkownik (Ustawienia albo przytrzymanie przycisku); pozycje zbudowane wyżej mają
    // kolejność domyślną, więc tu tylko się je układa.
    val cfg = set.barConfig
    val shown = items.filter { set.undoDock == 0 || (it.id != "undo" && it.id != "redo") }.sortedBy { cfg.order.indexOf(it.id) }
    val openSettings = LocalBarSettings.current
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val fit = BarFit.fit(
            shown.map { BarEntry(it.group, it.rank, cfg.mode(it.id)) }, (if (vertical) maxHeight else maxWidth).value - 8f, sz.value,
            shown.indexOfFirst { it.pin },
        )
        var more by remember { mutableStateOf(false) }
        var menuFor by remember { mutableStateOf<String?>(null) }
        Lin(vertical, Modifier.padding(4.dp), if (fit.tight) 0.dp else 2.dp) {
            var group = -1
            shown.forEachIndexed { i, b ->
                if (i !in fit.hidden) {
                    if (group >= 0 && b.group != group) BarSep(vertical, fit.tight)
                    group = b.group
                    Box {
                        ToolBtn(b.tip, b.onClick, enabled = b.enabled, selected = b.selected, size = sz, shortcut = b.shortcut, onLong = { b.onHold?.invoke() ?: run { menuFor = b.id } }) { c -> b.glyph(c, isz) }
                        if (menuFor == b.id) BarItemMenu(b.id, cfg, vertical, { set.barConfig = it }, { menuFor = null }, openSettings)
                        if (b.id == "painter") QuickFormat(ed, set, panel, sz, isz)
                    }
                }
            }
            if (fit.hidden.isNotEmpty()) Box {
                // Pędzel schowany pod „⋯” nie ma ikony na pasku, więc jego szybki przycisk wisi pod „⋯”.
                if (fit.hidden.any { shown[it].id == "painter" }) QuickFormat(ed, set, panel, sz, isz)
                // Włączone narzędzie spod „⋯” (np. linijka) zaznacza sam przycisk, żeby było widać, że coś działa.
                IconBtn(Icons.Rounded.MoreHoriz, T.t("Więcej narzędzi", "More tools"), { more = true }, selected = more || fit.hidden.any { shown[it].selected }, size = sz, iconSize = isz)
                DropdownMenu(more, { more = false }) {
                    for (i in fit.hidden.sorted()) {
                        val b = shown[i]
                        DropdownMenuItem(
                            text = { Text(b.tip.substringBefore(" — ")) }, enabled = b.enabled,
                            leadingIcon = { b.glyph(if (b.selected) m.accentText else MaterialTheme.colorScheme.onSurface.copy(alpha = if (b.enabled) 1f else 0.32f), 22.dp) },
                            trailingIcon = b.shortcut?.let { k -> { Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                            onClick = { more = false; b.onClick() },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(T.t("Dostosuj pasek…", "Customise toolbar…")) }, leadingIcon = { Icon(Icons.Rounded.Tune, null) }, onClick = { more = false; openSettings() })
                }
            }
        }
    }
}

/**
 * Szybki przycisk „Pobierz format” pod ikoną pędzla formatu (po stronie kartki), w kształcie przycisków paska.
 * Leży w oknie `Popup`, bo wystaje poza pasek, a poza granicami rodzica dotyk nie dociera do dzieci. Gaśnie płynnie.
 */
@Composable
private fun QuickFormat(ed: Editor, set: com.mdev.mnotes.data.Settings, panel: Panel?, sz: Dp, isz: Dp) {
    val m = mint
    val state = remember { MutableTransitionState(false) }
    state.targetState = ed.tool == Tool.PAINTER && ed.quickFormat && panel != Panel.PAINTER && !ed.readOnly
    if (!state.currentState && !state.targetState) return
    val pos = set.toolbarPos
    val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
    val place = remember(pos, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = when (pos) {
                1 -> IntOffset(anchorBounds.left, anchorBounds.top - gap - popupContentSize.height)
                2 -> IntOffset(anchorBounds.right + gap, anchorBounds.top)
                3 -> IntOffset(anchorBounds.left - gap - popupContentSize.width, anchorBounds.top)
                else -> IntOffset(anchorBounds.left, anchorBounds.bottom + gap)
            }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Popup(popupPositionProvider = place) {
        AnimatedVisibility(visibleState = state, enter = fadeIn(tween(150)), exit = fadeOut(tween(350))) {
            Box(Modifier.shadow(4.dp, shape).clip(shape).background(m.chrome).border(1.dp, m.line, shape).padding(2.dp)) {
                ToolBtn(
                    T.t("Pobierz format — dotknij obiektu, by przejąć jego kolor i styl", "Take format — tap an object to take its colour and style"),
                    {
                        ed.formatPick = !ed.formatPick
                        if (ed.formatPick) ed.say(T.t("Dotknij obiektu, z którego pobrać format", "Tap the object to take the format from"))
                    },
                    selected = ed.formatPick, size = sz,
                ) { c -> Icon(Icons.Rounded.Colorize, null, Modifier.size(isz), tint = c) }
            }
        }
    }
}

/**
 * Menu przytrzymanego przycisku paska: gdzie ma stać (zawsze, gdy jest miejsce, pod „⋯”) i o jedno miejsce dalej.
 * Zmiana działa od razu, bez wchodzenia w Ustawienia.
 */
@Composable
private fun BarItemMenu(id: String, cfg: BarConfig, vertical: Boolean, save: (BarConfig) -> Unit, close: () -> Unit, openSettings: () -> Unit) {
    DropdownMenu(true, close) {
        @Composable
        fun mode(label: String, v: Int) = DropdownMenuItem(
            text = { Text(label) },
            leadingIcon = { if (cfg.mode(id) == v) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
            onClick = { close(); save(cfg.withMode(id, v)) },
        )
        mode(T.t("Zawsze na pasku", "Always on the bar"), BarMode.ALWAYS)
        mode(T.t("Gdy jest miejsce", "When there is room"), BarMode.AUTO)
        mode(T.t("Tylko pod „⋯”", "Only under “⋯”"), BarMode.MENU)
        HorizontalDivider()
        val back = if (vertical) T.t("Przesuń w górę", "Move up") else T.t("Przesuń w lewo", "Move left")
        val fwd = if (vertical) T.t("Przesuń w dół", "Move down") else T.t("Przesuń w prawo", "Move right")
        DropdownMenuItem(text = { Text(back) }, leadingIcon = { Icon(if (vertical) Icons.Rounded.ArrowUpward else Icons.AutoMirrored.Rounded.ArrowBack, null) }, onClick = { close(); save(cfg.move(id, -1)) })
        DropdownMenuItem(text = { Text(fwd) }, leadingIcon = { Icon(if (vertical) Icons.Rounded.ArrowDownward else Icons.AutoMirrored.Rounded.ArrowForward, null) }, onClick = { close(); save(cfg.move(id, 1)) })
        HorizontalDivider()
        DropdownMenuItem(text = { Text(T.t("Dostosuj pasek…", "Customise toolbar…")) }, leadingIcon = { Icon(Icons.Rounded.Tune, null) }, onClick = { close(); openSettings() })
    }
}

// ─────────────────────────────── Listwa pióra ───────────────────────────────

/**
 * Listwa aktywnego pióra: trzy grubości i kolory z jego zestawu, wybierane jednym dotknięciem, bez otwierania panelu.
 * Zadokowana jak paski narzędzi, więc nie zasłania strony. [side]: 0 z lewej strony kartki, 1 z prawej,
 * 2 pod górnym paskiem, 3 nad dolnym.
 */
@Composable
private fun PenStrip(ed: Editor, set: com.mdev.mnotes.data.Settings, side: Int, toolbarPos: Int, panel: Panel?, onColorSets: () -> Unit, toggle: (Panel) -> Unit) {
    val m = mint
    if (side < 2) Row(Modifier.fillMaxHeight()) {
        if (side == 1) Hairline(vertical = true)
        // Opcje trzymają się paska narzędzi, żeby ręka nie wędrowała po całej wysokości ekranu.
        val at = when (toolbarPos) { 0 -> Alignment.TopCenter; 1 -> Alignment.BottomCenter; else -> Alignment.Center }
        Box(Modifier.fillMaxHeight().background(m.chrome), contentAlignment = at) { PenStripItems(ed, set, true, panel, onColorSets, toggle) }
        if (side == 0) Hairline(vertical = true)
    } else Column(Modifier.fillMaxWidth().background(m.chrome)) {
        if (side == 3) Hairline()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PenStripItems(ed, set, false, panel, onColorSets, toggle) }
        if (side == 2) Hairline()
    }
}

@Composable
private fun PenStripItems(ed: Editor, set: com.mdev.mnotes.data.Settings, vertical: Boolean, panel: Panel?, onColorSets: () -> Unit, toggle: (Panel) -> Unit) {
    val sz = barSize(set)
    val isz = barIcon(set)
    // F7: z gumką, lassem albo laserem w ręku listwa pokazuje ich opcje (jak przy piórze); można to wyłączyć w Ustawieniach.
    if (set.toolStrip) when (ed.tool) {
        Tool.ERASER -> { EraserStripItems(ed, set, vertical, panel, toggle); return }
        Tool.LASSO -> { LassoStripItems(ed, set, vertical, panel, toggle); return }
        Tool.LASER -> { LaserStripItems(ed, set, vertical); return }
        else -> {}
    }
    val kit = ed.kit
    val kind = kit.active
    val slot = kit.slot(kind)
    val cs = kit.setOf(kind)
    // Z innym narzędziem w ręku listwa przygasa, a dotknięcie jej wraca do pisania; ponowne dotknięcie wybranej
    // pozycji otwiera panel, w którym zmienia się jej wartość.
    val live = ed.penTool
    val scroll = rememberScrollState()
    var menuFor by remember { mutableStateOf<Int?>(null) }
    Lin(vertical, Modifier.padding(4.dp).then(if (vertical) Modifier.verticalScroll(scroll) else Modifier.horizontalScroll(scroll)).graphicsLayer { alpha = if (live) 1f else 0.55f }) {
        for (i in 0 until PenKit.WIDTHS) {
            val on = i == slot.widthIndex
            ToolBtn(
                T.t("Grubość", "Width") + String.format(" %.1f pt", slot.widths[i]),
                { if (on && live) toggle(Panel.PEN) else ed.tunePen { it.withWidthIndex(kind, i) } }, selected = on, size = sz,
            ) { c -> WidthGlyph(slot.widths[i], c, Modifier.size(isz + 2.dp)) }
        }
        BarSep(vertical)
        for (s in LineStyle.entries) {
            val on = s == slot.style
            ToolBtn(lineName(s), { if (on && live) toggle(Panel.PEN) else ed.tunePen { it.withStyle(kind, s) } }, selected = on, size = sz) { c ->
                LineGlyph(s, c, Modifier.size(isz + 2.dp))
            }
        }
        BarSep(vertical)
        for (c in kit.strip(kind)) {
            val on = c == slot.color
            // Długie przytrzymanie otwiera menu koloru (przestaw, schowaj, przenieś); menu wisi pod kropką, nie pod paskiem.
            androidx.compose.runtime.key(c) {
                Box {
                    StripDot(Color(InkColor.onPaper(c, cs.hl, kind)), on, sz, onLong = { menuFor = c }) { if (on && live) toggle(Panel.PEN) else ed.tunePen { it.withColor(kind, c) } }
                    if (menuFor == c) ColorMenu(ed, kind, c, { menuFor = null }, onColorSets)
                }
            }
        }
        BarSep(vertical)
        IconBtn(
            Icons.Rounded.Palette, T.t("Kolory i ustawienia pióra", "Colours and pen settings"),
            { if (!live) ed.setToolKey(Tool.PEN); toggle(Panel.PEN) }, selected = panel == Panel.PEN, size = sz, iconSize = isz,
        )
    }
}

/** Rozmiary gumki na listwie (pt); ponowne dotknięcie wybranego otwiera panel z suwakiem. */
private val ERASER_SIZES = floatArrayOf(4f, 14f, 32f)

/** Listwa gumki (F7): trzy tryby z ikonami, trzy rozmiary, „Nie ruszaj zakreślacza” i panel. */
@Composable
private fun EraserStripItems(ed: Editor, set: com.mdev.mnotes.data.Settings, vertical: Boolean, panel: Panel?, toggle: (Panel) -> Unit) {
    val sz = barSize(set); val isz = barIcon(set)
    val scroll = rememberScrollState()
    Lin(vertical, Modifier.padding(4.dp).then(if (vertical) Modifier.verticalScroll(scroll) else Modifier.horizontalScroll(scroll))) {
        val names = listOf(T.t("Gumka obiektowa — usuwa całe kreski", "Object eraser — removes whole strokes"), T.t("Gumka pikselowa — ściera tylko to, nad czym przejdzie", "Pixel eraser — erases only what it passes over"), T.t("Lasso gumki — usuwa to, co obrysujesz", "Lasso eraser — removes what you circle"), T.t("Wycinanie — tnie kreski dokładnie na obrysie", "Cut out — cuts strokes exactly along the loop"))
        for (mode in 0..3) {
            val on = set.eraserMode == mode
            ToolBtn(names[mode], { if (on) toggle(Panel.ERASER) else set.eraserMode = mode }, selected = on, size = sz) { c -> EraserModeGlyph(mode, c, Modifier.size(isz + 2.dp)) }
        }
        if (set.eraserMode < 2) {
            BarSep(vertical)
            for (s in ERASER_SIZES) {
                val on = kotlin.math.abs(set.eraserSize - s) < 0.5f
                ToolBtn(T.t("Rozmiar gumki", "Eraser size") + String.format(" %.0f pt", s), { if (on) toggle(Panel.ERASER) else set.eraserSize = s }, selected = on, size = sz) { c -> EraserSizeGlyph(s, c, Modifier.size(isz + 2.dp)) }
            }
        }
        BarSep(vertical)
        IconBtn(Icons.Rounded.Highlight, T.t("Nie ruszaj zakreślacza", "Keep highlighter"), { set.eraserKeepHighlighter = !set.eraserKeepHighlighter }, selected = set.eraserKeepHighlighter, size = sz, iconSize = isz)
        IconBtn(Icons.Rounded.Tune, T.t("Ustawienia gumki", "Eraser settings"), { toggle(Panel.ERASER) }, selected = panel == Panel.ERASER, size = sz, iconSize = isz)
    }
}

/** Listwa lassa (F7): kształt pętli (ikony z przerywanej linii), tryb zaznaczania i panel. */
@Composable
private fun LassoStripItems(ed: Editor, set: com.mdev.mnotes.data.Settings, vertical: Boolean, panel: Panel?, toggle: (Panel) -> Unit) {
    val sz = barSize(set); val isz = barIcon(set)
    Lin(vertical, Modifier.padding(4.dp)) {
        ToolBtn(T.t("Lasso odręczne", "Freehand lasso"), { if (!set.lassoRect) toggle(Panel.LASSO) else set.lassoRect = false }, selected = !set.lassoRect, size = sz) { c -> LassoGlyph(false, c, Modifier.size(isz + 2.dp)) }
        ToolBtn(T.t("Zaznaczanie prostokątem", "Rectangle selection"), { if (set.lassoRect) toggle(Panel.LASSO) else set.lassoRect = true }, selected = set.lassoRect, size = sz) { c -> LassoGlyph(true, c, Modifier.size(isz + 2.dp)) }
        BarSep(vertical)
        IconBtn(Icons.Rounded.SelectAll, T.t("Zbalansowane — połowa obiektu w pętli", "Balanced — half of the object in the loop"), { set.lassoTouch = false }, selected = !set.lassoTouch, size = sz, iconSize = isz)
        IconBtn(Icons.Rounded.TouchApp, T.t("Tylko zahacz — wystarczy musnąć obiekt", "Just touch — brushing the object is enough"), { set.lassoTouch = true }, selected = set.lassoTouch, size = sz, iconSize = isz)
        BarSep(vertical)
        IconBtn(Icons.Rounded.Tune, T.t("Ustawienia lassa", "Lasso settings"), { toggle(Panel.LASSO) }, selected = panel == Panel.LASSO, size = sz, iconSize = isz)
    }
}

/** Listwa lasera (F7): kolor śladu. */
@Composable
private fun LaserStripItems(ed: Editor, set: com.mdev.mnotes.data.Settings, vertical: Boolean) {
    val sz = barSize(set)
    Lin(vertical, Modifier.padding(4.dp)) {
        for (c in LaserColors.PALETTE) StripDot(Color(0xFF000000.toInt() or c), set.laserColor == c, sz) { set.laserColor = c }
    }
}

/**
 * Menu koloru z listwy. Przestawianie nie zamyka go, żeby dało się przesunąć kolor o kilka miejsc; przenoszenie
 * między zestawami idzie na osobnych stronach tego samego okna, żeby nie piętrzyć okienek.
 */
@Composable
private fun ColorMenu(ed: Editor, kind: PenKind, c: Int, close: () -> Unit, onColorSets: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val kit = ed.kit
    val cs = kit.setOf(kind)
    val at = cs.colors.indexOf(c)
    val others = kit.sets.filter { it.id != cs.id }
    val full = cs.colors.size >= PenKit.MAX_COLORS
    DropdownMenu(true, close) {
        if (page != 0) {
            DropdownMenuItem(text = { Text(T.t("‹ Wstecz", "‹ Back")) }, onClick = { page = 0 })
            HorizontalDivider()
        }
        when (page) {
            0 -> {
                if (at >= 0) {
                    DropdownMenuItem(text = { Text(T.t("Przesuń wcześniej", "Move earlier")) }, enabled = at > 0, onClick = { ed.editKit { it.moveColor(cs.id, at, at - 1) } })
                    DropdownMenuItem(text = { Text(T.t("Przesuń później", "Move later")) }, enabled = at < cs.colors.size - 1, onClick = { ed.editKit { it.moveColor(cs.id, at, at + 1) } })
                    if (at >= cs.visible) DropdownMenuItem(text = { Text(T.t("Pokaż na listwie", "Show on strip")) }, onClick = { close(); ed.editKit { it.showColor(cs.id, c) } })
                    else DropdownMenuItem(text = { Text(T.t("Schowaj z listwy", "Hide from strip")) }, enabled = cs.visible > 1, onClick = { close(); ed.editKit { it.hideColor(cs.id, c) } })
                    if (others.isNotEmpty()) {
                        DropdownMenuItem(text = { Text(T.t("Kopiuj do zestawu ▸", "Copy to set ▸")) }, onClick = { page = 1 })
                        if (cs.colors.size > 1) DropdownMenuItem(text = { Text(T.t("Przenieś do zestawu ▸", "Move to set ▸")) }, onClick = { page = 2 })
                    }
                    if (cs.colors.size > 1) DropdownMenuItem(text = { Text(T.t("Usuń z zestawu", "Remove from set")) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { close(); ed.editKit { it.removeColor(cs.id, c) } })
                } else {
                    DropdownMenuItem(
                        text = { Text(T.t("Dodaj do zestawu „${setName(cs)}”", "Add to set “${setName(cs)}”")) }, enabled = !full,
                        leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { close(); ed.editKit { it.addColor(cs.id, c) } },
                    )
                }
                if (others.isNotEmpty()) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(T.t("Kolory z innego zestawu ▸", "Colours from another set ▸")) }, onClick = { page = 3 })
                }
                DropdownMenuItem(text = { Text(T.t("Zestawy kolorów…", "Colour sets…")) }, leadingIcon = { Icon(Icons.Rounded.Settings, null) }, onClick = { close(); onColorSets() })
            }
            1, 2 -> for (o in others) {
                // Cel może już mieć tę barwę (po przeliczeniu atrament ↔ zakreślacz) albo być pełny.
                val conv = InkColor.between(c, cs.hl, o.hl) or (0xFF shl 24)
                DropdownMenuItem(
                    text = { Text(setName(o)) }, enabled = conv !in o.colors && o.colors.size < PenKit.MAX_COLORS,
                    leadingIcon = { SetGlyph(o) },
                    onClick = { close(); ed.editKit { if (page == 1) it.copyColor(cs.id, c, o.id) else it.moveColorTo(cs.id, c, o.id) } },
                )
            }
            else -> for (o in others) DropdownMenuItem(
                text = { Text(setName(o)) }, leadingIcon = { SetGlyph(o) },
                onClick = { close(); ed.editKit { it.withSet(kind, o.id) } },
            )
        }
    }
}

/** Próbka grubości: kreska rośnie wolniej niż sama grubość, żeby 0,5 pt i 40 pt dały się porównać w jednym przycisku. */
@Composable
private fun WidthGlyph(width: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val d = (1.3f * Math.pow(2.2 * width, 0.62).toFloat()).coerceIn(1.5f, 16f).dp.toPx()
        drawLine(color, Offset(d / 2f, size.height / 2f), Offset(size.width - d / 2f, size.height / 2f), d, StrokeCap.Round)
    }
}

/** Próbka stylu linii: ciągła, trzy kreski albo cztery kropki, zawsze poziomo, żeby czytała się tak samo na listwie bocznej. */
@Composable
private fun LineGlyph(style: LineStyle, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val d = 2.4f.dp.toPx()
        val y = size.height / 2f
        val a = d / 2f
        val b = size.width - d / 2f
        when (style) {
            LineStyle.SOLID -> drawLine(color, Offset(a, y), Offset(b, y), d, StrokeCap.Round)
            LineStyle.DASHED -> {
                // Trzy kreski i dwie przerwy tej samej długości co kreska bez okrągłych końców.
                val u = (b - a) / 5f
                for (i in 0 until 3) drawLine(color, Offset(a + 2f * i * u + d * 0.25f, y), Offset(a + (2f * i + 1f) * u - d * 0.25f, y), d, StrokeCap.Round)
            }
            LineStyle.DOTTED -> for (i in 0 until 4) drawCircle(color, d * 0.62f, Offset(a + (b - a) * i / 3f, y))
        }
    }
}

private fun lineName(s: LineStyle): String = when (s) {
    LineStyle.SOLID -> T.t("Linia ciągła", "Solid line")
    LineStyle.DASHED -> T.t("Linia przerywana", "Dashed line")
    LineStyle.DOTTED -> T.t("Linia kropkowana", "Dotted line")
}

/**
 * Trzy style linii jako rząd równych pól na całą szerokość panelu; [current] = null, gdy zaznaczenie miesza style.
 * Próbka stoi nad podpisem, więc rząd nie rośnie z długością nazw (przy chipach „Przerywana” + „Kropkowana” wystawał poza panel).
 */
@Composable
private fun LineStyleRow(current: LineStyle?, onPick: (LineStyle) -> Unit) {
    val m = mint
    val tick = rememberTick()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in LineStyle.entries) {
            val on = s == current
            val fg = if (on) m.accentText else MaterialTheme.colorScheme.onSurfaceVariant
            val shape = RoundedCornerShape(8.dp)
            Column(
                Modifier.weight(1f).clip(shape).background(if (on) m.accentSoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, if (on) m.accent else Color.Transparent, shape)
                    .clickable(role = Role.Button) { tick(); onPick(s) }.padding(horizontal = 4.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                LineGlyph(s, fg, Modifier.width(36.dp).height(14.dp))
                Text(
                    when (s) { LineStyle.SOLID -> T.t("Ciągła", "Solid"); LineStyle.DASHED -> T.t("Przerywana", "Dashed"); LineStyle.DOTTED -> T.t("Kropkowana", "Dotted") },
                    style = MaterialTheme.typography.labelMedium, color = if (on) m.accentText else MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StripDot(color: Color, active: Boolean, size: Dp, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val m = mint
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val tick = rememberTick()
    val ring by animateColorAsState(if (active) m.accent else Color.Transparent, tween(150), label = "dotRing")
    val halo by animateColorAsState(if (active) m.accentSoft else Color.Transparent, tween(150), label = "dotHalo")
    val scale by animateFloatAsState(if (pressed) 0.84f else if (active) 1.05f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "dotScale")
    Box(
        Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
            .combinedClickable(
                interactionSource = src, indication = null, role = Role.Button,
                onLongClick = onLong?.let { f -> { tick(); f() } }, onClick = { tick(); onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val d = size * 0.5f
        Box(
            Modifier.size(d + 8.dp).clip(CircleShape).background(halo).border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(d).clip(CircleShape).background(color).border(1.dp, m.line, CircleShape)) }
    }
}

// ─────────────────────────────── Panele narzędzi ───────────────────────────────

@Composable
private fun ToolPanel(ed: Editor, set: com.mdev.mnotes.data.Settings, panel: Panel, onColorSets: () -> Unit, close: () -> Unit) {
    val m = mint
    FloatingCard(Modifier.width(320.dp)) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (panel) {
                Panel.PEN -> PenPanel(ed, set, onColorSets)
                Panel.ERASER -> {
                    SectionLabel(T.t("Tryb gumki", "Eraser mode"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(T.t("Obiektowa", "Object"), set.eraserMode == 0, { set.eraserMode = 0 })
                        Chip(T.t("Pikselowa", "Pixel"), set.eraserMode == 1, { set.eraserMode = 1 })
                        Chip(T.t("Lasso", "Lasso"), set.eraserMode == 2, { set.eraserMode = 2 })
                        Chip(T.t("Wycinanie", "Cut out"), set.eraserMode == 3, { set.eraserMode = 3 })
                    }
                    if (set.eraserMode == 3) Text(T.t("Obrysuj fragment: kreski zostaną przecięte dokładnie na obrysie, a to, co w środku, zniknie.", "Circle a part: strokes are cut exactly along the loop and everything inside is removed."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(T.t("Rozmiar", "Size")); Spacer(Modifier.weight(1f))
                        Text(String.format("%.0f pt", set.eraserSize), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MSlider(set.eraserSize, 1f, 60f) { set.eraserSize = it }
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
                Panel.PAINTER -> {
                    val openPainterSettings = LocalPainterSettings.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(T.t("Pędzel formatu", "Format painter")); Spacer(Modifier.weight(1f))
                        IconBtn(Icons.Rounded.Settings, T.t("Ustawienia pędzla formatu", "Format painter settings"), { close(); openPainterSettings() }, size = 32.dp, iconSize = 18.dp)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(T.t("Obiektowo", "Object"), set.painterMode == 0, { set.painterMode = 0 }, icon = Icons.Rounded.FormatPaint)
                        Chip(T.t("Lasso", "Lasso"), set.painterMode == 1, { set.painterMode = 1 }, icon = Icons.Rounded.Gesture)
                    }
                    Chip(T.t("Pobierz format", "Take format"), ed.formatPick, { ed.formatPick = !ed.formatPick; if (ed.formatPick) { ed.say(T.t("Dotknij obiektu, z którego pobrać format", "Tap the object to take the format from")); close() } }, icon = Icons.Rounded.Colorize)
                    if (ed.formatPick) Text(T.t("Dotknij rysikiem obiektu na stronie — jego kolor, grubość, styl linii i rodzaj pióra staną się aktywnym piórem.", "Tap an object with the stylus — its colour, width, line style and pen type become the active pen."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(T.t("Kolor i styl bierze z aktywnego pióra — wybierz pióro na pasku, a kolor i grubość na listwie. Obiektowo: przeciągnij po obiektach. Lasso: obrysuj je.", "Colour and style come from the active pen — pick the pen on the toolbar, the colour and width on the strip. Object: drag over objects. Lasso: circle them."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Panel.FILL -> {
                    SectionLabel(T.t("Wiaderko", "Fill"))
                    ToggleRow(T.t("Dojeżdżaj w zakamarki", "Reach into tight corners"), set.fillCloseGaps) { set.fillCloseGaps = it }
                    Text(T.t("Włączone: wiaderko domyka małe szpary w liniach i dochodzi do ostrych kątów. Wyłączone: wypełnia dokładnie to, co jest zamknięte, a przez każdą szparę wylewa się dalej.", "On: the bucket closes small gaps in lines and reaches sharp corners. Off: it fills exactly what is closed and spills through any gap."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(T.t("Kolor i styl bierze z aktywnego pióra.", "Colour and style come from the active pen."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    val openRotationSettings = LocalRotationSettings.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(T.t("Kształt lassa", "Lasso shape")); Spacer(Modifier.weight(1f))
                        IconBtn(Icons.Rounded.Settings, T.t("Ustawienia kątów obrotu", "Rotation angle settings"), { close(); openRotationSettings() }, size = 32.dp, iconSize = 18.dp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(T.t("Odręczne", "Freehand"), !set.lassoRect, { set.lassoRect = false }, icon = Icons.Rounded.Gesture)
                        Chip(T.t("Prostokąt", "Rectangle"), set.lassoRect, { set.lassoRect = true }, icon = Icons.Rounded.HighlightAlt)
                    }
                    SectionLabel(T.t("Co zaznacza pętla", "What the loop selects"))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(T.t("Zbalansowane", "Balanced"), !set.lassoTouch, { set.lassoTouch = false })
                        Chip(T.t("Tylko zahacz", "Just touch"), set.lassoTouch, { set.lassoTouch = true })
                    }
                    Text(lassoModeHint(set.lassoTouch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ToggleRow(T.t("Przyciągaj do siatki", "Snap to grid"), ed.gridSnap) { ed.gridSnap = it; set.snapGrid = it }
                    ToggleRow(T.t("Przyciągaj do równych kątów", "Snap to even angles"), set.rotSnap) { set.rotSnap = it }
                    Text(rotationHint(set), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(T.t("Ctrl+A zaznacza wszystko na stronie.", "Ctrl+A selects everything on the page."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Pasek przecinania obrazu: podpowiedź gestu, dodawanie linii bez przeciągania i zatwierdzenie. */
@Composable
private fun CutBar(ed: Editor) {
    val c = ed.cut ?: return
    val n = ed.cutPieceCount()
    FloatingCard(Modifier.widthIn(max = 460.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(T.t("Przecinanie obrazu", "Cut image"))
            Text(
                if (c.lines.isEmpty()) T.t("Przeciągnij palcem lub rysikiem w poprzek obrazu, by narysować linię cięcia. Linię można potem przesunąć, a wyciągnięcie jej poza obraz usuwa ją. Dwa palce przewijają stronę.", "Swipe across the image to draw a cut line. Drag a line to move it; pull it off the image to remove it. Two fingers scroll the page.")
                else T.t("Linie pojawią się jako cięcia dopiero po zatwierdzeniu. Przeciągnij linię, by ją przesunąć; blisko 1/2, 1/3 i 1/4 sama się przyciągnie.", "Lines become cuts only after you confirm. Drag a line to move it; it snaps near 1/2, 1/3 and 1/4."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(T.t("Pionowa", "Vertical"), false, { ed.addCutLine(true) }, icon = Icons.Rounded.VerticalSplit)
                Chip(T.t("Pozioma", "Horizontal"), false, { ed.addCutLine(false) }, icon = Icons.Rounded.HorizontalSplit)
                if (c.lines.isNotEmpty()) IconBtn(Icons.AutoMirrored.Rounded.Undo, T.t("Cofnij ostatnią linię", "Undo last line"), { ed.undoCutLine() }, size = 36.dp, iconSize = 20.dp)
                Spacer(Modifier.weight(1f))
                Chip(T.t("Rozsuń", "Spread"), c.spread, { c.spread = !c.spread })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton({ ed.cancelCut() }) { Text(T.t("Anuluj", "Cancel")) }
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.Button({ ed.applyCut() }, enabled = n > 1) {
                    Text(if (n > 1) T.t("Przetnij na $n części", "Cut into $n pieces") else T.t("Przetnij", "Cut"))
                }
            }
        }
    }
}

/** Nazwa proporcji ramki na chipie. */
private fun cropRatioName(r: CropRatio) = when (r) {
    CropRatio.FREE -> T.t("Dowolne", "Free")
    CropRatio.ORIGINAL -> T.t("Oryginał", "Original")
    CropRatio.SQUARE -> "1:1"
    CropRatio.R43 -> "4:3"
    CropRatio.R32 -> "3:2"
    CropRatio.R169 -> "16:9"
}

/**
 * Pasek przycinania i przecinania leży przy tym, czego dotyczy, a nie na stałe u góry: stoi pod obiektem, nad nim albo z boku,
 * zależnie od tego, gdzie jest miejsce, i zasłania go jak najmniej (jak pasek zaznaczenia). Dotychczasowa strona trzyma się,
 * dopóki pasuje, więc pasek nie skacze przy każdym ruchu ramki.
 */
@Composable
private fun NearHost(rect: android.graphics.RectF?, content: @Composable () -> Unit) {
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val side = remember { arrayOfNulls<CardSide>(1) }
    val r = rect
    Box(
        Modifier.fillMaxSize().layout { mm, cs ->
            val p = mm.measure(cs.copy(minWidth = 0, minHeight = 0))
            layout(cs.maxWidth, cs.maxHeight) {
                // górny pas zajmuje listwa narzędzi, więc pasek nie wchodzi pod nią
                val top = (48 * dens).toInt().coerceAtMost(cs.maxHeight / 3)
                val spot = if (r == null) CardPlacement.place(0f, 0f, 0f, 0f, p.width, p.height, cs.maxWidth, cs.maxHeight - top, 12 * dens, side[0])
                else CardPlacement.place(r.left, r.top - top, r.right, r.bottom - top, p.width, p.height, cs.maxWidth, cs.maxHeight - top, 12 * dens, side[0])
                side[0] = spot.side
                p.place(spot.x, spot.y + top)
            }
        },
    ) { Box(Modifier.panelEnter()) { content() } }
}

@Composable
private fun CropBarHost(ed: Editor) {
    val c = ed.crop ?: return
    c.frame; c.place; ed.viewTick
    NearHost(ed.view?.cropScreenRect()) { CropBar(ed) }
}

/**
 * Panel stylu strony stoi tam, gdzie nie zasłania strony (obok niej, gdy po oddaleniu widoku jest wolne miejsce);
 * gdy strona wypełnia ekran, zostaje w prawym górnym rogu jak dotąd.
 */
@Composable
private fun StyleHost(ed: Editor, close: () -> Unit) {
    ed.viewTick
    val page = ed.view?.pageScreenRect(ed.pageIndex.coerceIn(0, (ed.doc.pages.size - 1).coerceAtLeast(0)))
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val side = remember { arrayOfNulls<CardSide>(1) }
    Box(
        Modifier.fillMaxSize().layout { mm, cs ->
            val p = mm.measure(cs.copy(minWidth = 0, minHeight = 0))
            layout(cs.maxWidth, cs.maxHeight) {
                val spot = page?.let { CardPlacement.free(it.left, it.top, it.right, it.bottom, p.width, p.height, cs.maxWidth, cs.maxHeight, 8 * dens, side[0]) }
                side[0] = spot?.side
                if (spot != null) p.place(spot.x, spot.y)
                else p.place((cs.maxWidth - p.width - 8 * dens).toInt().coerceAtLeast(0), (8 * dens).toInt())
            }
        },
    ) { Box(Modifier.panelEnter()) { StylePanel(ed, close) } }
}

@Composable
private fun CutBarHost(ed: Editor) {
    if (ed.cut == null) return
    ed.selTick; ed.viewTick
    NearHost(ed.view?.selScreenRect()) { CutBar(ed) }
}

/** Zwarty pasek przycinania: prostowanie, proporcje i zatwierdzenie w trzech wierszach (bez opisów, podpowiedzi są w opisach przycisków). */
@Composable
private fun CropBar(ed: Editor) {
    val c = ed.crop ?: return
    val half = (Math.PI / 2).toFloat()
    val q = c.quarter
    val deg = Math.toDegrees(CropGeom.tiltOf(c.frame.a, q).toDouble()).toFloat()
    val changed = !ed.cropIsFull()
    FloatingCard(Modifier.width(300.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.RotateLeft, T.t("Obróć o 90° w lewo", "Rotate 90° left"), { ed.cropQuarter(false) }, size = 32.dp, iconSize = 18.dp)
                Box(Modifier.weight(1f)) {
                    MSlider(deg, -45f, 45f) { v ->
                        val snapped = if (kotlin.math.abs(v) < 1f) 0f else v
                        ed.cropSetAngle(q * half + Math.toRadians(snapped.toDouble()).toFloat())
                    }
                }
                IconBtn(Icons.Rounded.RotateRight, T.t("Obróć o 90° w prawo", "Rotate 90° right"), { ed.cropQuarter(true) }, size = 32.dp, iconSize = 18.dp)
                Text(String.format("%+.1f°", deg), Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in CropRatio.values()) Chip(cropRatioName(r), c.ratio == r, { ed.cropSetRatio(r) })
                }
                IconBtn(Icons.Rounded.SwapHoriz, T.t("Zamień poziom z pionem", "Swap landscape and portrait"), { ed.cropSwapRatio() }, size = 32.dp, iconSize = 18.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton({ ed.cancelCrop() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(T.t("Anuluj", "Cancel"), style = MaterialTheme.typography.labelLarge) }
                androidx.compose.material3.TextButton({ ed.cropReset() }, enabled = changed || c.frame.a != 0f, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(T.t("Cały obraz", "Whole image"), style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.Button({ ed.applyCrop() }, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(34.dp)) { Text(T.t("Zastosuj", "Apply")) }
            }
        }
    }
}

/** Karta widoczna podczas pobierania koloru: wybór, czy pipeta bierze kolor widoczny na stronie, czy pierwotny kolor kreski. */
@Composable
private fun PipetteCard(set: com.mdev.mnotes.data.Settings) {
    FloatingCard(Modifier.width(320.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(T.t("Pobierany kolor", "Colour to pick"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(T.t("Rzeczywisty", "As seen"), !set.pipetteOriginal, { set.pipetteOriginal = false })
                Chip(T.t("Pierwotny", "Original"), set.pipetteOriginal, { set.pipetteOriginal = true })
            }
            Text(
                if (set.pipetteOriginal) T.t("Kolor zapisany w narysowanym obiekcie. Zakreślacz ma pierwszeństwo przed pismem pod nim albo nad nim. Poza kreskami i wypełnieniami pobierze kolor ze strony.", "The colour stored in the drawn object. A highlighter takes priority over writing under or over it. Away from strokes and fills it picks the colour from the page.")
                else T.t("Kolor dokładnie taki, jaki widać na stronie — zakreślacz wyjdzie jaśniejszy.", "The colour exactly as seen on the page — a highlighter comes out lighter."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Panel aktywnego pióra: to, czego nie robi się jednym dotknięciem listwy, czyli wartości trzech grubości, skład
 * i kolejność zestawu kolorów, wybór zestawu (także cudzego) oraz kolor spoza listy.
 */
@Composable
private fun PenPanel(ed: Editor, set: com.mdev.mnotes.data.Settings, onColorSets: () -> Unit) {
    val m = mint
    val kit = ed.kit
    val kind = kit.active
    val slot = kit.slot(kind)
    val cs = kit.setOf(kind)
    val spec = PenSpec.of(kind)
    val pen = kit.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PenGlyph(kind, Color(pen.color), MaterialTheme.colorScheme.onSurface, Modifier.size(24.dp))
        Text(penName(kind), style = MaterialTheme.typography.titleSmall)
    }
    PenTester(pen, set.smoothing)

    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(T.t("Grubość", "Width")); Spacer(Modifier.weight(1f))
        Text(String.format("%.1f pt", slot.width), style = MaterialTheme.typography.labelMedium, color = dim)
    }
    // Te same trzy grubości co na listwie; suwak ustawia wybraną.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until PenKit.WIDTHS) Chip(
            String.format("%.1f", slot.widths[i]), i == slot.widthIndex, { ed.editKit { it.withWidthIndex(kind, i) } },
            glyph = { c -> WidthGlyph(slot.widths[i], c, Modifier.size(20.dp)) },
        )
    }
    MSlider(slot.width, spec.minWidth, spec.maxWidth) { v -> ed.editKit { it.withWidth(kind, slot.widthIndex, v) } }

    SectionLabel(T.t("Styl linii", "Line style"))
    LineStyleRow(slot.style) { s -> ed.editKit { it.withStyle(kind, s) } }

    @Composable
    fun Swatches(colors: List<Int>) = FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in colors) Dot(Color(InkColor.onPaper(c, cs.hl, kind)), 30.dp, selected = c == slot.color, onClick = { ed.editKit { it.withColor(kind, c) } })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(T.t("Kolory na listwie", "Colours on the strip")); Spacer(Modifier.weight(1f))
        IconBtn(Icons.Rounded.Remove, T.t("Mniej kolorów na listwie", "Fewer colours on the strip"), { ed.editKit { it.withVisible(cs.id, cs.visible - 1) } }, enabled = cs.visible > 1, size = 30.dp, iconSize = 18.dp)
        Text("${cs.visible}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp))
        IconBtn(Icons.Rounded.Add, T.t("Więcej kolorów na listwie", "More colours on the strip"), { ed.editKit { it.withVisible(cs.id, cs.visible + 1) } }, enabled = cs.visible < minOf(PenKit.MAX_VISIBLE, cs.colors.size), size = 30.dp, iconSize = 18.dp)
    }
    Swatches(cs.colors.take(cs.visible))
    if (cs.colors.size > cs.visible) {
        SectionLabel(T.t("Pozostałe w zestawie", "Others in the set"))
        Swatches(cs.colors.drop(cs.visible))
    }
    // Działania na wybranym kolorze: spoza zestawu można go dopisać, a ten z zestawu przestawić albo wyrzucić.
    val at = cs.colors.indexOf(slot.color)
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (at < 0) {
            if (cs.colors.size < PenKit.MAX_COLORS) Chip(T.t("Dodaj do zestawu", "Add to the set"), false, { ed.editKit { it.addColor(cs.id, slot.color) } }, icon = Icons.Rounded.Add)
        } else {
            if (at > 0) Chip(T.t("Przesuń wcześniej", "Move earlier"), false, { ed.editKit { it.moveColor(cs.id, at, at - 1) } }, icon = Icons.AutoMirrored.Rounded.ArrowBack)
            if (at < cs.colors.size - 1) Chip(T.t("Przesuń później", "Move later"), false, { ed.editKit { it.moveColor(cs.id, at, at + 1) } }, icon = Icons.AutoMirrored.Rounded.ArrowForward)
            if (cs.colors.size > 1) Chip(T.t("Usuń z zestawu", "Remove from the set"), false, { ed.editKit { it.removeColor(cs.id, slot.color) } }, icon = Icons.Rounded.Delete)
        }
    }

    SectionLabel(T.t("Zestaw kolorów", "Colour set"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in kit.sets) Chip(setName(s), s.id == cs.id, { ed.editKit { it.withSet(kind, s.id) } })
    }
    val sharing = PenKind.entries.filter { it != kind && kit.slot(it).setId == cs.id }.joinToString(", ") { penName(it).lowercase() }
    Text(
        if (sharing.isEmpty()) T.t("Z tego zestawu korzysta tylko to narzędzie.", "Only this tool uses this set.")
        else T.t("Z tego zestawu korzystają też: $sharing. Zmiany w zestawie widać w każdym z nich.", "Also used by: $sharing. Changes to the set show up in each of them."),
        style = MaterialTheme.typography.bodySmall, color = dim,
    )
    Chip(T.t("Zestawy kolorów…", "Colour sets…"), false, onColorSets, icon = Icons.Rounded.Settings)
    Text(T.t("Przytrzymaj kolor na listwie, żeby go przestawić, schować albo przenieść do innego zestawu.", "Hold a colour on the strip to move, hide or transfer it to another set."), style = MaterialTheme.typography.bodySmall, color = dim)

    SectionLabel(T.t("Inny kolor", "Another colour"))
    ColorPicker(
        slot.color, { c -> ed.editKit { it.withColor(kind, c) } }, highlighter = cs.hl,
        onPipette = { ed.startPipette { c -> ed.setPenColor(if (cs.hl) softenForHighlighter(c) else c) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = m.line)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(T.t("Wygładzanie", "Smoothing")); Spacer(Modifier.weight(1f))
        Text("${(set.smoothing * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = dim)
    }
    MSlider(set.smoothing, 0f, 2f) { set.smoothing = it }
    ToggleRow(T.t("Przytrzymaj, aby wyprostować kształt", "Hold to snap to shape"), set.holdToShape) { set.holdToShape = it }
}

@Composable
fun MSlider(value: Float, min: Float, max: Float, onFinish: (() -> Unit)? = null, onChange: (Float) -> Unit) {
    val m = mint
    Slider(
        value.coerceIn(min, max), onChange, valueRange = min..max, onValueChangeFinished = onFinish, modifier = Modifier.fillMaxWidth().height(32.dp),
        colors = SliderDefaults.colors(thumbColor = m.accent, activeTrackColor = m.accent, inactiveTrackColor = m.line),
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, keys: String = "", onChange: (Boolean) -> Unit) {
    val m = mint
    if (!rowVisible(label, keys)) return
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rowLabel(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
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
private fun PenTester(p: ActivePen, smoothing: Float) {
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
            Modifier.matchParentSize().pointerInput(p.kind, p.color, p.width, p.style, smoothing) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val spec = PenSpec.of(p.kind)
                    builder.begin(p.kind, p.color, p.width, (spec.smoothing * smoothing).coerceIn(0f, 1f), 0.5f, p.style)
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
                for (st in strokes) renderer.drawStrokeData(c, st.pen, st.color, st.width, st.pts, st.count, dens, line = st.line)
                if (builder.count > 0) renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, builder.pts, builder.count, dens, line = builder.line)
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
    if (ed.cut != null || ed.crop != null) return
    ed.selTick; ed.viewTick; ed.undoTick
    val v = ed.view ?: return
    val r = v.selScreenRect() ?: return
    val els = ed.selElements()
    if (els.isEmpty()) return
    val locked = els.all { it.locked }
    val hasImage = els.any { it is ImageEl }
    val hasColorable = els.any { it !is ImageEl }
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val side = remember(sel) { arrayOfNulls<CardSide>(1) }
    // Panel dodatkowy (kolor, efekty, wyrównanie) leży obok paska, ale poza zaznaczeniem: zasłonięty obiekt to ten, który zmieniasz.
    val extra: (@Composable () -> Unit)? = when {
        panel == Panel.SELCOLOR && hasColorable -> { { SelColorPanel(ed, els) } }
        panel == Panel.IMAGEFX && hasImage -> { { ImageFxPanel(ed, els.first { it is ImageEl } as ImageEl) } }
        panel == Panel.ALIGN -> { { AlignCard(ed) } }
        else -> null
    }
    Layout(
        content = {
            Box { SelectionButtons(ed, panel, toggle, editText, els, locked, hasImage, hasColorable) }
            if (extra != null) Box(Modifier.panelEnter()) { extra() }
        },
        modifier = Modifier.fillMaxSize(),
    ) { ms, c ->
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val p = ms[0].measure(loose)
        layout(c.maxWidth, c.maxHeight) {
            val x = (r.centerX() - p.width / 2f).toInt().coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0))
            // przy włączonym skalowaniu/obrocie uchwyt obrotu wisi nad ramką — pasek leży nad nim
            val above = r.top - p.height - (if (ed.transformMode && !locked) NoteView.ROTATE_OFFSET_DP + 22 else 14f) * dens
            val barAbove = above > 4
            val y = (if (barAbove) above else r.bottom + 14 * dens).toInt().coerceIn(0, (c.maxHeight - p.height).coerceAtLeast(0))
            p.place(x, y)
            if (ms.size > 1) {
                val q = ms[1].measure(loose)
                // przeszkodą jest zaznaczenie razem z paskiem; panel stoi z tej strony, z której jest pasek, jeśli tam jest miejsce
                val spot = CardPlacement.place(
                    minOf(r.left, x.toFloat()), minOf(r.top, y.toFloat()), maxOf(r.right, (x + p.width).toFloat()), maxOf(r.bottom, (y + p.height).toFloat()),
                    q.width, q.height, c.maxWidth, c.maxHeight, 8 * dens, side[0] ?: if (barAbove) CardSide.ABOVE else CardSide.BELOW,
                )
                side[0] = spot.side
                q.place(spot.x, spot.y)
            }
        }
    }
}

@Composable
private fun SelectionButtons(ed: Editor, panel: Panel?, toggle: (Panel) -> Unit, editText: () -> Unit, els: List<Element>, locked: Boolean, hasImage: Boolean, hasColorable: Boolean) {
    FloatingCard {
        Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.ContentCut, T.t("Wytnij", "Cut"), { ed.cutSel() }, size = 36.dp, iconSize = 20.dp, shortcut = "Ctrl+X")
            IconBtn(Icons.Rounded.ContentCopy, T.t("Kopiuj", "Copy"), { ed.copySel() }, size = 36.dp, iconSize = 20.dp, shortcut = "Ctrl+C")
            IconBtn(Icons.Rounded.ContentCopy, T.t("Duplikuj", "Duplicate"), { ed.duplicateSel() }, size = 36.dp, iconSize = 20.dp, shortcut = "Ctrl+D", tint = mint.accentText)
            BarSep(false)
            // sam obraz nie ma koloru do zmiany: dostaje własny panel efektów zamiast wyboru koloru
            if (hasColorable) IconBtn(Icons.Rounded.Palette, T.t("Kolor, grubość i styl linii", "Color, width & line style"), { toggle(Panel.SELCOLOR) }, selected = panel == Panel.SELCOLOR, size = 36.dp, iconSize = 20.dp)
            if (hasImage) IconBtn(Icons.Rounded.Tune, T.t("Efekty obrazu", "Image effects"), { toggle(Panel.IMAGEFX) }, selected = panel == Panel.IMAGEFX, size = 36.dp, iconSize = 20.dp)
            if (!locked) IconBtn(Icons.Rounded.CropRotate, T.t("Skaluj i obracaj", "Resize & rotate"), { ed.transformMode = !ed.transformMode }, selected = ed.transformMode, size = 36.dp, iconSize = 20.dp)
            if (!locked && els.size == 1 && els[0] is ImageEl && !ed.readOnly) IconBtn(Icons.Rounded.Crop, T.t("Przytnij", "Crop"), { ed.startCrop() }, size = 36.dp, iconSize = 20.dp)
            if (ed.canFlipSel()) {
                IconBtn(Icons.Rounded.Flip, T.t("Odbij w poziomie", "Flip horizontally"), { ed.flipSel(true) }, size = 36.dp, iconSize = 20.dp)
                ToolBtn(T.t("Odbij w pionie", "Flip vertically"), { ed.flipSel(false) }, size = 36.dp) { c ->
                    Icon(Icons.Rounded.Flip, T.t("Odbij w pionie", "Flip vertically"), tint = c, modifier = Modifier.size(20.dp).rotate(90f))
                }
            }
            IconBtn(Icons.Rounded.FlipToFront, T.t("Na wierzch", "Bring to front"), { ed.frontSel() }, size = 36.dp, iconSize = 20.dp)
            IconBtn(Icons.Rounded.FlipToBack, T.t("Na spód", "Send to back"), { ed.backSel() }, size = 36.dp, iconSize = 20.dp)
            IconBtn(if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, if (locked) T.t("Odblokuj", "Unlock") else T.t("Zablokuj", "Lock"), { ed.lockSel(!locked) }, size = 36.dp, iconSize = 20.dp)
            if (els.size > 1) IconBtn(Icons.Rounded.AlignHorizontalLeft, T.t("Wyrównaj", "Align"), { toggle(Panel.ALIGN) }, selected = panel == Panel.ALIGN, size = 36.dp, iconSize = 20.dp)
            if (els.size == 1 && els[0] is TextBox) IconBtn(Icons.Rounded.Edit, T.t("Edytuj tekst", "Edit text"), editText, size = 36.dp, iconSize = 20.dp)
            if (els.size == 1 && els[0] is TextBox && !locked) {
                val auto = (els[0] as TextBox).fit == TextBox.FIT_AUTO
                IconBtn(
                    Icons.Rounded.WidthFull,
                    if (auto) T.t("Auto-rozmiar włączony: dotknij, by zamrozić szerokość", "Auto-size on: tap to freeze the width")
                    else T.t("Auto-rozmiar: dopasuj pole do tekstu", "Auto-size: fit the box to its text"),
                    { ed.toggleTextFit() }, selected = auto, size = 36.dp, iconSize = 20.dp,
                )
            }
            BarSep(false)
            IconBtn(Icons.Rounded.Delete, T.t("Usuń", "Delete"), { ed.deleteSel() }, size = 36.dp, iconSize = 20.dp, shortcut = "Del", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SelColorPanel(ed: Editor, els: List<Element>) {
    FloatingCard(Modifier.width(320.dp)) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val colors = els.mapNotNull { colorOf(it) }.map { it and 0xFFFFFF }.distinct()
            val first = els.firstOrNull { colorOf(it) != null }
            val hasStroke = els.any { it is InkStroke }
            SectionLabel(T.t("Kolor", "Color"))
            ColorPicker(first?.let { colorOf(it) } ?: 0xFF000000.toInt(), { ed.recolorSel(it) }, mixed = colors.size > 1, recent = ed.kit.sets.filter { !it.hl }.flatMap { it.colors.take(it.visible) }.distinct(), onPipette = { ed.startPipette { c -> ed.recolorSel(c) } })
            if (hasStroke) {
                val w = (els.firstOrNull { it is InkStroke } as InkStroke).width
                SectionLabel(T.t("Grubość", "Width"))
                MSlider(w, 0.3f, 24f) { ed.setSelWidth(it) }
                SectionLabel(T.t("Styl linii", "Line style"))
                LineStyleRow(els.filterIsInstance<InkStroke>().map { it.line }.distinct().singleOrNull()) { ed.setSelLine(it) }
            }
        }
    }
}

@Composable
private fun AlignCard(ed: Editor) {
    FloatingCard {
        Row(Modifier.padding(3.dp)) {
            val icons = listOf(Icons.Rounded.AlignHorizontalLeft, Icons.Rounded.AlignHorizontalCenter, Icons.Rounded.AlignHorizontalRight, Icons.Rounded.AlignVerticalTop, Icons.Rounded.AlignVerticalCenter, Icons.Rounded.AlignVerticalBottom)
            val names = listOf(T.t("Do lewej", "Left"), T.t("Środek poziomo", "Center H"), T.t("Do prawej", "Right"), T.t("Do góry", "Top"), T.t("Środek pionowo", "Center V"), T.t("Do dołu", "Bottom"))
            icons.forEachIndexed { i, ic -> IconBtn(ic, names[i], { ed.alignSel(i) }, size = 36.dp, iconSize = 20.dp) }
        }
    }
}

@Composable
private fun ImageFxPanel(ed: Editor, img: ImageEl) {
    val fx = img.fx
    val pct: (Float) -> String = { " ${(it * 100).roundToInt()}%" }
    val signed: (Float) -> String = { v -> " " + (if (v > 0.005f) "+" else "") + (v * 100).roundToInt() }
    var rgbOpen by remember { mutableStateOf(false) }
    FloatingCard(Modifier.width(320.dp)) {
        // Długi panel przewija się wewnątrz karty, a rzadziej używane kanały R/G/B są zwinięte (F13: ergonomia menu).
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel(T.t("Efekt", "Effect"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(T.t("Kolor", "Color"), fx.mode == ImageFx.MODE_COLOR, { ed.setSelImageFx { it.copy(mode = ImageFx.MODE_COLOR) } })
                Chip(T.t("Odcienie szarości", "Grayscale"), fx.mode == ImageFx.MODE_GRAY, { ed.setSelImageFx { it.copy(mode = ImageFx.MODE_GRAY) } })
                Chip(T.t("Czarno-biały", "Black & white"), fx.mode == ImageFx.MODE_BW, { ed.setSelImageFx { it.copy(mode = ImageFx.MODE_BW) } })
                Chip(T.t("Odwróć kolory", "Invert colors"), fx.invert, { ed.setSelImageFx { it.copy(invert = !it.invert) } }, icon = Icons.Rounded.InvertColors)
            }
            if (fx.mode == ImageFx.MODE_BW) {
                FxSlider(ed, T.t("Próg bieli", "White threshold"), fx.threshold, 0f, 1f, { "" }) { v -> { im -> im.copy(fx = im.fx.copy(threshold = v)) } }
            }
            // F13: jasność, temperatura i odcień to inny układ tych samych wzmocnień R/G/B — suwaki poruszają się razem.
            val tone = ImageTone.of(fx)
            FxSlider(ed, T.t("Jasność", "Brightness"), tone.brightness, 0.2f, 1.8f, pct) { v -> { im -> im.copy(fx = ImageTone.withBrightness(im.fx, v)) } }
            FxSlider(ed, T.t("Temperatura (zimno ↔ ciepło)", "Temperature (cool ↔ warm)"), tone.temperature, -0.6f, 0.6f, signed) { v -> { im -> im.copy(fx = ImageTone.withTemperature(im.fx, v)) } }
            FxSlider(ed, T.t("Odcień (magenta ↔ zieleń)", "Tint (magenta ↔ green)"), tone.tint, -0.6f, 0.6f, signed) { v -> { im -> im.copy(fx = ImageTone.withTint(im.fx, v)) } }
            SectionLabel(T.t("Balans bieli", "White balance"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(T.t("Auto: białe tło", "Auto: white background"), false, { ed.autoWhiteBalanceSel(true) }, icon = Icons.Rounded.WbAuto)
                Chip(T.t("Auto: szary świat", "Auto: gray world"), false, { ed.autoWhiteBalanceSel(false) })
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { rgbOpen = !rgbOpen }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(T.t("Kanały R / G / B", "R / G / B channels"), Modifier.weight(1f))
                Icon(if (rgbOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rgbOpen) {
                FxSlider(ed, T.t("Czerwony", "Red"), fx.r, 0f, ImageFx.GAIN_MAX, pct) { v -> { im -> im.copy(fx = im.fx.copy(r = v)) } }
                FxSlider(ed, T.t("Zielony", "Green"), fx.g, 0f, ImageFx.GAIN_MAX, pct) { v -> { im -> im.copy(fx = im.fx.copy(g = v)) } }
                FxSlider(ed, T.t("Niebieski", "Blue"), fx.b, 0f, ImageFx.GAIN_MAX, pct) { v -> { im -> im.copy(fx = im.fx.copy(b = v)) } }
            }
            FxSlider(ed, T.t("Krycie obrazu", "Image opacity"), img.alpha, 0.1f, 1f, { "" }) { v -> { im -> im.copy(alpha = v) } }
            if (!fx.isNone) {
                Spacer(Modifier.height(6.dp))
                Chip(T.t("Przywróć oryginał", "Reset effects"), false, { ed.setSelImageFx { ImageFx.NONE } }, icon = Icons.Rounded.RestartAlt)
            }
        }
    }
}

/**
 * Suwak efektów obrazu. Położenie i podpis trzyma lokalnie (palec nie czeka na dokument), a zmianę stosuje na żywo
 * najwyżej co [PREVIEW_MS] jako podgląd bez wpisu cofania; kafelki przemalowują się w tle. Gest kończy jeden wpis cofania.
 */
@Composable
private fun FxSlider(ed: Editor, label: String, value: Float, min: Float, max: Float, suffix: (Float) -> String, change: (Float) -> (ImageEl) -> ImageEl) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val latest = androidx.compose.runtime.rememberUpdatedState(change)
    LaunchedEffect(Unit) {
        // conflate: między krokami podglądu zostaje tylko ostatnie położenie palca
        snapshotFlow { drag }.conflate().collect { v ->
            if (v != null) { ed.previewSelImages(latest.value(v)); delay(PREVIEW_MS) }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ed.commitSelPreview() } }
    val shown = drag ?: value
    SectionLabel(label + suffix(shown))
    MSlider(shown, min, max, onFinish = {
        drag?.let { ed.previewSelImages(latest.value(it)) }
        ed.commitSelPreview(); drag = null
    }) { drag = it }
}

/** Kolor elementu, albo null, gdy element nie ma koloru (obraz, taśma). */
private fun colorOf(e: com.mdev.mnotes.core.model.Element?): Int? = when (e) {
    is InkStroke -> e.color
    is TextBox -> e.color
    is com.mdev.mnotes.core.model.FillEl -> e.color
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
                    // F14: wycinek strony pod zaznaczeniem (z papierem i tłem) jako obraz.
                    Item(T.t("Wycinek jako obraz", "Snip as image"), enabled = !ed.readOnly) { ed.snipSel(false) }
                    Item(T.t("Kopiuj wycinek do schowka", "Copy snip to clipboard")) { ed.snipSel(true) }
                }
                Item(T.t("Wklej", "Paste"), "Ctrl+V", ed.canPaste() && !ed.readOnly) { ed.paste(if (cm.pageIndex >= 0) Triple(cm.pageIndex, cm.lx, cm.ly) else null) }
                if (cm.hasSel) {
                    Item(T.t("Odbij w poziomie", "Flip horizontally"), enabled = ed.canFlipSel()) { ed.flipSel(true) }
                    Item(T.t("Odbij w pionie", "Flip vertically"), enabled = ed.canFlipSel()) { ed.flipSel(false) }
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
                Chip(T.t("Pionowo", "Vertical"), ed.layoutMode == 0, { applyLayout(0) })
                Chip(T.t("Poziomo", "Horizontal"), ed.layoutMode == 1, { applyLayout(1) })
                Chip(T.t("Pojedyncza", "Single"), ed.layoutMode == 2, { applyLayout(2) })
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = m.line)
            MenuRow(Icons.Rounded.FitScreen, T.t("Dopasuj do szerokości", "Fit width"), "Ctrl+0") { ed.view?.fitWidth(); close() }
            MenuRow(Icons.Rounded.Palette, T.t("Styl strony…", "Page style…"), "") { openStyle() }
            if (!ed.readOnly) MenuRow(Icons.Rounded.ShowChart, T.t("Układ współrzędnych…", "Coordinate axes…"), "") { close(); ed.axesOpen = true }
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

/**
 * Suwak rozstawu wzoru. Położenie trzyma lokalnie (palec nie czeka na dokument), a zmianę strony stosuje na żywo
 * najwyżej co [PREVIEW_MS], bo każda zmiana przemalowuje kafelki strony. Gest kończy jeden wpis cofania.
 */
@Composable
private fun SpacingSlider(spec: com.mdev.mnotes.core.model.PageSpec, default: Float, preview: (Float) -> Unit, commit: () -> Unit, reset: () -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: spec.spacing
    val latest = androidx.compose.runtime.rememberUpdatedState(preview)
    var applied by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PREVIEW_MS)
            val v = drag
            if (v != null && v != applied) { latest.value(v); applied = v }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { commit() } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            MSlider(shown, 4f, 60f, onFinish = {
                drag?.let { if (it != applied) latest.value(it) }
                commit(); drag = null; applied = null
            }) { drag = it }
        }
        Text(String.format(java.util.Locale.US, "%.1f", shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Chip(T.t("Domyślny rozstaw", "Default spacing"), kotlin.math.abs(shown - default) < 0.005f, { drag = null; applied = null; reset() })
}

private const val PREVIEW_MS = 70L

@Composable
private fun StylePanel(ed: Editor, close: () -> Unit) {
    val m = mint
    ed.structTick
    ed.undoTick   // zmiany stylu idą przez stos cofania; bez odczytu panel pokazywał stary stan (suwak stał w miejscu)
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
            // F11: ciemny tryb dokumentu także dla stron PDF (w przeciwieństwie do wzoru i papieru).
            ToggleRow(T.t("Odwróć kolory (ciemny tryb)", "Invert colours (dark mode)"), spec.inverted) { on ->
                if (all) ed.restyleAll { it.withInverted(on) } else ed.setSpec(idx, spec.withInverted(on))
            }
            if (spec.inverted) Text(T.t("Jasność odwrócona, odcienie zostają. Tylko na ekranie: eksport i druk są w oryginale.", "Brightness inverted, hues kept. Screen only: export and print stay original."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    SpacingSlider(spec, spec.template.defaultSpacing,
                        preview = { v -> ed.previewSpec(all, idx) { if (it.pdf != null) it else it.copy(spacing = v) } },
                        commit = { ed.commitSpecPreview() },
                        reset = { apply { it.copy(spacing = it.template.defaultSpacing) } })
                }
                SectionLabel(T.t("Rozmiar", "Size"))
                // Każda strona trzyma własny format i orientację (F5); „cały zeszyt” zmienia format, a orientację zostawia.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in com.mdev.mnotes.core.model.PageFormat.entries) Chip(formatName(f), spec.format == f, { apply { it.withFormat(f) } })
                }
            }
            if (!isPdf && spec.w != spec.h) {
                SectionLabel(T.t("Orientacja", "Orientation"))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(T.t("Pionowo", "Portrait"), !spec.landscape, { apply { it.oriented(false) } }, icon = Icons.Rounded.StayCurrentPortrait)
                    Chip(T.t("Poziomo", "Landscape"), spec.landscape, { apply { it.oriented(true) } }, icon = Icons.Rounded.StayCurrentLandscape)
                }
                Text(T.t("Treść zostaje na miejscu; to, co wyjdzie poza nową krawędź, przesuń lassem.", "Content stays in place; move anything past the new edge with the lasso."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Key.Backspace -> if (ed.sel != null && !ed.readOnly && ed.cut == null && ed.crop == null) { ed.deleteSel(); return true }
            Key.Escape -> {
                if (ed.pipette != null) ed.cancelPipette() else if (ed.cut != null) ed.cancelCut() else if (ed.crop != null) ed.cancelCrop() else if (ed.searchOpen) ed.closeSearch() else if (ed.sel != null) ed.clearSel() else if (!ed.chrome) ed.chrome = true else return false
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
