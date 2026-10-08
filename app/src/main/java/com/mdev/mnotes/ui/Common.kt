package com.mdev.mnotes.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdev.mnotes.editor.T
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind

fun dateText(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
fun dateTimeText(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

/** Lekkie „tyknięcie” przy dotknięciu przycisku (wyłączane w ustawieniach). */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    val on = LocalHaptics.current
    return remember(view, on) { { if (on) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } }
}

/**
 * Przycisk narzędzia z dowolną treścią: tło płynnie przechodzi między stanami (spoczynek, najechanie, wciśnięcie,
 * wybrany), przy wciśnięciu lekko się zapada (sprężyna), a dotknięcie daje subtelną haptykę. Treść dostaje kolor ikony.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolBtn(
    tip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = Color.Unspecified,
    size: Dp = 40.dp,
    shortcut: String? = null,
    content: @Composable (Color) -> Unit,
) {
    val m = mint
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hovered by src.collectIsHoveredAsState()
    val tick = rememberTick()
    val bg by animateColorAsState(
        when {
            selected -> m.accentSoft
            pressed -> m.hover.copy(alpha = (m.hover.alpha * 2f).coerceAtMost(1f))
            hovered && enabled -> m.hover
            else -> Color.Transparent
        },
        tween(110), label = "toolBg",
    )
    val scale by animateFloatAsState(if (pressed && enabled) 0.88f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "toolScale")
    val base = MaterialTheme.colorScheme.onSurface
    val c by animateColorAsState(
        when {
            !enabled -> base.copy(alpha = 0.32f)
            tint != Color.Unspecified -> tint
            selected -> m.accentText
            else -> base
        },
        tween(140), label = "toolTint",
    )
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(if (shortcut != null) "$tip  ($shortcut)" else tip) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(10.dp)).background(bg)
                .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button) { tick(); onClick() },
            contentAlignment = Alignment.Center,
        ) { content(c) }
    }
}

/** Przycisk-ikona z podpowiedzią (tooltip pokazuje też skrót klawiszowy — wygodne przy myszy). */
@Composable
fun IconBtn(
    icon: ImageVector,
    tip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = Color.Unspecified,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    shortcut: String? = null,
) {
    ToolBtn(tip, onClick, modifier, enabled, selected, tint, size, shortcut) { c -> Icon(icon, tip, tint = c, modifier = Modifier.size(iconSize)) }
}

/**
 * Wejście dla kafelka/wiersza: klik (z Ctrl/Shift), długie przytrzymanie, prawy przycisk myszy.
 * Przewijanie rodzica anuluje gest, więc nie przeszkadza w listach.
 */
fun Modifier.tileInput(
    onClick: (ctrl: Boolean, shift: Boolean) -> Unit,
    onLong: () -> Unit = {},
    onSecondary: (Offset) -> Unit = {},
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val ev = currentEvent
        if (ev.buttons.isSecondaryPressed) {
            down.consume(); onSecondary(down.position)
            waitForUpOrCancellation()
            return@awaitEachGesture
        }
        val ctrl = ev.keyboardModifiers.isCtrlPressed
        val shift = ev.keyboardModifiers.isShiftPressed
        var timedOut = false
        val up = try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
        } catch (_: PointerEventTimeoutCancellationException) { timedOut = true; null }
        if (timedOut) { onLong(); waitForUpOrCancellation() }
        else if (up != null) { up.consume(); onClick(ctrl, shift) }
    }
}

@Composable
fun MDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    dismissText: String = T.t("Anuluj", "Cancel"),
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Column { content() } },
        confirmButton = { if (confirm != null) TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirm, color = if (confirmEnabled) mint.accentText else Color.Unspecified) } },
        dismissButton = if (dismissText.isEmpty()) null else { { TextButton(onClick = onDismiss) { Text(dismissText) } } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    )
}

/** True, gdy jakiekolwiek pole tekstowe ma fokus — skróty jednoliterowe edytora są wtedy wyłączone. */
var fieldFocused by mutableStateOf(false)

