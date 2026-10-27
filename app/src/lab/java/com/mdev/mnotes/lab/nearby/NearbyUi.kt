package com.mdev.mnotes.lab.nearby

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.app.MNotesApp
import com.mdev.mnotes.core.nearby.LinkMachine
import com.mdev.mnotes.core.nearby.LinkMode
import com.mdev.mnotes.core.nearby.LinkPermissions
import com.mdev.mnotes.core.nearby.LinkState
import com.mdev.mnotes.core.nearby.SyncMsg
import com.mdev.mnotes.core.nearby.SyncRole
import com.mdev.mnotes.data.ExportFormat
import com.mdev.mnotes.data.Exporter
import com.mdev.mnotes.data.Importer
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.ui.MDialog
import com.mdev.mnotes.ui.mint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Rola w parowaniu z ekranu: duży (≥ 600 dp) to tablet i się ogłasza, mniejszy to telefon i szuka (§3.2). */
private fun defaultRole(ctx: Context) = if (ctx.resources.configuration.smallestScreenWidthDp >= 600) SyncRole.HOST else SyncRole.CLIENT

/** Nazwa urządzenia widoczna na drugim ekranie: nazwa z ustawień systemu, a bez niej producent i model. */
private fun deviceName(ctx: Context): String =
    runCatching { Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()?.takeIf { it.isNotBlank() }?.take(40)
        ?: "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(40)

private fun missing(ctx: Context) = LinkPermissions.missing(Build.VERSION.SDK_INT) { LinkDiagnostics.has(ctx, it) }

/** Schodki kolejnych zdjęć z telefonu: przesunięcie w pt i po ilu zdjęciach od nowa. */
private const val CASCADE_STEP = 24f
private const val CASCADE = 6

/**
 * Jedno okno łącza telefon ↔ tablet po obu stronach (2b-B1, `docs/NEARBY-PRZEBUDOWA.md` §3.7): ikona i duże zdanie
 * stanu, pod nim jedna rzecz zależna od stanu (lista znalezionych, sześć cyfr, postęp, lista „co sprawdzić”), na dole
 * „Diagnostyka”. [start]: parowanie (oba), odbiór (tablet, [ed] = gdzie wstawiać zdjęcia; bez [ed] odbiera zeszyty),
 * wysyłanie (telefon: zdjęcia z galerii albo gotowe [files], np. zeszyt `.mnotes`). Bez pary odbiór i wysyłanie najpierw
 * parują w tym samym oknie. Logika w `core/nearby/LinkMachine`, tu tylko pokazywanie i uprawnienia.
 */
@Composable
fun PhoneLinkDialog(start: LinkMode, ed: Editor?, files: List<SyncMsg.Photo>? = null, close: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val notebooks = if (start == LinkMode.SEND) files != null else ed == null
    var imported by remember { mutableIntStateOf(0) }
    val store = remember { PairStore(ctx) }
    val me = remember { deviceName(ctx) }
    var key by remember { mutableStateOf(if (start == LinkMode.PAIR) null else store.loadOrForget()) }
    val expired = remember { start != LinkMode.PAIR && key == null && store.peerName.isNotEmpty() }
    var mode by remember { mutableStateOf(if (start != LinkMode.PAIR && key == null) LinkMode.PAIR else start) }
    var role by remember(mode) { mutableStateOf(when (mode) { LinkMode.PAIR -> defaultRole(ctx); LinkMode.RECEIVE -> SyncRole.HOST; LinkMode.SEND -> SyncRole.CLIENT }) }
    var allowed by remember { mutableStateOf(missing(ctx).isEmpty()) }
    var refused by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LinkState>(LinkState.Idle) }
    var received by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf(files != null) }
    var note by remember { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf(false) }
    val waiting = remember { ArrayList<SyncMsg.Photo>(files.orEmpty()) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = missing(ctx).isEmpty()
        refused = !allowed
    }
    val ready = allowed && (mode == LinkMode.PAIR || key != null) && (mode != LinkMode.SEND || picked)
    val link = remember(mode, role, generation, ready, key?.contentHashCode()) {
        if (!ready) null else {
            val t = NearbyTransport(ctx)
            val m = LinkMachine(t, mode, role, me, if (mode == LinkMode.PAIR) null else key, store.peerName, listener = object : LinkMachine.Listener {
                override fun changed(s: LinkState) { state = s }
                override fun photo(p: SyncMsg.Photo) {
                    if (p.mime == ExportFormat.MNOTES.mime) {
                        // zeszyt z drugiego urządzenia: kopia jako nowy zeszyt w bibliotece (nic nie jest nadpisywane)
                        val app = ctx.applicationContext as MNotesApp
                        scope.launch {
                            val meta = withContext(Dispatchers.IO) { runCatching { Importer(app.repo, app.pdf).importMnotes(p.bytes.inputStream(), null) }.getOrNull() }
                            if (meta == null) { note = T.t("Nie udało się dodać zeszytu z drugiego urządzenia.", "Could not add the notebook from the other device."); return@launch }
                            app.repo.addNotebook(meta)
                            imported++
                            note = T.t("Dodano zeszyt „${meta.title}” do biblioteki.", "Added the notebook “${meta.title}” to the library.")
                        }
                        return
                    }
                    val e = ed ?: run { note = T.t("Przyszło zdjęcie, ale to okno nie jest otwarte w zeszycie.", "A photo arrived, but this window is not open in a notebook."); return }
                    val k = received % CASCADE
                    e.insertImage(p.bytes.inputStream(), if ("png" in p.mime) "png" else "jpg") { if (k > 0) e.nudgeSel(k * CASCADE_STEP, k * CASCADE_STEP) }
                    received++
                }
            })
            t.machine = m
            m to t
        }
    }
    DisposableEffect(link) {
        link?.first?.let { m ->
            m.start()
            for (p in waiting) m.sendPhoto(p.name, p.mime, p.bytes)
            waiting.clear()
        }
        onDispose { link?.let { (m, t) -> m.stop(); t.close() } }
    }
    LaunchedEffect(link) {
        val m = link?.first ?: return@LaunchedEffect
        while (true) { delay(1_000); m.tick() }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) { if (!picked) close(); return@rememberLauncherForActivityResult }
        val photo = readPhoto(ctx, uri)
        if (photo == null) { note = T.t("Nie udało się wczytać zdjęcia.", "Could not read the photo."); return@rememberLauncherForActivityResult }
        note = null
        picked = true
        link?.first?.sendPhoto(photo.name, photo.mime, photo.bytes) ?: waiting.add(photo)
    }
    fun pickPhoto() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    LaunchedEffect(mode, allowed) { if (mode == LinkMode.SEND && allowed && !picked && files == null) pickPhoto() }

    val title = when (start) {
        LinkMode.PAIR -> T.t("Połącz telefon z tabletem", "Connect phone and tablet")
        LinkMode.RECEIVE -> if (notebooks) T.t("Odbierz zeszyt", "Receive a notebook") else T.t("Zdjęcie z telefonu", "Photo from phone")
        LinkMode.SEND -> if (notebooks) T.t("Wyślij zeszyt", "Send a notebook") else T.t("Wyślij zdjęcie na tablet", "Send photo to tablet")
    }
    val digits = state as? LinkState.ConfirmDigits
    val sentSome = ((state as? LinkState.Ready)?.sent ?: 0) > 0
    MDialog(
        title, onDismiss = close,
        confirm = when {
            digits != null -> T.t("Zgadza się", "They match")
            mode == LinkMode.SEND && files == null && picked && (state is LinkState.Ready || state is LinkState.Sending) -> T.t("Następne zdjęcie", "Next photo")
            else -> null
        },
        onConfirm = {
            val result = link?.first?.pairResult
            if (digits != null && result != null) {
                when {
                    !store.save(result.pairKey, result.peerName) -> note = T.t("Nie udało się zapisać pary w magazynie kluczy urządzenia.", "Could not store the pairing in the device keystore.")
                    start == LinkMode.PAIR -> close()
                    else -> { key = result.pairKey; mode = start; note = null }
                }
            } else pickPhoto()
        },
        dismissText = if (received > 0 || imported > 0 || sentSome) T.t("Gotowe", "Done") else T.t("Zamknij", "Close"),
    ) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            if (!allowed) PermissionsPart(ctx, refused) { ask.launch(LinkPermissions.required(Build.VERSION.SDK_INT).toTypedArray()) }
            else {
                if (expired && mode == LinkMode.PAIR) Line(T.t("Para wygasła (np. po przywróceniu kopii zapasowej): połącz urządzenia jeszcze raz.", "The pairing expired (e.g. after restoring a backup): connect the devices again."))
                else if (mode == LinkMode.PAIR && start != LinkMode.PAIR) Line(T.t("Najpierw połącz telefon z tabletem (raz).", "First connect the phone and the tablet (once)."))
                StatePart(state, mode, role, me, store.peerName, if (notebooks) imported else received, link?.first, notebooks)
                if (mode == LinkMode.PAIR && digits == null) {
                    TextButton(onClick = { role = if (role == SyncRole.HOST) SyncRole.CLIENT else SyncRole.HOST }) {
                        Text(if (role == SyncRole.HOST) T.t("To urządzenie: tablet (odbiera) · zmień", "This device: tablet (receives) · change") else T.t("To urządzenie: telefon (wysyła) · zmień", "This device: phone (sends) · change"))
                    }
                }
                if (digits != null) TextButton(onClick = { generation++ }) { Text(T.t("Inne cyfry? Połącz od nowa", "Different digits? Start over")) }
            }
            note?.let { Line(it) }
            TextButton(onClick = { diagnostics = true }) { Text(T.t("Diagnostyka", "Diagnostics")) }
        }
    }
    if (diagnostics) DiagnosticsDialog(link?.first) { diagnostics = false }
}

