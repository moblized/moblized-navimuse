package app.navimuse.net

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class AudioMuseLogin(
    val baseUrl: String,
    /** The AudioMuse-AI API token (blank if its login is turned off). */
    val token: String,
    /** The server's name in AudioMuse-AI, only needed if it's set up with several media servers. */
    val serverName: String,
)

/**
 * Optional direct line to AudioMuse-AI for its mood search ("describe a vibe").
 * Its API token is admin-level there, so [SafetyGuard.checkAudioMuse] only lets searches through.
 */
class AudioMuseClient(
    val login: AudioMuseLogin,
    private val http: OkHttpClient,
    private val onBlocked: (BlockedRequestException) -> Unit = {},
) {
    private val base: HttpUrl = login.baseUrl.toHttpUrl()

    private fun guard(method: String, path: String) {
        try {
            SafetyGuard.checkAudioMuse(method, path)
        } catch (e: BlockedRequestException) {
            onBlocked(e)
            throw e
        }
    }

    private fun request(url: HttpUrl): Request.Builder {
        val b = Request.Builder().url(url)
        if (login.token.isNotBlank()) b.header("Authorization", "Bearer ${login.token}")
        return b
    }

    private fun failure(code: Int, body: String): Exception {
        val msg = runCatching { JSONObject(body).optString("error").ifBlank { JSONObject(body).optString("message") } }
            .getOrNull().orEmpty()
        return when (code) {
            401, 403 -> IOException("AudioMuse-AI didn't accept the API token.")
            400 -> IOException(msg.ifBlank { "AudioMuse-AI couldn't run that search. Is CLAP turned on?" })
            503 -> IOException("AudioMuse-AI is still loading its mood index. Try again in a minute (it needs a finished analysis).")
            else -> IOException("AudioMuse-AI answered $code. ${msg}".trim())
        }
    }

    /** Checks the address and token. Returns a short status line. */
    suspend fun test(): String = withContext(Dispatchers.IO) {
        val path = "/api/search_tracks"
        guard("GET", path)
        val b = base.newBuilder().addPathSegments("api/search_tracks")
            .addQueryParameter("search_query", "a")
            .addQueryParameter("end", "1")
        if (login.serverName.isNotBlank()) b.addQueryParameter("server", login.serverName)
        http.newCall(request(b.build()).get().build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw failure(r.code, body)
            "Connected to AudioMuse-AI"
        }
    }

    /** Songs that match a description like "calm piano for a rainy night". */
    suspend fun moodSearch(query: String, limit: Int = 40): List<MoodHit> = withContext(Dispatchers.IO) {
        val path = "/api/clap/search"
        guard("POST", path)
        val json = JSONObject().put("query", query).put("limit", limit)
        if (login.serverName.isNotBlank()) json.put("server", login.serverName)
        val url = base.newBuilder().addPathSegments("api/clap/search").build()
        val req = request(url).post(json.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw failure(r.code, body)
            Parse.moodHits(body)
        }
    }
}
