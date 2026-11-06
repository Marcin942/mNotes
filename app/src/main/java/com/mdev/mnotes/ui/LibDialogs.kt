package com.mdev.mnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.core.text.NameText
import com.mdev.mnotes.core.text.TagNames
import com.mdev.mnotes.app.app
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.render.PageRenderer

fun formatName(f: PageFormat) = when (f) {
    PageFormat.A4 -> "A4"; PageFormat.A5 -> "A5"; PageFormat.A6 -> "A6"; PageFormat.B5 -> "B5"
    PageFormat.LETTER -> "Letter"; PageFormat.LEGAL -> "Legal"
    PageFormat.TABLET -> T.t("Tablet 5:8", "Tablet 5:8"); PageFormat.SQUARE -> T.t("Kwadrat", "Square")
}

fun templateName(t: Template) = when (t) {
    Template.BLANK -> T.t("Czysta", "Blank"); Template.LINED -> T.t("Linie", "Lined"); Template.GRID -> T.t("Kratka", "Grid")
    Template.DOTS -> T.t("Kropki", "Dots"); Template.ISO_DOTS -> T.t("Kropki izo", "Iso dots"); Template.ISO_GRID -> T.t("Siatka izo", "Iso grid")
    Template.CORNELL -> T.t("Cornell", "Cornell"); Template.STAFF -> T.t("Pięciolinia", "Staff"); Template.CHECKLIST -> T.t("Lista zadań", "Checklist")
    Template.MILLIMETER -> T.t("Milimetrowy", "Millimetre")
}

fun paperName(c: Int) = when (c) {
    Paper.WHITE -> T.t("Biały", "White"); Paper.IVORY -> T.t("Kremowy", "Ivory"); Paper.YELLOW -> T.t("Żółty", "Yellow")
    Paper.GRAY -> T.t("Szary", "Gray"); Paper.BLUE -> T.t("Błękit", "Blue"); Paper.GREEN -> T.t("Zielony", "Green")
    Paper.PINK -> T.t("Różowy", "Pink"); Paper.DARK -> T.t("Ciemny", "Dark"); Paper.BLACK -> T.t("Czarny", "Black")
    else -> T.t("Własny", "Custom")
}

