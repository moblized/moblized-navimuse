package app.navimuse.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Navidrome's own API (the one its web UI uses), for the one thing the Subsonic API won't say:
 * where a song's file really is. Signs in with your Navidrome login and only reads songs; every
 * request is checked by SafetyGuard.checkNavidromeNative before it's sent.
 */
class NavidromeNative(private val http: OkHttpClient, private val login: ServerLogin) {
    /** Where a song's file is: [path] inside the library folder [libraryPath]. */
    data class SongFile(val path: String, val libraryPath: String?)

    private var token: String? = null

    /** Whether the signed-in user is a Navidrome admin (only admins can ask for a scan). */
    var isAdmin: Boolean = false
        private set

    private fun send(method: String, path: String, body: RequestBody? = null, auth: Boolean = true): Pair<Int, String> {
        val url = (login.baseUrl.trimEnd('/') + path).toHttpUrl()
        SafetyGuard.checkNavidromeNative(method, url.encodedPath)
        val request = Request.Builder().url(url).method(method, body)
            .apply { if (auth) token?.let { header("X-ND-Authorization", "Bearer $it") } }
            .build()
        http.newCall(request).execute().use { r -> return r.code to (r.body?.string().orEmpty()) }
    }

    private fun signIn() {
        val json = JSONObject().put("username", login.username).put("password", login.password).toString()
        val (code, body) = send("POST", "/auth/login", json.toRequestBody("application/json".toMediaType()), auth = false)
        if (code == 401 || code == 403) throw IOException("Navidrome didn't accept your login for its own API.")
        if (code !in 200..299) throw IOException("Navidrome's sign-in answered HTTP $code.")
        val o = JSONObject(body)
        token = o.optString("token").takeIf { it.isNotBlank() } ?: throw IOException("Navidrome's sign-in gave no token.")
        isAdmin = o.optBoolean("isAdmin", false)
    }

    /** Signs in (if needed) and says whether you're an admin. */
    suspend fun admin(): Boolean = withContext(Dispatchers.IO) {
        if (token == null) signIn()
        isAdmin
    }

    /** Where song [id]'s file is. */
    suspend fun songFile(id: String): SongFile = withContext(Dispatchers.IO) {
        if (token == null) signIn()
        var (code, body) = send("GET", "/api/song/$id")
        if (code == 401) {
            signIn()
            val again = send("GET", "/api/song/$id")
            code = again.first
            body = again.second
        }
        if (code == 404) throw IOException("Navidrome doesn't know that song any more.")
        if (code !in 200..299) throw IOException("Navidrome answered HTTP $code for that song.")
        parseSongFile(JSONObject(body))
    }

    companion object {
        fun parseSongFile(o: JSONObject): SongFile {
            val path = o.optString("path").takeIf { it.isNotBlank() && it != "null" }
                ?: throw IOException("Navidrome didn't say where that song's file is.")
            val lib = o.optString("libraryPath").takeIf { it.isNotBlank() && it != "null" }
            // Older Navidrome gives the whole path; newer gives it inside the library folder.
            val relative = if (lib != null && path.startsWith(lib)) path.removePrefix(lib) else path
            return SongFile(relative.trimStart('/', '\\'), lib)
        }
    }
}
