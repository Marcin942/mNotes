package com.mdev.mnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.editor.Tool

/** Wysokość paska menu — rezerwowana przez edytor nad widokiem notatnika. */
val MenuBarHeight = 33.dp

/** Czy pasek menu ma być widoczny: 0 nigdy, 1 tylko na dużych ekranach (tablet w poziomie, DeX), 2 zawsze. */
fun menuBarVisible(mode: Int, widthDp: Int) = mode == 2 || (mode == 1 && widthDp >= 840)

/** Znacznik „zaznaczone” dla pozycji przełączanych (układ, narzędzie, pasek narzędzi, tryb tylko do odczytu). */
private fun checked(ed: Editor, set: com.mdev.mnotes.data.Settings, id: String): Boolean = when (id) {
    "layoutV" -> ed.layoutMode == 0
    "layoutH" -> ed.layoutMode == 1
    "layoutS" -> ed.layoutMode == 2
    "readOnly" -> ed.readOnly
    "reader" -> ed.reader
    "pagesDock" -> set.dockPages
    "zoomWindow" -> ed.zoomWin
    "tbTop" -> set.toolbarPos == 0
    "tbBottom" -> set.toolbarPos == 1
    "tbLeft" -> set.toolbarPos == 2
    "tbRight" -> set.toolbarPos == 3
    "toolPen" -> ed.tool == Tool.PEN
    "toolEraser" -> ed.tool == Tool.ERASER
    "toolPainter" -> ed.tool == Tool.PAINTER
    "toolFill" -> ed.tool == Tool.FILL
    "toolLasso" -> ed.tool == Tool.LASSO
    "toolText" -> ed.tool == Tool.TEXT
    "toolLaser" -> ed.tool == Tool.LASER
    "toolHand" -> ed.tool == Tool.HAND
    "toolCurve" -> ed.tool == Tool.CURVE
    "toolSnip" -> ed.tool == Tool.SNIP
    "attach" -> ed.settings.attach
    "snipClipboard" -> ed.settings.snipMode == com.mdev.mnotes.core.edit.SnipMode.CLIPBOARD
    "toolSpace" -> ed.tool == Tool.SPACE
    "toolTape" -> ed.tool == Tool.TAPE
    "ruler" -> ed.ruler
    "protractor" -> ed.protractor
    "toolGeo" -> ed.tool == Tool.GEO
    "pipette" -> ed.pipette != null
    "eraserObject" -> set.eraserMode == 0
    "eraserPixel" -> set.eraserMode == 1
    else -> false
}

/** Klasyczny pasek menu (Plik · Edycja · Widok …) — te same polecenia i skróty co w palecie Ctrl+K. */
@Composable
fun MenuBar(ed: Editor, cmds: List<Cmd>, refocus: () -> Unit, modifier: Modifier = Modifier) {
    val m = mint
    val set = ed.settings
    var open by remember { mutableIntStateOf(-1) }
    // Po zamknięciu menu fokus wraca do edytora, żeby skróty działały dalej.
    LaunchedEffect(open) { if (open < 0) refocus() }

    Column(modifier.background(m.header).statusBarsPadding()) {
    Row(Modifier.fillMaxWidth().height(MenuBarHeight - 1.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (g in 0 until CmdDefs.groups.size) {
            val items = cmds.filter { it.def.group == g }
            if (items.isEmpty()) continue
            Box {
                Text(
                    CmdDefs.groupName(g),
                    fontSize = 13.sp,
                    color = if (open == g) m.accentText else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (open == g) m.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { open = if (open == g) -1 else g }
                        // Gdy któreś menu jest otwarte, najechanie myszą na sąsiednie przełącza menu (jak w menedżerach plików Linuksa).
                        .pointerInput(g) {
                            awaitPointerEventScope { while (true) { if (awaitPointerEvent().type == PointerEventType.Enter && open >= 0) open = g } }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                DropdownMenu(expanded = open == g, onDismissRequest = { if (open == g) open = -1 }, containerColor = m.chrome) {
                    for (c in items) {
                        val on = checked(ed, set, c.def.id)
                        DropdownMenuItem(
                            text = {
                                Row(Modifier.width(260.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(20.dp)) { if (on) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = m.accentText) }
                                    Text(c.title, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
                                    if (c.keyText.isNotEmpty()) Text(c.keyText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            enabled = c.enabled,
                            onClick = { open = -1; c.run() },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(T.t("Ctrl+K — paleta poleceń", "Ctrl+K — command palette"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
    }
    HorizontalDivider(color = m.line)
    }
}
