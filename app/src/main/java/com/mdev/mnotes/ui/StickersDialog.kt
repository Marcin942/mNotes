package com.mdev.mnotes.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.app.app
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.data.StickerStore
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Okno „Naklejki” (F19): siatka zapisanych rysunków; dotknięcie wstawia naklejkę na środku widoku (zaznaczoną),
 * kosz usuwa ją po potwierdzeniu. Pusty stan mówi, jak zrobić pierwszą.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickersDialog(ed: Editor, onDismiss: () -> Unit) {
    val items by produceState<List<StickerStore.Item>?>(null, ed.stickerTick) { value = withContext(Dispatchers.IO) { ed.stickers.list() } }
    var deleting by remember { mutableStateOf<String?>(null) }
    MDialog(T.t("Naklejki", "Stickers"), onDismiss, dismissText = T.t("Zamknij", "Close")) {
        val list = items
        when {
            list == null -> Text("…", style = MaterialTheme.typography.bodyMedium)
            list.isEmpty() -> Text(
                T.t("Nie masz jeszcze naklejek. Zaznacz rysunek lassem, przytrzymaj go i wybierz „Zapisz jako naklejkę”.",
                    "No stickers yet. Select a drawing with the lasso, long-press it and choose “Save as sticker”."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (s in list) StickerCard(ed, s, deleting == s.id,
                        onInsert = { onDismiss(); ed.insertSticker(s.id) },
                        onDelete = { if (deleting == s.id) { ed.stickers.delete(s.id); deleting = null; ed.stickerTick++ } else deleting = s.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerCard(ed: Editor, s: StickerStore.Item, confirm: Boolean, onInsert: () -> Unit, onDelete: () -> Unit) {
    val m = mint
    val thumbs = LocalContext.current.app.thumbs
    val bmp by produceState<Bitmap?>(null, s.id) {
        value = withContext(Dispatchers.IO) {
            val n = ed.stickers.load(s.id) ?: return@withContext null
            // Miniatura na białym papierze z marginesem; obrazy naklejki czytane z jej własnego katalogu nie są potrzebne do podglądu kresek.
            val pad = 6f
            val spec = PageSpec(w = (n.w + 2 * pad).coerceAtLeast(12f), h = (n.h + 2 * pad).coerceAtLeast(12f))
            val els = n.elements.map { it.transformed(com.mdev.mnotes.core.geom.Affine.translation(pad, pad)) }
            runCatching { thumbs.render(ed.nbId, spec, PageContent.of(els), 220) }.getOrNull()
        }
    }
    Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(104.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, m.line, RoundedCornerShape(8.dp)).clickable { onInsert() },
            contentAlignment = Alignment.Center,
        ) {
            bmp?.let { Image(it.asImageBitmap(), s.name, Modifier.padding(8.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (confirm) T.t("Usunąć?", "Delete?") else s.name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (confirm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            IconBtn(Icons.Rounded.Delete, T.t("Usuń naklejkę", "Delete sticker"), onDelete, size = 28.dp, iconSize = 16.dp, selected = confirm)
        }
    }
}

/** Okno nazwy nowej naklejki z bieżącego zaznaczenia. */
@Composable
fun SaveStickerDialog(ed: Editor, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    MDialog(
        T.t("Zapisz jako naklejkę", "Save as sticker"), onDismiss, confirm = T.t("Zapisz", "Save"), confirmEnabled = name.isNotBlank(),
        onConfirm = { ed.saveSelAsSticker(name.trim()); onDismiss() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), hint = T.t("Nazwa, np. „pierścień benzenowy”", "Name, e.g. “benzene ring”"))
            Text(T.t("Naklejki są wspólne dla wszystkich zeszytów: Więcej (⋮) → Naklejki.", "Stickers are shared by all notebooks: More (⋮) → Stickers."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
