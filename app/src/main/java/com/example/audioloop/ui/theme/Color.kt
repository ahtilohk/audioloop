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

// === THEME PALETTES ===

// Cyan Theme (Default)
val Cyan200 = Color(0xFFA5F3FC)
val Cyan300 = Color(0xFF67E8F9)
val Cyan400 = Color(0xFF22D3EE)
val Cyan500 = Color(0xFF06B6D4)
val Cyan600 = Color(0xFF0891B2)
val Cyan700 = Color(0xFF0E7490)
val Cyan800 = Color(0xFF155E75)
val Cyan900 = Color(0xFF164E63)

// Sunset Theme (Warm Orange/Pink)
val Sunset200 = Color(0xFFFFD4A8)
val Sunset300 = Color(0xFFFFB870)
val Sunset400 = Color(0xFFFF9A45)
val Sunset500 = Color(0xFFFF7B1C)
val Sunset600 = Color(0xFFE86A10)
val Sunset700 = Color(0xFFCC5A0E)
val Sunset800 = Color(0xFFAA4B0C)
val Sunset900 = Color(0xFF8A3D0A)

// Ocean Theme (Blue/Teal)
val Ocean200 = Color(0xFFBAE6FD)
val Ocean300 = Color(0xFF7DD3FC)
val Ocean400 = Color(0xFF38BDF8)
val Ocean500 = Color(0xFF0EA5E9)
val Ocean600 = Color(0xFF0284C7)
val Ocean700 = Color(0xFF0369A1)
val Ocean800 = Color(0xFF075985)
val Ocean900 = Color(0xFF0C4A6E)

// Forest Theme (Green)
val Forest200 = Color(0xFFBBF7D0)
val Forest300 = Color(0xFF86EFAC)
val Forest400 = Color(0xFF4ADE80)
val Forest500 = Color(0xFF22C55E)
val Forest600 = Color(0xFF16A34A)
val Forest700 = Color(0xFF15803D)
val Forest800 = Color(0xFF166534)
val Forest900 = Color(0xFF14532D)

// Violet Theme (Purple)
val Violet200 = Color(0xFFDDD6FE)
val Violet300 = Color(0xFFC4B5FD)
val Violet400 = Color(0xFFA78BFA)
val Violet500 = Color(0xFF8B5CF6)
val Violet600 = Color(0xFF7C3AED)
val Violet700 = Color(0xFF6D28D9)
val Violet800 = Color(0xFF5B21B6)
val Violet900 = Color(0xFF4C1D95)

// Rose Theme (Pink)
val Rose200 = Color(0xFFFECDD3)
val Rose300 = Color(0xFFFDA4AF)
val Rose400 = Color(0xFFFB7185)
val Rose500 = Color(0xFFF43F5E)
val Rose600 = Color(0xFFE11D48)
val Rose700 = Color(0xFFBE123C)
val Rose800 = Color(0xFF9F1239)
val Rose900 = Color(0xFF881337)

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
