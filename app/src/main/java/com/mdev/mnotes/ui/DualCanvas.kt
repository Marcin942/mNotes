package com.mdev.mnotes.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mdev.mnotes.core.layout.DualRows
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.NoteView
import com.mdev.mnotes.editor.T

/**
 * Płótno edytora (F4): jeden zeszyt jak dotąd albo dwa rzędy — górny to zeszyt [ed], dolny wybrany w „⋮” →
 * „Drugi zeszyt poniżej…”. Rzędy przesuwają się osobno (strony w poziomie), rozdzielacz zmienia ich wysokość,
 * a powiększenie może być wspólne. Narzędzie i pióro są wspólne (dolny rząd bierze narzędzie z paska).
 * [onTouch] zamyka otwarty panel przy pierwszym dotknięciu płótna.
 */
@Composable
internal fun EditorCanvas(ed: Editor, onTouch: () -> Unit) {
    val m = mint
    val set = ed.settings
    val second = ed.dualId
    val ed2 = second?.let { id -> remember(id) { Editor(ed.ctx, id) } }
    if (ed2 != null) DisposableEffect(ed2) {
        val app = ed.app
        val mine = app.openIds.add(ed2.nbId)
        if (!mine) {
            ed.say(T.t("Ten zeszyt jest otwarty w innym oknie", "This notebook is open in another window"))
            ed.dualId = null
        }
        ed.forceRowLayout(true); ed2.forceRowLayout(true)
        // Rejestr otwartych zeszytów zwalniamy tylko, gdy to my go zajęliśmy (inaczej należy do innego okna).
        onDispose { ed2.close(); if (mine) app.openIds.remove(ed2.nbId); ed.forceRowLayout(false) }
    }
    // Narzędzie z paska działa w obu rzędach.
    if (ed2 != null) LaunchedEffect(ed.tool) { ed2.tool = ed.tool }
    // A2-5: pasek zaznaczenia jest tylko przy górnym zeszycie — przy pierwszym zaznaczeniu w dolnym mówimy, gdzie są akcje.
    if (ed2 != null) {
        var hinted by remember(ed2) { mutableStateOf(false) }
        val hasSel = ed2.sel != null
        LaunchedEffect(hasSel) {
            if (hasSel && !hinted) {
                hinted = true
                ed2.say(T.t("Pasek zaznaczenia działa w górnym zeszycie — tu przytrzymaj zaznaczenie, by otworzyć menu", "The selection bar works in the upper notebook — here, long-press the selection for its menu"))
            }
        }
    }
    val touch = Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); onTouch() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dens = LocalDensity.current
        val total = with(dens) { maxHeight.toPx() }
        val div = with(dens) { DIVIDER.toPx() }
        var frac by remember { mutableFloatStateOf(DualRows.clamp(set.dualFraction)) }
        // W trakcie przeciągania rusza się tylko uchwyt (preview); wysokości płócien zmieniają się po puszczeniu.
        var preview by remember { mutableFloatStateOf(Float.NaN) }
        val hs = DualRows.heights(total, frac, div)
        Column(Modifier.fillMaxSize()) {
            // Górne płótno jest zawsze tym samym widokiem (to samo miejsce w kompozycji), więc otwarcie drugiego zeszytu go nie odtwarza.
            AndroidView(
                factory = { c -> NoteView(c, ed).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) } },
                update = { v ->
                    v.accent = m.accent.toArgb(); v.viewBg = m.canvasBg.toArgb(); v.darkUi = m.dark
                    v.onZoomChange = if (ed2 != null && set.dualSyncZoom) { z -> ed2.view?.followZoom(z) } else null
                },
                modifier = (if (ed2 == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(with(dens) { hs[0].toDp() })).clipToBounds().then(touch),
            )
            if (ed2 != null) {
                DualDivider(
                    ed, ed2, DualRows.handleOffset(total, frac, preview, div),
                    onDrag = { dy -> preview = DualRows.dragged(if (preview.isNaN()) frac else preview, dy, total, div) },
                    onDragEnd = { if (!preview.isNaN()) { frac = preview; set.dualFraction = frac }; preview = Float.NaN },
                    onDragCancel = { preview = Float.NaN },
                )
                Box(Modifier.fillMaxWidth().height(with(dens) { hs[1].toDp() })) {
                    AndroidView(
                        factory = { c -> NoteView(c, ed2).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) } },
                        update = { v ->
                            v.accent = m.accent.toArgb(); v.viewBg = m.canvasBg.toArgb(); v.darkUi = m.dark
                            v.onZoomChange = if (set.dualSyncZoom) { z -> ed.view?.followZoom(z) } else null
                        },
                        onRelease = { it.dispose() },
                        modifier = Modifier.fillMaxSize().clipToBounds().then(touch),
                    )
                    EdToast(ed2)
                    // A2-5: przytrzymanie i uchwyty w dolnym rzędzie otwierają te same menu co w górnym.
                    CtxMenuOverlay(ed2, null, tools = ed)
                    PointMenuOverlay(ed2)
                }
                ed2.textReq?.let { TextDialog(ed2, it) }
                if (ed2.stickerSaveOpen) SaveStickerDialog(ed2) { ed2.stickerSaveOpen = false }
            }
        }
    }
}

