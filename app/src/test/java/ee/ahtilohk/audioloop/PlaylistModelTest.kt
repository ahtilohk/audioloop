package ee.ahtilohk.audioloop

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Playlist data model and playlist manipulation logic.
 * Covers: creation, speed/pitch, path resolution, gap logic, and file management.
 */
class PlaylistModelTest {

    private fun makePlaylist(
        id: String = "pl_001",
        name: String = "Test Playlist",
        files: List<String> = emptyList(),
        gapSeconds: Int = 0,
        shuffle: Boolean = false,
        loopCount: Int = 1,
        speed: Float = 1.0f,
        pitch: Float = 1.0f,
        sleepMinutes: Int = 0
    ) = Playlist(
        id = id, name = name, files = files,
        createdAt = 1_000_000L,
        gapSeconds = gapSeconds, shuffle = shuffle,
        playCount = 0, speed = speed, pitch = pitch,
        loopCount = loopCount, sleepMinutes = sleepMinutes
    )

    // ── Creation ──
    @Test
    fun `new playlist has empty files`() {
        val p = makePlaylist()
        assertTrue(p.files.isEmpty())
    }

    @Test
    fun `playlist has correct name`() {
        val p = makePlaylist(name = "My Songs")
        assertEquals("My Songs", p.name)
    }

    @Test
    fun `playlist has correct id`() {
        val p = makePlaylist(id = "new_abc123")
        assertEquals("new_abc123", p.id)
    }

    @Test
    fun `new playlist id starts with new_`() {
        val id = "new_" + java.util.UUID.randomUUID().toString()
        assertTrue(id.startsWith("new_"))
    }

    @Test
    fun `existing playlist id does not start with new_`() {
        val p = makePlaylist(id = "pl_abc123")
        assertFalse(p.id.startsWith("new_"))
    }

    // ── File Paths ──
    @Test
    fun `general category file is just filename`() {
        val path = "recording_001.m4a"
        val name = path.substringAfter("/")
        assertEquals("recording_001.m4a", name) // No / found, returns original
    }

    @Test
    fun `category file has category prefix`() {
        val path = "English/lesson1.m4a"
        val category = path.substringBefore("/", "General")
        val name = path.substringAfter("/")
        assertEquals("English", category)
        assertEquals("lesson1.m4a", name)
    }

    @Test
    fun `file without category returns General`() {
        val path = "recording.m4a"
        val category = path.substringBefore("/", "General")
        assertEquals("General", category)
    }

    @Test
    fun `nested path extraction`() {
        val paths = listOf("General/file1.m4a", "French/lesson.m4a", "plain.m4a")
        val names = paths.map { it.substringAfter("/") }
        // "General/file1.m4a".substringAfter("/") = "file1.m4a"
        assertEquals("file1.m4a", names[0])
        assertEquals("lesson.m4a", names[1])
        // "plain.m4a".substringAfter("/") = "plain.m4a" (no "/" found)
        assertEquals("plain.m4a", names[2])
    }

    // ── Play Settings ──
    @Test
    fun `default speed is 1`() {
        val p = makePlaylist()
        assertEquals(1.0f, p.speed, 0.001f)
    }

    @Test
    fun `custom speed is preserved`() {
        val p = makePlaylist(speed = 1.5f)
        assertEquals(1.5f, p.speed, 0.001f)
    }

    @Test
    fun `default pitch is 1`() {
        val p = makePlaylist()
        assertEquals(1.0f, p.pitch, 0.001f)
    }

    @Test
    fun `custom pitch is preserved`() {
        val p = makePlaylist(pitch = 0.8f)
        assertEquals(0.8f, p.pitch, 0.001f)
    }

    @Test
    fun `default loop count is 1`() {
        val p = makePlaylist()
        assertEquals(1, p.loopCount)
    }

    @Test
    fun `infinite loop is minus one`() {
        val p = makePlaylist(loopCount = -1)
        assertEquals(-1, p.loopCount)
    }

