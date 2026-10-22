package com.mdev.mnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.core.edit.AngleSnap
import com.mdev.mnotes.data.Settings
import com.mdev.mnotes.editor.T
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Otwiera Ustawienia na grupie „Kąty obrotu” (zębatka w panelu lasso). */
val LocalRotationSettings = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

/** Zapytanie, które w wyszukiwarce ustawień zostawia tylko grupę kątów obrotu. */
const val ROTATION_QUERY = "kąty obrotu"

private val PRESET_STEPS = listOf(5f, 10f, 15f, 30f, 45f, 90f)

/** Krótki opis zbioru kątów: „co 15°”, „0°, 45°, 90°” albo „12 wybranych kątów”. */
internal fun anglesSummary(angles: List<Float>): String {
    if (angles.isEmpty()) return T.t("brak kątów", "no angles")
    PRESET_STEPS.firstOrNull { AngleSnap.every(it) == angles }?.let { return T.t("co ${AngleSnap.label(it)}°", "every ${AngleSnap.label(it)}°") }
    if (angles.size <= 6) return angles.joinToString(", ") { "${AngleSnap.label(it)}°" }
    return T.t("${angles.size} wybranych kątów", "${angles.size} chosen angles")
}

private fun marked(angles: List<Float>, a: Float) = angles.any { abs(AngleSnap.wrap(it - a)) < 0.05f }

/**
 * Edytor zbioru kątów przyciągania: tarcza-kątomierz (0° u góry, kąty rosną zgodnie z ruchem wskazówek zegara, tak
 * jak obraca się uchwyt zaznaczenia), wpisywanie wartości, gotowe zestawy i lista wybranych kątów.
 */
