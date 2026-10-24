package app.navimuse.net

import java.security.MessageDigest
import java.security.SecureRandom
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrl {
    /**
     * Turns whatever was typed or pasted into the server's base address:
     * "192.168.1.5:4533" → "http://192.168.1.5:4533",
     * "https://music.example.com/app/#/album/all" → "https://music.example.com".
     * A reverse-proxy sub-path ("/navidrome") is kept.
     */
    fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "http://$s"
        val url = s.toHttpUrlOrNull() ?: return null
        if (url.scheme != "http" && url.scheme != "https") return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val cut = segments.indexOfFirst { it == "app" || it == "rest" }
        val keep = if (cut >= 0) segments.take(cut) else segments
        val builder = url.newBuilder().query(null).fragment(null).encodedPath("/")
        keep.forEach { builder.addPathSegment(it) }
        return builder.build().toString().trimEnd('/')
    }
}

/** Subsonic token login: the password itself is never sent, only md5(password + salt). */
object SubsonicAuth {
    private val random = SecureRandom()

    fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun newSalt(): String {
        val b = ByteArray(8)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}
