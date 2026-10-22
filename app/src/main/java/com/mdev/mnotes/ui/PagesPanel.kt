package com.mdev.mnotes.ui

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mdev.mnotes.app.app
import com.mdev.mnotes.core.model.PageOrder
import com.mdev.mnotes.data.Bookmark
import com.mdev.mnotes.data.PageState
import com.mdev.mnotes.editor.Clip
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

// ─────────────────────────────── Panel stron i zakładek ───────────────────────────────

/**
 * Przeciąganie miniatury: na czas gestu lista pokazuje kolejność [order], a podniesiona miniatura jedzie za palcem.
 * Dokument zmienia się dopiero po upuszczeniu, jednym ruchem (jeden wpis cofania).
 */
private class PageDrag(private val ls: LazyListState, private val scope: CoroutineScope) {
    var id by mutableStateOf<String?>(null)
        private set
    val order = mutableStateListOf<String>()
    /** Przesunięcie palca od początku gestu; miniatura stoi w [start] + [delta] (układ listy). */
    private var delta by mutableFloatStateOf(0f)
    private var start = 0f
    var pointerY = 0f
        private set
    var settling = false
        private set

    val active get() = id != null

    private fun slot() = ls.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }

    fun begin(key: String, ids: List<String>, slotOffset: Int, y: Float) {
        order.clear(); order.addAll(ids)
        start = slotOffset.toFloat(); delta = 0f; pointerY = y; settling = false
        id = key
    }

    /** Przesunięcie rysowanej miniatury względem jej miejsca w liście. */
    fun offset(): Float = slot()?.let { start + delta - it.offset } ?: 0f

    fun move(dy: Float, y: Float): Boolean { delta = dy; pointerY = y; return swap() }

    /** Zamienia miejscami z sąsiadem, gdy środek miniatury minął jego środek; true = była zamiana. */
    fun swap(): Boolean {
        val key = id ?: return false
        if (settling) return false
        val items = ls.layoutInfo.visibleItemsInfo
        val k = items.indexOfFirst { it.key == key }
        if (k < 0) return false
        val cur = items[k]
        val idx = order.indexOf(key)
        if (cur.index != idx) return false // układ jeszcze nie odświeżony po poprzedniej zamianie
        val prev = items.getOrNull(k - 1)?.takeIf { it.index == idx - 1 }
        val next = items.getOrNull(k + 1)?.takeIf { it.index == idx + 1 }
        val to = PageOrder.step(idx, start + delta + cur.size / 2f, prev?.let { it.offset + it.size / 2f }, next?.let { it.offset + it.size / 2f })
        if (to == idx) return false
        // Lista trzyma pozycję przy kluczu pierwszej widocznej pozycji; bez tego zamiana z nią przewija listę.
        val fi = ls.firstVisibleItemIndex; val fo = ls.firstVisibleItemScrollOffset
        PageOrder.move(order, idx, to)
        if (idx == fi || to == fi) scope.launch { ls.scrollToItem(fi, fo) }
        return true
    }

    /** Dosuwa miniaturę na jej nowe miejsce, potem [commit] z indeksem docelowym. */
    fun drop(commit: (key: String, to: Int) -> Unit) {
        val key = id ?: return
        settling = true
        scope.launch {
            val target = slot()?.let { it.offset - start }
            if (target != null) animate(delta, target, animationSpec = tween(140)) { v, _ -> delta = v }
            val to = order.indexOf(key)
            if (id == key) { commit(key, to); cancel() }
        }
    }

    fun cancel() { id = null; settling = false; delta = 0f; order.clear() }
}

