package app.navimuse

import app.navimuse.data.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodsTest {
    private val library = listOf(
        "Metal", "Heavy Metal", "Metalcore", "Hip-Hop", "Classic Rock", "Rock", "Pop", "Lo-Fi", "Jazz",
        "R&B", "Ambient", "Country", "Trap", "Rapcore", "Ska Punk",
    )

    @Test
    fun genresMatchByWholeWordsInAnyCase() {
        assertEquals(listOf("Heavy Metal", "Metal", "Metalcore"), Mood.matchGenres(Mood.DARK, library))
        // "hip hop" matches "Hip-Hop"; "rap" doesn't catch "Rapcore", and "trap" is its own word.
        val workout = Mood.matchGenres(Mood.WORKOUT, library)
        assertTrue("Hip-Hop" in workout)
        assertTrue("Trap" in workout)
        assertTrue("Rapcore" !in workout)
        assertEquals(listOf("Lo-Fi", "Ambient"), Mood.matchGenres(Mood.CHILL, library))
        assertEquals(listOf("Jazz", "R&B"), Mood.matchGenres(Mood.LATE_NIGHT, library))
    }

    @Test
    fun genresThatFitBetterComeFirst() {
        // "Classic Rock" fits Road trip twice ("classic rock" and "rock"); plain "Rock" once.
        assertEquals(listOf("Classic Rock", "Rock", "Country"), Mood.matchGenres(Mood.ROAD_TRIP, library))
    }

    @Test
    fun noFittingGenresGivesNothing() {
        assertTrue(Mood.matchGenres(Mood.SLEEP, listOf("Metal", "Punk")).isEmpty())
        assertTrue(Mood.matchGenres(Mood.PARTY, emptyList()).isEmpty())
    }

    @Test
    fun everyMoodHasASearchAndGenres() {
        Mood.entries.forEach {
            assertTrue(it.label, it.query.isNotBlank())
            assertTrue(it.label, it.genres.isNotEmpty())
        }
    }
}
