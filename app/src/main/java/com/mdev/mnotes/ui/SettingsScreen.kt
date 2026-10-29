package com.mdev.mnotes.ui

import com.mdev.mnotes.core.edit.AngleSnap
import com.mdev.mnotes.core.layout.HelperStrip
import com.mdev.mnotes.core.layout.MiniMap
import kotlin.math.roundToInt
import com.mdev.mnotes.core.layout.PageShift
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.app.Flavor
import com.mdev.mnotes.app.app
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.pen.LaserColors
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
import androidx.compose.ui.input.key.isCtrlPressed
import com.mdev.mnotes.core.search.SettingsQuery
import com.mdev.mnotes.data.Settings

private class Cat(val icon: ImageVector, val title: String)

/** Indeks kategorii „Skróty klawiszowe” — otwierany bezpośrednio z palety poleceń. */
const val SHORTCUTS_CAT = 6

/** Indeks kategorii „Kolory narzędzi” — otwierany z listwy w edytorze. */
const val COLORS_CAT = 2

/** Indeks kategorii „Edytor i pióro” — otwierany z menu paska narzędzi („Dostosuj pasek…”). */
const val EDITOR_CAT = 1

@Composable
fun SettingsScreen(onBack: () -> Unit, startCat: Int = 0, startQuery: String = "") {
    val ctx = LocalContext.current
    val set = ctx.app.settings
    val m = mint
    var cat by remember { mutableIntStateOf(startCat) }
    var query by remember { mutableStateOf(startQuery) }
    // 3c-A6: słownik literówek zbiera się z wierszy przy pierwszym wyszukiwaniu; gdy urośnie, zapytanie liczy się od nowa.
    var vocabN by remember { mutableIntStateOf(SearchVocab.size) }
    val q = remember(query, vocabN) { SettingsQuery.of(query, SearchVocab.words()) }
    SideEffect { if (q != null && SearchVocab.size != vocabN) vocabN = SearchVocab.size }
    val searchFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val cats = listOf(
        Cat(Icons.Rounded.Palette, T.t("Wygląd", "Appearance")),
        Cat(Icons.Rounded.Edit, T.t("Edytor i pióro", "Editor & pen")),
        Cat(Icons.Rounded.ColorLens, T.t("Kolory narzędzi", "Tool colours")),
        Cat(Icons.Rounded.TouchApp, T.t("Wejście", "Input")),
        Cat(Icons.Rounded.NoteAdd, T.t("Nowe notatki", "New notes")),
        Cat(Icons.Rounded.Lock, T.t("Prywatność i kopie", "Privacy & backup")),
        Cat(Icons.Rounded.Keyboard, T.t("Skróty klawiszowe", "Keyboard shortcuts")),
    ) + listOfNotNull(
        Flavor.lab?.let { Cat(Icons.Rounded.Extension, T.t("Rozszerzenia", "Extensions")) },
        Cat(Icons.Rounded.Info, T.t("O aplikacji", "About")),
    )
    fun open(i: Int) { query = ""; cat = i }
    BackHandler { if (query.isNotEmpty()) query = "" else onBack() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.isCtrlPressed && e.key == Key.F -> { runCatching { searchFocus.requestFocus() }; true }
                    e.key == Key.Escape && query.isNotEmpty() -> { query = ""; true }
                    else -> false
                }
            },
    ) {
        Row(Modifier.fillMaxWidth().background(m.header).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, T.t("Wstecz", "Back"), onBack, shortcut = "Esc")
            Text(T.t("Ustawienia", "Settings"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            Box(Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterEnd) {
                MField(
                    query, { query = it }, Modifier.widthIn(max = 380.dp).fillMaxWidth().focusRequester(searchFocus),
                    hint = T.t("Szukaj w ustawieniach…", "Search settings…"),
                    leading = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            if (query.isNotEmpty()) IconBtn(Icons.Rounded.Close, T.t("Wyczyść wyszukiwanie", "Clear search"), { query = "" })
        }
        HorizontalDivider(color = m.line)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 760.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    Column(Modifier.width(240.dp).fillMaxHeight().background(m.sidebar).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        cats.forEachIndexed { i, c ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (q == null && i == cat) m.accentSoft else Color.Transparent)
                                    .clickable { open(i) }.padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(c.icon, null, Modifier.size(20.dp), tint = if (q == null && i == cat) m.accentText else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(c.title, style = MaterialTheme.typography.bodyLarge, color = if (q == null && i == cat) m.accentText else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(m.line))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (!wide) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cats.forEachIndexed { i, c -> Chip(c.title, q == null && i == cat, { open(i) }, icon = c.icon) }
                    }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 32.dp else 16.dp, vertical = 12.dp)) {
                        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                            if (q != null) SearchResults(q, query, set, cats.map { it.icon to it.title }, ::open) { query = it } else {
                                Text(cats[cat].title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
                                when (cat) {
                                    0 -> Appearance(set)
                                    1 -> EditorPrefs(set)
                                    2 -> ColorSetsPrefs(set)
                                    3 -> InputPrefs(set)
                                    4 -> NewNotePrefs(set)
                                    5 -> PrivacyPrefs(set)
                                    6 -> ShortcutsTable(set)
                                    7 -> Flavor.lab?.SettingsSection() ?: About()
                                    else -> About()
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Kategorie, które przeszukujemy (bez „O aplikacji”: sam opis, nic do ustawienia; „Laboratorium” tylko w lab). */
private val SEARCHABLE_CATS = SettingsQuery.searchableCats(Flavor.lab != null)

/** Wyniki wyszukiwania: te same kontrolki co w kategoriach, odfiltrowane i pogrupowane pod nagłówkami kategorii. */
@Composable
private fun SearchResults(q: SettingsQuery, raw: String, set: Settings, cats: List<Pair<ImageVector, String>>, open: (Int) -> Unit, retry: (String) -> Unit) {
    val m = mint
    // 3c-A6: kategoria z najpewniejszym trafieniem idzie pierwsza; wyniki zgłaszają wiersze w trakcie rysowania.
    val best = remember(q) { HashMap<Int, Int>() }
    var order by remember(q) { mutableStateOf(SEARCHABLE_CATS) }
    SideEffect { SEARCHABLE_CATS.sortedByDescending { best[it] ?: 0 }.let { if (it != order) order = it } }
    q.suggestion?.let { s ->
        Row(
            Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { retry(s) }.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = m.accentText)
            Text(T.t("Czy chodziło o: „$s”?", "Did you mean: “$s”?"), style = MaterialTheme.typography.bodyMedium, color = m.accentText)
        }
    }
    CompositionLocalProvider(LocalSettingsQuery provides q) {
        IfAny(fallback = { NoResults(raw) }) {
            for (i in order) CompositionLocalProvider(LocalScoreSink provides { s: Int -> if (s > (best[i] ?: 0)) best[i] = s }) { IfAny(
                top = 18.dp,
                header = {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open(i) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(cats[i].first, null, Modifier.size(20.dp), tint = m.accentText)
                        Text(cats[i].second, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            ) {
                when (i) {
                    0 -> Appearance(set)
                    1 -> EditorPrefs(set)
                    2 -> ColorsPointer(set)
                    3 -> InputPrefs(set)
                    4 -> NewNotePrefs(set)
                    5 -> PrivacyPrefs(set)
                    6 -> ShortcutsTable(set)
                    else -> Flavor.lab?.SettingsSection()
                }
            } }
        }
    }
}

@Composable
private fun NoResults(raw: String) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(T.t("Nic nie znaleziono dla „${raw.trim()}”", "Nothing found for “${raw.trim()}”"), style = MaterialTheme.typography.titleMedium)
        Text(
            T.t("Spróbuj innego słowa, np. gumka, motyw, skróty, kopia.", "Try another word, e.g. eraser, theme, shortcuts, backup."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Zamiast całych „Kolorów narzędzi” (zestawy, karty, okna) wynik prowadzi do tej kategorii. */
@Composable
private fun ColorsPointer(set: Settings) {
    Group(keys = "kolory narzędzi zestawy kolorów barwy paleta atrament zakreślacz tool colours colour sets palette ink highlighter") {
        Extra {
            Text(
                T.t("Zestawy kolorów i to, które narzędzie z którego korzysta, ustawisz w tej kategorii.", "Colour sets and which tool uses which are set in this category."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
internal fun Group(title: String? = null, keys: String = "", content: @Composable () -> Unit) {
    val m = mint
    if (LocalSettingsQuery.current != null) {
        val shape = RoundedCornerShape(12.dp)
        GroupSearchScope(title.orEmpty(), keys) {
            IfAny(
                gap = 2.dp, padH = 14.dp, padV = 8.dp, top = if (title == null) 8.dp else 0.dp,
                header = title?.let { { SectionLabel(it) } },
                backdrop = Modifier.clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, m.line, shape),
                body = content,
            )
        }
        return
    }
    if (title != null) SectionLabel(title)
    Column(
        Modifier.fillMaxWidth().padding(top = if (title == null) 8.dp else 0.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, m.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
private fun Opt(label: String, keys: String = "", content: @Composable () -> Unit) {
    if (!rowVisible(label, keys)) return
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(rowLabel(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
internal fun Choice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, t -> Chip(t, i == selected, { onSelect(i) }) }
    }
}

@Composable
private fun Appearance(set: com.mdev.mnotes.data.Settings) {
    Group {
        Opt(T.t("Motyw", "Theme"), "ciemny jasny dark light amoled systemowy czarny wygląd kolory") { Choice(listOf(T.t("Systemowy", "System"), T.t("Jasny", "Light"), T.t("Ciemny", "Dark"), "AMOLED"), set.theme) { set.theme = it } }
        Opt(T.t("Kolor akcentu (jak w Mint-Y)", "Accent color (like Mint-Y)"), "barwa zielony niebieski kolor interfejsu accent colour") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Accents.list.forEachIndexed { i, a -> Dot(a.color, 34.dp, i == set.accent, { set.accent = i }) }
            }
            Text(Accents.list[set.accent.coerceIn(0, Accents.list.size - 1)].name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        // 2b-A9: motyw dotyczy interfejsu, a ten wybór — samych stron zeszytów (też w „⋮” edytora).
        Opt(T.t("Strony zeszytów", "Notebook pages"), "tryb ciemny jasny strony papier tło odwróć kolory noc dark mode light pages invert night") {
            Choice(pageThemeLabels().map { it.second }, set.pageTheme) { set.pageTheme = it }
            Text(
                T.t("„Jak powstały”: każda strona po swojemu (F11 w „Stylu strony” odwraca jedną). Pismo zachowuje odcień, zmienia tylko jasność.",
                    "“As made”: each page as created (F11 in “Page style” inverts one). Ink keeps its hue and only changes lightness."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
        }
        ToggleRow(
            T.t("Odwracaj też zdjęcia i obrazy na stronach", "Also invert photos and images on pages"),
            set.pageThemePhotos, keys = "zdjęcia obrazy fotografie odwracanie tryb ciemny photos images invert dark",
        ) { set.pageThemePhotos = it }
        ToggleRow(T.t("Zagęszczony interfejs (desktopowy)", "Compact interface (desktop-like)"), set.compact, keys = "compact gęsty kompaktowy desktop małe odstępy") { set.compact = it }
        ToggleRow(T.t("Delikatna wibracja przy dotknięciu przycisków", "Light vibration when tapping buttons"), set.haptics, keys = "haptyka drgania wibracje vibration haptic") { set.haptics = it }
        ToggleRow(T.t("Podpowiedź z nazwą przycisku (najechanie, przytrzymanie)", "Button name tooltip (hover, long press)"), set.tooltips, keys = "podpowiedź dymek napis nazwa tooltip hover") { set.tooltips = it }
        ToggleRow(T.t("Pokazuj dymki o narzędziach po ich wybraniu", "Show tool tips after picking a tool"), set.toolHints, keys = "dymki komunikaty napis wybrane narzędzie podpowiedź toast hint") { set.toolHints = it }
        HelperStripPrefs(set)
        ToggleRow(
            T.t("Przewijanie o pół ekranu poza brzeg stron (wygodniej pisać przy brzegu, np. leworęcznym)", "Scroll half a screen past the page edges (easier writing near an edge, e.g. left-handed)"),
            set.scrollMargin, keys = "leworęczny lewa ręka dłoń przewijanie margines zapas brzeg pierwsza strona przesuń left handed scroll margin overscroll",
        ) { set.scrollMargin = it }
        ToggleRow(
            T.t("Mini-mapa strony po przewinięciu przy dużym powiększeniu (przesuwana palcem)", "Page mini-map after scrolling when zoomed in (moved with a finger)"),
            set.miniMap, keys = "mini mapa podgląd strony nawigacja powiększenie zoom róg minimap overview",
        ) { set.miniMap = it }
        // 2b-A3: rozmiar i narożnik mini-mapy.
        if (set.miniMap) {
            Opt(T.t("Rozmiar mini-mapy: ${set.miniMapSize.toInt()} dp", "Mini-map size: ${set.miniMapSize.toInt()} dp"), "mini mapa rozmiar wielkość duża mała size") {
                MSlider(set.miniMapSize, MiniMap.MIN_SIZE_DP, MiniMap.MAX_SIZE_DP) { set.miniMapSize = (it / 10f).roundToInt() * 10f }
            }
            Opt(T.t("Narożnik mini-mapy", "Mini-map corner"), "mini mapa róg narożnik położenie lewy prawy górny dolny corner position") {
                Choice(
                    listOf(T.t("Prawy dolny", "Bottom right"), T.t("Lewy dolny", "Bottom left"), T.t("Prawy górny", "Top right"), T.t("Lewy górny", "Top left")),
                    set.miniMapCorner,
                ) { set.miniMapCorner = it }
            }
        }
        // Przegląd §3.2a: powiększenie to widok, nie wejście.
        Opt(T.t("Największe powiększenie: ×${set.zoomMaxFactor.toInt()} (domyślnie ×6)", "Maximum zoom: ×${set.zoomMaxFactor.toInt()} (default ×6)"), "zoom powiększenie maksymalne przybliżenie max zoom") {
            MSlider(set.zoomMaxFactor, 2f, 24f) { set.zoomMaxFactor = Math.round(it).toFloat() }
        }
        ToggleRow(T.t("Animacja zmiany kolejności stron", "Animate page reordering"), set.pageAnim, keys = "animacja strony kolejność przesuwanie przestawianie animation reorder pages") { set.pageAnim = it }
        if (set.pageAnim) Opt(T.t("Czas animacji stron: ${set.pageAnimMs} ms", "Page animation time: ${set.pageAnimMs} ms"), "animacja strony czas szybkość duration speed") {
            MSlider(set.pageAnimMs.toFloat(), PageShift.MIN_MS.toFloat(), PageShift.MAX_MS.toFloat()) { set.pageAnimMs = (it / 20).toInt() * 20 }
        }
        Opt(T.t("Pasek menu w edytorze (Plik, Edycja, Widok…)", "Editor menu bar (File, Edit, View…)"), "menu menubar") {
            Choice(listOf(T.t("Nigdy", "Never"), T.t("Na dużych ekranach", "On large screens"), T.t("Zawsze", "Always")), set.menuBar) { set.menuBar = it }
        }
        ToggleRow(T.t("Otwieraj zeszyty jako karty (pasek kart w nagłówku, bez dodatkowego miejsca; Ctrl+Tab przełącza)", "Open notebooks as tabs (tab strip inside the header, no extra space; Ctrl+Tab switches)"), set.tabs, keys = "zakładki tabs przełączanie notatek wiele zeszytów") { set.tabs = it }
        ToggleRow(T.t("Zadokuj panel stron z lewej na szerokim oknie (≥ 840 dp, DeX)", "Dock the page panel on the left in wide windows (≥ 840 dp, DeX)"), set.dockPages, keys = "panel stron miniatury dex szeroki ekran okno tablet") { set.dockPages = it }
        Opt(T.t("Język", "Language"), "language polski angielski english") { Choice(listOf(T.t("Systemowy", "System"), "Polski", "English"), set.lang) { set.lang = it } }
        Opt(T.t("Jednostki", "Units"), "units mm cale milimetry centymetry inches") { Choice(listOf(T.t("Milimetry", "Millimeters"), T.t("Cale", "Inches")), if (set.unitsMm) 0 else 1) { set.unitsMm = it == 0 } }
    }
}

/**
 * 2b-A2: listwa przy gumce, lassie i laserze — tryb i zawartość wyskakującego menu (dla których narzędzi, które grupy).
 * Wiersze są widoczne w wyszukiwarce osobno, więc „gumka menu rozmiary” trafia wprost w przełącznik.
 */
@Composable
private fun HelperStripPrefs(set: com.mdev.mnotes.data.Settings) {
    Opt(
        T.t("Listwa przy gumce, lassie i laserze", "Strip with the eraser, lasso and laser"),
        "listwa pasek boczny gumka lasso laser opcje menu wyskakujące powrót pisanie strip eraser popup back writing",
    ) {
        Choice(listOf(T.t("Pisanie + menu przy ikonie", "Writing + menu by the icon"), T.t("Opcje narzędzia + powrót", "Tool options + back")), set.helperStrip) { set.helperStrip = it }
        Text(
            if (set.helperStrip == HelperStrip.WRITE_AND_MENU) T.t("Listwa zostaje przy kolorach i grubościach — dotknięcie koloru wraca do pisania; opcje narzędzia wyskakują przy jego ikonie.", "The strip keeps colours and widths — tapping a colour goes back to writing; tool options pop up by its icon.")
            else T.t("Listwa pokazuje opcje narzędzia, a ostatni przycisk wraca do ostatniego narzędzia pisania.", "The strip shows the tool's options; the last button goes back to the last writing tool."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (set.helperStrip != HelperStrip.WRITE_AND_MENU) return
    val cfg = set.helperMenu
    val groups = listOf(
        Triple(HelperStrip.Helper.ERASER, T.t("Menu przy gumce", "Menu by the eraser"), "gumka mazanie ścieranie tryby rozmiar zakreślacz eraser"),
        Triple(HelperStrip.Helper.LASSO, T.t("Menu przy lassie", "Menu by the lasso"), "lasso zaznaczanie kształt prostokąt pętla lasso shape"),
        Triple(HelperStrip.Helper.LASER, T.t("Menu przy laserze", "Menu by the laser"), "laser wskaźnik kolor śladu pointer colour"),
        Triple(HelperStrip.Helper.SNIP, T.t("Menu przy wycinku", "Menu by the snip"), "wycinek nożyczki kształt prostokąt lasso podpis snip shape caption"),
    )
    for ((h, label, keys) in groups) {
        ToggleRow(label, cfg.menuFor(h), keys = "menu wyskakujące $keys popup") { set.helperMenu = cfg.withMenu(h, it) }
        if (cfg.menuFor(h)) {
            val items = HelperStrip.Item.entries.filter { it.helper == h }
            if (items.size > 1 && rowVisible(label, keys)) FlowRow(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in items) Chip(helperItemName(i), cfg.shows(i), { set.helperMenu = cfg.withItem(i, !cfg.shows(i)) })
            }
        }
    }
}

private fun helperItemName(i: HelperStrip.Item) = when (i) {
    HelperStrip.Item.ERASER_MODES -> T.t("Tryby", "Modes")
    HelperStrip.Item.ERASER_SIZES -> T.t("Rozmiary", "Sizes")
    HelperStrip.Item.ERASER_HIGHLIGHTER -> T.t("Pomijanie zakreślacza", "Skip highlighter")
    HelperStrip.Item.LASSO_SHAPE -> T.t("Kształt pętli", "Loop shape")
    HelperStrip.Item.LASSO_MODE -> T.t("Co zaznacza", "What it selects")
    HelperStrip.Item.LASER_COLORS -> T.t("Kolory", "Colours")
    HelperStrip.Item.SNIP_SHAPE -> T.t("Kształt wycinka", "Snip shape")
    HelperStrip.Item.SNIP_CAPTION -> T.t("Podpis źródła", "Source caption")
}

@Composable
private fun EditorPrefs(set: com.mdev.mnotes.data.Settings) {
    Group(T.t("Pasek narzędzi", "Toolbar"), keys = "położenie rozmiar listwa") {
        Opt(T.t("Położenie", "Position"), "toolbar pasek góra dół lewo prawo") { Choice(listOf(T.t("Góra", "Top"), T.t("Dół", "Bottom"), T.t("Lewo", "Left"), T.t("Prawo", "Right")), set.toolbarPos) { set.toolbarPos = it } }
        Opt(T.t("Rozmiar", "Size"), "toolbar pasek mały duży przyciski") { Choice(listOf(T.t("Mały", "Small"), T.t("Normalny", "Normal"), T.t("Duży", "Large")), set.toolbarSize) { set.toolbarSize = it } }
        Opt(T.t("Listwa grubości i kolorów", "Width and colour strip"), "toolbar pasek grubość kolor listwa") { Choice(listOf(T.t("Z lewej", "Left"), T.t("Z prawej", "Right"), T.t("Przy pasku", "By the toolbar")), set.optionsPos) { set.optionsPos = it } }
    }
    BarLayoutPrefs(set)
    Group(T.t("Układ stron przy otwieraniu", "Page layout on opening"), keys = "pionowo poziomo pojedyncza otwieranie widok domyślny zeszyty pdf dokumenty układ strony vertical horizontal single default notebooks documents layout") {
        val modes = listOf(T.t("Pionowo", "Vertical"), T.t("Poziomo", "Horizontal"), T.t("Pojedyncza", "Single"))
        Opt(T.t("Zeszyty i pliki mNotes", "Notebooks and mNotes files"), "zeszyty notatki mnotes samsung notes sdocx obrazy układ domyślny pionowo poziomo pojedyncza notebooks") {
            Choice(modes, set.viewNotebook) { set.viewNotebook = it }
        }
        Opt(T.t("Dokumenty PDF", "PDF documents"), "pdf dokumenty układ domyślny pionowo poziomo pojedyncza documents") {
            Choice(modes, set.viewPdf) { set.viewPdf = it }
        }
        Text(
            T.t(
                "Dotyczy dokumentów, w których nie wybrano układu ręcznie. Układ wybrany w edytorze (menu „Więcej” → Układ stron) jest zapamiętany osobno dla każdego dokumentu.",
                "Applies to documents where no layout was picked by hand. A layout picked in the editor (More menu → Page layout) is remembered for each document separately.",
            ),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
        )
    }
    Group(T.t("Renderowanie", "Rendering"), keys = "jakość ostrość wydajność") {
        Opt(T.t("Jakość wyświetlania strony", "Page display quality"), "render ostrość wydajność bateria pamięć kafelki płynność rozmycie ostra") {
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
    Group(T.t("Pióro", "Pen"), keys = "wygładzanie kształty rysowanie pen") {
        Opt(T.t("Wygładzanie: ${(set.smoothing * 100).toInt()}%", "Smoothing: ${(set.smoothing * 100).toInt()}%"), "gładkość stabilizacja drżenie pióro") { MSlider(set.smoothing, 0f, 2f) { set.smoothing = it } }
        ToggleRow(T.t("Przytrzymaj, aby wyprostować kształt", "Hold to snap to shape"), set.holdToShape, keys = "kształty prosta linia okrąg prostokąt prostowanie snap shape") { set.holdToShape = it }
        if (set.holdToShape) Opt(T.t("Czas przytrzymania: ${set.holdMs} ms", "Hold time: ${set.holdMs} ms"), "prostowanie kształt opóźnienie") { MSlider(set.holdMs.toFloat(), 250f, 1200f) { set.holdMs = (it / 50).toInt() * 50 } }
        // F28 (A2-4): te same przełączniki co kafelki w panelu pióra, żeby wyszukiwarka ustawień je znajdowała.
        if (set.holdToShape) {
            ToggleRow(T.t("Auto popraw √ (pierwiastek) po przytrzymaniu", "Auto-correct √ (root) after holding"), set.mathRoot, keys = "pierwiastek symbol auto korekta popraw wyrównaj matematyka root sqrt") { set.mathRoot = it }
            ToggleRow(T.t("Auto popraw ∑ (suma) po przytrzymaniu", "Auto-correct ∑ (sum) after holding"), set.mathSum, keys = "suma sigma symbol auto korekta popraw wyrównaj matematyka sum") { set.mathSum = it }
            ToggleRow(T.t("Auto popraw ∫ (całka) po przytrzymaniu", "Auto-correct ∫ (integral) after holding"), set.mathIntegral, keys = "całka symbol auto korekta popraw wygładź matematyka integral") { set.mathIntegral = it }
        }
        if (set.holdToShape) ToggleRow(T.t("Trzymaj dalej, aby wygładzić w krzywą", "Keep holding to smooth into a curve"), set.holdToCurve, keys = "krzywa bezier wygładź gładka curve smooth") { set.holdToCurve = it }
        if (set.holdToShape && set.holdToCurve) Opt(T.t("Dodatkowy czas dla krzywej: ${set.curveHoldMs} ms", "Extra time for a curve: ${set.curveHoldMs} ms"), "krzywa bezier czas opóźnienie curve") { MSlider(set.curveHoldMs.toFloat(), 0f, 1000f) { set.curveHoldMs = (it / 50).toInt() * 50 } }
        // Przegląd §3.2a: wstawianie zdjęć to praca w edytorze, nie ustawienie wejścia; 2c-A15: po parze „Przytrzymaj…” / „Czas przytrzymania”, nie między nimi.
        ToggleRow(T.t("Wstawione zdjęcie wyrównuj do kratki", "Align inserted photos to the grid"), set.imageGridFit, keys = "zdjęcie obraz kratka siatka wyrównaj przyciągnij grid snap image") { set.imageGridFit = it }
    }
    Group(T.t("Układ współrzędnych", "Coordinate axes"), keys = "osie wykres funkcja kratka siatka axes graph plot grid") {
        ToggleRow(
            T.t("Układ na kratce i wykres po kratkach", "Axes on the grid, graphs along grid lines"), set.axesSnap,
            keys = "przyciąganie docisk kratka siatka osie początek wykres linie snap grid axes origin graph",
        ) { set.axesSnap = it }
        if (set.axesSnap) Opt(
            if (set.axesSnapDp <= 0f) T.t("Docisk kresek do siatki: wyłączony", "Strokes to grid lines: off") else T.t("Docisk kresek do siatki: ${set.axesSnapDp.toInt()} dp", "Strokes to grid lines: ${set.axesSnapDp.toInt()} dp"),
            "próg docisk kreska wykres siatka czułość threshold stroke graph",
        ) { MSlider(set.axesSnapDp, 0f, 8f) { set.axesSnapDp = Math.round(it).toFloat() } }
    }
    Group(T.t("Konstrukcje", "Constructions"), keys = "konstrukcja geometria trójkąt okrąg construction geometry") {
        ToggleRow(
            T.t("Podpisuj punkty konstrukcji (A, B, C, O, H…)", "Name construction points (A, B, C, O, H…)"), set.constructNames,
            keys = "nazwy punktów litery podpis wierzchołki ortocentrum środek okręgu labels names points",
        ) { set.constructNames = it }
    }
    Group(T.t("Pędzel formatu", "Format painter"), keys = "malarz kopiuj formatowanie painter") {
        Opt(T.t("Tryb pędzla formatu", "Format painter mode"), "format painter kopiuj formatowanie styl") { Choice(listOf(T.t("Obiektowo", "Object"), "Lasso"), set.painterMode) { set.painterMode = it } }
        ToggleRow(T.t("Szybki przycisk „Pobierz format”", "Quick “Take format” button"), set.painterQuick, keys = "pobierz format pipeta pędzel malarz przycisk pod ikoną szybki") { set.painterQuick = it }
        if (set.painterQuick) {
            Text(
                T.t("Wybranie pędzla pokazuje pod jego ikoną drugi przycisk: dotknij go, a potem obiektu na stronie, by przejąć jego kolor i styl. Drugie dotknięcie pędzla otwiera jego panel.", "Picking the painter shows a second button under its icon: tap it, then an object on the page, to take its colour and style. A second tap on the painter opens its panel."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToggleRow(T.t("Gaś przy pierwszym malowaniu", "Fade when painting starts"), set.painterQuickFadeUse, keys = "pobierz format przycisk gaśnie znika malowanie użycie pędzel") { set.painterQuickFadeUse = it }
            ToggleRow(T.t("Gaś po pobraniu formatu", "Fade after taking a format"), set.painterQuickFadeTake, keys = "pobierz format przycisk gaśnie znika po pobraniu pędzel") { set.painterQuickFadeTake = it }
            Opt(
                if (set.painterQuickSec == 0) T.t("Gaś po czasie: nigdy", "Fade after time: never") else T.t("Gaś po czasie: ${set.painterQuickSec} s", "Fade after time: ${set.painterQuickSec} s"),
                "pobierz format przycisk gaśnie znika czas sekundy opóźnienie pędzel",
            ) { MSlider(set.painterQuickSec.toFloat(), 0f, 30f) { set.painterQuickSec = it.toInt() } }
        }
    }
    Group(T.t("Wypełnienie", "Fill"), keys = "wiaderko wypełnianie fill") {
        ToggleRow(T.t("Wiaderko domyka małe szpary w liniach", "Fill closes small gaps in lines"), set.fillCloseGaps, keys = "wiaderko wypełnianie fill kolor szpary luka") { set.fillCloseGaps = it }
    }
    Group(T.t("Pipeta", "Eyedropper"), keys = "pipeta próbnik kolor paleta eyedropper") {
        ToggleRow(T.t("Pobrany kolor zostaje w palecie", "Picked colour stays in the palette"), set.pipetteKeep, keys = "pipeta próbnik kolor paleta zestaw zostaje znika eyedropper") { set.pipetteKeep = it }
        Text(
            T.t("Dotyczy tylko niepełnej listwy. Wyłączone: pobrany kolor znika z listwy po zmianie koloru.", "Applies only while the strip has room. Off: a picked colour disappears from the strip once you change colour."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    Group(T.t("Gumka i lasso", "Eraser & lasso"), keys = "wymaż zaznaczanie") {
        Opt(T.t("Domyślny tryb gumki", "Default eraser mode"), "eraser wymaż kasowanie pikselowa obiektowa wycinanie lasso nożyczki cut out") { Choice(listOf(T.t("Obiektowa", "Object"), T.t("Pikselowa", "Pixel"), "Lasso", T.t("Wycinanie", "Cut out")), set.eraserMode) { set.eraserMode = it } }
        Opt(T.t("Rozmiar gumki: ${set.eraserSize.toInt()} pt", "Eraser size: ${set.eraserSize.toInt()} pt"), "eraser grubość wymaż") { MSlider(set.eraserSize, 1f, 60f) { set.eraserSize = it } }
        ToggleRow(T.t("Gumka pomija zakreślacz", "Eraser ignores highlighter"), set.eraserKeepHighlighter, keys = "eraser wymaż zakreślacz marker") { set.eraserKeepHighlighter = it }
        Opt(T.t("Kształt lassa", "Lasso shape"), "zaznaczanie zaznacz odręczne prostokąt") { Choice(listOf(T.t("Odręczne", "Freehand"), T.t("Prostokąt", "Rectangle")), if (set.lassoRect) 1 else 0) { set.lassoRect = it == 1 } }
        Opt(T.t("Co zaznacza lasso", "What the lasso selects"), "zaznaczanie pętla zahacz musnąć zbalansowane touch balanced") {
            Choice(listOf(T.t("Połowa w pętli", "Half in the loop"), T.t("Wystarczy dotknąć", "Just touch")), if (set.lassoTouch) 1 else 0) { set.lassoTouch = it == 1 }
        }
        Text(lassoModeHint(set.lassoTouch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        ToggleRow(T.t("Przyciągaj do siatki", "Snap to grid"), set.snapGrid, keys = "grid siatka magnes snap") { set.snapGrid = it }
        // F22 był tylko w „⋮” edytora (przegląd Fable §3.2): rodzeństwo przyciągania do siatki.
        ToggleRow(T.t("Przyczep do figur (końce linii i punkty)", "Attach to shapes (line ends and points)"), set.attach, keys = "przyczep magnes wierzchołek końce punkty figury attach snap vertex") { set.attach = it }
    }
    // Przegląd §3.2a: kolor lasera był tylko na listwie lasera.
    Group(T.t("Laser", "Laser"), keys = "laser wskaźnik pokaz prezentacja pointer") {
        Opt(T.t("Kolor śladu lasera", "Laser trail colour"), "laser wskaźnik kolor ślad czerwony zielony niebieski fioletowy pointer colour trail") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in LaserColors.PALETTE) Dot(Color(0xFF000000.toInt() or c), 34.dp, set.laserColor == c, { set.laserColor = c })
            }
        }
    }
    Group(T.t("Obracanie i kąty", "Rotation & angles"), keys = "$ROTATION_QUERY przyciąganie równe kąty obrót obracanie kątomierz tarcza stopnie rotate snap protractor dial degrees") {
        ToggleRow(T.t("Przyciągaj do równych kątów", "Snap to even angles"), set.rotSnap, keys = "obrót obracanie kąt snap rotate angle") { set.rotSnap = it }
        Opt(T.t("Siła przyciągania: ±${AngleSnap.label(set.rotTolerance)}°", "Snap range: ±${AngleSnap.label(set.rotTolerance)}°"), "tolerancja zasięg strefa tolerance range") {
            MSlider(set.rotTolerance, AngleSnap.MIN_TOLERANCE, AngleSnap.MAX_TOLERANCE) { set.rotTolerance = Math.round(it).toFloat() }
        }
        Extra {
            Text(
                T.t("Przy obracaniu zaznaczenia lassem obiekt zatrzyma się na wybranych kątach, gdy zbliżysz się do nich na odległość podaną wyżej.", "When you rotate a lasso selection, it stops at the chosen angles once you get within the range above."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp),
            )
            AngleSetEditor(set.rotAngles, set.rotTolerance) { set.rotAngles = it }
        }
    }
}

@Composable
private fun InputPrefs(set: com.mdev.mnotes.data.Settings) {
    ProfilesPrefs(set)
    Group {
        Opt(
            T.t("Piszę", "I write with"),
            "ręka lewa prawa leworęczny praworęczny mańkut dłoń zasłania strona paska hand left right handed lefty",
        ) { Choice(listOf(T.t("Prawą ręką", "Right hand"), T.t("Lewą ręką", "Left hand")), set.hand) { set.hand = it } }
        Text(
            T.t("Przestawia na drugą stronę pasek boczny, listwę pióra, kapsułę Cofnij i mini-mapę; lewą ręką — także zapas przewijania.",
                "Moves the side toolbar, pen strip, undo capsule and mini-map to the other side; left hand also turns on extra scrolling."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp),
        )
        Opt(T.t("Palec", "Finger"), "finger dotyk przewijanie powiększanie rysowanie touch") { Choice(listOf(T.t("Przewija i powiększa", "Pans & zooms"), T.t("Rysuje", "Draws"), T.t("Ignorowany", "Ignored")), set.fingerMode) { set.fingerMode = it } }
        ToggleRow(T.t("Odrzucanie dłoni (rysik ma pierwszeństwo)", "Palm rejection (stylus wins)"), set.palmRejection, keys = "palm dłoń rysik stylus przypadkowe dotknięcia") { set.palmRejection = it }
        ToggleRow(T.t("Cofnij dotknięciem dwóch palców", "Two-finger tap to undo"), set.twoFingerUndo, keys = "undo cofanie gest dwa palce") { set.twoFingerUndo = it }
        ToggleRow(T.t("Odciągnij za ostatnią stroną, aby dodać nową", "Pull past the last page to add a new one"), set.pullAddPage, keys = "nowa strona dodaj pull gest koniec zeszytu") { set.pullAddPage = it }
        ToggleRow(T.t("Nowa strona, gdy tekst wyjdzie za ostatnią", "New page when text runs past the last one"), set.autoTextPage, keys = "nowa strona tekst pole automatycznie koniec auto page text") { set.autoTextPage = it }
        ToggleRow(T.t("Grecki i indeksy w tekście (\\alpha → α, x^2 → x², a_1 → a₁)", "Greek and indices in text (\\alpha → α, x^2 → x², a_1 → a₁)"), set.mathText, keys = "grecki litery indeks górny dolny potęga latex alfa unicode greek subscript superscript") { set.mathText = it }
        ToggleRow(T.t("Ponowne dotknięcie lasera, ręki lub lassa wraca do pisania", "Tapping laser, hand or lasso again returns to writing"), set.returnToPen, keys = "odznacz narzędzie powrót pióro pisanie laser ręka lasso pusty obszar return pen") { set.returnToPen = it }
        // Dopowiedzenie znika razem z wierszem, gdy wyszukiwanie go nie pokazuje (przegląd §3.2 F26).
        if (rowVisible(T.t("Ponowne dotknięcie lasera, ręki lub lassa wraca do pisania", "Tapping laser, hand or lasso again returns to writing"), "odznacz narzędzie powrót pióro pisanie laser ręka lasso pusty obszar return pen")) {
            Text(T.t("Także dotknięcie pustego miejsca tym narzędziem (bez zaznaczenia).", "Also a tap on an empty spot with that tool (with nothing selected)."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        val tapActions = listOf(T.t("Cofnij", "Undo"), T.t("Ponów", "Redo"), T.t("Przybliż / oddal", "Zoom in / out"), T.t("Brak", "None"))
        Opt(T.t("Podwójne dotknięcie palcem", "Finger double tap"), "double tap gest dwukrotne") { Choice(tapActions, set.doubleTap) { set.doubleTap = it } }
        Opt(T.t("Potrójne dotknięcie palcem", "Finger triple tap"), "triple tap gest trzykrotne") { Choice(tapActions, set.tripleTap) { set.tripleTap = it } }
        if (set.tripleTap != 3) Extra {
            Text(
                T.t("Gdy potrójne dotknięcie coś robi, podwójne czeka ułamek sekundy na trzecie dotknięcie. Ustaw „Brak”, aby podwójne działało od razu.",
                    "While triple tap does something, double tap waits a fraction of a second for the third tap. Set it to “None” to make double tap instant."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Opt(T.t("Przycisk rysika", "Stylus button"), "stylus pen rysik s-pen przycisk boczny gumka lasso laser cofnij") {
            Choice(listOf(T.t("Gumka", "Eraser"), "Lasso", T.t("Cofnij", "Undo"), T.t("Laser", "Laser"), T.t("Brak", "None")), set.penButton) { set.penButton = it }
        }
    }
    Group(T.t("Mysz i klawiatura (DeX / Linux)", "Mouse & keyboard (DeX / Linux)"), keys = "kółko przewijanie powiększanie prawy przycisk menu kontekstowe paleta poleceń ctrl+k wheel context palette") { Extra {
        Text(
            T.t(
                "Kółko przewija, Ctrl+kółko powiększa, prawy przycisk otwiera menu kontekstowe. Skróty klawiszowe są wymienione w zakładce „Skróty klawiszowe”, a Ctrl+K otwiera paletę poleceń.",
                "Wheel scrolls, Ctrl+wheel zooms, right-click opens the context menu. Keyboard shortcuts are listed in the “Keyboard shortcuts” tab, and Ctrl+K opens the command palette.",
            ),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
        )
    } }
}

/**
 * Profile pracy (2b-A12): lista z przełączaniem, nowy profil z obecnych ustawień, edycja sekcji, eksport i import pliku.
 * Profil zapamiętuje wybrane sekcje ustawień takie, jakie są w chwili zapisu — żeby zmienić np. pasek w profilu,
 * włącza się profil, ustawia pasek i „Zapisz obecne”.
 */
@Composable
private fun ProfilesPrefs(set: com.mdev.mnotes.data.Settings) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<com.mdev.mnotes.core.prefs.Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf<String?>(null) }
    val list = set.profiles
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(com.mdev.mnotes.core.prefs.Profiles.encode(set.profiles).toByteArray()) } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val txt = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching
            val got = com.mdev.mnotes.core.prefs.Profiles.decode(txt)
            set.profiles = com.mdev.mnotes.core.prefs.Profiles.merge(set.profiles, got)
            android.widget.Toast.makeText(ctx, T.t("Zaimportowano profile: ", "Profiles imported: ") + got.size, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    Group(T.t("Profile pracy", "Work profiles"), keys = "profil profile tryb tryby wykład rysowanie rysik dotyk palec przełączanie custom modes lecture drawing stylus touch") {
        Extra {
            Text(
                T.t("Profil przełącza naraz dotyk palcem, gesty, pasek narzędzi i widok. Przełączysz go w edytorze: „⋮” → Profil pracy albo paletą poleceń. Wyłączenie profilu przywraca ustawienia sprzed niego.",
                    "A profile switches finger touch, gestures, toolbar and view at once. Switch it in the editor: “⋮” → Work profile or from the command palette. Turning a profile off restores the settings from before it."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        for (p in list) Extra {
            val on = set.profileActive == p.id
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), color = if (on) mint.accentText else MaterialTheme.colorScheme.onSurface)
                    Text(profileSummary(p), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(if (on) T.t("Włączony — wyłącz", "On — turn off") else T.t("Włącz", "Turn on"), on, { set.switchProfile(if (on) null else p) })
                    Chip(T.t("Edytuj…", "Edit…"), false, { editing = p })
                    Chip(T.t("Zapisz obecne", "Save current"), false, {
                        set.profiles = set.profiles.map { if (it.id == p.id) com.mdev.mnotes.core.prefs.Profiles.capture(p.id, p.name, p.values.keys, set::prefValue) else it }
                    })
                    Chip(if (confirmDel == p.id) T.t("Usunąć?", "Delete?") else T.t("Usuń", "Delete"), confirmDel == p.id, {
                        if (confirmDel != p.id) confirmDel = p.id
                        else {
                            if (on) set.switchProfile(null)
                            set.profiles = set.profiles.filter { it.id != p.id }
                            confirmDel = null
                        }
                    })
                }
            }
        }
        Extra {
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(T.t("Nowy z obecnych ustawień…", "New from current settings…"), false, { creating = true })
                Chip(T.t("Eksportuj profile", "Export profiles"), false, { exportLauncher.launch("mnotes-profile.txt") })
                Chip(T.t("Importuj", "Import"), false, { importLauncher.launch(arrayOf("text/plain", "*/*")) })
            }
        }
    }
    if (creating || editing != null) ProfileDialog(set, editing) { creating = false; editing = null }
}

/** Krótki opis profilu: dotyk i liczba sekcji. */
private fun profileSummary(p: com.mdev.mnotes.core.prefs.Profile): String {
    val P = com.mdev.mnotes.core.prefs.Profiles
    val finger = when ((p.values[P.FINGER] as? com.mdev.mnotes.core.prefs.PVal.I)?.v) {
        P.FINGER_SCROLL -> T.t("palec przewija", "finger scrolls")
        P.FINGER_DRAW -> T.t("palec rysuje", "finger draws")
        P.FINGER_OFF -> T.t("palec wyłączony", "finger off")
        else -> T.t("dotyk bez zmian", "touch unchanged")
    }
    val groups = P.GROUPS.indices.count { g -> P.keysOf(g).any { it in p.values } }
    return finger + " · " + T.t("sekcje: ", "sections: ") + groups
}

/** Nowy profil (z obecnych ustawień) albo edycja: nazwa, dotyk palcem i sekcje, które profil ma przełączać. */
@Composable
private fun ProfileDialog(set: com.mdev.mnotes.data.Settings, edit: com.mdev.mnotes.core.prefs.Profile?, close: () -> Unit) {
    val P = com.mdev.mnotes.core.prefs.Profiles
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var finger by remember { mutableStateOf((edit?.values?.get(P.FINGER) as? com.mdev.mnotes.core.prefs.PVal.I)?.v ?: set.fingerMode) }
    val groups = remember { androidx.compose.runtime.mutableStateListOf<Int>().apply { addAll(P.GROUPS.indices.filter { g -> if (edit == null) g <= 2 else P.keysOf(g).any { it in edit.values } }) } }
    MDialog(
        if (edit == null) T.t("Nowy profil pracy", "New work profile") else T.t("Profil pracy", "Work profile"), close,
        confirm = T.t("Zapisz", "Save"), confirmEnabled = name.isNotBlank(),
        onConfirm = {
            val keys = groups.sorted().flatMap { P.keysOf(it) }
            // Sekcje już w profilu zachowują jego wartości; nowe biorą obecne ustawienia.
            val vals = LinkedHashMap<String, com.mdev.mnotes.core.prefs.PVal>()
            for (k in keys) (edit?.values?.get(k) ?: set.prefValue(k))?.let { vals[k] = it }
            if (0 in groups) vals[P.FINGER] = com.mdev.mnotes.core.prefs.PVal.I(finger)
            val id = edit?.id ?: P.newId(set.profiles.map { it.id })
            val p = com.mdev.mnotes.core.prefs.Profile(id, name.trim(), vals)
            set.profiles = if (edit == null) set.profiles + p else set.profiles.map { if (it.id == id) p else it }
            // Edycja włączonego profilu działa od razu (powrót do stanu sprzed i nałożenie nowej wersji).
            if (set.profileActive == id) set.switchProfile(p)
            close()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            MField(name, { name = it }, hint = T.t("Nazwa, np. Wykład", "Name, e.g. Lecture"))
            SectionLabel(T.t("Dotyk palcem", "Finger touch"))
            Choice(listOf(T.t("Przewija i powiększa", "Pans & zooms"), T.t("Rysuje", "Draws"), T.t("Wyłączony", "Off")), finger) { finger = it; if (0 !in groups) groups.add(0) }
            SectionLabel(T.t("Profil przełącza", "The profile switches"))
            P.GROUPS.forEachIndexed { g, (pl, en) ->
                ToggleRow(T.t(pl, en), g in groups) { if (it) groups.add(g) else groups.remove(g) }
            }
            Text(
                T.t("Nowe sekcje zapamiętują ustawienia takie, jakie są teraz. Żeby np. zmienić widoczne narzędzia w profilu: włącz profil, ustaw pasek, a potem „Zapisz obecne”.",
                    "New sections remember the settings as they are now. To change e.g. the visible tools in a profile: turn the profile on, set up the toolbar, then “Save current”."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun NewNotePrefs(set: com.mdev.mnotes.data.Settings) {
    Group(keys = "nowe notatki nowy zeszyt domyślne ustawienia new notes notebook defaults") {
        Opt(T.t("Rozmiar strony", "Page size"), "format a3 a4 a5 a6 letter strona papier size") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (f in PageFormat.entries) Chip(formatName(f), f.id == set.newFormat, { set.newFormat = f.id })
            }
        }
        ToggleRow(T.t("Orientacja pozioma", "Landscape"), set.newLandscape, keys = "poziom pion landscape portrait orientacja strona") { set.newLandscape = it }
        Opt(T.t("Wzór", "Template"), "template kratka linie kropki czysta pusta tło siatka szablon lined dots grid blank") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in Template.entries) Chip(templateName(t), t.code == set.newTemplate, { set.newTemplate = t.code })
            }
        }
        Opt(T.t("Papier", "Paper"), "kolor tło kremowy biały barwa strony colour") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (p in Paper.presets) Dot(Color(p), 32.dp, p == set.newPaper, { set.newPaper = p })
            }
        }
    }
}

@Composable
private fun PrivacyPrefs(set: com.mdev.mnotes.data.Settings) {
    val ctx = LocalContext.current
    Group(T.t("Prywatność", "Privacy"), keys = "offline internet dane telemetria śledzenie") {
        Extra {
            Text(
                T.t("mNotes działa w całości offline: aplikacja nie ma uprawnienia INTERNET i nie zbiera żadnych danych.", "mNotes is fully offline: the app has no INTERNET permission and collects no data."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        ToggleRow(T.t("Blokada aplikacji (biometria lub PIN urządzenia)", "App lock (biometrics or device PIN)"), set.appLock, keys = "pin biometria odcisk palca lock hasło blokada bezpieczeństwo") { on ->
            if (!on) set.appLock = false
            else if ((ctx as? androidx.fragment.app.FragmentActivity)?.let { com.mdev.mnotes.app.MainActivity.canLock(it) } == true) set.appLock = true
            else android.widget.Toast.makeText(ctx, T.t("Najpierw ustaw blokadę ekranu w systemie.", "Set a screen lock in system settings first."), android.widget.Toast.LENGTH_LONG).show()
        }
        ToggleRow(T.t("Blokuj zrzuty ekranu i podgląd w ostatnich", "Block screenshots and recents preview"), set.secureFlag, keys = "zrzut ekranu screenshot ostatnie aplikacje podgląd bezpieczeństwo") { set.secureFlag = it }
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
    Group(T.t("Ustawienia jako plik", "Settings as a file"), keys = "eksport import json kopia zapasowa backup przenieś export") { Extra {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(T.t("Eksportuj ustawienia (JSON)", "Export settings (JSON)"), false, { exportLauncher.launch("mnotes-settings.json") })
            Chip(T.t("Importuj", "Import"), false, { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
        }
    } }
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

    Group(T.t("Folder synchronizacji", "Sync folder"), keys = "synchronizacja sync syncthing nextcloud rsync kopia zapasowa backup chmura dysk") { Extra {
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
        } }
        if (linked) {
            Extra { Text(
                T.t("Folder: ", "Folder: ") + (name ?: "…"),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp),
            ) }
            if (!access) Extra { Text(
                T.t("Brak dostępu do folderu — wybierz go ponownie.", "No access to the folder — choose it again."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp),
            ) }
            ToggleRow(T.t("Dołącz PDF", "Include PDF"), set.syncPdf, keys = "eksport pdf") { set.syncPdf = it }
            ToggleRow(T.t("Dołącz Markdown (tylko pola tekstowe)", "Include Markdown (text boxes only)"), set.syncMd, keys = "eksport md tekst") { set.syncMd = it }
            ToggleRow(T.t("Kopiuj automatycznie po wyjściu z aplikacji", "Copy automatically when leaving the app"), set.syncAuto, keys = "automatycznie wyjście zamknięcie kopia zapasowa") { set.syncAuto = it }
            Extra { Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(
                    if (sync.running) T.t("Synchronizuję…", "Syncing…") else T.t("Synchronizuj teraz", "Sync now"), false,
                    { if (access) sync.start() },
                )
                if (sync.running && sync.total > 0) Text("${sync.done} / ${sync.total}", style = MaterialTheme.typography.labelLarge)
            } }
            val r = sync.report
            val line = when {
                r != null -> T.t("Ostatnio: ", "Last: ") + whenText((r.at / 60000L).toInt()) + T.t(
                    " · zapisano ${r.written}, bez zmian ${r.unchanged}, zablokowane ${r.locked}, błędy ${r.failed}",
                    " · written ${r.written}, unchanged ${r.unchanged}, locked ${r.locked}, errors ${r.failed}",
                )
                set.lastSync > 0 -> T.t("Ostatnia udana synchronizacja: ", "Last successful sync: ") + whenText(set.lastSync)
                else -> ""
            }
            if (line.isNotEmpty()) Extra { Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    Extra {
        Text(
            T.t("Kliknij polecenie, aby zmienić jego skrót. Te same polecenia są w palecie (Ctrl+K) i na pasku menu.", "Click a command to change its shortcut. The same commands appear in the palette (Ctrl+K) and the menu bar."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    for (g in 0 until CmdDefs.groups.size) {
        Group(CmdDefs.groupName(g)) {
            // Filtr przed pętlą, żeby między ukrytymi wierszami nie zostały osamotnione kreski.
            CmdDefs.list.filter { it.group == g && rowVisible(it.title, keys[it.id] ?: it.key) }.forEachIndexed { i, d ->
                val key = keys[d.id] ?: d.key
                if (i > 0) HorizontalDivider(color = m.line.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { editing = d }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rowLabel(d.title), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    KeyCap(key.ifEmpty { T.t("brak", "none") }, accent = keys.containsKey(d.id))
                }
            }
        }
    }
    if (keys.isNotEmpty()) Extra { Chip(T.t("Przywróć wszystkie domyślne", "Restore all defaults"), false, { Keymap.resetAll(set) }, Modifier.padding(vertical = 8.dp)) }
    for ((group, rows) in FIXED_SHORTCUTS) {
        Group(group) {
            rows.filter { (k, d) -> rowVisible(d, k) }.forEachIndexed { i, (k, d) ->
                if (i > 0) HorizontalDivider(color = m.line.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rowLabel(d), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
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
