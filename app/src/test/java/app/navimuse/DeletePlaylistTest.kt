package app.navimuse

import app.navimuse.data.DeletedPlaylists
import app.navimuse.net.BlockedRequestException
import app.navimuse.net.SafetyGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DeletePlaylistTest {
    private fun blocked(vararg params: Pair<String, String>, now: Long = 1_000_000L): Boolean = try {
        SafetyGuard.check("deletePlaylist", params.toList(), now)
        false
    } catch (e: BlockedRequestException) {
        true
    }

    @Before
    fun reset() = DeletedPlaylists.clearAll()

    @Test
    fun withoutYourConfirmationItIsNeverSent() {
        assertTrue(blocked("id" to "never-confirmed"))
    }

    @Test
    fun oneConfirmationOneDelete() {
        SafetyGuard.permitPlaylistDelete("p1", now = 1_000_000L)
        assertTrue("another playlist", blocked("id" to "p2"))
        try {
            SafetyGuard.check("deletePlaylist", listOf("id" to "p1"), 1_000_500L)
        } catch (e: BlockedRequestException) {
            fail("the confirmed delete was refused: ${e.message}")
        }
        assertTrue("the same delete again", blocked("id" to "p1", now = 1_001_000L))
    }

    @Test
    fun aConfirmationRunsOut() {
        SafetyGuard.permitPlaylistDelete("p3", now = 1_000_000L)
        assertTrue(blocked("id" to "p3", now = 1_000_000L + 61_000L))
    }

    @Test
    fun exactlyOnePlaylistAndNothingElse() {
        SafetyGuard.permitPlaylistDelete("p4", now = 1_000_000L)
        assertTrue(blocked("id" to "p4", "id" to "p5"))
        SafetyGuard.permitPlaylistDelete("p6", now = 1_000_000L)
        assertTrue(blocked("id" to "p6", "songIndexToRemove" to "0"))
        assertTrue(blocked())
    }

    @Test
    fun deletedPlaylistsAreKeptToRestore() {
        val a = DeletedPlaylists.Entry("p1", "Gym", listOf("s1", "s2", "s3"), 600, 1_000L)
        val b = DeletedPlaylists.Entry("p2", "Rock_Fast_automatic", emptyList(), 0, 2_000L)
        DeletedPlaylists.add(a)
        DeletedPlaylists.add(b)
        assertEquals(listOf("p2", "p1"), DeletedPlaylists.all.value.map { it.playlistId })
        val back = DeletedPlaylists.fromJson(DeletedPlaylists.toJson(DeletedPlaylists.all.value))
        assertEquals(DeletedPlaylists.all.value, back)
        assertEquals(listOf("s1", "s2", "s3"), back.last().songIds)
    }

    @Test
    fun keptThirtyDays() {
        val day = 24L * 60 * 60 * 1000
        DeletedPlaylists.add(DeletedPlaylists.Entry("old", "Old", listOf("s1"), 60, 0L))
        DeletedPlaylists.add(DeletedPlaylists.Entry("new", "New", listOf("s1"), 60, 20 * day))
        DeletedPlaylists.prune(now = 31 * day)
        assertEquals(listOf("new"), DeletedPlaylists.all.value.map { it.playlistId })
    }
}
