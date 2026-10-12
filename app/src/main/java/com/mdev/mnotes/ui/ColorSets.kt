package com.mdev.mnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.pen.ColorSet
import com.mdev.mnotes.core.pen.InkColor
import com.mdev.mnotes.core.pen.PenKit
import com.mdev.mnotes.data.Settings
import com.mdev.mnotes.editor.T

/** Narzędzia w kolejności z paska; trzy pierwsze dzielą fabrycznie atramenty. */
private val TOOL_ORDER = listOf(PenKind.BALLPOINT, PenKind.FOUNTAIN, PenKind.PENCIL, PenKind.BRUSH, PenKind.HIGHLIGHTER)

private enum class Dlg { RENAME, RESET, DELETE, ADD, REPLACE }

private const val OPAQUE = 0xFF shl 24

/** Barwa zestawu tak, jak wygląda na papierze, gdy nie wybrano jeszcze żadnego narzędzia. */
private fun paper(s: ColorSet, c: Int) = if (s.hl) InkColor.look(c) else c

/**
 * Kategoria „Kolory narzędzi”: które narzędzie bierze kolory z którego zestawu oraz skład, kolejność i widoczność
 * barw każdego zestawu. Wszystko zapisuje się od razu, a edytor czyta komplet na żywo.
 */
@Composable
internal fun ColorSetsPrefs(set: Settings) {
    val kit = set.penKit
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    fun edit(f: (PenKit) -> PenKit) { set.penKit = f(set.penKit) }

    Text(
        T.t(
            "Zestaw kolorów to lista barw, którą może dzielić dowolna liczba narzędzi: zmiana w zestawie widać wszędzie, gdzie jest używany. Na listwie w edytorze przytrzymaj kolor, żeby go przestawić, schować albo przenieść.",
            "A colour set is a list of colours any number of tools can share: a change shows up everywhere it is used. In the editor, hold a colour on the strip to move, hide or transfer it.",
        ),
        style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.padding(bottom = 4.dp),
    )

    Group(T.t("Skąd narzędzie bierze kolory", "Where each tool takes its colours")) {
        for (k in TOOL_ORDER) key(k) {
            val slot = kit.slot(k)
            val cs = kit.setOf(k)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.width(132.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PenGlyph(k, Color(InkColor.onPaper(slot.color, cs.hl, k)), MaterialTheme.colorScheme.onSurface, Modifier.size(22.dp))
                    Text(penName(k), style = MaterialTheme.typography.bodyMedium)
                }
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in kit.sets) Chip(setName(s), s.id == slot.setId, { edit { it.withSet(k, s.id) } }, glyph = { SetGlyph(s) })
                }
            }
        }
        Text(
            T.t(
                "Barwy zakreślacza są jaśniejsze niż atramentu, więc przy zmianie zestawu między tymi typami kolor wybranego narzędzia jest przeliczany tak, żeby na papierze wyglądał podobnie.",
                "Highlighter colours are lighter than ink, so switching a tool between the two kinds recalculates its colour to look alike on paper.",
            ),
            style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.padding(vertical = 4.dp),
        )
    }

    SectionLabel(T.t("Zestawy kolorów", "Colour sets"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (s in kit.sets) key(s.id) { SetCard(kit, s, ::edit) }
    }

    var newSet by remember { mutableStateOf(false) }
    Box(Modifier.padding(top = 10.dp)) {
        Chip(T.t("Nowy zestaw", "New set"), false, { newSet = true }, icon = Icons.Rounded.Add)
    }
    if (kit.sets.size >= PenKit.MAX_SETS) {
        Text(T.t("Osiągnięto limit ${PenKit.MAX_SETS} zestawów.", "Reached the limit of ${PenKit.MAX_SETS} sets."), style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.padding(top = 4.dp))
    }
    if (newSet) NewSetDialog(kit, { newSet = false }) { name, hl -> edit { it.addSet(name, hl) }; newSet = false }
}