@Composable
fun PagesPanel(ed: Editor, close: () -> Unit, @Suppress("UNUSED_PARAMETER") pickImage: () -> Unit, docked: Boolean = false) {
    val m = mint
    var tab by remember { mutableStateOf(0) }
    val nb = ed.repo.meta(ed.nbId)
    ed.structTick; ed.undoTick
    val pages = ed.doc.pages.toList() // kopia: żywa lista kurczy się przy usuwaniu strony, a LazyColumn mierzy jeszcze po starym rozmiarze
    val cur = ed.pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val ro = ed.readOnly
    val ls = rememberLazyListState(initialFirstVisibleItemIndex = cur)
    val scope = rememberCoroutineScope()
    val drag = remember(ls) { PageDrag(ls, scope) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var moveTo by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val haptics = LocalHaptics.current
    fun buzz(c: Int) { if (haptics) view.performHapticFeedback(c) }

    // Zmiana struktury z zewnątrz (cofnięcie, skrót) w trakcie przeciągania: kolejność z gestu jest już nieaktualna.
    LaunchedEffect(ed.structTick) { if (drag.active && !drag.settling) drag.cancel() }
    LaunchedEffect(ro) { if (ro) drag.cancel() }
    // Bieżąca strona zawsze widoczna w panelu, ale bez szarpania listy, gdy już na niej jest.
    LaunchedEffect(cur, tab) {
        if (tab != 0 || drag.active) return@LaunchedEffect
        val info = ls.layoutInfo
        val it = info.visibleItemsInfo.firstOrNull { it.index == cur }
        val whole = it != null && it.offset >= 0 && it.offset + it.size <= info.viewportEndOffset - info.afterContentPadding
        if (!whole) runCatching { ls.animateScrollToItem(cur) }
    }
    // Przewijanie listy, gdy podniesiona miniatura dojeżdża do krawędzi.
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    val speed = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(drag.id) {
        while (isActive && drag.active) {
            withFrameNanos { }
            if (drag.settling) continue
            val h = ls.layoutInfo.viewportSize.height.toFloat()
            val y = drag.pointerY
            val f = when {
                y < edge -> -((edge - y) / edge)
                y > h - edge -> (y - (h - edge)) / edge
                else -> 0f
            }.coerceIn(-1.5f, 1.5f)
            if (f != 0f && ls.scrollBy(f * speed) != 0f && drag.swap()) buzz(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Column(
        (if (docked) Modifier.width(236.dp).fillMaxHeight().padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
        else Modifier.width(236.dp).fillMaxHeight().padding(top = 0.dp, end = 8.dp, bottom = 8.dp).shadow(10.dp, RoundedCornerShape(14.dp)))
            .clip(RoundedCornerShape(14.dp)).background(m.chrome).border(1.dp, m.line, RoundedCornerShape(14.dp)),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(T.t("Strony", "Pages"), tab == 0, { tab = 0 })
            Chip(T.t("Zakładki", "Bookmarks"), tab == 1, { tab = 1 })
            Spacer(Modifier.weight(1f))
            IconBtn(Icons.Rounded.Close, if (docked) T.t("Odepnij panel", "Undock panel") else T.t("Zamknij", "Close"), close, size = 32.dp, iconSize = 18.dp)
        }
        HorizontalDivider(color = m.line)
        if (tab == 0) {
            val byId = pages.associateBy { it.id }
            val ids = if (drag.active) drag.order.toList() else pages.map { it.id }
            val curId = pages.getOrNull(cur)?.id
            val canDrag = !ro && pages.size > 1
            LazyColumn(
                Modifier.weight(1f).pointerInput(canDrag) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (drag.active) return@awaitEachGesture
                        val info = ls.layoutInfo
                        val y0 = down.position.y + info.viewportStartOffset
                        val hit = info.visibleItemsInfo.firstOrNull { y0 >= it.offset && y0 < it.offset + it.size } ?: return@awaitEachGesture
                        val key = hit.key as? String ?: return@awaitEachGesture
                        if (currentEvent.buttons.isSecondaryPressed) {
                            down.consume(); menuFor = key
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        // Krótkie dotknięcie: przejście do strony. Przewinięcie listy albo przycisk „⋮” zużywa gest.
                        var aborted = false
                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation().also { if (it == null) aborted = true }
                        }
                        if (aborted) return@awaitEachGesture
                        if (up != null) {
                            up.consume()
                            ed.doc.indexOf(key).takeIf { it >= 0 }?.let { ed.view?.goToPage(it) }
                            return@awaitEachGesture
                        }
                        // Przytrzymanie: miniatura się podnosi. Ruch przenosi stronę, puszczenie w miejscu otwiera menu.
                        buzz(HapticFeedbackConstants.LONG_PRESS)
                        // Kolejność z dokumentu, nie z kompozycji: blok gestu żyje dłużej niż jedna kompozycja.
                        if (canDrag) drag.begin(key, ed.doc.pages.map { it.id }, hit.offset, down.position.y)
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) { c.consume(); break }
                            val d = c.position - down.position
                            if (!moved && abs(d.x) + abs(d.y) > viewConfiguration.touchSlop) moved = true
                            if (moved && canDrag && drag.move(d.y, c.position.y)) buzz(HapticFeedbackConstants.CLOCK_TICK)
                            c.consume()
                        }
                        if (moved && canDrag && drag.active) drag.drop { k, to ->
                            val from = ed.doc.indexOf(k)
                            if (from >= 0 && to >= 0 && from != to) ed.movePage(from, to)
                        } else { drag.cancel(); if (!moved) menuFor = key }
                    }
                },
                state = ls, contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(ids, key = { _, id -> id }) { i, id ->
                    val p = byId[id] ?: return@itemsIndexed
                    val lifted = drag.id == id
                    Box(
                        if (lifted) Modifier.zIndex(1f).graphicsLayer { translationY = drag.offset() }
                        else Modifier.animateItem(),
                    ) {
                        PageThumbItem(
                            ed, p, i, ids.size, current = id == curId, lifted = lifted, menu = menuFor == id,
                            openMenu = { menuFor = id }, closeMenu = { menuFor = null }, moveTo = { moveTo = id }, rename = { renaming = id },
                        )
                    }
                }
            }
            HorizontalDivider(color = m.line)
            PageActions(ed, cur, pages.size, ro)
        } else {
            BookmarksList(ed, nb?.bookmarks.orEmpty(), pages, cur) { renaming = it }
        }
    }
    moveTo?.let { id -> MoveToDialog(ed, id) { moveTo = null } }
    renaming?.let { id ->
        val b = nb?.bookmarks?.firstOrNull { it.pageId == id }
        if (b == null) renaming = null else RenameBookmarkDialog(ed, id, b.title, ed.doc.indexOf(id)) { renaming = null }
    }
}

