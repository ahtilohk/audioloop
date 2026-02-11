package com.example.audioloop.ui.theme

import androidx.compose.ui.graphics.Color

// === NEUTRAL PALETTE (Updated for MD3) ===
// Background & Surface colors
val Zinc950 = Color(0xFF09090B)  // Background dark
val Zinc900 = Color(0xFF18181B)  // Surface dark
val Zinc800 = Color(0xFF27272A)  // Surface variant
val Zinc700 = Color(0xFF3F3F46)  // Outline
val Zinc600 = Color(0xFF52525B)  // Outline variant
val Zinc500 = Color(0xFF71717A)  // On surface variant
val Zinc400 = Color(0xFFA1A1AA)  // On surface medium
val Zinc300 = Color(0xFFD4D4D8)  // On surface high
val Zinc200 = Color(0xFFE4E4E7)  // On surface
val Zinc100 = Color(0xFFF4F4F5)  // On surface bright

// Error colors (MD3 system)
val Red400 = Color(0xFFF87171)   // Error light
val Red500 = Color(0xFFEF4444)   // Error
val Red600 = Color(0xFFDC2626)   // Error dark
val Red800 = Color(0xFF991B1B)   // Error container
val Red900 = Color(0xFF7F1D1D)   // On error container

// === MD3 THEME PALETTES ===
// Each theme now follows Material Design 3 tonal palette structure
// with enhanced vibrancy and modern feel

// Cyan Theme (Default) - Modern Teal/Turquoise
val Cyan100 = Color(0xFFCCF4FA)
val Cyan200 = Color(0xFF99E9F5)
val Cyan300 = Color(0xFF66DBED)
val Cyan400 = Color(0xFF3DCDE0)
val Cyan500 = Color(0xFF22B8CF)  // Primary
val Cyan600 = Color(0xFF1992A6)
val Cyan700 = Color(0xFF146B7D)
val Cyan800 = Color(0xFF0E4854)
val Cyan900 = Color(0xFF09262B)

// Sunset Theme (Vibrant Orange/Coral)
val Sunset100 = Color(0xFFFFE4D6)
val Sunset200 = Color(0xFFFFCAB0)
val Sunset300 = Color(0xFFFFAD87)
val Sunset400 = Color(0xFFFF9363)
val Sunset500 = Color(0xFFFF7A45)  // Primary
val Sunset600 = Color(0xFFE66529)
val Sunset700 = Color(0xFFB84F1E)
val Sunset800 = Color(0xFF8A3B16)
val Sunset900 = Color(0xFF5C270F)

// Ocean Theme (Deep Blue)
val Ocean100 = Color(0xFFD4E7FF)
val Ocean200 = Color(0xFFA9CFFF)
val Ocean300 = Color(0xFF7DB5FF)
val Ocean400 = Color(0xFF5A9EFF)
val Ocean500 = Color(0xFF3B87F5)  // Primary
val Ocean600 = Color(0xFF2A6DD4)
val Ocean700 = Color(0xFF1F53A3)
val Ocean800 = Color(0xFF163C72)
val Ocean900 = Color(0xFF0E2541)

// Forest Theme (Fresh Green)
val Forest100 = Color(0xFFD1F4DD)
val Forest200 = Color(0xFFA3E9BB)
val Forest300 = Color(0xFF75DD9A)
val Forest400 = Color(0xFF4DD17E)
val Forest500 = Color(0xFF2DBE66)  // Primary
val Forest600 = Color(0xFF239F53)
val Forest700 = Color(0xFF1A7A40)
val Forest800 = Color(0xFF12552D)
val Forest900 = Color(0xFF0A301A)

// Violet Theme (Rich Purple)
val Violet100 = Color(0xFFE8DEFF)
val Violet200 = Color(0xFFD1BDFF)
val Violet300 = Color(0xFFB99BFF)
val Violet400 = Color(0xFFA47DFF)
val Violet500 = Color(0xFF9063F5)  // Primary
val Violet600 = Color(0xFF784DD4)
val Violet700 = Color(0xFF5F3AA3)
val Violet800 = Color(0xFF462972)
val Violet900 = Color(0xFF2D1A41)

// Rose Theme (Modern Pink/Magenta)
val Rose100 = Color(0xFFFFD9E5)
val Rose200 = Color(0xFFFFB3CC)
val Rose300 = Color(0xFFFF8DB3)
val Rose400 = Color(0xFFFF6B9D)
val Rose500 = Color(0xFFFF4D87)  // Primary
val Rose600 = Color(0xFFE6376F)
val Rose700 = Color(0xFFB82857)
val Rose800 = Color(0xFF8A1C3F)
val Rose900 = Color(0xFF5C1127)

// MD3 Theme data class with full tonal palette
data class AppColorPalette(
    val name: String,
    val primary100: Color,
    val primary200: Color,
    val primary300: Color,
    val primary400: Color,
    val primary500: Color,    // Main primary color
    val primary600: Color,
    val primary700: Color,
    val primary800: Color,
    val primary900: Color
) {
    // MD3 semantic colors derived from tonal palette
    val primary = primary500
    val primaryContainer = primary800
    val onPrimary = Color.White
    val onPrimaryContainer = primary100

    val secondary = primary600
    val secondaryContainer = primary900
    val onSecondary = Color.White
    val onSecondaryContainer = primary200

    val tertiary = primary400
    val tertiaryContainer = primary700
    val onTertiary = Zinc950
    val onTertiaryContainer = primary100
}

// Available themes with full MD3 tonal palettes
enum class AppTheme(val displayName: String, val palette: AppColorPalette) {
    CYAN("Cyan", AppColorPalette("Cyan", Cyan100, Cyan200, Cyan300, Cyan400, Cyan500, Cyan600, Cyan700, Cyan800, Cyan900)),
    SUNSET("Sunset", AppColorPalette("Sunset", Sunset100, Sunset200, Sunset300, Sunset400, Sunset500, Sunset600, Sunset700, Sunset800, Sunset900)),
    OCEAN("Ocean", AppColorPalette("Ocean", Ocean100, Ocean200, Ocean300, Ocean400, Ocean500, Ocean600, Ocean700, Ocean800, Ocean900)),
    FOREST("Forest", AppColorPalette("Forest", Forest100, Forest200, Forest300, Forest400, Forest500, Forest600, Forest700, Forest800, Forest900)),
    VIOLET("Violet", AppColorPalette("Violet", Violet100, Violet200, Violet300, Violet400, Violet500, Violet600, Violet700, Violet800, Violet900)),
    ROSE("Rose", AppColorPalette("Rose", Rose100, Rose200, Rose300, Rose400, Rose500, Rose600, Rose700, Rose800, Rose900))
}

// Legacy theme colors (for compatibility)
val Background = Zinc950
val Surface = Zinc900
val Primary = Cyan500
val OnPrimary = Color.White
val OnBackground = Color.White
val OnSurface = Color.White
