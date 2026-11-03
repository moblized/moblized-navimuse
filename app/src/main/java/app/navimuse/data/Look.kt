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

/** Colours that can be set exactly in Settings → Fine-tune, separately for dark and light mode. */
enum class ColorRole(val label: String, val detail: String) {
    ACCENT("Accent", "What's playing, quality badges, switches"),
    BUTTONS("Main buttons", "Play, Save and other filled buttons"),
    BACKGROUND("Background", "Behind every page"),
    CARDS("Cards and sheets", "Mini player, menus, the search box"),
    TEXT("Text", "Titles and names"),
    SECONDARY_TEXT("Secondary text", "Artists, times, hints"),
    LINES("Lines", "Dividers and outlines"),
}

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
    /** Swipe a song in a list to the right to add it to the queue. */
    val swipeToQueue: Boolean = true,
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
    /** Off by default: the playing screen has no Share button unless switched on in Customize. */
    val showShare: Boolean = false,
    val swipeArt: Boolean = true,
    val keepScreenOn: Boolean = false,
    // mini player
    val miniStyle: MiniStyle = MiniStyle.DOCKED,
    val miniProgress: Boolean = true,
    val miniPrevious: Boolean = false,
    val miniNext: Boolean = true,
    val miniSwipe: Boolean = true,
    // fine-tuning: null follows the choices above
    val textScale: Float? = null,
    val titleScale: Float? = null,
    val artCornerDp: Int? = null,
    val cardSizeDp: Int? = null,
    val rowPaddingDp: Int? = null,
    val playerArtCornerDp: Int? = null,
    val playerArtMarginDp: Int? = null,
    val seekBarScale: Float? = null,
    val playButtonDp: Int? = null,
    val controlIconDp: Int? = null,
    val miniArtDp: Int? = null,
    val miniCornerDp: Int? = null,
    val tabBarHeightDp: Int? = null,
    val tabIconDp: Int? = null,
    /** Exact colours (ARGB) by role, for the dark and the light look. */
    val colorsDark: Map<ColorRole, Long> = emptyMap(),
    val colorsLight: Map<ColorRole, Long> = emptyMap(),
) {
    // What's actually used: a fine-tuned value, else the preset chosen above.
    val effectiveTextScale: Float get() = textScale ?: textSize.scale
    val effectiveTitleScale: Float get() = titleScale ?: 1f
    val artCorner: Int get() = artCornerDp ?: corners.dp
    val cardWidth: Int get() = cardSizeDp ?: cardSize.dp
    val rowPadding: Int get() = rowPaddingDp ?: 7
    /** Corner of the playing screen's art, or null for a circle (circle or record). */
    val playerArtCorner: Int?
        get() = if (playerArt == PlayerArt.CIRCLE || playerArt == PlayerArt.RECORD) null else playerArtCornerDp ?: playerArt.cornerDp
    val playerArtMargin: Int get() = playerArtMarginDp ?: artSize.marginDp
    val seekScale: Float get() = seekBarScale ?: seekBar.scale
    val playButtonSize: Int get() = playButtonDp ?: if (playButton == PlayButtonStyle.CIRCLE) 78 else 88
    val controlIcon: Int get() = controlIconDp ?: 44
    val miniArt: Int get() = miniArtDp ?: 44
    val miniCorner: Int get() = miniCornerDp ?: 14
    val tabBarHeight: Int get() = tabBarHeightDp ?: 62
    val tabIcon: Int get() = tabIconDp ?: 24

    fun colors(dark: Boolean): Map<ColorRole, Long> = if (dark) colorsDark else colorsLight

    fun withColor(dark: Boolean, role: ColorRole, argb: Long?): Look {
        val now = colors(dark).let { if (argb == null) it - role else it + (role to argb) }
        return if (dark) copy(colorsDark = now) else copy(colorsLight = now)
    }

    val isFineTuned: Boolean
        get() = listOf<Any?>(
            textScale, titleScale, artCornerDp, cardSizeDp, rowPaddingDp, playerArtCornerDp, playerArtMarginDp,
            seekBarScale, playButtonDp, controlIconDp, miniArtDp, miniCornerDp, tabBarHeightDp, tabIconDp,
        ).any { it != null } || colorsDark.isNotEmpty() || colorsLight.isNotEmpty()

    /** The same choices without any fine-tuning. */
    fun withoutFineTuning(): Look = copy(
        textScale = null, titleScale = null, artCornerDp = null, cardSizeDp = null, rowPaddingDp = null,
        playerArtCornerDp = null, playerArtMarginDp = null, seekBarScale = null, playButtonDp = null,
        controlIconDp = null, miniArtDp = null, miniCornerDp = null, tabBarHeightDp = null, tabIconDp = null,
        colorsDark = emptyMap(), colorsLight = emptyMap(),
    )

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
            textScale = textScale?.coerceIn(TEXT_SCALE),
            titleScale = titleScale?.coerceIn(TITLE_SCALE),
            artCornerDp = artCornerDp?.coerceIn(ART_CORNER),
            cardSizeDp = cardSizeDp?.coerceIn(CARD_SIZE),
            rowPaddingDp = rowPaddingDp?.coerceIn(ROW_PADDING),
            playerArtCornerDp = playerArtCornerDp?.coerceIn(PLAYER_ART_CORNER),
            playerArtMarginDp = playerArtMarginDp?.coerceIn(PLAYER_ART_MARGIN),
            seekBarScale = seekBarScale?.coerceIn(SEEK_SCALE),
            playButtonDp = playButtonDp?.coerceIn(PLAY_BUTTON),
            controlIconDp = controlIconDp?.coerceIn(CONTROL_ICON),
            miniArtDp = miniArtDp?.coerceIn(MINI_ART),
            miniCornerDp = miniCornerDp?.coerceIn(MINI_CORNER),
            tabBarHeightDp = tabBarHeightDp?.coerceIn(TAB_BAR),
            tabIconDp = tabIconDp?.coerceIn(TAB_ICON),
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
        put("swipeToQueue", swipeToQueue)
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
        textScale?.let { put("textScale", it.toDouble()) }
        titleScale?.let { put("titleScale", it.toDouble()) }
        artCornerDp?.let { put("artCornerDp", it) }
        cardSizeDp?.let { put("cardSizeDp", it) }
        rowPaddingDp?.let { put("rowPaddingDp", it) }
        playerArtCornerDp?.let { put("playerArtCornerDp", it) }
        playerArtMarginDp?.let { put("playerArtMarginDp", it) }
        seekBarScale?.let { put("seekBarScale", it.toDouble()) }
        playButtonDp?.let { put("playButtonDp", it) }
        controlIconDp?.let { put("controlIconDp", it) }
        miniArtDp?.let { put("miniArtDp", it) }
        miniCornerDp?.let { put("miniCornerDp", it) }
        tabBarHeightDp?.let { put("tabBarHeightDp", it) }
        tabIconDp?.let { put("tabIconDp", it) }
        if (colorsDark.isNotEmpty()) put("colorsDark", colorsJson(colorsDark))
        if (colorsLight.isNotEmpty()) put("colorsLight", colorsJson(colorsLight))
    }.toString()

    companion object {
        // Slider ranges in Fine-tune.
        val TEXT_SCALE = 0.75f..1.6f
        val TITLE_SCALE = 0.7f..1.5f
        val ART_CORNER = 0..40
        val CARD_SIZE = 100..240
        val ROW_PADDING = 2..18
        val PLAYER_ART_CORNER = 0..60
        val PLAYER_ART_MARGIN = 0..120
        val SEEK_SCALE = 0.5f..3f
        val PLAY_BUTTON = 56..120
        val CONTROL_ICON = 28..64
        val MINI_ART = 32..72
        val MINI_CORNER = 0..36
        val TAB_BAR = 48..88
        val TAB_ICON = 18..34

        /** "#AARRGGBB" for each role. */
        private fun colorsJson(map: Map<ColorRole, Long>) = JSONObject().apply {
            map.forEach { (role, argb) -> put(role.name, "#%08X".format(argb and 0xFFFFFFFFL)) }
        }

        private fun JSONObject.colors(key: String): Map<ColorRole, Long> {
            val o = optJSONObject(key) ?: return emptyMap()
            return o.keys().asSequence().mapNotNull { k ->
                val role = runCatching { enumValueOf<ColorRole>(k) }.getOrNull() ?: return@mapNotNull null
                val hex = o.optString(k).removePrefix("#")
                val v = hex.toLongOrNull(16) ?: return@mapNotNull null
                role to (if (hex.length <= 6) v or 0xFF000000L else v)
            }.toMap()
        }

        private fun JSONObject.floatOrNull(key: String): Float? =
            if (has(key)) optDouble(key).toFloat().takeIf { !it.isNaN() } else null

        private fun JSONObject.intOrNull(key: String): Int? = if (has(key)) optInt(key) else null

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
                swipeToQueue = o.optBoolean("swipeToQueue", d.swipeToQueue),
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
                textScale = o.floatOrNull("textScale"),
                titleScale = o.floatOrNull("titleScale"),
                artCornerDp = o.intOrNull("artCornerDp"),
                cardSizeDp = o.intOrNull("cardSizeDp"),
                rowPaddingDp = o.intOrNull("rowPaddingDp"),
                playerArtCornerDp = o.intOrNull("playerArtCornerDp"),
                playerArtMarginDp = o.intOrNull("playerArtMarginDp"),
                seekBarScale = o.floatOrNull("seekBarScale"),
                playButtonDp = o.intOrNull("playButtonDp"),
                controlIconDp = o.intOrNull("controlIconDp"),
                miniArtDp = o.intOrNull("miniArtDp"),
                miniCornerDp = o.intOrNull("miniCornerDp"),
                tabBarHeightDp = o.intOrNull("tabBarHeightDp"),
                tabIconDp = o.intOrNull("tabIconDp"),
                colorsDark = o.colors("colorsDark"),
                colorsLight = o.colors("colorsLight"),
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
