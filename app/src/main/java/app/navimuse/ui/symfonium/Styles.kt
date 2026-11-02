package app.navimuse.ui.symfonium

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Symfonium's now-playing styles and full application styles (from Symfonium Styles). Both are
 * JSON exports of Symfonium's own settings:
 *
 *     {"name": "…", "type": "nowplaying" | "application", "style": "<JSON settings>"}
 *
 * Symfonium doesn't publish what its numbered options mean, so NaviMuse reads the settings
 * whose meaning is clear and keeps its own look for the rest:
 *  - now playing: custom text lines (Symfonium templates) and their order around the seek bar
 *    and buttons, top-bar text, cover-art corners, margins, cropping and rotation, background
 *    blur, seek-bar size and times, and the mini player's text, shape, floating and swipe;
 *  - app styles: home sections (which, in what order, their titles and sizes), home shortcuts,
 *    the library menu order, album grid columns, and the now-playing style inside.
 */

/** One line of text on the playing screen or mini player. */
data class TextLine(
    val template: String,
    val size: Float = 1f,
    val bold: Boolean = false,
    val accent: Boolean = false,
    val maxLines: Int = 1,
)

/** Pieces of the playing screen below the art, in the order a style puts them. */
sealed interface NpElement {
    data class Text(val line: TextLine) : NpElement
    data object Slider : NpElement
    data object Buttons : NpElement
}

data class NowPlayingStyle(
    val name: String,
    // mini player
    val miniTitle: TextLine? = null,
    val miniSubtitle: TextLine? = null,
    val miniFloating: Boolean = false,
    /** Corner rounding, 0–50 (% of the height). */
    val miniCornerPercent: Int = 0,
    val miniMarginH: Int = 0,
    val miniMarginBottom: Int = 0,
    val miniThumbCornerPercent: Int = 10,
    val swipeToSkip: Boolean = true,
    // playing screen
    val topTitle: TextLine? = null,
    val topSubtitle: TextLine? = null,
    /** Null: NaviMuse's own title, artist, seek bar and buttons. */
    val elements: List<NpElement>? = null,
    val coverCornerPercent: Int? = null,
    val coverMarginH: Int? = null,
    val coverNoCrop: Boolean = false,
    val coverRotates: Boolean = false,
    val backgroundBlur: Int = 0,
    val sliderScale: Float = 1f,
    val sliderTimes: Boolean = true,
)

/** A home section from a Symfonium app style. [source] is NaviMuse's matching list. */
data class HomeRow(
    val source: HomeSource,
    val title: String?,
    val showHeader: Boolean,
    val count: Int,
    /** Rows of a grid; 1 = a sideways shelf. */
    val gridRows: Int,
    val gridColumns: Int,
)

enum class HomeSource(val defaultTitle: String) {
    RECENTLY_PLAYED("Recently played"),
    RECENTLY_ADDED("Recently added"),
    MOST_PLAYED("Most played"),
    RANDOM_ALBUMS("Discover"),
    RANDOM_SONGS("Random songs"),
    FAVOURITE_SONGS("Favourite songs"),
    FAVOURITE_ALBUMS("Favourite albums"),
    FAVOURITE_ARTISTS("Favourite artists"),
    PLAYLISTS("Playlists"),
    SHORTCUTS(""),
}

/** A shortcut button on the home screen or a library menu entry. */
enum class Shortcut(val label: String) {
    LIBRARY("Library"), SEARCH("Search"), SETTINGS("Settings"), PLAYLISTS("Playlists"),
    ALBUMS("Albums"), ARTISTS("Artists"), GENRES("Genres"), FAVOURITES("Favourites"),
    DOWNLOADS("Downloads"), MIX("Surprise mix"), SHUFFLE("Shuffle all"), MIXES("Mixes"),
}

data class AppStyle(
    val name: String,
    val homeRows: List<HomeRow>,
    val shortcuts: List<List<Shortcut>>,
    val library: List<Shortcut>,
    val gridColumns: Int?,
    val nowPlaying: NowPlayingStyle?,
)

