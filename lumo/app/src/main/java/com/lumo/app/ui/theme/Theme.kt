package com.lumo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val NeonGreen = Color(0xFF00FF87)
val NeonGreenDim = Color(0xFF00CC6A)
val NeonGreenGlow = Color(0x3300FF87)
val CrimsonRed = Color(0xFFFF2D55)
val CrimsonRedDim = Color(0xFFCC2244)
val DeepBlack = Color(0xFF080A0E)
val SurfaceBlack = Color(0xFF0D1018)
val CardBlack = Color(0xFF141822)
val ElevatedBlack = Color(0xFF1C2230)
val DividerGray = Color(0xFF252D3E)
val TextPrimary = Color(0xFFF0F2F8)
val TextSecondary = Color(0xFF8892A8)
val TextTertiary = Color(0xFF4A5270)
val GlassWhite = Color(0x0DFFFFFF)
val GlassWhiteBorder = Color(0x1AFFFFFF)
val AccentAmber = Color(0xFFFFB830)
val AccentPurple = Color(0xFF8B5CF6)

private val LumoDarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = DeepBlack,
    primaryContainer = Color(0xFF003D1F),
    onPrimaryContainer = NeonGreen,
    secondary = CrimsonRed,
    onSecondary = TextPrimary,
    secondaryContainer = Color(0xFF5C0018),
    onSecondaryContainer = CrimsonRed,
    tertiary = AccentAmber,
    onTertiary = DeepBlack,
    background = DeepBlack,
    onBackground = TextPrimary,
    surface = SurfaceBlack,
    onSurface = TextPrimary,
    surfaceVariant = CardBlack,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = ElevatedBlack,
    surfaceContainerHigh = ElevatedBlack,
    outline = DividerGray,
    outlineVariant = Color(0xFF1E2638),
    error = CrimsonRed,
    onError = TextPrimary,
    inversePrimary = NeonGreenDim,
    scrim = Color(0xCC080A0E),
)

data class LumoExtendedColors(
    val neonGreen: Color = NeonGreen,
    val neonGreenDim: Color = NeonGreenDim,
    val neonGreenGlow: Color = NeonGreenGlow,
    val crimsonRed: Color = CrimsonRed,
    val crimsonRedDim: Color = CrimsonRedDim,
    val deepBlack: Color = DeepBlack,
    val surfaceBlack: Color = SurfaceBlack,
    val cardBlack: Color = CardBlack,
    val elevatedBlack: Color = ElevatedBlack,
    val dividerGray: Color = DividerGray,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textTertiary: Color = TextTertiary,
    val glassWhite: Color = GlassWhite,
    val glassWhiteBorder: Color = GlassWhiteBorder,
    val accentAmber: Color = AccentAmber,
    val accentPurple: Color = AccentPurple,
)

val LocalLumoColors = staticCompositionLocalOf { LumoExtendedColors() }

@Composable
fun LumoTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLumoColors provides LumoExtendedColors()) {
        MaterialTheme(
            colorScheme = LumoDarkColorScheme,
            typography = LumoTypography,
            shapes = LumoShapes,
            content = content
        )
    }
}

val lumoColors: LumoExtendedColors
    @Composable get() = LocalLumoColors.current
