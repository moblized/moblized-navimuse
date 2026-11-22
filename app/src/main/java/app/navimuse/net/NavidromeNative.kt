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
    /**
     * Where a song's file is: [path] inside the library folder [libraryPath]. [missing]: Navidrome's
     * last scan didn't find the file, so it's left out of every list (only admins still see it).
     */
    data class SongFile(val path: String, val libraryPath: String?, val missing: Boolean = false)

    private var token: String? = null

    /** Whether the signed-in user is a Navidrome admin (only admins can ask for a scan). */
    var isAdmin: Boolean = false
        private set

    /** Where Navidrome answers, after any redirect (say, an http:// address sent on to https://). */
    private var base: String = login.baseUrl.trimEnd('/')

    /**
     * Redirects are followed here, not by OkHttp: OkHttp turns a redirected POST into a GET, so the
     * sign-in reached Navidrome as "GET /auth/login" and it answered 405. Each step is checked by
     * SafetyGuard, and must still be the same request.
     */
    private val noRedirects by lazy { http.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    private fun send(method: String, path: String, body: RequestBody? = null, auth: Boolean = true): Pair<Int, String> {
        var url = (base + path).toHttpUrl()
        for (hop in 0..MAX_REDIRECTS) {
            SafetyGuard.checkNavidromeNative(method, url.encodedPath)
            val request = Request.Builder().url(url).method(method, body)
                .apply { if (auth) token?.let { header("X-ND-Authorization", "Bearer $it") } }
                .build()
            val next = noRedirects.newCall(request).execute().use { r ->
                if (r.code !in 300..399) return r.code to (r.body?.string().orEmpty())
                r.header("Location")?.let { url.resolve(it) } ?: return r.code to ""
            }
            val plain = next.newBuilder().query(null).fragment(null).build().toString().trimEnd('/')
            if (!plain.endsWith(path)) {
                throw IOException(
                    "Your Navidrome address sends NaviMuse on to ${next.host}${next.encodedPath} instead of Navidrome. " +
                        "Sign in with the address Navidrome itself answers on.",
                )
            }
            base = plain.dropLast(path.length)
            url = next
        }
        throw IOException("Your Navidrome address keeps redirecting.")
    }

    private fun signIn() {
        val json = JSONObject().put("username", login.username).put("password", login.password).toString()
        val (code, body) = send("POST", "/auth/login", json.toRequestBody("application/json".toMediaType()), auth = false)
        if (code == 401 || code == 403) throw IOException("Navidrome didn't accept your login for its own API.")
        if (code == 405) {
            throw IOException(
                "Navidrome's sign-in answered HTTP 405: something between NaviMuse and Navidrome (a proxy?) changed the request. " +
                    "If your Navidrome address starts with http://, sign in with https:// instead.",
            )
        }
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
    suspend fun songFile(id: String): SongFile =
        lookup(id) ?: throw IOException("Navidrome doesn't know that song any more.")

    /** Where song [id]'s file is, or null when Navidrome doesn't have the song at all any more. */
    suspend fun lookup(id: String): SongFile? = withContext(Dispatchers.IO) {
        if (token == null) signIn()
        var (code, body) = send("GET", "/api/song/$id")
        if (code == 401) {
            signIn()
            val again = send("GET", "/api/song/$id")
            code = again.first
            body = again.second
        }
        if (code == 404) return@withContext null
        if (code !in 200..299) throw IOException("Navidrome answered HTTP $code for that song.")
        parseSongFile(JSONObject(body))
    }

    companion object {
        private const val MAX_REDIRECTS = 5

        fun parseSongFile(o: JSONObject): SongFile {
            val path = o.optString("path").takeIf { it.isNotBlank() && it != "null" }
                ?: throw IOException("Navidrome didn't say where that song's file is.")
            val lib = o.optString("libraryPath").takeIf { it.isNotBlank() && it != "null" }
            // Older Navidrome gives the whole path; newer gives it inside the library folder.
            val relative = if (lib != null && path.startsWith(lib)) path.removePrefix(lib) else path
            return SongFile(relative.trimStart('/', '\\'), lib, missing = o.optBoolean("missing", false))
        }
    }
}
