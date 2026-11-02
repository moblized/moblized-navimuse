package app.navimuse.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.navimuse.BlockedEvent
import app.navimuse.NaviMuseApp
import app.navimuse.data.Settings
import app.navimuse.downloads.Downloads
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
import app.navimuse.playback.Dolby
import app.navimuse.playback.PlayerUiState
import app.navimuse.playback.Streaming
import app.navimuse.ui.theme.CustomTheme
import app.navimuse.ui.theme.CustomThemes
import app.navimuse.ui.theme.ThemeFormatException
import app.navimuse.update.Updater
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_THEME_BYTES = 1024 * 1024

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
        syncDownloads()
        if (settings.value.autoUpdate && !checkedForUpdate) {
            checkedForUpdate = true
            checkForUpdate(manual = false)
        }
    }

    private var checkedForUpdate = false

    // --- offline downloads -------------------------------------------------------------------

    val downloads: StateFlow<Map<String, Downloads.Item>> = Downloads.items

    private fun waitingForWifi(): Boolean = settings.value.downloadWifiOnly && !Streaming.unmetered(app)

    /** Saves songs on the phone for offline play. */
    fun download(songs: List<Song>) {
        val api = app.api() ?: return
        val n = Downloads.add(app, api, songs, app::downloadBitrateFor)
        val what = when (n) {
            0 -> "Already on this phone"
            1 -> "Downloading 1 song"
            else -> "Downloading $n songs"
        }
        say(if (n > 0 && waitingForWifi()) "$what when you're on Wi-Fi" else what)
    }

    /** Deletes the phone's copies. Nothing on the server changes. */
    fun removeDownloads(songs: List<Song>) {
        Downloads.remove(app, songs.map { it.id })
        say(if (songs.size == 1) "Removed from this phone" else "Removed ${songs.size} songs from this phone")
    }

    fun removeAllDownloads() {
        Downloads.removeAll(app)
        say("Removed all downloads from this phone")
    }

    /** Keeps a playlist downloaded, including songs added to it later. */
    fun setPlaylistAutoDownload(playlist: Playlist, songs: List<Song>, on: Boolean) {
        updateSettings {
            it.copy(autoDownloadPlaylists = if (on) it.autoDownloadPlaylists + playlist.id else it.autoDownloadPlaylists - playlist.id)
        }
        if (on) download(songs) else removeDownloads(songs)
    }

    fun setFavouritesAutoDownload(on: Boolean) {
        updateSettings { it.copy(autoDownloadFavourites = on) }
        if (on) syncDownloads(announce = true)
    }

    fun syncDownloads(announce: Boolean = false) {
        viewModelScope.launch {
            val n = runCatching { app.syncAutoDownloads() }.getOrDefault(0)
            if (announce) say(if (n == 0) "Your favourites are already on this phone" else "Downloading $n favourite songs")
        }
    }

    fun refreshDownloadProgress() = Downloads.refreshProgress(app)

    fun downloadBytes(): Long = Downloads.bytesUsed(app)

    // --- app updates -------------------------------------------------------------------------

    val updateStatus: StateFlow<Updater.Status> = Updater.status

    fun checkForUpdate(manual: Boolean) {
        viewModelScope.launch {
            try {
                val r = Updater.checkAndDownload(app)
                if (manual && r == null) say("You have the newest version.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) say(friendly(e))
            }
        }
    }

    fun installUpdate() = Updater.install(app)

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
    fun streamingKbps(song: Song): Int = app.bitrateFor(song)

    // --- Dolby Atmos -------------------------------------------------------------------------

    val dolby: StateFlow<Map<String, String>> = Dolby.known

    /**
     * Finds out once per album whether it's Dolby Atmos (or other Dolby / surround), by reading
     * the start (or end) of one track. The server can't tell an Atmos .m4a from a plain one.
     */
    fun checkDolby(albumId: String, songs: List<Song>) {
        if (Dolby.albumChecked(albumId) || songs.isEmpty()) return
        // Navidrome reports channels (6 for 5.1) but not the codec, so a surround .m4a may be
        // Dolby Atmos, Dolby Digital Plus or plain multichannel AAC: look inside to name it.
        val fromServer = songs.firstNotNullOfOrNull { Dolby.hint(it) }
        val sample = songs.firstOrNull { Dolby.canSniff(it) }
        if (sample == null) {
            Dolby.rememberAlbum(albumId, fromServer)
            return
        }
        viewModelScope.launch {
            runCatching {
                val head = api().peek(sample.id, fromEnd = false)
                Dolby.sniff(head) ?: if (Dolby.needsTail(head)) Dolby.sniff(api().peek(sample.id, fromEnd = true)) else null
            }.onSuccess { Dolby.rememberAlbum(albumId, it ?: fromServer) }
        }
    }

    fun audioSessionId(): Int = app.audioSessionId

    // --- colour themes (Symfonium / Material Theme Builder) ----------------------------------

    val themes: StateFlow<List<CustomTheme>> = CustomThemes.all

    fun useTheme(id: String?) = updateSettings { it.copy(themeId = id) }

    /** A Symfonium icon pack ("<package>/<pack id>"), or null for NaviMuse's own icons. */
    fun useIconPack(id: String?) = updateSettings { it.copy(iconPack = id) }

    fun deleteTheme(theme: CustomTheme) {
        if (settings.value.themeId == theme.id) useTheme(null)
        CustomThemes.delete(theme.id)
        say("Removed \"${theme.name}\"")
    }

    private fun addTheme(theme: CustomTheme) {
        CustomThemes.save(theme)
        useTheme(theme.id)
        say("Using \"${theme.name}\"")
    }

    /** Imports a theme file picked on the phone or opened with NaviMuse. */
    fun importTheme(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, text) = withContext(Dispatchers.IO) {
                    val resolver = app.contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: uri.lastPathSegment.orEmpty()
                    val bytes = resolver.openInputStream(uri)?.use { it.readNBytesCompat(MAX_THEME_BYTES) }
                        ?: throw ThemeFormatException("Couldn't open that file.")
                    name to String(bytes, Charsets.UTF_8)
                }
                addTheme(CustomThemes.parse(text, name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(e.message ?: "Couldn't import that theme.")
            }
        }
    }

    /** Imports a theme from a Symfonium Styles page link or a direct link to a theme file. */
    fun importThemeFromLink(link: String) {
        val url = CustomThemes.downloadUrl(link) ?: run {
            say("That isn't a link to a theme.")
            return
        }
        action("Downloading theme…") {
            val text = withContext(Dispatchers.IO) {
                app.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("The theme link answered ${r.code}.")
                    val bytes = r.body?.byteStream()?.use { it.readNBytesCompat(MAX_THEME_BYTES) } ?: ByteArray(0)
                    String(bytes, Charsets.UTF_8)
                }
            }
            addTheme(CustomThemes.parse(text, url.removeSuffix("/download").substringAfterLast('/')))
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

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
                if (starred && kind == SubsonicClient.StarKind.SONG && settings.value.autoDownloadFavourites) runCatching { app.syncAutoDownloads() }
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