/** Rama pola tekstowego w stylu Mint (wspólna dla wersji String i TextFieldValue). */
@Composable
private fun FieldFrame(focus: Boolean, singleLine: Boolean, leading: @Composable (() -> Unit)?, hint: String, empty: Boolean, inner: @Composable () -> Unit) {
    val m = mint
    Row(
        Modifier.fillMaxWidth().height(if (singleLine) 40.dp else 96.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (m.dark) 0.6f else 0.55f))
            .border(1.5.dp, if (focus) m.accent else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
        Box(Modifier.weight(1f).padding(vertical = if (singleLine) 0.dp else 10.dp)) {
            if (empty && hint.isNotEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            inner()
        }
    }
}

/** Pole tekstowe w stylu Mint (obramowanie, akcent przy fokusie) — bez pływającej etykiety. */
@Composable
fun MField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    singleLine: Boolean = true,
    number: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    val m = mint
    var focus by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(m.accent),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        modifier = modifier.onFocusChanged { focus = it.isFocused; fieldFocused = it.isFocused },
        decorationBox = { inner -> FieldFrame(focus, singleLine, leading, hint, value.isEmpty(), inner) },
    )
}

/** Jak [MField], ale ze stanem zaznaczenia (np. do operacji na wierszach). Zawsze wielowierszowe. */
@Composable
fun MAreaField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier, hint: String = "") {
    val m = mint
    var focus by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(m.accent),
        modifier = modifier.onFocusChanged { focus = it.isFocused; fieldFocused = it.isFocused },
        decorationBox = { inner -> FieldFrame(focus, false, null, hint, value.text.isEmpty(), inner) },
    )
}

@Composable
fun Dot(color: Color, size: Dp = 22.dp, selected: Boolean = false, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, mixed: Boolean = false) {
    val m = mint
    val rainbow = remember { Brush.sweepGradient(List(7) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 0.75f, 0.95f))) }) }
    Box(
        modifier.size(size).clip(CircleShape).then(if (mixed) Modifier.background(rainbow) else Modifier.background(color))
            .border(if (selected) 2.5.dp else 1.dp, if (selected) m.accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = if (color.luminanceLow()) Color.White else Color.Black, modifier = Modifier.size(size * 0.6f))
    }
}

private fun Color.luminanceLow() = (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, glyph: (@Composable (Color) -> Unit)? = null) {
    val m = mint
    val tick = rememberTick()
    val bg by animateColorAsState(if (selected) m.accentSoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), tween(120), label = "chipBg")
    val edge by animateColorAsState(if (selected) m.accent else Color.Transparent, tween(120), label = "chipEdge")
    val fg = if (selected) m.accentText else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(bg).border(1.dp, edge, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) { tick(); onClick() }.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (glyph != null) glyph(fg)
        else if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) m.accentText else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 14.dp, bottom = 6.dp))
}

/** Suwak z gradientowym torem (do HSV). */
@Composable
fun GradientSlider(value: Float, brush: Brush, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var w by remember { mutableStateOf(1f) }
    val m = mint
    Box(
        modifier.fillMaxWidth().height(26.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / w).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> onChange((c.position.x / w).coerceIn(0f, 1f)); c.consume() } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp)))
        val x = (value * w).coerceIn(0f, w)
        val half = with(LocalDensity.current) { 11.dp.toPx() }
        Box(Modifier.offset { IntOffset((x - half).toInt().coerceIn(0, (w - 2 * half).toInt().coerceAtLeast(0)), 0) }.size(22.dp).clip(CircleShape).background(Color.White).border(2.dp, m.accent, CircleShape))
    }
}

/**
 * Paleta atramentów: trzy rzędy po osiem — neutralne i głębokie, „rdzeniowe” (stonowane, nienasycone),
 * oraz miękkie pastele. Odcienie dobrane tak, by dobrze wyglądały na białym, kremowym i ciemnym papierze.
 */
val PALETTE: List<Int> = listOf(
    0xFF1B1D21, 0xFF454A52, 0xFF868C95, 0xFFC9CDD3, 0xFFFFFFFF, 0xFF24407E, 0xFF7A4A2B, 0xFF6A3D8F,
    0xFFC8433F, 0xFFE2742F, 0xFFDDA11C, 0xFF3E8E5A, 0xFF1E8C93, 0xFF2F7FC1, 0xFF5E6AD2, 0xFFD1568A,
    0xFFE58B8B, 0xFFF0AE7B, 0xFFEBCB6B, 0xFF9CCB8B, 0xFF7FCBC6, 0xFF8FBDE8, 0xFFA9A6E8, 0xFFE8A3C4,
).map { it.toInt() }

