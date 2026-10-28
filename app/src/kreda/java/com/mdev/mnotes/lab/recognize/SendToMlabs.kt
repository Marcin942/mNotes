package com.mdev.mnotes.lab.recognize

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.render.PageRenderer
import com.mdev.mnotes.ui.MDialog
import com.mdev.mnotes.ui.MField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

/**
 * „Wyślij stronę do mLabs” (K4, pierwszy krok z `docs/INTEGRACJA-MLABS.md`): strona jako PNG (dłuższy bok 1600 px) i
 * JSON `mnotes-strona` v1 obok, udostępnione tak jak eksport (`FileProvider`, katalog `cache/share`). Bez intencji do
 * konkretnego pakietu: mLabs nie jest w tym repozytorium, więc wybiera użytkownik. Rozpoznawania pisma jeszcze nie
 * ma ([NoRecognizer]); tekst strony można dopisać ręcznie — trafia jako blok `silnik: uzytkownik`, pewność 1.
 */
object SendToMlabs {
    const val PAGE_PX = 1600

    /** Dodatek intencji z nazwą pliku JSON (dla mLabs, gdy kiedyś zacznie przyjmować strony). */
    const val EXTRA_PAGE = "com.mdev.mnotes.extra.PAGE"

    private val main = Handler(Looper.getMainLooper())

    @Composable
    fun Dialog(ed: Editor, index: Int, work: CoroutineScope, close: () -> Unit) {
        val page = remember(index) { ed.doc.pages.getOrNull(index) }
        var text by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val title = remember { ed.repo.meta(ed.nbId)?.title ?: T.t("Zeszyt", "Notebook") }
        MDialog(
            T.t("Wyślij stronę do mLabs", "Send page to mLabs"), { if (!busy) close() },
            confirm = if (busy) T.t("Przygotowuję…", "Preparing…") else T.t("Udostępnij…", "Share…"), confirmEnabled = !busy && page != null,
            onConfirm = {
                val p = page ?: return@MDialog
                busy = true
                // Strona i jej zawartość brane w wątku UI, renderowanie i zapis w tle (jak wycinek F14).
                val spec = p.spec
                val content = ed.doc.content(p)
                val note = text
                work.launch {
                    var sent = false
                    try {
                        sent = runCatching { share(ed, title, index + 1, spec, content, note) }.getOrDefault(false)
                    } finally {
                        // Zawsze przez główny wątek, nie przez `ed.view`: bez widoku (obrót, zamknięty zeszyt) okno
                        // zostawało na zawsze w „Przygotowuję…” (2c-B5).
                        main.post {
                            busy = false
                            if (!sent) ed.say(T.t("Nie udało się przygotować strony", "Could not prepare the page"))
                            close()
                        }
                    }
                }
            },
        ) {
            Text(
                T.t(
                    "Strona ${index + 1} zeszytu „$title” pójdzie jako obraz PNG i plik JSON w formacie mLabs. W mLabs zapytasz o nią: „sprawdź moje obliczenia”, „zadaj mi 3 pytania”.",
                    "Page ${index + 1} of “$title” goes as a PNG image and a JSON file in the mLabs format. In mLabs you can ask about it: “check my calculations”, “ask me 3 questions”.",
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
            )
            MField(text, { text = it }, hint = T.t("Tekst strony (opcjonalnie)", "Page text (optional)"), singleLine = false)
            Text(
                T.t(
                    "Rozpoznawania pisma jeszcze nie ma: przepisz najważniejsze wzory albo zostaw puste — mLabs dostanie sam obraz.",
                    "Handwriting recognition is not here yet: type the key formulas or leave it empty — mLabs gets the image only.",
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    /** Renderuje stronę, zapisuje PNG i JSON w `cache/share/mlabs` i otwiera wybór aplikacji; false, gdy się nie udało. */
    private fun share(ed: Editor, title: String, number: Int, spec: PageSpec, content: PageContent, note: String): Boolean {
        val ctx: Context = ed.ctx
        val png = render(ed, spec, content) ?: return false
        val stem = PageExport.fileStem(title, number)
        val dir = File(ctx.cacheDir, "share/mlabs").apply { mkdirs() }
        val image = File(dir, "$stem.png").apply { writeBytes(png) }
        val blocks = listOfNotNull(PageExport.userBlock(note, spec.w, spec.h))
        val json = File(dir, "$stem.json").apply { writeText(PageExport.json(title, number, LocalDate.now().toString(), image.name, spec.w, spec.h, blocks)) }
        val uris = arrayListOf(image, json).map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", it) }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            .putExtra(Intent.EXTRA_SUBJECT, "$title — ${T.t("strona", "page")} $number")
            .putExtra(EXTRA_PAGE, json.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, T.t("Wyślij do mLabs", "Send to mLabs")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(chooser) }.isSuccess
    }

    /** Strona z papierem, wzorem, tłem PDF i treścią jako PNG o dłuższym boku [PAGE_PX] (jak wycinek F14 w `Editor`). */
    private fun render(ed: Editor, spec: PageSpec, content: PageContent): ByteArray? = runCatching {
        val sc = PAGE_PX / max(spec.w, spec.h)
        val wPx = ceil(spec.w * sc).toInt().coerceAtLeast(1); val hPx = ceil(spec.h * sc).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(sc, sc)
        val renderer = PageRenderer(ed.app.assets)
        renderer.drawPaper(c, spec, sc, null)
        spec.pdf?.let { pdf ->
            ed.app.pdf.renderTile(ed.nbId, pdf.asset, pdf.page, sc, 0, 0, wPx, hPx)?.let { bg -> c.drawBitmap(bg, null, RectF(0f, 0f, wPx / sc, hPx / sc), null) }
        }
        renderer.drawElements(c, ed.nbId, content, 0, Bounds(0f, 0f, spec.w, spec.h), sc, true, spec.darkPaper)
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it); bmp.recycle() }.toByteArray()
    }.getOrNull()
}
