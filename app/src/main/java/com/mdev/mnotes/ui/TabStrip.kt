package com.mdev.mnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
fun TabStrip(ctl: TabsCtl, modifier: Modifier = Modifier, showNew: Boolean = true, openActive: Boolean = false) {
    val m = mint
    val tick = rememberTick()
    val scroll = rememberScrollState()
    Row(modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ctl.tabs.ids.forEach { id ->
            val active = id == ctl.tabs.activeId
            val req = remember { BringIntoViewRequester() }
            if (active) LaunchedEffect(id, ctl.tabs.size) { req.bringIntoView() }
            val shape = RoundedCornerShape(9.dp)
            Row(
                Modifier.bringIntoViewRequester(req).height(34.dp).widthIn(min = 88.dp, max = 190.dp).clip(shape)
                    .background(if (active) m.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                    .border(0.5.dp, if (active) m.accent.copy(alpha = 0.45f) else m.line, shape)
                    .clickable(role = Role.Tab) { if (!active || openActive) { tick(); ctl.open(id) } }
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ctl.title(id).ifBlank { T.t("Bez tytułu", "Untitled") }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) m.accentText else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    Modifier.padding(horizontal = 3.dp).size(28.dp).clip(CircleShape).clickable(role = Role.Button) { tick(); ctl.close(id) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, T.t("Zamknij kartę", "Close tab"), Modifier.size(15.dp), tint = if (active) m.accentText else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (showNew) IconBtn(Icons.Rounded.Add, T.t("Otwórz kolejny zeszyt (biblioteka)", "Open another notebook (library)"), ctl.library, size = 34.dp, iconSize = 20.dp)
    }
}