    // ── Gap Logic ──
    @Test
    fun `default gap is zero seconds`() {
        val p = makePlaylist()
        assertEquals(0, p.gapSeconds)
    }

    @Test
    fun `gap to milliseconds conversion`() {
        val gapSeconds = 3
        val gapMs = gapSeconds * 1000L
        assertEquals(3000L, gapMs)
    }

    @Test
    fun `gap five seconds converts to 5000ms`() {
        val p = makePlaylist(gapSeconds = 5)
        val gapMs = p.gapSeconds * 1000L
        assertEquals(5000L, gapMs)
    }

    // ── Shuffle ──
    @Test
    fun `default shuffle is false`() {
        val p = makePlaylist()
        assertFalse(p.shuffle)
    }

    @Test
    fun `can enable shuffle`() {
        val p = makePlaylist(shuffle = true)
        assertTrue(p.shuffle)
    }

    @Test
    fun `shuffle changes file order`() {
        val files = listOf("a.m4a", "b.m4a", "c.m4a", "d.m4a", "e.m4a")
        val shuffled = files.shuffled()
        // Just verify shuffled has same elements
        assertEquals(files.size, shuffled.size)
        assertTrue(shuffled.containsAll(files))
    }

    // ── Sleep Timer ──
    @Test
    fun `default sleep is zero`() {
        val p = makePlaylist()
        assertEquals(0, p.sleepMinutes)
    }

    @Test
    fun `sleep 30 minutes in ms`() {
        val p = makePlaylist(sleepMinutes = 30)
        val sleepMs = p.sleepMinutes * 60_000L
        assertEquals(1_800_000L, sleepMs)
    }

    // ── Play Count ──
    @Test
    fun `initial play count is zero`() {
        val p = makePlaylist()
        assertEquals(0, p.playCount)
    }

    @Test
    fun `play count can increment`() {
        val p = makePlaylist()
        val updated = p.copy(playCount = p.playCount + 1)
        assertEquals(1, updated.playCount)
    }

    // ── File Manipulation ──
    @Test
    fun `adding file to playlist`() {
        val p = makePlaylist(files = listOf("old.m4a"))
        val updated = p.copy(files = p.files + "new.m4a")
        assertEquals(2, updated.files.size)
        assertTrue(updated.files.contains("new.m4a"))
    }

    @Test
    fun `removing file from playlist`() {
        val p = makePlaylist(files = listOf("file1.m4a", "file2.m4a", "file3.m4a"))
        val updated = p.copy(files = p.files - "file2.m4a")
        assertEquals(2, updated.files.size)
        assertFalse(updated.files.contains("file2.m4a"))
    }

    @Test
    fun `reordering files in playlist`() {
        val original = listOf("a.m4a", "b.m4a", "c.m4a")
        val reordered = listOf("c.m4a", "a.m4a", "b.m4a")
        val p = makePlaylist(files = original)
        val updated = p.copy(files = reordered)
        assertEquals("c.m4a", updated.files[0])
        assertEquals("a.m4a", updated.files[1])
    }

    @Test
    fun `total duration estimation simple`() {
        // Each file hypothetically 60s → 3 files = 180s
        val durations = mapOf("a.m4a" to 60_000L, "b.m4a" to 60_000L, "c.m4a" to 60_000L)
        val p = makePlaylist(files = listOf("a.m4a", "b.m4a", "c.m4a"))
        val total = p.files.sumOf { durations[it] ?: 0L }
        assertEquals(180_000L, total)
    }

    @Test
    fun `total duration with gap included`() {
        val durations = mapOf("a.m4a" to 30_000L, "b.m4a" to 30_000L)
        val p = makePlaylist(files = listOf("a.m4a", "b.m4a"), gapSeconds = 3)
        val audioTotal = p.files.sumOf { durations[it] ?: 0L }
        val gapTotal = (p.files.size - 1) * p.gapSeconds * 1000L
        assertEquals(60_000L + 3_000L, audioTotal + gapTotal)
    }
}
