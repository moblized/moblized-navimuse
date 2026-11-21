package app.navimuse

import app.navimuse.data.Mood
import app.navimuse.data.TitleWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleWordsTest {
    @Test
    fun everydayWordsPointToMoods() {
        assertEquals(Mood.WORKOUT, TitleWords.moodsIn("Gym").first())
        assertEquals(Mood.WORKOUT, TitleWords.moodsIn("leg day!!").first())
        assertTrue(Mood.SAD in TitleWords.moodsIn("Rainy Sunday"))
        assertTrue(Mood.CHILL in TitleWords.moodsIn("Rainy Sunday"))
        assertTrue(Mood.ROAD_TRIP in TitleWords.moodsIn("Late night drive"))
        assertEquals(Mood.LATE_NIGHT, TitleWords.moodsIn("Late night drive").first())
        assertEquals(Mood.FOCUS, TitleWords.moodsIn("study").first())
        // A mood's own name works too.
        assertEquals(Mood.PARTY, TitleWords.moodsIn("PARTY").first())
        assertEquals(emptyList<Mood>(), TitleWords.moodsIn("Zzyzx"))
    }

    @Test
    fun wordsAreWholeWords() {
        // "bed" is a sleep word, but not inside "Bedrock".
        assertTrue(Mood.SLEEP !in TitleWords.moodsIn("Bedrock"))
        // "run" is a workout word, but not inside "Runaway".
        assertTrue(Mood.WORKOUT !in TitleWords.moodsIn("Runaway"))
    }

    @Test
    fun genresTheTitleNames() {
        val genres = listOf("Rock", "Hard Rock", "Hip-Hop", "Metal", "Pop")
        assertEquals(listOf("Hard Rock", "Rock"), TitleWords.genresIn("Hard rock for the gym", genres))
        assertEquals(listOf("Hip-Hop"), TitleWords.genresIn("hip hop workout", genres))
        assertEquals(emptyList<String>(), TitleWords.genresIn("Poppy songs", genres))
    }

    @Test
    fun artistsTheTitleNames() {
        val names = listOf("Skillet", "Saliva", "Three Days Grace", "U2", "Skillet Tribute Band")
        assertEquals(listOf("Skillet"), TitleWords.artistsIn("Skillet and friends", names))
        assertEquals(listOf("Three Days Grace", "Saliva"), TitleWords.artistsIn("three days grace & saliva", names).sortedByDescending { it.length })
        // Too short to trust as a word in a title.
        assertEquals(emptyList<String>(), TitleWords.artistsIn("U2 style", names))
    }
}
