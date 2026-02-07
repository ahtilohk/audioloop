package com.example.audioloop.ui.theme

import androidx.compose.ui.graphics.Color

// Zinc Colors (Neutral)
val Zinc950 = Color(0xFF09090B)
val Zinc900 = Color(0xFF18181B)
val Zinc800 = Color(0xFF27272A)
val Zinc700 = Color(0xFF3F3F46)
val Zinc600 = Color(0xFF52525B)
val Zinc500 = Color(0xFF71717A)
val Zinc400 = Color(0xFFA1A1AA)
val Zinc300 = Color(0xFFD4D4D8)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc100 = Color(0xFFF4F4F5)

// Red Colors
val Red400 = Color(0xFFF87171)
val Red500 = Color(0xFFEF4444)
val Red600 = Color(0xFFDC2626)

// === THEME PALETTES (Softer, eye-friendly colors) ===

// Cyan Theme (Default) - Soft teal
val Cyan200 = Color(0xFFB8E8ED)
val Cyan300 = Color(0xFF8CD4DD)
val Cyan400 = Color(0xFF5FBAC7)
val Cyan500 = Color(0xFF4A9DA8)
val Cyan600 = Color(0xFF3D8691)
val Cyan700 = Color(0xFF336F78)
val Cyan800 = Color(0xFF2A5860)
val Cyan900 = Color(0xFF224850)

// Sunset Theme (Warm muted orange)
val Sunset200 = Color(0xFFE8D4C4)
val Sunset300 = Color(0xFFDDBFA5)
val Sunset400 = Color(0xFFCDA585)
val Sunset500 = Color(0xFFB8906E)
val Sunset600 = Color(0xFFA37B5A)
val Sunset700 = Color(0xFF8C6A4E)
val Sunset800 = Color(0xFF735842)
val Sunset900 = Color(0xFF5C4736)

// Ocean Theme (Soft blue)
val Ocean200 = Color(0xFFBDD4E8)
val Ocean300 = Color(0xFF95BCD8)
val Ocean400 = Color(0xFF6CA2C5)
val Ocean500 = Color(0xFF5289AD)
val Ocean600 = Color(0xFF447494)
val Ocean700 = Color(0xFF3A6280)
val Ocean800 = Color(0xFF30516A)
val Ocean900 = Color(0xFF284356)

// Forest Theme (Soft green)
val Forest200 = Color(0xFFBFDDC8)
val Forest300 = Color(0xFF9ACBA8)
val Forest400 = Color(0xFF73B588)
val Forest500 = Color(0xFF5A9A6E)
val Forest600 = Color(0xFF4A845C)
val Forest700 = Color(0xFF3E6E4D)
val Forest800 = Color(0xFF345A40)
val Forest900 = Color(0xFF2A4935)

// Violet Theme (Soft purple)
val Violet200 = Color(0xFFD5D0E8)
val Violet300 = Color(0xFFBBB3D8)
val Violet400 = Color(0xFF9E93C5)
val Violet500 = Color(0xFF8578AD)
val Violet600 = Color(0xFF6F6394)
val Violet700 = Color(0xFF5C5280)
val Violet800 = Color(0xFF4B446A)
val Violet900 = Color(0xFF3D3856)

// Rose Theme (Soft pink)
val Rose200 = Color(0xFFE8D0D5)
val Rose300 = Color(0xFFD8B3BC)
val Rose400 = Color(0xFFC5939F)
val Rose500 = Color(0xFFAD7785)
val Rose600 = Color(0xFF95606E)
val Rose700 = Color(0xFF7D505C)
val Rose800 = Color(0xFF66424C)
val Rose900 = Color(0xFF52363E)

// Theme data class
data class AppColorPalette(
    val name: String,
    val primary200: Color,
    val primary300: Color,
    val primary400: Color,
    val primary500: Color,
    val primary600: Color,
    val primary700: Color,
    val primary800: Color,
    val primary900: Color
)

// Available themes
enum class AppTheme(val displayName: String, val palette: AppColorPalette) {
    CYAN("Cyan", AppColorPalette("Cyan", Cyan200, Cyan300, Cyan400, Cyan500, Cyan600, Cyan700, Cyan800, Cyan900)),
    SUNSET("Sunset", AppColorPalette("Sunset", Sunset200, Sunset300, Sunset400, Sunset500, Sunset600, Sunset700, Sunset800, Sunset900)),
    OCEAN("Ocean", AppColorPalette("Ocean", Ocean200, Ocean300, Ocean400, Ocean500, Ocean600, Ocean700, Ocean800, Ocean900)),
    FOREST("Forest", AppColorPalette("Forest", Forest200, Forest300, Forest400, Forest500, Forest600, Forest700, Forest800, Forest900)),
    VIOLET("Violet", AppColorPalette("Violet", Violet200, Violet300, Violet400, Violet500, Violet600, Violet700, Violet800, Violet900)),
    ROSE("Rose", AppColorPalette("Rose", Rose200, Rose300, Rose400, Rose500, Rose600, Rose700, Rose800, Rose900))
}

// Legacy theme colors (for compatibility)
val Background = Zinc950
val Surface = Zinc900
val Primary = Cyan500
val OnPrimary = Color.White
val OnBackground = Color.White
val OnSurface = Color.White
