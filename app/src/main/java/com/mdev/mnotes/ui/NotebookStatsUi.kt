package com.mdev.mnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.core.stats.ActiveTime
import com.mdev.mnotes.core.stats.NotebookStats
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

private const val WEEKS_SHOWN = 8

/**
 * Okno „Statystyki zeszytu” (FB7): treść liczona w tle ze wszystkich stron (także niewczytanych) i czas pisania
 * z ostatnich tygodni jako słupki. Wszystko z tego urządzenia; nic nie wychodzi poza aplikację.
 */
@Composable
internal fun NotebookStatsDialog(ed: Editor, onDismiss: () -> Unit) {
    val m = mint
    // Bieżące otwarcie też ma się liczyć, a licznik zapisuje się do preferencji dopiero co minutę.
    LaunchedEffect(Unit) { ed.flushWriting() }
    val counts by produceState<NotebookStats.Counts?>(null) {
        val pages = ed.pagesSnapshot()
        value = withContext(Dispatchers.Default) { NotebookStats.count(pages.map { ed.doc.peekContent(it).elements }) }
    }
    val today = remember { LocalDate.now() }
    val weeks = ActiveTime.recent(ed.settings.writingWeeks(ed.nbId), today, WEEKS_SHOWN)
    MDialog(T.t("Statystyki zeszytu", "Notebook statistics"), onDismiss, dismissText = T.t("Zamknij", "Close")) {
        Text(ed.repo.meta(ed.nbId)?.title.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel(T.t("Treść", "Content"))
        val c = counts
        if (c == null) Text(T.t("Liczę…", "Counting…"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            StatRow(T.t("Strony", "Pages"), "%,d".format(c.pages))
            StatRow(T.t("Kreski pisma", "Handwritten strokes"), "%,d".format(c.strokes))
            if (c.shapes > 0) StatRow(T.t("Kształty i krzywe", "Shapes and curves"), "%,d".format(c.shapes))
            StatRow(T.t("Pola tekstu", "Text boxes"), if (c.textBoxes == 0) "0" else T.t("${c.textBoxes} · ${"%,d".format(c.words)} słów", "${c.textBoxes} · ${"%,d".format(c.words)} words"))
            if (c.images > 0) StatRow(T.t("Obrazy i zdjęcia", "Images and photos"), "%,d".format(c.images))
            StatRow(T.t("Długość atramentu", "Ink length"), "%.1f m".format(c.inkMeters))
        }
        SectionLabel(T.t("Czas pisania", "Writing time"))
        StatRow(T.t("Ten tydzień", "This week"), ActiveTime.format(weeks.last().second))
        StatRow(T.t("Poprzedni tydzień", "Last week"), ActiveTime.format(weeks[weeks.size - 2].second))
        StatRow(T.t("Ostatnie $WEEKS_SHOWN tygodni", "Last $WEEKS_SHOWN weeks"), ActiveTime.format(weeks.sumOf { it.second }))
        val top = weeks.maxOf { it.second }.coerceAtLeast(1L)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            weeks.forEachIndexed { i, (_, ms) ->
                val monday = today.minusWeeks((weeks.size - 1 - i).toLong()).with(DayOfWeek.MONDAY)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(56.dp), contentAlignment = Alignment.BottomCenter) {
                        // Pusty tydzień zostaje cienką kreską, żeby było widać, że jest na osi, a nie że go brakuje.
                        Box(
                            Modifier.width(14.dp).height((56f * ms / top).coerceAtLeast(2f).dp).clip(RoundedCornerShape(3.dp))
                                .background(if (i == weeks.size - 1) m.accent else m.accent.copy(alpha = 0.35f)),
                        )
                    }
                    Text("%02d.%02d".format(monday.dayOfMonth, monday.monthValue), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            T.t("Liczone na tym urządzeniu z odstępów między kreskami; przerwa dłuższa niż 2 min się nie liczy. Nic nie jest nigdzie wysyłane.",
                "Counted on this device from the gaps between strokes; a break longer than 2 min does not count. Nothing is sent anywhere."),
            Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = mint.accentText)
    }
}
