package app.navimuse

import app.navimuse.net.ShareGuess
import app.navimuse.net.ShareLogin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareGuessTest {
    private fun guess(server: String, share: String, folder: String) =
        ShareGuess.guesses(ShareLogin(server, share, folder, " moe ", "pw")).map { it.share to it.folder }

    @Test
    fun theNasAddressTypedAgainInShare() {
        // As in the screenshot: Share holds the address, Folder the whole path.
        assertEquals(listOf("media" to "tidarr", "tidarr" to ""), guess("192.168.0.26", "192.168.0.26", "/media/tidarr"))
    }

    @Test
    fun typedRightComesFirst() {
        assertEquals("music" to "", guess("truenas.local", "music", "").first())
        assertEquals("media" to "tidarr", guess("truenas.local", "media", "tidarr").first())
    }

    @Test
    fun theShareNameRepeatedInFolder() {
        val g = guess("nas", "media", "/media/tidarr")
        assertEquals("media" to "media/tidarr", g[0])
        assertTrue("media" to "tidarr" in g)
    }

    @Test
    fun windowsAndSmbStyleAddresses() {
        assertEquals("media" to "tidarr", guess("smb://nas/media", "", "tidarr").first())
        assertEquals("media" to "tidarr", guess("nas", "\\\\nas\\media\\tidarr", "").first())
    }

    @Test
    fun aTrueNasPathTriesEachFolderAsTheShare() {
        val g = guess("nas", "", "/mnt/tank/media/tidarr")
        assertTrue("media" to "tidarr" in g)
        assertTrue("tank" to "media/tidarr" in g)
    }

    @Test
    fun keepsTheLoginTidy() {
        val g = ShareGuess.guesses(ShareLogin(" 192.168.0.26 ", "192.168.0.26", "/media", " moe ", "pw")).first()
        assertEquals("192.168.0.26", g.server)
        assertEquals("moe", g.username)
        assertEquals("pw", g.password)
    }

    @Test
    fun nothingToGoOn() {
        assertTrue(guess("", "media", "").isEmpty())
        assertTrue(guess("nas", "nas", "").isEmpty())
    }
}
