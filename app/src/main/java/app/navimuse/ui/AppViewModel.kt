package app.navimuse.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.navimuse.BlockedEvent
import app.navimuse.NaviMuseApp
import app.navimuse.data.Accent
import app.navimuse.data.Blocklist
import app.navimuse.playback.AudioCache
import app.navimuse.net.Credit
import app.navimuse.data.Look
import app.navimuse.data.Mood
import app.navimuse.playback.SongTrouble
import app.navimuse.playback.StreamCheck
import app.navimuse.data.Duplicates
import app.navimuse.data.GhostSongs
import app.navimuse.data.SongLists
import app.navimuse.data.SongMatch
import app.navimuse.data.TitleWords
import app.navimuse.data.WantedSong
import app.navimuse.net.MusicShare
import app.navimuse.net.NavidromeNative
import app.navimuse.net.ShareLogin
import app.navimuse.net.TidalClient
import app.navimuse.net.TrashItem
import app.navimuse.net.NoSuchShare
import app.navimuse.net.ShareGuess
import app.navimuse.data.TrashQueue
import app.navimuse.data.DeletedPlaylists
import app.navimuse.trash.TrashMover
import app.navimuse.trash.TrashQueueWorker
import app.navimuse.net.TidalLinks
import app.navimuse.net.TidalLogin
import app.navimuse.data.Settings
import app.navimuse.downloads.Downloads
import app.navimuse.net.Album
import app.navimuse.net.Artist
import app.navimuse.net.AudioMuseLogin
import app.navimuse.net.Lyrics
import app.navimuse.net.Playlist
import app.navimuse.net.PlaylistNames
import app.navimuse.net.SafetyGuard
import app.navimuse.net.ServerInfo
import app.navimuse.net.ServerLogin
import app.navimuse.net.ServerUrl
import app.navimuse.net.Song
import app.navimuse.net.SonicMatch
import app.navimuse.net.SubsonicClient
import app.navimuse.playback.Dolby
import app.navimuse.playback.PlayerUiState
import app.navimuse.playback.Streaming
import app.navimuse.ui.symfonium.AppStyle
import app.navimuse.ui.symfonium.NowPlayingStyle
import app.navimuse.ui.symfonium.SymfoniumStyles
import app.navimuse.ui.theme.CustomTheme
import app.navimuse.ui.theme.CustomThemes
import app.navimuse.ui.theme.ThemeFormatException
import app.navimuse.update.Updater
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    /** A message with a button, such as Undo. */
    data class Note(val text: String, val action: String, val onAction: () -> Unit)

    private val _notes = MutableSharedFlow<Note>(extraBufferCapacity = 4)
    val notes: SharedFlow<Note> = _notes.asSharedFlow()

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
        // Ghost songs whose files came back stop being ghosts (once a session, quietly).
        if (!recheckedGhosts) {
            recheckedGhosts = true
            recheckGhosts(quiet = true)
        }
        emptyOldTrash()
        moveWaitingSongs()
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

    /** Where the queue came from, for "Playing from" on the playing screen. */
    data class PlaySource(val name: String, val route: Route? = null)

    private val _source = MutableStateFlow<PlaySource?>(null)
    val source: StateFlow<PlaySource?> = _source.asStateFlow()

    fun play(songs: List<Song>, index: Int = 0, shuffle: Boolean = false, from: PlaySource? = null) {
        if (app.api() == null) return
        // Ghost songs (see GhostSongs) are left out; the one tapped can't be one.
        val picked = songs.getOrNull(index)
        if (picked != null && GhostSongs.isGhost(picked)) {
            say("\"${picked.title}\" is a ghost song: Navidrome lists it but can't find its file. See Settings → Playback → Ghost songs.")
            return
        }
        val playable = GhostSongs.playable(songs)
        if (playable.isEmpty()) return
        _source.value = from
        player.play(playable, picked?.let { p -> playable.indexOfFirst { it.id == p.id } }?.coerceAtLeast(0) ?: 0, shuffle)
    }

    fun playNext(songs: List<Song>) {
        @Suppress("NAME_SHADOWING")
        val songs = GhostSongs.playable(songs)
        if (songs.isEmpty()) return
        player.playNext(songs)
        say(if (songs.size == 1) "Plays next: ${songs[0].title}" else "${songs.size} songs play next")
    }

    fun addToQueue(songs: List<Song>) {
        @Suppress("NAME_SHADOWING")
        val songs = GhostSongs.playable(songs)
        if (songs.isEmpty()) return
        player.addToQueue(songs)
        say(if (songs.size == 1) "Added to queue: ${songs[0].title}" else "Added ${songs.size} songs to the queue")
    }

    private fun mixSize() = settings.value.mixSize

    /** Instant Mixes made this session, so going back to one shows the same songs. */
    private val mixes = HashMap<String, Mix>()

    /**
     * The Instant Mix for [seed]: the song it starts from, then songs that sound like it. Made once
     * and kept (ask for [fresh] to make a new one). Nothing plays; the mix page has a Play button.
     */
    suspend fun mix(seed: MixSeed, fresh: Boolean = false): Mix {
        if (!fresh) synchronized(mixes) { mixes[seed.key] }?.let { return it }
        val first = when (seed) {
            is MixSeed.FromSong -> seed.song
            is MixSeed.FromAlbum -> Blocklist.allowed(api().album(seed.album.id).songs).randomOrNull()
                ?: throw IllegalStateException("This album has no songs to start a mix from.")
            is MixSeed.Surprise -> Blocklist.allowed(api().randomSongs(5)).firstOrNull()
                ?: throw IllegalStateException("Your library has no songs to start a mix from.")
        }
        val similar = Blocklist.allowed(api().similarSongs(first.id, mixSize())).filter { it.id != first.id }
        val mix = Mix(seed, first, listOf(first) + similar)
        synchronized(mixes) { mixes[seed.key] = mix }
        return mix
    }

    /** Plays a mix from its page. */
    fun playMix(mix: Mix, index: Int = 0, shuffle: Boolean = false) =
        play(mix.songs, index, shuffle, from = PlaySource(mix.name, Route.MixPage(mix.seed)))

    fun artistRadio(artist: Artist) = action("Starting artist radio…") {
        val songs = Blocklist.allowed(api().similarSongsByArtist(artist.id, mixSize()))
        if (songs.isEmpty()) say("No songs came back for ${artist.name}'s radio.") else play(songs, from = PlaySource("${artist.name} radio", Route.ArtistPage(artist.id)))
    }

    fun shuffleAll() = action("Shuffling your library…") {
        play(Blocklist.allowed(api().randomSongs(100)), from = PlaySource("Your library"))
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

    /** Songs for a mood in Discover, and where they came from. */
    data class MoodMix(val songs: List<Song>, val note: String)

    /**
     * A fresh mix for [mood]: by how songs sound when AudioMuse-AI is connected directly,
     * otherwise random songs from the library's genres that fit the mood.
     */
    suspend fun moodMix(mood: Mood): MoodMix {
        val am = app.audioMuse()
        if (am != null) {
            try {
                // Ask for more than needed and pick from them, so "New mix" isn't the same list again.
                val hits = am.moodSearch(mood.query, 120).shuffled().take(60)
                val songs = Blocklist.allowed(api().songs(hits.map { it.itemId }))
                if (songs.isNotEmpty()) return MoodMix(songs, "Picked by how they sound, with AudioMuse-AI")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall back to genres below.
            }
        }
        val fitting = Mood.matchGenres(mood, api().genres().filter { it.songCount > 0 }.map { it.name })
        if (fitting.isEmpty()) {
            return MoodMix(
                emptyList(),
                "None of your genres fit this mood. Connect AudioMuse-AI in Settings to pick songs by how they sound.",
            )
        }
        // A few of the best-fitting genres, mixed up a little so each mix differs.
        val chosen = (fitting.take(4) + fitting.drop(4).shuffled().take(2)).shuffled().take(5)
        val songs = coroutineScope {
            chosen.map { g -> async { runCatching { api().randomSongs(20, genre = g) }.getOrDefault(emptyList()) } }.awaitAll()
        }.flatten().distinctBy { it.id }.shuffled()
        return MoodMix(Blocklist.allowed(songs).take(60), "From your genres: " + chosen.joinToString(", "))
    }

    /** Songs for a playlist made from its title, and in plain words where they came from. */
    data class TitleMix(val songs: List<Song>, val note: String)

    /**
     * Fills a new playlist from its title alone, like Tidal does: "Gym", "Rainy Sunday", "Late
     * night drive", "Skillet and friends". Artists the title names bring their radio. The rest
     * comes from AudioMuse-AI's mood search, given the title as it is, when it's connected;
     * otherwise from the genres the title names or the moods its words point to.
     */
    suspend fun titleMix(title: String, size: Int = 50): TitleMix = coroutineScope {
        val t = title.trim()
        // Artists named in the title: look each word up, then keep the names the title really says.
        val lookups = TitleWords.words(t).trim().split(" ").filter { it.length >= 3 }.distinct().take(5)
        val found = lookups.map { w ->
            async { runCatching { api().search(w, songs = 0, albums = 0, artists = 6).artists }.getOrDefault(emptyList()) }
        }.awaitAll().flatten().distinctBy { it.id }
        val artists = TitleWords.artistsIn(t, found.map { it.name }).mapNotNull { n -> found.firstOrNull { it.name == n } }.take(3)
        val fromArtists = artists.map { a ->
            async { runCatching { api().similarSongsByArtist(a.id, 25) }.getOrDefault(emptyList()) }
        }

        // The sound of it: AudioMuse-AI's mood search reads the title as it is.
        var how: String? = null
        var bySound = emptyList<Song>()
        app.audioMuse()?.let { am ->
            try {
                val hits = am.moodSearch(t, 120).shuffled().take(size)
                bySound = api().songs(hits.map { it.itemId })
                if (bySound.isNotEmpty()) how = "picked by how they sound, with AudioMuse-AI"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Read the words instead, below.
            }
        }
        // Without it: genres the title names, then genres that fit the moods its words point to.
        var byGenre = emptyList<Song>()
        if (bySound.isEmpty()) {
            val genres = runCatching { api().genres().filter { it.songCount > 0 }.map { it.name } }.getOrDefault(emptyList())
            val named = TitleWords.genresIn(t, genres)
            val moods = TitleWords.moodsIn(t)
            val fitting = (named + moods.flatMap { Mood.matchGenres(it, genres).take(4) }).distinct().take(6)
            if (fitting.isNotEmpty()) {
                byGenre = fitting.map { g -> async { runCatching { api().randomSongs(20, genre = g) }.getOrDefault(emptyList()) } }
                    .awaitAll().flatten()
                how = when {
                    moods.isNotEmpty() && named.isEmpty() -> "for ${moods.take(2).joinToString(" and ") { it.label.lowercase() }}, from your genres: ${fitting.joinToString(", ")}"
                    else -> "from your genres: ${fitting.joinToString(", ")}"
                }
            }
        }
        val radio = fromArtists.awaitAll().flatten()
        // The artists' songs and the rest, mixed together.
        val songs = Blocklist.allowed((radio + bySound + byGenre).distinctBy { it.id }.shuffled()).take(size)
        val parts = listOfNotNull(
            artists.takeIf { it.isNotEmpty() }?.let { "built around " + it.joinToString(" and ") { a -> a.name } },
            how,
        )
        val note = when {
            songs.isEmpty() && app.audioMuse() == null ->
                "Nothing in your library matched \"$t\". Try a mood (chill, gym, late night), a genre or an artist, " +
                    "or connect AudioMuse-AI in Settings so any words work."
            songs.isEmpty() -> "Nothing in your library matched \"$t\". Try other words: a mood, a genre or an artist."
            else -> "${songs.size} songs " + parts.joinToString(", ") + "."
        }
        TitleMix(songs, note)
    }

    // --- ghost songs ------------------------------------------------------------------------

    /** A look through the whole library for ghost songs, while it runs. */
    data class GhostScan(val checked: Int, val found: Int, val done: Boolean = false, val error: String? = null)

    private val _ghostScan = MutableStateFlow<GhostScan?>(null)
    val ghostScan: StateFlow<GhostScan?> = _ghostScan.asStateFlow()
    private var ghostJob: Job? = null
    private var recheckedGhosts = false

    /** What the server sends for song [id]'s original file: one byte of audio is enough to know it's there. */
    private suspend fun probe(id: String): StreamCheck = StreamCheck.run(app.http, api().streamUrl(id, 0), range = 1)

    /**
     * Looks at every song in the library for ghost songs, a few at a time, by asking for the
     * first byte of each file. Read-only, and no play is reported to Navidrome.
     */
    fun lookForGhosts() {
        if (ghostJob?.isActive == true) return
        ghostJob = viewModelScope.launch {
            var checked = 0
            var found = 0
            _ghostScan.value = GhostScan(0, 0)
            try {
                var offset = 0
                while (true) {
                    val page = api().allSongs(offset, 500)
                    if (page.isEmpty()) break
                    val ghostly = ArrayList<Pair<Song, String>>()
                    val playing = ArrayList<String>()
                    page.chunked(6).forEach { batch ->
                        coroutineScope {
                            batch.map { s -> async { s to runCatching { probe(s.id) }.getOrDefault(StreamCheck.Unreachable) } }.awaitAll()
                        }.forEach { (s, check) ->
                            if (check.ghostly()) {
                                ghostly += s to SongTrouble.reason(check)
                                found++
                            } else if (check == StreamCheck.Audio) {
                                playing += s.id
                            }
                        }
                        checked += batch.size
                        _ghostScan.value = GhostScan(checked, found)
                    }
                    // A page at a time: one save each, not one per song.
                    GhostSongs.markAll(ghostly)
                    GhostSongs.clear(playing)
                    if (page.size < 500) break
                    offset += page.size
                }
                _ghostScan.value = GhostScan(checked, found, done = true)
            } catch (e: CancellationException) {
                _ghostScan.value = GhostScan(checked, found, done = true, error = "Stopped.")
                throw e
            } catch (e: Exception) {
                _ghostScan.value = GhostScan(checked, found, done = true, error = friendly(e))
            }
        }
    }

    fun stopLookingForGhosts() {
        ghostJob?.cancel()
    }

    /**
     * Tries the ghost songs again; any that play now (the file came back, or Navidrome was
     * rescanned) stop being ghosts. [quiet] for the check made when the app opens.
     */
    fun recheckGhosts(quiet: Boolean = false) {
        // Not the ones waiting to move to the Trash: they're hidden on purpose.
        val all = GhostSongs.all.value.values.filterNot { TrashQueue.has(it.id) }
        // Deleted ones: forgotten once Navidrome stops listing them.
        val deleted = all.filter { it.removed }.map { it.id }.let { if (quiet) it.shuffled().take(QUIET_CHECKS) else it }
        // When the app opens, a handful at random: a big library can have thousands.
        val ghosts = all.filterNot { it.removed }.map { it.id }.let { if (quiet) it.shuffled().take(QUIET_CHECKS) else it }
        if (deleted.isNotEmpty()) forgetDropped(deleted)
        if (ghosts.isEmpty()) {
            if (!quiet) say("No ghost songs.")
            return
        }
        viewModelScope.launch {
            val playing = ArrayList<String>()
            ghosts.chunked(6).forEach { batch ->
                coroutineScope { batch.map { id -> async { id to runCatching { probe(id) }.getOrNull() } }.awaitAll() }
                    .forEach { (id, check) -> if (check == StreamCheck.Audio) playing += id }
            }
            GhostSongs.clear(playing)
            val back = playing.size
            if (!quiet) say(if (back == 0) "Still ghosts, all ${ghosts.size}." else "$back song${if (back == 1) " plays" else "s play"} again.")
        }
    }

    /** Forgets deleted ghost songs that Navidrome doesn't list any more. */
    private fun forgetDropped(ids: List<String>) {
        val login = app.login.value ?: return
        viewModelScope.launch {
            val native = NavidromeNative(app.http, login)
            val dropped = ArrayList<String>()
            ids.chunked(8).forEach { batch ->
                coroutineScope { batch.map { id -> async { id to runCatching { native.lookup(id) } } }.awaitAll() }
                    .forEach { (id, r) -> if (r.isSuccess && r.getOrNull().let { it == null || it.missing }) dropped += id }
            }
            GhostSongs.clear(dropped)
        }
    }

    private val _ghostRemoval = MutableStateFlow<TrashMover.GhostRemoval?>(null)

    /** Deleting ghost songs, while it runs. */
    val ghostRemoval: StateFlow<TrashMover.GhostRemoval?> = _ghostRemoval.asStateFlow()
    private var ghostRemovalJob: Job? = null

    /**
     * Deletes ghost songs [ids] (see TrashMover.removeGhosts): Navidrome's quick scan drops the
     * ones whose files are gone; one it still lists that still won't play has its file moved to
     * the Trash on the NAS.
     */
    fun deleteGhosts(ids: Collection<String>) {
        if (ghostRemovalJob?.isActive == true) return
        val ghosts = ids.mapNotNull { GhostSongs.all.value[it] }
        if (ghosts.isEmpty()) return
        _ghostRemoval.value = TrashMover.GhostRemoval("Starting…")
        ghostRemovalJob = viewModelScope.launch {
            try {
                val result = TrashMover.removeGhosts(app, ghosts) { _ghostRemoval.value = it }
                _duplicates.update { s ->
                    s?.copy(
                        groups = s.groups.mapNotNull { g ->
                            val left = g.songs.filterNot { it.id in result.handled }
                            if (left.size < 2) null else g.copy(songs = left, keep = g.keep?.takeIf { k -> left.any { it.id == k } })
                        },
                    )
                }
                say(result.summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(friendly(e))
            } finally {
                _ghostRemoval.value = null
            }
        }
    }

    /** A check that means the song's file isn't there (or can't be read): a ghost song. */
    private fun StreamCheck.ghostly(): Boolean =
        this == StreamCheck.Empty || (this is StreamCheck.Refused && songOnly)

    // --- duplicates, and moving songs to the Trash on the NAS -------------------------------

    val shareLogin: StateFlow<ShareLogin?> get() = app.shareLogin

    fun saveShare(login: ShareLogin?) {
        app.saveShare(login)
        say(if (login == null) "Music folder removed" else "Music folder saved")
    }

    /** What Test found: the music folder that works (maybe tidied from what you typed), and where. */
    data class ShareTest(val login: ShareLogin, val message: String)

    /**
     * Tries [entered]: finds one of your songs' files on the share, and says where. What you typed
     * is read kindly (the NAS address in Share, the whole path in Folder): the share and folder
     * splits it could mean are tried in turn, and the one that works is saved.
     */
    suspend fun testShare(entered: ShareLogin): ShareTest {
        val guesses = ShareGuess.guesses(entered)
        if (guesses.isEmpty()) throw IllegalStateException("Fill in the NAS address and Share (the share's name on your NAS, like \"media\").")
        val host = guesses[0].server
        if (!TrashMover.reachable(host)) throw IllegalStateException("Can't reach $host from here. Test this on your home Wi-Fi.")
        // A few songs that play, to look for: not a ghost song, whose file is gone (that would
        // look like the wrong folder).
        val native = NavidromeNative(app.http, app.login.value ?: throw IllegalStateException("Not signed in."))
        val files = ArrayList<NavidromeNative.SongFile>()
        for (s in api().allSongs(0, 50).filterNot { GhostSongs.isGhost(it.id) }) {
            if (files.size >= TEST_SONGS) break
            runCatching { native.lookup(s.id) }.getOrNull()?.takeIf { !it.missing }?.let { files += it }
        }
        val file = files.firstOrNull() ?: throw IllegalStateException("Couldn't find a song in your library to look for.")
        val missing = ArrayList<String>()
        var opened: ShareLogin? = null
        for (g in guesses) {
            val found = try {
                withContext(Dispatchers.IO) { MusicShare(g).use { ms -> files.firstNotNullOfOrNull { ms.locate(it.path) } } }
            } catch (e: NoSuchShare) {
                missing += e.share
                continue
            }
            if (found != null) {
                app.saveShare(g)
                val tidied = g.share != entered.share.trim() || g.folder != entered.folder.trim().trim('/', '\\') || g.server != entered.server.trim()
                val where = if (g.folder.isEmpty()) "Share \"${g.share}\" (the whole share)" else "Share \"${g.share}\", Folder \"${g.folder}\""
                return ShareTest(g, "Works: found $found." + if (tidied) " Set to $where and saved." else " Saved.")
            }
            if (opened == null) opened = g
        }
        opened?.let {
            throw IllegalStateException(
                "Opened the share \"${it.share}\", but \"${file.path}\" isn't in it. Set Folder to the one that holds the music Navidrome reads.",
            )
        }
        throw IllegalStateException(
            "There's no share called " + missing.distinct().joinToString(" or ") { "\"$it\"" } + " on $host. " +
                "Share is the share's name as your NAS lists it (TrueNAS: Shares → Windows (SMB) Shares), not the NAS address.",
        )
    }

    private val dupPrefs by lazy { app.getSharedPreferences("navimuse_duplicates", android.content.Context.MODE_PRIVATE) }
    private fun dismissedDuplicates(): Set<String> = dupPrefs.getStringSet("dismissed", emptySet()).orEmpty()

    /** A look through the library for duplicates, and what it found. */
    data class DuplicateScan(val songs: Int, val groups: List<Duplicates.Group>, val done: Boolean, val error: String? = null)

    private val _duplicates = MutableStateFlow<DuplicateScan?>(null)
    val duplicates: StateFlow<DuplicateScan?> = _duplicates.asStateFlow()
    private var duplicateJob: Job? = null

    /** Reads every song in the library and groups the same song found more than once. */
    fun lookForDuplicates() {
        if (duplicateJob?.isActive == true) return
        duplicateJob = viewModelScope.launch {
            val all = ArrayList<Song>()
            _duplicates.value = DuplicateScan(0, emptyList(), done = false)
            try {
                var offset = 0
                while (true) {
                    val page = api().allSongs(offset, 500)
                    all += page
                    _duplicates.value = DuplicateScan(all.size, emptyList(), done = false)
                    if (page.size < 500) break
                    offset += page.size
                }
                val groups = withContext(Dispatchers.Default) { Duplicates.find(all, GhostSongs::isGhost, dismissedDuplicates()) }
                _duplicates.value = DuplicateScan(all.size, groups, done = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _duplicates.value = DuplicateScan(all.size, emptyList(), done = true, error = friendly(e))
            }
        }
    }

    /** Group [key] isn't really duplicates: never show it again. */
    fun notDuplicates(key: String) {
        dupPrefs.edit().putStringSet("dismissed", dismissedDuplicates() + key).apply()
        _duplicates.update { s -> s?.copy(groups = s.groups.filterNot { it.key == key }) }
    }

    /**
     * Moves [song]'s file to the Trash folder in your music folder on the NAS (nothing is deleted;
     * Put back undoes it). NaviMuse stops showing it at once, and asks Navidrome for a quick scan
     * so it drops off Navidrome's lists too (if you're an admin; otherwise the next scan does it).
     * Away from home, where the NAS can't be reached, it waits (hidden) and moves next time the
     * phone is on home Wi-Fi.
     */
    fun moveToTrash(song: Song) {
        viewModelScope.launch {
            try {
                val outcome = TrashMover.trashOrQueue(app, song)
                _duplicates.update { s ->
                    s?.copy(
                        groups = s.groups.mapNotNull { g ->
                            val left = g.songs.filterNot { it.id == song.id }
                            if (left.size < 2) null else g.copy(songs = left, keep = g.keep?.takeIf { it != song.id })
                        },
                    )
                }
                say(
                    when (outcome) {
                        is TrashMover.Outcome.Moved ->
                            "Moved \"${song.title}\" to the Trash." + if (outcome.scanned) "" else " Navidrome drops it at its next scan."
                        TrashMover.Outcome.Queued ->
                            "Can't reach your NAS from here, so \"${song.title}\" moves to the Trash next time you're on home Wi-Fi. It's hidden until then."
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(friendly(e))
            }
        }
    }

    /** Songs deleted away from home, waiting for the NAS. */
    val trashQueue: StateFlow<List<TrashQueue.Entry>> get() = TrashQueue.all

    /** Keeps a song that was waiting to move to the Trash: it's back in your lists. */
    fun keepWaitingSong(entry: TrashQueue.Entry) {
        TrashQueue.remove(entry.songId)
        GhostSongs.clear(entry.songId)
        TrashQueueWorker.sync(app)
        say("Kept \"${entry.title}\"")
    }

    /** Moves the songs waiting for home, if the NAS answers now (when the app opens). */
    private fun moveWaitingSongs() {
        if (TrashQueue.waiting().isEmpty()) return
        viewModelScope.launch {
            val run = runCatching { TrashMover.runQueue(app, retryProblems = false) }.getOrNull() ?: return@launch
            TrashQueueWorker.sync(app)
            when (run.moved.size) {
                0 -> {}
                1 -> say("Back home: moved \"${run.moved[0].title}\" to the Trash.")
                else -> say("Back home: moved ${run.moved.size} songs to the Trash.")
            }
        }
    }

    /**
     * What's in the Trash on the NAS (after moving the songs waiting for home, and emptying what's
     * been there 30 days, if that's on).
     */
    suspend fun trash(): List<TrashItem> {
        val share = app.shareLogin.value ?: return emptyList()
        if (!TrashMover.reachable(share.server)) {
            throw IllegalStateException("Can't reach your NAS from here. The Trash opens on your home Wi-Fi.")
        }
        TrashMover.runQueue(app, retryProblems = true)
        TrashQueueWorker.sync(app)
        return withContext(Dispatchers.IO) {
            MusicShare(share).use { ms ->
                if (settings.value.trashAutoEmpty) runCatching { ms.emptyOld() }
                ms.trash()
            }
        }
    }

    private var emptiedTrash = false

    /** Deletes for good the songs 30 days in the Trash: once a session, quietly, when it's on. */
    private fun emptyOldTrash() {
        if (emptiedTrash || !settings.value.trashAutoEmpty) return
        val share = app.shareLogin.value ?: return
        emptiedTrash = true
        viewModelScope.launch(Dispatchers.IO) {
            // Away from home: try again next time the app opens.
            if (!TrashMover.reachable(share.server)) {
                emptiedTrash = false
                return@launch
            }
            runCatching { MusicShare(share).use { it.emptyOld() } }
        }
    }

    /** Puts a song back from the Trash where it was, and has Navidrome pick it up again. */
    suspend fun putBack(item: TrashItem) {
        val share = app.shareLogin.value ?: throw IllegalStateException("No music folder set up.")
        val back = withContext(Dispatchers.IO) { MusicShare(share).use { it.putBack(item.path) } }
        runCatching {
            val native = NavidromeNative(app.http, app.login.value ?: return@runCatching)
            if (native.admin()) api().startQuickScan()
        }
        // It's where Navidrome expects it again: it plays, so it's no ghost.
        recheckGhosts(quiet = true)
        say("Put back: $back")
    }

    // --- importing playlists (TIDAL links, song lists) -------------------------------------

    val tidalLogin: StateFlow<TidalLogin?> get() = app.tidalLogin

    fun saveTidal(login: TidalLogin?) {
        app.saveTidal(login)
        say(if (login == null) "TIDAL removed" else "TIDAL saved")
    }

    /** Where an import's songs come from: a TIDAL playlist link, or a list (pasted or a file). */
    sealed interface ImportSource {
        data class Tidal(val link: String) : ImportSource
        data class SongList(val name: String, val text: String) : ImportSource
    }

    /** An import so far: which songs were found in your library, which weren't. */
    data class ImportProgress(
        val name: String,
        val total: Int,
        val done: Int,
        val found: List<Song>,
        val missing: List<WantedSong>,
        /** In your library, but its file won't play (a ghost song): left out of the playlist. */
        val unplayable: List<Song> = emptyList(),
    ) {
        val finished: Boolean get() = done >= total
    }

    private val imports = HashMap<String, ImportSource>()

    /** Holds [source] for the import page (a list can be long, so it isn't put in the route). */
    fun stageImport(source: ImportSource): String {
        val key = java.util.UUID.randomUUID().toString()
        imports[key] = source
        return key
    }

    /**
     * Reads the songs [key]'s source lists and finds each one in your library, reporting as it
     * goes. Nothing is saved: the import page saves the songs found as a new playlist when asked.
     */
    suspend fun runImport(key: String, onProgress: (ImportProgress) -> Unit) {
        val source = imports[key] ?: throw IllegalStateException("That import is gone. Start it again.")
        val (name, wanted) = when (source) {
            is ImportSource.Tidal -> {
                val login = app.tidalLogin.value
                    ?: throw IllegalStateException("To read TIDAL links, add your TIDAL Client ID and secret in Settings → TIDAL. Or paste the songs as a list instead.")
                val id = TidalLinks.playlistId(source.link) ?: throw IllegalStateException("That isn't a TIDAL playlist link.")
                val playlist = TidalClient(app.http, login).playlist(id)
                playlist.name to playlist.tracks.map { WantedSong(it.title, it.artists, it.durationSec) }
            }
            is ImportSource.SongList -> source.name to SongLists.parse(source.text)
        }
        if (wanted.isEmpty()) throw IllegalStateException("No songs came back from that. Check the link or the list.")
        val results = arrayOfNulls<Song>(wanted.size)
        val wontPlay = arrayOfNulls<Song>(wanted.size)
        var done = 0
        fun report() = onProgress(
            ImportProgress(
                name,
                wanted.size,
                done,
                (0 until done).mapNotNull { results[it] },
                (0 until done).filter { results[it] == null && wontPlay[it] == null }.map { wanted[it] },
                (0 until done).mapNotNull { wontPlay[it] },
            ),
        )
        report()
        // A few searches at a time, so a long playlist is quick without crowding the server. Each
        // song found is tried (the first byte of its file), so only songs that play get in.
        wanted.indices.chunked(6).forEach { batch ->
            coroutineScope {
                batch.map { i ->
                    async<Triple<Int, Song?, Song?>> {
                        val song = findInLibrary(wanted[i]) ?: return@async Triple(i, null, null)
                        val check = runCatching { probe(song.id) }.getOrNull()
                        if (check != null && check.ghostly()) {
                            GhostSongs.mark(song, SongTrouble.reason(check))
                            Triple(i, null, song)
                        } else {
                            Triple(i, song, null)
                        }
                    }
                }.awaitAll()
            }.forEach { (i, song, ghost) ->
                results[i] = song
                wontPlay[i] = ghost
            }
            done = batch.last() + 1
            report()
        }
    }

    /** Your library's copy of [w], if it has one (see SongMatch). */
    private suspend fun findInLibrary(w: WantedSong): Song? {
        suspend fun look(q: String, n: Int, want: WantedSong): Song? {
            if (q.isBlank()) return null
            val hits = runCatching { api().search(q, songs = n, albums = 0, artists = 0).songs }.getOrDefault(emptyList())
            // A ghost song (see GhostSongs) doesn't count as having it.
            return SongMatch.best(want, GhostSongs.playable(hits))
        }
        return look(SongMatch.query(w), 10, w)
            // The title alone: the artist may be written differently.
            ?: look(SongMatch.plain(w.title), 25, w)
            // A pasted "Title - Artist", the other way round.
            ?: w.artists.singleOrNull()?.let { a -> WantedSong(a, listOf(w.title), w.durationSec).let { look(SongMatch.query(it), 10, it) } }
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

    /** Symfonium now-playing and application styles in use. */
    val nowPlayingStyle: StateFlow<NowPlayingStyle?> = SymfoniumStyles.nowPlaying
    val appStyle: StateFlow<AppStyle?> = SymfoniumStyles.app

    fun removeNowPlayingStyle() {
        SymfoniumStyles.removeNowPlaying()
        say("Back to NaviMuse's playing screen")
    }

    fun removeAppStyle() {
        SymfoniumStyles.removeApp()
        say("Back to NaviMuse's layout")
    }

    /** Installs a theme or style file of any kind Symfonium shares. */
    private fun importAny(text: String, fileName: String) {
        if (SymfoniumStyles.typeOf(text) != null) {
            val name = SymfoniumStyles.install(text)
            say("Using \"$name\"")
        } else {
            addTheme(CustomThemes.parse(text, fileName))
        }
    }

    /** Picking a colour theme brings its own accent back too. */
    fun useTheme(id: String?) = updateSettings { it.copy(themeId = id, look = it.look.copy(accent = Accent.THEME)) }

    /** Changes something in Settings → Customize. */
    fun updateLook(change: (Look) -> Look) = updateSettings { it.copy(look = change(it.look).sanitized()) }

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
                importAny(text, name)
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
            importAny(text, url.removeSuffix("/download").substringAfterLast('/'))
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
        val songs = Blocklist.allowed(api().songsByGenre(genre, 300))
        if (songs.isEmpty()) say("No songs tagged $genre.") else play(songs, shuffle = true, from = PlaySource(genre, Route.GenrePage(genre)))
    }

    // --- favourites ------------------------------------------------------------------------

    fun isStarred(id: String, fallback: Boolean): Boolean = _stars.value[id] ?: fallback

    fun setStar(id: String, kind: SubsonicClient.StarKind, starred: Boolean) {
        _stars.update { it + (id to starred) }
        if (kind == SubsonicClient.StarKind.SONG) player.markStarred(id, starred)
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

    // --- auto cache ----------------------------------------------------------------------

    /** Bytes in the stream cache and how many songs they belong to. */
    fun cacheUsage(): Pair<Long, Int> = AudioCache.usage(app)

    /** Empties the stream cache (downloads stay). */
    fun clearCache() = action {
        withContext(Dispatchers.IO) { AudioCache.clear(app) }
        say("Cache cleared. Downloads are still on the phone.")
    }

    // --- song menu: credits, save for later, blocking ------------------------------------

    suspend fun credits(song: Song): List<Credit> = api().credits(song.id)

    /** Adds the song to your "Saved for later" playlist (made the first time). Only ever adds. */
    fun saveForLater(song: Song) = action {
        val existing = ownPlaylists().firstOrNull { it.name.equals(SAVED_FOR_LATER, ignoreCase = true) }
        if (existing == null) {
            api().createPlaylist(SAVED_FOR_LATER, listOf(song.id))
        } else {
            if (api().playlist(existing.id).songs.any { it.id == song.id }) {
                say("\"${song.title}\" is already saved for later")
                return@action
            }
            api().addToPlaylist(existing.id, listOf(song.id))
        }
        _playlistsVersion.update { it + 1 }
        say("Saved for later: ${song.title}")
    }

    val blockedMusic: StateFlow<Blocklist.Lists> = Blocklist.lists

    /** Blocks a song on this phone; if it's playing, moves on to the next one. */
    fun blockSong(song: Song) {
        Blocklist.blockSong(song)
        skipIfPlaying(song)
        say("Blocked \"${song.title}\". It's left out of mixes and radio, and skipped when it comes up.")
    }

    fun blockArtist(song: Song) {
        Blocklist.blockArtist(song)
        skipIfPlaying(song)
        say("Blocked ${song.artist}. Their songs are left out of mixes and radio, and skipped when they come up.")
    }

    fun unblockSong(id: String) = Blocklist.unblockSong(id)
    fun unblockArtist(id: String) = Blocklist.unblockArtist(id)

    private fun skipIfPlaying(song: Song) {
        val current = playerState.value.current?.song ?: return
        if (Blocklist.isBlocked(current) && playerState.value.hasNext) player.next()
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

    // --- deleting playlists (restorable for 30 days) -------------------------------------------

    val deletedPlaylists: StateFlow<List<DeletedPlaylists.Entry>> get() = DeletedPlaylists.all

    /** Your own playlists (as an admin, Navidrome would let you delete anyone's: NaviMuse won't). */
    fun canDelete(p: Playlist): Boolean = p.ownedBy(login.value?.username)

    /**
     * Deletes playlist [p] from Navidrome (its songs stay in your library). Its name and songs are
     * kept on the phone first, so Undo, or Restore within 30 days, makes it again.
     */
    fun deletePlaylist(p: Playlist) = action("Deleting playlist…") {
        if (!canDelete(p)) {
            say("\"${p.name}\" is ${p.owner}'s playlist, so NaviMuse won't delete it.")
            return@action
        }
        val songs = api().playlist(p.id).songs
        val entry = DeletedPlaylists.Entry(p.id, p.name, songs.map { it.id }, songs.sumOf { it.duration }, System.currentTimeMillis())
        DeletedPlaylists.add(entry)
        SafetyGuard.permitPlaylistDelete(p.id)
        try {
            api().deletePlaylist(p.id)
        } catch (e: Exception) {
            DeletedPlaylists.remove(p.id)
            throw e
        }
        if (p.id in settings.value.autoDownloadPlaylists) {
            updateSettings { it.copy(autoDownloadPlaylists = it.autoDownloadPlaylists - p.id) }
        }
        _playlistsVersion.update { it + 1 }
        _notes.tryEmit(Note("Deleted \"${p.name}\". Its songs are still in your library.", "Undo") { restorePlaylist(entry) })
    }

    /** Makes a deleted playlist again, with the same name and songs. */
    fun restorePlaylist(entry: DeletedPlaylists.Entry) = action("Restoring playlist…") {
        val name = PlaylistNames.safe(entry.name)
        api().createPlaylist(name, entry.songIds)
        DeletedPlaylists.remove(entry.playlistId)
        _playlistsVersion.update { it + 1 }
        say(if (name != entry.name.trim()) "Restored as \"$name\" (AudioMuse-AI deletes names ending in _automatic)" else "Restored \"$name\"")
    }

    /** Your playlists (owner names regardless of capitals), without AudioMuse-AI's own. */
    suspend fun ownPlaylists(): List<Playlist> {
        val me = login.value?.username
        return api().playlists().filter { it.ownedBy(me) && !it.isAudioMuseManaged }
    }

    companion object {
        /** The playlist "Save for later" adds songs to. */
        const val SAVED_FOR_LATER = "Saved for later"

        /** Ghost songs checked again each time the app opens (at random, for big libraries). */
        private const val QUIET_CHECKS = 100

        /** Songs the music folder Test looks for (any one found is enough). */
        private const val TEST_SONGS = 5
    }
}
