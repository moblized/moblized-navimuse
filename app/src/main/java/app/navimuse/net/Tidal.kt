package app.navimuse.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/** Your own TIDAL developer app (developer.tidal.com), used only to read public playlists. */
data class TidalLogin(val clientId: String, val clientSecret: String)

/** A song as a TIDAL playlist lists it. */
data class TidalTrack(
    val id: String,
    val title: String,
    val version: String? = null,
    val artists: List<String> = emptyList(),
    val isrc: String? = null,
    val durationSec: Int = 0,
)

data class TidalPlaylist(val name: String, val tracks: List<TidalTrack>)

/** Reading TIDAL playlist links: https://tidal.com/browse/playlist/<id>, listen.tidal.com/playlist/<id>… */
object TidalLinks {
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val link = Regex("tidal\\.com/(?:[a-z]+/)*playlist/(${uuid.pattern})", RegexOption.IGNORE_CASE)

    /** The playlist id in a TIDAL link anywhere in [text] (or a bare id), else null. */
    fun playlistId(text: String): String? =
        link.find(text)?.groupValues?.get(1)?.lowercase()
            ?: text.trim().takeIf { uuid.matches(it) }?.lowercase()
}

/**
 * Reads public TIDAL playlists through TIDAL's official API (openapi.tidal.com/v2), signed in as
 * your own developer app with the client-credentials flow: it can read the catalogue and public
 * playlists, never your TIDAL account. Read-only.
 */
class TidalClient(private val http: OkHttpClient, private val login: TidalLogin) {
    private var token: String? = null

    private fun accessToken(): String {
        token?.let { return it }
        val request = Request.Builder()
            .url(TOKEN_URL)
            .header("Authorization", Credentials.basic(login.clientId.trim(), login.clientSecret.trim()))
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()
        http.newCall(request).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (r.code == 400 || r.code == 401 || r.code == 403) {
                throw IOException("TIDAL didn't accept your Client ID and secret. Check them in Settings → TIDAL.")
            }
            if (!r.isSuccessful) throw IOException("TIDAL's sign-in answered HTTP ${r.code}.")
            return JSONObject(body).getString("access_token").also { token = it }
        }
    }

    private fun get(url: HttpUrl): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${accessToken()}")
            .header("Accept", "application/vnd.api+json")
            .get()
            .build()
        http.newCall(request).execute().use { r ->
            val body = r.body?.string().orEmpty()
            when {
                r.code == 404 -> throw IOException("TIDAL can't find that playlist. Only public playlists can be read: in TIDAL, make it public, then copy its link again.")
                r.code == 401 || r.code == 403 -> throw IOException("TIDAL turned the request down (HTTP ${r.code}). Only public playlists can be read.")
                r.code == 429 -> throw IOException("TIDAL says too many requests. Try again in a minute.")
                !r.isSuccessful -> throw IOException("TIDAL answered HTTP ${r.code}.")
            }
            return JSONObject(body)
        }
    }

    /** The playlist's name and songs, in order. */
    suspend fun playlist(id: String, country: String = countryCode()): TidalPlaylist = withContext(Dispatchers.IO) {
        val name = runCatching {
            TidalParse.playlistName(get(API.toHttpUrl().newBuilder().addPathSegments("playlists/$id").addQueryParameter("countryCode", country).build()))
        }.getOrNull()

        // The songs, a page at a time, with their details included.
        val order = ArrayList<String>()
        val tracks = HashMap<String, TidalTrack>()
        var next: HttpUrl? = API.toHttpUrl().newBuilder()
            .addPathSegments("playlists/$id/relationships/items")
            .addQueryParameter("countryCode", country)
            .addQueryParameter("include", "items")
            .build()
        var pages = 0
        while (next != null && pages < MAX_PAGES) {
            val page = get(next)
            val items = TidalParse.items(page)
            order += items.ids
            tracks += items.tracks.associateBy { it.id }
            next = TidalParse.nextLink(page)?.let(::resolve)
            pages++
        }

        // Artist names come with the tracks themselves, a batch at a time.
        order.distinct().chunked(20).forEach { ids ->
            runCatching {
                val url = API.toHttpUrl().newBuilder().addPathSegment("tracks")
                    .addQueryParameter("countryCode", country)
                    .addQueryParameter("include", "artists")
                    .apply { ids.forEach { addQueryParameter("filter[id]", it) } }
                    .build()
                TidalParse.tracks(get(url)).forEach { t ->
                    val before = tracks[t.id]
                    tracks[t.id] = if (before == null) t else before.copy(artists = t.artists.ifEmpty { before.artists })
                }
            }
        }
        TidalPlaylist(name ?: "TIDAL playlist", order.mapNotNull { tracks[it] })
    }

    /** A link TIDAL gives for the next page: absolute, or a path on the API. */
    private fun resolve(link: String): HttpUrl? = when {
        link.startsWith("http") -> link.toHttpUrl()
        link.startsWith("/v2/") -> ("https://openapi.tidal.com$link").toHttpUrl()
        link.startsWith("/") -> ("$API$link").toHttpUrl()
        else -> ("$API/$link").toHttpUrl()
    }

    companion object {
        const val API = "https://openapi.tidal.com/v2"
        const val TOKEN_URL = "https://auth.tidal.com/v1/oauth2/token"
        private const val MAX_PAGES = 50

        fun countryCode(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
    }
}

