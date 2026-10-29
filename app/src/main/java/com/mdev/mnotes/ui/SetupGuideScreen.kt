package com.mdev.mnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdev.mnotes.core.guide.SetupGuide
import com.mdev.mnotes.core.guide.SetupGuide.State
import com.mdev.mnotes.core.guide.SetupScenes
import com.mdev.mnotes.data.Settings
import com.mdev.mnotes.editor.T

/** Otwiera przewodnik po ustawieniach (biblioteka, Ustawienia); dostarcza go `MainActivity`. */
val LocalOpenGuide = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Propozycja przewodnika dla starej instalacji albo przerwanego startu: jedna karta na dole biblioteki,
 * „Nie teraz” chowa ją na dobre (przewodnik zostaje w menu).
 */
@Composable
fun SetupBanner(set: Settings, onStart: () -> Unit, modifier: Modifier = Modifier) {
    if (SetupGuide.entry(set.setupState, set.oldInstall, set.setupDismissed) != SetupGuide.Entry.SUGGEST) return
    val m = mint
    val resume = set.setupState.isNotEmpty()
    Row(
        modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, m.line, RoundedCornerShape(14.dp)).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                if (resume) T.t("Dokończ przewodnik po ustawieniach", "Finish the settings guide") else T.t("Nowy przewodnik po ustawieniach", "New settings guide"),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(T.t("Kilka pytań, po jednym na stronę — ok. 2 minuty.", "A few questions, one per page — about 2 minutes."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton({ set.setupDismissed = true }, Modifier.heightIn(min = 48.dp)) { Text(T.t("Nie teraz", "Not now")) }
        Button(onStart, Modifier.heightIn(min = 48.dp)) { Text(if (resume) T.t("Dokończ", "Continue") else T.t("Zacznij", "Start")) }
    }
}

/** Tekst strony w bieżącym języku. */
private val SetupGuide.Page.title get() = T.t(titlePl, titleEn)
private val SetupGuide.Page.body get() = T.t(bodyPl, bodyEn)

/**
 * 3c-A10: przewodnik po ustawieniach (projekt: docs/features/PRZEWODNIK-USTAWIEN.md). Jedna strona = jedna decyzja;
 * wybór zapisuje się od razu jako zwykłe ustawienie, a stan (ścieżka, strona) — przy każdym kroku, więc przerwany
 * przewodnik wraca w to samo miejsce.
 */
@Composable
fun SetupGuideScreen(set: Settings, onClose: () -> Unit) {
    val store = remember(set) { SetupStore(set) }
    var st by remember { mutableStateOf(State.decode(set.setupState)) }
    // Kierunek animacji: dalej — z prawej, wstecz — z lewej.
    var forward by remember { mutableStateOf(true) }
    fun go(n: State, fwd: Boolean = true) { forward = fwd; st = n; set.setupState = n.encode() }
    // Zapis przy otwarciu: przewodnik pokazany raz nie wyskoczy znowu w całości (tylko propozycja dokończenia).
    LaunchedEffect(Unit) { set.setupState = st.encode() }
    BackHandler { if (!st.first) go(st.back(), false) else if (st.path != SetupGuide.START && st.path != SetupGuide.HUB) go(st.skip(), false) else onClose() }

    val m = mint
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        // Górny pasek: postęp i zamknięcie (zawsze dostępne — przewodnik niczego nie wymusza, wraca się w to samo miejsce).
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Na pierwszej stronie zamiast „Pierwsze kroki” (tytuł i tak wita) — język: kto nie czyta po polsku, nie musi
            // szukać go w Ustawieniach (3c-A15).
            if (st.path == SetupGuide.START && st.first) Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { LanguageSwitch(set) }
            else Text(
                when (st.path) {
                    SetupGuide.START -> T.t("Pierwsze kroki", "Getting started")
                    SetupGuide.HUB -> T.t("Poznaj więcej", "Learn more")
                    else -> SetupGuide.TOPICS.first { it.id == st.path }.let { T.t(it.titlePl, it.titleEn) }
                },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            if (st.path != SetupGuide.HUB) Dots(st.count, st.index)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                IconBtn(Icons.Rounded.Close, T.t("Zamknij — wrócisz tu później z menu", "Close — come back later from the menu"), onClose)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Dwie kolumny tylko w poziomie: tablet pionowo i telefon mają próbkę nad tekstem.
            val wide = maxWidth > maxHeight && maxWidth >= 560.dp
            AnimatedContent(
                targetState = st, label = "setup",
                contentKey = { it.path to it.index },
                transitionSpec = {
                    val d = if (forward) 1 else -1
                    (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { d * it / 12 }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { -d * it / 12 })
                },
            ) { s ->
                val page = s.page
                if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.widthIn(max = 480.dp).weight(1f).verticalScroll(rememberScrollState())) {
                        PageText(page)
                        Spacer(Modifier.size(20.dp))
                        Choices(s, page, store) { go(it) }
                    }
                    Box(Modifier.weight(1.15f).fillMaxSize(), contentAlignment = Alignment.Center) { Sample(page, store, set) }
                } else Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.widthIn(max = 420.dp).fillMaxWidth()) { Sample(page, store, set) }
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                        PageText(page)
                        Spacer(Modifier.size(16.dp))
                        Choices(s, page, store) { go(it) }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(m.line))
        NavBar(st, ::go, onClose)
    }
}

