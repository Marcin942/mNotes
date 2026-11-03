package com.mdev.mnotes.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.FileProvider
import java.io.File

/**
 * Kładzenie eksportu do schowka systemu. Pliki idą przez FileProvider (adres `content://`), więc inne aplikacje
 * wklejają obraz albo plik, a nie ścieżkę; schowek nadaje im prawo odczytu przy wklejaniu.
 */
object ClipOut {
    /**
     * Katalog plików schowka. Osobny podkatalog `share/clip`: eksport do udostępniania czyści `share/` przed każdym
     * plikiem, a obraz w schowku ma przeżyć do wklejenia. Pliki starsze niż doba są usuwane.
     */
    fun dir(ctx: Context): File = File(ctx.cacheDir, "share/clip").apply {
        mkdirs()
        val old = System.currentTimeMillis() - 24 * 3600_000L
        listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
    }

    /** Nowy plik w [dir] o nazwie [name] (bez kolizji z poprzednim kopiowaniem tego samego). */
    fun file(ctx: Context, name: String): File {
        val d = File(dir(ctx), System.currentTimeMillis().toString()).apply { mkdirs() }
        return File(d, name.ifBlank { "mnotes" })
    }

    /** Pliki [files] (wspólny typ [mime]) jako elementy schowka; [sensitive] — bez podglądu treści (blokada aplikacji). */
    fun files(ctx: Context, files: List<File>, mime: String, sensitive: Boolean) {
        if (files.isEmpty()) return
        val uris = files.map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", it) }
        val clip = ClipData(ClipDescription("mNotes", arrayOf(mime)), ClipData.Item(uris[0]))
        for (u in uris.drop(1)) clip.addItem(ClipData.Item(u))
        put(ctx, clip, sensitive)
    }

    fun text(ctx: Context, label: String, text: String, sensitive: Boolean) = put(ctx, ClipData.newPlainText(label, text), sensitive)

    /** Przy blokadzie aplikacji system nie pokazuje treści schowka w podglądzie (Android 13+): notatka jest prywatna. */
    fun put(ctx: Context, clip: ClipData, sensitive: Boolean) {
        if (sensitive && Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }
}
