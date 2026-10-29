package com.mdev.mnotes.lab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.mdev.mnotes.app.LabCommand
import com.mdev.mnotes.app.LabFeatures
import com.mdev.mnotes.core.image.BoardEdits
import com.mdev.mnotes.core.image.BoardGeometry
import com.mdev.mnotes.core.image.BoardOptions
import com.mdev.mnotes.core.image.BoardPipeline
import com.mdev.mnotes.core.image.BoardSource
import com.mdev.mnotes.core.image.ChalkInk
import com.mdev.mnotes.core.image.ChalkStrokes
import com.mdev.mnotes.core.image.DecodePlan
import com.mdev.mnotes.core.image.Quad
import com.mdev.mnotes.core.image.Raster
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.ui.Choice
import com.mdev.mnotes.ui.Group
import com.mdev.mnotes.ui.MDialog
import com.mdev.mnotes.ui.MSlider
import com.mdev.mnotes.ui.SectionLabel
import com.mdev.mnotes.ui.ToggleRow
import com.mdev.mnotes.ui.mint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Kreda i „Wyślij do mLabs” (gałąź `kreda`) widziane przez wspólny kod przez [LabFeatures]; main ładuje ten obiekt po
 * nazwie (`Extensions.KREDA`), więc zmiana nazwy lub pakietu wymaga zmiany tam.
 */
object Lab : LabFeatures {

    override val board = true

    /** Rozmiar podglądu w panelu: szybki na tablecie, wystarczy do oceny progu. */
    private const val PREVIEW = 520

    /** Kolor kresek z wektoryzacji, gdy kreda zostaje w oryginalnych barwach (grafit czytelny na jasnej stronie). */
    private const val GRAPHITE = 0xFF37474F.toInt()

    /**
     * Ciężka praca kredy (potok na 2400 px, mediana, wektoryzacja, sprawdzenie wstawionego zdjęcia) na wspólnej puli
     * obliczeń, a nie na jednowątkowym `repo.io`, które w tym czasie musi móc zapisywać zeszyt.
     */
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // lazy: obiekt ładuje się po nazwie także w testach JVM, gdzie Looper to atrapa
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Tryby podglądu: wynik, ręczne narożniki tablicy, gumka wielokątna. */
    private const val SHOW_RESULT = 0
    private const val SHOW_CORNERS = 1
    private const val SHOW_ERASER = 2

    @Composable
    override fun BoardPanel(ed: Editor, imgs: List<ImageEl>, close: () -> Unit) {
        val set = LabSettings.of(LocalContext.current)
        val files = remember(imgs.map { it.asset }) { imgs.map { ed.repo.assetFile(ed.nbId, it.asset) } }
        val many = imgs.size > 1
        // 2c-B4: kolor kreski wg papieru strony, na której leży zdjęcie (biała kreda na białej kartce znikała)
        var opt by remember { mutableStateOf(set.options().copy(paper = paperOf(ed, imgs[0]))) }
        var asStrokes by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf(SHOW_RESULT) }
        // Narożniki (ułamki zdjęcia, null = zgadnięte) i wielokąty gumki (ułamki wyniku: ten sam kształt na podglądzie i
        // w pełnym rozmiarze). Zmiana narożników, prostowania albo kadru zeruje gumkę (2c-B5, [BoardEdits]).
        var edits by remember { mutableStateOf(BoardEdits(opt.straighten, opt.crop)) }
        val corners = edits.corners
        val erase = edits.erase
        var prep by remember { mutableStateOf<BoardSource?>(null) }
        var preview by remember { mutableStateOf<Bitmap?>(null) }
        // 3c-B3 (K8): po zmianie geometrii podgląd pokazuje stary wynik, dopóki nowy się nie policzy — pętla gumki
        // narysowana na nim trafiłaby w inne miejsce nowego wyniku.
        var stale by remember { mutableStateOf(false) }
        var info by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var more by remember { mutableStateOf(false) }
        val sources by produceState<List<Raster>?>(null, files) {
            value = withContext(Dispatchers.IO) { files.map { loadRaster(it, PREVIEW) ?: return@withContext null } }
        }
        val photo = remember(sources) { sources?.firstOrNull()?.toBitmap() }