/** Podgląd papieru i wzoru dokładnie tym samym rendererem, którego używa edytor. */
@Composable
fun PaperPreview(spec: PageSpec, modifier: Modifier = Modifier) {
    val renderer = remember { PageRenderer(null) }
    Canvas(modifier.aspectRatio(spec.w / spec.h)) {
        val s = size.width / spec.w
        drawIntoCanvasCompat { c -> c.save(); c.scale(s, s); renderer.drawPaper(c, spec, s); c.restore() }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoCanvasCompat(f: (android.graphics.Canvas) -> Unit) =
    drawContext.canvas.nativeCanvas.let(f)

@Composable
fun LibDialogs(st: LibState, onOpen: (String) -> Unit) {
    val repo = LocalContext.current.app.repo
    val set = LocalContext.current.app.settings
    when (val d = st.dialog) {
        null -> Unit
        is LibDialog.NewNb -> NewNotebookDialog(
            onDismiss = { st.dialog = null },
            onCreate = { title, spec, cover, style ->
                st.dialog = null
                val n = repo.createNotebook(title.ifBlank { T.t("Nowy zeszyt", "New notebook") }, spec, cover, style, if (st.place == Place.LIB) st.folderId else null)
                onOpen(n.id)
            },
        )
        is LibDialog.NewFolder -> {
            var name by remember { mutableStateOf("") }
            var color by remember { mutableStateOf(0xFF87CF3E.toInt()) }
            MDialog(
                T.t("Nowy folder", "New folder"), { st.dialog = null }, T.t("Utwórz", "Create"),
                { repo.createFolder(NameText.oneLine(name), if (st.place == Place.LIB) st.folderId else null, color); st.dialog = null },
                confirmEnabled = name.isNotBlank(),
            ) {
                MField(name, { name = it }, Modifier.fillMaxWidth(), hint = T.t("Nazwa folderu", "Folder name"), onDone = {
                    if (name.isNotBlank()) { repo.createFolder(NameText.oneLine(name), if (st.place == Place.LIB) st.folderId else null, color); st.dialog = null }
                })
                Spacer(Modifier.height(14.dp))
                SectionLabel(T.t("Kolor", "Color"))
                FolderColors(color) { color = it }
            }
        }
        is LibDialog.Rename -> {
            val id = d.key.substring(2)
            val isFolder = d.key.startsWith("f:")
            val cur = if (isFolder) repo.folder(id)?.name else repo.meta(id)?.title
            if (cur == null) { st.dialog = null; return }
            var name by remember { mutableStateOf(cur) }
            var color by remember { mutableStateOf(if (isFolder) repo.folder(id)?.color ?: 0 else 0) }
            fun apply() {
                val n = NameText.oneLine(name)
                if (n.isNotEmpty()) { if (isFolder) repo.updateFolder(id) { it.copy(name = n, color = color) } else repo.update(id) { it.copy(title = n) } }
                st.dialog = null
            }
            MDialog(if (isFolder) T.t("Nazwa i kolor folderu", "Folder name and color") else T.t("Zmień nazwę", "Rename"), { st.dialog = null }, T.t("Zapisz", "Save"), ::apply, confirmEnabled = name.isNotBlank()) {
                MField(name, { name = it }, Modifier.fillMaxWidth(), onDone = ::apply)
                if (isFolder) {
                    Spacer(Modifier.height(14.dp))
                    SectionLabel(T.t("Kolor", "Color"))
                    FolderColors(color) { color = it }
                }
            }
        }
        is LibDialog.FolderColor -> {
            val f = repo.folder(d.id)
            if (f == null) { st.dialog = null; return }
            MDialog(T.t("Kolor folderu", "Folder color"), { st.dialog = null }, null) {
                FolderColors(f.color) { c -> repo.updateFolder(f.id) { it.copy(color = c) }; st.dialog = null }
            }
        }
        is LibDialog.Cover -> {
            val nb = repo.meta(d.id)
            if (nb == null) { st.dialog = null; return }
            var cover by remember { mutableStateOf(nb.cover) }
            var style by remember { mutableStateOf(nb.coverStyle) }
            MDialog(T.t("Okładka zeszytu", "Notebook cover"), { st.dialog = null }, T.t("Zapisz", "Save"), { repo.update(d.id) { it.copy(cover = cover, coverStyle = style) }; st.dialog = null }) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f)) { CoverPicker(cover, style, { cover = it }, { style = it }) }
                    NotebookCover(nb.title, cover, style, Modifier.width(108.dp).height(144.dp).shadow(6.dp, CoverShape).clip(CoverShape))
                }
            }
        }
        is LibDialog.Export -> ExportDialog(d.nb, 0, {}) { st.dialog = null }
        is LibDialog.Move -> MoveDialog(st, d)
        is LibDialog.Tags -> TagsDialog(st, d)
        LibDialog.ManageTags -> ManageTagsDialog(st)
        is LibDialog.Confirm -> MDialog(d.title, { st.dialog = null }, d.ok, { st.dialog = null; d.action() }) {
            Text(d.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun FolderColors(selected: Int, onPick: (Int) -> Unit) {
    val colors = remember { listOf(0xFF87CF3E, 0xFF1FB5C9, 0xFF5294E2, 0xFF9B6FD9, 0xFFE8638B, 0xFFE5484D, 0xFFF08A30, 0xFFF5B800, 0xFFC9A66B, 0xFF7D8590).map { it.toInt() } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in colors) Dot(Color(c), 30.dp, selected = c == selected, onClick = { onPick(c) })
    }
}

// ─────────────── Przenoszenie ───────────────

@Composable
private fun MoveDialog(st: LibState, d: LibDialog.Move) {
    val m = mint
    val repo = LocalContext.current.app.repo
    val folderIds = d.keys.filter { it.startsWith("f:") }.map { it.substring(2) }.toSet()
    val nbIds = d.keys.filter { it.startsWith("n:") }.map { it.substring(2) }
    var target by remember { mutableStateOf<String?>(null) }   // null = główny poziom biblioteki
    var chosen by remember { mutableStateOf(false) }

    fun allowed(f: com.mdev.mnotes.data.Folder): Boolean =
        f.trashedAt == 0L && f.id !in folderIds && folderIds.none { repo.isFolderDescendant(f.id, it) }

    fun flatten(parent: String?, depth: Int, out: MutableList<Pair<com.mdev.mnotes.data.Folder, Int>>) {
        for (f in repo.folders.filter { it.parentId == parent && allowed(it) }.sortedWith(compareBy(com.mdev.mnotes.core.text.NameOrder) { it.name })) { out += f to depth; flatten(f.id, depth + 1, out) }
    }
    val rows = remember(repo.folders) { ArrayList<Pair<com.mdev.mnotes.data.Folder, Int>>().also { flatten(null, 0, it) } }

    MDialog(
        T.t("Przenieś do…", "Move to…"), { st.dialog = null }, T.t("Przenieś", "Move"),
        {
            val dest = target
            nbIds.forEach { id -> repo.update(id) { it.copy(folderId = dest) } }
            folderIds.forEach { id -> repo.updateFolder(id) { it.copy(parentId = dest) } }
            st.sel = emptySet(); st.dialog = null
            st.say(T.t("Przeniesiono", "Moved"))
        },
        confirmEnabled = chosen,
    ) {
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            @Composable
            fun Row_(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, depth: Int, tint: Color?, id: String?) {
                val sel = chosen && target == id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (sel) m.accentSoft else Color.Transparent)
                        .clickable { target = id; chosen = true }.padding(start = (8 + depth * 18).dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(20.dp), tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(label, Modifier.weight(1f), fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = m.accentText)
                }
            }
            Row_(Icons.Rounded.Home, T.t("Biblioteka (główny poziom)", "Library (top level)"), 0, null, null)
            for ((f, depth) in rows) Row_(Icons.Rounded.Folder, f.name, depth + 1, Color(f.color), f.id)
        }
    }
}