@Composable
internal fun AngleSetEditor(angles: List<Float>, tolerance: Float, onChange: (List<Float>) -> Unit) {
    var pointer by remember { mutableFloatStateOf(45f) }
    var text by remember { mutableStateOf("45") }
    var mirror by remember { mutableStateOf(true) }
    fun point(a: Float) { pointer = a; text = AngleSnap.label(a) }
    val has = marked(angles, pointer)

    fun add() = onChange(AngleSnap.normalize(angles + pointer + (if (mirror) listOf(-pointer) else emptyList())))
    fun remove() {
        val drop = listOf(pointer) + (if (mirror) listOf(-pointer) else emptyList())
        onChange(angles.filter { a -> drop.none { abs(AngleSnap.wrap(a - it)) < 0.05f } })
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Dial(angles, tolerance, pointer, has, ::point, Modifier.align(Alignment.CenterHorizontally))
        Text(
            T.t("Przeciągnij po tarczy albo wpisz kąt. Kropki to kąty, na których obracane zaznaczenie się zatrzyma.", "Drag around the dial or type an angle. Dots are the angles a rotating selection stops at."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("−1°", false, { point(AngleSnap.normalize(listOf(pointer - 1f)).first()) })
            MField(text, { t -> text = t; AngleSnap.parse(t).singleOrNull()?.let { pointer = it } }, Modifier.width(92.dp), hint = "0", number = true)
            Text("°", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Chip("+1°", false, { point(AngleSnap.normalize(listOf(pointer + 1f)).first()) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (has) Chip(T.t("Usuń ${AngleSnap.label(pointer)}°", "Remove ${AngleSnap.label(pointer)}°"), false, ::remove, icon = Icons.Rounded.Remove)
            else Chip(T.t("Dodaj ${AngleSnap.label(pointer)}°", "Add ${AngleSnap.label(pointer)}°"), true, ::add, icon = Icons.Rounded.Add)
        }
        ToggleRow(
            T.t("Dodawaj też kąt przeciwny (${AngleSnap.label(pointer)}° i ${AngleSnap.label(AngleSnap.normalize(listOf(-pointer)).first())}°)", "Also add the opposite angle (${AngleSnap.label(pointer)}° and ${AngleSnap.label(AngleSnap.normalize(listOf(-pointer)).first())}°)"),
            mirror, keys = "symetria odbicie przeciwny mirror",
        ) { mirror = it }

        Text(T.t("Gotowe zestawy", "Presets"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in PRESET_STEPS) Chip(T.t("Co ${AngleSnap.label(s)}°", "Every ${AngleSnap.label(s)}°"), AngleSnap.every(s) == angles, { onChange(AngleSnap.every(s)) })
            Chip(T.t("Wyczyść", "Clear"), false, { onChange(emptyList()) }, icon = Icons.Rounded.Close)
            Chip(T.t("Domyślne", "Default"), false, { onChange(AngleSnap.every(AngleSnap.DEFAULT_STEP)) }, icon = Icons.Rounded.RestartAlt)
        }

        Text(
            T.t("Wybrane kąty: ${angles.size}", "Chosen angles: ${angles.size}"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (angles.isEmpty()) {
            Text(T.t("Brak kątów — przyciąganie nic nie robi. Dodaj kąt albo wybierz zestaw.", "No angles — snapping does nothing. Add an angle or pick a preset."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else if (angles.size > 24) {
            Text(T.t("Za dużo, by wypisać. Dotknij kropki na tarczy, by wybrać kąt, i usuń go przyciskiem.", "Too many to list. Tap a dot on the dial to pick an angle, then remove it with the button."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in angles) AngleTag(a, abs(AngleSnap.wrap(a - pointer)) < 0.05f, { point(a) }, { onChange(angles - a) })
            }
        }
    }
}

@Composable
private fun AngleTag(a: Float, selected: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    val m = mint
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.clip(shape).background(if (selected) m.accentSoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, if (selected) m.accent else Color.Transparent, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${AngleSnap.label(a)}°", Modifier.clickable(onClick = onSelect).padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            style = MaterialTheme.typography.labelLarge, color = if (selected) m.accentText else MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            Icons.Rounded.Close, T.t("Usuń kąt", "Remove angle"),
            Modifier.clickable(onClick = onRemove).padding(horizontal = 6.dp, vertical = 8.dp).size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun polar(c: Offset, r: Float, deg: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())
}

@Composable
private fun Dial(angles: List<Float>, tolerance: Float, pointer: Float, pointerMarked: Boolean, onPoint: (Float) -> Unit, modifier: Modifier = Modifier) {
    val m = mint
    val dp = LocalDensity.current.density
    val face = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val labelPaint = remember(ink) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = ink.toArgb(); textAlign = android.graphics.Paint.Align.CENTER }
    }
    val box = Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)

    fun angleAt(p: Offset, size: Size): Float {
        val c = Offset(size.width / 2, size.height / 2)
        var d = Math.toDegrees(atan2((p.x - c.x).toDouble(), (c.y - p.y).toDouble())).toFloat()
        d = ((d % 360f) + 360f) % 360f
        val whole = Math.round(d).toFloat() % 360f
        angles.firstOrNull { abs(AngleSnap.wrap(it - d)) < 2f }?.let { return it }
        return whole
    }

    Box(modifier.then(box), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.matchParentSize()
                .pointerInput(angles) { detectTapGestures { onPoint(angleAt(it, Size(size.width.toFloat(), size.height.toFloat()))) } }
                .pointerInput(angles) {
                    detectDragGestures(
                        onDragStart = { onPoint(angleAt(it, Size(size.width.toFloat(), size.height.toFloat()))) },
                        onDrag = { c, _ -> onPoint(angleAt(c.position, Size(size.width.toFloat(), size.height.toFloat()))); c.consume() },
                    )
                },
        ) {
            val c = Offset(size.width / 2, size.height / 2)
            val outer = size.minDimension / 2 - 22 * dp
            val ring = outer - 20 * dp
            drawCircle(face, outer + 4 * dp, c)
            drawCircle(m.line, outer + 4 * dp, c, style = Stroke(1f * dp))

            // pasma zasięgu przyciągania wokół wybranych kątów
            if (angles.isNotEmpty()) for (a in angles) drawArc(
                m.accent.copy(alpha = 0.22f), a - tolerance - 90f, 2 * tolerance, false,
                Offset(c.x - ring, c.y - ring), Size(2 * ring, 2 * ring), style = Stroke(16 * dp, cap = StrokeCap.Butt),
            )
            drawCircle(m.line, ring, c, style = Stroke(1f * dp))

            // podziałka: co 5° drobna, co 15° średnia, co 90° długa
            for (d in 0 until 360 step 5) {
                val len = when { d % 90 == 0 -> 12f; d % 15 == 0 -> 8f; else -> 4f } * dp
                drawLine(ink.copy(alpha = if (d % 15 == 0) 0.7f else 0.4f), polar(c, outer, d.toFloat()), polar(c, outer - len, d.toFloat()), if (d % 90 == 0) 2f * dp else 1f * dp)
            }
            drawLabels(c, outer + 14 * dp, labelPaint, 10.5f * dp)

            // wybrane kąty
            val r = (if (angles.size > 48) 2.6f else 4.2f) * dp
            for (a in angles) drawCircle(m.accent, r, polar(c, ring, a))

            // wskaźnik
            val tip = polar(c, ring, pointer)
            drawLine(m.accent.copy(alpha = 0.55f), polar(c, ring * 0.5f, pointer), tip, 2f * dp, StrokeCap.Round)
            drawCircle(if (pointerMarked) m.accent else Color.White, 11f * dp, tip)
            drawCircle(m.accent, 11f * dp, tip, style = Stroke(2.2f * dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${AngleSnap.label(pointer)}°", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (pointerMarked) T.t("przyciągany", "snapped") else T.t("nie wybrany", "not chosen"),
                style = MaterialTheme.typography.labelMedium, color = if (pointerMarked) m.accentText else ink,
            )
        }
    }
}

private fun DrawScope.drawLabels(c: Offset, r: Float, paint: android.graphics.Paint, textSize: Float) {
    paint.textSize = textSize
    drawIntoCanvas { cv ->
        for (d in 0 until 360 step 30) {
            val p = polar(c, r, d.toFloat())
            cv.nativeCanvas.drawText("$d", p.x, p.y + textSize * 0.35f, paint)
        }
    }
}

/** Skrót stanu do panelu lasso: „Obrót przyciąga: co 15°”. */
internal fun rotationHint(set: Settings): String =
    if (!set.rotSnap) T.t("Obrót jest płynny, bez zatrzymywania na kątach.", "Rotation is free, with no stops at angles.")
    else T.t("Obrót zatrzyma się na: ${anglesSummary(set.rotAngles)} (±${AngleSnap.label(set.rotTolerance)}°).", "Rotation stops at: ${anglesSummary(set.rotAngles)} (±${AngleSnap.label(set.rotTolerance)}°).")
