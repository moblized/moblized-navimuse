package app.navimuse

import app.navimuse.net.NavidromeNative
import app.navimuse.net.ServerLogin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class NavidromeNativeTest {
    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun native(base: String) = NavidromeNative(OkHttpClient(), ServerLogin(base, "moe", "pw", "salt"))

    @Test
    fun aRedirectedSignInIsStillASignIn() = runBlocking {
        // Like an http:// address sent on to https://: the sign-in must stay a POST, with its body.
        val moved = server.url("/new").toString().trimEnd('/')
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "$moved/auth/login"))
        server.enqueue(MockResponse().setBody("""{"token":"t1","isAdmin":true}"""))
        server.enqueue(MockResponse().setBody("""{"path":"Artist/Album/01.flac","libraryPath":"/music"}"""))
        val nd = native(server.url("/").toString())
        assertTrue(nd.admin())
        server.takeRequest()
        val signIn = server.takeRequest()
        assertEquals("POST", signIn.method)
        assertEquals("/new/auth/login", signIn.path)
        assertTrue(signIn.body.readUtf8().contains("\"username\":\"moe\""))
        // Later requests go straight to where it moved.
        assertEquals("Artist/Album/01.flac", nd.songFile("abc").path)
        val song = server.takeRequest()
        assertEquals("/new/api/song/abc", song.path)
        assertEquals("Bearer t1", song.getHeader("X-ND-Authorization"))
    }

    @Test
    fun aRedirectAwayFromNavidromeIsExplained() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/sso/login?rd=x"))
        try {
            runBlocking { native(server.url("/").toString()).admin() }
            fail("signed in through a login page")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("instead of Navidrome"))
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun methodNotAllowedSaysWhatToTry() {
        server.enqueue(MockResponse().setResponseCode(405))
        try {
            runBlocking { native(server.url("/").toString()).admin() }
            fail("no error")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("https://"))
        }
    }
}