// ─────────────── Tagi ───────────────

@Composable
private fun TagsDialog(st: LibState, d: LibDialog.Tags) {
    val m = mint
    val repo = LocalContext.current.app.repo
    val all = remember(repo.tagColors, repo.notebooks) {
        (repo.tagColors.keys + repo.notebooks.flatMap { it.tags }).distinct().sortedWith(com.mdev.mnotes.core.text.NameOrder)
    }
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(TagPalette[0]) }

    fun toggle(tag: String) {
        val has = d.nb.all { repo.meta(it)?.tags?.contains(tag) == true }
        d.nb.forEach { id -> repo.update(id) { n -> n.copy(tags = if (has) n.tags - tag else (n.tags + tag).distinct()) } }
    }
    fun add() {
        val n = TagNames.clean(newName)
        if (n.isEmpty()) return
        if (repo.tagColors[n] == null) repo.setTag(n, newColor)
        d.nb.forEach { id -> repo.update(id) { x -> x.copy(tags = (x.tags + n).distinct()) } }
        newName = ""
    }

    MDialog(T.t("Tagi", "Tags"), { st.dialog = null }, T.t("Gotowe", "Done"), { st.dialog = null }, dismissText = "") {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            if (all.isEmpty()) Text(T.t("Nie masz jeszcze tagów — dodaj pierwszy poniżej.", "No tags yet — add the first one below."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (t in all) {
                val count = d.nb.count { repo.meta(it)?.tags?.contains(t) == true }
                val state = when (count) { 0 -> 0; d.nb.size -> 2; else -> 1 }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { toggle(t) }.padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(if (state > 0) m.accent else Color.Transparent)
                            .border(1.5.dp, if (state > 0) m.accent else MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (state == 2) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = m.onAccent) else if (state == 1) Box(Modifier.size(8.dp, 2.dp).background(m.onAccent)) }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(repo.tagColors[t]?.let { Color(it) } ?: Color.Gray))
                    Spacer(Modifier.width(8.dp))
                    Text(t, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel(T.t("Nowy tag", "New tag"))
            MField(newName, { newName = it }, Modifier.fillMaxWidth(), hint = T.t("Nazwa tagu", "Tag name"), onDone = ::add)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (c in TagPalette) Dot(Color(c), 24.dp, selected = c == newColor, onClick = { newColor = c })
                Spacer(Modifier.weight(1f))
                Chip(T.t("Dodaj", "Add"), newName.isNotBlank(), ::add)
            }
            Chip(T.t("Zarządzaj tagami…", "Manage tags…"), false, { st.dialog = LibDialog.ManageTags }, Modifier.padding(top = 12.dp), icon = Icons.Rounded.Edit)
        }
    }
}

// ─────────────── Nowy zeszyt ───────────────

