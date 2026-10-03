package com.mdev.mnotes.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Przeciągnij i upuść pliki z innej aplikacji / menedżera plików (FIL-006), np. w trybie okienkowym lub DeX.
 * Podczas przeciągania nad obszarem rysuje akcentową ramkę; [onDrop] dostaje adresy URI upuszczonych plików.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.fileDrop(onDrop: (List<Uri>) -> Unit): Modifier {
    val activity = LocalContext.current.activity()
    val latest = rememberUpdatedState(onDrop)
    var hot by remember { mutableStateOf(false) }
    val accent = mint.accent
    val target = remember(activity) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hot = true }
            override fun onExited(event: DragAndDropEvent) { hot = false }
            override fun onEnded(event: DragAndDropEvent) { hot = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hot = false
                val ae = event.toAndroidDragEvent()
                val clip = ae.clipData ?: return false
                val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                if (uris.isEmpty()) return false
                // Bez tego adresy content:// z innych aplikacji byłyby nieczytelne po powrocie z onDrop.
                runCatching { activity?.requestDragAndDropPermissions(ae) }
                latest.value(uris)
                return true
            }
        }
    }
    return this
        .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
        .drawWithContent {
            drawContent()
            if (hot) {
                val w = 3.dp.toPx()
                drawRoundRect(accent, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(10.dp.toPx()), Stroke(w))
            }
        }
}
