package com.mdev.mnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.core.geom.Axes
import com.mdev.mnotes.core.geom.AxesSpec
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import kotlin.math.min

/** Gotowe zakresy: najczęstsze w zadaniach szkolnych i na pierwszym roku. */
private fun presets() = listOf(
    "−5 … 5" to AxesSpec(),
    T.t("I ćwiartka", "Quadrant I") to AxesSpec(0f, 10f, 0f, 10f, labelEvery = 2),
    "t ≥ 0" to AxesSpec(0f, 10f, -5f, 5f, xName = "t", yName = "v"),
    "−1 … 1" to AxesSpec(-1f, 1f, -1f, 1f, 0.25f, 0.25f, 60f, labelEvery = 2),
)

/**
 * Okno „Układ współrzędnych” (F8): zakres, krok podziałki, wielkość jednostki, siatka, opisy i strzałki, z podglądem
 * na żywo. Wstawiony układ to zwykłe kreski i opisy na stronie, a funkcję rysuje się na nim piórem.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AxesDialog(ed: Editor, onDismiss: () -> Unit) {
    val set = ed.settings
    val start = remember { if (set.axesLast.isEmpty()) AxesSpec() else AxesSpec.decode(set.axesLast) }
    var spec by remember { mutableStateOf(start) }
    // Pola trzymają tekst, żeby dało się wpisać „−” albo „0,” bez natychmiastowego poprawiania.
    var fx0 by remember { mutableStateOf(Axes.format(start.xMin)) }
    var fx1 by remember { mutableStateOf(Axes.format(start.xMax)) }
    var fy0 by remember { mutableStateOf(Axes.format(start.yMin)) }
    var fy1 by remember { mutableStateOf(Axes.format(start.yMax)) }
    var fsx by remember { mutableStateOf(Axes.format(start.xStep)) }
    var fsy by remember { mutableStateOf(Axes.format(start.yStep)) }
    fun fromFields(s: AxesSpec) = s.copy(
        xMin = AxesSpec.parse(fx0) ?: s.xMin, xMax = AxesSpec.parse(fx1) ?: s.xMax,
        yMin = AxesSpec.parse(fy0) ?: s.yMin, yMax = AxesSpec.parse(fy1) ?: s.yMax,
        xStep = AxesSpec.parse(fsx) ?: s.xStep, yStep = AxesSpec.parse(fsy) ?: s.yStep,
    )
    fun load(s: AxesSpec) {
        spec = s
        fx0 = Axes.format(s.xMin); fx1 = Axes.format(s.xMax); fy0 = Axes.format(s.yMin); fy1 = Axes.format(s.yMax)
        fsx = Axes.format(s.xStep); fsy = Axes.format(s.yStep)
    }
    val eff = fromFields(spec).normalized()
    MDialog(
        T.t("Układ współrzędnych", "Coordinate axes"), onDismiss,
        confirm = T.t("Wstaw", "Insert"),
        onConfirm = { set.axesLast = eff.encode(); ed.insertAxes(eff); onDismiss() },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AxesPreview(eff, Modifier.fillMaxWidth().height(170.dp))
            Text(
                T.t("Wykres rysujesz sam piórem — układ daje równe osie, podziałkę i kratkę. Po wstawieniu jest zaznaczony: przesuń go lub przeskaluj.",
                    "You draw the graph yourself — the axes give an even scale and grid. After inserting it is selected: move or resize it."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionLabel(T.t("Gotowe", "Presets"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((name, p) in presets()) Chip(name, false, { load(p.copy(unit = spec.unit)) })
            }
            SectionLabel(T.t("Zakres", "Range"))
            RangeRow("x", fx0, { fx0 = it }, fx1, { fx1 = it }, fsx, { fsx = it })
            RangeRow("y", fy0, { fy0 = it }, fy1, { fy1 = it }, fsy, { fsy = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(T.t("Jednostka", "Unit"), Modifier.weight(1f))
                Text(String.format("%.1f mm", spec.unit * 25.4f / 72f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MSlider(spec.unit, 8f, 60f) { spec = fromFields(spec).copy(unit = it) }
            ToggleRow(T.t("Siatka", "Grid"), spec.grid) { spec = fromFields(spec).copy(grid = it) }
            ToggleRow(T.t("Strzałki na osiach", "Arrows on axes"), spec.arrows) { spec = fromFields(spec).copy(arrows = it) }
            ToggleRow(T.t("Opisy liczbowe", "Number labels"), spec.labels) { spec = fromFields(spec).copy(labels = it) }
            if (spec.labels) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(T.t("Opis co", "Label every"), style = MaterialTheme.typography.bodyMedium)
                for (k in listOf(1, 2, 5)) Chip("$k", spec.labelEvery == k, { spec = fromFields(spec).copy(labelEvery = k) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(T.t("Nazwy osi", "Axis names"), style = MaterialTheme.typography.bodyMedium)
                MField(spec.xName, { spec = fromFields(spec).copy(xName = it.take(6)) }, Modifier.width(64.dp), hint = "x")
                MField(spec.yName, { spec = fromFields(spec).copy(yName = it.take(6)) }, Modifier.width(64.dp), hint = "y")
            }
        }
    }
}

@Composable
private fun RangeRow(axis: String, lo: String, setLo: (String) -> Unit, hi: String, setHi: (String) -> Unit, step: String, setStep: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(axis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(16.dp))
        MField(lo, setLo, Modifier.width(72.dp), hint = T.t("od", "from"), number = true)
        Text("…", style = MaterialTheme.typography.bodyMedium)
        MField(hi, setHi, Modifier.width(72.dp), hint = T.t("do", "to"), number = true)
        Text(T.t("krok", "step"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MField(step, setStep, Modifier.width(64.dp), hint = "1", number = true)
    }
}

/** Podgląd układu w kolorach motywu, wpisany w pole (bez opisów — te widać dopiero na stronie). */
@Composable
private fun AxesPreview(spec: AxesSpec, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val d = remember(spec) { Axes.build(spec, 0f, 0f) }
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(10.dp)) {
        val b = d.bounds
        val k = min(size.width / b.w, size.height / b.h)
        val ox = (size.width - b.w * k) / 2f - b.l * k
        val oy = (size.height - b.h * k) / 2f - b.t * k
        fun seg(xy: FloatArray, color: Color, w: Float) {
            for (i in 0 until xy.size / 2 - 1) drawLine(color, Offset(ox + xy[2 * i] * k, oy + xy[2 * i + 1] * k), Offset(ox + xy[2 * i + 2] * k, oy + xy[2 * i + 3] * k), w, StrokeCap.Round)
        }
        d.grid.forEach { seg(it, ink.copy(alpha = 0.18f), 1f) }
        d.axes.forEach { seg(it, ink, 1.6f) }
        d.ticks.forEach { seg(it, ink, 1.2f) }
    }
}