/** Ekran „Dlaczego” przed prośbą systemową; po odmowie droga do ustawień aplikacji. */
@Composable
private fun PermissionsPart(ctx: Context, refused: Boolean, ask: () -> Unit) {
    Status(Icons.Rounded.Lock, if (refused) T.t("Bez tych zgód urządzenia się nie znajdą.", "Without these permissions the devices cannot find each other.") else T.t("Łącze potrzebuje zgód Androida:", "The link needs Android permissions:"))
    for (w in missing(ctx).map { LinkDiagnostics.why(it) }.distinct()) Line("• $w")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = ask) { Text(T.t("Pozwól", "Allow")) }
        if (refused) TextButton(onClick = {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text(T.t("Ustawienia aplikacji", "App settings")) }
    }
}

/** Zdanie stanu z ikoną i jedna rzecz pod nim. */
@Composable
private fun StatePart(state: LinkState, mode: LinkMode, role: SyncRole, me: String, peer: String, received: Int, machine: LinkMachine?, notebooks: Boolean) {
    val host = role == SyncRole.HOST
    when (state) {
        LinkState.Idle -> Status(Icons.Rounded.Bluetooth, T.t("Przygotowuję łącze…", "Preparing the link…"))
        is LinkState.Searching -> {
            val text = when {
                host && mode == LinkMode.PAIR -> T.t("Widać mnie jako „$me”. Na telefonie otwórz „Połącz telefon z tabletem”.", "Visible as “$me”. On the phone open “Connect phone and tablet”.")
                host && notebooks -> T.t("Czekam na „$peer”. Na drugim urządzeniu: Ustawienia → Laboratorium → „Wyślij zeszyt…”.", "Waiting for “$peer”. On the other device: Settings → Lab → “Send a notebook…”.")
                host -> T.t("Czekam na telefon „$peer”. Na telefonie: Ustawienia → Laboratorium → „Wyślij zdjęcie na tablet”.", "Waiting for the phone “$peer”. On the phone: Settings → Lab → “Send photo to tablet”.")
                state.found.isNotEmpty() -> T.t("Znaleziono — stuknij tablet:", "Found — tap the tablet:")
                mode == LinkMode.PAIR -> T.t("Szukam tabletu… Na tablecie otwórz „Połącz telefon z tabletem”.", "Looking for the tablet… On the tablet open “Connect phone and tablet”.")
                notebooks -> T.t("Szukam „$peer”… Na drugim urządzeniu otwórz „Odbierz zeszyt”.", "Looking for “$peer”… On the other device open “Receive a notebook”.")
                else -> T.t("Szukam tabletu „$peer”… Na tablecie otwórz „Zdjęcie z telefonu”.", "Looking for the tablet “$peer”… On the tablet open “Photo from phone”.")
            }
            Status(Icons.Rounded.BluetoothSearching, text)
            if (state.found.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in state.found) TextButton(onClick = { machine?.choose(p.id) }) { Text(p.name) }
            }
            if (state.slow) {
                Line(T.t("Nie widać drugiego urządzenia. Sprawdź:", "The other device is not visible. Check:"))
                Line(T.t("• na drugim urządzeniu otwarte to samo okno (tablet: „Zdjęcie z telefonu”),", "• the same window is open on the other device (tablet: “Photo from phone”),"))
                Line(T.t("• Bluetooth i lokalizacja włączone na obu,", "• Bluetooth and location are on on both,"))
                Line(T.t("• urządzenia blisko siebie (kilka metrów).", "• the devices are close (a few metres)."))
            }
        }
        is LinkState.Connecting -> Status(Icons.Rounded.BluetoothSearching, T.t("Łączę z „${state.peer}”…", "Connecting to “${state.peer}”…"))
        is LinkState.Securing -> Status(Icons.Rounded.Lock, T.t("Zabezpieczam połączenie…", "Securing the connection…"))
        is LinkState.ConfirmDigits -> {
            Status(Icons.Rounded.Lock, T.t("Czy na obu ekranach są te same cyfry?", "Do both screens show the same digits?"))
            Text(state.sas, style = MaterialTheme.typography.displayMedium, color = mint.accentText, modifier = Modifier.padding(vertical = 10.dp))
            Line(T.t("Połączono z „${state.peer}”. Inne cyfry znaczą, że ktoś mógł się wtrącić.", "Connected to “${state.peer}”. Different digits mean someone may be interfering."))
        }
        is LinkState.Ready -> Status(
            Icons.Rounded.CheckCircle,
            when {
                host && mode != LinkMode.PAIR && notebooks -> if (received == 0) T.t("Połączono z „${state.peer}”. Zeszyty trafią do biblioteki.", "Connected to “${state.peer}”. Notebooks will go to the library.")
                else T.t("Dodano zeszytów: $received.", "Notebooks added: $received.")
                host && mode != LinkMode.PAIR -> if (received == 0) T.t("Połączono z „${state.peer}”. Zdjęcia trafią na tę stronę.", "Connected to “${state.peer}”. Photos will land on this page.")
                else T.t("Wstawiono zdjęć: $received. Możesz wysłać kolejne.", "Photos inserted: $received. You can send more.")
                state.sent > 0 && notebooks -> T.t("Zeszyt jest już na drugim urządzeniu.", "The notebook is on the other device.")
                state.sent > 0 -> T.t("Wstawiono na tablecie zdjęć: ${state.sent}.", "Photos inserted on the tablet: ${state.sent}.")
                else -> T.t("Połączono z „${state.peer}”.", "Connected to “${state.peer}”.")
            },
        )
        is LinkState.Sending -> {
            val pct = (state.progress * 100).toInt()
            val part = minOf(state.part + 1, state.parts)
            Status(Icons.Rounded.PhoneAndroid, if (notebooks) T.t("Wysyłam zeszyt · część $part z ${state.parts} · $pct %", "Sending the notebook · part $part of ${state.parts} · $pct %")
                else T.t("Wysyłam zdjęcie · część $part z ${state.parts} · $pct %", "Sending the photo · part $part of ${state.parts} · $pct %"))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = mint.accent)
            if (state.queued > 0) Line(T.t("W kolejce: ${state.queued}", "Queued: ${state.queued}"))
        }
        is LinkState.Problem -> {
            Status(Icons.Rounded.Warning, T.t(state.problem.pl, state.problem.en))
            if (state.retrying) Line(T.t("Spróbuję ponownie za chwilę.", "I will retry in a moment."))
            else TextButton(onClick = { machine?.retry() }) { Text(T.t("Spróbuj ponownie", "Try again")) }
        }
    }
}

