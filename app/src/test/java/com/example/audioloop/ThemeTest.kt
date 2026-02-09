package com.example.audioloop

import com.example.audioloop.ui.theme.*
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for theme system
 */
class ThemeTest {

    @Test
    fun `all themes have unique names`() {
        val names = AppTheme.entries.map { it.displayName }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `all themes have 6 entries`() {
        assertEquals(6, AppTheme.entries.size)
    }

    @Test
    fun `cyan theme is default and first`() {
        assertEquals(AppTheme.CYAN, AppTheme.entries.first())
        assertEquals("Cyan", AppTheme.CYAN.displayName)
    }

    @Test
    fun `all theme palettes have valid color values`() {
        AppTheme.entries.forEach { theme ->
            val palette = theme.palette
            assertNotNull(palette.primary200)
            assertNotNull(palette.primary300)
            assertNotNull(palette.primary400)
            assertNotNull(palette.primary500)
            assertNotNull(palette.primary600)
            assertNotNull(palette.primary700)
            assertNotNull(palette.primary800)
            assertNotNull(palette.primary900)
        }
    }

    @Test
    fun `theme palette names match theme display names`() {
        AppTheme.entries.forEach { theme ->
            assertEquals(theme.displayName, theme.palette.name)
        }
    }

    @Test
    fun `sunset theme exists with correct name`() {
        val sunset = AppTheme.SUNSET
        assertEquals("Sunset", sunset.displayName)
        assertNotNull(sunset.palette)
    }

    @Test
    fun `ocean theme exists with correct name`() {
        val ocean = AppTheme.OCEAN
        assertEquals("Ocean", ocean.displayName)
        assertNotNull(ocean.palette)
    }

    @Test
    fun `forest theme exists with correct name`() {
        val forest = AppTheme.FOREST
        assertEquals("Forest", forest.displayName)
        assertNotNull(forest.palette)
    }

    @Test
    fun `violet theme exists with correct name`() {
        val violet = AppTheme.VIOLET
        assertEquals("Violet", violet.displayName)
        assertNotNull(violet.palette)
    }

    @Test
    fun `rose theme exists with correct name`() {
        val rose = AppTheme.ROSE
        assertEquals("Rose", rose.displayName)
        assertNotNull(rose.palette)
    }

    @Test
    fun `zinc colors are defined for neutral UI`() {
        assertNotNull(Zinc950)
        assertNotNull(Zinc900)
        assertNotNull(Zinc800)
        assertNotNull(Zinc700)
        assertNotNull(Zinc600)
        assertNotNull(Zinc500)
        assertNotNull(Zinc400)
        assertNotNull(Zinc300)
        assertNotNull(Zinc200)
        assertNotNull(Zinc100)
    }

    @Test
    fun `red colors are defined for alerts`() {
        assertNotNull(Red400)
        assertNotNull(Red500)
        assertNotNull(Red600)
    }

    @Test
    fun `legacy colors are defined for compatibility`() {
        assertNotNull(Background)
        assertNotNull(Surface)
        assertNotNull(Primary)
        assertNotNull(OnPrimary)
        assertNotNull(OnBackground)
        assertNotNull(OnSurface)
    }

    @Test
    fun `theme can be looked up by name`() {
        val theme = AppTheme.valueOf("CYAN")
        assertEquals(AppTheme.CYAN, theme)
    }

    @Test
    fun `all themes can be iterated`() {
        var count = 0
        AppTheme.entries.forEach { count++ }
        assertEquals(6, count)
    }
}
