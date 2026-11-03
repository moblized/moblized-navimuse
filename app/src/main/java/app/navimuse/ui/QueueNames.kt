package app.navimuse.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A name for a playlist saved from the queue, taken from where it's playing from:
 * "Instant Mix: Song", "Road trip copy" for a playlist, or "Queue Nov 2" when it isn't known.
 */
fun queuePlaylistName(source: AppViewModel.PlaySource?, today: Date = Date(), locale: Locale = Locale.getDefault()): String = when {
    source == null || source.name.isBlank() -> "Queue " + SimpleDateFormat("MMM d", locale).format(today)
    source.route is Route.PlaylistPage -> "${source.name} copy"
    else -> source.name
}
