package com.mdev.mnotes.ui

import android.view.ViewGroup
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mdev.mnotes.core.layout.DocLayout
import com.mdev.mnotes.core.layout.DualRows
import com.mdev.mnotes.core.layout.Workbench
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.NoteView
import com.mdev.mnotes.editor.Station
import com.mdev.mnotes.editor.T
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Wysokość treści rzędu w pt: najwyższa strona i marginesy układu poziomego (jak `DocLayout.docHeight`). */
private fun rowContent(e: Editor): Float =
    (e.doc.pages.maxOfOrNull { it.spec.h } ?: 842f) + 2f * DocLayout.DEFAULT_PAD

/**
 * Płótno edytora (2b-A7): jeden zeszyt jak dotąd albo stanowisko — dokumenty jeden pod drugim na wspólnym polu, każdy
 * jako rząd stron przesuwany w bok osobno; pole przewija się w pionie całe, powiększenie jest wspólne. Zaznaczenie
 * przeciągnięte poza rząd ląduje w dokumencie pod palcem. Widok głównego zeszytu jest zawsze tym samym widokiem (to samo
 * miejsce w kompozycji), więc dołożenie i zamknięcie rzędów go nie odtwarza. [onTouch] zamyka otwarty panel.
 */
@Composable
internal fun EditorCanvas(st: Station, onTouch: () -> Unit) {
    val m = mint
    val scope = rememberCoroutineScope()
    var fling by remember { mutableStateOf<Job?>(null) }
    val touch = Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); fling?.cancel(); onTouch() } }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val dens = LocalDensity.current
        val viewW = constraints.maxWidth
        val viewH = constraints.maxHeight.toFloat()
        val gap = with(dens) { 10.dp.toPx() }
        val rows = st.rows
        // Struktura dokumentów (nowa strona pozioma) zmienia wysokość rzędu.
        for (e in rows) e.structTick
        val contents = rows.map { rowContent(it) }
        val maxContent = contents.maxOrNull() ?: 882f
        // „100 %” stanowiska: jeden rząd wypełnia okno; na start dwa rzędy mieszczą się naraz.
        val base = viewH / maxContent
        if (st.on && st.zoom <= 0f) st.zoom = ((viewH - gap) / 2f / maxContent).coerceAtMost(base)
        fun geometry(z: Float) = Workbench.geometry(if (st.on) contents.map { it * z } else listOf(viewH), gap)
        val g = geometry(st.zoom)
        val scroll = if (st.on) Workbench.clampScroll(st.scroll, g.total, viewH) else 0f
        if (st.on && scroll != st.scroll) st.scroll = scroll
        fun clampedScroll(s: Float) = Workbench.clampScroll(s, geometry(st.zoom).total, viewH)

        Layout(
            content = {
                rows.forEachIndexed { i, e ->
                    key(e.nbId) {
                        AndroidView(
                            factory = { c -> NoteView(c, e).apply { layoutParams = ViewGroup.LayoutParams(-1, -1); if (st.on) { baseZoomOverride = base; startZoom = st.zoom } } },
                            update = { v ->
                                v.accent = m.accent.toArgb(); v.viewBg = m.canvasBg.toArgb(); v.darkUi = m.dark
                                if (!st.on) { v.leaveStation(); return@AndroidView }
                                val gi = geometry(st.zoom)
                                val idx = st.rows.indexOf(e)
                                if (idx < 0) return@AndroidView
                                v.baseZoomOverride = base
                                v.hostOffsetY = gi.tops[idx] - st.scroll
                                v.band = gi.band(idx, st.scroll, viewH) ?: floatArrayOf(0f, 0f)
                                if (abs(v.currentZoom - st.zoom) > 1e-4f * st.zoom) v.followZoom(st.zoom)
                                v.onTouched = { fling?.cancel(); st.activate(e) }
                                v.onPanY = { dy -> st.scroll = clampedScroll(st.scroll - dy) }
                                v.onFlingY = { vy ->
                                    fling?.cancel()
                                    fling = scope.launch {
                                        var last = 0f
                                        AnimationState(0f, -vy).animateDecay(exponentialDecay()) { st.scroll = clampedScroll(st.scroll + (value - last)); last = value }
                                    }
                                }
                                // Wspólne powiększenie: punkt pod palcami zostaje w miejscu, choć rzędy zmieniają wysokość.
                                v.onZoomChange = { z ->
                                    if (abs(z - st.zoom) > 1e-4f * st.zoom) {
                                        val old = geometry(st.zoom)
                                        val j = st.rows.indexOf(e)
                                        val anchor = (if (j >= 0) old.tops[j] - st.scroll else 0f) + (v.zoomAnchorY.takeIf { !it.isNaN() } ?: (v.height / 2f))
                                        st.zoom = z
                                        st.scroll = Workbench.clampScroll(Workbench.keepAnchor(old, geometry(z), st.scroll, anchor), geometry(z).total, viewH)
                                        for (o in st.rows) if (o !== e) o.view?.followZoom(z)
                                    }
                                }
                                v.onDropOutside = { els, cx, cy ->
                                    val gn = geometry(st.zoom)
                                    val j = st.rows.indexOf(e)
                                    val hit = if (j >= 0) Workbench.locate(gn, gn.tops[j] - st.scroll + cy, st.scroll) else null
                                    val target = hit?.let { st.rows.getOrNull(it.first) }
                                    if (hit == null || target == null || target === e || target.readOnly) false
                                    else {
                                        target.view?.dropIn(els, e.nbId, cx, hit.second)
                                        st.activate(target)
                                        true
                                    }
                                }
                            },
                            onRelease = { if (e !== st.main) it.dispose() },
                            modifier = Modifier.clipToBounds().then(touch),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize().pointerInput(st.on) {
                // Przerwy między rzędami: pionowy ruch przewija pole.
                if (st.on) detectVerticalDragGestures { c, dy -> c.consume(); fling?.cancel(); st.scroll = clampedScroll(st.scroll - dy) }
            },
        ) { measurables, cons ->
            val gm = geometry(st.zoom)
            val ps = measurables.mapIndexed { i, mm ->
                val h = gm.heights.getOrElse(i) { viewH }.roundToInt().coerceAtLeast(1)
                mm.measure(Constraints.fixed(viewW, h))
            }
            layout(cons.maxWidth, cons.maxHeight) {
                ps.forEachIndexed { i, p -> p.place(0, (gm.tops.getOrElse(i) { 0f } - (if (st.on) st.scroll else 0f)).roundToInt()) }
            }
        }
        if (st.on) rows.forEachIndexed { i, e -> RowChip(st, e, i, (g.tops[i] - scroll)) }
    }
}

/** Nagłówek rzędu w lewym górnym rogu: tytuł zeszytu (aktywny w akcencie) i ✕ dla dołożonych rzędów. */
@Composable
private fun RowChip(st: Station, e: Editor, i: Int, top: Float) {
    val m = mint
    val on = st.active === e
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.offset { IntOffset(8.dp.roundToPx(), top.roundToInt() + 6.dp.roundToPx()) }
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

/** Wybór zeszytu do dołożenia pod spodem: ostatnio otwierane najpierw, bez tych, które już są na stanowisku. */
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
                T.t("Wybrany zeszyt stanie pod pozostałymi jako rząd stron. Rzędy przesuwasz w bok osobno, w pionie razem; zaznaczenie przeciągnięte lassem do innego rzędu przenosi się do tamtego zeszytu.",
                    "The chosen notebook goes below the others as a row of pages. Rows scroll sideways separately and up and down together; a lasso selection dragged to another row moves into that notebook."),
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
