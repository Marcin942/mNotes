package com.mdev.mnotes.lab.nearby

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.core.nearby.SyncMsg
import com.mdev.mnotes.core.nearby.SyncRole
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.ui.Choice
import com.mdev.mnotes.ui.MDialog
import com.mdev.mnotes.ui.mint
import java.io.ByteArrayOutputStream

/** Krótki opis stanu łącza w oknach Nearby (jedno zdanie, jak stany puste w mNotes). */
@Composable
private fun Status(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))

/** Duże sześć cyfr do porównania z drugim ekranem. */
@Composable
private fun Digits(sas: String) = Text(sas, style = MaterialTheme.typography.displaySmall, color = mint.accentText, modifier = Modifier.padding(vertical = 10.dp))

/**
 * Prosi o uprawnienia Nearby raz przy otwarciu okna; [ready] dostaje wynik. Lokalizacja przybliżona wystarcza (Android
 * 12+ pozwala odmówić dokładnej): gdyby Nearby jej nie przyjęło, zgłosi brak uprawnienia kodem błędu, a okno to pokaże.
 */
@Composable
private fun WithPermissions(ready: (Boolean) -> Unit) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> ready(NearbyLink.granted(r)) }
    LaunchedEffect(Unit) { ask.launch(NearbyLink.permissions(Build.VERSION.SDK_INT)) }
}

/** Zdanie dla pary, której nie da się odczytać (usunięta przez [PairStore.loadOrForget]). */
private fun expiredText() = T.t(
    "Para wygasła (np. po przywróceniu kopii zapasowej). Sparuj urządzenia jeszcze raz.",
    "The pairing expired (e.g. after restoring a backup). Pair the devices again.",
)

/**
 * Parowanie dwóch urządzeń: jedno jest tabletem (ogłasza się), drugie telefonem (szuka). Po połączeniu oba pokazują
 * te same sześć cyfr; dopiero „Zgadza się” zapisuje klucz pary.
 */
