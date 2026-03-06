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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

// Creates a dark color scheme for a given theme palette
fun createDarkColorScheme(palette: AppColorPalette) = darkColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.primaryContainer,
    onPrimaryContainer = palette.onPrimaryContainer,

    secondary = palette.secondary,
    onSecondary = Color.White,
    secondaryContainer = palette.secondaryContainer,
    onSecondaryContainer = palette.onSecondaryContainer,

    tertiary = palette.tertiary,
    onTertiary = palette.onTertiary,
    tertiaryContainer = palette.tertiaryContainer,
    onTertiaryContainer = palette.onTertiaryContainer,

    error = Red500,
    onError = Color.White,
    errorContainer = Red800,
    onErrorContainer = Red400,

    background = Zinc950,
    onBackground = Color.White,
    surface = Zinc900,
    onSurface = Color.White,
    surfaceVariant = Zinc800,
    onSurfaceVariant = Zinc400,

    outline = Zinc700,
    outlineVariant = Zinc600,

    scrim = Color.Black,
    inverseSurface = Color.White,
    inverseOnSurface = Zinc950,
    inversePrimary = palette.primary700,
    surfaceTint = palette.primary
)

// Default color schemes
private val DarkColorScheme = createDarkColorScheme(AppTheme.SLATE.palette)

// Creates a light color scheme for a given theme palette
fun createLightColorScheme(palette: AppColorPalette) = lightColorScheme(
    primary = palette.primary600,
    onPrimary = Color.White,
    primaryContainer = palette.primary200,
    onPrimaryContainer = palette.primary900,

    secondary = palette.primary700,
    onSecondary = Color.White,
    secondaryContainer = palette.primary300,
    onSecondaryContainer = palette.primary800,

    tertiary = palette.primary500,
    onTertiary = Color.White,
    tertiaryContainer = palette.primary100,
    onTertiaryContainer = palette.primary900,

    error = Red600,
    onError = Color.White,
    errorContainer = Red400,
    onErrorContainer = Red900,

    background = Zinc100,
    onBackground = Zinc950,
    surface = Color.White,
    onSurface = Zinc950,
    surfaceVariant = Zinc200,
    onSurfaceVariant = Zinc600,

    outline = Zinc500,
    outlineVariant = Zinc300
)

@Composable
fun AudioLoopTheme(
    darkTheme: Boolean = true,
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

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalRadius provides Radius()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
