package com.mdev.mnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.core.layout.BarConfig
import com.mdev.mnotes.core.layout.BarIcons
import com.mdev.mnotes.core.layout.BarMode
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.editor.T

/** Otwiera Ustawienia na układzie paska; edytor dostarcza to menu przytrzymanego przycisku, bez przeciągania parametrów. */
val LocalBarSettings = staticCompositionLocalOf<() -> Unit> { {} }

/** Otwiera ustawienia pędzla formatu (zębatka w jego panelu). */
val LocalPainterSettings = staticCompositionLocalOf<() -> Unit> { {} }

/** 3c-A17b: otwiera Ustawienia z zapytaniem-kotwicą ([com.mdev.mnotes.core.search.SettingsAnchors]). */
val LocalSettingsAt = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Zapytanie, które w wyszukiwarce ustawień zostawia tylko grupę pędzla formatu. */
const val PAINTER_QUERY = "pędzel formatu"

/** Nazwa pozycji paska narzędzi o stabilnym id z [BarConfig.DEFAULT_ORDER]. */
internal fun barName(id: String): String = when (id) {
    "undo" -> T.t("Cofnij", "Undo")
    "redo" -> T.t("Ponów", "Redo")
    "eraser" -> T.t("Gumka", "Eraser")
    "painter" -> T.t("Pędzel formatu", "Format painter")
    "fill" -> T.t("Wypełnienie (wiaderko)", "Fill (paint bucket)")
    "pipette" -> T.t("Pipeta (próbnik koloru)", "Eyedropper")
    "lasso" -> T.t("Lasso", "Lasso")
    "text" -> T.t("Tekst", "Text")
    "laser" -> T.t("Laser", "Laser")
    "hand" -> T.t("Ręka (przewijanie)", "Hand (pan)")
    "curve" -> T.t("Krzywa Béziera", "Bézier curve")
    "tape" -> T.t("Taśma", "Tape")
    "space" -> T.t("Wstaw / usuń odstęp", "Insert / remove space")
    "ruler" -> T.t("Linijka", "Ruler")
    "protractor" -> T.t("Kątomierz", "Protractor")
    "geometry" -> T.t("Geometria (konstrukcje)", "Geometry (constructions)")
    else -> penOf(id)?.let { penName(it) } ?: id
}

/** Pióro o danym id pozycji paska (np. „fountain”) albo null. */
internal fun penOf(id: String): PenKind? = PenKind.entries.firstOrNull { it.name.lowercase() == id }

/** Ikona Material pozycji paska ([BarIcons.Kind.VECTOR]); null dla pozycji z własnym glifem. */
internal fun barVector(id: String): ImageVector? = when (id) {
    "undo" -> Icons.AutoMirrored.Rounded.Undo
    "redo" -> Icons.AutoMirrored.Rounded.Redo
    "painter" -> Icons.Rounded.FormatPaint
    "fill" -> Icons.Rounded.FormatColorFill
    "pipette" -> Icons.Rounded.Colorize
    "text" -> Icons.Rounded.TextFields
    "laser" -> Icons.Rounded.TrackChanges
    "hand" -> Icons.Rounded.PanTool
    "tape" -> Icons.Rounded.VisibilityOff
    "space" -> Icons.Rounded.UnfoldMore
    "ruler" -> Icons.Rounded.Straighten
    "geometry" -> Icons.Rounded.Architecture
    else -> null
}

/** Ikona pozycji paska w Ustawieniach — ten sam glif co na pasku (2b-A1). */
@Composable
private fun BarIcon(id: String, set: com.mdev.mnotes.data.Settings, ink: androidx.compose.ui.graphics.Color) {
    val m = Modifier.size(24.dp)
    when (BarIcons.kind(id)) {
        BarIcons.Kind.PEN -> penOf(id)?.let { PenGlyph(it, androidx.compose.ui.graphics.Color(set.penKit.pen(it).color), ink, m) }
        BarIcons.Kind.ERASER -> EraserModeGlyph(set.eraserMode, ink, m)
        BarIcons.Kind.LASSO -> LassoGlyph(set.lassoRect, ink, m)
        BarIcons.Kind.CURVE -> BezierGlyph(ink, m)
        BarIcons.Kind.PROTRACTOR -> ProtractorGlyph(ink, m)
        BarIcons.Kind.VECTOR -> barVector(id)?.let { Icon(it, null, Modifier.size(22.dp), tint = ink) }
        null -> {}
    }
}

