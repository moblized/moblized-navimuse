package app.navimuse.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Everything about how NaviMuse looks and is laid out, chosen in Settings → Customize.
 * Saved as one JSON object, so new options can be added later without losing old choices.
 */

/** The accent colour: what's playing, audio quality, active toggles. Colours are ARGB for dark, then light. */
enum class Accent(val label: String, val dark: Long = 0, val light: Long = 0) {
    THEME("Theme's own"),
    TEAL("Teal", 0xFF3DDCD2, 0xFF00807A),
    BLUE("Blue", 0xFF6CB4FF, 0xFF1565C0),
    PURPLE("Purple", 0xFFB79CFF, 0xFF6A3FD0),
    PINK("Pink", 0xFFFF79B0, 0xFFC2185B),
    RED("Red", 0xFFFF6B6B, 0xFFC62828),
    ORANGE("Orange", 0xFFFFA048, 0xFFC4500A),
    GOLD("Gold", 0xFFF5C84C, 0xFF8A6500),
    GREEN("Green", 0xFF63DB8F, 0xFF1B7F4B),
    MONO("Black and white", 0xFFFFFFFF, 0xFF000000),
    ALBUM("From the song playing"),
    PHONE("From your wallpaper"),
    ;

    val isPreset: Boolean get() = dark != 0L
}

/** Background in the dark look. */
enum class DarkShade(val label: String) { BLACK("Black"), GREY("Dark grey") }

enum class FontChoice(val label: String) {
    INSTRUMENT("Instrument Sans"),
    PHONE("Your phone's font"),
    SERIF("Serif"),
    MONO("Monospace"),
}

/** Page titles and names: Instrument Sans's narrow bold cut, or the normal bold. */
enum class TitleFont(val label: String) { CONDENSED("Narrow"), REGULAR("Regular") }

enum class TextSize(val label: String, val scale: Float) {
    SMALL("Small", 0.9f),
    DEFAULT("Default", 1f),
    LARGE("Large", 1.12f),
    LARGER("Larger", 1.25f),
}

/** Corners of album art everywhere except the big art on the playing screen. */
enum class Corners(val label: String, val dp: Int) {
    SQUARE("Square", 0),
    SLIGHT("Slight", 4),
    ROUNDED("Rounded", 12),
    ROUND("Round", 24),
}

/** Width of album and playlist cards on shelves. */
enum class CardSize(val label: String, val dp: Int) { SMALL("Small", 128), MEDIUM("Medium", 156), LARGE("Large", 188) }

enum class TabLabels(val label: String) { ALWAYS("Always"), SELECTED("Selected"), NEVER("Never") }

enum class PlayerBackground(val label: String) { SONG_COLOUR("Song colour"), BLUR("Blurred art"), PLAIN("Plain") }

enum class PlayerArt(val label: String, val cornerDp: Int = 0) {
    SQUARE("Square", 0),
    SOFT("Slightly rounded", 6),
    ROUNDED("Rounded", 20),
    CIRCLE("Circle"),
    RECORD("Spinning record"),
}

/** How much room the big art leaves at its sides. */
enum class ArtSize(val label: String, val marginDp: Int) { FULL("Full", 0), LARGE("Large", 20), MEDIUM("Medium", 48), SMALL("Small", 80) }

enum class TitleAlign(val label: String) { START("Left"), CENTER("Centre") }

enum class PlayButtonStyle(val label: String) { PLAIN("Plain"), CIRCLE("Circle") }

enum class SeekBarStyle(val label: String, val scale: Float) { THIN("Thin", 1f), THICK("Thick", 1.6f) }

enum class MiniStyle(val label: String) { DOCKED("Docked"), FLOATING("Floating") }

/** Sections of the home screen. */
enum class HomeSection(val label: String) {
    SHORTCUTS("Shuffle and mix buttons"),
    RECENTLY_PLAYED("Recently played"),
    MIXES("Your Instant Mixes"),
    RECENTLY_ADDED("Recently added"),
    MOST_PLAYED("Most played"),
    DISCOVER("Discover"),
    RANDOM_SONGS("Random songs"),
    FAVOURITE_SONGS("Favourite songs"),
    FAVOURITE_ALBUMS("Favourite albums"),
    FAVOURITE_ARTISTS("Favourite artists"),
    PLAYLISTS("Playlists"),
}

