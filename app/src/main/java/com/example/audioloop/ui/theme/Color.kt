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

// Cyan Theme (Default) - Muted Teal/Turquoise
val Cyan100 = Color(0xFFD9F2F5)
val Cyan200 = Color(0xFFB3E3E9)
val Cyan300 = Color(0xFF8AD1DA)
val Cyan400 = Color(0xFF6ABEC9)
val Cyan500 = Color(0xFF4FA8B5)  // Primary (softer)
val Cyan600 = Color(0xFF3D8A96)
val Cyan700 = Color(0xFF2E6A73)
val Cyan800 = Color(0xFF204A50)
val Cyan900 = Color(0xFF132C30)

// Sunset Theme (Warm Peach/Coral)
val Sunset100 = Color(0xFFFFF0E8)
val Sunset200 = Color(0xFFFFDCC8)
val Sunset300 = Color(0xFFF5C4A8)
val Sunset400 = Color(0xFFE8A98A)
val Sunset500 = Color(0xFFD9906E)  // Primary (softer)
val Sunset600 = Color(0xFFBF7758)
val Sunset700 = Color(0xFF9A5E44)
val Sunset800 = Color(0xFF744632)
val Sunset900 = Color(0xFF4E2F21)

// Ocean Theme (Soft Blue)
val Ocean100 = Color(0xFFE0ECFA)
val Ocean200 = Color(0xFFC0D8F2)
val Ocean300 = Color(0xFF9AC0E6)
val Ocean400 = Color(0xFF78A8D6)
val Ocean500 = Color(0xFF5B8FC2)  // Primary (softer)
val Ocean600 = Color(0xFF4774A6)
val Ocean700 = Color(0xFF365A85)
val Ocean800 = Color(0xFF274060)
val Ocean900 = Color(0xFF1A2B3F)

// Forest Theme (Sage Green)
val Forest100 = Color(0xFFDEF0E4)
val Forest200 = Color(0xFFBBDFC8)
val Forest300 = Color(0xFF96CCAB)
val Forest400 = Color(0xFF74B890)
val Forest500 = Color(0xFF58A378)  // Primary (softer)
val Forest600 = Color(0xFF468863)
val Forest700 = Color(0xFF356A4D)
val Forest800 = Color(0xFF264C37)
val Forest900 = Color(0xFF182E22)

// Violet Theme (Soft Lavender)
val Violet100 = Color(0xFFEDE6F8)
val Violet200 = Color(0xFFDAC9F0)
val Violet300 = Color(0xFFC3ABE4)
val Violet400 = Color(0xFFAD90D6)
val Violet500 = Color(0xFF9578C4)  // Primary (softer)
val Violet600 = Color(0xFF7C62A8)
val Violet700 = Color(0xFF614B85)
val Violet800 = Color(0xFF473662)
val Violet900 = Color(0xFF2E2340)

// Rose Theme (Soft Pink/Blush)
val Rose100 = Color(0xFFFCE4EC)
val Rose200 = Color(0xFFF5C5D4)
val Rose300 = Color(0xFFECA5BC)
val Rose400 = Color(0xFFE088A6)
val Rose500 = Color(0xFFD06E90)  // Primary (softer)
val Rose600 = Color(0xFFB35878)
val Rose700 = Color(0xFF904460)
val Rose800 = Color(0xFF6D3148)
val Rose900 = Color(0xFF4A2030)

// Slate Theme (Professional Blue-Grey)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)  // Primary
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)

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
    SLATE("Professional", AppColorPalette("Slate", Slate100, Slate200, Slate300, Slate400, Slate500, Slate600, Slate700, Slate800, Slate900)),
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
