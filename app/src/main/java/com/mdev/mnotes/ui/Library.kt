package com.mdev.mnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdev.mnotes.app.app
import com.mdev.mnotes.app.openNewWindow
import com.mdev.mnotes.data.CoverStyle
import com.mdev.mnotes.data.Folder
import com.mdev.mnotes.data.NotebookMeta
import com.mdev.mnotes.data.Repository
import com.mdev.mnotes.editor.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.mdev.mnotes.core.search.TextSearch
import com.mdev.mnotes.data.LibHit
import kotlinx.coroutines.launch
import kotlin.math.sin

object Place { const val LIB = 0; const val RECENT = 1; const val FAV = 2; const val TRASH = 3; const val TAG = 4 }

/** Cel menu kontekstowego: klucz elementu (`n:` zeszyt / `f:` folder) i miejsce kliknięcia. */
class MenuTarget(val key: String, val x: Int, val y: Int)

sealed interface LibDialog {
    data object NewNb : LibDialog
    data object NewFolder : LibDialog
    data class Export(val nb: String) : LibDialog
    data class Rename(val key: String) : LibDialog
    data class Move(val keys: Set<String>) : LibDialog
    data class Tags(val nb: Set<String>) : LibDialog
    data class FolderColor(val id: String) : LibDialog
    data class Confirm(val title: String, val text: String, val ok: String, val action: () -> Unit) : LibDialog
}

/** Stan nawigacji biblioteki — przeżywa przejście do edytora i z powrotem. */
class LibState {
    var place by mutableIntStateOf(Place.LIB)
    var folderId by mutableStateOf<String?>(null)
    var tag by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var searchOpen by mutableStateOf(false)
    var sel by mutableStateOf<Set<String>>(emptySet())
    var anchor by mutableStateOf<String?>(null)
    /** Pozycja „kursora” klawiatury (koniec zakresu przy Shift+strzałka). */
    var cursor by mutableStateOf<String?>(null)
    /** Liczba kolumn siatki ikon (ustawiana przez IconGrid; potrzebna do strzałek góra/dół). */
    var cols = 1
    var menu by mutableStateOf<MenuTarget?>(null)
    var dialog by mutableStateOf<LibDialog?>(null)
    var toast by mutableStateOf("")
    val selecting get() = sel.isNotEmpty()

    fun go(place: Int, folder: String? = null, tag: String? = null) {
        sel = emptySet(); anchor = null; query = ""; searchOpen = false; menu = null
        this.place = place; this.folderId = folder; this.tag = tag
    }

    fun say(s: String) { toast = s }
}

private fun nbKey(id: String) = "n:$id"
private fun foKey(id: String) = "f:$id"
private fun keyId(k: String) = k.substring(2)

// ─────────────────────────────── Okładki i miniatury ───────────────────────────────

@Composable
fun CoverArt(color: Int, style: Int, modifier: Modifier = Modifier) {
    val base = Color(color)
    val light = lerp(base, Color.White, 0.20f)
    val dark = lerp(base, Color.Black, 0.30f)
    Canvas(modifier) {
        drawRect(Brush.linearGradient(listOf(light, base, dark), start = Offset.Zero, end = Offset(size.width, size.height)))
        val pat = Color.White.copy(alpha = 0.17f)
        val w = size.width; val h = size.height
        when (style) {
            CoverStyle.STRIPES -> {
                var x = -h
                while (x < w) { drawLine(pat, Offset(x, h), Offset(x + h, 0f), strokeWidth = w * 0.035f); x += w * 0.1f }
            }
            CoverStyle.DOTS -> {
                val st = w * 0.1f
                var y = st / 2
                while (y < h) { var x = st / 2; while (x < w) { drawCircle(pat, w * 0.016f, Offset(x, y)); x += st }; y += st }
            }
            CoverStyle.WAVES -> {
                val amp = w * 0.03f
                var y = h * 0.08f
                while (y < h) {
                    val p = Path(); p.moveTo(0f, y)
                    var x = 0f
                    while (x <= w) { p.lineTo(x, y + sin(x / w * 6.2832f * 3f) * amp); x += 4f }
                    drawPath(p, pat, style = Stroke(w * 0.012f)); y += h * 0.065f
                }
            }
            CoverStyle.GRID -> {
                val st = w * 0.12f
                var x = st; while (x < w) { drawLine(pat, Offset(x, 0f), Offset(x, h), w * 0.006f); x += st }
                var y = st; while (y < h) { drawLine(pat, Offset(0f, y), Offset(w, y), w * 0.006f); y += st }
            }
        }
        drawRect(Color.Black.copy(alpha = 0.20f), size = Size(w * 0.055f, h))
        drawRect(Color.White.copy(alpha = 0.14f), topLeft = Offset(w * 0.055f, 0f), size = Size(w * 0.01f, h))
    }
}

/** Pierwsza strona zeszytu jako miniatura (async, z cache). */
@Composable
fun PageThumb(nb: NotebookMeta, wPx: Int, modifier: Modifier = Modifier, fit: ContentScale = ContentScale.Fit) {
    val thumbs = LocalContext.current.app.thumbs
    val key = "c:${nb.id}:${nb.modified}:$wPx"
    var bmp by remember(key) { mutableStateOf(thumbs.cached(key)) }
    LaunchedEffect(key) {
        if (bmp != null) return@LaunchedEffect
        thumbs.async(key, { thumbs.cover(nb, wPx) }) { bmp = it }
        var n = 0
        while (bmp == null && n++ < 60) { delay(120); bmp = thumbs.cached(key) }
    }
    val img = remember(bmp) { bmp?.asImageBitmap() }
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = fit)
    }
}

@Composable
private fun FolderArt(color: Int, modifier: Modifier = Modifier) {
    val base = Color(color)
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val tabH = h * 0.14f
        val back = Path().apply {
            moveTo(w * 0.04f, h * 0.12f); lineTo(w * 0.38f, h * 0.12f); lineTo(w * 0.46f, h * 0.12f + tabH)
            lineTo(w * 0.96f, h * 0.12f + tabH); lineTo(w * 0.96f, h * 0.9f); lineTo(w * 0.04f, h * 0.9f); close()
        }
        drawPath(back, lerp(base, Color.Black, 0.22f))
        val front = Path().apply {
            moveTo(w * 0.04f, h * 0.30f); lineTo(w * 0.96f, h * 0.30f); lineTo(w * 0.96f, h * 0.9f); lineTo(w * 0.04f, h * 0.9f); close()
        }
        drawPath(front, Brush.verticalGradient(listOf(lerp(base, Color.White, 0.22f), base), startY = h * 0.3f, endY = h * 0.9f))
    }
}

