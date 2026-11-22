package app.navimuse.trash

import app.navimuse.NaviMuseApp
import app.navimuse.data.GhostSongs
import app.navimuse.data.TrashQueue
import app.navimuse.net.MusicShare
import app.navimuse.net.NavidromeNative
import app.navimuse.net.Song
import app.navimuse.ui.friendly
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Moves songs' files to the Trash on the NAS: right away when the NAS answers, otherwise into
 * [TrashQueue] until it does (the SMB share is only reachable at home). Every move still goes
 * through MusicShare, so SafetyGuard.checkMove allows only moves into the Trash.
 */
object TrashMover {
    private const val SMB_PORT = 445

    /** One at a time, whether the app or the background check is moving songs. */
    private val lock = Mutex()

    /** Why a waiting song is hidden, on the Ghost songs list's terms. */
    const val WAITING_REASON = "Waiting to move to the Trash (next time you're on home Wi-Fi)"

    /**
     * Whether the NAS answers on the SMB port, within a few seconds: how NaviMuse tells it's home.
     * Run on a thread of its own, since looking up a name that only exists at home can hang.
     */
    suspend fun reachable(server: String): Boolean {
        val host = server.trim()
        if (host.isEmpty()) return false
        val answer = CompletableDeferred<Boolean>()
        thread(isDaemon = true, name = "nas-check") {
            answer.complete(
                runCatching { Socket().use { it.connect(InetSocketAddress(host, SMB_PORT), 3_000) } }.isSuccess,
            )
        }
        return withTimeoutOrNull(5_000) { answer.await() } ?: false
    }

    sealed interface Outcome {
        /** In the Trash now, at [where]; [scanned]: Navidrome was asked to drop it. */
        data class Moved(val where: String, val scanned: Boolean) : Outcome

        /** The NAS didn't answer, so it waits for home; hidden until then. */
        data object Queued : Outcome
    }

    /** Moves [song]'s file to the Trash, or queues it when the NAS can't be reached from here. */
    suspend fun trashOrQueue(app: NaviMuseApp, song: Song): Outcome {
        val share = app.shareLogin.value
            ?: throw IllegalStateException("Set up your music folder first: Settings → General → Music folder (SMB).")
        val native = NavidromeNative(app.http, app.login.value ?: throw IllegalStateException("Not signed in."))
        if (!reachable(share.server)) {
            // Navidrome usually answers from anywhere: ask it now where the file is, for later.
            val path = runCatching { native.songFile(song.id).path }.getOrNull()
            TrashQueue.add(TrashQueue.Entry(song.id, song.title, song.artist, song.album, path, System.currentTimeMillis()))
            GhostSongs.mark(song, WAITING_REASON)
            TrashQueueWorker.sync(app)
            return Outcome.Queued
        }
        val file = native.songFile(song.id)
        val trashed = lock.withLock {
            withContext(Dispatchers.IO) {
                MusicShare(share).use { ms ->
                    val rel = ms.locate(file.path)
                        ?: throw IllegalStateException("Couldn't find \"${file.path}\" in your music folder, so nothing was moved. Check Settings → Music folder.")
                    ms.moveToTrash(rel)
                }
            }
        }
        // It was waiting from before, and now it's done.
        if (TrashQueue.has(song.id)) TrashQueue.done(song.id)
        GhostSongs.mark(song, "Moved to the Trash ($trashed)")
        return Outcome.Moved(trashed, scanned = quickScan(app, native))
    }

    /** What a look at the queue did. [away]: the NAS didn't answer, so nothing was tried. */
    data class QueueRun(val moved: List<TrashQueue.Entry>, val failed: Int, val away: Boolean)

    /**
     * Moves the waiting songs to the Trash if the NAS answers. [retryProblems]: also tries the
     * ones that went wrong before (when you open the Trash).
     */
    suspend fun runQueue(app: NaviMuseApp, retryProblems: Boolean): QueueRun = lock.withLock { moveWaiting(app, retryProblems) }

    private suspend fun moveWaiting(app: NaviMuseApp, retryProblems: Boolean): QueueRun {
        val share = app.shareLogin.value ?: return QueueRun(emptyList(), 0, away = false)
        val todo = if (retryProblems) TrashQueue.all.value else TrashQueue.waiting()
        if (todo.isEmpty()) return QueueRun(emptyList(), 0, away = false)
        if (!reachable(share.server)) return QueueRun(emptyList(), 0, away = true)
        val native = app.login.value?.let { NavidromeNative(app.http, it) }
        val moved = ArrayList<TrashQueue.Entry>()
        var failed = 0
        withContext(Dispatchers.IO) {
            MusicShare(share).use { ms ->
                for (e in todo) {
                    try {
                        val path = e.path
                            ?: native?.songFile(e.songId)?.path
                            ?: throw IllegalStateException("Sign in to Navidrome so NaviMuse can find this song's file.")
                        val rel = ms.locate(path)
                            ?: throw IllegalStateException("Couldn't find \"$path\" in your music folder, so nothing was moved.")
                        val trashed = ms.moveToTrash(rel)
                        TrashQueue.done(e.songId)
                        GhostSongs.mark(e.songId, e.title, e.artist, e.album, "Moved to the Trash ($trashed)")
                        moved += e
                    } catch (x: CancellationException) {
                        throw x
                    } catch (x: Exception) {
                        // Left home in the middle: the rest just keep waiting.
                        if (!reachable(share.server)) break
                        TrashQueue.setProblem(e.songId, friendly(x))
                        failed++
                    }
                }
            }
        }
        if (moved.isNotEmpty() && native != null) quickScan(app, native)
        return QueueRun(moved, failed, away = false)
    }

    /** Asks Navidrome to notice the files that moved, if you're an admin (else its next scan does). */
    private suspend fun quickScan(app: NaviMuseApp, native: NavidromeNative): Boolean =
        runCatching { if (native.admin()) app.api()?.startQuickScan() else null }.getOrNull() != null
}
