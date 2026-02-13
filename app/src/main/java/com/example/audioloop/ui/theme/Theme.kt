package com.example.audioloop.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

// Creates a dark color scheme for a given theme palette
fun createDarkColorScheme(palette: AppColorPalette) = darkColorScheme(
    // Primary colors
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.primaryContainer,
    onPrimaryContainer = palette.onPrimaryContainer,

    // Secondary colors
    secondary = palette.secondary,
    onSecondary = Color.White,
    secondaryContainer = palette.secondaryContainer,
    onSecondaryContainer = palette.onSecondaryContainer,

    // Tertiary colors
    tertiary = palette.tertiary,
    onTertiary = palette.onTertiary,
    tertiaryContainer = palette.tertiaryContainer,
    onTertiaryContainer = palette.onTertiaryContainer,

    // Error colors
    error = Red500,
    onError = Color.White,
    errorContainer = Red800,
    onErrorContainer = Red400,

    // Surface colors
    background = Zinc950,
    onBackground = Zinc200,
    surface = Zinc900,
    onSurface = Zinc200,
    surfaceVariant = Zinc800,
    onSurfaceVariant = Zinc400,

    // Outline colors
    outline = Zinc600,
    outlineVariant = Zinc700,

    // Other
    scrim = Color.Black,
    inverseSurface = Zinc200,
    inverseOnSurface = Zinc900,
    inversePrimary = palette.primary700,
    surfaceTint = palette.primary
)

// Creates a light color scheme for a given theme palette
fun createLightColorScheme(palette: AppColorPalette) = lightColorScheme(
    // Primary colors
    primary = palette.primary600,
    onPrimary = Color.White,
    primaryContainer = palette.primary200,
    onPrimaryContainer = palette.primary900,

    // Secondary colors
    secondary = palette.primary700,
    onSecondary = Color.White,
    secondaryContainer = palette.primary300,
    onSecondaryContainer = palette.primary800,

    // Tertiary colors
    tertiary = palette.primary500,
    onTertiary = Color.White,
    tertiaryContainer = palette.primary100,
    onTertiaryContainer = palette.primary900,

    // Error colors
    error = Red600,
    onError = Color.White,
    errorContainer = Red400,
    onErrorContainer = Red900,

    // Surface colors
    background = Zinc100,
    onBackground = Zinc900,
    surface = Color.White,
    onSurface = Zinc900,
    surfaceVariant = Zinc200,
    onSurfaceVariant = Zinc700,

    // Outline colors
    outline = Zinc500,
    outlineVariant = Zinc300
)

@Composable
fun AudioLoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    appTheme: AppTheme = AppTheme.SLATE,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> createDarkColorScheme(appTheme.palette)
        else -> createLightColorScheme(appTheme.palette)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}