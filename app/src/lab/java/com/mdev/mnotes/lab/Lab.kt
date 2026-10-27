package com.mdev.mnotes.lab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.app.LabFeatures
import com.mdev.mnotes.core.image.BoardOptions
import com.mdev.mnotes.core.image.BoardPipeline
import com.mdev.mnotes.core.image.ChalkVector
import com.mdev.mnotes.core.image.Raster
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.ui.Choice
import com.mdev.mnotes.ui.Group
import com.mdev.mnotes.ui.MDialog
import com.mdev.mnotes.ui.MSlider
import com.mdev.mnotes.ui.SectionLabel
import com.mdev.mnotes.ui.ToggleRow
import com.mdev.mnotes.ui.mint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** Funkcje wariantu lab widziane przez wspólny kod przez [LabFeatures]. */
object Lab : LabFeatures {

    /** Rozmiar podglądu w panelu: szybki na tablecie, wystarczy do oceny progu. */
    private const val PREVIEW = 520

    /** Kolor kresek z wektoryzacji, gdy kreda zostaje w oryginalnych barwach (grafit czytelny na jasnej stronie). */
    private const val GRAPHITE = 0xFF37474F.toInt()

    @Composable
    override fun BoardPanel(ed: Editor, imgs: List<ImageEl>, close: () -> Unit) {
        val set = LabSettings.of(LocalContext.current)
        val files = remember(imgs.map { it.asset }) { imgs.map { ed.repo.assetFile(ed.nbId, it.asset) } }
        val many = imgs.size > 1
        var opt by remember { mutableStateOf(set.options()) }
        var asStrokes by remember { mutableStateOf(false) }
        var preview by remember { mutableStateOf<Bitmap?>(null) }
        var info by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val sources by produceState<List<Raster>?>(null, files) {
            value = withContext(Dispatchers.IO) { files.map { loadRaster(it, PREVIEW) ?: return@withContext null } }
        }

        LaunchedEffect(sources, opt) {
            val src = sources ?: return@LaunchedEffect
            val res = withContext(Dispatchers.Default) { BoardPipeline.runMany(src, opt.copy(maxSide = PREVIEW)) }
            preview = res.image.toBitmap()
            info = T.t(
                "Tło ${if (res.black) "ciemne" else "jasne"} · próg ${"%.2f".format(res.cut)}" + (if (many) " · mediana ${imgs.size} zdjęć" else ""),
                "${if (res.black) "Dark" else "Light"} background · cut ${"%.2f".format(res.cut)}" + (if (many) " · median of ${imgs.size} photos" else ""),
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
                ed.repo.io.execute {
                    val full = files.map { loadRaster(it, chosen.maxSide) }
                    val out = if (full.any { it == null }) null else BoardPipeline.runMany(full.filterNotNull(), chosen).image
                    val post = { f: () -> Unit -> ed.view?.post(f) }
                    when {
                        out == null -> post { busy = false; ed.say(T.t("Nie udało się wczytać zdjęcia", "Could not load the photo")) }
                        strokes -> {
                            val els = toStrokes(out, imgs[0], chosen.tint ?: GRAPHITE)
                            post {
                                busy = false
                                if (els.isEmpty()) ed.say(T.t("Nie znaleziono kredy do zamiany na kreski", "No chalk found to turn into strokes"))
                                else { ed.addOnSelPage(els); close() }
                            }
                        }
                        many -> {
                            val png = encodePng(out)
                            post {
                                if (png == null) busy = false
                                else addImage(ed, imgs[0], png, out) { busy = false; close() }
                            }
                        }
                        else -> {
                            val png = encodePng(out)
                            post {
                                if (png == null) busy = false
                                else ed.replaceSelImageAsset(imgs[0].id, png, out.w, out.h) { busy = false; if (it) close() }
                            }
                        }
                    }
                }
            },
        ) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                PreviewBox(preview)
                Text(
                    if (sources == null) T.t("Wczytuję zdjęcie…", "Loading the photo…") else info,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                )
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

    /** Podgląd wyniku na szachownicy (przezroczystość widać jak w edytorach grafiki). */
    @Composable
    private fun PreviewBox(bmp: Bitmap?) {
        val m = mint
        val shape = RoundedCornerShape(10.dp)
        val ratio = bmp?.let { it.width.toFloat() / it.height }?.coerceIn(0.5f, 3f) ?: 1.5f
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(shape).border(1.dp, m.line, shape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val cell = 12.dp.toPx()
                val light = Color(0xFFF2F2F2); val dark = Color(0xFFDADADA)
                var y = 0f; var row = 0
                while (y < size.height) {
                    var x = 0f; var col = row
                    while (x < size.width) {
                        drawRect(if (col % 2 == 0) light else dark, Offset(x, y), Size(cell, cell))
                        x += cell; col++
                    }
                    y += cell; row++
                }
            }
            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth())
            else Text(T.t("Podgląd pojawi się za chwilę", "The preview will appear in a moment"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    override fun phoneButton(): Boolean = LabSettings.of(LocalContext.current).nearby

    @Composable
    override fun PhonePanel(ed: Editor, close: () -> Unit) = com.mdev.mnotes.lab.nearby.ReceivePhotosDialog(ed, close)

    @Composable
    override fun SettingsSection() {
        val set = LabSettings.of(LocalContext.current)
        NearbySection(set)
        Group(T.t("Kreda: zdjęcia tablicy", "Chalk: blackboard photos"), keys = "kreda tablica zdjęcie oczyść prostowanie próg chalk board photo clean") {
            Text(
                T.t(
                    "„Oczyść tablicę” w menu zaznaczonego zdjęcia zostawia samą kredę na przezroczystym tle; kilka zaznaczonych zdjęć tej samej tablicy łączy medianą, która usuwa wykładowcę. Tu wybierasz, które kroki są włączone na start; w panelu zmienisz je dla jednego zdjęcia.",
                    "“Clean up the board” in the selected photo's menu keeps only the chalk on a transparent background; several selected photos of the same board are merged with a median that removes the lecturer. Here you choose which steps start enabled; the panel changes them for one photo.",
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp),
            )
            Steps(set.options()) { o ->
                set.straighten = o.straighten; set.crop = o.crop; set.flatten = o.flatten; set.sharpen = o.sharpen
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
        Group(T.t("Telefon i tablet (Nearby)", "Phone and tablet (Nearby)"), keys = "nearby telefon tablet zdjęcie bluetooth parowanie wyślij phone pair send") {
            Text(
                T.t(
                    "Zdjęcie zrobione telefonem trafia od razu na stronę na tablecie, przez Bluetooth albo Wi-Fi Direct, bez internetu i bez wspólnej sieci. Wymaga Usług Google Play na obu urządzeniach; każda ramka jest szyfrowana kluczem pary.",
                    "A photo taken with the phone lands right on the tablet page, over Bluetooth or Wi-Fi Direct, without internet or a shared network. Needs Google Play services on both devices; every frame is encrypted with the pair key.",
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
                androidx.compose.material3.TextButton(onClick = { dialog = 1 }) { Text(T.t("Sparuj z drugim urządzeniem", "Pair with the other device")) }
                if (store.paired) {
                    androidx.compose.material3.TextButton(onClick = { dialog = 2 }) { Text(T.t("Wyślij zdjęcie na tablet (na telefonie)", "Send a photo to the tablet (on the phone)")) }
                    androidx.compose.material3.TextButton(onClick = { store.clear(); tick++ }) { Text(T.t("Usuń parę", "Forget the pair"), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        when (dialog) {
            1 -> com.mdev.mnotes.lab.nearby.PairDialog(com.mdev.mnotes.core.nearby.SyncRole.CLIENT) { dialog = 0; tick++ }
            2 -> com.mdev.mnotes.lab.nearby.SendPhotoDialog { dialog = 0 }
        }
    }

    /** Kreski pióra z kredy, ułożone tam, gdzie leży zdjęcie [over] (z jego obrotem); grubość przeliczona na pt. */
    fun toStrokes(chalk: Raster, over: ImageEl, color: Int): List<Element> {
        val lines = ChalkVector.vectorize(chalk)
        val k = over.w / chalk.w
        val c = cos(over.rot); val s = sin(over.rot)
        return lines.map { l ->
            val pts = FloatArray(l.size * 3)
            for (i in 0 until l.size) {
                val lx = (l.pts[2 * i] + 0.5f) * k - over.w / 2f
                val ly = (l.pts[2 * i + 1] + 0.5f) * k - over.h / 2f
                pts[3 * i] = over.cx + lx * c - ly * s
                pts[3 * i + 1] = over.cy + lx * s + ly * c
                pts[3 * i + 2] = 1f
            }
            Stroke(Ids.next(), PenKind.BALLPOINT, color or (0xFF shl 24), (l.width * k).coerceIn(0.4f, 12f), pts, poly = true)
        }
    }

    /** Nowy obraz (wynik mediany) w miejscu pierwszego zdjęcia, ta sama szerokość. */
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

    /** Plik obrazu jako [Raster] z dłuższym bokiem co najwyżej [longest] (dekodowanie z próbkowaniem, bez OOM). */
    fun loadRaster(file: File, longest: Int): Raster? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longest) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: return null
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
