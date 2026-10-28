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
import com.mdev.mnotes.core.nearby.SyncMsg
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.lab.nearby.DiagnosticsDialog
import com.mdev.mnotes.lab.nearby.NotebookSendDialog
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
        var outgoing by remember { mutableStateOf<SyncMsg.Photo?>(null) }
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
            1 -> PhoneLinkDialog(LinkMode.PAIR, null) { dialog = 0; tick++ }
            2 -> PhoneLinkDialog(LinkMode.SEND, null) { dialog = 0; tick++ }
            3 -> DiagnosticsDialog(null) { dialog = 0 }
            4 -> NotebookSendDialog({ dialog = 0 }) { file -> outgoing = file; dialog = 6 }
            5 -> PhoneLinkDialog(LinkMode.RECEIVE, null) { dialog = 0 }
            6 -> PhoneLinkDialog(LinkMode.SEND, null, outgoing?.let { listOf(it) }) { dialog = 0; outgoing = null }
        }
    }
}

/**
 * Przełącznik łącza w tym samym pliku preferencji i pod tym samym kluczem co dawny wariant lab (`mnotes.lab`,
 * `nearby.enabled`), żeby po aktualizacji tablet nie zgubił ustawienia.
 */
class LinkSettings(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("mnotes.lab", Context.MODE_PRIVATE)
    private var nearbyState by mutableStateOf(sp.getBoolean("nearby.enabled", false))

    /** Łącze Nearby (zdjęcie z telefonu); domyślnie wyłączone, bo wymaga Usług Google Play i uprawnień radia. */
    var nearby: Boolean
        get() = nearbyState
        set(v) { nearbyState = v; sp.edit().putBoolean("nearby.enabled", v).apply() }

    companion object {
        private var instance: LinkSettings? = null

        fun of(ctx: Context): LinkSettings = instance ?: LinkSettings(ctx.applicationContext).also { instance = it }
    }
}
