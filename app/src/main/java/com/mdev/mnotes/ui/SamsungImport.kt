package com.mdev.mnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.foundation.layout.safeDrawingPadding
import com.mdev.mnotes.core.samsung.ConvertedNote
import com.mdev.mnotes.core.samsung.ConvertedPage
import com.mdev.mnotes.core.samsung.ImportMode
import com.mdev.mnotes.core.samsung.Level
import com.mdev.mnotes.data.ImportSource
import com.mdev.mnotes.data.SamsungItem
import com.mdev.mnotes.data.SamsungSession
import com.mdev.mnotes.data.SdItemState
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.render.PageRenderer
import com.mdev.mnotes.render.SamsungBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Uruchamia okno importu Samsung Notes dla podanych plików; drugi argument to folder docelowy (null = korzeń biblioteki).
 * Ustawia je korzeń aplikacji, a miejsca, które importują pliki (biblioteka, upuszczenie pliku, udostępnianie), tylko
 * oddają mu to, co rozpoznały jako Samsung Notes.
 */
val LocalSamsungStart = staticCompositionLocalOf<(List<ImportSource>, String?) -> Unit> { { _, _ -> } }

private val WIDE = 880.dp
private val WARN_COLOR = Color(0xFFE0A100)

/** „1 plik / 2 pliki / 5 plików” (i angielskie odpowiedniki). */
private fun plural(n: Int, one: String, few: String, many: String, en1: String, enN: String): String = T.t(
    "$n " + when { n == 1 -> one; n % 10 in 2..4 && n % 100 !in 12..14 -> few; else -> many },
    "$n " + if (n == 1) en1 else enN,
)

private fun pagesText(n: Int) = plural(n, "strona", "strony", "stron", "page", "pages")

/**
 * Okno importu: po lewej (na szerokim ekranie) lista plików, pośrodku podgląd stron po konwersji, po prawej wybór sposobu
 * przeniesienia i raport odstępstw. Nic nie trafia do biblioteki, dopóki nie zostanie wciśnięte „Importuj”.
 * [onFinished] dostaje liczbę zapisanych i nieudanych plików; okno zamyka wołający.
 */