@Composable
private fun Status(icon: ImageVector, text: String) = Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
    Icon(icon, null, tint = mint.accentText, modifier = Modifier.size(28.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
}

@Composable
private fun Line(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))

/**
 * Wybór zeszytu do wysłania (wersja 1 synchronizacji): eksport `.mnotes` tym samym kodem co „Eksport i udostępnianie”,
 * potem [send] z gotowym plikiem. Odbiorca dodaje go jako nowy zeszyt — nic po żadnej stronie nie jest nadpisywane.
 */
@Composable
fun NotebookSendDialog(close: () -> Unit, send: (SyncMsg.Photo) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as MNotesApp
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val list = app.repo.notebooks.filter { it.trashedAt == 0L }.sortedByDescending { it.modified }
    MDialog(T.t("Wyślij zeszyt", "Send a notebook"), close, dismissText = T.t("Anuluj", "Cancel")) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            Line(
                if (busy != null) T.t("Przygotowuję „$busy”…", "Preparing “$busy”…")
                else T.t("Wybierz zeszyt. Drugie urządzenie doda go jako nowy zeszyt (nic nie nadpisuje).", "Choose a notebook. The other device adds it as a new notebook (nothing is overwritten)."),
            )
            if (list.isEmpty()) Line(T.t("Nie ma jeszcze żadnego zeszytu.", "There are no notebooks yet."))
            for (m in list) TextButton(onClick = {
                if (busy != null) return@TextButton
                busy = m.title
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching {
                            val exporter = Exporter(app.repo, app.assets, app.pdf)
                            exporter.awaitWrites()
                            ByteArrayOutputStream().also { exporter.write(m.id, ExportFormat.MNOTES, null, it) }.toByteArray()
                        }.getOrNull()
                    }
                    busy = null
                    when {
                        bytes == null || bytes.isEmpty() -> note = T.t("Nie udało się przygotować zeszytu.", "Could not prepare the notebook.")
                        bytes.size > LinkMachine.FILE_MAX -> note = T.t("Zeszyt ma ${bytes.size shr 20} MB, łącze przyjmuje do ${LinkMachine.FILE_MAX shr 20} MB. Wyślij go przez „Eksport i udostępnianie”.", "The notebook is ${bytes.size shr 20} MB; the link takes up to ${LinkMachine.FILE_MAX shr 20} MB. Send it via “Export & share”.")
                        else -> send(SyncMsg.Photo(m.title.take(60) + "." + ExportFormat.MNOTES.ext, ExportFormat.MNOTES.mime, bytes))
                    }
                }
            }) { Text(m.title, modifier = Modifier.fillMaxWidth()) }
            note?.let { Line(it) }
        }
    }
}