/** Przełącznik polski/angielski: drugi język podpisany w nim samym, bo właśnie go ktoś szuka. */
@Composable
private fun LanguageSwitch(set: Settings) {
    val other = if (T.pl) "English" else "Polski"
    TextButton(
        { set.lang = if (T.pl) 2 else 1 },
        // Wcięcie przycisku zjada odstęp paska — tekst ma stać tam, gdzie na innych stronach nazwa ścieżki.
        Modifier.offset(x = (-8).dp).heightIn(min = 48.dp).semantics { contentDescription = T.t("Język: polski. Zmień na angielski", "Language: English. Switch to Polish") },
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(Icons.Rounded.Language, null, Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(other)
    }
}

@Composable
private fun Dots(count: Int, index: Int) {
    val m = mint
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = T.t("Strona ${index + 1} z $count", "Page ${index + 1} of $count") },
    ) {
        for (i in 0 until count) {
            val c by animateColorAsState(if (i <= index) m.accent else m.line, tween(200), label = "dot")
            // Bieżąca strona to wydłużona kreska — odróżnia się od przebytych nie tylko rozmiarem.
            val w by animateDpAsState(if (i == index) 20.dp else 8.dp, tween(200), label = "dotw")
            Box(Modifier.width(w).height(8.dp).clip(CircleShape).background(c))
        }
    }
}

@Composable
private fun PageText(page: SetupGuide.Page) {
    Text(page.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp))
    Spacer(Modifier.size(8.dp))
    Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Opcje strony albo — na stronie „Gotowe” — tematy „Poznaj więcej” (kolejność pod zastosowanie). */
