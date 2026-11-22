package app.navimuse.net

/**
 * Reads the music folder fields kindly. People type "smb://nas/media", "\\nas\media", the NAS
 * address again in Share, or the whole path ("/media/tidarr", "/mnt/tank/media/tidarr") in
 * Folder. [guesses] lists the share and folder splits worth trying, best first. Test tries them
 * in turn and keeps the first one where your songs are.
 */
object ShareGuess {
    private const val MAX_GUESSES = 6

    private fun parts(p: String) =
        p.trim().replace(Regex("^smb:", RegexOption.IGNORE_CASE), "").replace('\\', '/')
            .split('/').map { it.trim() }.filter { it.isNotEmpty() }

    fun guesses(login: ShareLogin): List<ShareLogin> {
        val server = parts(login.server)
        val host = server.firstOrNull() ?: return emptyList()
        fun notHost(p: List<String>) = if (p.firstOrNull()?.equals(host, ignoreCase = true) == true) p.drop(1) else p
        val afterHost = server.drop(1)
        val share = notHost(parts(login.share))
        val folder = notHost(parts(login.folder))
        val all = afterHost + share + folder
        val splits = LinkedHashSet<Pair<String, String>>()
        // As typed first: the first part of Share is the share's name.
        if (share.isNotEmpty()) splits += share[0] to all.drop(afterHost.size + 1).joinToString("/")
        // Then each part of the whole path as the share, with the rest as the folder in it.
        all.forEachIndexed { i, name -> splits += name to all.drop(i + 1).joinToString("/") }
        return splits
            .filterNot { (name, _) -> name.equals(host, ignoreCase = true) }
            .take(MAX_GUESSES)
            .map { (name, inside) -> ShareLogin(host, name, inside, login.username.trim(), login.password) }
    }
}