@Composable
fun PairDialog(startRole: SyncRole, close: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { PairStore(ctx) }
    var role by remember { mutableStateOf(startRole) }
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var code by remember { mutableStateOf<Triple<String, String, ByteArray>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val link = remember { NearbyLink(ctx, object : SyncListener {
        override fun onPairCode(sas: String, peerName: String, pairKey: ByteArray) { code = Triple(sas, peerName, pairKey) }
        override fun onError(code: Int, permission: Boolean) { error = errorText(code, permission) }
    }) }
    WithPermissions { allowed = it }
    LaunchedEffect(allowed, role) { if (allowed == true) { code = null; link.pair(role, deviceName()) } }
    DisposableEffect(Unit) { onDispose { link.stop() } }

    MDialog(
        title = T.t("Sparuj telefon z tabletem", "Pair phone and tablet"),
        onDismiss = close,
        confirm = if (code != null) T.t("Zgadza się", "They match") else null,
        onConfirm = {
            val c = code
            if (c != null && !store.save(c.third, c.second)) error = T.t("Nie udało się zapisać pary w magazynie kluczy urządzenia.", "Could not store the pairing in the device keystore.")
            else close()
        },
    ) {
        Choice(listOf(T.t("To jest tablet (odbiera)", "This is the tablet (receives)"), T.t("To jest telefon (wysyła)", "This is the phone (sends)")), if (role == SyncRole.HOST) 0 else 1) {
            role = if (it == 0) SyncRole.HOST else SyncRole.CLIENT
        }
        val c = code
        when {
            allowed == false -> Status(T.t("Bez uprawnień do Bluetooth i Wi-Fi w pobliżu łącze nie zadziała. Zezwól w ustawieniach systemu.", "Without Bluetooth and nearby Wi-Fi permissions the link cannot work. Allow them in system settings."))
            error != null -> Status(error!!)
            c == null -> Status(T.t("Otwórz to samo okno na drugim urządzeniu i wybierz drugą rolę. Szukam…", "Open this window on the other device and choose the other role. Searching…"))
            else -> Column {
                Status(T.t("Połączono z „${c.second}”. Sprawdź, czy na obu ekranach są te same cyfry:", "Connected to “${c.second}”. Check that both screens show the same digits:"))
                Digits(c.first)
                Status(T.t("Inne cyfry? Zamknij okno: ktoś mógł się wtrącić.", "Different digits? Close the window: someone may be interfering."))
            }
        }
    }
}

/** Tablet: czeka na zdjęcia z telefonu i wstawia je na bieżącą stronę (każde zaznaczone, jak zwykły obraz). */
@Composable
fun ReceivePhotosDialog(ed: Editor, close: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { PairStore(ctx) }
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var up by remember { mutableStateOf(false) }
    var received by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var pairing by remember { mutableStateOf(!store.paired) }
    var expired by remember { mutableStateOf(false) }
    lateinit var link: NearbyLink
    link = remember { NearbyLink(ctx, object : SyncListener {
        override fun onUp() { up = true; error = null }
        override fun onDown() { up = false }
        override fun onError(code: Int, permission: Boolean) { error = errorText(code, permission) }
        override fun onMessage(msg: SyncMsg) {
            if (msg is SyncMsg.Photo) {
                // Kolejne zdjęcia schodkami (co 24 pt, po 6 od nowa), żeby nie leżały jedno na drugim na środku widoku.
                val k = received % CASCADE
                ed.insertImage(msg.bytes.inputStream(), if ("png" in msg.mime) "png" else "jpg") { if (k > 0) ed.nudgeSel(k * CASCADE_STEP, k * CASCADE_STEP) }
                received++
                link.send(SyncMsg.Note(T.t("Wstawiono do notatki", "Inserted into the note")))
            }
        }
    }) }
    if (pairing) { PairDialog(SyncRole.HOST) { pairing = false; expired = false; if (!store.paired) close() }; return }
    WithPermissions { allowed = it }
    LaunchedEffect(allowed, pairing) {
        if (allowed == true && !pairing) {
            val key = store.loadOrForget()
            if (key == null) expired = true else link.connect(SyncRole.HOST, key)
        }
    }
    DisposableEffect(Unit) { onDispose { link.stop() } }

    MDialog(title = T.t("Zdjęcie z telefonu", "Photo from phone"), onDismiss = close, dismissText = T.t("Zamknij", "Close")) {
        Status(
            when {
                allowed == false -> T.t("Bez uprawnień do Bluetooth i Wi-Fi w pobliżu łącze nie zadziała.", "Without Bluetooth and nearby Wi-Fi permissions the link cannot work.")
                expired -> expiredText()
                error != null -> error!!
                !up -> T.t("Czekam na „${store.peerName}”. Na telefonie: mNotes → Ustawienia → Laboratorium → Wyślij zdjęcie na tablet.", "Waiting for “${store.peerName}”. On the phone: mNotes → Settings → Lab → Send photo to tablet.")
                received == 0 -> T.t("Telefon połączony. Zdjęcie trafi na tę stronę.", "Phone connected. The photo will land on this page.")
                else -> T.t("Wstawiono zdjęć: $received. Możesz wysłać kolejne.", "Photos inserted: $received. You can send more.")
            },
        )
        TextButton(onClick = { pairing = true }) { Text(if (expired) T.t("Sparuj telefon", "Pair a phone") else T.t("Sparuj inny telefon", "Pair another phone")) }
    }
}

/** Telefon: wybierz zdjęcie z galerii i wyślij je na sparowany tablet. */
@Composable
fun SendPhotoDialog(close: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { PairStore(ctx) }
    var photo by remember { mutableStateOf<SyncMsg.Photo?>(null) }
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var state by remember { mutableStateOf(T.t("Wybierz zdjęcie.", "Choose a photo.")) }
    var sent by remember { mutableStateOf(false) }
    var up by remember { mutableStateOf(false) }
    // Uprawnienia i łącze zostają od pierwszego wybranego zdjęcia (kolejne nie pytają drugi raz).
    var asked by remember { mutableStateOf(false) }
    lateinit var link: NearbyLink
    // Wysyła wybrane zdjęcie, gdy łącze już stoi; inaczej czeka na onUp. Dzięki temu „Następne zdjęcie” wysyła od razu
    // (łącze jest już połączone, onUp drugi raz nie przyjdzie).
    fun trySend() {
        val p = photo ?: return
        if (up && link.send(p)) { sent = true; photo = null; state = T.t("Wysłano, czekam na potwierdzenie…", "Sent, waiting for confirmation…") }
    }
    link = remember { NearbyLink(ctx, object : SyncListener {
        override fun onUp() { up = true; trySend() }
        override fun onDown() { up = false }
        override fun onMessage(msg: SyncMsg) { if (msg is SyncMsg.Note) state = msg.text }
        override fun onError(code: Int, permission: Boolean) { state = errorText(code, permission) }
    }) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) { if (!sent) close() } else {
            asked = true
            photo = readPhoto(ctx, uri)
            state = if (photo == null) T.t("Nie udało się wczytać zdjęcia.", "Could not read the photo.") else T.t("Szukam tabletu „${store.peerName}”…", "Looking for the tablet “${store.peerName}”…")
            trySend()
        }
    }
    LaunchedEffect(Unit) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    if (asked) WithPermissions { allowed = it }
    LaunchedEffect(allowed) {
        if (allowed == true) {
            val key = store.loadOrForget()
            if (key == null) state = expiredText() else link.connect(SyncRole.CLIENT, key)
        }
    }
    DisposableEffect(Unit) { onDispose { link.stop() } }

    MDialog(title = T.t("Wyślij zdjęcie na tablet", "Send photo to tablet"), onDismiss = close, dismissText = if (sent) T.t("Gotowe", "Done") else T.t("Anuluj", "Cancel")) {
        Status(if (allowed == false) T.t("Bez uprawnień do Bluetooth i Wi-Fi w pobliżu łącze nie zadziała.", "Without Bluetooth and nearby Wi-Fi permissions the link cannot work.") else state)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sent && photo == null) TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(T.t("Następne zdjęcie", "Next photo")) }
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

private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(40)

private fun errorText(code: Int, permission: Boolean): String =
    if (permission) T.t("Brak uprawnienia albo wyłączony Bluetooth / Wi-Fi (kod $code).", "Missing permission or Bluetooth / Wi-Fi is off (code $code).")
    else T.t("Nearby nie wystartowało (kod $code); próbuję dalej. Czy na urządzeniu są Usługi Google Play?", "Nearby did not start (code $code); retrying. Does the device have Google Play services?")

/** Schodki kolejnych zdjęć z telefonu: przesunięcie w pt i po ilu zdjęciach od nowa. */
private const val CASCADE_STEP = 24f
private const val CASCADE = 6
