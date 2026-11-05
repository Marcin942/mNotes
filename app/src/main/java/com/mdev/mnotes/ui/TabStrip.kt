package com.mdev.mnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.core.nav.TabOrder
import com.mdev.mnotes.core.nav.Tabs
import com.mdev.mnotes.editor.T

/** Sterowanie kartami przekazywane do edytora: stan [tabs] i akcje (przełącz, zamknij, krok, biblioteka). */
class TabsCtl(
    val tabs: Tabs,
    val enabled: Boolean,
    val open: (String) -> Unit,
    val close: (String) -> Unit,
    val step: (Int) -> Unit,
    val library: () -> Unit,
    val title: (String) -> String,
    /** 3c-A7: dane zeszytu karty do ikonki (okładka, PDF); null — sam tytuł. */
    val meta: (String) -> com.mdev.mnotes.data.NotebookMeta? = { null },
) {
    /** Tryb kart jest włączony i jest co zamykać — wtedy Ctrl+W zamyka kartę zamiast zeszytu. */
    val on get() = enabled && tabs.size > 0

    /** Pasek kart pokazujemy dopiero przy dwóch kartach, więc pojedynczy zeszyt nie kosztuje ani piksela. */
    val showStrip get() = enabled && tabs.size >= 2
}

/**
 * Karty zeszytów w jednej linii z nagłówkiem (zastępują tytuł, więc nie zabierają miejsca). Przewijane w poziomie,
 * aktywna zawsze wjeżdża w widok. Z biblioteki ([openActive]) dotknięcie aktywnej karty wraca do jej zeszytu. Zamknięcie ✕ ma 28 dp, „+” prowadzi do biblioteki, gdzie wybiera się kolejny zeszyt.
 */
@Composable
fun TabStrip(ctl: TabsCtl, modifier: Modifier = Modifier, showNew: Boolean = true, openActive: Boolean = false, st: com.mdev.mnotes.editor.Station? = null) {
    val m = mint
    val tick = rememberTick()
    val scroll = rememberScrollState()
    // LP-1: przy stanowisku karty jego dokumentów stoją w kolejności rzędów, ze strzałką do środka widoku (na żywo).
    val group = st?.takeIf { it.on }?.rows.orEmpty()
    st?.view?.tick
    val center = if (group.isEmpty()) -1 else st!!.centerIndex()
    val ids = TabOrder.merge(ctl.tabs.ids, group.map { it.nbId })
    Row(modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ids.forEach { id ->
            val row = group.indexOfFirst { it.nbId == id }
            val e = group.getOrNull(row)
            val active = if (group.isEmpty()) id == ctl.tabs.activeId else row == center
            val arrow = if (e == null) "" else TabOrder.arrow(row, center, st!!.horizontal)
            var menu by remember { mutableStateOf(false) }
            val req = remember { BringIntoViewRequester() }
            if (active) LaunchedEffect(id, ids.size) { req.bringIntoView() }
            val shape = RoundedCornerShape(9.dp)
            val onClick = {
                if (e != null) { tick(); st!!.focus(e) }
                else if (!active || openActive) { tick(); ctl.open(id) }
            }
            Box {
                Row(
                    Modifier.bringIntoViewRequester(req).height(34.dp).widthIn(min = 88.dp, max = 190.dp).clip(shape)
                        .background(if (active) m.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                        .border(0.5.dp, if (active) m.accent.copy(alpha = 0.45f) else m.line, shape)
                        .combinedClickable(
                            role = Role.Tab, onClick = onClick,
                            onLongClick = if (st != null && e == null && !openActive) ({ tick(); menu = true }) else null,
                        )
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ctl.meta(id)?.let { nb ->
                        DocIcon(nb.cover, nb.coverStyle, if (nb.pdfDoc) com.mdev.mnotes.core.layout.LaneLabel.Kind.PDF else com.mdev.mnotes.core.layout.LaneLabel.Kind.NOTEBOOK)
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 6.dp))
                    }
                    Text(
                        ctl.title(id).ifBlank { T.t("Bez tytułu", "Untitled") }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) m.accentText else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (arrow.isNotEmpty()) Text(" $arrow", style = MaterialTheme.typography.labelLarge, color = m.accent)
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(28.dp).clip(CircleShape).clickable(role = Role.Button) {
                            tick()
                            if (e != null && e !== st!!.main) { st.remove(e); if (ctl.tabs.has(id)) ctl.close(id) } else ctl.close(id)
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, T.t("Zamknij kartę", "Close tab"), Modifier.size(15.dp), tint = if (active) m.accentText else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (st != null) DropdownMenu(menu, { menu = false }, containerColor = m.chrome) {
                    val h = st.horizontal
                    DropdownMenuItem(
                        text = { Text(if (h) T.t("Otwórz poniżej", "Open below") else T.t("Otwórz po prawej", "Open on the right")) },
                        onClick = { menu = false; st.add(id, st.centerIndex() + 1) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (h) T.t("Otwórz powyżej", "Open above") else T.t("Otwórz po lewej", "Open on the left")) },
                        onClick = { menu = false; st.add(id, st.centerIndex().coerceAtLeast(0)) },
                    )
                }
            }
        }
        if (showNew) IconBtn(Icons.Rounded.Add, T.t("Otwórz kolejny zeszyt (biblioteka)", "Open another notebook (library)"), ctl.library, size = 34.dp, iconSize = 20.dp)
    }
}
