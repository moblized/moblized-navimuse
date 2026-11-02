package app.navimuse.ui.icons

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** The Symfonium icon pack in use, or null for NaviMuse's own icons. */
val LocalIconPack = staticCompositionLocalOf<LoadedIconPack?> { null }

/**
 * Which Symfonium icon key stands in for each of NaviMuse's icons (by the Material icon's name).
 * Keys come from Symfonium's icon-pack contract (docs/ICON_KEYS.md in its template).
 */
object IconKeys {
    private val byName = mapOf(
        "Filled.PlayArrow" to "playback.play",
        "Filled.Pause" to "playback.pause",
        "Filled.SkipNext" to "playback.next",
        "Filled.SkipPrevious" to "playback.previous",
        "Filled.Shuffle" to "playback.shuffle",
        "Filled.Repeat" to "playback.repeat",
        "Filled.RepeatOne" to "playback.repeat.one",
        "AutoMirrored.Filled.QueueMusic" to "playback.queue",
        "Filled.FormatQuote" to "playback.lyrics",
        "Filled.AutoAwesome" to "playback.radio.mix",
        "Outlined.AutoAwesome" to "playback.radio.mix",
        "Filled.Radio" to "playback.radio.mix",
        "Filled.GraphicEq" to "playback.current.track",
        "Filled.Favorite" to "action.favorite.filled",
        "Filled.FavoriteBorder" to "action.favorite",
        "Filled.MoreVert" to "action.more",
        "Filled.MoreHoriz" to "action.more.horizontal",
        "Filled.FileDownload" to "action.download",
        "Filled.OfflinePin" to "status.offline.available",
        "Filled.Close" to "action.close",
        "Filled.Check" to "action.check",
        "Filled.Save" to "action.save",
        "Filled.ContentCopy" to "action.copy",
        "Filled.Info" to "action.info",
        "Filled.Refresh" to "action.refresh",
        "Filled.RemoveCircleOutline" to "action.remove",
        "Filled.Visibility" to "action.visibility",
        "Filled.FileOpen" to "action.import",
        "Filled.Search" to "action.search",
        "AutoMirrored.Filled.PlaylistAdd" to "action.playlist.add",
        "AutoMirrored.Filled.Sort" to "action.sort",
        "AutoMirrored.Filled.ArrowBack" to "navigation.back",
        "Filled.Settings" to "navigation.settings",
        "Outlined.Home" to "navigation.home",
        "Filled.Home" to "navigation.home.selected",
        "Outlined.Search" to "navigation.search",
        "Outlined.LibraryMusic" to "navigation.library",
        "Filled.LibraryMusic" to "navigation.library.selected",
        "Filled.Album" to "media.album",
        "Filled.Person" to "media.artist",
        "Filled.MusicNote" to "media.track",
        "Filled.Mood" to "rating.sentiment.satisfied",
        "Filled.ChevronRight" to "interface.chevron.right",
        "Filled.KeyboardArrowDown" to "interface.collapse.down",
        "Filled.KeyboardArrowUp" to "interface.collapse.up",
        "Filled.Palette" to "interface.palette",
        "Filled.Shield" to "system.shield",
        "Filled.Warning" to "status.warning",
        "Filled.CheckCircle" to "status.check.circle",
    )

    fun of(vector: ImageVector): String? = byName[vector.name]
}

/**
 * Draws an icon: the selected Symfonium icon pack's version when it has one, otherwise
 * NaviMuse's own. Same parameters as Material's Icon, plus an explicit [key] for icons whose
 * Symfonium key depends on state (a selected tab, shuffle switched on…).
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    key: String? = null,
) {
    val pack = LocalIconPack.current
    val bitmap = pack?.let { p -> (key ?: IconKeys.of(imageVector))?.let { p.bitmap(it) } }
    if (bitmap != null) {
        androidx.compose.material3.Icon(bitmap, contentDescription, modifier, tint)
    } else {
        androidx.compose.material3.Icon(imageVector, contentDescription, modifier, tint)
    }
}
