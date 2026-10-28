package com.mdev.mnotes.lab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.mdev.mnotes.app.LabCommand
import com.mdev.mnotes.app.LabFeatures
import com.mdev.mnotes.core.image.BoardGeometry
import com.mdev.mnotes.core.image.BoardOptions
import com.mdev.mnotes.core.image.BoardPipeline
import com.mdev.mnotes.core.image.BoardSource
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
import kotlin.math.min

/** Funkcje wariantu lab widziane przez wspólny kod przez [LabFeatures]. */
object Lab : LabFeatures {

    /** Rozmiar podglądu w panelu: szybki na tablecie, wystarczy do oceny progu. */
    private const val PREVIEW = 520

    /** Kolor kresek z wektoryzacji, gdy kreda zostaje w oryginalnych barwach (grafit czytelny na jasnej stronie). */
    private const val GRAPHITE = 0xFF37474F.toInt()

    /**
     * Ciężka praca kredy (potok na 2400 px, mediana, wektoryzacja, sprawdzenie wstawionego zdjęcia) na wspólnej puli
     * obliczeń, a nie na jednowątkowym `repo.io`, które w tym czasie musi móc zapisywać zeszyt.
     */
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Tryby podglądu: wynik, ręczne narożniki tablicy, gumka wielokątna. */
    private const val SHOW_RESULT = 0
    private const val SHOW_CORNERS = 1
    private const val SHOW_ERASER = 2

    @Composable
    override fun BoardPanel(ed: Editor, imgs: List<ImageEl>, close: () -> Unit) {
        val set = LabSettings.of(LocalContext.current)
        val files = remember(imgs.map { it.asset }) { imgs.map { ed.repo.assetFile(ed.nbId, it.asset) } }
        val many = imgs.size > 1
        var opt by remember { mutableStateOf(set.options()) }
        var asStrokes by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf(SHOW_RESULT) }
        // Narożniki ustawione ręcznie, w ułamkach zdjęcia (lewy górny, prawy górny, prawy dolny, lewy dolny); null = zgadnięte.
        var corners by remember { mutableStateOf<FloatArray?>(null) }
        // Wielokąty gumki w ułamkach wyniku: ten sam kształt na podglądzie i w pełnym rozmiarze.
        var erase by remember { mutableStateOf(listOf<FloatArray>()) }
        var prep by remember { mutableStateOf<BoardSource?>(null) }
        var preview by remember { mutableStateOf<Bitmap?>(null) }
        var info by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val sources by produceState<List<Raster>?>(null, files) {
            value = withContext(Dispatchers.IO) { files.map { loadRaster(it, PREVIEW) ?: return@withContext null } }
        }
        val photo = remember(sources) { sources?.firstOrNull()?.toBitmap() }

        // Kroki geometrii i filtrów są drogie: liczą się od nowa tylko po zmianie przełączników kroków albo narożników,
        // i dopiero po 60 ms ciszy (szybkie klikanie nie ustawia w kolejce kilku przeliczeń).
        LaunchedEffect(sources, listOf(opt.straighten, opt.crop, opt.flatten, opt.sharpen), corners?.toList()) {
            val src = sources ?: return@LaunchedEffect
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
                    // Pełny rozmiar: zdjęcia wczytywane po jednym (przy kilku zostają tylko wyprostowane klatki).
                    val src = if (many) BoardPipeline.prepareMany(files.size, chosen) { loadRaster(files[it], chosen.maxSide) }
                    else loadRaster(files[0], chosen.maxSide)?.let { BoardPipeline.prepare(it, chosen, quad?.let { q -> quadOf(q, it) }) }
                    val res = src?.let { BoardPipeline.finish(it, chosen, scaled(rub, it.image)) }
                    val post = { f: () -> Unit -> ed.view?.post(f) }
                    val skippedNote = src?.skipped?.takeIf { it.isNotEmpty() }?.let { s ->
                        T.t("Pominięto zdjęcia ${s.joinToString { "${it + 1}" }}: nie znalazłem na nich tablicy", "Skipped photos ${s.joinToString { "${it + 1}" }}: no board found on them")
                    }
                    if (res == null || src == null) {
                        post { busy = false; ed.say(T.t("Nie udało się wczytać zdjęcia", "Could not load the photo")) }
                        return@launch
                    }
                    when {
                        strokes -> {
                            val els = if (chosen.curves) ChalkStrokes.toCurves(res.image, src, imgs[src.ref], chosen.tint ?: GRAPHITE)
                            else ChalkStrokes.toStrokes(res.image, src, imgs[src.ref], chosen.tint ?: GRAPHITE)
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
                }
            },
        ) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Choice(listOf(T.t("Wynik", "Result"), T.t("Narożniki", "Corners"), T.t("Gumka", "Eraser")), mode) { mode = it }
                val auto = prep?.quad?.let { normOf(it, prep!!) }
                when {
                    mode == SHOW_CORNERS && photo != null && !many && opt.straighten ->
                        CornerBox(photo, corners ?: auto ?: FULL) { corners = it }
                    else -> ResultBox(preview, if (mode == SHOW_ERASER) erase else null) { erase = erase + listOf(it) }
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
                if (mode == SHOW_CORNERS && corners != null) {
                    TextButton(onClick = { corners = null }) { Text(T.t("Narożniki automatycznie", "Automatic corners")) }
                }
                if (mode == SHOW_ERASER && erase.isNotEmpty()) {
                    Row {
                        TextButton(onClick = { erase = erase.dropLast(1) }) { Text(T.t("Cofnij gumkę", "Undo eraser")) }
                        TextButton(onClick = { erase = emptyList() }) { Text(T.t("Wyczyść gumkę (${erase.size})", "Clear eraser (${erase.size})")) }
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
                Steps(opt) { opt = it }
            }
        }
    }

