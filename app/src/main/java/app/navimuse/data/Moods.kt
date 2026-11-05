package app.navimuse.data

/**
 * Moods in Discover. Each one is a description AudioMuse-AI's mood search can match by how songs
 * sound, plus genre words to fall back on when AudioMuse-AI isn't connected directly.
 */
enum class Mood(
    val label: String,
    /** What AudioMuse-AI's mood search is asked for. */
    val query: String,
    /** Genre words (whole words, any case) that fit this mood. */
    val genres: List<String>,
    /** Tile gradient, top-left to bottom-right (ARGB). */
    val from: Long,
    val to: Long,
) {
    CHILL(
        "Chill", "chill relaxed mellow laid-back music",
        listOf("chill", "chillout", "chill out", "lounge", "downtempo", "trip hop", "lo fi", "lofi", "easy listening", "acoustic", "ambient"),
        0xFF2BC0B4, 0xFF1B4F72,
    ),
    HAPPY(
        "Happy", "happy upbeat cheerful feel-good music",
        listOf("pop", "funk", "disco", "reggae", "ska", "motown", "soul", "pop rock", "power pop", "indie pop"),
        0xFFFFC23C, 0xFFF2662E,
    ),
    ENERGETIC(
        "Energetic", "energetic fast exciting music with a lot of drive",
        listOf("rock", "punk", "pop punk", "hard rock", "drum and bass", "dnb", "edm", "electro", "big beat", "alternative rock"),
        0xFFFF5F6D, 0xFFFFA24C,
    ),
    WORKOUT(
        "Workout", "high energy workout music with a pounding driving beat",
        listOf("metal", "metalcore", "nu metal", "hardcore", "hip hop", "rap", "trap", "dubstep", "edm", "drum and bass", "hardstyle", "industrial"),
        0xFFE53935, 0xFF7A0C0C,
    ),
    PARTY(
        "Party", "party dance music, loud and fun",
        listOf("dance", "house", "disco", "edm", "electro", "hip hop", "reggaeton", "latin", "dancehall", "club", "eurodance"),
        0xFFC33CFF, 0xFFFF3C8E,
    ),
    FOCUS(
        "Focus", "calm instrumental music for focus and concentration",
        listOf("classical", "instrumental", "post rock", "ambient", "piano", "soundtrack", "score", "lo fi", "lofi", "minimal", "modern classical"),
        0xFF4A6CF7, 0xFF1F2A6B,
    ),
    SLEEP(
        "Sleep", "soft quiet slow ambient music for falling asleep",
        listOf("ambient", "new age", "meditation", "piano", "classical", "drone", "sleep", "dark ambient"),
        0xFF3B2C7A, 0xFF0E1033,
    ),
    ROMANCE(
        "Romance", "romantic warm love songs",
        listOf("r&b", "rnb", "soul", "neo soul", "ballad", "bossa nova", "quiet storm", "smooth jazz"),
        0xFFFF6F91, 0xFF9E2F5E,
    ),
    SAD(
        "Sad", "sad slow emotional melancholic music",
        listOf("blues", "emo", "slowcore", "sadcore", "singer songwriter", "folk", "dream pop", "shoegaze"),
        0xFF5B7DB1, 0xFF243349,
    ),
    DARK(
        "Dark & heavy", "dark heavy aggressive distorted guitars",
        listOf("metal", "heavy metal", "death metal", "black metal", "doom metal", "metalcore", "sludge", "industrial", "gothic", "nu metal", "thrash metal"),
        0xFF5A1010, 0xFF0B0B0B,
    ),
    ROAD_TRIP(
        "Road trip", "upbeat road trip anthems for driving",
        listOf("classic rock", "rock", "country", "southern rock", "americana", "heartland rock", "alternative", "indie rock", "red dirt"),
        0xFF56CCF2, 0xFF2F5FD0,
    ),
    LATE_NIGHT(
        "Late night", "late night smooth jazz and soul",
        listOf("jazz", "soul", "r&b", "trip hop", "lounge", "blues", "neo soul", "smooth jazz", "synthwave"),
        0xFF6A3093, 0xFF1D1036,
    );

    companion object {
        /** Lower case, with every run of punctuation as one space, so "Hip-Hop" and "hip hop" match. */
        private fun words(s: String) = " " + s.lowercase().replace(Regex("[^a-z0-9&]+"), " ").trim() + " "

        /** The library's genres that fit [mood], best matches (more fitting words) first. */
        fun matchGenres(mood: Mood, genres: List<String>): List<String> {
            val wanted = mood.genres.map(::words)
            return genres
                .map { g -> g to wanted.count { w -> words(g).contains(w) } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first }
        }
    }
}
