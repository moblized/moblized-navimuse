package app.navimuse.ui

import app.navimuse.net.BlockedRequestException
import app.navimuse.net.SubsonicException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A message a person can act on, instead of an exception name. */
fun friendly(e: Throwable): String = when (e) {
    is BlockedRequestException -> "Blocked for safety: ${e.message}"
    is SubsonicException -> e.message ?: "The server reported an error."
    is UnknownHostException -> "Can't find that server. Check the address, or connect to your home Wi-Fi or VPN."
    is ConnectException -> "Can't connect. Is Navidrome running, and is the port right?"
    is SocketTimeoutException -> "The server took too long to answer."
    is SSLException -> "The secure connection failed. If your server uses plain http://, type that instead."
    is IOException -> e.message ?: "Network error."
    else -> e.message ?: e.javaClass.simpleName
}

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatTotal(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
}
