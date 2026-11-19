package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = Color(0xFF00205A),
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = CyanSecondaryDark,
    onSecondary = Color(0xFF00363F),
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = VioletTertiaryDark,
    background = SlateBackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SlateSurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = CoralError
)

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainerLight,
    onPrimaryContainer = CobaltOnContainerLight,
    secondary = CyanSecondary,
    onSecondary = Color.White,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = CyanOnContainerLight,
    tertiary = VioletTertiary,
    tertiaryContainer = VioletContainerLight,
    background = SlateBackgroundLight,
    onBackground = OnSurfaceLight,
    surface = PureSurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = CoralError,
    errorContainer = CoralErrorContainer
)

val ShareMeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ShareMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShareMeTypography,
        shapes = ShareMeShapes,
        content = content
    )
}