    /** Przełączniki kroków i suwaki progu; te same kontrolki co w ustawieniach. */
    @Composable
    private fun Steps(opt: BoardOptions, change: (BoardOptions) -> Unit) {
        ToggleRow(T.t("Prostuj perspektywę (narożniki tablicy)", "Straighten perspective (board corners)"), opt.straighten) { change(opt.copy(straighten = it)) }
        ToggleRow(T.t("Przytnij do tablicy", "Crop to the board"), opt.crop) { change(opt.copy(crop = it)) }
        ToggleRow(T.t("Wyrównaj oświetlenie", "Even out the lighting"), opt.flatten) { change(opt.copy(flatten = it)) }
        ToggleRow(T.t("Wyostrz kredę", "Sharpen the chalk"), opt.sharpen) { change(opt.copy(sharpen = it)) }
        ToggleRow(T.t("Dociągaj bladą kredę do wyraźnej", "Keep faint chalk joined to clear chalk"), opt.faint) { change(opt.copy(faint = it)) }
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
                        q = q.copyOf().also {
                            it[2 * i] = ((ch.position.x - r.left) / r.width).coerceIn(0f, 1f)
                            it[2 * i + 1] = ((ch.position.y - r.top) / r.height).coerceIn(0f, 1f)
                        }
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
            }
        }
    }

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

    override fun commands(ed: Editor): List<LabCommand> = listOf(
        LabCommand("labSendMlabs", 0, "Wyślij stronę do mLabs…", "Send page to mLabs…", { ed.doc.pages.isNotEmpty() }) { sendPage = ed to ed.pageIndex },
    )

    @Composable
    override fun Overlay(ed: Editor) {
        val (owner, index) = sendPage ?: return
        if (owner !== ed) return
        com.mdev.mnotes.lab.recognize.SendToMlabs.Dialog(ed, index, work) { sendPage = null }
    }

    @Composable
    override fun phoneButton(): Boolean = LabSettings.of(LocalContext.current).nearby

    @Composable
    override fun PhonePanel(ed: Editor, close: () -> Unit) = com.mdev.mnotes.lab.nearby.PhoneLinkDialog(com.mdev.mnotes.core.nearby.LinkMode.RECEIVE, ed, close = close)

    /**
     * Zdjęcie z trybu wykładu (2b-B3, hook A2-8): gdy to ciemna tablica, przechodzi przez kredę z krokami z Ustawień
     * i podmienia plik obrazu jako **osobna** edycja — zdjęcie na stronie to podgląd, a jedno cofnięcie wraca do
     * oryginału. Inne zdjęcia (kartka, slajd) zostają bez zmian. Wyłączane w Ustawienia → Laboratorium.
     */
    override fun onLecturePhoto(ed: Editor, pageId: String, image: ImageEl) {
        val set = LabSettings.of(ed.view?.context ?: return)
        if (!set.lecture) return
        val file = ed.repo.assetFile(ed.nbId, image.asset)
        val opt = set.options()
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
                    ed.say(T.t("Tablica oczyszczona: sama kreda. Cofnij (↶), żeby wrócić do zdjęcia.", "Board cleaned: chalk only. Undo (↶) to get the photo back."))
                }
            }
        }
    }

    /** Po wstawieniu zdjęcia ciemnej tablicy raz podpowiada „Oczyść tablicę”; sprawdzenie na 320 px w tle. */
    override fun imageInserted(ed: Editor, el: ImageEl) {
        val set = LabSettings.of(ed.view?.context ?: return)
        if (!set.boardHint) return
        val file = ed.repo.assetFile(ed.nbId, el.asset)
        work.launch {
            val small = loadRaster(file, 320) ?: return@launch
            if (!BoardGeometry.looksLikeDarkBoard(small)) return@launch
            ed.view?.post {
                set.boardHint = false
                ed.say(T.t(
                    "Wygląda jak tablica: różdżka „Oczyść tablicę” w menu zdjęcia zostawi samą kredę (podpowiedź raz; Ustawienia → Laboratorium)",
                    "Looks like a blackboard: the “Clean up the board” wand in the photo menu keeps only the chalk (shown once; Settings → Lab)",
                ))
            }
        }
    }

    @Composable
    override fun SettingsSection() {
        val set = LabSettings.of(LocalContext.current)
        NearbySection(set)
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
                set.faint = o.faint; set.frames = o.frames; set.specks = o.specks; set.curves = o.curves
                set.autoCut = o.autoCut; set.cut = o.cut; set.solid = o.solid
                set.tint = listOf(null, LabSettings.tintColor(1), LabSettings.tintColor(2)).indexOf(o.tint).coerceAtLeast(0)
            }
        }
    }

    /** Łącze z telefonem: przełącznik, parowanie, wysyłanie zdjęcia (to samo menu na tablecie i telefonie). */
    @Composable
    private fun NearbySection(set: LabSettings) {
        val ctx = LocalContext.current
        val store = remember { com.mdev.mnotes.lab.nearby.PairStore(ctx) }
        var dialog by remember { mutableStateOf(0) }
        var tick by remember { mutableStateOf(0) }
        var outgoing by remember { mutableStateOf<com.mdev.mnotes.lab.nearby.Outgoing?>(null) }
        Group(T.t("Telefon i tablet (Nearby)", "Phone and tablet (Nearby)"), keys = "nearby telefon tablet zdjęcie bluetooth parowanie połącz wyślij diagnostyka łącze phone pair connect send diagnostics link") {
            Text(
                T.t(
                    "Zdjęcie zrobione telefonem trafia od razu na stronę na tablecie, przez Bluetooth albo Wi-Fi Direct, bez internetu i bez wspólnej sieci. Wymaga Usług Google Play na obu urządzeniach; każda ramka jest szyfrowana kluczem pary. Android każe pytać o lokalizację, bo szukanie urządzeń przez Bluetooth mogłoby ją zdradzić; mNotes położenia nie odczytuje.",
                    "A photo taken with the phone lands right on the tablet page, over Bluetooth or Wi-Fi Direct, without internet or a shared network. Needs Google Play services on both devices; every frame is encrypted with the pair key. Android requires the location permission because scanning for Bluetooth devices could reveal it; mNotes never reads your location.",
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp),
            )
            ToggleRow(T.t("Włącz łącze z telefonem", "Enable the phone link"), set.nearby) { set.nearby = it }
            if (set.nearby) {
                tick
                Text(
                    if (store.paired) T.t("Sparowano z „${store.peerName}”.", "Paired with “${store.peerName}”.") else T.t("Brak sparowanego urządzenia.", "No paired device."),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp),
                )
                TextButton(onClick = { dialog = 1 }) { Text(T.t("Połącz telefon z tabletem", "Connect phone and tablet")) }
                if (store.paired) {
                    TextButton(onClick = { dialog = 2 }) { Text(T.t("Wyślij zdjęcie na tablet (na telefonie)", "Send a photo to the tablet (on the phone)")) }
                    TextButton(onClick = { store.clear(); tick++ }) { Text(T.t("Usuń parę", "Forget the pair"), color = MaterialTheme.colorScheme.error) }
                }
                if (store.paired) {
                    TextButton(onClick = { dialog = 4 }) { Text(T.t("Wyślij zeszyt na drugie urządzenie", "Send a notebook to the other device")) }
                    TextButton(onClick = { dialog = 5 }) { Text(T.t("Odbierz zeszyt z drugiego urządzenia", "Receive a notebook from the other device")) }
                }
                TextButton(onClick = { dialog = 3 }) { Text(T.t("Diagnostyka łącza", "Link diagnostics")) }
            }
        }
        when (dialog) {
            1 -> com.mdev.mnotes.lab.nearby.PhoneLinkDialog(com.mdev.mnotes.core.nearby.LinkMode.PAIR, null) { dialog = 0; tick++ }
            2 -> com.mdev.mnotes.lab.nearby.PhoneLinkDialog(com.mdev.mnotes.core.nearby.LinkMode.SEND, null) { dialog = 0; tick++ }
            3 -> com.mdev.mnotes.lab.nearby.DiagnosticsDialog(null) { dialog = 0 }
            4 -> com.mdev.mnotes.lab.nearby.NotebookSendDialog({ dialog = 0 }) { file -> outgoing = file; dialog = 6 }
            5 -> com.mdev.mnotes.lab.nearby.PhoneLinkDialog(com.mdev.mnotes.core.nearby.LinkMode.RECEIVE, null) { dialog = 0 }
            6 -> com.mdev.mnotes.lab.nearby.PhoneLinkDialog(com.mdev.mnotes.core.nearby.LinkMode.SEND, null, outgoing?.let { listOf(it) }) { dialog = 0; outgoing = null }
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