@Composable
private fun Choices(st: State, page: SetupGuide.Page, store: SetupGuide.Store, go: (State) -> Unit) {
    if (page.sample == SetupGuide.Sample.DONE) {
        Text(T.t("Poznaj więcej", "Learn more"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in SetupGuide.topics(store)) Card(
                T.t(t.titlePl, t.titleEn), T.t(t.descPl, t.descEn), selected = false, recommended = false, done = t.id in st.seen, nav = true,
            ) { go((if (st.path == SetupGuide.START) st.next() else st).open(t.id)) }
        }
        return
    }
    val cur = SetupGuide.current(page, store)
    val rec = page.recommended(store)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        page.options.forEachIndexed { i, o ->
            Card(T.t(o.pl, o.en), T.t(o.notePl, o.noteEn), selected = i == cur, recommended = i == rec, done = false) {
                SetupGuide.choose(page, i, store)
            }
        }
        // Ustawienie spoza listy (np. AMOLED, inny przycisk rysika) — mówimy o tym, zamiast udawać, że nic nie wybrano.
        if (cur < 0) Text(
            T.t("Teraz ustawione inaczej (w Ustawieniach) — wybierz, aby zmienić.", "Currently set differently (in Settings) — pick one to change it."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Card(title: String, note: String, selected: Boolean, recommended: Boolean, done: Boolean, nav: Boolean = false, onClick: () -> Unit) {
    val m = mint
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) m.accentSoft else cs.surface, tween(200), label = "card")
    val edge by animateColorAsState(if (selected) m.accent else m.line, tween(200), label = "edge")
    val shape = RoundedCornerShape(12.dp)
    val seen = T.t("przejrzane", "viewed")
    val base = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape).background(bg).border(if (selected) 2.dp else 1.dp, edge, shape)
    Row(
        (if (nav) base.clickable(role = Role.Button, onClick = onClick).semantics { if (done) stateDescription = seen }
        else base.selectable(selected, role = Role.RadioButton, onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Opcja ma znacznik jak przełącznik radiowy; temat to przejście dalej (strzałka), bez znacznika z przodu.
        if (!nav) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(if (selected) m.accent else Color.Transparent)
                    .border(2.dp, if (selected) m.accent else m.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Icon(Icons.Rounded.Check, null, tint = m.onAccent, modifier = Modifier.size(14.dp)) }
            Spacer(Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        if (recommended) {
            Spacer(Modifier.size(8.dp))
            // Obrys, nie tło: na zaznaczonej karcie tło pigułki zlewałoby się z kartą.
            Text(
                T.t("Zalecane", "Recommended"), style = MaterialTheme.typography.labelMedium, color = m.accentText,
                modifier = Modifier.border(1.dp, m.accent, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        if (nav) {
            if (done) Icon(Icons.Rounded.Check, null, tint = m.accentText, modifier = Modifier.size(20.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun NavBar(st: State, go: (State, Boolean) -> Unit, onClose: () -> Unit) {
    val page = st.page
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!st.first) TextButton({ go(st.back(), false) }, Modifier.heightIn(min = 48.dp)) { Text(T.t("Wstecz", "Back")) }
        Spacer(Modifier.weight(1f))
        when {
            st.path == SetupGuide.HUB -> Button(onClose, Modifier.heightIn(min = 48.dp)) { Text(T.t("Zacznij pisać", "Start writing")) }
            page.sample == SetupGuide.Sample.DONE -> Button({ go(st.next(), true); onClose() }, Modifier.heightIn(min = 48.dp)) { Text(T.t("Zacznij pisać", "Start writing")) }
            else -> {
                // Na ostatniej stronie tematu „Wróć do tematów” robiłoby to samo co „Gotowe”.
                if (!(st.last && st.path != SetupGuide.START)) {
                    TextButton({ go(st.skip(), true) }, Modifier.heightIn(min = 48.dp)) {
                        Text(if (st.path == SetupGuide.START) T.t("Pomiń przewodnik", "Skip guide") else T.t("Wróć do tematów", "Back to topics"))
                    }
                    Spacer(Modifier.size(8.dp))
                }
                Button({ go(st.next(), true) }, Modifier.heightIn(min = 48.dp)) {
                    Text(if (st.last) T.t("Gotowe", "Done") else T.t("Dalej", "Next"))
                }
            }
        }
    }
}

/**
 * Próbka strony: scena z [SetupScenes] w kolorach motywu, a na stronie „Gotowe” — podsumowanie wyborów. Na stronie
 * „Rysik i palec” to żywe pole: rysik zawsze pisze, palec pisze, przesuwa kartkę albo nic — według bieżącego wyboru.
 */
@Composable
private fun Sample(page: SetupGuide.Page, store: SetupGuide.Store, set: Settings) {
    if (page.sample == SetupGuide.Sample.DONE) { Summary(store); return }
    val m = mint
    val accent = m.accent.toArgb()
    val shapes = SetupScenes.of(page, store)
    val tm = rememberTextMeasurer()
    val live = page.sample == SetupGuide.Sample.INPUT
    val lines = remember(page.id) { mutableStateListOf<List<Offset>>() }
    var pan by remember(page.id) { mutableStateOf(0f) }
    val finger = set.fingerMode
    // Bez fillMaxWidth: proporcje wybierają większy rozmiar, który mieści się i w szerokości, i w wysokości.
    var pad = Modifier.aspectRatio(SetupScenes.W / SetupScenes.H).clipToBounds()
    if (live) pad = pad.pointerInput(finger) {
        val k = size.width / SetupScenes.W
        awaitEachGesture {
            val down = awaitFirstDown()
            val pen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
            val draws = pen || finger == 1
            val scrolls = !pen && finger == 0
            if (!draws && !scrolls) return@awaitEachGesture
            // Punkty w układzie kartki (bez przesunięcia), żeby pismo jechało razem z nią.
            val pts = ArrayList<Offset>().apply { add(down.position / k - Offset(0f, pan)) }
            if (draws) lines += pts.toList()
            var last = down.position
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                c.consume()
                if (draws) { pts += c.position / k - Offset(0f, pan); lines[lines.size - 1] = pts.toList() }
                else pan = (pan + (c.position.y - last.y) / k).coerceIn(-40f, 40f)
                last = c.position
            }
        }
    }
    Box {
        Canvas(pad.semantics { contentDescription = T.t("Podgląd: ${page.title}", "Preview: ${page.title}") }) {
            val k = size.width / SetupScenes.W
            fun col(r: SetupScenes.Role) = Color(SetupScenes.color(r, m.dark, accent))
            if (!live) { drawScene(shapes, k, ::col, tm); return@Canvas }
            // Ramka stoi, przesuwa się tylko kartka z pismem — przycięta do wnętrza ramki.
            drawScene(shapes.take(SetupScenes.FRAME_SHAPES), k, ::col, tm)
            val i = SetupScenes.INSET * k
            clipRect(i, i, size.width - i, size.height - i) {
                translate(0f, pan * k) {
                    drawScene(shapes.drop(SetupScenes.FRAME_SHAPES), k, ::col, tm)
                    for (l in lines) if (l.size > 1) {
                        val p = Path().apply { moveTo(l[0].x * k, l[0].y * k); for (q in l.drop(1)) lineTo(q.x * k, q.y * k) }
                        drawPath(p, col(SetupScenes.Role.ACCENT), style = Stroke(2.4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
        if (live && (lines.isNotEmpty() || pan != 0f)) TextButton({ lines.clear(); pan = 0f }, Modifier.align(Alignment.TopEnd).padding(14.dp)) { Text(T.t("Wyczyść", "Clear")) }
    }
}

/** Strona „Gotowe”: co zostało wybrane na starcie, w jednym rzucie oka. */
@Composable
private fun Summary(store: SetupGuide.Store) {
    val m = mint
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(cs.surface).border(1.dp, m.line, shape).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(T.t("Twoje wybory", "Your choices"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
        for ((p, i) in SetupGuide.summary(store)) Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            val (pl, en) = p.short!!
            Text(T.t(pl, en), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(96.dp))
            Text(
                p.options.getOrNull(i)?.let { T.t(it.pl, it.en) } ?: T.t("ustawione w Ustawieniach", "set in Settings"),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f),
            )
        }
        Text(T.t("Wszystko zmienisz w Ustawieniach.", "You can change all of it in Settings."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun DrawScope.drawScene(shapes: List<SetupScenes.Shape>, k: Float, col: (SetupScenes.Role) -> Color, tm: TextMeasurer) {
    for (s in shapes) {
        val c = col(s.role)
        when (s) {
            is SetupScenes.Box -> {
                val tl = Offset(s.l * k, s.t * k); val sz = Size((s.r - s.l) * k, (s.b - s.t) * k); val cr = CornerRadius(s.radius * k)
                if (s.outline) drawRoundRect(c, tl, sz, cr, style = Stroke(1.2f * k)) else drawRoundRect(c, tl, sz, cr)
            }
            is SetupScenes.Dot ->
                if (s.outline) drawCircle(c, s.rad * k, Offset(s.cx * k, s.cy * k), style = Stroke(1.4f * k)) else drawCircle(c, s.rad * k, Offset(s.cx * k, s.cy * k))
            is SetupScenes.Line -> {
                val p = Path(); p.moveTo(s.pts[0] * k, s.pts[1] * k)
                var i = 2; while (i + 1 < s.pts.size) { p.lineTo(s.pts[i] * k, s.pts[i + 1] * k); i += 2 }
                drawPath(p, c, style = Stroke(s.width * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            is SetupScenes.Label -> {
                val r = tm.measure(T.t(s.pl, s.en), TextStyle(fontSize = (s.size * k / (density * fontScale)).sp, color = c, fontWeight = FontWeight.Medium))
                drawText(r, topLeft = Offset(s.x * k, s.y * k - r.firstBaseline))
            }
        }
    }
}