/** Działania na bieżącej stronie, zawsze pod ręką pod listą miniatur. */
@Composable
private fun PageActions(ed: Editor, cur: Int, total: Int, ro: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(T.t("Strona ${cur + 1} z $total", "Page ${cur + 1} of $total"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconBtn(Icons.Rounded.FileCopy, T.t("Duplikuj stronę", "Duplicate page"), { ed.duplicatePage(cur) }, enabled = !ro, size = 32.dp, iconSize = 18.dp)
            IconBtn(Icons.Rounded.Delete, T.t("Usuń stronę", "Delete page"), { deleteWithNote(ed, cur) }, enabled = !ro && total > 1, size = 32.dp, iconSize = 18.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelBtn(Icons.Rounded.ArrowUpward, T.t("Wcześniej", "Earlier"), !ro && cur > 0, Modifier.weight(1f)) { ed.movePage(cur, cur - 1) }
            PanelBtn(Icons.Rounded.ArrowDownward, T.t("Dalej", "Later"), !ro && cur < total - 1, Modifier.weight(1f)) { ed.movePage(cur, cur + 1) }
        }
        PanelBtn(Icons.Rounded.Add, T.t("Dodaj stronę", "Add page"), !ro, Modifier.fillMaxWidth(), accent = true) { ed.addPage(cur) }
    }
}

private fun deleteWithNote(ed: Editor, i: Int) {
    if (ed.doc.pages.size <= 1) return
    ed.deletePage(i)
    ed.say(T.t("Usunięto stronę ${i + 1} · Cofnij przywróci ją", "Page ${i + 1} deleted · Undo brings it back"))
}

@Composable
private fun PanelBtn(icon: ImageVector, text: String, enabled: Boolean, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    val m = mint
    val tick = rememberTick()
    val bg = if (accent) m.accentSoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val fg = (if (accent) m.accentText else MaterialTheme.colorScheme.onSurface).copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(8.dp)).background(bg)
            .clickable(enabled = enabled, role = Role.Button) { tick(); onClick() }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@Composable
private fun PageThumbItem(
    ed: Editor, page: PageState, index: Int, total: Int, current: Boolean, lifted: Boolean, menu: Boolean,
    openMenu: () -> Unit, closeMenu: () -> Unit, moveTo: () -> Unit, rename: () -> Unit,
) {
    val m = mint
    val thumbs = LocalContext.current.app.thumbs
    val spec = page.spec
    val wPx = 360
    val key = "p:${page.id}:${page.version}:$wPx"
    var bmp by remember(key) { mutableStateOf<Bitmap?>(thumbs.cached(key)) }
    if (bmp == null) {
        // Zawartość czytamy na wątku UI (bezpieczne dla modelu), rysujemy w tle.
        val content = remember(key) { ed.doc.content(page) }
        LaunchedEffect(key) { thumbs.async(key, { thumbs.render(ed.nbId, spec, content, wPx) }) { bmp = it } }
    }
    val scale by animateFloatAsState(if (lifted) 1.05f else 1f, tween(120), label = "lift")
    val ro = ed.readOnly
    val marked = ed.isBookmarked(ed.doc.indexOf(page.id))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(if (lifted) 14.dp else 0.dp, RoundedCornerShape(6.dp))
                .aspectRatio(spec.w / spec.h).clip(RoundedCornerShape(6.dp)).background(Color(spec.paper))
                .border(if (current || lifted) 2.5.dp else 1.dp, if (current || lifted) m.accent else m.line, RoundedCornerShape(6.dp)),
        ) {
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
            if (marked) Icon(Icons.Rounded.Bookmark, null, Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp), tint = m.accent)
            DropdownMenu(menu, closeMenu) {
                @Composable
                fun It(t: String, icon: ImageVector, en: Boolean = true, danger: Boolean = false, f: () -> Unit) = DropdownMenuItem(
                    text = { Text(t, color = if (danger && en) MaterialTheme.colorScheme.error else Color.Unspecified) },
                    leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = if (danger && en) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                    enabled = en, onClick = { closeMenu(); f() },
                )
                val i = ed.doc.indexOf(page.id)
                if (i >= 0) {
                    Text(T.t("Strona ${i + 1}", "Page ${i + 1}"), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    It(T.t("Wstaw pustą stronę przed", "Insert blank page before"), Icons.Rounded.Add, !ro) { ed.addPage(i - 1, page.spec) }
                    It(T.t("Wstaw pustą stronę po", "Insert blank page after"), Icons.Rounded.Add, !ro) { ed.addPage(i, page.spec) }
                    It(T.t("Duplikuj", "Duplicate"), Icons.Rounded.FileCopy, !ro) { ed.duplicatePage(i) }
                    HorizontalDivider(color = m.line)
                    It(T.t("Kopiuj stronę", "Copy page"), Icons.Rounded.ContentCopy) { ed.copyPage(i) }
                    It(T.t("Wytnij stronę", "Cut page"), Icons.Rounded.ContentCut, !ro && total > 1) { ed.cutPage(i) }
                    It(T.t("Wklej stronę po tej", "Paste page after"), Icons.Rounded.ContentPaste, !ro && Clip.page != null) { ed.pastePage(i) }
                    It(if (marked) T.t("Usuń zakładkę", "Remove bookmark") else T.t("Dodaj zakładkę", "Add bookmark"), if (marked) Icons.Rounded.BookmarkBorder else Icons.Rounded.Bookmark) { ed.toggleBookmark(i) }
                    if (marked) It(T.t("Zmień nazwę zakładki", "Rename bookmark"), Icons.Rounded.Edit) { rename() }
                    HorizontalDivider(color = m.line)
                    It(if (i > 0) T.t("Przesuń wcześniej (na stronę $i)", "Move earlier (to page $i)") else T.t("Przesuń wcześniej", "Move earlier"), Icons.Rounded.ArrowUpward, !ro && i > 0) { ed.movePage(i, i - 1) }
                    It(if (i < total - 1) T.t("Przesuń dalej (na stronę ${i + 2})", "Move later (to page ${i + 2})") else T.t("Przesuń dalej", "Move later"), Icons.Rounded.ArrowDownward, !ro && i < total - 1) { ed.movePage(i, i + 1) }
                    It(T.t("Przenieś na początek", "Move to start"), Icons.Rounded.VerticalAlignTop, !ro && i > 0) { ed.movePage(i, 0) }
                    It(T.t("Przenieś na koniec", "Move to end"), Icons.Rounded.VerticalAlignBottom, !ro && i < total - 1) { ed.movePage(i, total - 1) }
                    It(T.t("Przenieś na stronę…", "Move to page…"), Icons.Rounded.SwapVert, !ro && total > 1) { moveTo() }
                    HorizontalDivider(color = m.line)
                    It(T.t("Wyczyść stronę", "Clear page"), Icons.Rounded.CleaningServices, !ro && !ed.doc.content(page).isEmpty) { ed.clearPage(i) }
                    It(T.t("Usuń stronę", "Delete page"), Icons.Rounded.Delete, !ro && total > 1, danger = true) { deleteWithNote(ed, i) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                "${index + 1}",
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(if (current) m.accent else Color.Transparent).padding(horizontal = 9.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelMedium, color = if (current) m.onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconBtn(Icons.Rounded.MoreVert, T.t("Działania na stronie", "Page actions"), openMenu, Modifier.align(Alignment.CenterEnd), size = 28.dp, iconSize = 18.dp)
        }
    }
}

@Composable
private fun BookmarksList(ed: Editor, marks: List<Bookmark>, pages: List<PageState>, cur: Int, rename: (pageId: String) -> Unit) {
    val m = mint
    val rows = marks.mapNotNull { b -> pages.indexOfFirst { it.id == b.pageId }.takeIf { it >= 0 }?.let { it to b } }.sortedBy { it.first }
    if (rows.isEmpty()) {
        Text(T.t("Brak zakładek. Użyj ikony zakładki w nagłówku (Ctrl+B) albo menu „⋮” przy miniaturze strony.", "No bookmarks. Use the bookmark icon (Ctrl+B) or the “⋮” menu of a page thumbnail."), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(Modifier.fillMaxHeight()) {
        items(rows, key = { it.second.pageId }) { (idx, b) ->
            val here = idx == cur
            Row(
                Modifier.fillMaxWidth().background(if (here) m.accentSoft else Color.Transparent).clickable { ed.view?.goToPage(idx) }.padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp), tint = m.accent)
                Text(PageOrder.bookmarkTitle(b.title, idx, T.t("Strona", "Page")), Modifier.weight(1f).padding(vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = if (here) m.accentText else MaterialTheme.colorScheme.onSurface)
                Text("${idx + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconBtn(Icons.Rounded.Edit, T.t("Zmień nazwę zakładki", "Rename bookmark"), { rename(b.pageId) }, size = 32.dp, iconSize = 16.dp)
                IconBtn(Icons.Rounded.Close, T.t("Usuń zakładkę", "Remove bookmark"), { ed.toggleBookmark(idx) }, size = 32.dp, iconSize = 16.dp)
            }
        }
    }
}

@Composable
private fun MoveToDialog(ed: Editor, pageId: String, onDismiss: () -> Unit) {
    val from = ed.doc.indexOf(pageId)
    if (from < 0) { onDismiss(); return }
    val total = ed.pageCount
    var t by remember { mutableStateOf("") }
    val n = t.trim().toIntOrNull()
    val ok = n != null && n in 1..total && n - 1 != from
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun go() { if (ok) { ed.movePage(from, n!! - 1); onDismiss() } }
    MDialog(T.t("Przenieś stronę ${from + 1}", "Move page ${from + 1}"), onDismiss, T.t("Przenieś", "Move"), ::go, ok) {
        Text(T.t("Na którym miejscu ma stanąć ta strona?", "Which position should this page take?"), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium)
        MField(t, { t = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().focusRequester(focus), hint = "1 – $total", number = true, onDone = ::go)
    }
}

/** Nazwa zakładki oznacza temat albo grupę zadań i trafia do nazw plików przy eksporcie od zakładki do zakładki. */
@Composable
private fun RenameBookmarkDialog(ed: Editor, pageId: String, current: String, index: Int, onDismiss: () -> Unit) {
    // Samoczynne „Strona N” podpowiadamy z bieżącym numerem, żeby nie zapisać nieaktualnego.
    var text by remember { mutableStateOf(if (index >= 0) PageOrder.bookmarkTitle(current, index, T.t("Strona", "Page")) else current) }
    MDialog(
        T.t("Nazwa zakładki", "Bookmark name"), onDismiss, confirm = T.t("Zapisz", "Save"),
        onConfirm = { ed.renameBookmark(pageId, text); onDismiss() },
    ) {
        MField(text, { text = it }, hint = T.t("np. Zestaw 3 — całki", "e.g. Set 3 — integrals"))
    }
}
