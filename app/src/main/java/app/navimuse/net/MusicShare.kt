package app.navimuse.net

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Your music folder on the NAS, over SMB: where NaviMuse moves a song you delete. It goes into a
 * `_Trash` folder at the top of the music folder, at the same place inside it, so it can be put
 * back. The Trash gets an empty `.ndignore`, so Navidrome doesn't list what's in it.
 *
 * This class can't delete anything: it has no delete at all, and every move is checked by
 * SafetyGuard.checkMove (only into the Trash and back) before it happens. Emptying the Trash is
 * up to you, from a computer.
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

/** A song in the Trash: [path] inside the Trash, the same as where it was in the music folder. */
data class TrashItem(val path: String, val bytes: Long, val trashedAt: Long)

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
        connection.authenticate(AuthenticationContext(name, login.password.toCharArray(), domain))
    }
    private val disk by lazy {
        session.connectShare(login.share.trim().trim('/', '\\')) as? DiskShare
            ?: throw IOException("\"${login.share}\" isn't a folder share.")
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

    /** Moves the song at [rel] into the Trash; returns its place in the Trash. */
    fun moveToTrash(rel: String): String {
        ensureTrash()
        val to = "${SafetyGuard.TRASH}/${parts(rel).joinToString("/")}"
        move(rel, to)
        return to
    }

    /** Puts a song from the Trash ([trashPath], as listed) back where it was; returns where that is. */
    fun putBack(trashPath: String): String {
        val p = parts(trashPath)
        val from = (if (p.firstOrNull() == SafetyGuard.TRASH) p else listOf(SafetyGuard.TRASH) + p).joinToString("/")
        val to = parts(from).drop(1).joinToString("/")
        move(from, to)
        return to
    }

    /** Everything in the Trash (songs only, not its .ndignore), newest first. */
    fun trash(): List<TrashItem> {
        if (!disk.folderExists(onShare(SafetyGuard.TRASH))) return emptyList()
        val out = ArrayList<TrashItem>()
        fun walk(rel: String, depth: Int) {
            if (depth > 12) return
            for (f in disk.list(onShare(rel))) {
                val name = f.fileName
                if (name == "." || name == ".." || name == ".ndignore") continue
                val child = "$rel/$name"
                val folder = EnumWithValue.EnumUtils.isSet(f.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
                if (folder) {
                    walk(child, depth + 1)
                } else {
                    out += TrashItem(child.removePrefix("${SafetyGuard.TRASH}/"), f.endOfFile, f.changeTime.toEpochMillis())
                }
            }
        }
        walk(SafetyGuard.TRASH, 0)
        return out.sortedByDescending { it.trashedAt }
    }

    override fun close() {
        runCatching { client.close() }
    }
}
