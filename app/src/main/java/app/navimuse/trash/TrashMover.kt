package app.navimuse.trash

import app.navimuse.NaviMuseApp
import app.navimuse.data.GhostSongs
import app.navimuse.data.TrashQueue
import app.navimuse.net.MusicShare
import app.navimuse.net.NavidromeNative
import app.navimuse.net.Song
import app.navimuse.playback.StreamCheck
import app.navimuse.ui.friendly
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

    /**
     * What a look at the queue did. [away]: the NAS didn't answer, so nothing was tried. [cleared]:
     * ghost songs whose files were gone already, so there was nothing to move.
     */
    data class QueueRun(
        val moved: List<TrashQueue.Entry>,
        val failed: Int,
        val away: Boolean,
        val cleared: List<TrashQueue.Entry> = emptyList(),
    )

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
        val cleared = ArrayList<TrashQueue.Entry>()
        var failed = 0
        withContext(Dispatchers.IO) {
            MusicShare(share).use { ms ->
                for (e in todo) {
                    try {
                        val known = if (e.ghost) native?.lookup(e.songId) else null
                        if (e.ghost && native != null && (known == null || known.missing)) {
                            // Navidrome dropped it already: nothing left to move.
                            TrashQueue.remove(e.songId)
                            cleared += e
                            continue
                        }
                        val path = e.path
                            ?: known?.path
                            ?: native?.songFile(e.songId)?.path
                            ?: throw IllegalStateException("Sign in to Navidrome so NaviMuse can find this song's file.")
                        val rel = ms.locate(path)
                        if (rel == null && e.ghost) {
                            // A ghost whose file is gone: Navidrome's scan drops it.
                            TrashQueue.remove(e.songId)
                            GhostSongs.relabel(listOf(e.songId), GONE_REASON, removed = true)
                            cleared += e
                            continue
                        }
                        rel ?: throw IllegalStateException("Couldn't find \"$path\" in your music folder, so nothing was moved.")
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
        if ((moved.isNotEmpty() || cleared.isNotEmpty()) && native != null) quickScan(app, native)
        return QueueRun(moved, failed, away = false, cleared = cleared)
    }

    // --- deleting ghost songs -------------------------------------------------------------

    private const val GONE_REASON = "Deleted: its file is gone, and Navidrome drops it at its next scan"

    /** How deleting ghost songs is going, for the page that started it. */
    data class GhostRemoval(val step: String, val done: Int = 0, val total: Int = 0)

    /** What deleting ghost songs did. [handled]: the ones dealt with (off the lists now, or soon). */
    data class GhostResult(val handled: Set<String>, val summary: String)

    /**
     * Deletes ghost songs. A ghost is a song Navidrome lists whose file it can't open, so usually
     * there's no file left to delete: what's left is Navidrome's entry for it. So:
     * 1. Navidrome is asked for a quick scan (if you're an admin), which drops the entries whose
     *    files are gone.
     * 2. Each one Navidrome still lists is tried again; one that still won't play has its file
     *    moved to the Trash on the NAS (Put back undoes it), or waits for home Wi-Fi.
     * Nothing is deleted from Navidrome by NaviMuse, and no file leaves the share.
     */
    suspend fun removeGhosts(app: NaviMuseApp, ghosts: List<GhostSongs.Ghost>, progress: (GhostRemoval) -> Unit): GhostResult =
        try {
            deleteGhosts(app, ghosts, progress)
        } catch (e: Throwable) {
            // Stopped part way: the ones not dealt with go back on the list.
            val stuck = ghosts.map { it.id }.filter { GhostSongs.all.value[it]?.reason == DELETING }
            GhostSongs.relabel(stuck, "Couldn't delete it: ${if (e is CancellationException) "stopped" else friendly(e)}", removed = false)
            throw e
        }

    private const val DELETING = "Deleting…"

    private suspend fun deleteGhosts(app: NaviMuseApp, ghosts: List<GhostSongs.Ghost>, progress: (GhostRemoval) -> Unit): GhostResult {
        val api = app.api() ?: throw IllegalStateException("Not signed in.")
        val native = NavidromeNative(app.http, app.login.value ?: throw IllegalStateException("Not signed in."))
        val ids = ghosts.map { it.id }
        // Off the Ghost songs list straight away; still kept out of mixes and queues.
        GhostSongs.relabel(ids, DELETING, removed = true)
        val admin = runCatching { native.admin() }.getOrDefault(false)
        if (admin) {
            progress(GhostRemoval("Asking Navidrome for a quick scan…"))
            runCatching { api.startQuickScan() }
            waitForScan(app) { progress(GhostRemoval("Navidrome is scanning your library…")) }
        }

        // What Navidrome still lists after its scan.
        val gone = ArrayList<String>()
        val listed = ArrayList<Pair<GhostSongs.Ghost, NavidromeNative.SongFile>>()
        val unsure = ArrayList<GhostSongs.Ghost>()
        var checked = 0
        ghosts.chunked(8).forEach { batch ->
            coroutineScope {
                batch.map { g -> async { g to runCatching { native.lookup(g.id) } } }.awaitAll()
            }.forEach { (g, r) ->
                val f = r.getOrNull()
                when {
                    r.isFailure -> unsure += g
                    f == null || f.missing -> gone += g.id
                    else -> listed += g to f
                }
            }
            checked += batch.size
            progress(GhostRemoval("Checking what Navidrome still lists…", checked, ghosts.size))
        }
        GhostSongs.clear(gone)

        // Still listed: try each again, so nothing that plays now is touched.
        val stillGhosts = ArrayList<Pair<GhostSongs.Ghost, NavidromeNative.SongFile>>()
        val playsNow = ArrayList<String>()
        checked = 0
        listed.chunked(6).forEach { batch ->
            coroutineScope {
                batch.map { (g, f) ->
                    async { Triple(g, f, runCatching { StreamCheck.run(app.http, api.streamUrl(g.id, 0), range = 1) }.getOrNull()) }
                }.awaitAll()
            }.forEach { (g, f, check) ->
                when {
                    check == StreamCheck.Audio -> playsNow += g.id
                    check == StreamCheck.Empty || (check is StreamCheck.Refused && check.songOnly) -> stillGhosts += g to f
                    else -> unsure += g
                }
            }
            checked += batch.size
            progress(GhostRemoval("Trying the ones Navidrome still lists…", checked, listed.size))
        }
        GhostSongs.clear(playsNow)

        // Their files: into the Trash, if they're still on the NAS.
        val share = app.shareLogin.value
        val moved = ArrayList<String>()
        val queued = ArrayList<String>()
        val notFound = ArrayList<String>()
        val noFolder = ArrayList<String>()
        // Without a scan, a file that isn't there is simply gone: Navidrome drops it later.
        val goneLater = ArrayList<String>()
        when {
            stillGhosts.isEmpty() -> Unit
            share == null -> noFolder += stillGhosts.map { it.first.id }
            !reachable(share.server) -> {
                val now = System.currentTimeMillis()
                TrashQueue.addAll(
                    stillGhosts.map { (g, f) -> TrashQueue.Entry(g.id, g.title, g.artist, g.album, f.path, now, ghost = true) },
                )
                queued += stillGhosts.map { it.first.id }
                GhostSongs.relabel(queued, WAITING_REASON)
                TrashQueueWorker.sync(app)
            }
            else -> lock.withLock {
                withContext(Dispatchers.IO) {
                    MusicShare(share).use { ms ->
                        stillGhosts.forEachIndexed { i, (g, f) ->
                            progress(GhostRemoval("Moving their files to the Trash…", i, stillGhosts.size))
                            val rel = runCatching { ms.locate(f.path) }.getOrNull()
                            if (rel == null) {
                                if (admin) notFound += g.id else goneLater += g.id
                            } else {
                                runCatching { ms.moveToTrash(rel) }
                                    .onSuccess { where ->
                                        GhostSongs.relabel(listOf(g.id), "Moved to the Trash ($where)")
                                        moved += g.id
                                    }
                                    .onFailure { notFound += g.id }
                            }
                        }
                    }
                }
            }
        }

        // The files that moved: Navidrome's scan drops them too.
        if (moved.isNotEmpty() && admin) {
            progress(GhostRemoval("Asking Navidrome to drop the moved files…"))
            runCatching { api.startQuickScan() }
            waitForScan(app) { progress(GhostRemoval("Navidrome is scanning your library…")) }
            val dropped = moved.filter { id -> runCatching { native.lookup(id) }.getOrNull().let { it == null || it.missing } }
            GhostSongs.clear(dropped)
        }

        // Back on the Ghost songs list, with why: these weren't dealt with.
        val back = notFound + noFolder + unsure.map { it.id }
        if (notFound.isNotEmpty()) {
            GhostSongs.relabel(notFound, "Navidrome still lists it, but its file isn't in your music folder (Settings → General → Music folder)", removed = false)
        }
        if (noFolder.isNotEmpty()) {
            GhostSongs.relabel(noFolder, "Its file is still on the NAS: set up your music folder (Settings → General) to move it to the Trash", removed = false)
        }
        if (unsure.isNotEmpty()) {
            GhostSongs.relabel(unsure.map { it.id }, "Couldn't check it just now: try deleting it again", removed = false)
        }
        if (goneLater.isNotEmpty()) GhostSongs.relabel(goneLater, GONE_REASON, removed = true)

        val handled = ids.toSet() - back.toSet() - playsNow.toSet()
        val dropped = gone.size
        val parts = buildList {
            if (dropped > 0) add("$dropped ${if (dropped == 1) "entry was" else "entries were"} dropped by Navidrome's scan (the files were gone)")
            if (moved.isNotEmpty()) add("${moved.size} still wouldn't play, so ${if (moved.size == 1) "its file" else "their files"} moved to the Trash")
            if (queued.isNotEmpty()) add("${queued.size} move to the Trash next time you're on home Wi-Fi")
            if (goneLater.isNotEmpty()) {
                add("${goneLater.size} ${if (goneLater.size == 1) "has" else "have"} no file left and leave${if (goneLater.size == 1) "s" else ""} Navidrome's lists at its next scan (only a Navidrome admin can start one)")
            }
            if (playsNow.isNotEmpty()) add("${playsNow.size} play${if (playsNow.size == 1) "s" else ""} now, so ${if (playsNow.size == 1) "it was" else "they were"} kept")
            if (back.isNotEmpty()) add("${back.size} couldn't be deleted (see Ghost songs for why)")
        }
        val summary = "Deleted ${handled.size} ghost song${if (handled.size == 1) "" else "s"}" +
            if (parts.isEmpty()) "." else ": " + parts.joinToString("; ") + "."
        return GhostResult(handled, summary)
    }

    /** Waits (up to 20 minutes) for Navidrome's scan to finish. */
    private suspend fun waitForScan(app: NaviMuseApp, onWait: () -> Unit) {
        val api = app.api() ?: return
        delay(2_000)
        val until = System.currentTimeMillis() + 20 * 60_000
        while (System.currentTimeMillis() < until) {
            if (!runCatching { api.scanning() }.getOrDefault(false)) return
            onWait()
            delay(3_000)
        }
    }

    /** Asks Navidrome to notice the files that moved, if you're an admin (else its next scan does). */
    private suspend fun quickScan(app: NaviMuseApp, native: NavidromeNative): Boolean =
        runCatching { if (native.admin()) app.api()?.startQuickScan() else null }.getOrNull() != null
}
