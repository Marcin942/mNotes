package com.mdev.mnotes.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mdev.mnotes.app.MNotesApp
import com.mdev.mnotes.app.app
import com.mdev.mnotes.data.ExportFormat
import com.mdev.mnotes.data.Exporter
import com.mdev.mnotes.core.codec.FileKind
import com.mdev.mnotes.core.text.PageSpan
import com.mdev.mnotes.data.ImportProbe
import com.mdev.mnotes.data.ImportSource
import com.mdev.mnotes.data.Importer
import com.mdev.mnotes.data.NotebookMeta
import com.mdev.mnotes.editor.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

private fun fmtLabel(f: ExportFormat) = when (f) {
    ExportFormat.PDF -> "PDF"
    ExportFormat.PNG -> "PNG"
    ExportFormat.SVG -> "SVG"
    ExportFormat.MD -> "Markdown"
    ExportFormat.MNOTES -> ".mnotes"
}

private fun fmtInfo(f: ExportFormat) = when (f) {
    ExportFormat.PDF -> T.t("Wektorowy dokument: tekst i pismo pozostają ostre, tło PDF zachowane.", "Vector document: text and ink stay sharp, PDF background preserved.")
    ExportFormat.PNG -> T.t("Obraz rastrowy każdej strony (kilka stron → archiwum ZIP).", "Raster image of each page (several pages → ZIP archive).")
    ExportFormat.SVG -> T.t("Grafika wektorowa do edycji w Inkscape (kilka stron → archiwum ZIP).", "Vector graphics for Inkscape (several pages → ZIP archive).")
    ExportFormat.MD -> T.t("Tylko tekst z pól tekstowych; pismo odręczne i obrazy nie wchodzą do pliku.", "Text boxes only; handwriting and images are not included.")
    ExportFormat.MNOTES -> T.t("Pełna kopia zeszytu (otwarty format ZIP) — do przeniesienia na inne urządzenie lub kopii zapasowej.", "Full notebook copy (open ZIP format) — for moving to another device or backup.")
}

private enum class ExportScope { ALL, PAGE, SPAN }