// ─────────────────────────────── Ekran biblioteki ───────────────────────────────

@Composable
fun LibraryScreen(st: LibState, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val m = mint
    val scope = rememberCoroutineScope()
    val app = androidx.compose.ui.platform.LocalContext.current.app
    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).fileDrop { uris ->
            scope.launch { val made = importUris(app, uris, st.folderId); if (made.size == 1) onOpen(made[0].id) }
        },
    ) {
        val wide = maxWidth >= 840.dp
        val drawer = rememberDrawerState(DrawerValue.Closed)
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(st, onSettings, {}, Modifier.width(250.dp).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(m.line))
                LibMain(st, true, onOpen, {}, Modifier.weight(1f))
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = m.sidebar, modifier = Modifier.width(280.dp)) {
                        Sidebar(st, onSettings, { scope.launch { drawer.close() } }, Modifier.fillMaxSize())
                    }
                },
            ) { LibMain(st, false, onOpen, { scope.launch { drawer.open() } }, Modifier.fillMaxSize()) }
        }
        LibDialogs(st, onOpen)
        Toast(st)
    }
}

@Composable
private fun Toast(st: LibState) {
    LaunchedEffect(st.toast) { if (st.toast.isNotEmpty()) { delay(2200); st.toast = "" } }
    if (st.toast.isEmpty()) return
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            st.toast, color = Color.White, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xE6222428)).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

// — Panel boczny —

