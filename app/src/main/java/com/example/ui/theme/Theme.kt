package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class ForgeExtendedColors(
    val titaniumAccent: Color = BrushedTitaniumAccent,
    val borderHairline: Color = BrushedTitaniumHairline,
    val borderStrong: Color = BrushedTitaniumBorder,
    val recessedWell: Color = ForgedSteelWell,
    val amber: Color = TacticalAmber,
    val amberContainer: Color = TacticalAmberContainer,
    val tacticalBlue: Color = TacticalBlue,
    val tacticalBlueContainer: Color = TacticalBlueContainer,
    val criticalRed: Color = IndustrialCrimson,
    val criticalRedContainer: Color = IndustrialCrimsonContainer,
    val verifiedGreen: Color = TacticalSage,
    val verifiedGreenContainer: Color = TacticalSageContainer,
    val diffAddedBg: Color = DiffAddedBg,
    val diffAddedInk: Color = DiffAddedInk,
    val diffRemovedBg: Color = DiffRemovedBg,
    val diffRemovedInk: Color = DiffRemovedInk,
    val diffHunkBg: Color = DiffHunkBg,
    val diffHunkInk: Color = DiffHunkInk,
    val telemetryMuted: Color = TelemetryDim
)

val LocalForgeColors = staticCompositionLocalOf { ForgeExtendedColors() }

private val PatchForgeDarkColorScheme = darkColorScheme(
    primary = TacticalAmber,
    onPrimary = OnTacticalAmber,
    primaryContainer = TacticalAmberContainer,
    onPrimaryContainer = TacticalAmberBright,
    secondary = TacticalBlue,
    onSecondary = OnTacticalBlue,
    secondaryContainer = TacticalBlueContainer,
    onSecondaryContainer = TacticalBlueBright,
    tertiary = BrushedTitaniumAccent,
    onTertiary = ForgedSteelWell,
    tertiaryContainer = TacticalSageContainer,
    onTertiaryContainer = DiffAddedInk,
    background = ForgedSteelBackground,
    onBackground = AluminumInk,
    surface = ForgedSteelPlate,
    onSurface = AluminumInk,
    surfaceVariant = ForgedSteelMachined,
    onSurfaceVariant = SlateInk,
    surfaceContainerLowest = ForgedSteelWell,
    surfaceContainerLow = ForgedSteelBackground,
    surfaceContainer = ForgedSteelPlate,
    surfaceContainerHigh = ForgedSteelElevated,
    surfaceContainerHighest = ForgedSteelContainerHighest,
    outline = BrushedTitaniumAccent,
    outlineVariant = BrushedTitaniumHairline,
    error = IndustrialCrimson,
    onError = OnIndustrialCrimson,
    errorContainer = IndustrialCrimsonContainer,
    onErrorContainer = DiffRemovedInk
)

// Sharp utilitarian geometry: chamfered/machined industrial edges.
private val PatchForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

@Composable
fun PatchForgeTheme(
    content: @Composable () -> Unit
) {
    val extendedColors = ForgeExtendedColors()
    CompositionLocalProvider(LocalForgeColors provides extendedColors) {
        MaterialTheme(
            colorScheme = PatchForgeDarkColorScheme,
            typography = PatchForgeTypography,
            shapes = PatchForgeShapes,
            content = content
        )
    }
}

object ForgeTheme {
    val colors: ForgeExtendedColors
        @Composable
        get() = LocalForgeColors.current
}
