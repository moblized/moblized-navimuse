package app.navimuse

import app.navimuse.net.PlaylistNames
import app.navimuse.ui.AppViewModel.PlaySource
import app.navimuse.ui.Route
import app.navimuse.ui.queuePlaylistName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class QueueNamesTest {
    private val nov2 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 2) }.time

    @Test
    fun instantMixQueueIsNamedAfterTheMix() {
        assertEquals("Instant Mix: Bohemian Rhapsody", queuePlaylistName(PlaySource("Instant Mix: Bohemian Rhapsody"), nov2, Locale.US))
        assertEquals("Instant Mix: Abbey Road", queuePlaylistName(PlaySource("Instant Mix: Abbey Road", Route.AlbumPage("al-1")), nov2, Locale.US))
    }

    @Test
    fun playlistQueueBecomesACopy() {
        assertEquals("Road trip copy", queuePlaylistName(PlaySource("Road trip", Route.PlaylistPage("pl-1")), nov2, Locale.US))
    }

    @Test
    fun unknownSourceGetsADatedName() {
        assertEquals("Queue Nov 2", queuePlaylistName(null, nov2, Locale.US))
        assertEquals("Queue Nov 2", queuePlaylistName(PlaySource("  "), nov2, Locale.US))
    }

    @Test
    fun copyOfAnAutomaticPlaylistIsSafeFromAudioMuse() {
        val name = queuePlaylistName(PlaySource("Chill_automatic", Route.PlaylistPage("pl-2")), nov2, Locale.US)
        assertFalse(PlaylistNames.isAudioMuseManaged(name))
    }
}
