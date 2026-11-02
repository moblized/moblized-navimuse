package app.navimuse

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.navimuse.data.Settings
import app.navimuse.data.Store
import app.navimuse.net.AudioMuseClient
import app.navimuse.net.AudioMuseLogin
import app.navimuse.net.BlockedRequestException
import app.navimuse.net.ServerInfo
import app.navimuse.net.ServerLogin
import app.navimuse.net.SubsonicClient
import app.navimuse.downloads.Downloads
import app.navimuse.net.Song
import app.navimuse.playback.Dolby
import app.navimuse.playback.PlayerConnection
import app.navimuse.update.UpdateWorker
import app.navimuse.update.Updater
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** A request the safety guard refused, kept to show on the Safety screen. */
data class BlockedEvent(val atMillis: Long, val request: String, val reason: String)

/** App-wide pieces shared by the screens and the background player. */
class NaviMuseApp : Application() {
    lateinit var store: Store
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _login = MutableStateFlow<ServerLogin?>(null)
    val login: StateFlow<ServerLogin?> = _login.asStateFlow()

    private val _audioMuse = MutableStateFlow<AudioMuseLogin?>(null)
    val audioMuseLogin: StateFlow<AudioMuseLogin?> = _audioMuse.asStateFlow()

    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    private val _blocked = MutableStateFlow<List<BlockedEvent>>(emptyList())
    val blocked: StateFlow<List<BlockedEvent>> = _blocked.asStateFlow()

    @Volatile private var apiCache: SubsonicClient? = null
    @Volatile private var audioMuseCache: AudioMuseClient? = null

    val player: PlayerConnection by lazy { PlayerConnection(this) }

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        _settings.value = store.settings()
        _login.value = store.server()
        _audioMuse.value = store.audioMuse()
        Dolby.init(this)
        // Loads the list of downloads, so songs saved on the phone play from it straight away.
        Downloads.manager(this)
        UpdateWorker.schedule(this, _settings.value.autoUpdate)
        Updater.restoreReady(this)
    }

    private fun recordBlocked(e: BlockedRequestException) {
        _blocked.update { (listOf(BlockedEvent(System.currentTimeMillis(), e.request, e.message.orEmpty())) + it).take(50) }
    }

    /** The Navidrome connection, or null when nobody is logged in. */
    fun api(): SubsonicClient? {
        val l = _login.value ?: return null
        apiCache?.let { if (it.login == l) return it }
        return SubsonicClient(l, http, ::recordBlocked).also { apiCache = it }
    }

    /** The optional direct AudioMuse-AI connection (mood search). */
    fun audioMuse(): AudioMuseClient? {
        val l = _audioMuse.value ?: return null
        audioMuseCache?.let { if (it.login == l) return it }
        return AudioMuseClient(l, http, ::recordBlocked).also { audioMuseCache = it }
    }

    fun clientFor(login: ServerLogin) = SubsonicClient(login, http, ::recordBlocked)
    fun audioMuseClientFor(login: AudioMuseLogin) = AudioMuseClient(login, http, ::recordBlocked)

    fun saveLogin(baseUrl: String, username: String, password: String) {
        store.saveServer(baseUrl, username, password)
        _login.value = store.server()
    }

    fun logout() {
        player.stopAndClear()
        // Downloads belong to this login; the phone's copies go with it. Nothing on the server changes.
        Downloads.removeAll(this)
        Dolby.clear()
        store.clearServer()
        _login.value = null
        _serverInfo.value = null
    }

    fun saveAudioMuse(login: AudioMuseLogin?) {
        store.saveAudioMuse(login)
        _audioMuse.value = store.audioMuse()
    }

    fun setServerInfo(info: ServerInfo?) {
        _serverInfo.value = info
    }

    fun updateSettings(change: (Settings) -> Settings) {
        val before = _settings.value
        val next = change(before)
        _settings.value = next
        store.saveSettings(next)
        Downloads.onSettingsChanged(this, next)
        if (next.autoUpdate != before.autoUpdate) UpdateWorker.schedule(this, next.autoUpdate)
    }

    /**
     * Downloads favourites and chosen playlists that aren't on the phone yet. Runs when the app
     * opens and when those lists change. Returns how many songs were queued.
     */
    suspend fun syncAutoDownloads(): Int {
        val s = _settings.value
        val api = api() ?: return 0
        if (!s.autoDownloadFavourites && s.autoDownloadPlaylists.isEmpty()) return 0
        val songs = withContext(Dispatchers.IO) {
            buildList<app.navimuse.net.Song> {
                if (s.autoDownloadFavourites) addAll(runCatching { api.starred().songs }.getOrDefault(emptyList()))
                s.autoDownloadPlaylists.forEach { id -> addAll(runCatching { api.playlist(id).songs }.getOrDefault(emptyList())) }
            }
        }
        return withContext(Dispatchers.Main) { Downloads.add(this@NaviMuseApp, api, songs, ::downloadBitrateFor) }
    }

    /**
     * Stream bitrate for this song: the original file for Dolby Atmos and surround songs (a
     * converted copy would be plain stereo), otherwise the setting for the current network.
     */
    fun bitrateFor(song: Song): Int =
        if (_settings.value.dolbyOriginal && Dolby.labelFor(song) != null) 0 else currentMaxBitRate()

    fun downloadBitrateFor(song: Song): Int =
        if (_settings.value.dolbyOriginal && Dolby.labelFor(song) != null) 0 else _settings.value.downloadQuality.kbps

    /** Id of the player's audio output, for the phone's sound effects (Dolby Atmos, equalizer). */
    @Volatile var audioSessionId: Int = 0

    /** Stream bitrate for the network the phone is on right now (0 = original file). */
    fun currentMaxBitRate(): Int {
        val s = _settings.value
        val cm = getSystemService(ConnectivityManager::class.java) ?: return s.wifiQuality.kbps
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val unmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        return if (unmetered) s.wifiQuality.kbps else s.mobileQuality.kbps
    }
}