@Composable
private fun SetCard(kit: PenKit, s: ColorSet, edit: ((PenKit) -> PenKit) -> Unit) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var sel by remember { mutableStateOf<Int?>(null) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var copyMenu by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    val picked = sel?.takeIf { it in s.colors }
    val at = picked?.let { s.colors.indexOf(it) } ?: -1
    val users = kit.users(s.id)
    val others = kit.sets.filter { it.id != s.id }

    Group {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SetGlyph(s, 20.dp)
            Column(Modifier.weight(1f)) {
                Text(setName(s), style = MaterialTheme.typography.titleSmall)
                Text(
                    (if (s.hl) T.t("Kolory zakreślacza", "Highlighter colours") else T.t("Zwykłe kolory", "Ink colours")) + " · " +
                        if (users.isEmpty()) T.t("nie używa go żadne narzędzie", "no tool uses it")
                        else T.t("używają: ", "used by: ") + users.joinToString(", ") { penName(it).lowercase() },
                    style = MaterialTheme.typography.bodySmall, color = dim,
                )
            }
            IconBtn(Icons.Rounded.Edit, T.t("Zmień nazwę", "Rename"), { dlg = Dlg.RENAME }, size = 36.dp, iconSize = 20.dp)
            IconBtn(
                Icons.Rounded.ContentCopy, T.t("Duplikuj zestaw", "Duplicate set"),
                { edit { it.addSet(setName(s) + " " + T.t("(kopia)", "(copy)"), s.hl, from = s.id) } },
                enabled = kit.sets.size < PenKit.MAX_SETS, size = 36.dp, iconSize = 20.dp,
            )
            if (PenKit.builtIn(s.id)) IconBtn(Icons.Rounded.RestartAlt, T.t("Przywróć fabryczne kolory", "Restore factory colours"), { dlg = Dlg.RESET }, enabled = kit.changed(s.id), size = 36.dp, iconSize = 20.dp)
            else IconBtn(Icons.Rounded.Delete, T.t("Usuń zestaw", "Delete set"), { dlg = Dlg.DELETE }, size = 36.dp, iconSize = 20.dp)
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(T.t("Na listwie", "On the strip"), style = MaterialTheme.typography.bodyMedium, color = dim)
            Spacer(Modifier.weight(1f))
            IconBtn(Icons.Rounded.Remove, T.t("Mniej kolorów na listwie", "Fewer colours on the strip"), { edit { it.withVisible(s.id, s.visible - 1) } }, enabled = s.visible > 1, size = 32.dp, iconSize = 18.dp)
            Text("${s.visible}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
            IconBtn(Icons.Rounded.Add, T.t("Więcej kolorów na listwie", "More colours on the strip"), { edit { it.withVisible(s.id, s.visible + 1) } }, enabled = s.visible < minOf(PenKit.MAX_VISIBLE, s.colors.size), size = 32.dp, iconSize = 18.dp)
        }

        fun toggle(c: Int) { sel = if (c == picked) null else c }
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (c in s.colors.take(s.visible)) Dot(Color(paper(s, c)), 32.dp, selected = c == picked, onClick = { toggle(c) })
        }
        if (s.colors.size > s.visible) {
            Text(T.t("Schowane (poza listwą)", "Hidden (off the strip)"), style = MaterialTheme.typography.labelSmall, color = dim)
            FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in s.colors.drop(s.visible)) Dot(Color(paper(s, c)), 32.dp, selected = c == picked, onClick = { toggle(c) })
            }
        }

        FlowRow(Modifier.padding(top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (picked != null) {
                if (at > 0) Chip(T.t("Wcześniej", "Earlier"), false, { edit { it.moveColor(s.id, at, at - 1) } }, icon = Icons.AutoMirrored.Rounded.ArrowBack)
                if (at < s.colors.size - 1) Chip(T.t("Później", "Later"), false, { edit { it.moveColor(s.id, at, at + 1) } }, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                if (at >= s.visible) Chip(T.t("Pokaż na listwie", "Show on strip"), false, { edit { it.showColor(s.id, picked) } }, icon = Icons.Rounded.Visibility)
                else if (s.visible > 1) Chip(T.t("Schowaj z listwy", "Hide from strip"), false, { edit { it.hideColor(s.id, picked) } }, icon = Icons.Rounded.VisibilityOff)
                Chip(T.t("Zmień…", "Change…"), false, { dlg = Dlg.REPLACE }, icon = Icons.Rounded.Edit)
                if (others.isNotEmpty()) {
                    Box {
                        Chip(T.t("Kopiuj do…", "Copy to…"), false, { copyMenu = true }, icon = Icons.Rounded.ContentCopy)
                        TargetMenu(copyMenu, { copyMenu = false }, others, s, picked) { o -> edit { it.copyColor(s.id, picked, o.id) } }
                    }
                    if (s.colors.size > 1) Box {
                        Chip(T.t("Przenieś do…", "Move to…"), false, { moveMenu = true }, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                        TargetMenu(moveMenu, { moveMenu = false }, others, s, picked) { o -> edit { it.moveColorTo(s.id, picked, o.id) } }
                    }
                }
                if (s.colors.size > 1) Chip(T.t("Usuń", "Remove"), false, { edit { it.removeColor(s.id, picked) } }, icon = Icons.Rounded.Delete)
            }
            Chip(T.t("Dodaj kolor", "Add colour"), false, { dlg = Dlg.ADD }, icon = Icons.Rounded.Add)
        }
        Text(
            if (picked == null) T.t("Dotknij koloru, żeby go przestawić, schować, zmienić albo przenieść do innego zestawu. Kolory: ${s.colors.size} / ${PenKit.MAX_COLORS}.", "Tap a colour to move, hide, change or transfer it to another set. Colours: ${s.colors.size} / ${PenKit.MAX_COLORS}.")
            else T.t("Kolory: ${s.colors.size} / ${PenKit.MAX_COLORS}.", "Colours: ${s.colors.size} / ${PenKit.MAX_COLORS}."),
            style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.padding(bottom = 6.dp),
        )
    }

    when (dlg) {
        Dlg.RENAME -> RenameDialog(s, { dlg = null }) { name -> edit { it.renameSet(s.id, name) }; dlg = null }
        Dlg.RESET -> MDialog(
            T.t("Przywrócić fabryczne kolory?", "Restore factory colours?"), { dlg = null },
            confirm = T.t("Przywróć", "Restore"), onConfirm = { edit { it.resetSet(s.id) }; dlg = null },
        ) { Text(T.t("Zestaw „${setName(s)}” wróci do barw i liczby kolorów na listwie z instalacji. Nazwa zostanie.", "The “${setName(s)}” set goes back to its original colours and strip size. Its name stays.")) }
        Dlg.DELETE -> MDialog(
            T.t("Usunąć zestaw?", "Delete the set?"), { dlg = null },
            confirm = T.t("Usuń", "Delete"), onConfirm = { edit { it.removeSet(s.id) }; dlg = null },
        ) {
            Text(
                if (users.isEmpty()) T.t("Zestaw „${setName(s)}” zniknie.", "The “${setName(s)}” set will be removed.")
                else T.t("Zestaw „${setName(s)}” zniknie, a narzędzia, które go używały, wrócą do swojego fabrycznego zestawu i zostaną przy obecnym kolorze.", "The “${setName(s)}” set will be removed; tools using it go back to their default set and keep their current colour."),
            )
        }
        Dlg.ADD -> {
            val pal = if (s.hl) HIGHLIGHTER_PALETTE else PALETTE
            ColorDialog(T.t("Dodaj kolor", "Add colour"), s, pal.firstOrNull { it !in s.colors } ?: pal.first(), T.t("Dodaj", "Add"), { dlg = null }) { c ->
                edit { it.addColor(s.id, c) }; sel = c or OPAQUE; dlg = null
            }
        }
        Dlg.REPLACE -> if (picked != null) ColorDialog(T.t("Zmień kolor", "Change colour"), s, picked, T.t("Zmień", "Change"), { dlg = null }) { c ->
            edit { it.replaceColor(s.id, picked, c) }; sel = c or OPAQUE; dlg = null
        }
        null -> {}
    }
}

/** Lista zestawów docelowych; pozycja jest wyłączona, gdy cel jest pełny albo ma już tę barwę (po przeliczeniu typu). */
@Composable
private fun TargetMenu(open: Boolean, close: () -> Unit, targets: List<ColorSet>, from: ColorSet, color: Int, onPick: (ColorSet) -> Unit) {
    DropdownMenu(open, close) {
        for (o in targets) {
            val c = InkColor.between(color, from.hl, o.hl) or OPAQUE
            DropdownMenuItem(
                text = { Text(setName(o)) }, enabled = c !in o.colors && o.colors.size < PenKit.MAX_COLORS,
                leadingIcon = { SetGlyph(o) }, onClick = { close(); onPick(o) },
            )
        }
    }
}

@Composable
private fun ColorDialog(title: String, s: ColorSet, start: Int, confirm: String, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    var draft by remember { mutableStateOf(start) }
    val c = draft or OPAQUE
    MDialog(title, onDismiss, confirm = confirm, onConfirm = { onOk(c) }, confirmEnabled = c !in s.colors && s.colors.size <= PenKit.MAX_COLORS) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            ColorPicker(draft, { draft = it }, highlighter = s.hl)
            if (c in s.colors) Text(T.t("Ten kolor już jest w zestawie.", "This colour is already in the set."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun RenameDialog(s: ColorSet, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(s.name) }
    MDialog(T.t("Nazwa zestawu", "Set name"), onDismiss, confirm = T.t("Zapisz", "Save"), onConfirm = { onOk(name) }) {
        MField(name, { name = it.take(PenKit.MAX_NAME) }, Modifier.fillMaxWidth(), hint = setName(s.copy(name = "")), onDone = { onOk(name) })
        if (PenKit.builtIn(s.id)) Text(T.t("Pusta nazwa przywraca wbudowaną.", "An empty name restores the built-in one."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun NewSetDialog(kit: PenKit, onDismiss: () -> Unit, onOk: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(T.t("Zestaw", "Set") + " ${kit.sets.size + 1}") }
    var type by remember { mutableStateOf(0) }
    MDialog(T.t("Nowy zestaw kolorów", "New colour set"), onDismiss, confirm = T.t("Utwórz", "Create"), onConfirm = { onOk(name, type == 1) }, confirmEnabled = name.isNotBlank()) {
        MField(name, { name = it.take(PenKit.MAX_NAME) }, Modifier.fillMaxWidth(), onDone = { if (name.isNotBlank()) onOk(name, type == 1) })
        Text(T.t("Rodzaj", "Kind"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Choice(listOf(T.t("Zwykłe kolory", "Ink colours"), T.t("Kolory zakreślacza", "Highlighter colours")), type) { type = it }
        Text(
            T.t("Rodzaj zostaje na stałe, bo te same barwy znaczą co innego jako atrament i jako zakreślacz. Zestaw startuje z fabrycznymi kolorami tego rodzaju.", "The kind is permanent, since the same colours mean something else as ink and as highlighter. The set starts from that kind's factory colours."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Mała próbka zestawu: trzy nachodzące na siebie koła w jego pierwszych barwach. */
@Composable
internal fun SetGlyph(s: ColorSet, size: Dp = 16.dp) {
    val line = mint.line
    Canvas(Modifier.size(size * 1.9f, size)) {
        val r = this.size.height / 2f
        val step = this.size.height * 0.45f
        s.colors.take(3).forEachIndexed { i, c ->
            val ctr = Offset(r + i * step, r)
            drawCircle(Color(paper(s, c)), r - 0.5f, ctr)
            drawCircle(line, r - 0.5f, ctr, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
    }
}