        // Kroki geometrii i filtrów są drogie: liczą się od nowa tylko po zmianie przełączników kroków albo narożników,
        // i dopiero po 60 ms ciszy (szybkie klikanie nie ustawia w kolejce kilku przeliczeń).
        LaunchedEffect(sources, listOf(opt.straighten, opt.crop, opt.flatten, opt.sharpen), corners?.toList()) {
            val src = sources ?: return@LaunchedEffect
            stale = true
            delay(60)
            val small = opt.copy(maxSide = PREVIEW)
            prep = withContext(Dispatchers.Default) {
                if (many) BoardPipeline.prepareMany(src.size, small) { src[it] }
                else BoardPipeline.prepare(src[0], small, corners?.let { quadOf(it, src[0]) })
            }
        }
        // Próg, krycie, kolor i gumka są tanie: przy każdym ruchu suwaka tylko kolor do alfy na gotowym źródle.
        LaunchedEffect(prep, opt, erase) {
            val p = prep ?: return@LaunchedEffect
            val res = withContext(Dispatchers.Default) { BoardPipeline.finish(p, opt, scaled(erase, p.image)) }
            preview = res.image.toBitmap()
            stale = false
            val skipped = p.skipped.map { it + 1 }
            info = T.t(
                "Tło ${if (res.black) "ciemne" else "jasne"} · próg ${"%.2f".format(res.cut)}" + (if (many) " · mediana ${imgs.size - skipped.size} zdjęć" else "") +
                    (if (skipped.isNotEmpty()) " · bez tablicy, pominięte: ${skipped.joinToString()}" else ""),
                "${if (res.black) "Dark" else "Light"} background · cut ${"%.2f".format(res.cut)}" + (if (many) " · median of ${imgs.size - skipped.size} photos" else "") +
                    (if (skipped.isNotEmpty()) " · no board found, skipped: ${skipped.joinToString()}" else ""),
            )
        }