/** Entries in the Collection menu. */
enum class CollectionEntry(val label: String) {
    MIXES("Mixes & Radio"),
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres"),
    FAVOURITES("Favourites"),
    DOWNLOADS("Downloads"),
}

/** The bottom tabs. Home and Collection hold the Settings button, so one of them always stays. */
enum class TabChoice(val label: String) { HOME("Home"), MIXES("Mixes"), SEARCH("Search"), COLLECTION("Collection") }

data class Look(
    // colours
    val accent: Accent = Accent.THEME,
    val darkShade: DarkShade = DarkShade.BLACK,
    // text
    val font: FontChoice = FontChoice.INSTRUMENT,
    val titleFont: TitleFont = TitleFont.CONDENSED,
    val textSize: TextSize = TextSize.DEFAULT,
    // shapes and sizes
    val corners: Corners = Corners.SLIGHT,
    val cardSize: CardSize = CardSize.MEDIUM,
    /** Album grid columns; 0 fits as many as the screen allows. */
    val gridColumns: Int = 0,
    // bottom tabs
    val tabs: List<TabChoice> = TabChoice.entries,
    val tabLabels: TabLabels = TabLabels.ALWAYS,
    val startTab: TabChoice = TabChoice.HOME,
    // home and collection (shown entries, in order)
    val home: List<HomeSection> = DEFAULT_HOME,
    val collection: List<CollectionEntry> = DEFAULT_COLLECTION,
    val collectionRecent: Boolean = true,
    // playing screen
    val playerBackground: PlayerBackground = PlayerBackground.SONG_COLOUR,
    val playerArt: PlayerArt = PlayerArt.SOFT,
    val artSize: ArtSize = ArtSize.FULL,
    val titleAlign: TitleAlign = TitleAlign.START,
    val playButton: PlayButtonStyle = PlayButtonStyle.PLAIN,
    val seekBar: SeekBarStyle = SeekBarStyle.THIN,
    val showTimes: Boolean = true,
    val showShuffleRepeat: Boolean = true,
    val showArtistPhoto: Boolean = true,
    val showOutputButton: Boolean = true,
    val showPlayingFrom: Boolean = true,
    val showShare: Boolean = true,
    val swipeArt: Boolean = true,
    val keepScreenOn: Boolean = false,
    // mini player
    val miniStyle: MiniStyle = MiniStyle.DOCKED,
    val miniProgress: Boolean = true,
    val miniPrevious: Boolean = false,
    val miniNext: Boolean = true,
    val miniSwipe: Boolean = true,
) {
    /** Fixes choices that can't work together: no tabs, or none with a way into Settings. */
    fun sanitized(): Look {
        var t = tabs.distinct()
        if (TabChoice.HOME !in t && TabChoice.COLLECTION !in t) t = t + TabChoice.COLLECTION
        return copy(
            tabs = t,
            startTab = startTab.takeIf { it in t } ?: t.first(),
            home = home.distinct(),
            collection = collection.distinct(),
            gridColumns = gridColumns.coerceIn(0, 6),
        )
    }

    fun toJson(): String = JSONObject().apply {
        put("accent", accent.name)
        put("darkShade", darkShade.name)
        put("font", font.name)
        put("titleFont", titleFont.name)
        put("textSize", textSize.name)
        put("corners", corners.name)
        put("cardSize", cardSize.name)
        put("gridColumns", gridColumns)
        put("tabs", JSONArray(tabs.map { it.name }))
        put("tabLabels", tabLabels.name)
        put("startTab", startTab.name)
        put("home", JSONArray(home.map { it.name }))
        put("collection", JSONArray(collection.map { it.name }))
        put("collectionRecent", collectionRecent)
        put("playerBackground", playerBackground.name)
        put("playerArt", playerArt.name)
        put("artSize", artSize.name)
        put("titleAlign", titleAlign.name)
        put("playButton", playButton.name)
        put("seekBar", seekBar.name)
        put("showTimes", showTimes)
        put("showShuffleRepeat", showShuffleRepeat)
        put("showArtistPhoto", showArtistPhoto)
        put("showOutputButton", showOutputButton)
        put("showPlayingFrom", showPlayingFrom)
        put("showShare", showShare)
        put("swipeArt", swipeArt)
        put("keepScreenOn", keepScreenOn)
        put("miniStyle", miniStyle.name)
        put("miniProgress", miniProgress)
        put("miniPrevious", miniPrevious)
        put("miniNext", miniNext)
        put("miniSwipe", miniSwipe)
    }.toString()

    companion object {
        val DEFAULT_HOME = listOf(
            HomeSection.SHORTCUTS,
            HomeSection.RECENTLY_PLAYED,
            HomeSection.MIXES,
            HomeSection.RECENTLY_ADDED,
            HomeSection.MOST_PLAYED,
            HomeSection.DISCOVER,
        )
        val DEFAULT_COLLECTION = listOf(
            CollectionEntry.MIXES,
            CollectionEntry.PLAYLISTS,
            CollectionEntry.ALBUMS,
            CollectionEntry.ARTISTS,
            CollectionEntry.FAVOURITES,
            CollectionEntry.DOWNLOADS,
        )

        /** Reads saved choices. Anything missing or unknown keeps its default. */
        fun fromJson(json: String?): Look {
            if (json.isNullOrBlank()) return Look()
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return Look()
            val d = Look()
            return Look(
                accent = o.enumOr("accent", d.accent),
                darkShade = o.enumOr("darkShade", d.darkShade),
                font = o.enumOr("font", d.font),
                titleFont = o.enumOr("titleFont", d.titleFont),
                textSize = o.enumOr("textSize", d.textSize),
                corners = o.enumOr("corners", d.corners),
                cardSize = o.enumOr("cardSize", d.cardSize),
                gridColumns = o.optInt("gridColumns", d.gridColumns),
                tabs = o.enumList("tabs", d.tabs),
                tabLabels = o.enumOr("tabLabels", d.tabLabels),
                startTab = o.enumOr("startTab", d.startTab),
                home = o.enumList("home", d.home),
                collection = o.enumList("collection", d.collection),
                collectionRecent = o.optBoolean("collectionRecent", d.collectionRecent),
                playerBackground = o.enumOr("playerBackground", d.playerBackground),
                playerArt = o.enumOr("playerArt", d.playerArt),
                artSize = o.enumOr("artSize", d.artSize),
                titleAlign = o.enumOr("titleAlign", d.titleAlign),
                playButton = o.enumOr("playButton", d.playButton),
                seekBar = o.enumOr("seekBar", d.seekBar),
                showTimes = o.optBoolean("showTimes", d.showTimes),
                showShuffleRepeat = o.optBoolean("showShuffleRepeat", d.showShuffleRepeat),
                showArtistPhoto = o.optBoolean("showArtistPhoto", d.showArtistPhoto),
                showOutputButton = o.optBoolean("showOutputButton", d.showOutputButton),
                showPlayingFrom = o.optBoolean("showPlayingFrom", d.showPlayingFrom),
                showShare = o.optBoolean("showShare", d.showShare),
                swipeArt = o.optBoolean("swipeArt", d.swipeArt),
                keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
                miniStyle = o.enumOr("miniStyle", d.miniStyle),
                miniProgress = o.optBoolean("miniProgress", d.miniProgress),
                miniPrevious = o.optBoolean("miniPrevious", d.miniPrevious),
                miniNext = o.optBoolean("miniNext", d.miniNext),
                miniSwipe = o.optBoolean("miniSwipe", d.miniSwipe),
            ).sanitized()
        }

        private inline fun <reified E : Enum<E>> JSONObject.enumOr(key: String, default: E): E =
            runCatching { enumValueOf<E>(optString(key)) }.getOrDefault(default)

        /** A saved list of names; unknown names are dropped. A list that isn't there keeps [default]. */
        private inline fun <reified E : Enum<E>> JSONObject.enumList(key: String, default: List<E>): List<E> {
            val a = optJSONArray(key) ?: return default
            return (0 until a.length()).mapNotNull { i -> runCatching { enumValueOf<E>(a.optString(i)) }.getOrNull() }.distinct()
        }
    }
}
