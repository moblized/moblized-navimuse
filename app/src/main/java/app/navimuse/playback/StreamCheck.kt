package app.navimuse.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * What the server really sends for a song that didn't play. Before the player tries a song in
 * another form, it reads the first few KB of the same link: when Navidrome answers with an error
 * instead of audio (its file was moved or deleted, say), no other form will play either, so the
 * player says what Navidrome said and moves on, instead of trying every form and learning the
 * wrong lesson about the phone.
 *
 * Navidrome answers a song it can't open with a Subsonic error (HTTP 200, JSON) for the original
 * file, and with an empty body for a conversion (ffmpeg had nothing to read). The phone sees both
 * as "not audio", the same as a format it can't play, which is why it has to look.
 */
sealed interface StreamCheck {
    /** Audio arrived: the phone couldn't play it in this form, so another form may work. */
    data object Audio : StreamCheck

    /** The server answered, with nothing in it. */
    data object Empty : StreamCheck

    /** The server is busy converting other songs (HTTP 429): the same form works in a moment. */
    data class Busy(val retryAfterSeconds: Int) : StreamCheck

    /** Navidrome (or something in front of it) answered with an error instead of audio. */
    data class Refused(val code: Int?, val message: String) : StreamCheck {
        /** Navidrome can't find the song's file: moved, renamed or deleted since its last scan. */
        val missing: Boolean
            get() = code == ERROR_NOT_FOUND || MISSING.any { message.contains(it, ignoreCase = true) }

        /** Navidrome isn't allowed to read the song's file. */
        val forbidden: Boolean
            get() = message.contains("permission denied", ignoreCase = true)

        /** It's this song's file, not the server as a whole: other songs will play. */
        val songOnly: Boolean get() = missing || forbidden
    }

    /** The server couldn't be reached at all. */
    data object Unreachable : StreamCheck

    companion object {
        /** Enough to see whether it's audio or an error, without reading a whole song. */
        const val PEEK_BYTES = 16 * 1024

        /** Subsonic's "the requested data was not found". */
        const val ERROR_NOT_FOUND = 70

        private val MISSING = listOf("no such file", "does not exist", "not found", "cannot find", "file not exist")

        /**
         * Content types that are never audio: Subsonic errors (JSON or XML) and web pages from
         * the server or a proxy in front of it. Anything else, including none, may be audio.
         */
        fun isErrorContentType(contentType: String?): Boolean {
            val type = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
            return type == "application/json" || type.endsWith("+json") ||
                type == "application/xml" || type == "text/xml" || type.endsWith("+xml") ||
                type == "text/html"
        }

        /** Reads the start of [url] and says what came back. */
        suspend fun run(http: OkHttpClient, url: String): StreamCheck = withContext(Dispatchers.IO) {
            val client = http.newBuilder()
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
            // Original files honour the range; conversions ignore it and are cut off once read.
            val request = Request.Builder().url(url).header("Range", "bytes=0-${PEEK_BYTES - 1}").get().build()
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                return@withContext Unreachable
            }
            response.use { r ->
                val head = Buffer()
                try {
                    val source = r.body?.source()
                    while (source != null && head.size < PEEK_BYTES) {
                        if (source.read(head, PEEK_BYTES - head.size) == -1L) break
                    }
                } catch (e: IOException) {
                    // Cut short: judge by what did arrive.
                }
                classify(r.code, r.header("Content-Type"), head.readByteArray(), r.header("Retry-After"))
            }
        }

        /** The verdict for an answer with this status, content type and first bytes. */
        fun classify(httpCode: Int, contentType: String?, head: ByteArray, retryAfter: String? = null): StreamCheck {
            val text = if (looksLikeText(head)) head.toString(Charsets.UTF_8).trimStart() else ""
            val subsonic = text.startsWith("{") || text.startsWith("<?xml", ignoreCase = true) ||
                text.startsWith("<subsonic-response")
            val page = text.startsWith("<!doctype html", ignoreCase = true) || text.startsWith("<html", ignoreCase = true)
            if (httpCode == 429) return Busy(retryAfter?.trim()?.toIntOrNull()?.coerceIn(1, 30) ?: 5)
            if (isErrorContentType(contentType) || subsonic || page) {
                errorOf(text)?.let { return it }
                return Refused(
                    null,
                    when {
                        httpCode !in 200..299 -> "HTTP $httpCode"
                        page || contentType?.contains("html", ignoreCase = true) == true -> "it sent a web page instead of the song"
                        else -> "it sent something that isn't audio"
                    },
                )
            }
            if (httpCode !in 200..299) return Refused(null, "HTTP $httpCode")
            if (head.isEmpty()) return Empty
            return Audio
        }

        /** The code and message of a Subsonic error in JSON or XML, if [text] is one. */
        fun errorOf(text: String): Refused? {
            if (text.startsWith("{")) {
                val error = runCatching { JSONObject(text).optJSONObject("subsonic-response")?.optJSONObject("error") }.getOrNull()
                    ?: return null
                return Refused(error.optInt("code", -1).takeIf { it >= 0 }, error.optString("message").cleanMessage())
            }
            val tag = Regex("<error\\b[^>]*>").find(text)?.value ?: return null
            val code = Regex("\\bcode=\"(\\d+)\"").find(tag)?.groupValues?.get(1)?.toIntOrNull()
            val message = Regex("\\bmessage=\"([^\"]*)\"").find(tag)?.groupValues?.get(1).orEmpty()
            return Refused(code, message.unescapeXml().cleanMessage())
        }

        /** Mostly printable characters (audio has plenty of zero and control bytes up front). */
        private fun looksLikeText(head: ByteArray): Boolean {
            if (head.isEmpty()) return false
            val sample = head.take(512)
            val odd = sample.count { b -> val c = b.toInt() and 0xff; c < 0x09 || (c in 0x0e..0x1f) }
            return odd == 0
        }

        private fun String.cleanMessage(): String =
            trim().removePrefix("Internal Server Error:").trim().trimEnd('.').ifBlank { "no reason given" }

        private fun String.unescapeXml(): String =
            replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }
}

/** What to tell the user about a song the server wouldn't send, and whether to move on. */
object SongTrouble {
    /** Why [check] means "[title]" can't play right now, in a sentence. */
    fun reason(check: StreamCheck): String = when (check) {
        is StreamCheck.Refused -> when {
            check.missing -> "Navidrome can't find its file on your server. It may have been moved, renamed or deleted; a full scan in Navidrome tidies that up"
            check.forbidden -> "Navidrome isn't allowed to read its file. Check that folder's permissions on your server"
            else -> "Navidrome said: ${check.message}"
        }
        StreamCheck.Empty -> "Navidrome sent an empty file for it"
        StreamCheck.Unreachable -> "your server can't be reached"
        is StreamCheck.Busy -> "your server is busy converting other songs"
        StreamCheck.Audio -> "it wouldn't play"
    }

    /** The note shown for a song that was skipped ([skipped]) or stopped on. */
    fun notice(title: String, check: StreamCheck, skipped: Boolean): String =
        (if (skipped) "Skipped \"$title\": " else "Can't play \"$title\": ") + reason(check) + "."
}