        MDialog(
            title = if (many) T.t("Połącz zdjęcia tablicy", "Merge board photos") else T.t("Oczyść tablicę", "Clean up the board"),
            onDismiss = { if (!busy) close() },
            confirm = if (busy) T.t("Przetwarzam…", "Processing…") else T.t("Zastosuj", "Apply"),
            confirmEnabled = !busy && sources != null,
            onConfirm = {
                busy = true
                val chosen = opt
                val strokes = asStrokes
                val quad = corners
                val rub = erase
                work.launch {
                    // 3c-B3 (K7): powrót przez główny wątek, nie przez widok (po jego zwolnieniu okno wisiało na
                    // „Przetwarzam…”), a błąd potoku (też brak pamięci przy wielu zdjęciach) zdejmuje blokadę, nie wywraca aplikacji.
                    val post = { f: () -> Unit -> main.post(f); Unit }
                    try {
                        // Pełny rozmiar: zdjęcia wczytywane po jednym (przy kilku zostają tylko wyprostowane klatki).
                        val src = if (many) BoardPipeline.prepareMany(files.size, chosen) { loadRaster(files[it], chosen.maxSide) }
                        else loadRaster(files[0], chosen.maxSide)?.let { BoardPipeline.prepare(it, chosen, quad?.let { q -> quadOf(q, it) }) }
                        val res = src?.let { BoardPipeline.finish(it, chosen, scaled(rub, it.image)) }
                        val skippedNote = src?.skipped?.takeIf { it.isNotEmpty() }?.let { s ->
                            T.t("Pominięto zdjęcia ${s.joinToString { "${it + 1}" }}: nie znalazłem na nich tablicy", "Skipped photos ${s.joinToString { "${it + 1}" }}: no board found on them")
                        }
                        if (res == null || src == null) {
                            post { busy = false; ed.say(T.t("Nie udało się wczytać zdjęcia", "Could not load the photo")) }
                            return@launch
                        }
                        when {
                            strokes -> {
                                val ink = strokeInk(chosen)
                                val els = if (chosen.curves) ChalkStrokes.toCurves(res.image, src, imgs[src.ref], ink)
                                else ChalkStrokes.toStrokes(res.image, src, imgs[src.ref], ink)
                                post {
                                    busy = false
                                    if (els.isEmpty()) ed.say(T.t("Nie znaleziono kredy do zamiany na kreski", "No chalk found to turn into strokes"))
                                    else { ed.addOnSelPage(els); skippedNote?.let(ed::say); close() }
                                }
                            }
                            many -> {
                                val png = encodePng(res.image)
                                post {
                                    if (png == null) busy = false
                                    else addImage(ed, imgs[src.ref], png, res.image) { busy = false; skippedNote?.let(ed::say); close() }
                                }
                            }
                            else -> {
                                val png = encodePng(res.image)
                                post {
                                    if (png == null) busy = false
                                    else ed.replaceSelImageAsset(imgs[0].id, png, res.image.w, res.image.h) { busy = false; if (it) close() }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        post { busy = false; ed.say(if (e is OutOfMemoryError) T.t("Za mało pamięci — wybierz mniej zdjęć", "Not enough memory — pick fewer photos") else T.t("Nie udało się oczyścić zdjęcia", "Could not clean up the photo")) }
                    }
                }
            },
        ) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Choice(listOf(T.t("Wynik", "Result"), T.t("Narożniki", "Corners"), T.t("Gumka", "Eraser")), mode) { mode = it }
                val auto = prep?.quad?.let { normOf(it, prep!!) }
                when {
                    mode == SHOW_CORNERS && photo != null && !many && opt.straighten ->
                        CornerBox(photo, corners ?: auto ?: FULL) { edits = edits.withGeometry(it) }
                    else -> ResultBox(preview, if (mode == SHOW_ERASER) erase else null) { if (!stale) edits = edits.withLoop(it) }
                }
                val hint = when (mode) {
                    SHOW_CORNERS -> when {
                        many -> T.t("Przy kilku zdjęciach narożniki znajduję na każdym zdjęciu osobno.", "With several photos the corners are found on each photo separately.")
                        !opt.straighten -> T.t("Włącz „Prostuj perspektywę”, żeby ustawić narożniki.", "Turn on “Straighten perspective” to set the corners.")
                        else -> T.t("Przeciągnij kółka do narożników tablicy; podgląd przeliczy się po puszczeniu.", "Drag the circles to the board corners; the preview updates when you let go.")
                    }
                    SHOW_ERASER -> T.t("Obwiedź palcem to, co ma zniknąć (ręka, napis na ramie, odblask).", "Draw around what should disappear (a hand, a label on the frame, a glare).")
                    else -> if (sources == null) T.t("Wczytuję zdjęcie…", "Loading the photo…") else info
                }
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                // Poprawki wyniku tam, gdzie widać problem: krzywo lub ucięte → narożniki, śmieci → gumka.
                if (mode == SHOW_RESULT && sources != null) Row {
                    if (!many && opt.straighten) TextButton(onClick = { mode = SHOW_CORNERS }) { Text(T.t("Krzywo albo ucięte? Narożniki", "Crooked or cut off? Corners")) }
                    TextButton(onClick = { mode = SHOW_ERASER }) { Text(T.t("Śmieci? Gumka", "Leftovers? Eraser")) }
                }
                if (mode == SHOW_CORNERS && corners != null) {
                    TextButton(onClick = { edits = edits.withGeometry(null) }) { Text(T.t("Narożniki automatycznie", "Automatic corners")) }
                }
                if (mode == SHOW_ERASER && erase.isNotEmpty()) {
                    Row {
                        TextButton(onClick = { edits = edits.undoLoop() }) { Text(T.t("Cofnij gumkę", "Undo eraser")) }
                        TextButton(onClick = { edits = edits.clearLoops() }) { Text(T.t("Wyczyść gumkę (${erase.size})", "Clear eraser (${erase.size})")) }
                    }
                }
                SectionLabel(T.t("Wynik", "Result"))
                Choice(listOf(T.t("Obraz kredy", "Chalk image"), T.t("Edytowalne kreski", "Editable strokes")), if (asStrokes) 1 else 0) { asStrokes = it == 1 }
                Text(
                    when {
                        asStrokes -> T.t("Kreda zamieni się w kreski pióra nad zdjęciem; zdjęcie zostaje, możesz je potem usunąć.", "The chalk becomes pen strokes above the photo; the photo stays, you can delete it later.")
                        many -> T.t("Powstanie nowy obraz; zaznaczone zdjęcia zostają bez zmian.", "A new image is created; the selected photos stay unchanged.")
                        else -> T.t("Zdjęcie zostanie zastąpione samą kredą (cofniesz to jak każdą zmianę).", "The photo is replaced by the chalk alone (undo works as usual).")
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp),
                )
                // 3c-B1: na wierzchu tylko wynik i kolor; kroki potoku i suwaki pod „Więcej ustawień” (domyślne są dobre
                // na większości zdjęć, a dziesięć przełączników na wierzchu odstraszało).
                ChalkColour(opt) { opt = it }
                TextButton(onClick = { more = !more }) {
                    Text(if (more) T.t("Mniej ustawień", "Fewer settings") else T.t("Więcej ustawień", "More settings"))
                }
                if (more) Steps(opt) { opt = it; edits = edits.withGeometry(edits.corners, it.straighten, it.crop) }
            }
        }
    }

    /** Papier strony, na której leży [img] (do koloru kredy, 2c-B4); null, gdy obrazu nie ma na żadnej stronie. */
    private fun paperOf(ed: Editor, img: ImageEl): Int? =
        ed.doc.pages.firstOrNull { p -> ed.doc.content(p).elements.any { it.id == img.id } }?.spec?.paper

    /** Kolor „Edytowalnych kresek”: wybrany albo grafit, z kontrastem ≥ 3:1 do papieru strony (grafit na ciemnej jaśnieje). */
    private fun strokeInk(opt: BoardOptions): Int = (opt.tint ?: GRAPHITE).let { c -> opt.paper?.let { ChalkInk.fit(c, it) } ?: c }

