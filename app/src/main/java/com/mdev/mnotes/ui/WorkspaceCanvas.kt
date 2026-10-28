package com.mdev.mnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mdev.mnotes.core.layout.DualRows
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.Station
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.editor.WorkspaceView
import kotlin.math.roundToInt

/**
 * Płótno edytora (2d-A1): jeden zeszyt jak dotąd albo stanowisko — dokumenty jak kolejne strony jednego dokumentu,
 * w jednym widoku [WorkspaceView] (jedno powiększenie, wspólne przesunięcie, każdy dokument osobno wzdłuż swoich stron).
 * Widok głównego zeszytu jest zawsze tym samym widokiem, więc dołożenie i zamknięcie dokumentów go nie odtwarza.
 * [onTouch] zamyka otwarty panel.
 */
@Composable
internal fun EditorCanvas(st: Station, onTouch: () -> Unit) {
    val m = mint
    var host by remember { mutableStateOf<WorkspaceView?>(null) }
    val rows = st.rows
    Box(Modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            factory = { c -> WorkspaceView(c, st).also { host = it; st.view = it } },
            update = { v -> v.sync(rows, m.accent.toArgb(), m.canvasBg.toArgb(), m.dark) },
            onRelease = { it.release(); if (st.view === it) st.view = null },
            modifier = Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); onTouch() } },
        )
        val h = host
        if (st.on && h != null) LaneChips(st, h, rows)
    }
}

/** Nagłówki idą za modelem co klatkę ruchu — osobny zakres, żeby licznik nie przebudowywał płótna. */
@Composable
private fun LaneChips(st: Station, h: WorkspaceView, rows: List<Editor>) {
    h.tick
    rows.forEachIndexed { i, e -> h.laneRect(i)?.let { r -> RowChip(st, e, r[0], r[1]) } }
}

/** Nagłówek dokumentu w lewym górnym rogu jego pasa: tytuł zeszytu (aktywny w akcencie) i ✕ dla dołożonych. */
@Composable
private fun RowChip(st: Station, e: Editor, left: Float, top: Float) {
    val m = mint
    val on = st.active === e
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.offset { IntOffset(left.roundToInt() + 8.dp.roundToPx(), top.roundToInt() + 6.dp.roundToPx()) }
            .clip(shape).background(if (on) m.accentSoft else m.chrome).border(1.dp, if (on) m.accent else m.line, shape)
            .clickable { st.activate(e) }.padding(start = 10.dp, end = if (e === st.main) 10.dp else 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            e.repo.meta(e.nbId)?.title.orEmpty().ifBlank { T.t("Bez tytułu", "Untitled") }, Modifier.widthIn(max = 220.dp),
            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (on) m.accentText else MaterialTheme.colorScheme.onSurface,
        )
        if (e !== st.main) IconBtn(Icons.Rounded.Close, T.t("Zamknij ten dokument na stanowisku", "Close this document on the workspace"), { st.remove(e) }, size = 28.dp, iconSize = 16.dp)
    }
}

/** Wybór zeszytu do dołożenia na stanowisko: ostatnio otwierane najpierw, bez tych, które już są na stanowisku. */
@Composable
internal fun StationPickDialog(st: Station, onDismiss: () -> Unit) {
    val ed = st.main
    val list = DualRows.candidates(ed.repo.notebooks.filter { !it.isTrashed }, { it.id }, { it.opened }, st.rows.map { it.nbId }.toSet())
    // Pusty stan z krokiem dalej — nowy zeszyt z domyślnymi ustawieniami (jak „Nowa notatka”) od razu na dole.
    fun createBelow() {
        val s = ed.settings
        val spec = PageSpec.of(PageFormat.fromId(s.newFormat) ?: PageFormat.A4, s.newLandscape, Template.fromCode(s.newTemplate), s.newPaper)
        st.add(ed.repo.createNotebook(T.t("Nowy zeszyt", "New notebook"), spec, CoverPalette.default, CoverPalette.DEFAULT_STYLE, null).id)
        onDismiss()
    }
    MDialog(
        T.t("Dołóż zeszyt na stanowisko", "Add a notebook to the workspace"), onDismiss,
        confirm = T.t("Nowy zeszyt", "New notebook"), onConfirm = ::createBelow, dismissText = T.t("Anuluj", "Cancel"),
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(
                T.t("Wybrany zeszyt dołączy do pozostałych jak kolejne strony jednego dokumentu: rząd pod spodem (albo kolumna obok w układzie pionowym). Wzdłuż stron każdy dokument przesuwa się osobno, w poprzek — wszystkie razem; zaznaczenie przeciągnięte lassem do innego dokumentu przenosi się do niego.",
                    "The chosen notebook joins the others like more pages of one document: a row below (or a column beside it in vertical layout). Along the pages each document scrolls on its own, across them all move together; a lasso selection dragged to another document moves into it."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
            )
            if (list.isEmpty()) Text(T.t("Nie ma innych zeszytów. Utwórz nowy — stanie na dole.", "There are no other notebooks. Create one — it goes at the bottom."), style = MaterialTheme.typography.bodyMedium)
            for (nb in list) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { st.add(nb.id); onDismiss() }.padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 18.dp, height = 24.dp).clip(RoundedCornerShape(3.dp)).background(androidx.compose.ui.graphics.Color(nb.cover)))
                Text(nb.title.ifBlank { T.t("Bez tytułu", "Untitled") }, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(T.t("${nb.pages.size} str.", "${nb.pages.size} p."), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Zapisane stanowiska: otwórz (jak kartę), usuń, zapisz bieżące pod nazwą. */
@Composable
internal fun StationsDialog(st: Station, openNotebook: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val list = remember(tick) { st.saved() }
    MDialog(
        T.t("Stanowiska", "Workspaces"), onDismiss,
        confirm = if (st.on) T.t("Zapisz bieżące", "Save current") else null, confirmEnabled = name.isNotBlank(),
        onConfirm = { st.save(name); name = ""; tick++ }, dismissText = T.t("Zamknij", "Close"),
    ) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (st.on) MField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), hint = T.t("Nazwa, np. „Wykład + zadania”", "Name, e.g. “Lecture + exercises”"))
            else Text(T.t("Dołóż zeszyt na stanowisko („⋮” → „Dołóż zeszyt poniżej…”), żeby zapisać układ kilku dokumentów.", "Add a notebook to the workspace (“⋮” → “Add notebook below…”) to save a layout of several documents."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (list.isEmpty()) Text(T.t("Nie ma zapisanych stanowisk.", "No saved workspaces."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            for (s in list) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onDismiss(); st.open(s, openNotebook) }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        s.ids.mapNotNull { st.main.repo.meta(it)?.title }.joinToString(" · ").ifBlank { "…" },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconBtn(Icons.Rounded.Delete, if (deleting == s.name) T.t("Dotknij jeszcze raz, by usunąć", "Tap again to delete") else T.t("Usuń stanowisko", "Delete workspace"),
                    { if (deleting == s.name) { st.delete(s.name); deleting = null; tick++ } else deleting = s.name }, selected = deleting == s.name, size = 32.dp, iconSize = 18.dp)
            }
        }
    }
}
