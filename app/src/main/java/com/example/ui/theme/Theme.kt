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
    val borderHairline: Color = TitaniumHairline,
    val borderStrong: Color = TitaniumEdge,
    val amber: Color = ForgeAmber,
    val amberContainer: Color = ForgeAmberContainer,
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

private val ForgeDarkColorScheme = darkColorScheme(
    primary = ForgeAmber,
    onPrimary = OnForgeAmber,
    primaryContainer = ForgeAmberContainer,
    onPrimaryContainer = ForgeAmberBright,
    secondary = TacticalBlue,
    onSecondary = OnTacticalBlue,
    secondaryContainer = TacticalBlueContainer,
    onSecondaryContainer = TacticalBlueBright,
    tertiary = TacticalSage,
    onTertiary = SteelVoid,
    tertiaryContainer = TacticalSageContainer,
    onTertiaryContainer = DiffAddedInk,
    background = SteelVoid,
    onBackground = AluminumInk,
    surface = SteelPlate,
    onSurface = AluminumInk,
    surfaceVariant = SteelMachined,
    onSurfaceVariant = SlateInk,
    surfaceContainerLowest = SteelVoid,
    surfaceContainerLow = SteelPlate,
    surfaceContainer = SteelMachined,
    surfaceContainerHigh = SteelElevated,
    surfaceContainerHighest = SteelActiveContainer,
    outline = TitaniumEdge,
    outlineVariant = TitaniumHairline,
    error = IndustrialCrimson,
    onError = OnIndustrialCrimson,
    errorContainer = IndustrialCrimsonContainer,
    onErrorContainer = DiffRemovedInk
)

// Sharp utilitarian geometry: chamfered/machined edges rather than soft rounded bubbles.
private val ForgeShapes = Shapes(
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
            colorScheme = ForgeDarkColorScheme,
            typography = PatchForgeTypography,
            shapes = ForgeShapes,
            content = content
        )
    }
}

object ForgeTheme {
    val colors: ForgeExtendedColors
        @Composable
        get() = LocalForgeColors.current
}
