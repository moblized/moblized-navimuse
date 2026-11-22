package app.navimuse

import app.navimuse.data.GhostSongs
import app.navimuse.data.TrashQueue
import app.navimuse.net.NavidromeNative
import app.navimuse.net.Song
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GhostDeleteTest {
    private fun song(id: String) =
        Song(id, "Song $id", "10 Years", "ar", "Division", "al", null, 195, 1, 1, 2008, "flac", 993, false)

    @Before
    fun reset() {
        TrashQueue.clearAll()
        GhostSongs.clearAll()
    }

    @Test
    fun navidromeSaysWhenAFileIsMissing() {
        val gone = NavidromeNative.parseSongFile(JSONObject("""{"id":"1","path":"10 Years/Division/01.flac","missing":true}"""))
        assertTrue(gone.missing)
        val there = NavidromeNative.parseSongFile(JSONObject("""{"id":"2","path":"10 Years/Division/02.flac"}"""))
        assertFalse(there.missing)
    }

    @Test
    fun deletedGhostsLeaveTheListButStayOutOfMixes() {
        GhostSongs.mark(song("1"), "Navidrome can't find its file")
        GhostSongs.mark(song("2"), "Navidrome can't find its file")
        GhostSongs.relabel(listOf("1"), "Deleting…", removed = true)
        val listed = GhostSongs.listed(GhostSongs.all.value, emptySet())
        assertEquals(setOf("2"), listed.keys)
        // Still a ghost: kept out of queues and mixes until Navidrome drops it.
        assertTrue(GhostSongs.isGhost("1"))
        assertTrue(GhostSongs.playable(listOf(song("1"), song("3"))).none { it.id == "1" })
    }

    @Test
    fun songsWaitingForTheTrashAreNotOnTheGhostList() {
        GhostSongs.mark(song("1"), "won't play")
        GhostSongs.mark(song("2"), "won't play")
        assertEquals(setOf("2"), GhostSongs.listed(GhostSongs.all.value, setOf("1")).keys)
    }

    @Test
    fun findingItAgainDoesntUndoTheDelete() {
        GhostSongs.mark(song("1"), "won't play")
        GhostSongs.relabel(listOf("1"), "Deleted", removed = true)
        GhostSongs.markAll(listOf(song("1") to "Navidrome can't find its file", song("2") to "Navidrome can't find its file"))
        assertTrue(GhostSongs.all.value.getValue("1").removed)
        assertFalse(GhostSongs.all.value.getValue("2").removed)
        GhostSongs.mark(song("1"), "Failed to play")
        assertTrue(GhostSongs.all.value.getValue("1").removed)
    }

    @Test
    fun forgettingManyAtOnceKeepsTheOnesWaitingForHome() {
        listOf("1", "2", "3").forEach { GhostSongs.mark(song(it), "won't play") }
        TrashQueue.add(TrashQueue.Entry("3", "Song 3", "10 Years", "Division", "x.flac", 1L, ghost = true))
        GhostSongs.clear(listOf("1", "2", "3", "not-a-ghost"))
        assertEquals(setOf("3"), GhostSongs.all.value.keys)
    }

    @Test
    fun keptBetweenRuns() {
        GhostSongs.mark(song("1"), "won't play")
        GhostSongs.relabel(listOf("1"), "Deleted", removed = true)
        val back = GhostSongs.fromJson(GhostSongs.toJson(GhostSongs.all.value))
        assertTrue(back.getValue("1").removed)
        val entry = TrashQueue.Entry("4", "Song 4", "10 Years", "Division", null, 1L, ghost = true)
        assertTrue(TrashQueue.fromJson(TrashQueue.toJson(listOf(entry))).single().ghost)
        // Saved before deleting existed: not deleted.
        assertFalse(GhostSongs.fromJson("""[{"id":"9","title":"t","artist":"a","album":"b","reason":"r","at":1}]""").getValue("9").removed)
    }
}
