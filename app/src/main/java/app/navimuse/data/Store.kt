package app.navimuse.data

import android.content.Context
import android.content.SharedPreferences
import app.navimuse.net.AudioMuseLogin
import app.navimuse.net.ServerLogin
import app.navimuse.net.SubsonicAuth

enum class ThemeMode(val label: String) { SYSTEM("Follow phone"), LIGHT("Light"), DARK("Dark") }

/** How the audio details under the seek bar read. */
enum class DetailsStyle(val label: String, val example: String) {
    TIDAL("Like Tidal", "16-BIT 44.1kHZ FLAC"),
    SYMFONIUM("Like Symfonium", "FLAC • 1,411 kb/s • 44.1 kHz"),
    OFF("Off", "Hidden"),
}

/** Streaming quality. 0 = the original file, untouched. */
enum class Quality(val kbps: Int, val label: String) {
    ORIGINAL(0, "Original"),
    HIGH(320, "High (320 kbps)"),
    NORMAL(192, "Normal (192 kbps)"),
    SAVER(128, "Data saver (128 kbps)"),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val wifiQuality: Quality = Quality.ORIGINAL,
    val mobileQuality: Quality = Quality.NORMAL,
    val scrobble: Boolean = true,
    val mixSize: Int = 50,
    /** Quality songs are saved at for offline play. */
    val downloadQuality: Quality = Quality.ORIGINAL,
    val downloadWifiOnly: Boolean = true,
    /** Download every favourite song, and new ones as you add them. */
    val autoDownloadFavourites: Boolean = false,
    /** Playlists kept downloaded, including songs added to them later. */
    val autoDownloadPlaylists: Set<String> = emptySet(),
    /** Check GitHub for new versions twice a day and download them. */
    val autoUpdate: Boolean = true,
    /** Stream and download Dolby Atmos and surround songs untouched, so the phone decodes them. */
    val dolbyOriginal: Boolean = true,
    /** Imported Symfonium / Material colour theme in use, or null for NaviMuse's own colours. */
    val themeId: String? = null,
    /** Symfonium icon pack in use ("<package>/<pack id>"), or null for NaviMuse's own icons. */
    val iconPack: String? = null,
    val detailsStyle: DetailsStyle = DetailsStyle.TIDAL,
    /** Everything chosen in Settings → Customize. */
    val look: Look = Look(),
    /** Keep played and upcoming songs in the phone's cache automatically. */
    val autoCache: Boolean = true,
    /** Cache each song in full as it plays, even if you skip it partway. */
    val cacheWholeSong: Boolean = true,
    /** How many of the next songs in the queue to cache in full. */
    val cacheAhead: Int = 5,
    /** Cache ahead on mobile data too (otherwise only on Wi-Fi). */
    val cacheAheadMobile: Boolean = false,
    /** Most the cache may use, in MB; the least recently played songs go first. */
    val cacheLimitMb: Int = 2048,
)

/** Saved logins and settings, in plain SharedPreferences (the secrets inside are encrypted). */
class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("navimuse", Context.MODE_PRIVATE)

    /** One random salt per install, so album-art and stream links stay the same and can be cached. */
    private val mediaSalt: String
        get() = prefs.getString("mediaSalt", null) ?: SubsonicAuth.newSalt().also {
            prefs.edit().putString("mediaSalt", it).apply()
        }

    fun server(): ServerLogin? {
        val url = prefs.getString("serverUrl", null) ?: return null
        val user = prefs.getString("username", null) ?: return null
        val pass = prefs.getString("password", null)?.let { SecretBox.decrypt(it) } ?: return null
        return ServerLogin(url, user, pass, mediaSalt)
    }

    fun saveServer(baseUrl: String, username: String, password: String) {
        prefs.edit()
            .putString("serverUrl", baseUrl)
            .putString("username", username)
            .putString("password", SecretBox.encrypt(password))
            .apply()
    }

    fun clearServer() {
        prefs.edit().remove("serverUrl").remove("username").remove("password").apply()
    }

    fun audioMuse(): AudioMuseLogin? {
        val url = prefs.getString("amUrl", null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString("amToken", null)?.let { SecretBox.decrypt(it) }.orEmpty()
        return AudioMuseLogin(url, token, prefs.getString("amServer", null).orEmpty())
    }

    fun saveAudioMuse(login: AudioMuseLogin?) {
        val e = prefs.edit()
        if (login == null) {
            e.remove("amUrl").remove("amToken").remove("amServer")
        } else {
            e.putString("amUrl", login.baseUrl)
                .putString("amToken", if (login.token.isBlank()) null else SecretBox.encrypt(login.token))
                .putString("amServer", login.serverName)
        }
        e.apply()
    }

    fun settings(): Settings = Settings(
        theme = enumOr(prefs.getString("theme", null), ThemeMode.DARK),
        wifiQuality = enumOr(prefs.getString("wifiQuality", null), Quality.ORIGINAL),
        mobileQuality = enumOr(prefs.getString("mobileQuality", null), Quality.NORMAL),
        scrobble = prefs.getBoolean("scrobble", true),
        mixSize = prefs.getInt("mixSize", 50),
        downloadQuality = enumOr(prefs.getString("downloadQuality", null), Quality.ORIGINAL),
        downloadWifiOnly = prefs.getBoolean("downloadWifiOnly", true),
        autoDownloadFavourites = prefs.getBoolean("autoDownloadFavourites", false),
        autoDownloadPlaylists = prefs.getStringSet("autoDownloadPlaylists", null)?.toSet().orEmpty(),
        autoUpdate = prefs.getBoolean("autoUpdate", true),
        dolbyOriginal = prefs.getBoolean("dolbyOriginal", true),
        themeId = prefs.getString("customTheme", null),
        iconPack = prefs.getString("iconPack", null),
        detailsStyle = enumOr(prefs.getString("detailsStyle", null), DetailsStyle.TIDAL),
        look = Look.fromJson(prefs.getString("look", null)),
        autoCache = prefs.getBoolean("autoCache", true),
        cacheWholeSong = prefs.getBoolean("cacheWholeSong", true),
        cacheAhead = prefs.getInt("cacheAhead", 5),
        cacheAheadMobile = prefs.getBoolean("cacheAheadMobile", false),
        cacheLimitMb = prefs.getInt("cacheLimitMb", 2048),
    )

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putString("wifiQuality", s.wifiQuality.name)
            .putString("mobileQuality", s.mobileQuality.name)
            .putBoolean("scrobble", s.scrobble)
            .putInt("mixSize", s.mixSize)
            .putString("downloadQuality", s.downloadQuality.name)
            .putBoolean("downloadWifiOnly", s.downloadWifiOnly)
            .putBoolean("autoDownloadFavourites", s.autoDownloadFavourites)
            .putStringSet("autoDownloadPlaylists", HashSet(s.autoDownloadPlaylists))
            .putBoolean("autoUpdate", s.autoUpdate)
            .putBoolean("dolbyOriginal", s.dolbyOriginal)
            .putString("customTheme", s.themeId)
            .putString("iconPack", s.iconPack)
            .putString("detailsStyle", s.detailsStyle.name)
            .putString("look", s.look.toJson())
            .putBoolean("autoCache", s.autoCache)
            .putBoolean("cacheWholeSong", s.cacheWholeSong)
            .putInt("cacheAhead", s.cacheAhead)
            .putBoolean("cacheAheadMobile", s.cacheAheadMobile)
            .putInt("cacheLimitMb", s.cacheLimitMb)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        runCatching { enumValueOf<E>(name ?: "") }.getOrDefault(default)
}
