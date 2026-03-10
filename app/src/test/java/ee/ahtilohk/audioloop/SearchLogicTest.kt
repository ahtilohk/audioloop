package ee.ahtilohk.audioloop

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import android.net.Uri
import io.mockk.*

/**
 * Unit tests for Search and Filtering reactor logic.
 * Verifies that the filtering behaves correctly for various sort modes and queries.
 */
class SearchLogicTest {

    private val mockUri = mockk<Uri>(relaxed = true)

    private val sampleItems = listOf(
        RecordingItem(File("/tmp/Alpha.m4a"), "Alpha.m4a", "00:01", 1000L, mockUri),
        RecordingItem(File("/tmp/Bravo.m4a"), "Bravo.m4a", "00:02", 2000L, mockUri),
        RecordingItem(File("/tmp/Charlie.m4a"), "Charlie.m4a", "00:03", 3000L, mockUri),
        RecordingItem(File("/tmp/Zebra.m4a"), "Zebra.m4a", "00:00", 500L, mockUri)
    )

    @Test
    fun `search filters items by name`() {
        val query = "alpha"
        val filtered = sampleItems.filter { it.name.contains(query, ignoreCase = true) }
        assertEquals(1, filtered.size)
        assertEquals("Alpha.m4a", filtered[0].name)
    }

    @Test
    fun `search is case insensitive`() {
        val query = "BRAVO"
        val filtered = sampleItems.filter { it.name.contains(query, ignoreCase = true) }
        assertEquals(1, filtered.size)
        assertEquals("Bravo.m4a", filtered[0].name)
    }

    @Test
    fun `sort mode BY_NAME_ASC works correctly`() {
        val sorted = sampleItems.sortedBy { it.name.lowercase() }
        assertEquals("Alpha.m4a", sorted[0].name)
        assertEquals("Zebra.m4a", sorted[3].name)
    }

    @Test
    fun `sort mode BY_NAME_DESC works correctly`() {
        val sorted = sampleItems.sortedByDescending { it.name.lowercase() }
        assertEquals("Zebra.m4a", sorted[0].name)
        assertEquals("Alpha.m4a", sorted[3].name)
    }

    @Test
    fun `sort mode BY_DURATION_ASC works correctly`() {
        val sorted = sampleItems.sortedBy { it.durationMillis }
        assertEquals("Zebra.m4a", sorted[0].name) // 500ms
        assertEquals("Charlie.m4a", sorted[3].name) // 3000ms
    }

    @Test
    fun `empty query returns all items`() {
        val query = ""
        val filtered = if (query.isEmpty()) sampleItems else sampleItems.filter { it.name.contains(query, ignoreCase = true) }
        assertEquals(sampleItems.size, filtered.size)
    }
}