private val DIVIDER = 40.dp

/** Rozdzielacz rzędów: uchwyt do przeciągania, nazwa dolnego zeszytu, cofnij/ponów dolnego, wspólne powiększenie, zamknij. */
@Composable
private fun DualDivider(ed: Editor, ed2: Editor, offset: Float, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit) {
    val m = mint
    val set = ed.settings
    // pointerInput(Unit) żyje dłużej niż jedna kompozycja — bez tego trzymałby pierwsze wersje funkcji (stare wysokości).
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    val moving = offset != 0f
    Row(
        Modifier.zIndex(1f).graphicsLayer { translationY = offset; shadowElevation = if (moving) 6.dp.toPx() else 0f }
            .fillMaxWidth().height(DIVIDER).background(m.chrome)
            .pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { end() }, onDragCancel = { cancel() }) { c, dy -> c.consume(); drag(dy) } }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.DragHandle, T.t("Przeciągnij, by zmienić wysokość rzędów", "Drag to resize the rows"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(ed2.repo.meta(ed2.nbId)?.title.orEmpty().ifBlank { T.t("Bez tytułu", "Untitled") }, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconBtn(Icons.AutoMirrored.Rounded.Undo, T.t("Cofnij w dolnym zeszycie", "Undo in the lower notebook"), { ed2.undo() }, enabled = ed2.canUndo, size = 32.dp, iconSize = 18.dp)
        IconBtn(Icons.AutoMirrored.Rounded.Redo, T.t("Ponów w dolnym zeszycie", "Redo in the lower notebook"), { ed2.redo() }, enabled = ed2.canRedo, size = 32.dp, iconSize = 18.dp)
        IconBtn(
            if (set.dualSyncZoom) Icons.Rounded.Link else Icons.Rounded.LinkOff,
            if (set.dualSyncZoom) T.t("Wspólne powiększenie: włączone", "Shared zoom: on") else T.t("Wspólne powiększenie: wyłączone", "Shared zoom: off"),
            { set.dualSyncZoom = !set.dualSyncZoom; if (set.dualSyncZoom) ed.view?.let { v -> ed2.view?.followZoom(v.currentZoom) } }, selected = set.dualSyncZoom, size = 32.dp, iconSize = 18.dp,
        )
        IconBtn(Icons.Rounded.Close, T.t("Zamknij dolny zeszyt", "Close the lower notebook"), { ed.dualId = null }, size = 32.dp, iconSize = 18.dp)
    }
}

/** Wybór zeszytu do dolnego rzędu: ostatnio otwierane najpierw, bez bieżącego i bez kosza. */
@Composable
internal fun DualPickDialog(ed: Editor, onDismiss: () -> Unit) {
    val list = DualRows.candidates(ed.repo.notebooks.filter { !it.isTrashed }, { it.id }, { it.opened }, setOf(ed.nbId))
    // A2-5: pusty stan z krokiem dalej — nowy zeszyt z domyślnymi ustawieniami (jak „Nowa notatka”) od razu w dolnym rzędzie.
    fun createBelow() {
        val s = ed.settings
        val spec = PageSpec.of(PageFormat.fromId(s.newFormat) ?: PageFormat.A4, s.newLandscape, Template.fromCode(s.newTemplate), s.newPaper)
        ed.dualId = ed.repo.createNotebook(T.t("Nowy zeszyt", "New notebook"), spec, CoverPalette.default, CoverPalette.DEFAULT_STYLE, null).id
        onDismiss()
    }
    MDialog(
        T.t("Drugi zeszyt poniżej", "Second notebook below"), onDismiss,
        confirm = if (list.isEmpty()) T.t("Utwórz zeszyt", "Create notebook") else null, onConfirm = ::createBelow,
        dismissText = T.t("Anuluj", "Cancel"),
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(T.t("Wybrany zeszyt otworzy się w dolnym rzędzie; oba rzędy przesuwasz osobno, a pióro i narzędzia są wspólne.", "The chosen notebook opens in the lower row; you scroll each row separately, pens and tools are shared."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            if (list.isEmpty()) Text(
                T.t("Nie ma innych zeszytów. Utwórz nowy — otworzy się poniżej, np. na zadania do wykładu z góry.", "There are no other notebooks. Create one — it opens below, e.g. for exercises next to the lecture above."),
                style = MaterialTheme.typography.bodyMedium,
            )
            for (nb in list) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { ed.dualId = nb.id; onDismiss() }.padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 18.dp, height = 24.dp).clip(RoundedCornerShape(3.dp)).background(androidx.compose.ui.graphics.Color(nb.cover)))
                Text(nb.title.ifBlank { T.t("Bez tytułu", "Untitled") }, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(T.t("${nb.pages.size} str.", "${nb.pages.size} p."), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
