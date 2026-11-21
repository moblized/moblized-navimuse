package app.navimuse.data

/**
 * Reads a playlist title in plain words ("Gym", "Rainy Sunday", "Late night drive", "Skillet and
 * friends") for what it asks for: moods, genres and artists from your library. Used to fill a new
 * playlist from its title when AudioMuse-AI isn't connected directly (with it, the title goes to
 * its mood search as is), and for artists either way.
 */
object TitleWords {
    /** Lower case, every run of punctuation as one space, padded: " hip hop ". */
    fun words(s: String): String = " " + s.lowercase().replace(Regex("[^a-z0-9&]+"), " ").trim() + " "

    /** Everyday words that point to a mood, beyond the mood's own name. */
    private val moodWords: Map<Mood, List<String>> = mapOf(
        Mood.CHILL to listOf("chill", "chilled", "relax", "relaxing", "calm", "mellow", "lazy", "sunday", "coffee", "cafe", "easy", "unwind", "beach", "porch"),
        Mood.HAPPY to listOf("happy", "sunny", "sunshine", "summer", "good vibes", "feel good", "smile", "cheerful", "fun", "spring"),
        Mood.ENERGETIC to listOf("energy", "energetic", "hype", "hyped", "upbeat", "fast", "power", "adrenaline", "pump up"),
        Mood.WORKOUT to listOf("workout", "work out", "gym", "lift", "lifting", "run", "running", "cardio", "training", "sweat", "beast mode", "leg day", "crossfit", "boxing"),
        Mood.PARTY to listOf("party", "dance", "dancing", "club", "pregame", "friday", "saturday", "weekend", "bbq", "cookout", "celebration"),
        Mood.FOCUS to listOf("focus", "study", "studying", "concentrate", "concentration", "reading", "coding", "deep work", "homework"),
        Mood.SLEEP to listOf("sleep", "sleeping", "bedtime", "bed", "nap", "lullaby", "dream", "dreams", "wind down"),
        Mood.ROMANCE to listOf("love", "romance", "romantic", "date night", "date", "valentine", "valentines", "wedding", "slow dance"),
        Mood.SAD to listOf("sad", "breakup", "break up", "heartbreak", "heartbroken", "cry", "crying", "rain", "rainy", "lonely", "blue", "missing you", "tears"),
        Mood.DARK to listOf("dark", "heavy", "angry", "anger", "rage", "aggressive", "brutal", "mosh", "headbang"),
        Mood.ROAD_TRIP to listOf("road", "road trip", "drive", "driving", "truck", "highway", "cruise", "cruising", "car", "commute", "travel", "miles"),
        Mood.LATE_NIGHT to listOf("late", "late night", "night", "midnight", "evening", "after hours", "smooth", "night drive"),
    )

    /** Moods [title] asks for, the most clearly asked first ("Gym" → Workout). */
    fun moodsIn(title: String): List<Mood> {
        val t = words(title)
        return Mood.entries
            .map { mood ->
                val asked = (moodWords[mood].orEmpty() + mood.label).map(::words)
                mood to asked.filter { t.contains(it) }.sumOf { it.trim().length }
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** The library's genres [title] names outright ("Rock for the gym" → Rock), longest names first. */
    fun genresIn(title: String, genres: List<String>): List<String> {
        val t = words(title)
        return genres
            .filter { g -> words(g).isNotBlank() && t.contains(words(g)) }
            .sortedByDescending { it.length }
    }

    /** Artists from [names] that [title] names outright ("Skillet and friends" → Skillet). */
    fun artistsIn(title: String, names: List<String>): List<String> {
        val t = words(title)
        return names.filter { n -> words(n).trim().length >= 3 && t.contains(words(n)) }.distinct()
    }
}
