package com.mdev.mnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.core.search.SettingsQuery

/**
 * Wyszukiwarka w Ustawieniach filtruje prawdziwe kontrolki „w miejscu”, a nie osobny indeks, więc etykiety
 * nie mogą się rozjechać z ekranem. `null` = tryb zwykły (także w panelach edytora, które używają tych samych wierszy).
 */
internal val LocalSettingsQuery = staticCompositionLocalOf<SettingsQuery?> { null }

/** 3c-A6: dokąd wiersz zgłasza swój wynik ([SettingsQuery.score]) — kolejność kategorii w wynikach wyszukiwania. */
internal val LocalScoreSink = staticCompositionLocalOf<((Int) -> Unit)?> { null }

/**
 * 3c-A6: słowa ekranu Ustawień (etykiety, klucze, tytuły grup) do poprawiania literówek. Zbierane z samych wierszy przy
 * wyszukiwaniu — osobna lista rozjechałaby się z ekranem, a tak zawiera dokładnie to, co da się znaleźć.
 */
internal object SearchVocab {
    private val seen = HashSet<String>()
    private var cached: List<Pair<String, String>> = emptyList()
    private var dirty = false
    val size: Int get() = seen.size
    fun note(vararg texts: String) { for (t in texts) if (t.isNotBlank() && seen.add(t)) dirty = true }
    fun words(): List<Pair<String, String>> {
        if (dirty) { cached = com.mdev.mnotes.core.search.SettingsPhrases.vocabulary(seen); dirty = false }
        return cached
    }
}

/** Tytuł bieżącej grupy z synonimami: dopasowanie wiersza liczy się razem z nim („gumka rozmiar”). */
private val LocalGroupTitle = compositionLocalOf { "" }

/** Czy sam tytuł (i synonimy) grupy spełnia zapytanie: wtedy grupa pokazuje całą zawartość, także opisy. */
private val LocalGroupHit = compositionLocalOf { false }

@Composable
internal fun GroupSearchScope(title: String, keys: String, content: @Composable () -> Unit) {
    val q = LocalSettingsQuery.current
    if (q != null) SearchVocab.note(title, keys)
    val s = q?.score(title, keys) ?: 0
    if (s > 0) LocalScoreSink.current?.invoke(s)
    androidx.compose.runtime.CompositionLocalProvider(
        LocalGroupTitle provides "$title $keys",
        LocalGroupHit provides (s > 0),
        content = content,
    )
}

/** Czy wiersz o etykiecie [label] (i synonimach [keys]) ma być widoczny przy bieżącym zapytaniu. */
@Composable
internal fun rowVisible(label: String, keys: String = ""): Boolean {
    val q = LocalSettingsQuery.current ?: return true
    SearchVocab.note(label, keys)
    val s = q.score(LocalGroupTitle.current, label, keys)
    if (s > 0) LocalScoreSink.current?.invoke(s)
    return s > 0
}

/** Etykieta z podświetlonymi trafieniami (bez zapytania: zwykły tekst). */
@Composable
internal fun rowLabel(label: String): AnnotatedString {
    val q = LocalSettingsQuery.current ?: return AnnotatedString(label)
    val bg = mint.accent.copy(alpha = 0.3f)
    return buildAnnotatedString {
        append(label)
        for (r in q.ranges(label)) addStyle(SpanStyle(background = bg), r.first, r.last + 1)
    }
}

/** Goła treść (opisy, przyciski, własne wiersze): przy wyszukiwaniu tylko gdy trafia sama grupa. */
@Composable
internal fun Extra(content: @Composable () -> Unit) {
    if (LocalSettingsQuery.current == null || LocalGroupHit.current) content()
}

/**
 * Pokazuje [body] w ramce z nagłówkiem tylko wtedy, gdy [body] ma jakąkolwiek zawartość (wysokość > 0); inaczej
 * zajmuje 0×0, więc nie zostaje ani nagłówek, ani pusta karta, ani odstęp. Gdy nic nie ma, można pokazać [fallback].
 */
@Composable
internal fun IfAny(
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    padH: Dp = 0.dp,
    padV: Dp = 0.dp,
    top: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
    backdrop: Modifier = Modifier,
    fallback: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box { header?.invoke() }
            Box(backdrop)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) { body() }
            Box { fallback?.invoke() }
        },
        modifier = modifier,
    ) { ms, c ->
        val w = if (c.hasBoundedWidth) c.maxWidth else 0
        val ph = padH.roundToPx()
        val pv = padV.roundToPx()
        val inner = (w - 2 * ph).coerceAtLeast(0)
        val b = ms[2].measure(Constraints(inner, inner, 0, Constraints.Infinity))
        if (b.height == 0) {
            if (fallback == null) return@Layout layout(0, 0) {}
            val f = ms[3].measure(Constraints(0, w, 0, Constraints.Infinity))
            return@Layout layout(w, f.height) { f.placeRelative(0, 0) }
        }
        val h = ms[0].measure(Constraints(0, w, 0, Constraints.Infinity))
        val t = top.roundToPx()
        val cardH = b.height + 2 * pv
        val back = ms[1].measure(Constraints.fixed(w, cardH))
        layout(w, t + h.height + cardH) {
            h.placeRelative(0, t)
            back.placeRelative(0, t + h.height)
            b.placeRelative(ph, t + h.height + pv)
        }
    }
}