class StyleFormatException(message: String) : Exception(message)

object SymfoniumStyles {
    /** "nowplaying", "application", or null for anything else (a colour theme, say). */
    fun typeOf(json: String): String? = runCatching { JSONObject(json.trim().removePrefix("﻿")).optString("type") }
        .getOrNull()?.takeIf { it == "nowplaying" || it == "application" }

    /** The settings inside an export: "style" may be an object or a JSON string. */
    private fun settings(root: JSONObject): JSONObject {
        val s = root.opt("style")
        return when (s) {
            is JSONObject -> s
            is String -> runCatching { JSONObject(s) }.getOrNull()
            else -> null
        } ?: throw StyleFormatException("That style has no settings in it.")
    }

    private fun JSONObject.obj(key: String): JSONObject? = when (val v = opt(key)) {
        is JSONObject -> v
        is String -> runCatching { JSONObject(v) }.getOrNull()
        else -> null
    }

    // --- now playing --------------------------------------------------------------------------

    fun parseNowPlaying(json: String): NowPlayingStyle {
        val root = JSONObject(json.trim().removePrefix("﻿"))
        if (root.optString("type") != "nowplaying") throw StyleFormatException("That isn't a now-playing style.")
        return nowPlayingFrom(settings(root), root.optString("name").ifBlank { "Now playing style" })
    }

    private fun nowPlayingFrom(s: JSONObject, name: String): NowPlayingStyle {
        val compact = s.obj("compactConfiguration")
        val portrait = s.obj("portraitConfiguration")
        if (compact == null && portrait == null) throw StyleFormatException("That style has no now-playing settings in it.")
        var style = NowPlayingStyle(name)
        if (compact != null) {
            val margins = compact.obj("playerMargins")
            style = style.copy(
                miniTitle = textLine(compact.obj("title")),
                miniSubtitle = textLine(compact.obj("subtitle")),
                miniFloating = compact.optBoolean("floating", false),
                miniCornerPercent = compact.optInt("playerCornerRadius", 0).coerceIn(0, 50),
                miniMarginH = margins?.optInt("horizontal", 0)?.coerceIn(0, 32) ?: 0,
                miniMarginBottom = margins?.optInt("bottom", 0)?.coerceIn(0, 32) ?: 0,
                miniThumbCornerPercent = compact.optInt("thumbnailRoundedCorners", 10).coerceIn(0, 50),
                swipeToSkip = compact.optBoolean("swipeToSkip", true),
            )
        }
        if (portrait != null) {
            val texts = (1..6).associateWith { textLine(portrait.obj("controlText$it")) }
            val order = portrait.optJSONArray("controlsOrder")?.ints()
            val elements = (order ?: listOf(10, 11, 20, 30, 12, 13, 14, 15)).mapNotNull { code ->
                when (code) {
                    in 10..15 -> texts[code - 9]?.let { NpElement.Text(it) }
                    20 -> NpElement.Slider
                    30 -> NpElement.Buttons
                    else -> null
                }
            }.let { list ->
                // Always keep the seek bar and buttons, even if a style hid them.
                var l = list
                if (NpElement.Slider !in l) l = l + NpElement.Slider
                if (NpElement.Buttons !in l) l = l + NpElement.Buttons
                l
            }
            val hasText = texts.values.any { it != null }
            style = style.copy(
                topTitle = textLine(portrait.obj("topBarTitle")),
                topSubtitle = textLine(portrait.obj("topBarSubtitle")),
                elements = elements.takeIf { hasText },
                coverCornerPercent = portrait.optInt("coverArtRoundedCornerPercent", -1).takeIf { it >= 0 }?.coerceIn(0, 50),
                coverMarginH = portrait.obj("coverArtMargins")?.optInt("horizontal", -1)?.takeIf { it >= 0 }?.coerceIn(0, 64),
                coverNoCrop = portrait.optBoolean("coverArtNoCrop", false),
                coverRotates = portrait.optInt("coverArtRotation", 0) != 0,
                backgroundBlur = portrait.optInt("backgroundBlurLevel", 0).coerceIn(0, 100),
                sliderScale = portrait.optDouble("sliderSize", 1.0).toFloat().coerceIn(0.5f, 2.5f),
                sliderTimes = portrait.optInt("sliderShowTimes", 1) != 0,
            )
        }
        return style
    }

