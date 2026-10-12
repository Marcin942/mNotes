package com.mdev.mnotes.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.app.app
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.editor.T
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

private class Cat(val icon: ImageVector, val title: String)

/** Indeks kategorii „Skróty klawiszowe” — otwierany bezpośrednio z palety poleceń. */
const val SHORTCUTS_CAT = 5

@Composable
fun SettingsScreen(onBack: () -> Unit, startCat: Int = 0) {
    val ctx = LocalContext.current
    val set = ctx.app.settings
    val m = mint
    var cat by remember { mutableIntStateOf(startCat) }
    val cats = listOf(
        Cat(Icons.Rounded.Palette, T.t("Wygląd", "Appearance")),
        Cat(Icons.Rounded.Edit, T.t("Edytor i pióro", "Editor & pen")),
        Cat(Icons.Rounded.TouchApp, T.t("Wejście", "Input")),
        Cat(Icons.Rounded.NoteAdd, T.t("Nowe notatki", "New notes")),
        Cat(Icons.Rounded.Lock, T.t("Prywatność i kopie", "Privacy & backup")),
        Cat(Icons.Rounded.Keyboard, T.t("Skróty klawiszowe", "Keyboard shortcuts")),
        Cat(Icons.Rounded.Info, T.t("O aplikacji", "About")),
    )
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().background(m.header).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, T.t("Wstecz", "Back"), onBack, shortcut = "Esc")
            Text(T.t("Ustawienia", "Settings"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        }
        HorizontalDivider(color = m.line)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 760.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    Column(Modifier.width(240.dp).fillMaxHeight().background(m.sidebar).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        cats.forEachIndexed { i, c ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (i == cat) m.accentSoft else Color.Transparent)
                                    .clickable { cat = i }.padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(c.icon, null, Modifier.size(20.dp), tint = if (i == cat) m.accentText else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(c.title, style = MaterialTheme.typography.bodyLarge, color = if (i == cat) m.accentText else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(m.line))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (!wide) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cats.forEachIndexed { i, c -> Chip(c.title, i == cat, { cat = i }, icon = c.icon) }
                    }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 32.dp else 16.dp, vertical = 12.dp)) {
                        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                            Text(cats[cat].title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
                            when (cat) {
                                0 -> Appearance(set)
                                1 -> EditorPrefs(set)
                                2 -> InputPrefs(set)
                                3 -> NewNotePrefs(set)
                                4 -> PrivacyPrefs(set)
                                5 -> ShortcutsTable(set)
                                else -> About()
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String? = null, content: @Composable () -> Unit) {
    val m = mint
    if (title != null) SectionLabel(title)
    Column(
        Modifier.fillMaxWidth().padding(top = if (title == null) 8.dp else 0.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, m.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
private fun Opt(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun Choice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, t -> Chip(t, i == selected, { onSelect(i) }) }
    }
}

@Composable
private fun Appearance(set: com.mdev.mnotes.data.Settings) {
    Group {
        Opt(T.t("Motyw", "Theme")) { Choice(listOf(T.t("Systemowy", "System"), T.t("Jasny", "Light"), T.t("Ciemny", "Dark"), "AMOLED"), set.theme) { set.theme = it } }
        Opt(T.t("Kolor akcentu (jak w Mint-Y)", "Accent color (like Mint-Y)")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Accents.list.forEachIndexed { i, a -> Dot(a.color, 34.dp, i == set.accent, { set.accent = i }) }
            }
            Text(Accents.list[set.accent.coerceIn(0, Accents.list.size - 1)].name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        ToggleRow(T.t("Zagęszczony interfejs (desktopowy)", "Compact interface (desktop-like)"), set.compact) { set.compact = it }
        ToggleRow(T.t("Delikatna wibracja przy dotknięciu przycisków", "Light vibration when tapping buttons"), set.haptics) { set.haptics = it }
        Opt(T.t("Pasek menu w edytorze (Plik, Edycja, Widok…)", "Editor menu bar (File, Edit, View…)")) {
            Choice(listOf(T.t("Nigdy", "Never"), T.t("Na dużych ekranach", "On large screens"), T.t("Zawsze", "Always")), set.menuBar) { set.menuBar = it }
        }
        ToggleRow(T.t("Otwieraj zeszyty jako karty (pasek kart w nagłówku, bez dodatkowego miejsca; Ctrl+Tab przełącza)", "Open notebooks as tabs (tab strip inside the header, no extra space; Ctrl+Tab switches)"), set.tabs) { set.tabs = it }
        ToggleRow(T.t("Zadokuj panel stron z lewej na szerokim oknie (≥ 840 dp, DeX)", "Dock the page panel on the left in wide windows (≥ 840 dp, DeX)"), set.dockPages) { set.dockPages = it }
        Opt(T.t("Język", "Language")) { Choice(listOf(T.t("Systemowy", "System"), "Polski", "English"), set.lang) { set.lang = it } }
        Opt(T.t("Jednostki", "Units")) { Choice(listOf(T.t("Milimetry", "Millimeters"), T.t("Cale", "Inches")), if (set.unitsMm) 0 else 1) { set.unitsMm = it == 0 } }
    }
}

@Composable
private fun EditorPrefs(set: com.mdev.mnotes.data.Settings) {
    Group(T.t("Pasek narzędzi", "Toolbar")) {
        Opt(T.t("Położenie", "Position")) { Choice(listOf(T.t("Góra", "Top"), T.t("Dół", "Bottom"), T.t("Lewo", "Left"), T.t("Prawo", "Right")), set.toolbarPos) { set.toolbarPos = it } }
        Opt(T.t("Rozmiar", "Size")) { Choice(listOf(T.t("Mały", "Small"), T.t("Normalny", "Normal"), T.t("Duży", "Large")), set.toolbarSize) { set.toolbarSize = it } }
        Opt(T.t("Listwa grubości i kolorów", "Width and colour strip")) { Choice(listOf(T.t("Z lewej", "Left"), T.t("Z prawej", "Right"), T.t("Przy pasku", "By the toolbar")), set.optionsPos) { set.optionsPos = it } }
    }
    Group(T.t("Renderowanie", "Rendering")) {
        Opt(T.t("Jakość wyświetlania strony", "Page display quality")) {
            Choice(
                listOf(T.t("Zrównoważona", "Balanced"), T.t("Ostra", "Sharp"), T.t("Oszczędna", "Economy"), T.t("Klasyczna (obraz)", "Classic (image)")),
                set.renderQuality,
            ) { set.renderQuality = it }
            Text(
                when (set.renderQuality) {
                    1 -> T.t("Najostrzejsza kreska nawet przy maksymalnym powiększeniu; więcej pamięci i pracy w tle.", "Sharpest strokes even at maximum zoom; uses more memory and background work.")
                    2 -> T.t("Najmniej pamięci i baterii. Przy dużym powiększeniu pismo jest mniej ostre.", "Least memory and battery. Strokes get softer at high zoom.")
                    3 -> T.t("Cała strona jako jeden obraz, jak w pierwszych wersjach. Najprostsza i płynna, ale po mocnym przybliżeniu widać piksele.", "Whole page as a single image, like early versions. Simplest and smooth, but pixels show when zoomed in far.")
                    else -> T.t("Ostry obraz w kafelkach dopasowanych do powiększenia, płynny zoom. Zalecana.", "Sharp tiled image matched to the zoom level with smooth zooming. Recommended.")
                },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    Group(T.t("Pióro", "Pen")) {
        Opt(T.t("Wygładzanie: ${(set.smoothing * 100).toInt()}%", "Smoothing: ${(set.smoothing * 100).toInt()}%")) { MSlider(set.smoothing, 0f, 2f) { set.smoothing = it } }
        ToggleRow(T.t("Przytrzymaj, aby wyprostować kształt", "Hold to snap to shape"), set.holdToShape) { set.holdToShape = it }
        if (set.holdToShape) Opt(T.t("Czas przytrzymania: ${set.holdMs} ms", "Hold time: ${set.holdMs} ms")) { MSlider(set.holdMs.toFloat(), 250f, 1200f) { set.holdMs = (it / 50).toInt() * 50 } }
    }
    Group(T.t("Pędzel formatu i wypełnienie", "Format painter & fill")) {
        Opt(T.t("Tryb pędzla formatu", "Format painter mode")) { Choice(listOf(T.t("Obiektowo", "Object"), "Lasso"), set.painterMode) { set.painterMode = it } }
        ToggleRow(T.t("Wiaderko domyka małe szpary w liniach", "Fill closes small gaps in lines"), set.fillCloseGaps) { set.fillCloseGaps = it }
    }
    Group(T.t("Gumka i lasso", "Eraser & lasso")) {
        Opt(T.t("Domyślny tryb gumki", "Default eraser mode")) { Choice(listOf(T.t("Obiektowa", "Object"), T.t("Pikselowa", "Pixel"), "Lasso"), set.eraserMode) { set.eraserMode = it } }
        Opt(T.t("Rozmiar gumki: ${set.eraserSize.toInt()} pt", "Eraser size: ${set.eraserSize.toInt()} pt")) { MSlider(set.eraserSize, 3f, 60f) { set.eraserSize = it } }
        ToggleRow(T.t("Gumka pomija zakreślacz", "Eraser ignores highlighter"), set.eraserKeepHighlighter) { set.eraserKeepHighlighter = it }
        Opt(T.t("Kształt lassa", "Lasso shape")) { Choice(listOf(T.t("Odręczne", "Freehand"), T.t("Prostokąt", "Rectangle")), if (set.lassoRect) 1 else 0) { set.lassoRect = it == 1 } }
        ToggleRow(T.t("Przyciągaj do siatki", "Snap to grid"), set.snapGrid) { set.snapGrid = it }
    }
}

@Composable
private fun InputPrefs(set: com.mdev.mnotes.data.Settings) {
    Group {
        Opt(T.t("Palec", "Finger")) { Choice(listOf(T.t("Przewija i powiększa", "Pans & zooms"), T.t("Rysuje", "Draws"), T.t("Ignorowany", "Ignored")), set.fingerMode) { set.fingerMode = it } }
        ToggleRow(T.t("Odrzucanie dłoni (rysik ma pierwszeństwo)", "Palm rejection (stylus wins)"), set.palmRejection) { set.palmRejection = it }
        ToggleRow(T.t("Cofnij dotknięciem dwóch palców", "Two-finger tap to undo"), set.twoFingerUndo) { set.twoFingerUndo = it }
        ToggleRow(T.t("Odciągnij za ostatnią stroną, aby dodać nową", "Pull past the last page to add a new one"), set.pullAddPage) { set.pullAddPage = it }
        Opt(T.t("Podwójne dotknięcie palcem", "Finger double tap")) {
            Choice(listOf(T.t("Cofnij", "Undo"), T.t("Ponów", "Redo"), T.t("Przybliż / oddal", "Zoom in / out"), T.t("Brak", "None")), set.doubleTap) { set.doubleTap = it }
        }
        Opt(T.t("Przycisk rysika", "Stylus button")) {
            Choice(listOf(T.t("Gumka", "Eraser"), "Lasso", T.t("Cofnij", "Undo"), T.t("Laser", "Laser"), T.t("Brak", "None")), set.penButton) { set.penButton = it }
        }
    }
    Group(T.t("Mysz i klawiatura (DeX / Linux)", "Mouse & keyboard (DeX / Linux)")) {
        Text(
            T.t(
                "Kółko przewija, Ctrl+kółko powiększa, prawy przycisk otwiera menu kontekstowe. Skróty klawiszowe są wymienione w zakładce „Skróty klawiszowe”, a Ctrl+K otwiera paletę poleceń.",
                "Wheel scrolls, Ctrl+wheel zooms, right-click opens the context menu. Keyboard shortcuts are listed in the “Keyboard shortcuts” tab, and Ctrl+K opens the command palette.",
            ),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun NewNotePrefs(set: com.mdev.mnotes.data.Settings) {
    Group {
        Opt(T.t("Rozmiar strony", "Page size")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (f in PageFormat.entries) Chip(formatName(f), f.id == set.newFormat, { set.newFormat = f.id })
            }
        }
        ToggleRow(T.t("Orientacja pozioma", "Landscape"), set.newLandscape) { set.newLandscape = it }
        Opt(T.t("Wzór", "Template")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in Template.entries) Chip(templateName(t), t.code == set.newTemplate, { set.newTemplate = t.code })
            }
        }
        Opt(T.t("Papier", "Paper")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (p in Paper.presets) Dot(Color(p), 32.dp, p == set.newPaper, { set.newPaper = p })
            }
        }
    }
}

@Composable
private fun PrivacyPrefs(set: com.mdev.mnotes.data.Settings) {
    val ctx = LocalContext.current
    Group(T.t("Prywatność", "Privacy")) {
        Text(
            T.t("mNotes działa w całości offline: aplikacja nie ma uprawnienia INTERNET i nie zbiera żadnych danych.", "mNotes is fully offline: the app has no INTERNET permission and collects no data."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
        )
        ToggleRow(T.t("Blokada aplikacji (biometria lub PIN urządzenia)", "App lock (biometrics or device PIN)"), set.appLock) { on ->
            if (!on) set.appLock = false
            else if ((ctx as? androidx.fragment.app.FragmentActivity)?.let { com.mdev.mnotes.app.MainActivity.canLock(it) } == true) set.appLock = true
            else android.widget.Toast.makeText(ctx, T.t("Najpierw ustaw blokadę ekranu w systemie.", "Set a screen lock in system settings first."), android.widget.Toast.LENGTH_LONG).show()
        }
        ToggleRow(T.t("Blokuj zrzuty ekranu i podgląd w ostatnich", "Block screenshots and recents preview"), set.secureFlag) { set.secureFlag = it }
    }
    SyncPrefs(set)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(set.exportJson().toByteArray()) } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val txt = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching
            set.importJson(txt)
            android.widget.Toast.makeText(ctx, T.t("Zaimportowano. Zrestartuj aplikację, aby zastosować wszystko.", "Imported. Restart the app to apply everything."), android.widget.Toast.LENGTH_LONG).show()
        }
    }
    Group(T.t("Ustawienia jako plik", "Settings as a file")) {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(T.t("Eksportuj ustawienia (JSON)", "Export settings (JSON)"), false, { exportLauncher.launch("mnotes-settings.json") })
            Chip(T.t("Importuj", "Import"), false, { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
        }
    }
}

/** Folder synchronizacji (FIL-004): lustro biblioteki w katalogu, który zsynchronizuje Syncthing, Nextcloud, rsync itp. */
@Composable
private fun SyncPrefs(set: com.mdev.mnotes.data.Settings) {
    val ctx = LocalContext.current
    val sync = ctx.app.sync
    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    fun release(uri: String) { runCatching { ctx.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(uri), flags) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, flags) }
            if (set.syncUri.isNotEmpty() && set.syncUri != uri.toString()) { release(set.syncUri); sync.forgetState() }
            set.syncUri = uri.toString()
        }
    }
    val linked = set.syncUri.isNotEmpty()
    val name by androidx.compose.runtime.produceState<String?>(null, set.syncUri) {
        value = if (set.syncUri.isEmpty()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { sync.folderName(set.syncUri) }
    }
    val access = remember(set.syncUri) { !linked || sync.accessible(set.syncUri) }
    fun whenText(min: Int) = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(min * 60000L))

    Group(T.t("Folder synchronizacji", "Sync folder")) {
        Text(
            T.t(
                "Kopiuje zeszyty do wybranego folderu jako pliki .mnotes (opcjonalnie PDF i Markdown), z zachowaniem struktury folderów biblioteki. Folder zsynchronizujesz Syncthingiem, Nextcloudem lub rsync-iem — aplikacja nie łączy się z siecią. Niczego w tym folderze nie usuwa; zeszyty zablokowane są pomijane.",
                "Copies notebooks to a folder you choose as .mnotes files (optionally PDF and Markdown), keeping the library's folder structure. Sync that folder with Syncthing, Nextcloud or rsync — the app never touches the network. It deletes nothing in that folder; locked notebooks are skipped.",
            ),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(if (linked) T.t("Zmień folder…", "Change folder…") else T.t("Wybierz folder…", "Choose folder…"), false, { picker.launch(null) })
            if (linked) Chip(T.t("Odłącz", "Detach"), false, { release(set.syncUri); sync.forgetState(); set.syncUri = ""; set.syncAuto = false })
        }
        if (linked) {
            Text(
                T.t("Folder: ", "Folder: ") + (name ?: "…"),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp),
            )
            if (!access) Text(
                T.t("Brak dostępu do folderu — wybierz go ponownie.", "No access to the folder — choose it again."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp),
            )
            ToggleRow(T.t("Dołącz PDF", "Include PDF"), set.syncPdf) { set.syncPdf = it }
            ToggleRow(T.t("Dołącz Markdown (tylko pola tekstowe)", "Include Markdown (text boxes only)"), set.syncMd) { set.syncMd = it }
            ToggleRow(T.t("Kopiuj automatycznie po wyjściu z aplikacji", "Copy automatically when leaving the app"), set.syncAuto) { set.syncAuto = it }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(
                    if (sync.running) T.t("Synchronizuję…", "Syncing…") else T.t("Synchronizuj teraz", "Sync now"), false,
                    { if (access) sync.start() },
                )
                if (sync.running && sync.total > 0) Text("${sync.done} / ${sync.total}", style = MaterialTheme.typography.labelLarge)
            }
            val r = sync.report
            val line = when {
                r != null -> T.t("Ostatnio: ", "Last: ") + whenText((r.at / 60000L).toInt()) + T.t(
                    " · zapisano ${r.written}, bez zmian ${r.unchanged}, zablokowane ${r.locked}, błędy ${r.failed}",
                    " · written ${r.written}, unchanged ${r.unchanged}, locked ${r.locked}, errors ${r.failed}",
                )
                set.lastSync > 0 -> T.t("Ostatnia udana synchronizacja: ", "Last successful sync: ") + whenText(set.lastSync)
                else -> ""
            }
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Skróty stałe (nieedytowalne) — alternatywy i klawisze kontekstowe. */
private val FIXED_SHORTCUTS: List<Pair<String, List<Pair<String, String>>>> = listOf(
    T.t("Edytor — stałe", "Editor — fixed") to listOf(
        "Ctrl+Shift+Z" to T.t("Ponów (alternatywa)", "Redo (alternative)"), "Ctrl+Shift+P" to T.t("Paleta poleceń (alternatywa)", "Command palette (alternative)"),
        "Backspace" to T.t("Usuń zaznaczenie", "Delete selection"), "←  →  ↑  ↓ (Shift = ×10)" to T.t("Przesuń zaznaczenie", "Nudge selection"),
        "Esc" to T.t("Cofnij zaznaczenie / zamknij szukanie / wyjdź z trybu skupienia", "Clear selection / close search / leave focus mode"),
    ),
    T.t("Biblioteka", "Library") to listOf(
        "Ctrl+N" to T.t("Nowy zeszyt", "New notebook"), "Ctrl+Shift+N" to T.t("Nowy folder", "New folder"), "Ctrl+F" to T.t("Szukaj", "Search"),
        "Ctrl+A" to T.t("Zaznacz wszystko", "Select all"), "Ctrl+1 / 2 / 3" to T.t("Widok: ikony / lista / kompaktowy", "View: icons / list / compact"),
        "F2" to T.t("Zmień nazwę", "Rename"), "Enter" to T.t("Otwórz", "Open"), "Del" to T.t("Do kosza", "Move to trash"),
        "Backspace / Alt+↑" to T.t("Folder wyżej", "Up one folder"), "Esc" to T.t("Wyczyść zaznaczenie", "Clear selection"),
    ),
)

@Composable
private fun KeyCap(text: String, accent: Boolean = false) {
    val m = mint
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = if (accent) m.accentText else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (accent) m.accentSoft else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Edytowalna lista skrótów (LNX-015): kliknij wiersz → naciśnij nową kombinację. Konflikt odbiera skrót poprzedniemu poleceniu. */
@Composable
private fun ShortcutsTable(set: com.mdev.mnotes.data.Settings) {
    val m = mint
    var editing by remember { mutableStateOf<CmdDef?>(null) }
    val keys = Keymap.overrides(set)
    Text(
        T.t("Kliknij polecenie, aby zmienić jego skrót. Te same polecenia są w palecie (Ctrl+K) i na pasku menu.", "Click a command to change its shortcut. The same commands appear in the palette (Ctrl+K) and the menu bar."),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
    )
    for (g in 0 until CmdDefs.groups.size) {
        Group(CmdDefs.groupName(g)) {
            CmdDefs.list.filter { it.group == g }.forEachIndexed { i, d ->
                val key = keys[d.id] ?: d.key
                if (i > 0) HorizontalDivider(color = m.line.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { editing = d }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    KeyCap(key.ifEmpty { T.t("brak", "none") }, accent = keys.containsKey(d.id))
                }
            }
        }
    }
    if (keys.isNotEmpty()) Chip(T.t("Przywróć wszystkie domyślne", "Restore all defaults"), false, { Keymap.resetAll(set) }, Modifier.padding(vertical = 8.dp))
    for ((group, rows) in FIXED_SHORTCUTS) {
        Group(group) {
            rows.forEachIndexed { i, (k, d) ->
                if (i > 0) HorizontalDivider(color = m.line.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    KeyCap(k)
                }
            }
        }
    }
    editing?.let { KeyCaptureDialog(set, it) { editing = null } }
}

@Composable
private fun KeyCaptureDialog(set: com.mdev.mnotes.data.Settings, d: CmdDef, onDismiss: () -> Unit) {
    val m = mint
    var chord by remember { mutableStateOf(Chord.parse(Keymap.keyOf(set, d))) }
    val focus = remember { FocusRequester() }
    val text = chord?.label().orEmpty()
    val other = if (text.isEmpty()) null else Keymap.owner(set, text, d.id)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    MDialog(d.title, onDismiss, confirm = T.t("Przypisz", "Assign"), onConfirm = { Keymap.assign(set, d.id, text); onDismiss() }) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, m.accent, RoundedCornerShape(10.dp)).focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val c = Chord.of(e)
                    if (c != null) chord = c
                    c != null || e.key != Key.Escape
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text.ifEmpty { T.t("Naciśnij skrót…", "Press a shortcut…") }, style = MaterialTheme.typography.titleMedium, color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else m.accentText)
        }
        if (other != null) Text(
            T.t("Ten skrót ma teraz „${other.title}” — zostanie mu odebrany.", "“${other.title}” currently uses this shortcut — it will lose it."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(T.t("Domyślny", "Default") + if (d.key.isNotEmpty()) " (${d.key})" else "", false, { chord = Chord.parse(d.key) })
            Chip(T.t("Bez skrótu", "None"), false, { chord = null })
        }
    }
}

@Composable
private fun About() {
    Group {
        Text("mNotes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            T.t(
                "Notatnik odręczny w stylu GoodNotes: wektorowe pióra, strony i szablony, PDF, obrazy, tekst, otwarty format plików. Działa offline, bez kont i telemetrii.",
                "A GoodNotes-style handwriting notebook: vector pens, pages and templates, PDF, images, text, open file format. Offline, no accounts, no telemetry.",
            ),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}
