package app.navimuse

import app.navimuse.data.GhostSongs
import app.navimuse.data.TrashQueue
import app.navimuse.net.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrashQueueTest {
    private fun entry(id: String, path: String? = "Saliva/Every Twenty Years/0$id.flac", problem: String? = null) =
        TrashQueue.Entry(id, "Song $id", "Saliva", "Every Twenty Years", path, 1_700_000_000_000, problem)

    private fun song(id: String) =
        Song(id, "Song $id", "Saliva", "ar", "Every Twenty Years", "al", null, 240, 1, 1, 2021, "flac", 900, false)

    @Before
    fun reset() {
        TrashQueue.clearAll()
        GhostSongs.clearAll()
    }

    @Test
    fun keptBetweenRuns() {
        val list = listOf(entry("1"), entry("2", path = null), entry("3", problem = "Couldn't find it"))
        val back = TrashQueue.fromJson(TrashQueue.toJson(list))
        assertEquals(list, back)
        assertNull(back[1].path)
        assertEquals("Couldn't find it", back[2].problem)
    }

    @Test
    fun brokenOrMissingDataIsAnEmptyQueue() {
        assertTrue(TrashQueue.fromJson(null).isEmpty())
        assertTrue(TrashQueue.fromJson("not json").isEmpty())
        assertTrue(TrashQueue.fromJson("""[{"title":"no id"}]""").isEmpty())
    }

    @Test
    fun deletingTheSameSongTwiceQueuesItOnce() {
        TrashQueue.add(entry("1"))
        TrashQueue.add(entry("1", path = "elsewhere.flac"))
        assertEquals(1, TrashQueue.all.value.size)
        assertEquals("elsewhere.flac", TrashQueue.all.value[0].path)
    }

    @Test
    fun songsThatWentWrongWaitForYou() {
        TrashQueue.add(entry("1"))
        TrashQueue.add(entry("2"))
        TrashQueue.setProblem("2", "Couldn't find it in your music folder")
        assertEquals(listOf("1"), TrashQueue.waiting().map { it.songId })
        TrashQueue.setProblem("2", null)
        assertEquals(listOf("1", "2"), TrashQueue.waiting().map { it.songId })
    }

    @Test
    fun movingOneCountsAndTakesItOff() {
        TrashQueue.add(entry("1"))
        val before = TrashQueue.moved.value
        TrashQueue.done("1")
        assertFalse(TrashQueue.has("1"))
        assertEquals(before + 1, TrashQueue.moved.value)
    }

    @Test
    fun aWaitingSongStaysHiddenEvenThoughItPlays() {
        val s = song("1")
        TrashQueue.add(entry("1"))
        GhostSongs.mark(s, "Waiting to move to the Trash")
        // A ghost check finds the file still plays: it stays hidden while it waits.
        GhostSongs.clear("1")
        assertTrue(GhostSongs.isGhost(s))
        // Keep: off the queue, and back in your lists.
        TrashQueue.remove("1")
        GhostSongs.clear("1")
        assertFalse(GhostSongs.isGhost(s))
    }
}