    /** A text setting with a custom template ("text": 100); other built-in choices aren't known. */
    private fun textLine(o: JSONObject?): TextLine? {
        if (o == null || o.optInt("text", 0) != 100) return null
        val t = o.optString("customText").ifBlank { return null }
        return TextLine(
            template = t,
            size = o.optDouble("size", 1.0).toFloat().coerceIn(0.5f, 3f),
            bold = o.optInt("style", 0) == 2,
            accent = o.optInt("color", 0) == 1,
            maxLines = kotlin.math.abs(o.optInt("lineCount", 1)).coerceIn(1, 6),
        )
    }

    private fun JSONArray.ints(): List<Int> = (0 until length()).map { optInt(it) }

    // --- application --------------------------------------------------------------------------

    fun parseApp(json: String): AppStyle {
        val root = JSONObject(json.trim().removePrefix("﻿"))
        if (root.optString("type") != "application") throw StyleFormatException("That isn't an application style.")
        val s = settings(root)
        // Settings are grouped under one-letter keys; read them all as one map.
        val all = JSONObject()
        s.keys().forEach { k ->
            val v = s.opt(k)
            if (v is JSONObject && k.length <= 2) v.keys().forEach { all.put(it, v.opt(it)) } else all.put(k, v)
        }
        val name = root.optString("name").ifBlank { "Application style" }

        val hidden = all.optString("home.rows.hidden").split(';').filter { it.isNotBlank() }.toSet()
        val shortcutSets = listOf("home.shortcuts", "home.shortcuts2", "home.shortcuts3", "home.shortcuts4")
            .map { shortcuts(all.optString(it)) }
        val rows = all.optString("home.rows").split(';').filter { it.isNotBlank() && it !in hidden }.mapNotNull { id ->
            val source = homeSource(id) ?: return@mapNotNull null
            val cfg = all.obj("overview.row.$id")
            if (source == HomeSource.SHORTCUTS) {
                val n = when (id) { "s:shortcut" -> 0; "s:shortcut2" -> 1; "s:shortcut3" -> 2; "s:shortcut4" -> 3; else -> -1 }
                if (n < 0 || shortcutSets[n].isEmpty()) return@mapNotNull null
                return@mapNotNull HomeRow(source, n.toString(), false, 0, 1, 0)
            }
            HomeRow(
                source = source,
                title = cfg?.optString("headerCustomText")?.takeIf { it.isNotBlank() },
                showHeader = cfg?.optBoolean("headerVisible", true) ?: true,
                count = (cfg?.optInt("dataItemCount", 20) ?: 20).coerceIn(4, 100),
                gridRows = (cfg?.optInt("dataItemGridRow", 1) ?: 1).coerceIn(1, 6),
                gridColumns = (cfg?.optInt("dataItemGridColumn", 3) ?: 3).coerceIn(1, 6),
            )
        }.distinctBy { if (it.source == HomeSource.SHORTCUTS) "s${it.title}" else it.source.name }

        val library = shortcuts(all.optString("library.shortcuts"))
        val grid = all.obj("library.page.configuration")?.optInt("gridColumns", 0)?.takeIf { it in 1..8 }
        val np = all.obj("now.playing.page.configuration")?.let { runCatching { nowPlayingFrom(it, name) }.getOrNull() }
        return AppStyle(name, rows, shortcutSets, library, grid, np)
    }