/** „Diagnostyka”: sprawdzenia ✓/✗, ostatnie zdarzenia łącza (gdy okno łącza jest otwarte) i „Kopiuj raport”. */
@Composable
fun DiagnosticsDialog(machine: LinkMachine?, close: () -> Unit) {
    val ctx = LocalContext.current
    val checks = remember { LinkDiagnostics.checks(ctx) }
    val log = machine?.log.orEmpty()
    MDialog(
        T.t("Diagnostyka łącza", "Link diagnostics"), close,
        confirm = T.t("Kopiuj raport", "Copy report"),
        onConfirm = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("mNotes Nearby", LinkDiagnostics.report(ctx, log)))
            close()
        },
        dismissText = T.t("Zamknij", "Close"),
    ) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            Line("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            for (c in checks) Text("${LinkDiagnostics.mark(c.ok)}  ${c.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            if (log.isNotEmpty()) {
                Text(T.t("Ostatnie zdarzenia", "Last events"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                for (e in log.asReversed()) Line(LinkDiagnostics.event(e))
            }
        }
    }
}

/** Zdjęcie z galerii jako wiadomość: duże zmniejszone do 3000 px i JPEG 88 (szybciej przez Bluetooth). */
private fun readPhoto(ctx: Context, uri: Uri): SyncMsg.Photo? = runCatching {
    val cr = ctx.contentResolver
    val mime = cr.getType(uri).orEmpty().ifEmpty { "image/jpeg" }
    val raw = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    val small = raw.size <= 4 shl 20 && maxOf(bounds.outWidth, bounds.outHeight) <= 3000 && ("jpeg" in mime || "png" in mime)
    if (small) return SyncMsg.Photo(uri.lastPathSegment ?: "zdjecie", mime, raw)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 3000) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
    bmp.recycle()
    SyncMsg.Photo((uri.lastPathSegment ?: "zdjecie") + ".jpg", "image/jpeg", out.toByteArray())
}.getOrNull()
