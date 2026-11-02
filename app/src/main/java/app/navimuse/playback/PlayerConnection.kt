package app.navimuse.playback

import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.navimuse.NaviMuseApp
import app.navimuse.net.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class QueueEntry(val index: Int, val song: Song, val artworkUrl: String?)

data class PlayerUiState(
    val connected: Boolean = false,
    val current: QueueEntry? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val error: String? = null,
    /** "Dolby Atmos", "5.1 surround"… for the audio being decoded right now, if it's special. */
    val formatLabel: String? = null,
    /** What the player is actually decoding right now. */
    val decoded: DecodedFormat? = null,
)

/** The audio the player is decoding: codec, sample rate, channels and bitrate (when known). */
data class DecodedFormat(val mime: String?, val sampleRate: Int, val channels: Int, val bitrate: Int)

/** The screens' handle on the background player. */
class PlayerConnection(private val app: NaviMuseApp) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var pending: ListenableFuture<MediaController>? = null

    /** Commands sent before the connection is ready run as soon as it is. */
    private val waiting = ArrayList<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION))
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = describe(error)) }
        }
    }

    fun connect() {
        if (controller != null || pending != null) return
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        pending = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            pending = null
            if (c == null) return@addListener
            controller = c
            c.addListener(listener)
            refresh(rebuildQueue = true)
            waiting.forEach { it(c) }
            waiting.clear()
        }, ContextCompat.getMainExecutor(app))
    }

    fun disconnect() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        pending?.cancel(true)
        pending = null
        _state.value = PlayerUiState()
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else {
            waiting += action
            connect()
        }
    }

    /** Current position in ms (read on demand by the progress bar). */
    fun positionMs(): Long = controller?.currentPosition ?: 0L
    fun bufferedMs(): Long = controller?.bufferedPosition ?: 0L

    private fun items(songs: List<Song>): List<MediaItem> {
        val api = app.api() ?: return emptyList()
        return songs.map { it.toMediaItem(api, app.bitrateFor(it)) }
    }

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val list = items(songs)
        if (list.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle) list.indices.random() else startIndex.coerceIn(0, list.lastIndex)
            c.setMediaItems(list, start, C.TIME_UNSET)
            c.prepare()
            c.play()
        }
        _state.update { it.copy(error = null) }
    }

    fun playNext(songs: List<Song>) {
        val list = items(songs)
        if (list.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(list)
                c.prepare()
                c.play()
            } else {
                c.addMediaItems(c.currentMediaItemIndex + 1, list)
            }
        }
    }

    fun addToQueue(songs: List<Song>) {
        val list = items(songs)
        if (list.isEmpty()) return
        withController { c ->
            val wasEmpty = c.mediaItemCount == 0
            c.addMediaItems(list)
            if (wasEmpty) {
                c.prepare()
                c.play()
            }
        }
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }

    fun skipTo(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Takes a song out of the play queue (on the phone only; your library isn't touched). */
    fun removeFromQueue(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun stopAndClear() {
        controller?.let {
            it.stop()
            it.clearMediaItems()
        }
        QueueMemory(app).clear()
        _state.update { it.copy(current = null, queue = emptyList(), isPlaying = false) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun refresh(rebuildQueue: Boolean) {
        val c = controller ?: return
        val queue = if (rebuildQueue || _state.value.queue.size != c.mediaItemCount) {
            (0 until c.mediaItemCount).mapNotNull { i ->
                val item = c.getMediaItemAt(i)
                item.toSong()?.let { QueueEntry(i, it, item.mediaMetadata.artworkUri?.toString()) }
            }
        } else {
            _state.value.queue
        }
        val index = c.currentMediaItemIndex
        val currentItem = c.currentMediaItem
        val current = currentItem?.toSong()?.let { QueueEntry(index, it, currentItem.mediaMetadata.artworkUri?.toString()) }
        _state.update {
            it.copy(
                connected = true,
                current = current,
                isPlaying = c.isPlaying,
                buffering = c.playbackState == Player.STATE_BUFFERING,
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                queue = queue,
                durationMs = c.duration.takeIf { d -> d != C.TIME_UNSET && d > 0 } ?: ((current?.song?.duration ?: 0) * 1000L),
                hasNext = c.hasNextMediaItem(),
                formatLabel = decodedOf(c)?.let { f -> Dolby.fromMime(f.mime, f.channels) },
                decoded = decodedOf(c),
            )
        }
    }

    private fun decodedOf(c: MediaController): DecodedFormat? {
        if (!c.isCommandAvailable(Player.COMMAND_GET_TRACKS)) return null
        val group = c.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected } ?: return null
        val index = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: return null
        val f = group.getTrackFormat(index)
        val bitrate = listOf(f.averageBitrate, f.bitrate, f.peakBitrate).firstOrNull { it > 0 } ?: 0
        return DecodedFormat(
            f.sampleMimeType,
            f.sampleRate.takeIf { it > 0 } ?: 0,
            f.channelCount.takeIf { it > 0 } ?: 0,
            bitrate,
        )
    }

    private fun describe(e: PlaybackException): String {
        val song = controller?.currentMediaItem?.toSong()
        val dolby = song?.let { Dolby.labelFor(it) }
        val decoding = e.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
            e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            e.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
        if (dolby != null && decoding && app.settings.value.dolbyOriginal) {
            return "This phone can't decode $dolby. Turn off \"Play Dolby Atmos in its original format\" in Settings and the server will convert it to stereo."
        }
        return describeGeneral(e)
    }

    private fun describeGeneral(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "Can't reach your server to play this song."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The server refused to stream this song."
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        -> "This phone can't play this file's format. Try a lower streaming quality in Settings so the server converts it."
        else -> "Playback stopped: ${e.errorCodeName}"
    }
}