    /** Przełączniki kroków i suwaki progu; te same kontrolki co w ustawieniach. */
    @Composable
    private fun Steps(opt: BoardOptions, change: (BoardOptions) -> Unit) {
        ToggleRow(T.t("Prostuj perspektywę (narożniki tablicy)", "Straighten perspective (board corners)"), opt.straighten) { change(opt.copy(straighten = it)) }
        ToggleRow(T.t("Przytnij do tablicy", "Crop to the board"), opt.crop) { change(opt.copy(crop = it)) }
        ToggleRow(T.t("Wyrównaj oświetlenie", "Even out the lighting"), opt.flatten) { change(opt.copy(flatten = it)) }
        ToggleRow(T.t("Wyostrz kredę", "Sharpen the chalk"), opt.sharpen) { change(opt.copy(sharpen = it)) }
        ToggleRow(T.t("Dociągaj bladą kredę do wyraźnej", "Keep faint chalk joined to clear chalk"), opt.faint) { change(opt.copy(faint = it)) }
        ToggleRow(T.t("Próg lokalny (blade litery w spokojnych miejscach)", "Local threshold (faint letters in quiet areas)"), opt.localCut) { change(opt.copy(localCut = it)) }
        ToggleRow(T.t("Usuń listwy, ramy i ścianę", "Remove ledges, frames and wall"), opt.frames) { change(opt.copy(frames = it)) }
        ToggleRow(T.t("Usuń drobinki (ziarno tablicy)", "Remove specks (board grain)"), opt.specks) { change(opt.copy(specks = it)) }
        ToggleRow(T.t("Edytowalne kreski jako gładkie krzywe", "Editable strokes as smooth curves"), opt.curves) { change(opt.copy(curves = it)) }
        ToggleRow(T.t("Próg tła automatycznie", "Automatic background cut"), opt.autoCut) { change(opt.copy(autoCut = it)) }
        if (!opt.autoCut) {
            SectionLabel(T.t("Próg tła: ${(opt.cut * 100).toInt()}%", "Background cut: ${(opt.cut * 100).toInt()}%"))
            MSlider(opt.cut, 0.02f, 0.6f) { change(opt.copy(cut = it)) }
        }
        SectionLabel(T.t("Pełne krycie od: ${(opt.solid * 100).toInt()}%", "Fully opaque from: ${(opt.solid * 100).toInt()}%"))
        MSlider(opt.solid, 0.3f, 1f) { change(opt.copy(solid = it)) }
    }

    /** Kolor kredy (na wierzchu panelu, zawsze widoczny). */
    @Composable
    private fun ChalkColour(opt: BoardOptions, change: (BoardOptions) -> Unit) {
        SectionLabel(T.t("Kolor kredy", "Chalk colour"))
        val modes = listOf(null, LabSettings.tintColor(1), LabSettings.tintColor(2))
        Choice(listOf(T.t("Oryginalny", "Original"), T.t("Biały", "White"), T.t("Grafit", "Graphite")), modes.indexOf(opt.tint).coerceAtLeast(0)) {
            change(opt.copy(tint = modes[it]))
        }
    }

    /** Prostokąt obrazu o proporcjach [ratio] wpisany w pole [size], na środku. */
    private fun fitRect(size: Size, ratio: Float): Rect {
        val w = min(size.width, size.height * ratio); val h = w / ratio
        return Rect((size.width - w) / 2f, (size.height - h) / 2f, (size.width + w) / 2f, (size.height + h) / 2f)
    }

