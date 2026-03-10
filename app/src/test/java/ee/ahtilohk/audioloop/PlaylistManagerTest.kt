package ee.ahtilohk.audioloop

import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlaylistManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var playlistManager: PlaylistManager

    @Before
    fun setup() {
        context = mockk()
        val mockFilesDir = tempFolder.newFolder("filesDir")
        every { context.filesDir } returns mockFilesDir

        playlistManager = PlaylistManager(context)
    }

    @After
    fun tearDown() {
        // Nothing since TemporaryFolder cleans up automatically
    }

    @Test
    fun `test create new playlist and save it`() {
        val newPlaylist = playlistManager.createNew("My Playlist")
        assertEquals("My Playlist", newPlaylist.name)
        assertTrue(newPlaylist.files.isEmpty())

        playlistManager.save(newPlaylist)

        val loadedPlaylists = playlistManager.loadAll()
        assertEquals(1, loadedPlaylists.size)
        assertEquals("My Playlist", loadedPlaylists[0].name)
        assertEquals(newPlaylist.id, loadedPlaylists[0].id)
    }

    @Test
    fun `test delete playlist`() {
        val newPlaylist = playlistManager.createNew("My Playlist")
        playlistManager.save(newPlaylist)
        
        var loadedPlaylists = playlistManager.loadAll()
        assertEquals(1, loadedPlaylists.size)

        playlistManager.delete(newPlaylist.id)

        loadedPlaylists = playlistManager.loadAll()
        assertTrue(loadedPlaylists.isEmpty())
    }

    @Test
    fun `test increment play count`() {
        val newPlaylist = playlistManager.createNew("My Playlist")
        playlistManager.save(newPlaylist)
        
        assertEquals(0, playlistManager.loadAll()[0].playCount)

        playlistManager.incrementPlayCount(newPlaylist.id)

        val updatedPlaylist = playlistManager.loadAll()[0]
        assertEquals(1, updatedPlaylist.playCount)
    }

    @Test
    fun `test resolve files`() {
        val uriMap = mockk<Uri>()
        val recording1 = RecordingItem(File("path/to/General/file1.mp3"), "file1.mp3", "00:10", 10000L, uriMap, "")
        val recording2 = RecordingItem(File("path/to/Music/file2.mp3"), "file2.mp3", "00:20", 20000L, uriMap, "")
        val allRecordings = listOf(recording1, recording2)

        val playlist = playlistManager.createNew("Test List").copy(
            files = listOf("General/file1.mp3", "Music/file2.mp3")
        )

        val resolved = playlistManager.resolveFiles(playlist, allRecordings)
        assertEquals(2, resolved.size)
        assertEquals("file1.mp3", resolved[0].name)
        assertEquals("file2.mp3", resolved[1].name)
        
        // Test filtering unknown files
        val playlistWithUnknown = playlist.copy(files = listOf("General/file1.mp3", "Unknown/file3.mp3"))
        val resolvedWithUnknown = playlistManager.resolveFiles(playlistWithUnknown, allRecordings)
        assertEquals(1, resolvedWithUnknown.size)
        assertEquals("file1.mp3", resolvedWithUnknown[0].name)
    }

    @Test
    fun `test total duration logic`() {
        val uriMap = mockk<Uri>()
        val recording1 = RecordingItem(File("path/to/file1.mp3"), "file1.mp3", "00:10", 10000L, uriMap, "")
        val recording2 = RecordingItem(File("path/to/file2.mp3"), "file2.mp3", "00:20", 20000L, uriMap, "")
        val allRecordings = listOf(recording1, recording2)

        val playlist = playlistManager.createNew("Test List").copy(
            files = listOf("General/file1.mp3", "General/file2.mp3"),
            gapSeconds = 5
        )

        // Total should be: 10s (rec1) + 20s (rec2) + 5s (1 gap) = 35 seconds = 35000 ms
        val totalMillis = playlistManager.totalDuration(playlist, allRecordings)
        assertEquals(35000L, totalMillis)

        // Format duration test
        val formatted = playlistManager.formatTotalDuration(playlist, allRecordings)
        assertEquals("35s", formatted)
        
        // Test longer duration
        val longPlaylist = playlist.copy(gapSeconds = 0)
        val recording3 = RecordingItem(File("path/to/file3.mp3"), "file3.mp3", "02:00", 120000L, uriMap, "") // 2 mins
        val longRecordings = listOf(recording1, recording2, recording3)
        val longPlaylistUpdated = longPlaylist.copy(files = listOf("General/file1.mp3", "General/file2.mp3", "General/file3.mp3"))

        // Total should be: 10s + 20s + 120s = 150s = 2.5 minutes
        val longFormatted = playlistManager.formatTotalDuration(longPlaylistUpdated, longRecordings)
        assertEquals("~2 min", longFormatted)
    }
}
