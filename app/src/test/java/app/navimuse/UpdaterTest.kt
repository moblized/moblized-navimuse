package app.navimuse

import app.navimuse.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdaterTest {
    @Test
    fun readsVersionAndApkFromLatestRelease() {
        val r = Updater.parse(
            """{"tag_name":"v1.0.42","assets":[
                {"name":"notes.txt","browser_download_url":"https://github.com/x/notes.txt"},
                {"name":"navimuse-1.0.42.apk","browser_download_url":"https://github.com/moblized/moblized-navimuse/releases/download/v1.0.42/navimuse-1.0.42.apk"}]}""",
        )!!
        assertEquals(42, r.versionCode)
        assertEquals("1.0.42", r.versionName)
        assertEquals("https://github.com/moblized/moblized-navimuse/releases/download/v1.0.42/navimuse-1.0.42.apk", r.apkUrl)
    }

    @Test
    fun ignoresReleasesWithoutAnApkOrVersion() {
        assertNull(Updater.parse("""{"tag_name":"v1.0.7","assets":[]}"""))
        assertNull(Updater.parse("""{"tag_name":"latest","assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk"}]}"""))
        assertNull(Updater.parse("""{"tag_name":"v1.0.8","assets":[{"name":"a.apk","browser_download_url":"http://x/a.apk"}]}"""))
    }
}
