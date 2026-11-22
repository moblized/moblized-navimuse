package app.navimuse.net

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Your music folder on the NAS, over SMB: where NaviMuse moves a song you delete. It goes into a
 * `_Trash` folder at the top of the music folder, at the same place inside it, so it can be put
 * back. The Trash gets an empty `.ndignore`, so Navidrome doesn't list what's in it.
 *
 * Every move is checked by SafetyGuard.checkMove (only into the Trash and back) before it
 * happens. The one deletion is [emptyOld]: songs that have been in the Trash for 30 days (by the
 * Trash's own log of when each went in), each checked by SafetyGuard.checkPurge.
 */
data class ShareLogin(
    /** The NAS's name or address, e.g. "truenas.local" or "192.168.1.10". */
    val server: String,
    /** The share, e.g. "music". */
    val share: String,
    /** The folder inside the share that Navidrome reads as its music folder ("" for the whole share). */
    val folder: String,
    val username: String,
    val password: String,
)

/**
 * A song in the Trash: [path] inside the Trash, the same as where it was in the music folder, and
 * when it went in (by the Trash's log).
 */
data class TrashItem(val path: String, val bytes: Long, val trashedAt: Long) {
    /** When it's deleted for good, if emptying is on. */
    val deletesAt: Long get() = trashedAt + SafetyGuard.TRASH_DAYS * 24L * 60 * 60 * 1000
}

/** The share isn't there (a wrong name in Share). */
class NoSuchShare(val share: String, message: String) : IOException(message)

/** The NAS turned the SMB login away (wrong username or password, or no access to the share). */
class ShareLoginRefused(message: String) : IOException(message)

class MusicShare(private val login: ShareLogin) : Closeable {
    private val client = SMBClient(
        SmbConfig.builder()
            .withTimeout(20, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            // BouncyCastle for NTLM's hashes: Android's own crypto has no MD4.
            .withSecurityProvider(BCSecurityProvider())
            .build(),
    )
    private val connection by lazy { client.connect(login.server.trim()) }
    private val session by lazy {
        val user = login.username.trim()
        val (domain, name) = if ('\\' in user) user.substringBefore('\\') to user.substringAfter('\\') else "" to user
        try {
            connection.authenticate(AuthenticationContext(name, login.password.toCharArray(), domain))
        } catch (e: SMBApiException) {
            if (e.status == NtStatus.STATUS_LOGON_FAILURE) {
                throw ShareLoginRefused("Your NAS didn't accept that SMB username and password.")
            }
            throw e
        }
    }
    private val disk by lazy {
        val name = login.share.trim().trim('/', '\\')
        val share = try {
            session.connectShare(name)
        } catch (e: SMBApiException) {
            when (e.status) {
                NtStatus.STATUS_BAD_NETWORK_NAME ->
                    throw NoSuchShare(name, "There's no share called \"$name\" on ${login.server.trim()}.")
                NtStatus.STATUS_ACCESS_DENIED ->
                    throw ShareLoginRefused("The SMB user \"${login.username.trim()}\" isn't allowed into the share \"$name\".")
                else -> throw e
            }
        }
        share as? DiskShare ?: throw IOException("\"${login.share}\" isn't a folder share.")
    }

    private fun parts(p: String) = p.replace('\\', '/').split('/').filter { it.isNotEmpty() }

    /** [rel] (inside the music folder) as a path on the share. */
    private fun onShare(rel: String) = (parts(login.folder) + parts(rel)).joinToString("\\")

    fun exists(rel: String): Boolean = disk.fileExists(onShare(rel))

    /**
     * Where Navidrome's path for a song is in the music folder. Navidrome may put its own folder
     * names in front (like "music/"), so leading folders are dropped until the file is found.
     */
    fun locate(navidromePath: String): String? {
        val p = parts(navidromePath)
        for (drop in 0..minOf(3, p.size - 1)) {
            val rel = p.drop(drop).joinToString("/")
            if (exists(rel)) return rel
        }
        return null
    }

    private fun ensureFolders(relFolder: String) {
        var path = ""
        for (part in parts(relFolder)) {
            path = if (path.isEmpty()) part else "$path/$part"
            if (!disk.folderExists(onShare(path))) disk.mkdir(onShare(path))
        }
    }

    /** The Trash folder, with an empty .ndignore so Navidrome leaves it alone. */
    private fun ensureTrash() {
        ensureFolders(SafetyGuard.TRASH)
        val ignore = onShare("${SafetyGuard.TRASH}/.ndignore")
        if (!disk.fileExists(ignore)) {
            SafetyGuard.checkTrashWrite("${SafetyGuard.TRASH}/.ndignore")
            // FILE_CREATE: only ever a new file, never over an existing one.
            disk.openFile(
                ignore,
                setOf(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_CREATE,
                null,
            ).close()
        }
    }

    private fun move(from: String, to: String) {
        SafetyGuard.checkMove(from, to)
        if (!exists(from)) throw IOException("There's no file at $from.")
        if (exists(to)) throw IOException("There's already a file at $to, so nothing was moved.")
        ensureFolders(parts(to).dropLast(1).joinToString("/"))
        disk.openFile(
            onShare(from),
            setOf(AccessMask.DELETE, AccessMask.FILE_READ_ATTRIBUTES),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        ).use { it.rename(onShare(to), false) }
    }

    // --- the Trash's log: when each song went in ------------------------------------------

    private val logPath get() = onShare("${SafetyGuard.TRASH}/${SafetyGuard.TRASH_LOG}")

    /** Path inside the Trash → when it went in (ms). */
    private fun readLog(): MutableMap<String, Long> {
        if (!disk.fileExists(logPath)) return HashMap()
        val text = disk.openFile(logPath, setOf(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
            .use { f -> f.inputStream.use { it.readBytes() } }.decodeToString()
        return runCatching {
            val o = JSONObject(text).optJSONObject("items") ?: JSONObject()
            o.keys().asSequence().associateWith { o.optLong(it) }.filterValues { it > 0 }.toMutableMap()
        }.getOrDefault(HashMap())
    }

    private fun writeLog(log: Map<String, Long>) {
        SafetyGuard.checkTrashWrite("${SafetyGuard.TRASH}/${SafetyGuard.TRASH_LOG}")
        val items = JSONObject()
        log.forEach { (k, v) -> items.put(k, v) }
        val bytes = JSONObject().put("items", items).toString(2).toByteArray()
        disk.openFile(logPath, setOf(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null)
            .use { f -> f.outputStream.use { it.write(bytes) } }
    }

    /** Moves the song at [rel] into the Trash; returns its place in the Trash. */
    fun moveToTrash(rel: String, now: Long = System.currentTimeMillis()): String {
        ensureTrash()
        val inside = parts(rel).joinToString("/")
        val to = "${SafetyGuard.TRASH}/$inside"
        move(rel, to)
        writeLog(readLog().apply { put(inside, now) })
        return to
    }

    /** Puts a song from the Trash ([trashPath], as listed) back where it was; returns where that is. */
    fun putBack(trashPath: String): String {
        val p = parts(trashPath)
        val from = (if (p.firstOrNull() == SafetyGuard.TRASH) p else listOf(SafetyGuard.TRASH) + p).joinToString("/")
        val to = parts(from).drop(1).joinToString("/")
        move(from, to)
        val log = readLog()
        if (log.remove(to) != null) writeLog(log)
        return to
    }

    /**
     * Deletes for good the songs that have been in the Trash for [days] days or more, then any
     * folders inside the Trash left empty. A song the log doesn't know (put there by hand) is
     * logged as going in now. Returns what was deleted.
     */
    fun emptyOld(days: Int = SafetyGuard.TRASH_DAYS, now: Long = System.currentTimeMillis()): List<String> {
        if (!disk.folderExists(onShare(SafetyGuard.TRASH))) return emptyList()
        val log = readLog()
        val present = files()
        var changed = false
        // Forget what's no longer there; start the clock for what the log didn't know.
        (log.keys - present.map { it.first }.toSet()).forEach { log.remove(it); changed = true }
        present.forEach { (inside, _) -> if (inside !in log) { log[inside] = now; changed = true } }
        val deleted = ArrayList<String>()
        for ((inside, _) in present) {
            val at = log[inside] ?: continue
            val path = "${SafetyGuard.TRASH}/$inside"
            if (now - at < days * 24L * 60 * 60 * 1000) continue
            SafetyGuard.checkPurge(path, at, now, days)
            disk.rm(onShare(path))
            log.remove(inside)
            deleted += inside
            changed = true
        }
        if (deleted.isNotEmpty()) removeEmptyFolders(SafetyGuard.TRASH, top = true)
        if (changed) writeLog(log)
        return deleted
    }

    /** Folders inside the Trash with nothing left in them, deepest first. */
    private fun removeEmptyFolders(rel: String, top: Boolean): Boolean {
        var empty = true
        for (f in disk.list(onShare(rel))) {
            val name = f.fileName
            if (name == "." || name == "..") continue
            if (EnumWithValue.EnumUtils.isSet(f.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)) {
                if (!removeEmptyFolders("$rel/$name", top = false)) empty = false
            } else {
                empty = false
            }
        }
        if (empty && !top) {
            SafetyGuard.checkRemoveFolder(rel)
            disk.rmdir(onShare(rel), false)
        }
        return empty
    }

    /** The songs in the Trash: path inside it, and size. */
    private fun files(): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        fun walk(rel: String, depth: Int) {
            if (depth > 12) return
            for (f in disk.list(onShare(rel))) {
                val name = f.fileName
                if (name == "." || name == ".." || (depth == 0 && (name == ".ndignore" || name == SafetyGuard.TRASH_LOG))) continue
                val child = "$rel/$name"
                if (EnumWithValue.EnumUtils.isSet(f.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)) {
                    walk(child, depth + 1)
                } else {
                    out += child.removePrefix("${SafetyGuard.TRASH}/") to f.endOfFile
                }
            }
        }
        walk(SafetyGuard.TRASH, 0)
        return out
    }

    /** Everything in the Trash (songs only), newest first, with when each went in. */
    fun trash(now: Long = System.currentTimeMillis()): List<TrashItem> {
        if (!disk.folderExists(onShare(SafetyGuard.TRASH))) return emptyList()
        val log = readLog()
        return files().map { (inside, bytes) -> TrashItem(inside, bytes, log[inside] ?: now) }.sortedByDescending { it.trashedAt }
    }

    override fun close() {
        runCatching { client.close() }
    }
}