    /** Szachownica pod przezroczystym wynikiem w kolorach motywu (w ciemnym motywie ciemna, nie jasna plansza). */
    private fun DrawScope.checker(base: Color, cell: Color) {
        drawRect(base)
        val step = 12.dp.toPx()
        var y = 0f; var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else step
            while (x < size.width) { drawRect(cell, Offset(x, y), Size(step, step)); x += 2 * step }
            y += step; row++
        }
    }

    /**
     * Podgląd wyniku na szachownicy (przezroczystość widać jak w edytorach grafiki). Z [erase] ≠ null to gumka: palec
     * rysuje pętlę, a po puszczeniu [onPolygon] dostaje ją w ułamkach obrazu; narysowane pętle widać jako obrys.
     */
    @Composable
    private fun ResultBox(bmp: Bitmap?, erase: List<FloatArray>?, onPolygon: (FloatArray) -> Unit) {
        val m = mint
        val shape = RoundedCornerShape(10.dp)
        val ratio = bmp?.let { it.width.toFloat() / it.height } ?: 1.5f
        val img = remember(bmp) { bmp?.asImageBitmap() }
        var live by remember { mutableStateOf(listOf<Offset>()) }
        val base = m.canvasBg; val cell = m.line.copy(alpha = 0.35f); val accent = m.accent
        val draw = if (erase != null && bmp != null) Modifier.pointerInput(bmp) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val r = fitRect(Size(size.width.toFloat(), size.height.toFloat()), ratio)
                if (!r.contains(down.position)) return@awaitEachGesture
                down.consume()
                val pts = mutableListOf(down.position)
                live = pts.toList()
                drag(down.id) { ch -> ch.consume(); pts += ch.position; live = pts.toList() }
                live = emptyList()
                if (pts.size >= 3) onPolygon(FloatArray(pts.size * 2) { i ->
                    val p = pts[i / 2]
                    if (i % 2 == 0) ((p.x - r.left) / r.width).coerceIn(0f, 1f) else ((p.y - r.top) / r.height).coerceIn(0f, 1f)
                })
            }
        } else Modifier
        Box(Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.5f, 3f)).clip(shape).border(1.dp, m.line, shape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize().then(draw)) {
                checker(base, cell)
                if (img == null) return@Canvas
                val r = fitRect(size, ratio)
                drawImage(img, dstOffset = IntOffset(r.left.toInt(), r.top.toInt()), dstSize = IntSize(r.width.toInt(), r.height.toInt()))
                val dash = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                erase?.forEach { poly ->
                    drawPath(Path().apply {
                        moveTo(r.left + poly[0] * r.width, r.top + poly[1] * r.height)
                        for (i in 1 until poly.size / 2) lineTo(r.left + poly[2 * i] * r.width, r.top + poly[2 * i + 1] * r.height)
                        close()
                    }, accent, style = dash)
                }
                if (live.size >= 2) drawPath(Path().apply { moveTo(live[0].x, live[0].y); for (p in live.drop(1)) lineTo(p.x, p.y) }, accent, style = Stroke(2.dp.toPx()))
            }
            if (bmp == null) Text(T.t("Podgląd pojawi się za chwilę", "The preview will appear in a moment"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    /**
     * Zdjęcie z czterema uchwytami narożników tablicy (w ułamkach zdjęcia). Uchwyt łapie się w promieniu 36 dp; dotyk
     * dalej od uchwytów nie jest przechwytywany, więc panel da się przewijać także po zdjęciu. [onDone] po puszczeniu.
     */
    @Composable
    private fun CornerBox(photo: Bitmap, start: FloatArray, onDone: (FloatArray) -> Unit) {
        val m = mint
        val shape = RoundedCornerShape(10.dp)
        val ratio = photo.width.toFloat() / photo.height
        val img = remember(photo) { photo.asImageBitmap() }
        var q by remember(start.toList()) { mutableStateOf(start.copyOf()) }
        var active by remember { mutableStateOf(-1) }
        val accent = m.accent; val base = m.canvasBg
        Box(Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.5f, 3f)).clip(shape).border(1.dp, m.line, shape)) {
            Canvas(Modifier.matchParentSize().pointerInput(photo, start.toList()) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val r = fitRect(Size(size.width.toFloat(), size.height.toFloat()), ratio)
                    val reach = 36.dp.toPx()
                    val i = (0 until 4).minByOrNull { hypot(r.left + q[2 * it] * r.width - down.position.x, r.top + q[2 * it + 1] * r.height - down.position.y) }!!
                    if (hypot(r.left + q[2 * i] * r.width - down.position.x, r.top + q[2 * i + 1] * r.height - down.position.y) > reach) return@awaitEachGesture
                    down.consume()
                    active = i
                    drag(down.id) { ch ->
                        ch.consume()
                        val next = q.copyOf().also {
                            it[2 * i] = ((ch.position.x - r.left) / r.width).coerceIn(0f, 1f)
                            it[2 * i + 1] = ((ch.position.y - r.top) / r.height).coerceIn(0f, 1f)
                        }
                        // 3c-B3 (K3): narożnik nie przechodzi za sąsiedni — czworokąt zostaje wypukły.
                        if (Quad(next).isConvex()) q = next
                    }
                    active = -1
                    onDone(q.copyOf())
                }
            }) {
                drawRect(base)
                val r = fitRect(size, ratio)
                drawImage(img, dstOffset = IntOffset(r.left.toInt(), r.top.toInt()), dstSize = IntSize(r.width.toInt(), r.height.toInt()))
                fun at(i: Int) = Offset(r.left + q[2 * i] * r.width, r.top + q[2 * i + 1] * r.height)
                drawPath(Path().apply { moveTo(at(0).x, at(0).y); for (i in 1..3) lineTo(at(i).x, at(i).y); close() }, accent, style = Stroke(2.dp.toPx()))
                for (i in 0 until 4) {
                    drawCircle(accent.copy(alpha = if (i == active) 0.5f else 0.3f), radius = 14.dp.toPx(), center = at(i))
                    drawCircle(accent, radius = 5.dp.toPx(), center = at(i))
                }
                if (active >= 0) loupe(img, r, at(active), accent, base)
            }
        }
    }

    /**
     * Lupa przy przeciąganiu narożnika (3c-B1): zdjęcie wokół uchwytu [at] powiększone [LOUPE_ZOOM]× w kółku w górnym
     * rogu po przeciwnej stronie niż palec, z krzyżykiem w miejscu narożnika (palec zasłania dokładnie to, co trzeba trafić).
     */
    private fun DrawScope.loupe(img: ImageBitmap, r: Rect, at: Offset, accent: Color, base: Color) {
        val rad = 48.dp.toPx()
        val margin = 8.dp.toPx()
        val c = Offset(if (at.x > size.width / 2) margin + rad else size.width - margin - rad, margin + rad)
        val perPx = r.width / img.width
        val half = rad / LOUPE_ZOOM / perPx
        val sx = (at.x - r.left) / perPx; val sy = (at.y - r.top) / perPx
        val circle = Path().apply { addOval(Rect(c, rad)) }
        clipPath(circle) {
            drawRect(base, Offset(c.x - rad, c.y - rad), Size(2 * rad, 2 * rad))
            // Wycinek przycięty do zdjęcia (przy brzegu część lupy zostaje tłem); cel przesunięty o tyle samo.
            val x0 = max(0f, sx - half); val y0 = max(0f, sy - half)
            val x1 = min(img.width.toFloat(), sx + half); val y1 = min(img.height.toFloat(), sy + half)
            val k = rad / half
            if (x1 - x0 >= 1f && y1 - y0 >= 1f) drawImage(
                img,
                srcOffset = IntOffset(x0.toInt(), y0.toInt()), srcSize = IntSize((x1 - x0).toInt(), (y1 - y0).toInt()),
                dstOffset = IntOffset((c.x + (x0 - sx) * k).toInt(), (c.y + (y0 - sy) * k).toInt()),
                dstSize = IntSize(((x1 - x0) * k).toInt(), ((y1 - y0) * k).toInt()),
            )
        }
        drawCircle(accent, rad, c, style = Stroke(2.dp.toPx()))
        val arm = 10.dp.toPx()
        drawLine(accent, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), 1.5.dp.toPx())
        drawLine(accent, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), 1.5.dp.toPx())
    }

    private const val LOUPE_ZOOM = 3f

    /** Cały kadr jako narożniki (gdy podglądu jeszcze nie ma). */
    private val FULL = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

    /** Narożniki w ułamkach zdjęcia → [Quad] w pikselach rastra [r] (środek piksela i to i). */
    private fun quadOf(n: FloatArray, r: Raster) = Quad(FloatArray(8) { i -> if (i % 2 == 0) n[i] * r.w - 0.5f else n[i] * r.h - 0.5f })

    /** Narożniki zgadnięte przez potok (piksele pomniejszonego zdjęcia źródła [s]) → ułamki zdjęcia. */
    private fun normOf(q: Quad, s: BoardSource) = FloatArray(8) { i -> if (i % 2 == 0) (q.pts[i] + 0.5f) / s.fitW else (q.pts[i] + 0.5f) / s.fitH }

    /** Wielokąty gumki z ułamków na piksele obrazu [img]. */
    private fun scaled(polys: List<FloatArray>, img: Raster) = polys.map { p -> FloatArray(p.size) { i -> p[i] * if (i % 2 == 0) img.w else img.h } }

    /** Okno „Wyślij stronę do mLabs”: edytor i indeks strony z chwili wywołania polecenia; null = zamknięte. */
    private var sendPage by mutableStateOf<Pair<Editor, Int>?>(null)
    /** „Oczyścić?” w podpowiedzi po wstawieniu zdjęcia tablicy: panel kredy dla tego zdjęcia (2c-B5). */
    private var cleanAsk by mutableStateOf<Pair<Editor, ImageEl>?>(null)

    override fun commands(ed: Editor): List<LabCommand> = listOf(
        LabCommand("labSendMlabs", 0, "Wyślij stronę do mLabs…", "Send page to mLabs…", { ed.doc.pages.isNotEmpty() }) { sendPage = ed to ed.pageIndex },
    )

    @Composable
    override fun Overlay(ed: Editor) {
        cleanAsk?.let { (owner, img) -> if (owner === ed) BoardPanel(ed, listOf(img)) { cleanAsk = null } }
        val (owner, index) = sendPage ?: return
        if (owner !== ed) return
        com.mdev.mnotes.lab.recognize.SendToMlabs.Dialog(ed, index, work) { sendPage = null }
    }

    /**
     * Zdjęcie z trybu wykładu (2b-B3, hook A2-8): gdy to ciemna tablica, przechodzi przez kredę z krokami z Ustawień
     * i podmienia plik obrazu jako **osobna** edycja — zdjęcie na stronie to podgląd, a jedno cofnięcie wraca do
     * oryginału. Inne zdjęcia (kartka, slajd) zostają bez zmian. Wyłączane w Ustawienia → Laboratorium.
     */
    override fun onLecturePhoto(ed: Editor, pageId: String, image: ImageEl) {
        val set = LabSettings.of(ed.view?.context ?: return)
        if (!set.lecture) return
        val file = ed.repo.assetFile(ed.nbId, image.asset)
        val opt = set.options().copy(paper = ed.doc.page(pageId)?.spec?.paper)
        work.launch {
            val photo = loadRaster(file, opt.maxSide) ?: return@launch
            if (!BoardGeometry.looksLikeDarkBoard(photo)) return@launch
            val out = BoardPipeline.run(photo, opt).image
            val png = encodePng(out) ?: return@launch
            ed.repo.io.execute {
                val name = runCatching { ed.repo.importAsset(ed.nbId, png.inputStream(), "png") }.getOrNull() ?: return@execute
                ed.view?.post {
                    val page = ed.doc.page(pageId) ?: return@post
                    val cur = ed.doc.content(page)
                    // użytkownik mógł już zdjęcie przesunąć albo usunąć: bierzemy je z bieżącej treści strony
                    val el = cur.elements.firstOrNull { it.id == image.id } as? ImageEl ?: return@post
                    if (el.asset != image.asset) return@post
                    val clean = el.copy(asset = name, h = el.w * out.h / out.w, crop = com.mdev.mnotes.core.model.ImageCrop.FULL, fx = com.mdev.mnotes.core.model.ImageFx.NONE)
                    ed.exec(com.mdev.mnotes.core.model.DocEdit.Elements(page.id, com.mdev.mnotes.core.model.ElementsEdit.replace(cur, mapOf(el.id to clean))))
                    // 2c-B4: przycisk zamiast samego ↶ — działa także po innych zmianach (podmienia tylko to zdjęcie z powrotem)
                    ed.say(
                        T.t("Tablica oczyszczona: sama kreda.", "Board cleaned: chalk only."),
                        listOf(T.t("Cofnij do oryginału", "Back to the original") to { restorePhoto(ed, pageId, el, name) }),
                    )
                }
            }
        }
    }

    /**
     * „Cofnij do oryginału” po oczyszczeniu: obraz [orig] wraca w miejscu i szerokości, które ma teraz (użytkownik mógł
     * go przesunąć); nic się nie dzieje, gdy na stronie nie ma już oczyszczonej wersji [cleanAsset].
     */
    private fun restorePhoto(ed: Editor, pageId: String, orig: ImageEl, cleanAsset: String) {
        val page = ed.doc.page(pageId) ?: return
        val cur = ed.doc.content(page)
        val now = cur.elements.firstOrNull { it.id == orig.id } as? ImageEl ?: return
        if (now.asset != cleanAsset) return
        val back = orig.copy(cx = now.cx, cy = now.cy, w = now.w, h = now.w * orig.h / orig.w, rot = now.rot)
        ed.exec(com.mdev.mnotes.core.model.DocEdit.Elements(page.id, com.mdev.mnotes.core.model.ElementsEdit.replace(cur, mapOf(orig.id to back))))
    }

    /**
     * Po wstawieniu zdjęcia ciemnej tablicy podpowiada „Oczyść tablicę” z przyciskami „Oczyścić?” (zaznacza zdjęcie
     * i otwiera panel kredy) i „Nie pokazuj więcej” (2c-B5; wcześniej sam dymek, raz); sprawdzenie na 320 px w tle.
     */
    override fun imageInserted(ed: Editor, el: ImageEl) {
        val set = LabSettings.of(ed.view?.context ?: return)
        if (!set.boardHint) return
        val file = ed.repo.assetFile(ed.nbId, el.asset)
        work.launch {
            val small = loadRaster(file, 320) ?: return@launch
            if (!BoardGeometry.looksLikeDarkBoard(small)) return@launch
            ed.view?.post {
                ed.say(
                    T.t("Wygląda jak tablica: zostawić samą kredę?", "Looks like a blackboard: keep only the chalk?"),
                    listOf(
                        T.t("Oczyścić?", "Clean up?") to { cleanPhoto(ed, el) },
                        T.t("Nie pokazuj więcej", "Don't show again") to { set.boardHint = false },
                    ),
                )
            }
        }
    }

    /** Zaznacza zdjęcie [el] (jeśli wciąż jest na stronie) i otwiera dla niego panel kredy. */
    private fun cleanPhoto(ed: Editor, el: ImageEl) {
        val page = ed.doc.pages.firstOrNull { p -> ed.doc.content(p).elements.any { it.id == el.id } } ?: return
        val now = ed.doc.content(page).elements.first { it.id == el.id } as? ImageEl ?: return
        ed.select(page.id, setOf(now.id))
        cleanAsk = ed to now
    }

    @Composable
    override fun SettingsSection() {
        val set = LabSettings.of(LocalContext.current)
        Group(T.t("Kreda: zdjęcia tablicy", "Chalk: blackboard photos"), keys = "kreda tablica zdjęcie oczyść prostowanie próg narożniki gumka podpowiedź blada listwy ramy kreski krzywe gładkie bezier chalk board photo clean corners eraser hint faint ledge frame strokes curves smooth") {
            Text(
                T.t(
                    "„Oczyść tablicę” w menu zaznaczonego zdjęcia zostawia samą kredę na przezroczystym tle; kilka zaznaczonych zdjęć tej samej tablicy łączy medianą, która usuwa wykładowcę. Narożniki tablicy i gumkę ustawisz w panelu. Tu wybierasz, które kroki są włączone na start; w panelu zmienisz je dla jednego zdjęcia.",
                    "“Clean up the board” in the selected photo's menu keeps only the chalk on a transparent background; several selected photos of the same board are merged with a median that removes the lecturer. Board corners and the eraser are in the panel. Here you choose which steps start enabled; the panel changes them for one photo.",
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp),
            )
            ToggleRow(T.t("Podpowiedź po wstawieniu zdjęcia tablicy", "Hint after inserting a board photo"), set.boardHint) { set.boardHint = it }
            ToggleRow(T.t("Zdjęcie z trybu wykładu od razu przez kredę", "Lecture-mode photo straight through chalk cleanup"), set.lecture) { set.lecture = it }
            Steps(set.options()) { o ->
                set.straighten = o.straighten; set.crop = o.crop; set.flatten = o.flatten; set.sharpen = o.sharpen
                set.faint = o.faint; set.localCut = o.localCut; set.frames = o.frames; set.specks = o.specks; set.curves = o.curves
                set.autoCut = o.autoCut; set.cut = o.cut; set.solid = o.solid
            }
            ChalkColour(set.options()) { o -> set.tint = listOf(null, LabSettings.tintColor(1), LabSettings.tintColor(2)).indexOf(o.tint).coerceAtLeast(0) }
        }
    }

    /** Nowy obraz (wynik mediany) w miejscu zdjęcia odniesienia, ta sama szerokość. */
    private fun addImage(ed: Editor, near: ImageEl, png: ByteArray, out: Raster, done: () -> Unit) {
        ed.repo.io.execute {
            val name = runCatching { ed.repo.importAsset(ed.nbId, png.inputStream(), "png") }.getOrNull()
            ed.view?.post {
                if (name != null) ed.addOnSelPage(listOf(ImageEl(Ids.next(), name, near.cx, near.cy, near.w, near.w * out.h / out.w)))
                else ed.say(T.t("Nie udało się zapisać obrazu", "Could not save the image"))
                done()
            }
        }
    }

    /**
     * Plik obrazu jako [Raster] z dłuższym bokiem najwyżej [longest], obrócony według EXIF jak przy wstawianiu
     * (`Assets.decodeFile`): zdjęcie pionowe z telefonu nie wyjdzie położone. Dekoder sam próbkuje i skaluje
     * ([DecodePlan]), więc 12 Mpx nie trafia w pełnym rozmiarze ani do pamięci Javy, ani do [Raster].
     */
    fun loadRaster(file: File, longest: Int): Raster? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val plan = DecodePlan.of(bounds.outWidth, bounds.outHeight, longest)
        var bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = plan.sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (plan.scales) { inScaled = true; inDensity = plan.density; inTargetDensity = plan.targetDensity }
        }) ?: return null
        val rot = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true).also { if (it !== bmp) bmp.recycle() }
        val r = Raster(bmp.width, bmp.height).also { bmp.getPixels(it.px, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        bmp.recycle()
        r.fit(longest)
    }.getOrNull()

    fun Raster.toBitmap(): Bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)

    fun encodePng(r: Raster): ByteArray? = runCatching {
        val out = ByteArrayOutputStream()
        val bmp = r.toBitmap()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        out.toByteArray()
    }.getOrNull()
}
