package com.mdev.mnotes.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.mdev.mnotes.data.Settings
import com.mdev.mnotes.editor.T

class AccentDef(val pl: String, val en: String, val color: Color) {
    val name: String get() = T.t(pl, en)
}

/** Akcenty w duchu motywów Mint-Y (domyślnie zielony „Mint”). */
object Accents {
    val list = listOf(
        AccentDef("Mięta", "Mint", Color(0xFF87CF3E)),
        AccentDef("Aqua", "Aqua", Color(0xFF1FB5C9)),
        AccentDef("Niebieski", "Blue", Color(0xFF5294E2)),
        AccentDef("Pomarańcz", "Orange", Color(0xFFF08A30)),
        AccentDef("Róż", "Pink", Color(0xFFE8638B)),
        AccentDef("Fiolet", "Purple", Color(0xFF9B6FD9)),
        AccentDef("Czerwień", "Red", Color(0xFFE5484D)),
        AccentDef("Piasek", "Sand", Color(0xFFC9A66B)),
        AccentDef("Grafit", "Grey", Color(0xFF7D8590)),
    )
}

/** Dodatkowe kolory poza Material: panel boczny, nagłówek, tło obszaru roboczego itd. */
class MintColors(
    val dark: Boolean,
    val amoled: Boolean,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val accentSoft: Color,
    val sidebar: Color,
    val header: Color,
    val canvasBg: Color,
    val line: Color,
    val hover: Color,
    val chrome: Color,
)

val LocalMint = staticCompositionLocalOf<MintColors> { error("MNotesTheme missing") }

private fun mix(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
)

@Composable
fun MNotesTheme(settings: Settings, content: @Composable () -> Unit) {
    val sys = isSystemInDarkTheme()
    val dark = when (settings.theme) { 1 -> false; 2, 3 -> true; else -> sys }
    val amoled = settings.theme == 3 && dark
    val accent = Accents.list[settings.accent.coerceIn(0, Accents.list.size - 1)].color
    val onAccent = if (accent.luminance() > 0.38f) Color(0xFF10180A) else Color.White
    val accentText = if (dark) mix(accent, Color.White, 0.18f) else mix(accent, Color.Black, if (accent.luminance() > 0.38f) 0.42f else 0.12f)

    val bg: Color; val surface: Color; val variant: Color; val outline: Color; val onSurface: Color; val onVar: Color
    val sidebar: Color; val header: Color; val canvas: Color; val hover: Color; val chrome: Color
    if (!dark) {
        bg = Color(0xFFF4F5F6); surface = Color.White; variant = Color(0xFFE9EBEE); outline = Color(0xFFD0D3D8)
        onSurface = Color(0xFF24272B); onVar = Color(0xFF5C626B)
        sidebar = Color(0xFFEDEEF0); header = Color(0xFFF9F9FA); canvas = Color(0xFFDDE0E4); hover = Color(0x14000000); chrome = Color(0xFFFCFCFD)
    } else if (!amoled) {
        bg = Color(0xFF2A2C2F); surface = Color(0xFF34363A); variant = Color(0xFF3E4045); outline = Color(0xFF55585E)
        onSurface = Color(0xFFE8E9EB); onVar = Color(0xFFA9AEB5)
        sidebar = Color(0xFF232527); header = Color(0xFF303236); canvas = Color(0xFF1C1D1F); hover = Color(0x1AFFFFFF); chrome = Color(0xFF383A3E)
    } else {
        bg = Color.Black; surface = Color(0xFF0C0C0D); variant = Color(0xFF1A1B1D); outline = Color(0xFF38393C)
        onSurface = Color(0xFFEDEEEF); onVar = Color(0xFF9EA3AA)
        sidebar = Color(0xFF050505); header = Color(0xFF0A0A0B); canvas = Color(0xFF000000); hover = Color(0x1AFFFFFF); chrome = Color(0xFF141416)
    }
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = mix(surface, accent, 0.28f), onPrimaryContainer = onSurface,
        secondary = accentText, onSecondary = onAccent, secondaryContainer = mix(surface, accent, 0.22f), onSecondaryContainer = onSurface,
        background = bg, onBackground = onSurface, surface = surface, onSurface = onSurface, surfaceVariant = variant, onSurfaceVariant = onVar,
        surfaceContainer = surface, surfaceContainerHigh = variant, surfaceContainerHighest = variant, surfaceContainerLow = bg, surfaceContainerLowest = bg,
        outline = outline, outlineVariant = outline, error = Color(0xFFFF6B6B), surfaceTint = accent,
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = mix(Color.White, accent, 0.28f), onPrimaryContainer = onSurface,
        secondary = accentText, onSecondary = onAccent, secondaryContainer = mix(Color.White, accent, 0.24f), onSecondaryContainer = onSurface,
        background = bg, onBackground = onSurface, surface = surface, onSurface = onSurface, surfaceVariant = variant, onSurfaceVariant = onVar,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = variant, surfaceContainerLow = bg, surfaceContainerLowest = Color.White,
        outline = outline, outlineVariant = outline, error = Color(0xFFD93636), surfaceTint = accent,
    )
    val mc = MintColors(dark, amoled, accent, onAccent, accentText, mix(surface, accent, if (dark) 0.2f else 0.18f), sidebar, header, canvas, outline, hover, chrome)

    val base = LocalDensity.current
    val dens = if (settings.compact) Density(base.density * 0.9f, base.fontScale * 0.96f) else base
    val tp = Typography().let { t ->
        fun TextStyle.tight() = copy(letterSpacing = 0.sp)
        t.copy(
            titleLarge = t.titleLarge.tight().copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            titleMedium = t.titleMedium.tight().copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            titleSmall = t.titleSmall.tight().copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            bodyLarge = t.bodyLarge.tight().copy(fontSize = 15.sp), bodyMedium = t.bodyMedium.tight().copy(fontSize = 13.5.sp), bodySmall = t.bodySmall.tight().copy(fontSize = 12.sp),
            labelLarge = t.labelLarge.tight().copy(fontSize = 13.5.sp), labelMedium = t.labelMedium.tight().copy(fontSize = 12.sp), labelSmall = t.labelSmall.tight().copy(fontSize = 11.sp),
        )
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(4), small = RoundedCornerShape(6), medium = RoundedCornerShape(8), large = RoundedCornerShape(12), extraLarge = RoundedCornerShape(16),
    )
    CompositionLocalProvider(LocalMint provides mc, LocalDensity provides dens) {
        MaterialTheme(colorScheme = scheme, typography = tp, shapes = shapes) {
            // Bez Surface domyślny kolor treści to czerń — ustawiamy go globalnie, żeby tekst był czytelny w obu motywach.
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides onSurface, content = content)
        }
    }
}

val mint: MintColors @Composable get() = LocalMint.current
