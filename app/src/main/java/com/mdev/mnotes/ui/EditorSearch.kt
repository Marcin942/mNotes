package com.mdev.mnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Pasek szukania w zeszycie (Ctrl+F): pole, licznik „k / n”, poprzednie/następne (Enter, Shift+Enter, F3), zamknięcie (Esc).
 * Wyniki liczone są w tle po krótkiej zwłoce, a trafienia podświetla `NoteView`.
 */
@Composable
fun BoxScope.SearchBar(ed: Editor, extraBottom: Dp = 0.dp) {
    val m = mint
    val focus = remember { FocusRequester() }
    var lastQ by remember { mutableStateOf<String?>(null) }

    // Zapytanie przyniesione z biblioteki: bez fokusu, żeby klawiatura nie zasłaniała strony z trafieniem.
    LaunchedEffect(ed.searchFocusTick) { delay(60); if (ed.searchQ.isBlank() || ed.searchFocusTick > 0) runCatching { focus.requestFocus() } }

    // Ponowne liczenie po zmianie zapytania albo struktury/zawartości; widok przewijany tylko przy nowym zapytaniu.
    LaunchedEffect(ed.searchQ, ed.structTick, ed.undoTick) {
        val q = ed.searchQ
        val fresh = q != lastQ
        if (fresh && q.isNotBlank()) delay(140)
        ed.searchBusy = true
        val pages = ed.pagesSnapshot()
        val marks = ed.bookmarksSnapshot()
        val hits = withContext(Dispatchers.Default) { ed.runSearch(q, pages, marks) }
        ed.searchHits = hits
        ed.searchBusy = false
        if (fresh) {
            lastQ = q
            ed.searchCur = hits.indexOfFirst { it.pageIndex >= ed.pageIndex }.let { if (it < 0) 0 else it }
            if (hits.isNotEmpty()) ed.showSearchHit()
        } else ed.searchCur = ed.searchCur.coerceIn(0, (hits.size - 1).coerceAtLeast(0))
        ed.view?.invalidate()
    }

    val n = ed.searchHits.size
    val none = ed.searchQ.isNotBlank() && !ed.searchBusy && n == 0
    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp + extraBottom, start = 8.dp, end = 8.dp)) {
        FloatingCard(radius = 16) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Rounded.Search, null, Modifier.padding(start = 8.dp, end = 4.dp), tint = m.accentText)
                MField(
                    ed.searchQ, { ed.searchQ = it },
                    Modifier.width(250.dp).focusRequester(focus).onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                            Key.Enter, Key.NumPadEnter -> { ed.searchStep(if (e.isShiftPressed) -1 else 1); true }
                            Key.Escape -> { ed.closeSearch(); true }
                            else -> false
                        }
                    },
                    hint = T.t("Szukaj w tekście i zakładkach", "Search text and bookmarks"),
                )
                Box(Modifier.widthIn(min = 64.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            ed.searchQ.isBlank() -> ""
                            none -> T.t("brak", "none")
                            else -> "${if (n == 0) 0 else ed.searchCur + 1} / $n"
                        },
                        style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Clip,
                        color = if (none) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconBtn(Icons.Rounded.KeyboardArrowUp, T.t("Poprzednie", "Previous"), { ed.searchStep(-1) }, enabled = n > 0, shortcut = "Shift+Enter")
                IconBtn(Icons.Rounded.KeyboardArrowDown, T.t("Następne", "Next"), { ed.searchStep(1) }, enabled = n > 0, shortcut = "Enter")
                IconBtn(Icons.Rounded.Close, T.t("Zamknij", "Close"), { ed.closeSearch() }, shortcut = "Esc")
            }
        }
    }
}