/**
 * Układ paska narzędzi w Ustawieniach: kolejność strzałkami i tryb każdej pozycji. Te same zmiany robi się na pasku
 * przytrzymaniem przycisku; tu widać wszystko naraz, także to, co siedzi pod „⋯”.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BarLayoutPrefs(set: com.mdev.mnotes.data.Settings) {
    val cfg = set.barConfig
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    Group(T.t("Cofnij i Ponów", "Undo and Redo"), keys = "undo redo kapsuła róg cofanie ponawianie") { Extra {
        Text(
            T.t(
                "Mogą stać na pasku narzędzi albo w osobnej kapsule w rogu strony, poza paskiem.",
                "They can sit on the toolbar or in a separate capsule in a corner of the page, off the toolbar.",
            ),
            style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val names = listOf(
                T.t("Na pasku", "On the toolbar"), T.t("Lewy górny róg", "Top left"), T.t("Prawy górny róg", "Top right"),
                T.t("Lewy dolny róg", "Bottom left"), T.t("Prawy dolny róg", "Bottom right"),
            )
            names.forEachIndexed { i, n -> Chip(n, set.undoDock == i, { set.undoDock = i }) }
        }
    } }
    Group(T.t("Układ paska narzędzi", "Toolbar layout"), keys = "kolejność narzędzia ukryj schowaj menu order tools hide") { Extra {
        Text(
            T.t(
                "„Gdy jest miejsce” chowa narzędzie pod „⋯” dopiero przy ciasnocie (np. przy kartach), „Zawsze” nigdy go nie chowa, " +
                    "a „Pod ⋯” trzyma je w menu. To samo zrobisz na pasku, przytrzymując przycisk.",
                "“When there is room” moves a tool under “⋯” only when the bar is tight (e.g. with tabs), “Always” never hides it, " +
                    "and “Under ⋯” keeps it in the menu. You can do the same on the bar by long-pressing a button.",
            ),
            style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.padding(bottom = 6.dp),
        )
        cfg.order.forEachIndexed { i, id ->
            // Cofnij i Ponów w kapsule w rogu nie stoją na pasku, więc nie mają tu ani kolejności, ani trybu.
            if (set.undoDock != 0 && (id == "undo" || id == "redo")) return@forEachIndexed
            if (i > 0) HorizontalDivider(color = mint.line)
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { BarIcon(id, set, ink) }
                    Spacer(Modifier.width(10.dp))
                    Text(barName(id), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconBtn(Icons.Rounded.ArrowUpward, T.t("Przesuń wcześniej", "Move earlier"), { set.barConfig = cfg.move(id, -1) }, enabled = i > 0, size = 34.dp, iconSize = 18.dp)
                    IconBtn(Icons.Rounded.ArrowDownward, T.t("Przesuń później", "Move later"), { set.barConfig = cfg.move(id, 1) }, enabled = i < cfg.order.size - 1, size = 34.dp, iconSize = 18.dp)
                }
                FlowRow(Modifier.padding(top = 4.dp, start = 38.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(T.t("Zawsze", "Always"), cfg.mode(id) == BarMode.ALWAYS, { set.barConfig = cfg.withMode(id, BarMode.ALWAYS) })
                    Chip(T.t("Gdy jest miejsce", "When there is room"), cfg.mode(id) == BarMode.AUTO, { set.barConfig = cfg.withMode(id, BarMode.AUTO) })
                    Chip(T.t("Pod ⋯", "Under ⋯"), cfg.mode(id) == BarMode.MENU, { set.barConfig = cfg.withMode(id, BarMode.MENU) })
                }
            }
        }
        Chip(T.t("Przywróć układ domyślny", "Restore default layout"), false, { set.barConfig = cfg.reset() }, Modifier.padding(top = 6.dp), icon = Icons.Rounded.RestartAlt)
    } }
}