@Composable
fun SamsungImportScreen(session: SamsungSession, onClose: () -> Unit, onFinished: (saved: Int, failed: Int) -> Unit) {
    val m = mint
    val ctx = LocalContext.current
    val multi = session.items.size > 1
    val item = session.current
    val opts = item?.let { session.optionsOf(it, m.dark) }
    var confirmExit by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }

    LaunchedEffect(item, opts) {
        if (item != null && opts != null) session.prepare(item, opts)
    }
    // Następny plik wczytuje się z wyprzedzeniem, żeby przejście dalej nie czekało.
    LaunchedEffect(session.index, item?.state, opts) {
        val next = session.items.getOrNull(session.index + 1) ?: return@LaunchedEffect
        if (item?.state == SdItemState.READY) session.prepare(next, session.optionsOf(next, m.dark))
    }

    val requestClose = { if (multi && session.selectedCount > 0) confirmExit = true else onClose() }
    BackHandler { if (!session.saving) requestClose() }

    val doSave = {
        session.save(m.dark) { saved, failed ->
            val msg = when {
                failed == 0 -> T.t("Zaimportowano z Samsung Notes: $saved", "Imported from Samsung Notes: $saved")
                saved == 0 -> T.t("Nie udało się zaimportować plików ($failed)", "Could not import the files ($failed)")
                else -> T.t("Zaimportowano $saved, nie udało się $failed", "Imported $saved, $failed failed")
            }
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
            session.items.indexOfFirst { it.state == SdItemState.FAILED }.takeIf { failed > 0 && it >= 0 }?.let(session::select)
            onFinished(saved, failed)
        }
    }

    // Pusty detektor dotknięć: bez niego zdarzenia przechodziłyby do ekranu pod oknem.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { } }.safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(session, onBack = requestClose, onImport = doSave)
            HorizontalDivider(color = m.line)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= WIDE
                when {
                    item == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(T.t("Brak plików do importu", "No files to import"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    wide -> Row(Modifier.fillMaxSize()) {
                        if (multi) {
                            Column(Modifier.width(260.dp).fillMaxHeight().background(m.sidebar)) {
                                DocListHeader(session)
                                DocRows(session, Modifier.weight(1f).fillMaxWidth())
                            }
                            VerticalDivider(color = m.line)
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (multi) NavStrip(session, onList = null)
                            key(item) { PreviewPane(item, Modifier.weight(1f).fillMaxWidth()) }
                        }
                        VerticalDivider(color = m.line)
                        SettingsPane(session, item, Modifier.width(340.dp).fillMaxHeight())
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        if (multi) NavStrip(session, onList = { listOpen = true })
                        key(item) { PreviewPane(item, Modifier.weight(1.15f).fillMaxWidth()) }
                        HorizontalDivider(color = m.line)
                        SettingsPane(session, item, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
        if (session.saving) SavingOverlay(session)
    }

    if (listOpen) MDialog(T.t("Pliki", "Files"), { listOpen = false }, dismissText = T.t("Zamknij", "Close")) {
        DocListHeader(session)
        DocRows(session, Modifier.heightIn(max = 380.dp).fillMaxWidth()) { listOpen = false }
    }
    if (confirmExit) MDialog(
        T.t("Zamknąć import?", "Close the import?"), { confirmExit = false },
        confirm = T.t("Zamknij", "Close"), onConfirm = { confirmExit = false; onClose() },
        dismissText = T.t("Wróć", "Back"),
    ) {
        Text(
            T.t("Pliki, które nie zostały jeszcze zaimportowane, nie trafią do biblioteki.", "Files that have not been imported yet will not be added to the library."),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TopBar(session: SamsungSession, onBack: () -> Unit, onImport: () -> Unit) {
    val m = mint
    val n = session.selectedCount
    Row(Modifier.fillMaxWidth().background(m.header).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, T.t("Zamknij", "Close"), onBack, enabled = !session.saving)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(T.t("Import z Samsung Notes", "Import from Samsung Notes"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val total = session.items.size
            Text(
                if (total > 1) T.t("${plural(total, "plik", "pliki", "plików", "file", "files")}, do zaimportowania: $n", "$total files, to import: $n")
                else session.current?.source?.name.orEmpty(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onImport, enabled = n > 0 && !session.saving,
            colors = ButtonDefaults.buttonColors(containerColor = m.accent, contentColor = m.onAccent),
            shape = RoundedCornerShape(10.dp),
        ) { Text(if (session.items.size > 1) T.t("Importuj ($n)", "Import ($n)") else T.t("Importuj", "Import")) }
    }
}

/** Poprzedni / następny plik i licznik; [onList] otwiera listę plików tam, gdzie nie ma jej na stałe. */
@Composable
private fun NavStrip(session: SamsungSession, onList: (() -> Unit)?) {
    val m = mint
    Row(Modifier.fillMaxWidth().background(m.header).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.Rounded.ChevronLeft, T.t("Poprzedni plik", "Previous file"), { session.step(-1) }, enabled = session.index > 0)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(session.current?.title.orEmpty(), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${session.index + 1} / ${session.items.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onList != null) IconBtn(Icons.AutoMirrored.Rounded.ViewList, T.t("Lista plików", "File list"), onList)
        IconBtn(Icons.Rounded.ChevronRight, T.t("Następny plik", "Next file"), { session.step(1) }, enabled = session.index < session.items.size - 1)
    }
}

@Composable
private fun DocListHeader(session: SamsungSession) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(T.t("PLIKI", "FILES"), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Chip(T.t("Wszystkie", "All"), false, { session.items.forEach { if (it.state != SdItemState.FAILED && it.state != SdItemState.SAVED) it.include = true } })
        Chip(T.t("Żadne", "None"), false, { session.items.forEach { it.include = false } })
    }
}

@Composable
private fun DocRows(session: SamsungSession, modifier: Modifier, onPick: () -> Unit = {}) {
    val m = mint
    LazyColumn(modifier) {
        itemsIndexed(session.items, key = { _, it -> it.source.uri.toString() }) { i, it ->
            val sel = i == session.index
            val usable = it.state != SdItemState.FAILED && it.state != SdItemState.SAVED
            Row(
                Modifier.fillMaxWidth().background(if (sel) m.accentSoft else Color.Transparent)
                    .clickable { session.select(i); onPick() }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    it.include, { v -> it.include = v }, enabled = usable && !session.saving,
                    colors = CheckboxDefaults.colors(checkedColor = m.accent, checkmarkColor = m.onAccent),
                )
                Column(Modifier.weight(1f).padding(end = 4.dp)) {
                    Text(it.title.ifBlank { it.source.name }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val failed = it.state == SdItemState.FAILED
                    Text(
                        docStatus(it), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (it.state) {
                    SdItemState.LOADING -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = m.accent)
                    SdItemState.SAVED -> Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = m.accent)
                    SdItemState.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    else -> if (it.warnings > 0) Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = WARN_COLOR)
                }
            }
        }
    }
}

private fun docStatus(it: SamsungItem): String = when (it.state) {
    SdItemState.LOADING -> T.t("Wczytuję…", "Loading…")
    SdItemState.SAVED -> T.t("Zaimportowano", "Imported")
    SdItemState.FAILED -> it.error ?: T.t("Nie udało się wczytać", "Could not be read")
    SdItemState.READY -> pagesText(it.pages) + if (it.warnings > 0) T.t(" · uwagi: ${it.warnings}", " · notes: ${it.warnings}") else ""
    SdItemState.IDLE -> if (it.pages > 0) pagesText(it.pages) else it.source.name
}

@Composable
private fun PreviewPane(item: SamsungItem, modifier: Modifier) {
    val m = mint
    Box(modifier.background(m.canvasBg)) {
        val note = item.note
        when {
            note != null -> PagePager(note, Modifier.fillMaxSize())
            item.state == SdItemState.FAILED -> Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
                Text(T.t("Nie udało się wczytać pliku", "The file could not be read"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(item.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = m.accent)
        }
        if (item.busy && note != null) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(12.dp).size(20.dp), strokeWidth = 2.dp, color = m.accent)
    }
}

@Composable
private fun PagePager(note: ConvertedNote, modifier: Modifier) {
    val count = note.pages.size
    if (count == 0) {
        Box(modifier, Alignment.Center) { Text(T.t("Ta notatka nie ma stron", "This note has no pages"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    val pager = rememberPagerState { count }
    val scope = rememberCoroutineScope()
    Column(modifier) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), pageSpacing = 16.dp) { i ->
            PagePreview(note.pages[i], Modifier.fillMaxSize())
        }
        if (count > 1) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.ChevronLeft, T.t("Poprzednia strona", "Previous page"), { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0)
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                if (count > 3) MSlider(pager.currentPage.toFloat(), 0f, (count - 1).toFloat()) { scope.launch { pager.scrollToPage(it.roundToInt()) } }
            }
            Text(T.t("Strona ${pager.currentPage + 1} z $count", "Page ${pager.currentPage + 1} of $count"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconBtn(Icons.Rounded.ChevronRight, T.t("Następna strona", "Next page"), { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < count - 1)
        }
    }
}

/** Jedna strona po konwersji, narysowana tym samym kodem co w edytorze i w pliku PDF z tłem. */
@Composable
private fun PagePreview(page: ConvertedPage, modifier: Modifier) {
    val m = mint
    val bmp by produceState<ImageBitmap?>(null, page) {
        value = withContext(Dispatchers.Default) {
            runCatching { SamsungBackground.preview(page, 1100, PageRenderer(null)).asImageBitmap() }.getOrNull()
        }
    }
    BoxWithConstraints(modifier, Alignment.Center) {
        val ratio = page.spec.w / page.spec.h
        val w = min(maxWidth, maxHeight * ratio)
        Box(Modifier.width(w).aspectRatio(ratio).shadow(4.dp).border(1.dp, m.line)) {
            val b = bmp
            if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            else CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), strokeWidth = 2.dp, color = m.accent)
        }
    }
}

@Composable
private fun SettingsPane(session: SamsungSession, item: SamsungItem, modifier: Modifier) {
    val m = mint
    val multi = session.items.size > 1
    val mode = session.modeOf(item)
    val dark = session.darkOf(item, m.dark)
    val canInclude = item.state != SdItemState.FAILED && item.state != SdItemState.SAVED
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
        SectionLabel(T.t("Nazwa zeszytu", "Notebook name"))
        MField(item.title, { item.title = it }, Modifier.fillMaxWidth(), hint = T.t("Nazwa zeszytu", "Notebook name"))
        item.note?.let {
            Text(
                T.t("${pagesText(it.pages.size)}, kresek: ${it.strokeCount}", "${it.pages.size} pages, ${it.strokeCount} strokes"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (multi && canInclude) ToggleRow(T.t("Importuj ten plik", "Import this file"), item.include) { item.include = it }

        SectionLabel(T.t("Jak przenieść", "How to import"))
        ModeCard(
            mode == ImportMode.ORIGINAL, T.t("Wygląd oryginału", "Look of the original"),
            T.t(
                "Strona ma ten sam rozmiar, kolor papieru i linie co w Notatkach Samsung. Tło leży pod stroną jak zaimportowany PDF; pisma i kresek nadal można normalnie edytować.",
                "The page has the same size, paper colour and lines as in Samsung Notes. The background sits under the page like an imported PDF; the writing stays fully editable.",
            ),
        ) { session.setMode(item, ImportMode.ORIGINAL) }
        ModeCard(
            mode == ImportMode.STANDARD, T.t("Standard mNotes", "mNotes standard"),
            T.t(
                "Najbliższy odpowiednik w mNotes: zwykły wzór (linie, kratka, kropki), który można potem zmienić dla całego zeszytu.",
                "Closest mNotes equivalent: a regular pattern (lines, grid, dots) that you can later restyle for the whole notebook.",
            ),
        ) { session.setMode(item, ImportMode.STANDARD) }

        SectionLabel(T.t("Wygląd", "Appearance"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(T.t("Jasny", "Light"), !dark, { session.setDark(item, false) }, icon = Icons.Rounded.LightMode)
            Chip(T.t("Ciemny", "Dark"), dark, { session.setDark(item, true) }, icon = Icons.Rounded.DarkMode)
        }
        Text(
            T.t(
                "Domyślnie jak motyw aplikacji. Notatki Samsung są zapisane jasno; „ciemny” odwraca jasność papieru i atramentu tak jak tryb ciemny w Notatkach.",
                "Follows the app theme by default. Samsung notes are stored light; “dark” inverts paper and ink lightness like dark mode in Samsung Notes.",
            ),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
        )

        if (multi) {
            SectionLabel(T.t("Kilka plików", "Several files"))
            ToggleRow(T.t("Te same ustawienia dla wszystkich plików", "Same settings for all files"), session.sameForAll) { session.sameForAll = it }
            if (!session.sameForAll) {
                Text(
                    T.t("Wybór wyżej dotyczy tylko tego pliku. Pozostałe mają własny albo ostatnio użyty.", "The choices above apply to this file only. The others keep their own or the last used."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp),
                )
                Chip(T.t("Zastosuj do wszystkich", "Apply to all"), false, { session.applyCurrentToAll(m.dark) })
            }
        }

        SectionLabel(T.t("Raport przeniesienia", "Import report"))
        Report(item)
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun ModeCard(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    val m = mint
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape)
            .background(if (selected) m.accentSoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.5.dp, if (selected) m.accent else Color.Transparent, shape)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, null, colors = RadioButtonDefaults.colors(selectedColor = m.accent))
            Text(title, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleSmall)
        }
        Text(desc, Modifier.padding(start = 34.dp, top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Report(item: SamsungItem) {
    val m = mint
    val note = item.note
    if (note == null) {
        Text(T.t("Raport pojawi się po wczytaniu pliku.", "The report appears once the file is loaded."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (note.report.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = m.accent)
            Text(T.t("Bez odstępstw: wszystko zostało przeniesione.", "No deviations: everything was carried over."), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    note.report.forEach { r ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Icon(
                if (r.level == Level.WARN) Icons.Rounded.Warning else Icons.Rounded.Info, null, Modifier.padding(top = 2.dp).size(18.dp),
                tint = if (r.level == Level.WARN) WARN_COLOR else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text(r.text(T.pl), style = MaterialTheme.typography.bodyMedium)
                if (r.pages.isNotEmpty()) {
                    val shown = r.pages.take(8).joinToString(", ") + if (r.pages.size > 8) "…" else ""
                    Text(T.t("str. $shown", "p. $shown"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SavingOverlay(session: SamsungSession) {
    val m = mint
    val done = session.savedCount + session.failedCount
    val total = (done + session.selectedCount).coerceAtLeast(1)
    val now = session.items.firstOrNull { it.include && it.state != SdItemState.SAVED && it.state != SdItemState.FAILED }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.padding(24.dp).widthIn(max = 340.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(T.t("Importuję ${(done + 1).coerceAtMost(total)} z $total", "Importing ${(done + 1).coerceAtMost(total)} of $total"), style = MaterialTheme.typography.titleMedium)
                Text(now?.title.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                LinearProgressIndicator(progress = { done.toFloat() / total }, Modifier.fillMaxWidth(), color = m.accent)
                Chip(T.t("Przerwij", "Stop"), false, { session.cancel() }, modifier = Modifier.padding(top = 14.dp))
            }
        }
    }
}