/** Reading TIDAL's JSON:API answers. Kept apart so it can be tested without TIDAL. */
object TidalParse {
    fun playlistName(doc: JSONObject): String? =
        doc.optJSONObject("data")?.optJSONObject("attributes")?.optString("name")?.takeIf { it.isNotBlank() }

    data class Items(val ids: List<String>, val tracks: List<TidalTrack>)

    /** A page of a playlist's items: the track ids in order, and any tracks included with them. */
    fun items(doc: JSONObject): Items {
        val ids = doc.optJSONArray("data").objects()
            .filter { it.optString("type") == "tracks" }
            .map { it.optString("id") }
            .filter { it.isNotBlank() }
        return Items(ids, tracks(doc, includedOnly = true))
    }

    /** Tracks in [doc] (its data, and/or its included resources), with artist names where included. */
    fun tracks(doc: JSONObject, includedOnly: Boolean = false): List<TidalTrack> {
        val included = doc.optJSONArray("included").objects()
        val artistNames = included.filter { it.optString("type") == "artists" }
            .associate { it.optString("id") to it.optJSONObject("attributes")?.optString("name").orEmpty() }
        val resources = (if (includedOnly) emptyList() else doc.optJSONArray("data").objects()) + included
        return resources.filter { it.optString("type") == "tracks" }.mapNotNull { t ->
            val a = t.optJSONObject("attributes") ?: return@mapNotNull null
            val title = a.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artistIds = t.optJSONObject("relationships")?.optJSONObject("artists")?.optJSONArray("data").objects()
                .map { it.optString("id") }
            TidalTrack(
                id = t.optString("id"),
                title = title,
                version = a.optString("version").takeIf { it.isNotBlank() && it != "null" },
                artists = artistIds.mapNotNull { artistNames[it]?.takeIf { n -> n.isNotBlank() } },
                isrc = a.optString("isrc").takeIf { it.isNotBlank() && it != "null" },
                durationSec = isoSeconds(a.optString("duration")),
            )
        }.distinctBy { it.id }
    }

    fun nextLink(doc: JSONObject): String? =
        doc.optJSONObject("links")?.optString("next")?.takeIf { it.isNotBlank() && it != "null" }

    /** ISO 8601 durations as TIDAL gives them ("PT3M25S") in whole seconds; 0 if unreadable. */
    fun isoSeconds(iso: String?): Int {
        val m = Regex("^P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?$").find(iso?.trim().orEmpty()) ?: return 0
        val (d, h, min, s) = m.destructured
        return (d.toIntOrNull() ?: 0) * 86400 + (h.toIntOrNull() ?: 0) * 3600 + (min.toIntOrNull() ?: 0) * 60 +
            (s.toDoubleOrNull()?.toInt() ?: 0)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
