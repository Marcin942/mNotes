package com.mdev.mnotes.lab.nearby

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.core.nearby.LinkJournal
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.ui.MDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * „Dziennik łącza” (3b-B2, Ustawienia → Rozszerzenia → Łącze): sesje od najnowszej, wpisy wybranej sesji, „Wyczyść”
 * i „Eksportuj” do pliku tekstowego przez systemowe okno zapisu (bez INTERNET; plik można potem wysłać dowolnie).
 * Dopisek mówi, co jest w pliku, a czego nie ma — to samo stoi w nagłówku eksportu.
 */
@Composable
fun LinkJournalDialog(close: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { LinkJournalStore.of(ctx) }
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var open by remember { mutableIntStateOf(-1) }
    var note by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = store.journal.export()
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false) }
            note = if (ok) T.t("Zapisano dziennik (${text.length / 1024 + 1} KB). Wyślij plik agentowi albo Marcinowi.", "Log saved (${text.length / 1024 + 1} KB). Send the file to whoever is debugging.")
            else T.t("Nie udało się zapisać pliku.", "Could not save the file.")
        }
    }
    tick
    val sessions = store.journal.sessions.asReversed()
    MDialog(
        T.t("Dziennik łącza", "Link log"), close,
        confirm = if (sessions.isEmpty()) null else T.t("Eksportuj", "Export"),
        onConfirm = { save.launch("mnotes-dziennik-lacza-${LinkJournal.date(System.currentTimeMillis()).replace(' ', '_').replace(":", "")}.txt") },
        dismissText = T.t("Zamknij", "Close"),
    ) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Small(T.t(
                "W pliku: etapy łączenia z czasem od startu (ms), transport, czy Bluetooth, Wi-Fi, lokalizacja i zgody są włączone, model i wersja Androida, rozmiary i czasy części zdjęć, ponowienia i błędy. " +
                    "Nie ma: nazw urządzeń, adresów, identyfikatorów (tylko skrót, inny przy każdym eksporcie), kodów parowania, kluczy, nazw plików, zdjęć, zeszytów, tytułów, kont ani położenia.",
                "In the file: link stages with time from start (ms), transport, whether Bluetooth, Wi-Fi, location and permissions are on, device model and Android version, photo part sizes and times, retries and errors. " +
                    "Not in it: device names, addresses, identifiers (only a hash, different on every export), pairing codes, keys, file names, photos, notebooks, titles, accounts or location.",
            ))
            if (sessions.isEmpty()) Small(T.t("Dziennik jest pusty. Otwórz łącze (np. „Wyślij zdjęcie na tablet”) i wróć tutaj.", "The log is empty. Open the link (e.g. “Send photo to tablet”) and come back here."))
            sessions.forEachIndexed { i, s ->
                TextButton(onClick = { open = if (open == i) -1 else i }) {
                    Text("${LinkJournal.date(s.startedAt)} · ${s.title} · ${T.t("wpisów", "entries")}: ${s.entries.size}")
                }
                if (open == i) for (e in s.entries.takeLast(SHOWN)) Small("+${e.ms} ms  ${e.text}${e.code?.let { "  (kod $it)" } ?: ""}")
                if (open == i && s.entries.size > SHOWN) Small(T.t("(pokazano ostatnie $SHOWN; w eksporcie są wszystkie)", "(last $SHOWN shown; the export has all of them)"))
            }
            note?.let { Small(it) }
            if (sessions.isNotEmpty()) Row {
                if (!confirmClear) TextButton(onClick = { confirmClear = true }) { Text(T.t("Wyczyść", "Clear"), color = MaterialTheme.colorScheme.error) }
                else {
                    TextButton(onClick = { store.clear(); confirmClear = false; open = -1; note = null; tick++ }) { Text(T.t("Tak, usuń dziennik", "Yes, delete the log"), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmClear = false }) { Text(T.t("Anuluj", "Cancel")) }
                }
            }
        }
    }
}

private const val SHOWN = 300

@Composable
private fun Small(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
