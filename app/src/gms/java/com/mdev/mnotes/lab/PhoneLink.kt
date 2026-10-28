package com.mdev.mnotes.lab

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.app.LabFeatures
import com.mdev.mnotes.core.nearby.LinkMode
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.lab.nearby.DiagnosticsDialog
import com.mdev.mnotes.lab.nearby.LinkJournalDialog
import com.mdev.mnotes.lab.nearby.NotebookSendDialog
import com.mdev.mnotes.lab.nearby.Outgoing
import com.mdev.mnotes.lab.nearby.PairStore
import com.mdev.mnotes.lab.nearby.PhoneLinkDialog
import com.mdev.mnotes.ui.Group
import com.mdev.mnotes.ui.ToggleRow

/** Łącze z telefonem (wariant gms): zdjęcie z telefonu na stronę, wysyłanie zeszytów, diagnostyka. */
object PhoneLink : LabFeatures {

    @Composable
    override fun phoneButton(): Boolean = LinkSettings.of(LocalContext.current).nearby

    @Composable
    override fun PhonePanel(ed: Editor, close: () -> Unit) = PhoneLinkDialog(LinkMode.RECEIVE, ed, close = close)

    /** Przełącznik, parowanie, wysyłanie zdjęcia i zeszytu (to samo menu na tablecie i telefonie). */
    @Composable
    override fun SettingsSection() {
        val ctx = LocalContext.current
        val set = LinkSettings.of(ctx)
        val store = remember { PairStore(ctx) }
        var dialog by remember { mutableStateOf(0) }
        var tick by remember { mutableStateOf(0) }
        var outgoing by remember { mutableStateOf<Outgoing?>(null) }
        Group(T.t("Telefon i tablet (Nearby / Bluetooth)", "Phone and tablet (Nearby / Bluetooth)"), keys = "nearby telefon tablet zdjęcie bluetooth rfcomm parowanie połącz wyślij diagnostyka dziennik eksperymentalne łącze jakość phone pair connect send diagnostics log experimental link quality") {
            Text(
                T.t(
                    "Zdjęcie zrobione telefonem trafia od razu na stronę na tablecie, bez internetu i bez wspólnej sieci; każda ramka jest szyfrowana kluczem pary. Gdy Android pyta o lokalizację, to dlatego, że szukanie urządzeń mogłoby ją zdradzić; mNotes położenia nie odczytuje. Jedno zdjęcie bez parowania: Udostępnij → Quick Share na tablet i wstaw obraz.",
                    "A photo taken with the phone lands right on the tablet page, without internet or a shared network; every frame is encrypted with the pair key. When Android asks for location, it is because scanning for devices could reveal it; mNotes never reads your location. A single photo without pairing: Share → Quick Share to the tablet and insert the image.",
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp),
            )
            // D5 (3b-B5): w 0.1.0 łącze jest eksperymentalne, domyślnie wyłączone; ten sam klucz `nearby.enabled`, więc kto
            // je włączył, zachowuje je po aktualizacji.
            ToggleRow(T.t("Łącze z telefonem (eksperymentalne)", "Phone link (experimental)"), set.nearby) { set.nearby = it }
            if (!set.nearby) Text(
                T.t("Eksperymentalne: na części telefonów szukanie trwa długo. Gdy coś nie działa, „Dziennik łącza” → „Eksportuj” i wyślij plik.", "Experimental: on some phones finding the other device takes long. If it fails, “Link log” → “Export” and send the file."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp),
            )
            if (set.nearby) {
                tick
                // 2e-B1: Bluetooth RFCOMM obok Nearby; para (klucz) wspólna, zmiana nie wymaga parowania od nowa.
                ToggleRow(T.t("Łącze przez Bluetooth (bez Usług Google Play)", "Link over Bluetooth (no Google Play services)"), set.bluetooth) { set.bluetooth = it }
                Text(
                    if (set.bluetooth) T.t(
                        "Bluetooth: tablet najłatwiej znaleźć, gdy jest sparowany w ustawieniach Bluetooth obu urządzeń; inaczej „Szukaj w pobliżu” przy parowaniu. Bez lokalizacji do Androida 11 i bez Wi-Fi Direct; zdjęcie 1 MB idzie kilka sekund.",
                        "Bluetooth: the tablet is easiest to find when it is paired in the Bluetooth settings of both devices; otherwise “Search nearby” while pairing. No location up to Android 11 and no Wi-Fi Direct; a 1 MB photo takes a few seconds.",
                    ) else T.t("Nearby: Bluetooth albo Wi-Fi Direct, wymaga Usług Google Play.", "Nearby: Bluetooth or Wi-Fi Direct, needs Google Play services."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp),
                )
                ToggleRow(T.t("Zdjęcia z telefonu w pełnej jakości (wolniej)", "Photos from the phone in full quality (slower)"), set.fullPhoto) { set.fullPhoto = it }
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
                TextButton(onClick = { dialog = 7 }) { Text(T.t("Dziennik łącza", "Link log")) }
            }
        }
        when (dialog) {
            1 -> PhoneLinkDialog(LinkMode.PAIR, null) { dialog = 0; tick++ }
            2 -> PhoneLinkDialog(LinkMode.SEND, null) { dialog = 0; tick++ }
            3 -> DiagnosticsDialog(null) { dialog = 0 }
            4 -> NotebookSendDialog({ dialog = 0 }) { file -> outgoing = file; dialog = 6 }
            5 -> PhoneLinkDialog(LinkMode.RECEIVE, null) { dialog = 0 }
            6 -> PhoneLinkDialog(LinkMode.SEND, null, outgoing?.let { listOf(it) }) { dialog = 0; outgoing = null }
            7 -> LinkJournalDialog { dialog = 0 }
        }
    }
}

/**
 * Ustawienia łącza w tym samym pliku preferencji i pod tymi samymi kluczami co dawny wariant lab (`mnotes.lab`,
 * `nearby.enabled`, `link.*`), żeby po aktualizacji tablet nie zgubił ustawień ani wyboru transportu.
 */
class LinkSettings(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("mnotes.lab", Context.MODE_PRIVATE)

    private inner class B(val key: String, def: Boolean) {
        var value by mutableStateOf(sp.getBoolean(key, def))
        fun set(v: Boolean) { value = v; sp.edit().putBoolean(key, v).apply() }
    }

    private val nearbyP = B("nearby.enabled", false)
    private val bluetoothP = B("link.bluetooth", false)
    private val fullPhotoP = B("link.photo.full", false)

    /** Łącze z telefonem (zdjęcie z telefonu, zeszyty); domyślnie wyłączone, bo wymaga uprawnień radia. */
    var nearby: Boolean get() = nearbyP.value; set(v) = nearbyP.set(v)

    /** Transport łącza (2e-B1): Bluetooth RFCOMM zamiast Nearby (Usługi Google). Oba zostają do porównania. */
    var bluetooth: Boolean get() = bluetoothP.value; set(v) = bluetoothP.set(v)

    /** Zdjęcia z telefonu w pełnej rozdzielczości; domyślnie dłuższy bok 2400 px (przez Bluetooth kilka razy szybciej). */
    var fullPhoto: Boolean get() = fullPhotoP.value; set(v) = fullPhotoP.set(v)

    companion object {
        private var instance: LinkSettings? = null

        fun of(ctx: Context): LinkSettings = instance ?: LinkSettings(ctx.applicationContext).also { instance = it }
    }
}
