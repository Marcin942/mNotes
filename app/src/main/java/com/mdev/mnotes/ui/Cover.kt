package com.mdev.mnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.data.CoverStyle
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Stonowana paleta okładek (kolory „papierowe”: szałwia, atrament, terakota…) i domyślny wybór. */
object CoverPalette {
    val colors: List<Int> = listOf(
        0xFF6B8F7B, 0xFF5E7FA3, 0xFF2F4468, 0xFF7F6A93, 0xFFB5694F, 0xFFB86F82,
        0xFFC9A05A, 0xFFB5A58E, 0xFF7C8450, 0xFF3F8586, 0xFF424850, 0xFF74384A,
    ).map { it.toInt() }

    val default: Int = colors[0]
    const val DEFAULT_STYLE = CoverStyle.LINEN
}

/** Kształt okładki: wąski grzbiet po lewej, miękkie rogi po prawej. */
val CoverShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp)

/**
 * Okładka bez tytułu: delikatny gradient i połysk, subtelny wzór (w odcieniu okładki, nie krzykliwy),
 * zawias grzbietu i cienka krawędź. Na jasnych okładkach wzór jest ciemny, na ciemnych — jasny.
 */
@Composable
fun CoverArt(color: Int, style: Int, modifier: Modifier = Modifier) {
    val base = Color(color)
    val lightBase = base.luminance() > 0.4f
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val px = max(density, 1f)
        drawRect(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.10f), base, lerp(base, Color.Black, 0.16f))))
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = Offset(w * 0.3f, h * 0.08f), radius = w * 1.1f))

        val ink = if (lightBase) Color.Black else Color.White
        val pat = ink.copy(alpha = if (lightBase) 0.08f else 0.11f)
        clipRect(0f, 0f, w, h) {
            when (style) {
                CoverStyle.STRIPES -> {
                    var x = -h
                    while (x < w) { drawLine(pat, Offset(x, h), Offset(x + h, 0f), strokeWidth = max(w * 0.016f, px)); x += w * 0.09f }
                }
                CoverStyle.DOTS -> {
                    val st = w * 0.09f
                    var y = st / 2
                    while (y < h) { var x = st / 2; while (x < w) { drawCircle(pat, max(w * 0.012f, px * 0.8f), Offset(x, y)); x += st }; y += st }
                }
                CoverStyle.WAVES -> {
                    val amp = w * 0.03f
                    var y = h * 0.08f
                    while (y < h) {
                        val p = Path(); p.moveTo(0f, y)
                        var x = 0f
                        while (x <= w) { p.lineTo(x, y + sin(x / w * 6.2832f * 3f) * amp); x += 4f }
                        drawPath(p, pat, style = Stroke(max(w * 0.008f, px))); y += h * 0.065f
                    }
                }
                CoverStyle.GRID -> {
                    val st = w * 0.1f
                    var x = st; while (x < w) { drawLine(pat, Offset(x, 0f), Offset(x, h), max(w * 0.004f, px * 0.8f)); x += st }
                    var y = st; while (y < h) { drawLine(pat, Offset(0f, y), Offset(w, y), max(w * 0.004f, px * 0.8f)); y += st }
                }
                CoverStyle.LINEN -> {
                    // Tkanina: cieńsze od piksela-dwóch linie, poziome jasne, pionowe ciemne.
                    val st = max(2.4f * px, 3f)
                    val a = Color.White.copy(alpha = if (lightBase) 0.16f else 0.06f)
                    val b = Color.Black.copy(alpha = if (lightBase) 0.05f else 0.09f)
                    var y = 0f; while (y < h) { drawLine(a, Offset(0f, y), Offset(w, y), px * 0.9f); y += st }
                    var x = 0f; while (x < w) { drawLine(b, Offset(x, 0f), Offset(x, h), px * 0.9f); x += st }
                }
                CoverStyle.ARCS -> {
                    // Koncentryczne łuki wychodzące z prawego dolnego rogu.
                    val c = Offset(w * 1.02f, h * 1.0f)
                    val far = hypot(c.x, c.y)
                    var r = w * 0.2f
                    while (r < far) { drawCircle(pat, r, c, style = Stroke(max(w * 0.01f, px))); r += w * 0.14f }
                }
            }
        }

        // Zawias grzbietu: ciemny cień przy krawędzi, jasna i ciemna linia zagięcia.
        val spine = w * 0.075f
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.08f), Color.Transparent), startX = 0f, endX = spine * 1.7f), size = Size(spine * 1.7f, h))
        drawRect(Color.Black.copy(alpha = 0.16f), topLeft = Offset(spine, 0f), size = Size(px, h))
        drawRect(Color.White.copy(alpha = 0.18f), topLeft = Offset(spine + px, 0f), size = Size(px, h))
        // Cienka jasna krawędź ostrzy kontur także na ciemnych kolorach.
        drawRect(Color.White.copy(alpha = 0.10f), topLeft = Offset(px / 2, px / 2), size = Size(w - px, h - px), style = Stroke(px))
    }
}

/** Okładka z tytułem na papierowej etykiecie (kolumna zeszytu w siatce biblioteki i podgląd w kreatorze). */
@Composable
fun NotebookCover(title: String, color: Int, style: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        // Czcionka i marginesy etykiety rosną z szerokością okładki, żeby tytuł w małym podglądzie nie łamał się w środku słowa.
        val fs = (maxWidth.value * 0.095f).coerceIn(9f, 15f)
        CoverArt(color, style, Modifier.fillMaxSize())
        Box(
            Modifier.align(BiasAlignment(0f, -0.22f)).padding(start = maxWidth * 0.15f, end = maxWidth * 0.07f).fillMaxWidth()
                .clip(RoundedCornerShape(3.dp)).background(Color(0xF2FBF8F1))
                .border(0.5.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.ifBlank { " " }, color = Color(0xFF2A2B2E), maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = fs.sp, lineHeight = (fs * 1.3f).sp),
            )
        }
    }
}

/** Wybór koloru i wzoru okładki (kreator nowego zeszytu oraz „Zmień okładkę…”). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoverPicker(cover: Int, style: Int, onCover: (Int) -> Unit, onStyle: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in CoverPalette.colors) Dot(Color(c), 30.dp, selected = c == cover, onClick = { onCover(c) })
    }
    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val names = listOf(T.t("Gładka", "Plain"), T.t("Pasy", "Stripes"), T.t("Kropki", "Dots"), T.t("Fale", "Waves"), T.t("Kratka", "Grid"), T.t("Len", "Linen"), T.t("Łuki", "Arcs"))
        for (i in 0 until CoverStyle.COUNT) Chip(names[i], style == i, { onStyle(i) })
    }
}
