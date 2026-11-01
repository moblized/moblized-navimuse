package app.navimuse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.navimuse.BlockedEvent
import app.navimuse.NaviMuseApp
import app.navimuse.data.Settings
import app.navimuse.net.Album
import app.navimuse.net.Artist
import app.navimuse.net.AudioMuseLogin
import app.navimuse.net.Lyrics
import app.navimuse.net.Playlist
import app.navimuse.net.PlaylistNames
import app.navimuse.net.ServerInfo
import app.navimuse.net.ServerLogin
import app.navimuse.net.ServerUrl
import app.navimuse.net.Song
import app.navimuse.net.SonicMatch
import app.navimuse.net.SubsonicClient
import app.navimuse.playback.PlayerUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NaviMuseApp

    val settings: StateFlow<Settings> = app.settings
    val login: StateFlow<ServerLogin?> = app.login
    val serverInfo: StateFlow<ServerInfo?> = app.serverInfo
    val audioMuseLogin: StateFlow<AudioMuseLogin?> = app.audioMuseLogin
    val blocked: StateFlow<List<BlockedEvent>> = app.blocked
    val player = app.player
    val playerState: StateFlow<PlayerUiState> = player.state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Bumped whenever playlists change, so lists showing them reload. */
    private val _playlistsVersion = MutableStateFlow(0)
    val playlistsVersion: StateFlow<Int> = _playlistsVersion.asStateFlow()

    /** Hearts changed in this session (song/album/artist id → starred), shown before lists reload. */
    private val _stars = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val stars: StateFlow<Map<String, Boolean>> = _stars.asStateFlow()

    /** Something is being prepared (a mix, a path…), for a small progress line. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    fun api(): SubsonicClient = app.api() ?: throw IllegalStateException("You're not logged in.")
    fun coverUrl(coverArt: String?, size: Int = 600): String? = app.api()?.coverUrl(coverArt, size)
    fun hasAudioMuseDirect(): Boolean = app.audioMuseLogin.value != null

    fun say(message: String) {
        _messages.tryEmit(message)
    }

    private fun action(busyLabel: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (busyLabel != null) _busy.value = busyLabel
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(friendly(e))
            } finally {
                if (busyLabel != null) _busy.value = null
            }
        }
    }

    fun onForeground() {
        if (login.value == null) return
        player.connect()
        if (serverInfo.value == null) refreshServerInfo()
    }

    fun refreshServerInfo() {
        viewModelScope.launch {
            runCatching { api().serverInfo() }.onSuccess { app.setServerInfo(it) }
        }
    }

    // --- account ---------------------------------------------------------------------------

    /** Checks the login with the server and saves it. Returns an error message, or null on success. */
    suspend fun logIn(address: String, username: String, password: String): String? {
        val base = ServerUrl.normalize(address) ?: return "That doesn't look like a server address."
        if (username.isBlank()) return "Enter your Navidrome username."
        if (password.isEmpty()) return "Enter your password."
        val client = app.clientFor(ServerLogin(base, username.trim(), password, "check"))
        return try {
            val info = client.serverInfo()
            app.saveLogin(base, username.trim(), password)
            app.setServerInfo(info)
            player.connect()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            friendly(e)
        }
    }

    fun logOut() {
        app.logout()
    }

    /** Tests and saves the optional AudioMuse-AI connection. Returns an error message, or null. */
    suspend fun saveAudioMuse(address: String, token: String, serverName: String): String? {
        val base = ServerUrl.normalize(address) ?: return "That doesn't look like an address."
        val l = AudioMuseLogin(base, token.trim(), serverName.trim())
        return try {
            app.audioMuseClientFor(l).test()
            app.saveAudioMuse(l)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            friendly(e)
        }
    }

    fun removeAudioMuse() = app.saveAudioMuse(null)

    fun updateSettings(change: (Settings) -> Settings) = app.updateSettings(change)

    // --- playing ---------------------------------------------------------------------------

    fun play(songs: List<Song>, index: Int = 0, shuffle: Boolean = false) {
        if (app.api() == null) return
        player.play(songs, index, shuffle)
    }

    fun playNext(songs: List<Song>) {
        player.playNext(songs)
        say(if (songs.size == 1) "Plays next: ${songs[0].title}" else "${songs.size} songs play next")
    }

    fun addToQueue(songs: List<Song>) {
        player.addToQueue(songs)
        say(if (songs.size == 1) "Added to queue: ${songs[0].title}" else "Added ${songs.size} songs to the queue")
    }

    private fun mixSize() = settings.value.mixSize

    /** AudioMuse-AI Instant Mix: this song, then songs that sound like it. */
    fun instantMix(seed: Song) = action("Making an Instant Mix…") {
        val similar = api().similarSongs(seed.id, mixSize()).filter { it.id != seed.id }
        if (similar.isEmpty()) {
            say("No similar songs came back. Has AudioMuse-AI analysed this song yet?")
            return@action
        }
        play(listOf(seed) + similar)
    }

    fun mixFromAlbum(album: Album) = action("Making an Instant Mix…") {
        val seed = api().album(album.id).songs.randomOrNull() ?: return@action
        val similar = api().similarSongs(seed.id, mixSize()).filter { it.id != seed.id }
        if (similar.isEmpty()) {
            say("No similar songs came back. Has AudioMuse-AI analysed this album yet?")
            return@action
        }
        play(listOf(seed) + similar)
    }

    fun artistRadio(artist: Artist) = action("Starting artist radio…") {
        val songs = api().similarSongsByArtist(artist.id, mixSize())
        if (songs.isEmpty()) say("No songs came back for ${artist.name}'s radio.") else play(songs)
    }

    /** An Instant Mix from a random song in your library. */
    fun surpriseMix() = action("Making a surprise mix…") {
        val seed = api().randomSongs(1).firstOrNull() ?: return@action
        val similar = api().similarSongs(seed.id, mixSize()).filter { it.id != seed.id }
        play(listOf(seed) + similar)
    }

    fun shuffleAll() = action("Shuffling your library…") {
        play(api().randomSongs(100))
    }

    suspend fun sonicSimilar(song: Song): List<SonicMatch> = api().sonicSimilar(song.id, 40)

    suspend fun sonicPath(start: Song, end: Song, length: Int): List<Song> =
        api().sonicPath(start.id, end.id, length).map { it.song }

    /** AudioMuse-AI mood search, with full song details from Navidrome. */
    suspend fun moodSearch(query: String): List<Song> {
        val am = app.audioMuse() ?: throw IllegalStateException("Connect AudioMuse-AI in Settings first.")
        val hits = am.moodSearch(query, 40)
        return api().songs(hits.map { it.itemId })
    }

    suspend fun searchSongs(query: String): List<Song> = api().search(query, songs = 25, albums = 0, artists = 0).songs

    /** Bitrate the server converts to on the phone's current network (0 = the original file). */
    fun streamingKbps(): Int = app.currentMaxBitRate()

    suspend fun lyrics(song: Song): Lyrics? = api().lyrics(song.id)

    /** Shuffles every song in a genre. */
    fun playGenre(genre: String) = action("Shuffling $genre…") {
        val songs = api().songsByGenre(genre, 300)
        if (songs.isEmpty()) say("No songs tagged $genre.") else play(songs, shuffle = true)
    }

    // --- favourites ------------------------------------------------------------------------

    fun isStarred(id: String, fallback: Boolean): Boolean = _stars.value[id] ?: fallback

    fun setStar(id: String, kind: SubsonicClient.StarKind, starred: Boolean) {
        _stars.update { it + (id to starred) }
        action {
            try {
                if (starred) api().star(id, kind) else api().unstar(id, kind)
            } catch (e: Exception) {
                _stars.update { it + (id to !starred) }
                throw e
            }
        }
    }

    // --- playlists (add-only) --------------------------------------------------------------

    fun savePlaylist(name: String, songs: List<Song>) = action("Saving playlist…") {
        if (songs.isEmpty()) {
            say("There's nothing to save yet.")
            return@action
        }
        val safe = PlaylistNames.safe(name)
        api().createPlaylist(safe, songs.map { it.id })
        _playlistsVersion.update { it + 1 }
        say(
            if (safe != name.trim()) "Saved as \"$safe\" (names ending in _automatic get deleted by AudioMuse-AI)"
            else "Saved playlist \"$safe\"",
        )
    }

    fun addToPlaylist(playlist: Playlist, songs: List<Song>) = action {
        api().addToPlaylist(playlist.id, songs.map { it.id })
        _playlistsVersion.update { it + 1 }
        say(if (songs.size == 1) "Added to ${playlist.name}" else "Added ${songs.size} songs to ${playlist.name}")
    }

    /** Copies an AudioMuse-AI "_automatic" playlist to a normal one, so its next run can't erase it. */
    fun keepCopy(playlist: Playlist) = action("Keeping a copy…") {
        val name = keepOne(playlist, api().playlists().map { it.name })
        _playlistsVersion.update { it + 1 }
        if (name != null) say("Kept as \"$name\". AudioMuse-AI won't touch it.")
    }

    fun keepAll(playlists: List<Playlist>) = action("Keeping copies…") {
        val names = api().playlists().map { it.name }.toMutableList()
        var kept = 0
        for (p in playlists.filter { it.isAudioMuseManaged }) {
            keepOne(p, names)?.let {
                names += it
                kept++
            }
        }
        _playlistsVersion.update { it + 1 }
        say("Kept $kept AudioMuse-AI playlist${if (kept == 1) "" else "s"}.")
    }

    private suspend fun keepOne(playlist: Playlist, existingNames: List<String>): String? {
        val songs = api().playlist(playlist.id).songs
        if (songs.isEmpty()) {
            say("${playlist.name} is empty, so there's nothing to keep.")
            return null
        }
        val name = PlaylistNames.keptCopyName(playlist.name, existingNames)
        api().createPlaylist(name, songs.map { it.id })
        return name
    }

    suspend fun ownPlaylists(): List<Playlist> {
        val me = login.value?.username
        return api().playlists().filter { (it.owner == null || it.owner == me) && !it.isAudioMuseManaged }
    }
}