/** Eksport zeszytu: zapis do wybranego miejsca (SAF), udostępnienie lub wydruk. [prepare] zapisuje stan edytora przed odczytem z dysku. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(nb: String, currentPage: Int, prepare: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app
    val pageCount = remember(nb) { prepare(); app.repo.meta(nb)?.pages?.size ?: 0 }
    val title = app.repo.meta(nb)?.title ?: "mNotes"
    val exporter = remember { Exporter(app.repo, app.assets, app.pdf) }
    var fmt by remember { mutableStateOf(ExportFormat.PDF) }
    var scopeMode by remember { mutableStateOf(ExportScope.ALL) }
    var spanText by remember { mutableStateOf("${currentPage + 1}") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canScope = pageCount > 1 && fmt != ExportFormat.MNOTES
    val spanPages = if (canScope && scopeMode == ExportScope.SPAN) PageSpan.parse(spanText, pageCount) else null
    val spanInvalid = canScope && scopeMode == ExportScope.SPAN && spanPages == null
    val pages: List<Int>? = when {
        !canScope -> null
        scopeMode == ExportScope.PAGE -> listOf(currentPage.coerceIn(0, pageCount - 1))
        scopeMode == ExportScope.SPAN -> spanPages
        else -> null
    }
    val count = if (fmt == ExportFormat.MNOTES) pageCount else pages?.size ?: pageCount
    val zip = !fmt.multi && count > 1
    val name = exporter.fileName(
        title, fmt, if (!fmt.multi && count == 1 && pageCount > 1) (pages?.first() ?: 0) else null, zip,
        span = if (scopeMode == ExportScope.SPAN) spanPages?.let { PageSpan.label(it) } else null,
    )
    val mime = if (zip) "application/zip" else fmt.mime
    // Dla własnego rozszerzenia SAF dostaje typ ogólny, żeby nie dopisywać „.bin” ani „.zip”.
    val safMime = if (fmt == ExportFormat.MNOTES) "application/octet-stream" else mime

    /** Eksport do pliku w pamięci podręcznej (udostępnianie, wydruk). */
    suspend fun toCache(): File? = withContext(Dispatchers.IO) {
        exporter.awaitWrites()
        val dir = File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, name)
        val ok = FileOutputStream(f).use { exporter.write(nb, fmt, pages, it) }
        if (ok) f else { f.delete(); null }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(safMime)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                exporter.awaitWrites()
                val good = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { exporter.write(nb, fmt, pages, it) } ?: false }.getOrDefault(false)
                if (!good) runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, uri) }
                good
            }
            busy = false
            ctx.toast(if (ok) T.t("Zapisano: $name", "Saved: $name") else T.t("Eksport nie powiódł się", "Export failed"))
            if (ok) onDismiss()
        }
    }

    fun share() {
        busy = true
        scope.launch {
            val f = toCache()
            busy = false
            if (f == null) { ctx.toast(T.t("Eksport nie powiódł się", "Export failed")); return@launch }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { ctx.startActivity(Intent.createChooser(send, T.t("Udostępnij", "Share"))) }.onFailure { ctx.toast(T.t("Brak aplikacji do udostępniania", "No app to share with")) }
            onDismiss()
        }
    }

    fun print() {
        busy = true
        scope.launch {
            val f = toCache()
            busy = false
            if (f == null) { ctx.toast(T.t("Eksport nie powiódł się", "Export failed")); return@launch }
            val pm = ctx.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (pm == null) { ctx.toast(T.t("Drukowanie niedostępne", "Printing unavailable")); return@launch }
            pm.print(title, object : PrintDocumentAdapter() {
                override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: android.os.Bundle?) {
                    if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                    cb.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true)
                }
                override fun onWrite(range: Array<out PageRange>, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
                    try {
                        FileInputStream(f).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                        cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) { cb.onWriteFailed(e.message) }
                }
            }, null)
            onDismiss()
        }
    }

    MDialog(
        T.t("Eksport i udostępnianie", "Export & share"), { if (!busy) onDismiss() },
        confirm = T.t("Zapisz jako…", "Save as…"), onConfirm = { saver.launch(name) }, confirmEnabled = !busy && pageCount > 0 && !spanInvalid,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel(T.t("Format", "Format"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (f in ExportFormat.entries) Chip(fmtLabel(f), fmt == f, { if (!busy) fmt = f })
        }
        Text(fmtInfo(fmt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        if (canScope) {
            SectionLabel(T.t("Zakres", "Range"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(T.t("Cały zeszyt ($pageCount)", "Whole notebook ($pageCount)"), scopeMode == ExportScope.ALL, { if (!busy) scopeMode = ExportScope.ALL })
                Chip(T.t("Strona ${currentPage + 1}", "Page ${currentPage + 1}"), scopeMode == ExportScope.PAGE, { if (!busy) scopeMode = ExportScope.PAGE })
                Chip(T.t("Przedział stron", "Page range"), scopeMode == ExportScope.SPAN, { if (!busy) scopeMode = ExportScope.SPAN })
            }
            if (scopeMode == ExportScope.SPAN) {
                MField(spanText, { if (!busy) spanText = it }, Modifier.padding(top = 8.dp), hint = T.t("np. 2-5, 8, 10-", "e.g. 2-5, 8, 10-"))
                Text(
                    if (spanInvalid) T.t("Podaj numery stron od 1 do $pageCount, np. 2-5 lub 1-3, 8.", "Enter page numbers from 1 to $pageCount, e.g. 2-5 or 1-3, 8.")
                    else T.t("Wybrano ${spanPages?.size ?: 0} z $pageCount stron.", "${spanPages?.size ?: 0} of $pageCount pages selected."),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (spanInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        SectionLabel(T.t("Inne działania", "Other actions"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(T.t("Udostępnij…", "Share…"), false, { if (!busy && !spanInvalid) share() }, icon = Icons.Rounded.IosShare)
            if (fmt == ExportFormat.PDF) Chip(T.t("Drukuj…", "Print…"), false, { if (!busy && !spanInvalid) print() }, icon = Icons.Rounded.Print)
            if (busy) {
                CircularProgressIndicator(Modifier.padding(start = 8.dp).size(20.dp), strokeWidth = 2.dp, color = mint.accent)
                Text(T.t("Przygotowuję…", "Preparing…"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
            }
        }
    }
}

// ─────────────────────────────── Import ───────────────────────────────

/**
 * Importuje pliki (PDF, obrazy, .mnotes) jako nowe zeszyty. Praca na wątku IO, rejestracja w bibliotece na głównym.
 * Rodzaj pliku ustala [ImportProbe] po zawartości, bo menedżery plików podają obce typy i adresy bez rozszerzenia.
 * Pliki Samsung Notes (`.sdocx`, PDF zgodny z Notatkami) trafiają do [onSamsung] (okno z podglądem i wyborem trybu);
 * bez odbiorcy są pomijane. Zwraca utworzone zeszyty; użytkownik dostaje komunikat (także o plikach pominiętych).
 */
suspend fun importUris(
    app: MNotesApp,
    uris: List<Uri>,
    folderId: String?,
    mimeHint: String? = null,
    onSamsung: ((List<ImportSource>) -> Unit)? = null,
): List<NotebookMeta> {
    val ctx: Context = app
    val imp = Importer(app.repo, app.pdf)
    val made = ArrayList<NotebookMeta>()
    val samsung = ArrayList<ImportSource>()
    var skipped = 0
    withContext(Dispatchers.IO) {
        val images = ArrayList<ImportSource>()
        for (u in uris) {
            val src = ImportProbe.probe(ctx, u, mimeHint)
            if (src.isSamsung && onSamsung != null) { samsung.add(src); continue }
            val base = src.name.substringBeforeLast('.', src.name).ifBlank { "import" }
            val meta = runCatching {
                when (src.kind) {
                    FileKind.PDF -> ctx.contentResolver.openInputStream(u)?.use { imp.importPdf(it, base, folderId) }
                    FileKind.MNOTES -> ctx.contentResolver.openInputStream(u)?.use { imp.importMnotes(it, folderId) }
                    FileKind.IMAGE -> { images.add(src); null }
                    else -> null
                }
            }.getOrNull()
            if (meta != null) made.add(meta) else if (src.kind != FileKind.IMAGE) skipped++
        }
        if (images.isNotEmpty()) {
            val title = if (images.size == 1) images[0].name.substringBeforeLast('.') else T.t("Import obrazów", "Imported images")
            val sources = images.map { src ->
                val ext = src.name.lowercase().substringAfterLast('.', "").takeIf { it.matches(Regex("[a-z0-9]{2,4}")) } ?: "jpg"
                ({ ctx.contentResolver.openInputStream(src.uri) } as () -> java.io.InputStream?) to ext
            }
            val meta = runCatching { imp.importImages(sources, title, folderId) }.getOrNull()
            if (meta != null) made.add(meta) else skipped += images.size
        }
    }
    made.forEach { app.repo.addNotebook(it) }
    if (samsung.isNotEmpty()) onSamsung?.invoke(samsung)
    if (samsung.isEmpty() || made.isNotEmpty() || skipped > 0) ctx.toast(
        when {
            made.isEmpty() -> T.t("Nie udało się zaimportować plików", "Could not import the files")
            skipped > 0 -> T.t("Zaimportowano ${made.size}, pominięto $skipped", "Imported ${made.size}, skipped $skipped")
            else -> T.t("Zaimportowano: ${made.size}", "Imported: ${made.size}")
        },
    )
    return made
}

/**
 * Importuje [uris] do folderu [folder]: zwykłe pliki od razu jako zeszyty (jeden zeszyt się otwiera), pliki Samsung Notes
 * przez okno importu z podglądem ([LocalSamsungStart]).
 */
@Composable
private fun rememberImporter(onOpen: (String) -> Unit): (List<Uri>, String?) -> Unit {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val startSamsung = LocalSamsungStart.current
    return { uris, folder ->
        if (uris.isNotEmpty()) scope.launch {
            var handed = false
            val made = importUris(app, uris, folder, onSamsung = { handed = true; startSamsung(it, folder) })
            if (made.size == 1 && !handed) onOpen(made[0].id)
        }
    }
}

/** Zwraca funkcję otwierającą systemowy wybór plików do importu (kilka naraz); po imporcie jednego zeszytu otwiera go. */
@Composable
fun rememberImportLauncher(folderId: () -> String?, onOpen: (String) -> Unit): () -> Unit {
    val import = rememberImporter(onOpen)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> import(uris, folderId()) }
    return { launcher.launch(arrayOf("application/pdf", "image/*", "application/zip", "application/x-mnotes", "application/octet-stream", "*/*")) }
}

/** Zwraca funkcję otwierającą wybór folderu; importuje z niego wszystkie pliki (razem z podfolderami), rodzaj ustala zawartość. */
@Composable
fun rememberFolderImportLauncher(folderId: () -> String?, onOpen: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val import = rememberImporter(onOpen)
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            val folder = folderId()
            scope.launch {
                val files = withContext(Dispatchers.IO) { ImportProbe.listTree(ctx, tree) }
                if (files.isEmpty()) ctx.toast(T.t("W tym folderze nie ma plików", "There are no files in this folder")) else import(files, folder)
            }
        }
    }
    return { launcher.launch(null) }
}