@Composable
fun NewNotebookDialog(onDismiss: () -> Unit, onCreate: (String, PageSpec, Int, Int) -> Unit) {
    val m = mint
    val set = LocalContext.current.app.settings
    var title by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(PageFormat.fromId(set.newFormat) ?: PageFormat.A4) }
    var landscape by remember { mutableStateOf(set.newLandscape) }
    var template by remember { mutableStateOf(Template.fromCode(set.newTemplate)) }
    var paper by remember { mutableStateOf(set.newPaper) }
    var cover by remember { mutableStateOf(CoverPalette.default) }
    var style by remember { mutableStateOf(CoverPalette.DEFAULT_STYLE) }

    val spec = remember(format, landscape, template, paper) { PageSpec.of(format, landscape, template, paper) }

    fun create() {
        set.newFormat = format.id; set.newLandscape = landscape; set.newTemplate = template.code; set.newPaper = paper
        onCreate(NameText.oneLine(title), spec, cover, style)
    }

    MDialog(T.t("Nowy zeszyt", "New notebook"), onDismiss, T.t("Utwórz", "Create"), ::create) {
        Row(Modifier.heightIn(max = 460.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                MField(title, { title = it }, Modifier.fillMaxWidth(), hint = T.t("Tytuł zeszytu", "Notebook title"), onDone = ::create)

                SectionLabel(T.t("Okładka", "Cover"), Modifier.padding(top = 14.dp))
                CoverPicker(cover, style, { cover = it }, { style = it })

                SectionLabel(T.t("Format", "Format"), Modifier.padding(top = 14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in PageFormat.entries) Chip(formatName(f), f == format, { format = f })
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(T.t("Pionowo", "Portrait"), !landscape, { landscape = false })
                    Chip(T.t("Poziomo", "Landscape"), landscape, { landscape = true })
                }

                SectionLabel(T.t("Wzór", "Template"), Modifier.padding(top = 14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in Template.entries) Chip(templateName(t), t == template, { template = t })
                }

                SectionLabel(T.t("Papier", "Paper"), Modifier.padding(top = 14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (p in Paper.presets) Dot(Color(p), 30.dp, selected = p == paper, onClick = { paper = p })
                }
                Text(paperName(paper), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.width(150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel(T.t("Podgląd", "Preview"), Modifier.align(Alignment.Start))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    NotebookCover(title.trim().ifEmpty { T.t("Zeszyt", "Notebook") }, cover, style, Modifier.align(Alignment.Center).width(108.dp).height(144.dp).shadow(6.dp, CoverShape).clip(CoverShape))
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PaperPreview(spec, Modifier.then(if (spec.w > spec.h) Modifier.width(140.dp) else Modifier.height(150.dp)).shadow(4.dp, RoundedCornerShape(2.dp)).border(0.5.dp, m.line))
                }
            }
        }
    }
}

private val TagPalette = listOf(0xFF87CF3E, 0xFF1FB5C9, 0xFF5294E2, 0xFF9B6FD9, 0xFFE8638B, 0xFFE5484D, 0xFFF08A30, 0xFFF5B800).map { it.toInt() }

/** Zarządzanie tagami całej biblioteki: zmiana nazwy i koloru oraz usunięcie, także tagu, którego nie ma już żaden zeszyt. */
@Composable
private fun ManageTagsDialog(st: LibState) {
    val m = mint
    val repo = LocalContext.current.app.repo
    val all = remember(repo.tagColors, repo.notebooks) {
        (repo.tagColors.keys + repo.notebooks.flatMap { it.tags }).distinct().sortedWith(com.mdev.mnotes.core.text.NameOrder)
    }
    var open by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }

    fun rename(t: String) {
        val n = TagNames.clean(name)
        if (n.isNotEmpty() && n != t) {
            repo.renameTag(t, n)
            if (st.tag == t) st.tag = n
            open = n
        }
    }

    MDialog(T.t("Zarządzaj tagami", "Manage tags"), { st.dialog = null }, T.t("Gotowe", "Done"), { st.dialog = null }, dismissText = "") {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (all.isEmpty()) Text(T.t("Nie ma żadnych tagów.", "There are no tags."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (t in all) {
                val used = remember(repo.notebooks, t) { repo.notebooks.count { !it.isTrashed && t in it.tags } }
                val expanded = open == t
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (expanded) m.accentSoft else Color.Transparent)
                        .clickable { open = if (expanded) null else t; name = t }.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(repo.tagColors[t]?.let { Color(it) } ?: Color.Gray))
                    Spacer(Modifier.width(10.dp))
                    Text(t, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (used == 0) T.t("nieużywany", "unused") else "$used",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (expanded) Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MField(name, { name = it }, Modifier.fillMaxWidth(), hint = T.t("Nazwa tagu", "Tag name"), onDone = { rename(t) })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in TagPalette) Dot(Color(c), 24.dp, selected = c == repo.tagColors[t], onClick = { repo.setTag(t, c) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(T.t("Zmień nazwę", "Rename"), false, { rename(t) }, icon = Icons.Rounded.Edit)
                        Chip(T.t("Usuń tag", "Delete tag"), false, {
                            st.dialog = LibDialog.Confirm(
                                T.t("Usunąć tag „$t”?", "Delete tag “$t”?"),
                                if (used == 0) T.t("Żaden zeszyt go nie używa.", "No notebook uses it.")
                                else T.t("Zniknie z $used zeszytów (same zeszyty zostaną).", "It will be removed from $used notebooks (the notebooks stay)."),
                                T.t("Usuń", "Delete"),
                            ) {
                                repo.setTag(t, null)
                                if (st.tag == t) st.go(Place.LIB)
                                st.dialog = LibDialog.ManageTags
                            }
                        }, icon = Icons.Rounded.Delete)
                    }
                }
            }
        }
    }
}
