package com.mdev.mnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.core.pages.PageTags
import com.mdev.mnotes.data.PageState
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T

/**
 * Okno tagów strony (FB8): obecne tagi jako wybrane chipy (dotknięcie usuwa), podpowiedzi z zeszytu i stałe rodzaje
 * treści (dotknięcie dodaje), pole na nowy tag. Zmiany trafiają do zeszytu dopiero po „Zapisz”.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PageTagsDialog(ed: Editor, pageId: String, onDismiss: () -> Unit) {
    val index = ed.doc.indexOf(pageId)
    if (index < 0) { onDismiss(); return }
    var tags by remember(pageId) { mutableStateOf(ed.tagsOf(pageId)) }
    var typed by remember(pageId) { mutableStateOf("") }
    val fromBook = remember(pageId) { ed.tagUsage().map { it.tag } }
    val suggested = (fromBook + if (T.pl) PageTags.SUGGESTED_PL else PageTags.SUGGESTED_EN)
        .filter { !PageTags.has(tags, it) }.distinctBy { com.mdev.mnotes.core.search.TextSearch.fold(it) }.take(12)
    val full = tags.size >= PageTags.MAX_PER_PAGE
    fun addTyped() { if (typed.isNotBlank()) { tags = PageTags.added(tags, PageTags.parse(typed)); typed = "" } }
    MDialog(
        T.t("Tagi strony ${index + 1}", "Tags of page ${index + 1}"), onDismiss, confirm = T.t("Zapisz", "Save"),
        onConfirm = { ed.setPageTags(pageId, PageTags.added(tags, PageTags.parse(typed))); onDismiss() },
    ) {
        Text(
            T.t("Tag zbiera strony jednego rodzaju. Wpisz np. „#definicja” w szukaniu zeszytu (Ctrl+F) albo biblioteki.",
                "A tag gathers pages of one kind. Type e.g. “#definition” in the notebook search (Ctrl+F) or the library search."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionLabel(T.t("Na tej stronie", "On this page"))
        if (tags.isEmpty()) Text(T.t("Strona nie ma jeszcze tagów.", "This page has no tags yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in tags) Chip("#$t", true, { tags = PageTags.removed(tags, t) }, icon = Icons.Rounded.Close)
        }
        if (!full && suggested.isNotEmpty()) {
            SectionLabel(T.t("Dodaj", "Add"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in suggested) Chip("#$t", false, { tags = PageTags.added(tags, listOf(t)) })
            }
        }
        if (!full) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MField(typed, { typed = it }, Modifier.weight(1f), hint = T.t("Nowy tag, np. całki", "New tag, e.g. integrals"), onDone = ::addTyped)
            IconBtn(Icons.Rounded.Add, T.t("Dodaj tag", "Add tag"), ::addTyped, enabled = PageTags.parse(typed).isNotEmpty(), size = 40.dp, iconSize = 20.dp)
        }
        else Text(T.t("Najwyżej ${PageTags.MAX_PER_PAGE} tagów na stronę.", "At most ${PageTags.MAX_PER_PAGE} tags per page."), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Zakładka „Zakładki” panelu stron z tagami (FB8): gdy zeszyt ma tagi, nad listą stoi rząd chipów „Zakładki” i
 * „#tag n” (najczęstsze najpierw); wybrany tag pokazuje swoje strony zamiast zakładek. Bez tagów — sama lista [marks].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MarksAndTags(ed: Editor, pages: List<PageState>, cur: Int, marks: @Composable () -> Unit) {
    val usage = ed.tagUsage()
    var sel by remember { mutableStateOf<String?>(null) }
    val chosen = usage.firstOrNull { it.tag == sel }
    Column(Modifier.fillMaxHeight()) {
        if (usage.isNotEmpty()) {
            FlowRow(
                Modifier.heightIn(max = 132.dp).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(T.t("Zakładki", "Bookmarks"), chosen == null, { sel = null }, icon = Icons.Rounded.Bookmark)
                for (u in usage) Chip("#${u.tag} ${u.pages.size}", chosen == u, { sel = if (chosen == u) null else u.tag })
            }
            HorizontalDivider(color = mint.line)
        }
        Box(Modifier.weight(1f)) { if (chosen == null) marks() else TaggedPages(ed, chosen.pages, pages, cur) }
    }
}

/** Strony jednego tagu: dotknięcie wiersza przenosi do strony, ołówek otwiera tagi strony. */
@Composable
private fun TaggedPages(ed: Editor, rows: List<Int>, pages: List<PageState>, cur: Int) {
    val m = mint
    LazyColumn(Modifier.fillMaxHeight()) {
        items(rows, key = { pages.getOrNull(it)?.id ?: it.toString() }) { idx ->
            val p = pages.getOrNull(idx) ?: return@items
            val here = idx == cur
            Row(
                Modifier.fillMaxWidth().background(if (here) m.accentSoft else Color.Transparent).clickable { ed.view?.goToPage(idx) }.padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Tag, null, Modifier.size(18.dp), tint = m.accent)
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(T.t("Strona ${idx + 1}", "Page ${idx + 1}"), style = MaterialTheme.typography.bodyMedium, color = if (here) m.accentText else MaterialTheme.colorScheme.onSurface)
                    Text(PageTags.label(ed.tagsOf(p.id)), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconBtn(Icons.Rounded.Edit, T.t("Tagi strony", "Page tags"), { ed.tagsFor = p.id }, size = 32.dp, iconSize = 16.dp)
            }
        }
    }
}
