package app.navimuse.data

import android.content.Context
import android.content.SharedPreferences
import app.navimuse.net.AudioMuseLogin
import app.navimuse.net.ServerLogin
import app.navimuse.net.SubsonicAuth

enum class ThemeMode(val label: String) { SYSTEM("Follow phone"), LIGHT("Light"), DARK("Dark") }

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
    )

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putString("wifiQuality", s.wifiQuality.name)
            .putString("mobileQuality", s.mobileQuality.name)
            .putBoolean("scrobble", s.scrobble)
            .putInt("mixSize", s.mixSize)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        runCatching { enumValueOf<E>(name ?: "") }.getOrDefault(default)
}