@Composable
private fun Sidebar(st: LibState, onSettings: () -> Unit, afterNav: () -> Unit, modifier: Modifier) {
    val m = mint
    val repo = LocalContext.current.app.repo
    val trashCount = remember(repo.notebooks, repo.folders) {
        repo.notebooks.count { it.isTrashed && !repo.folderTrashed(it.folderId) } + repo.folders.count { it.trashedAt > 0 && !repo.folderTrashed(it.parentId) }
    }
    val tags = remember(repo.notebooks, repo.tagColors) {
        (repo.tagColors.keys + repo.notebooks.filter { !it.isTrashed }.flatMap { it.tags }).distinct().sortedBy { it.lowercase() }
    }
    Column(modifier.background(m.sidebar).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(m.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Edit, null, tint = m.onAccent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("mNotes", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            SectionLabel(T.t("Miejsca", "Places"), Modifier.padding(horizontal = 8.dp))
            SideItem(Icons.Rounded.Home, T.t("Biblioteka", "Library"), st.place == Place.LIB && st.folderId == null) { st.go(Place.LIB); afterNav() }
            SideItem(Icons.Rounded.History, T.t("Ostatnie", "Recent"), st.place == Place.RECENT) { st.go(Place.RECENT); afterNav() }
            SideItem(Icons.Rounded.Star, T.t("Ulubione", "Favorites"), st.place == Place.FAV) { st.go(Place.FAV); afterNav() }
            SideItem(Icons.Rounded.Delete, T.t("Kosz", "Trash"), st.place == Place.TRASH, count = trashCount) { st.go(Place.TRASH); afterNav() }

            val roots = repo.folders.filter { it.parentId == null && it.trashedAt == 0L }.sortedBy { it.name.lowercase() }
            if (roots.isNotEmpty()) {
                SectionLabel(T.t("Foldery", "Folders"), Modifier.padding(horizontal = 8.dp))
                for (f in roots) FolderNode(st, repo, f, 0, afterNav)
            }
            if (tags.isNotEmpty()) {
                SectionLabel(T.t("Tagi", "Tags"), Modifier.padding(horizontal = 8.dp))
                for (t in tags) SideItem(
                    Icons.AutoMirrored.Rounded.Label, t, st.place == Place.TAG && st.tag == t,
                    iconTint = repo.tagColors[t]?.let { Color(it) },
                ) { st.go(Place.TAG, tag = t); afterNav() }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(m.line))
        Box(Modifier.padding(8.dp)) { SideItem(Icons.Rounded.Settings, T.t("Ustawienia", "Settings"), false) { onSettings(); afterNav() } }
    }
}

@Composable
private fun FolderNode(st: LibState, repo: Repository, f: Folder, depth: Int, afterNav: () -> Unit) {
    val kids = repo.folders.filter { it.parentId == f.id && it.trashedAt == 0L }.sortedBy { it.name.lowercase() }
    val open = st.place == Place.LIB && st.folderId != null && (st.folderId == f.id || repo.isFolderDescendant(st.folderId, f.id))
    SideItem(
        Icons.Rounded.Folder, f.name, st.place == Place.LIB && st.folderId == f.id, indent = depth, iconTint = Color(f.color),
        expand = if (kids.isEmpty()) null else open,
    ) { st.go(Place.LIB, folder = f.id); afterNav() }
    if (open) for (k in kids) FolderNode(st, repo, k, depth + 1, afterNav)
}

@Composable
private fun SideItem(
    icon: ImageVector, label: String, selected: Boolean, count: Int = 0, indent: Int = 0, iconTint: Color? = null,
    expand: Boolean? = null, onClick: () -> Unit,
) {
    val m = mint
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) m.accentSoft else if (hover) m.hover else Color.Transparent)
            .hoverable(src).clickable(onClick = onClick)
            .padding(start = (10 + indent * 14).dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expand != null) Icon(if (expand) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight, null, Modifier.size(16.dp).padding(end = 0.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (indent > 0) Spacer(Modifier.width(16.dp))
        Icon(icon, null, Modifier.size(20.dp), tint = iconTint ?: if (selected) m.accentText else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(
            label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
            color = if (selected) m.accentText else MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (count > 0) Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// — Główna część —

@Composable
private fun LibMain(st: LibState, wide: Boolean, onOpen: (String) -> Unit, openDrawer: () -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val app = ctx.app; val repo = app.repo; val set = app.settings
    val m = mint
    val rootFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var searchHasFocus by remember { mutableStateOf(false) }

    val q = st.query.trim()
    val vis = remember(repo.notebooks, repo.folders) { repo.notebooks.filter { !it.isTrashed && !repo.folderTrashed(it.folderId) } }
    val words = remember(q) { TextSearch.words(q) }
    // Szukanie w treści zeszytów (pola tekstowe, zakładki) w tle, z krótką zwłoką przy pisaniu; wynik ważny tylko dla swojego zapytania.
    val scan by produceState(q to emptyMap<String, LibHit>(), q, vis) {
        if (q.length < 2 || words.isEmpty()) { value = q to emptyMap(); return@produceState }
        delay(250)
        value = q to withContext(Dispatchers.IO) { app.search.scan(vis, words) }
    }
    val textHits: Map<String, LibHit> = if (scan.first == q) scan.second else emptyMap()
    val liveFolders = remember(repo.folders) { repo.folders.filter { it.trashedAt == 0L && !repo.folderTrashed(it.parentId) } }

    val cmp: Comparator<NotebookMeta> = when (set.libSort) {
        0 -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        2 -> compareBy { it.created }
        else -> compareBy { it.modified }
    }
    fun List<NotebookMeta>.userSorted() = sortedWith(if (set.libSortDesc) cmp.reversed() else cmp)

    val folders: List<Folder>
    val books: List<NotebookMeta>
    val inTrash = st.place == Place.TRASH && q.isEmpty()
    when {
        q.isNotEmpty() -> {
            folders = liveFolders.filter { it.name.contains(q, true) || TextSearch.containsAll(TextSearch.fold(it.name), words) }.sortedBy { it.name.lowercase() }
            books = vis.filter { n ->
                n.title.contains(q, true) || n.tags.any { it.contains(q, true) } || n.id in textHits ||
                    TextSearch.containsAll(TextSearch.fold(n.title + " " + n.tags.joinToString(" ")), words)
            }.userSorted()
        }
        st.place == Place.LIB -> {
            folders = liveFolders.filter { it.parentId == st.folderId }.sortedBy { it.name.lowercase() }
            books = vis.filter { it.folderId == st.folderId }.userSorted()
        }
        st.place == Place.RECENT -> { folders = emptyList(); books = vis.sortedByDescending { it.opened }.take(60) }
        st.place == Place.FAV -> { folders = emptyList(); books = vis.filter { it.favorite }.userSorted() }
        st.place == Place.TAG -> { folders = emptyList(); books = vis.filter { st.tag in it.tags }.userSorted() }
        else -> {
            folders = repo.folders.filter { it.trashedAt > 0 && !repo.folderTrashed(it.parentId) }.sortedBy { it.name.lowercase() }
            books = repo.notebooks.filter { it.isTrashed && !repo.folderTrashed(it.folderId) }.sortedByDescending { it.trashedAt }
        }
    }
    val order = remember(folders, books) { folders.map { foKey(it.id) } + books.map { nbKey(it.id) } }

    fun open(key: String) {
        val id = keyId(key)
        if (key.startsWith("f:")) { if (!inTrash) st.go(Place.LIB, folder = id) else st.say(T.t("Przywróć folder, aby go otworzyć", "Restore the folder to open it")) }
        else if (inTrash) st.say(T.t("Przywróć zeszyt z kosza, aby go otworzyć", "Restore the notebook to open it"))
        else {
            // Z wyniku szukania w treści: otwórz od strony z trafieniem i wpisz zapytanie w pasek szukania edytora.
            textHits[id]?.takeIf { q.isNotEmpty() }?.let { h ->
                repo.update(id) { it.copy(lastPage = h.pageIndex) }
                app.pendingSearch = q
            }
            onOpen(id)
        }
    }
    fun click(key: String, ctrl: Boolean, shift: Boolean) {
        st.menu = null
        val a = st.anchor
        when {
            shift && a != null && a in order && key in order -> {
                val i = order.indexOf(a); val j = order.indexOf(key)
                st.sel = order.subList(minOf(i, j), maxOf(i, j) + 1).toSet()
            }
            ctrl || st.selecting -> { st.sel = if (key in st.sel) st.sel - key else st.sel + key; st.anchor = key }
            else -> open(key)
        }
    }
    fun longPress(key: String) { st.menu = null; st.sel = st.sel + key; st.anchor = key }
    fun secondary(key: String, p: androidx.compose.ui.geometry.Offset, tileOrigin: IntOffset) {
        if (key !in st.sel) { st.sel = setOf(key); st.anchor = key }
        st.menu = MenuTarget(key, tileOrigin.x + p.x.toInt(), tileOrigin.y + p.y.toInt())
    }

    fun navTo(key: String?, extend: Boolean) {
        if (key == null) return
        val a = st.anchor?.takeIf { it in order }
        if (extend && a != null) {
            val i = order.indexOf(a); val j = order.indexOf(key)
            st.sel = order.subList(minOf(i, j), maxOf(i, j) + 1).toSet()
        } else { st.sel = setOf(key); st.anchor = key }
        st.cursor = key
    }
    fun navMove(dx: Int, dy: Int, extend: Boolean) {
        if (order.isEmpty()) return
        val cur = st.cursor?.takeIf { it in order && it in st.sel } ?: st.anchor?.takeIf { it in order && it in st.sel }
            ?: st.sel.lastOrNull { it in order }
        if (cur == null) { navTo(order.first(), false); return }
        val i = order.indexOf(cur); val last = order.lastIndex
        val grid = set.libView == 0
        val c = if (grid) st.cols.coerceAtLeast(1) else 1
        val nf = folders.size
        var j = i
        if (dx != 0 && grid) j = (i + dx).coerceIn(0, last)
        if (dy != 0) {
            if (!grid) j = (i + dy).coerceIn(0, last)
            else {
                val inF = i < nf
                val start = if (inF) 0 else nf
                val len = if (inF) nf else order.size - nf
                val si = i - start
                if (dy > 0) {
                    j = when {
                        si + c < len -> i + c
                        si / c < (len - 1) / c -> start + len - 1
                        inF && order.size > nf -> nf + minOf(si % c, order.size - nf - 1)
                        else -> i
                    }
                } else {
                    j = when {
                        si - c >= 0 -> i - c
                        !inF && nf > 0 -> minOf(((nf - 1) / c) * c + si % c, nf - 1)
                        else -> i
                    }
                }
            }
        }
        navTo(order[j], extend)
    }

    val ops = remember(st, repo) { LibOps(st, repo, onOpen) }
    val doImport = rememberImportLauncher({ if (st.place == Place.LIB) st.folderId else null }, onOpen)

    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    LaunchedEffect(st.dialog) { if (st.dialog == null) runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(st.searchOpen) { if (st.searchOpen) runCatching { searchFocus.requestFocus() } }

    Column(
        modifier.background(MaterialTheme.colorScheme.background).focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || st.dialog != null) return@onPreviewKeyEvent false
                val ctrl = e.isCtrlPressed; val shift = e.isShiftPressed
                when {
                    ctrl && e.key == Key.N -> { if (shift) st.dialog = LibDialog.NewFolder else st.dialog = LibDialog.NewNb; true }
                    ctrl && e.key == Key.O -> { doImport(); true }
                    ctrl && e.key == Key.E && st.sel.size == 1 && st.sel.first().startsWith("n:") -> { st.dialog = LibDialog.Export(keyId(st.sel.first())); true }
                    ctrl && e.key == Key.F -> { st.searchOpen = true; runCatching { searchFocus.requestFocus() }; true }
                    ctrl && e.key == Key.A && !searchHasFocus -> { st.sel = order.toSet(); true }
                    ctrl && e.key == Key.One -> { set.libView = 0; true }
                    ctrl && e.key == Key.Two -> { set.libView = 1; true }
                    ctrl && e.key == Key.Three -> { set.libView = 2; true }
                    e.key == Key.Escape -> {
                        when {
                            st.menu != null -> st.menu = null
                            st.selecting -> st.sel = emptySet()
                            st.query.isNotEmpty() || st.searchOpen -> { st.query = ""; st.searchOpen = false; runCatching { rootFocus.requestFocus() } }
                            st.place != Place.LIB || st.folderId != null -> ops.up()
                            else -> return@onPreviewKeyEvent false
                        }; true
                    }
                    searchHasFocus -> false
                    e.key == Key.DirectionRight && !e.isAltPressed -> { navMove(1, 0, shift); true }
                    e.key == Key.DirectionLeft && !e.isAltPressed -> { navMove(-1, 0, shift); true }
                    e.key == Key.DirectionDown -> { navMove(0, 1, shift); true }
                    e.key == Key.DirectionUp && !e.isAltPressed -> { navMove(0, -1, shift); true }
                    e.key == Key.MoveHome -> { navTo(order.firstOrNull(), shift); true }
                    e.key == Key.MoveEnd -> { navTo(order.lastOrNull(), shift); true }
                    e.key == Key.Delete -> { if (st.selecting) ops.trashOrDelete(st.sel, inTrash); st.selecting }
                    e.key == Key.F2 && st.sel.size == 1 -> { st.dialog = LibDialog.Rename(st.sel.first()); true }
                    (e.key == Key.Enter || e.key == Key.NumPadEnter) && st.sel.size == 1 -> { open(st.sel.first()); true }
                    (e.key == Key.Backspace || (e.isAltPressed && e.key == Key.DirectionUp)) -> { ops.up(); true }
                    else -> false
                }
            },
    ) {
        if (st.selecting) SelectionBar(st, ops, inTrash, order) else TopBar(st, wide, ops, openDrawer, searchFocus, doImport) { searchHasFocus = it }
        Box(Modifier.fillMaxWidth().height(1.dp).background(m.line))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val empty = folders.isEmpty() && books.isEmpty()
            when {
                !repo.loaded -> Unit
                empty -> EmptyState(st, q)
                set.libView == 0 -> IconGrid(st, folders, books, inTrash, ::click, ::longPress, ::secondary, set.libIcon, set.libPreview, textHits)
                else -> ListView(st, folders, books, inTrash, ::click, ::longPress, ::secondary, set.libView == 2, textHits)
            }
            ItemMenu(st, ops, inTrash)
        }
        StatusBar(st, folders.size + books.size, set.libView == 0)
    }
}

/** Operacje na zaznaczeniu. */
private class LibOps(val st: LibState, val repo: Repository, val onOpen: (String) -> Unit) {
    fun nbs(keys: Set<String>) = keys.filter { it.startsWith("n:") }.map { keyId(it) }
    fun fos(keys: Set<String>) = keys.filter { it.startsWith("f:") }.map { keyId(it) }

    fun up() {
        if (st.query.isNotEmpty() || st.searchOpen) { st.query = ""; st.searchOpen = false; return }
        if (st.place == Place.LIB && st.folderId != null) st.go(Place.LIB, folder = repo.folder(st.folderId)?.parentId)
        else if (st.place != Place.LIB) st.go(Place.LIB)
    }

    fun trash(keys: Set<String>) {
        val now = System.currentTimeMillis()
        repo.trash(nbs(keys))
        fos(keys).forEach { id -> repo.updateFolder(id) { it.copy(trashedAt = now) } }
        st.sel = emptySet()
        st.say(T.t("Przeniesiono do kosza", "Moved to trash"))
    }

    fun restore(keys: Set<String>) {
        repo.restore(nbs(keys))
        fos(keys).forEach { id -> repo.updateFolder(id) { it.copy(trashedAt = 0L) } }
        st.sel = emptySet()
        st.say(T.t("Przywrócono", "Restored"))
    }

    fun deleteForever(keys: Set<String>) {
        st.dialog = LibDialog.Confirm(
            T.t("Usunąć na zawsze?", "Delete forever?"),
            T.t("Tej operacji nie można cofnąć. Zaznaczone elementy (${keys.size}) zostaną trwale usunięte.", "This cannot be undone. The ${keys.size} selected item(s) will be permanently deleted."),
            T.t("Usuń", "Delete"),
        ) {
            nbs(keys).forEach { repo.deleteForever(it) }
            fos(keys).forEach { repo.deleteFolderForever(it) }
            st.sel = emptySet()
        }
    }

    fun trashOrDelete(keys: Set<String>, inTrash: Boolean) { if (inTrash) deleteForever(keys) else trash(keys) }

    fun favorite(keys: Set<String>) {
        val ids = nbs(keys)
        val all = ids.all { repo.meta(it)?.favorite == true }
        ids.forEach { id -> repo.update(id) { it.copy(favorite = !all) } }
    }

    fun duplicate(keys: Set<String>) {
        nbs(keys).forEach { repo.duplicate(it, T.t(" (kopia)", " (copy)")) }
        st.sel = emptySet()
    }
}

// — Pasek górny —

@Composable
private fun TopBar(st: LibState, wide: Boolean, ops: LibOps, openDrawer: () -> Unit, searchFocus: FocusRequester, doImport: () -> Unit, onSearchFocus: (Boolean) -> Unit) {
    val m = mint
    val app = LocalContext.current.app; val repo = app.repo; val set = app.settings
    var sortMenu by remember { mutableStateOf(false) }
    var newMenu by remember { mutableStateOf(false) }
    val showSearch = wide || st.searchOpen || st.query.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().background(m.header).statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (!wide) IconBtn(Icons.Rounded.Menu, T.t("Menu", "Menu"), openDrawer)
        val canUp = st.place != Place.LIB || st.folderId != null || st.query.isNotEmpty()
        if (canUp) IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, T.t("Wstecz", "Back"), { ops.up() }, shortcut = "Esc")

        if (!(showSearch && !wide)) {
            Breadcrumb(st, repo, Modifier.weight(1f))
        } else Spacer(Modifier.weight(0.0001f))

        if (showSearch) {
            val fm = Modifier.then(if (wide) Modifier.width(240.dp) else Modifier.weight(1f))
            MField(
                st.query, { st.query = it }, fm.focusRequester(searchFocus).onFocusChanged { onSearchFocus(it.isFocused) },
                hint = T.t("Szukaj…", "Search…"),
                leading = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            if (st.query.isNotEmpty() || (!wide && st.searchOpen)) IconBtn(Icons.Rounded.Close, T.t("Wyczyść", "Clear"), { st.query = ""; st.searchOpen = false })
        } else IconBtn(Icons.Rounded.Search, T.t("Szukaj", "Search"), { st.searchOpen = true }, shortcut = "Ctrl+F")

        if (!(showSearch && !wide)) {
            if (wide) {
                Row(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(2.dp)) {
                    IconBtn(Icons.Rounded.ViewModule, T.t("Ikony", "Icons"), { set.libView = 0 }, selected = set.libView == 0, size = 34.dp, iconSize = 20.dp, shortcut = "Ctrl+1")
                    IconBtn(Icons.AutoMirrored.Rounded.ViewList, T.t("Lista", "List"), { set.libView = 1 }, selected = set.libView == 1, size = 34.dp, iconSize = 20.dp, shortcut = "Ctrl+2")
                    IconBtn(Icons.Rounded.ViewHeadline, T.t("Kompaktowy", "Compact"), { set.libView = 2 }, selected = set.libView == 2, size = 34.dp, iconSize = 20.dp, shortcut = "Ctrl+3")
                }
            } else IconBtn(
                when (set.libView) { 0 -> Icons.Rounded.ViewModule; 1 -> Icons.AutoMirrored.Rounded.ViewList; else -> Icons.Rounded.ViewHeadline },
                T.t("Widok", "View"), { set.libView = (set.libView + 1) % 3 },
            )
            Box {
                IconBtn(Icons.Rounded.Sort, T.t("Sortuj", "Sort"), { sortMenu = true })
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    val names = listOf(T.t("Nazwa", "Name"), T.t("Data modyfikacji", "Modified"), T.t("Data utworzenia", "Created"))
                    names.forEachIndexed { i, n ->
                        DropdownMenuItem(
                            text = { Text(n) },
                            leadingIcon = { if (set.libSort == i) Icon(Icons.Rounded.Check, null, tint = m.accentText) else Spacer(Modifier.size(24.dp)) },
                            onClick = { if (set.libSort != i) { set.libSort = i; set.libSortDesc = i != 0 }; sortMenu = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(T.t("Malejąco", "Descending")) },
                        leadingIcon = { if (set.libSortDesc) Icon(Icons.Rounded.Check, null, tint = m.accentText) else Spacer(Modifier.size(24.dp)) },
                        onClick = { set.libSortDesc = !set.libSortDesc },
                    )
                    DropdownMenuItem(
                        text = { Text(T.t("Podgląd strony zamiast okładki", "Page preview instead of cover")) },
                        leadingIcon = { if (set.libPreview) Icon(Icons.Rounded.Check, null, tint = m.accentText) else Spacer(Modifier.size(24.dp)) },
                        onClick = { set.libPreview = !set.libPreview },
                    )
                }
            }
        }
        if (st.place == Place.TRASH) {
            if (wide || !showSearch) {
                val has = repo.notebooks.any { it.isTrashed } || repo.folders.any { it.trashedAt > 0 }
                Chip(T.t("Opróżnij kosz", "Empty trash"), false, {
                    if (has) st.dialog = LibDialog.Confirm(
                        T.t("Opróżnić kosz?", "Empty trash?"), T.t("Wszystkie elementy w koszu zostaną trwale usunięte.", "All items in the trash will be permanently deleted."), T.t("Opróżnij", "Empty"),
                    ) { repo.emptyTrash() }
                }, icon = Icons.Rounded.DeleteForever)
            }
        } else if (wide || !showSearch) {
            Box(Modifier.padding(start = 4.dp)) {
                Row(
                    Modifier.height(36.dp).clip(RoundedCornerShape(8.dp)).background(m.accent).clickable { newMenu = true }.padding(horizontal = if (wide) 14.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Add, null, tint = m.onAccent, modifier = Modifier.size(20.dp))
                    if (wide) Text(T.t("Nowy", "New"), color = m.onAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(newMenu, { newMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(T.t("Nowy zeszyt…", "New notebook…")) }, leadingIcon = { Icon(Icons.Rounded.Description, null) },
                        trailingIcon = { Text("Ctrl+N", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { newMenu = false; st.dialog = LibDialog.NewNb },
                    )
                    DropdownMenuItem(
                        text = { Text(T.t("Nowy folder…", "New folder…")) }, leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                        trailingIcon = { Text("Ctrl+Shift+N", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { newMenu = false; st.dialog = LibDialog.NewFolder },
                    )
                    DropdownMenuItem(
                        text = { Text(T.t("Importuj PDF, obrazy, .mnotes…", "Import PDF, images, .mnotes…")) }, leadingIcon = { Icon(Icons.Rounded.FileOpen, null) },
                        trailingIcon = { Text("Ctrl+O", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { newMenu = false; doImport() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Breadcrumb(st: LibState, repo: Repository, modifier: Modifier) {
    val parts = ArrayList<Pair<String, (() -> Unit)?>>()
    val root = T.t("Biblioteka", "Library")
    when {
        st.query.isNotEmpty() -> parts += T.t("Wyniki wyszukiwania", "Search results") to null
        st.place == Place.RECENT -> parts += T.t("Ostatnie", "Recent") to null
        st.place == Place.FAV -> parts += T.t("Ulubione", "Favorites") to null
        st.place == Place.TRASH -> parts += T.t("Kosz", "Trash") to null
        st.place == Place.TAG -> parts += ("#" + (st.tag ?: "")) to null
        else -> {
            val chain = ArrayList<Folder>()
            var f = repo.folder(st.folderId); var guard = 0
            while (f != null && guard++ < 64) { chain.add(0, f); f = repo.folder(f.parentId) }
            parts += root to (if (chain.isEmpty()) null else { { st.go(Place.LIB) } })
            chain.forEachIndexed { i, fo -> parts += fo.name to (if (i == chain.lastIndex) null else { { st.go(Place.LIB, folder = fo.id) } }) }
        }
    }
    val scroll = rememberScrollState(Int.MAX_VALUE)
    LaunchedEffect(parts.size) { scroll.scrollTo(scroll.maxValue) }
    Row(modifier.horizontalScroll(scroll).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, (name, go) ->
            if (i > 0) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            val last = i == parts.lastIndex
            Text(
                name, maxLines = 1, style = if (last) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).then(if (go != null) Modifier.clickable { go() } else Modifier).padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SelectionBar(st: LibState, ops: LibOps, inTrash: Boolean, order: List<String>) {
    val m = mint
    val repo = LocalContext.current.app.repo
    val hasNb = st.sel.any { it.startsWith("n:") }
    Row(
        Modifier.fillMaxWidth().background(m.accentSoft).statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.Rounded.Close, T.t("Anuluj zaznaczenie", "Clear selection"), { st.sel = emptySet() }, shortcut = "Esc")
        Text(T.t("Zaznaczono: ${st.sel.size}", "${st.sel.size} selected"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp).weight(1f))
        IconBtn(Icons.Rounded.SelectAll, T.t("Zaznacz wszystko", "Select all"), { st.sel = order.toSet() }, shortcut = "Ctrl+A")
        if (inTrash) {
            IconBtn(Icons.Rounded.RestoreFromTrash, T.t("Przywróć", "Restore"), { ops.restore(st.sel) })
            IconBtn(Icons.Rounded.DeleteForever, T.t("Usuń na zawsze", "Delete forever"), { ops.deleteForever(st.sel) }, shortcut = "Del")
        } else {
            if (hasNb) {
                val allFav = ops.nbs(st.sel).all { repo.meta(it)?.favorite == true }
                IconBtn(if (allFav) Icons.Rounded.Star else Icons.Rounded.StarBorder, T.t("Ulubione", "Favorite"), { ops.favorite(st.sel) }, tint = if (allFav) Color(0xFFF5B800) else Color.Unspecified)
                IconBtn(Icons.AutoMirrored.Rounded.Label, T.t("Tagi", "Tags"), { st.dialog = LibDialog.Tags(ops.nbs(st.sel).toSet()) })
                IconBtn(Icons.Rounded.ContentCopy, T.t("Duplikuj", "Duplicate"), { ops.duplicate(st.sel) })
            }
            IconBtn(Icons.Rounded.DriveFileMove, T.t("Przenieś", "Move"), { st.dialog = LibDialog.Move(st.sel) })
            IconBtn(Icons.Rounded.Delete, T.t("Do kosza", "Move to trash"), { ops.trash(st.sel) }, shortcut = "Del")
        }
    }
}

// — Zawartość —

@Composable
private fun EmptyState(st: LibState, q: String) {
    val m = mint
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        val (icon, title, sub) = when {
            q.isNotEmpty() -> Triple(Icons.Rounded.Search, T.t("Nic nie znaleziono", "Nothing found"), T.t("Spróbuj innej frazy lub taga.", "Try another phrase or tag."))
            st.place == Place.TRASH -> Triple(Icons.Rounded.Delete, T.t("Kosz jest pusty", "Trash is empty"), T.t("Usunięte zeszyty trafiają tu na ${Repository.TRASH_DAYS} dni.", "Deleted notebooks stay here for ${Repository.TRASH_DAYS} days."))
            st.place == Place.FAV -> Triple(Icons.Rounded.StarBorder, T.t("Brak ulubionych", "No favorites"), T.t("Oznacz zeszyt gwiazdką, aby miał tu skrót.", "Star a notebook to pin it here."))
            st.place == Place.RECENT -> Triple(Icons.Rounded.History, T.t("Jeszcze nic nie otwierałeś", "Nothing opened yet"), "")
            st.place == Place.TAG -> Triple(Icons.AutoMirrored.Rounded.Label, T.t("Brak zeszytów z tym tagiem", "No notebooks with this tag"), "")
            else -> Triple(Icons.Rounded.Description, T.t("Ten folder jest pusty", "This folder is empty"), T.t("Utwórz pierwszy zeszyt — Ctrl+N.", "Create your first notebook — Ctrl+N."))
        }
        Box(Modifier.size(84.dp).clip(CircleShape).background(m.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(40.dp), tint = m.accentText)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (st.place == Place.LIB && q.isEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(m.accent).clickable { st.dialog = LibDialog.NewNb }.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Add, null, tint = m.onAccent)
                Text(T.t("Nowy zeszyt", "New notebook"), color = m.onAccent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private typealias ClickFn = (String, Boolean, Boolean) -> Unit
private typealias SecFn = (String, Offset, IntOffset) -> Unit

@Composable
private fun IconGrid(
    st: LibState, folders: List<Folder>, books: List<NotebookMeta>, inTrash: Boolean,
    click: ClickFn, longPress: (String) -> Unit, secondary: SecFn, iconDp: Float, preview: Boolean, hits: Map<String, LibHit> = emptyMap(),
) {
    val gridState = rememberLazyGridState()
    val twoSections = folders.isNotEmpty() && books.isNotEmpty()
    LaunchedEffect(st.cursor) {
        val k = st.cursor ?: return@LaunchedEffect
        val idx = when {
            k.startsWith("f:") -> folders.indexOfFirst { foKey(it.id) == k }.let { if (it < 0) it else if (twoSections) it + 1 else it }
            else -> books.indexOfFirst { nbKey(it.id) == k }.let { if (it < 0) it else it + (if (twoSections) folders.size + 2 else 0) }
        }
        if (idx < 0) return@LaunchedEffect
        val vis = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
        val vp = gridState.layoutInfo.viewportSize.height
        if (vis == null || vis.offset.y < 0 || vis.offset.y + vis.size.height > vp) gridState.animateScrollToItem(idx)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val avail = maxWidth - 36.dp
        st.cols = maxOf(1, ((avail + 16.dp) / (iconDp.dp + 16.dp)).toInt())
    LazyVerticalGrid(
        columns = GridCells.Adaptive(iconDp.dp), modifier = Modifier.fillMaxSize(), state = gridState,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (folders.isNotEmpty() && books.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(T.t("Foldery", "Folders")) }
        items(folders, key = { "f" + it.id }) { f ->
            val key = foKey(f.id)
            FolderTile(f, key in st.sel, st.selecting, key, click, longPress, secondary)
        }
        if (folders.isNotEmpty() && books.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(T.t("Zeszyty", "Notebooks")) }
        items(books, key = { "n" + it.id }) { n ->
            val key = nbKey(n.id)
            NotebookTile(n, key in st.sel, st.selecting, preview, inTrash, key, click, longPress, secondary, hits[n.id])
        }
    }
    }
}

@Composable
private fun SelectBadge(selected: Boolean, selecting: Boolean, modifier: Modifier) {
    val m = mint
    if (!selecting && !selected) return
    Box(
        modifier.size(22.dp).clip(CircleShape).background(if (selected) m.accent else Color.Black.copy(alpha = 0.28f))
            .border(1.5.dp, if (selected) m.accent else Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(Icons.Rounded.Check, null, tint = m.onAccent, modifier = Modifier.size(15.dp)) }
}

@Composable
private fun NotebookTile(
    n: NotebookMeta, selected: Boolean, selecting: Boolean, preview: Boolean, inTrash: Boolean, key: String,
    click: ClickFn, longPress: (String) -> Unit, secondary: SecFn, hit: LibHit? = null,
) {
    val m = mint
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) m.accentSoft else if (hover) m.hover else Color.Transparent)
            .hoverable(src)
            .trackRoot { origin = it }
            .tileInput({ c, s -> click(key, c, s) }, { longPress(key) }, { p -> secondary(key, p, origin) })
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.76f), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp)
            if (preview) {
                val spec = n.pages.firstOrNull()?.spec
                val ar = if (spec != null) (spec.w / spec.h).coerceIn(0.4f, 2.5f) else 0.707f
                PageThumb(n, 420, Modifier.then(if (ar >= 0.76f) Modifier.fillMaxWidth().aspectRatio(ar) else Modifier.fillMaxHeight().aspectRatio(ar)).shadow(5.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)))
            } else {
                Box(Modifier.fillMaxSize().shadow(6.dp, shape).clip(shape)) {
                    CoverArt(n.cover, n.coverStyle, Modifier.fillMaxSize())
                    Box(
                        Modifier.align(Alignment.Center).padding(start = 18.dp, end = 12.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.93f)).padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(n.title, color = Color(0xFF24272B), maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (n.favorite) Icon(Icons.Rounded.Star, null, Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp), tint = Color(0xFFF5B800))
            if (n.locked) Icon(Icons.Rounded.Lock, null, Modifier.align(Alignment.BottomEnd).padding(6.dp).size(16.dp), tint = Color.White)
            SelectBadge(selected, selecting, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (preview) Text(n.title, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
        Text(
            if (inTrash) T.t("Usunięto ", "Deleted ") + dateText(n.trashedAt) else T.t("${n.pages.size} str. · ", "${n.pages.size} p. · ") + dateText(n.modified),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
        if (hit != null) Text(
            T.t("str. ${hit.pageIndex + 1}: ", "p. ${hit.pageIndex + 1}: ") + hit.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall, color = m.accentText, modifier = Modifier.padding(top = 2.dp),
        )
        if (n.tags.isNotEmpty()) TagDots(n.tags, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TagDots(tags: List<String>, modifier: Modifier = Modifier) {
    val repo = LocalContext.current.app.repo
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (t in tags.take(6)) Box(Modifier.size(8.dp).clip(CircleShape).background(repo.tagColors[t]?.let { Color(it) } ?: Color.Gray))
    }
}

@Composable
private fun FolderTile(f: Folder, selected: Boolean, selecting: Boolean, key: String, click: ClickFn, longPress: (String) -> Unit, secondary: SecFn) {
    val m = mint
    val repo = LocalContext.current.app.repo
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val count = repo.notebooks.count { it.folderId == f.id && !it.isTrashed } + repo.folders.count { it.parentId == f.id && it.trashedAt == 0L }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) m.accentSoft else if (hover) m.hover else Color.Transparent)
            .hoverable(src).trackRoot { origin = it }
            .tileInput({ c, s -> click(key, c, s) }, { longPress(key) }, { p -> secondary(key, p, origin) })
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.Center) {
            FolderArt(f.color, Modifier.fillMaxSize().padding(horizontal = 14.dp))
            SelectBadge(selected, selecting, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
        Text(T.t("$count el.", "$count items"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ListView(
    st: LibState, folders: List<Folder>, books: List<NotebookMeta>, inTrash: Boolean,
    click: ClickFn, longPress: (String) -> Unit, secondary: SecFn, compact: Boolean, hits: Map<String, LibHit> = emptyMap(),
) {
    val m = mint
    val repo = LocalContext.current.app.repo
    val listState = rememberLazyListState()
    LaunchedEffect(st.cursor) {
        val k = st.cursor ?: return@LaunchedEffect
        val idx = if (k.startsWith("f:")) folders.indexOfFirst { foKey(it.id) == k }
        else books.indexOfFirst { nbKey(it.id) == k }.let { if (it < 0) it else it + folders.size }
        if (idx < 0) return@LaunchedEffect
        val vis = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
        val vp = listState.layoutInfo.viewportSize.height
        if (vis == null || vis.offset < 0 || vis.offset + vis.size > vp) listState.animateScrollToItem(idx)
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        items(folders, key = { "f" + it.id }) { f ->
            val key = foKey(f.id)
            val sel = key in st.sel
            ListRow(
                sel, st.selecting, compact, key, click, longPress, secondary,
                leading = { FolderArt(f.color, Modifier.size(if (compact) 26.dp else 42.dp)) },
                title = f.name, subtitle = T.t("Folder", "Folder"), trailing = "",
            )
        }
        items(books, key = { "n" + it.id }) { n ->
            val key = nbKey(n.id)
            val sel = key in st.sel
            ListRow(
                sel, st.selecting, compact, key, click, longPress, secondary,
                leading = {
                    if (compact) CoverArt(n.cover, n.coverStyle, Modifier.size(width = 20.dp, height = 26.dp).clip(RoundedCornerShape(3.dp)))
                    else PageThumb(n, 140, Modifier.size(width = 40.dp, height = 54.dp).shadow(2.dp, RoundedCornerShape(3.dp)).clip(RoundedCornerShape(3.dp)))
                },
                title = n.title,
                subtitle = (if (n.folderId != null && (st.query.isNotEmpty() || st.place != Place.LIB)) (repo.folder(n.folderId)?.name ?: "") + " · " else "") +
                    T.t("${n.pages.size} str.", "${n.pages.size} pages") +
                    (hits[n.id]?.let { " · " + T.t("str. ${it.pageIndex + 1}: ", "p. ${it.pageIndex + 1}: ") + it.snippet } ?: ""),
                trailing = if (inTrash) T.t("Usunięto ", "Deleted ") + dateText(n.trashedAt) else dateTimeText(n.modified),
                tags = n.tags, fav = n.favorite,
            )
        }
    }
}

@Composable
private fun ListRow(
    selected: Boolean, selecting: Boolean, compact: Boolean, key: String,
    click: ClickFn, longPress: (String) -> Unit, secondary: SecFn,
    leading: @Composable () -> Unit, title: String, subtitle: String, trailing: String,
    tags: List<String> = emptyList(), fav: Boolean = false,
) {
    val m = mint
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) m.accentSoft else if (hover) m.hover else Color.Transparent)
            .hoverable(src).trackRoot { origin = it }
            .tileInput({ c, s -> click(key, c, s) }, { longPress(key) }, { p -> secondary(key, p, origin) })
            .padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting || selected) { SelectBadge(selected, true, Modifier); Spacer(Modifier.width(10.dp)) }
        Box(Modifier.width(if (compact) 28.dp else 44.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                if (fav) Icon(Icons.Rounded.Star, null, Modifier.padding(start = 6.dp).size(15.dp), tint = Color(0xFFF5B800))
                if (tags.isNotEmpty()) TagDots(tags, Modifier.padding(start = 8.dp))
            }
            if (!compact) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (compact) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 14.dp))
        Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusBar(st: LibState, count: Int, icons: Boolean) {
    val m = mint
    val set = LocalContext.current.app.settings
    Box(Modifier.fillMaxWidth().height(1.dp).background(m.line))
    Row(
        Modifier.fillMaxWidth().background(m.header).navigationBarsPadding().height(30.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (st.selecting) T.t("Zaznaczono ${st.sel.size} z $count", "${st.sel.size} of $count selected") else T.t("$count elementów", "$count items"),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        if (icons) {
            Icon(Icons.Rounded.ViewModule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                set.libIcon, { set.libIcon = it }, valueRange = 110f..320f, modifier = Modifier.width(140.dp).height(24.dp),
                colors = SliderDefaults.colors(thumbColor = m.accent, activeTrackColor = m.accent, inactiveTrackColor = m.line),
            )
            Icon(Icons.Rounded.ViewModule, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// — Menu kontekstowe —

@Composable
private fun ItemMenu(st: LibState, ops: LibOps, inTrash: Boolean) {
    val mt = st.menu ?: return
    val m = mint
    val multi = st.sel.size > 1
    val isFolder = mt.key.startsWith("f:")
    val repo = LocalContext.current.app.repo
    fun close() { st.menu = null }
    @Composable
    fun Item(icon: ImageVector, text: String, hint: String = "", tint: Color = Color.Unspecified, onClick: () -> Unit) {
        DropdownMenuItem(
            text = { Text(text) }, leadingIcon = { Icon(icon, null, tint = tint) },
            trailingIcon = if (hint.isNotEmpty()) { { Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } else null,
            onClick = { close(); onClick() },
        )
    }
    var base by remember { mutableStateOf(IntOffset.Zero) }
    Box(Modifier.fillMaxSize().trackRoot { base = it }) {
      Box(Modifier.offset { IntOffset(mt.x - base.x, mt.y - base.y) }) {
        DropdownMenu(true, { close() }) {
            if (inTrash) {
                Item(Icons.Rounded.RestoreFromTrash, T.t("Przywróć", "Restore")) { ops.restore(st.sel) }
                Item(Icons.Rounded.DeleteForever, T.t("Usuń na zawsze", "Delete forever"), "Del") { ops.deleteForever(st.sel) }
            } else {
                if (!multi) Item(Icons.Rounded.Folder.takeIf { isFolder } ?: Icons.Rounded.Description, T.t("Otwórz", "Open"), "Enter") {
                    if (isFolder) st.go(Place.LIB, folder = keyId(mt.key)) else ops.onOpen(keyId(mt.key))
                }
                if (!multi && !isFolder) { val ctx = LocalContext.current; Item(Icons.Rounded.OpenInNew, T.t("Otwórz w nowym oknie", "Open in new window")) { ctx.openNewWindow(keyId(mt.key)) } }
                if (!multi) Item(Icons.Rounded.Edit, T.t("Zmień nazwę…", "Rename…"), "F2") { st.dialog = LibDialog.Rename(mt.key) }
                if (!multi && isFolder) Item(Icons.Rounded.Palette, T.t("Kolor folderu…", "Folder color…")) { st.dialog = LibDialog.FolderColor(keyId(mt.key)) }
                if (!isFolder || multi) {
                    val ids = ops.nbs(st.sel)
                    if (ids.isNotEmpty()) {
                        val allFav = ids.all { repo.meta(it)?.favorite == true }
                        Item(if (allFav) Icons.Rounded.StarBorder else Icons.Rounded.Star, if (allFav) T.t("Usuń z ulubionych", "Remove from favorites") else T.t("Dodaj do ulubionych", "Add to favorites")) { ops.favorite(st.sel) }
                        Item(Icons.AutoMirrored.Rounded.Label, T.t("Tagi…", "Tags…")) { st.dialog = LibDialog.Tags(ids.toSet()) }
                    }
                }
                if (!multi && !isFolder) Item(Icons.Rounded.IosShare, T.t("Eksportuj / udostępnij…", "Export / share…"), "Ctrl+E") { st.dialog = LibDialog.Export(keyId(mt.key)) }
                Item(Icons.Rounded.DriveFileMove, T.t("Przenieś do…", "Move to…")) { st.dialog = LibDialog.Move(st.sel) }
                if (ops.nbs(st.sel).isNotEmpty()) Item(Icons.Rounded.ContentCopy, T.t("Duplikuj", "Duplicate")) { ops.duplicate(st.sel) }
                HorizontalDivider()
                Item(Icons.Rounded.Delete, T.t("Przenieś do kosza", "Move to trash"), "Del", tint = MaterialTheme.colorScheme.error) { ops.trash(st.sel) }
            }
        }
      }
    }
}

// Pozycja kafelka w układzie okna — menu kontekstowe jest przeliczane względem własnego kontenera.
private fun Modifier.trackRoot(f: (IntOffset) -> Unit): Modifier = this.onGloballyPositioned {
    val p = it.positionInRoot(); f(IntOffset(p.x.toInt(), p.y.toInt()))
}