    /** Symfonium's home rows that NaviMuse has a list for (Navidrome keeps no per-song history). */
    fun homeSource(id: String): HomeSource? = when (id) {
        "q:lastplayedalbums", "q:lastplayedsongs", "q:resumealbums" -> HomeSource.RECENTLY_PLAYED
        "q:recentalbums", "q:recentsongs", "q:recentreleasealbum" -> HomeSource.RECENTLY_ADDED
        "q:mostplayedalbums", "q:mostplayedsongs" -> HomeSource.MOST_PLAYED
        "q:randomalbums", "q:rediscoveralbums" -> HomeSource.RANDOM_ALBUMS
        "q:randomsongs", "q:rediscoversongs" -> HomeSource.RANDOM_SONGS
        "q:favoritesongs" -> HomeSource.FAVOURITE_SONGS
        "q:favoritealbums" -> HomeSource.FAVOURITE_ALBUMS
        "q:favoriteartists" -> HomeSource.FAVOURITE_ARTISTS
        "q:favoriteplaylists" -> HomeSource.PLAYLISTS
        "s:shortcut", "s:shortcut2", "s:shortcut3", "s:shortcut4" -> HomeSource.SHORTCUTS
        else -> null
    }

    fun shortcuts(list: String): List<Shortcut> = list.split(';').mapNotNull {
        when (it.trim()) {
            "library" -> Shortcut.LIBRARY
            "search" -> Shortcut.SEARCH
            "settings" -> Shortcut.SETTINGS
            "playlists" -> Shortcut.PLAYLISTS
            "realalbums", "albums" -> Shortcut.ALBUMS
            "artists", "albumartists" -> Shortcut.ARTISTS
            "genres" -> Shortcut.GENRES
            "favorites", "favourites" -> Shortcut.FAVOURITES
            "offline", "downloads" -> Shortcut.DOWNLOADS
            "songmix", "mix", "radiomix" -> Shortcut.MIX
            "shuffle", "shuffleall" -> Shortcut.SHUFFLE
            else -> null
        }
    }.distinct()

    // --- saved styles -------------------------------------------------------------------------

    private var prefs: SharedPreferences? = null
    private val _nowPlaying = MutableStateFlow<NowPlayingStyle?>(null)
    private val _app = MutableStateFlow<AppStyle?>(null)

    /** The now-playing style in use: the imported one, else the one inside the app style. */
    val nowPlaying: StateFlow<NowPlayingStyle?> = _nowPlaying.asStateFlow()
    val app: StateFlow<AppStyle?> = _app.asStateFlow()

    private var ownNowPlaying: NowPlayingStyle? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("navimuse_symfonium_styles", Context.MODE_PRIVATE)
        prefs = p
        ownNowPlaying = p.getString("nowplaying", null)?.let { runCatching { parseNowPlaying(it) }.getOrNull() }
        _app.value = p.getString("application", null)?.let { runCatching { parseApp(it) }.getOrNull() }
        publish()
    }

    private fun publish() {
        _nowPlaying.value = ownNowPlaying ?: _app.value?.nowPlaying
    }

    /** Saves and applies a style file. Returns its name. */
    fun install(json: String): String {
        return when (typeOf(json)) {
            "nowplaying" -> {
                val s = parseNowPlaying(json)
                prefs?.edit()?.putString("nowplaying", json)?.apply()
                ownNowPlaying = s
                publish()
                s.name
            }
            "application" -> {
                val a = parseApp(json)
                prefs?.edit()?.putString("application", json)?.apply()
                _app.value = a
                publish()
                a.name
            }
            else -> throw StyleFormatException("That isn't a Symfonium style.")
        }
    }

    fun removeNowPlaying() {
        prefs?.edit()?.remove("nowplaying")?.apply()
        ownNowPlaying = null
        publish()
    }

    fun removeApp() {
        prefs?.edit()?.remove("application")?.apply()
        _app.value = null
        publish()
    }

    /** Name of the imported now-playing style (not the one inside an app style). */
    fun ownNowPlayingName(): String? = ownNowPlaying?.name
}