/**
 * Paleta zakreślaczy: dziesięć stonowanych pasteli (dobór w OKLCH: jasność .83–.93, chroma .03–.115).
 * Zapisana barwa C to „atrament” zakreślacza; na papierze, przy multiply z kryciem 50 %, wychodzi z niej
 * pastel E = (255 + C) / 2 (patrz [highlighterLook]) — stąd C = 2E − 255. Kolejność: ciepłe → chłodne → neutralny.
 */
val HIGHLIGHTER_PALETTE: List<Int> = listOf(
    0xFFF5CF17, 0xFFFFA355, 0xFFFD614F, 0xFFF57BA5, 0xFFAB85F7,
    0xFF5F97FF, 0xFF85CFFD, 0xFF57F3C1, 0xFF71DF77, 0xFFC7AD85,
).map { it.toInt() }

/** Jak zakreślacz o danej barwie wygląda na białym papierze (multiply, krycie 0,5). */
fun highlighterLook(c: Int): Int {
    fun ch(sh: Int) = (255 + ((c shr sh and 0xFF) - 255) / 2)
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/**
 * Dla dowolnej barwy (suwaki, hex, pipeta) pilnuje, by efekt na papierze pozostał jasnym pastelem: barwy tak
 * ciemne, że po multiply miałyby jasność poniżej ~0,68 (zasłaniałyby tekst), są rozjaśniane ku bieli.
 */
fun softenForHighlighter(c: Int): Int {
    val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
    val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
    val min = 92f
    if (lum >= min) return c or (0xFF shl 24)
    val t = (min - lum) / (255f - lum)
    fun up(v: Int) = (v + (255 - v) * t + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (up(r) shl 16) or (up(g) shl 8) or up(b)
}

/** Wybór koloru: paleta + HSV + hex. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    color: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, recent: List<Int> = emptyList(), withAlpha: Boolean = false,
    onPipette: (() -> Unit)? = null, highlighter: Boolean = false,
    /** Zaznaczono elementy o różnych kolorach: podgląd jest tęczowy, a żaden kolor z palety nie jest „wybrany”. */
    mixed: Boolean = false,
) {
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    var hex by remember(color, mixed) { mutableStateOf(if (mixed) "" else String.format("%06X", color and 0xFFFFFF)) }
    val set: (Int) -> Unit = { c -> onChange(if (highlighter) softenForHighlighter(c) else c) }
    fun emit(h: Float, s: Float, v: Float) { set(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))) }
    // Zakreślacz: miniatury pokazują efekt na papierze, a nie zapisaną barwę.
    fun shown(c: Int) = Color(if (highlighter) highlighterLook(c) else c)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val pal = if (highlighter) HIGHLIGHTER_PALETTE else PALETTE
        val per = if (highlighter) 5 else 8
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in pal.chunked(per)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in row) Dot(shown(c), 30.dp, selected = !mixed && (c and 0xFFFFFF) == (color and 0xFFFFFF), onClick = { onChange(c) })
            }
        }
        if (recent.isNotEmpty()) {
            Text(T.t("Ostatnie", "Recent"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in recent.take(10)) Dot(shown(c), 24.dp, onClick = { onChange(c) })
            }
        }
        val hues = remember { Brush.horizontalGradient(List(7) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }) }
        GradientSlider(hsv[0] / 360f, hues, { emit(it * 360f, hsv[1].coerceAtLeast(0.05f), hsv[2].coerceAtLeast(0.05f)) })
        GradientSlider(hsv[1], Brush.horizontalGradient(listOf(Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2]))), Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2]))))), { emit(hsv[0], it, hsv[2]) })
        GradientSlider(hsv[2], Brush.horizontalGradient(listOf(Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))))), { emit(hsv[0], hsv[1], it) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dot(shown(color), 32.dp, mixed = mixed)
            MField(hex, { s ->
                val t = s.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                hex = t
                if (t.length == 6) set(0xFF000000.toInt() or t.toInt(16))
            }, Modifier.width(140.dp), leading = { Text("#", color = MaterialTheme.colorScheme.onSurfaceVariant) })
            if (onPipette != null) IconBtn(Icons.Rounded.Colorize, T.t("Pipeta — pobierz kolor ze strony", "Eyedropper — pick a color from the page"), onPipette, size = 36.dp, iconSize = 20.dp, shortcut = "I")
        }
    }
}
