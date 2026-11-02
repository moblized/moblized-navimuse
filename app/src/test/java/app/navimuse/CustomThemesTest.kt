package app.navimuse

import app.navimuse.ui.theme.CustomThemes
import app.navimuse.ui.theme.ThemeFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CustomThemesTest {
    /** Shaped like a theme exported from Symfonium (or downloaded from Symfonium Styles). */
    private val symfonium = """
        {"name":"Night Drive","id":"","imported":true,"schemes":{
          "light":{"primary":"#A6005C","onPrimary":"#FFFFFF","surface":"#FFF8FC","onSurface":"#1F1826","surfaceContainer":"#F2E6F5","shadow":"#000000"},
          "dark":{"primary":"#FF2E92","onPrimary":"#4A0028","surface":"#150F1C","onSurface":"#EEE3F5","surfaceContainerHigh":"#2A2235"}}}
    """.trimIndent()

    /** Shaped like Material Theme Builder's "Material Theme (JSON)" export. */
    private val materialBuilder = """
        {"description":"TYPE: CUSTOM\nMaterial Theme Builder export","seed":"#6750A4",
         "coreColors":{"primary":"#6750A4"},
         "schemes":{"light":{"primary":"#6750A4","surface":"#FEF7FF","onSurface":"#1D1B20"},
                    "light-high-contrast":{"primary":"#000000","surface":"#FFFFFF"},
                    "dark":{"primary":"#D0BCFF","surface":"#141218","onSurface":"#E6E0E9"}}}
    """.trimIndent()

    @Test
    fun readsSymfoniumThemes() {
        val t = CustomThemes.parse(symfonium)
        assertEquals("Night Drive", t.name)
        assertEquals(0xFFFF2E92L, t.dark["primary"])
        assertEquals(0xFF150F1CL, t.dark["surface"])
        assertEquals(0xFFA6005CL, t.light["primary"])
        assertEquals(0xFFF2E6F5L, t.light["surfaceContainer"])
        // Roles the app doesn't use are dropped.
        assertNull(t.light["shadow"])
    }

    @Test
    fun readsMaterialThemeBuilderExports() {
        val t = CustomThemes.parse(materialBuilder, "material-theme.json")
        assertEquals("material-theme", t.name)
        assertEquals(0xFFD0BCFFL, t.dark["primary"])
        assertEquals(0xFF6750A4L, t.light["primary"])
    }

    @Test
    fun aDarkOnlyThemeIsUsedForLightToo() {
        val t = CustomThemes.parse("""{"name":"Mocha","schemes":{"dark":{"primary":"#cba6f7","surface":"#181825"}}}""")
        assertEquals(t.dark, t.light)
    }

    @Test
    fun namesComeFromTheFileWhenTheThemeHasNone() {
        val t = CustomThemes.parse(
            """{"schemes":{"dark":{"primary":"#cba6f7","surface":"#181825"}}}""",
            "Exotic Theme-20250124_193507.json",
        )
        assertEquals("Exotic Theme", t.name)
    }

    @Test
    fun sameThemeGetsTheSameId() {
        assertEquals(CustomThemes.parse(symfonium).id, CustomThemes.parse(symfonium).id)
    }

    @Test
    fun colourFormats() {
        assertEquals(0xFF1E1E2EL, CustomThemes.parseColor("#1e1e2e"))
        assertEquals(0x801E1E2EL, CustomThemes.parseColor("#1e1e2e80"))
        assertNull(CustomThemes.parseColor("blue"))
        assertNull(CustomThemes.parseColor(""))
    }

    @Test
    fun refusesFilesThatArentThemes() {
        for (bad in listOf("not json", """{"name":"x"}""", """{"schemes":{"dark":{"onSurface":"#ffffff"}}}""")) {
            try {
                CustomThemes.parse(bad)
                fail("accepted $bad")
            } catch (e: ThemeFormatException) {
                assertTrue(e.message!!.isNotEmpty())
            }
        }
    }

    @Test
    fun symfoniumStylesLinksPointAtTheDownload() {
        assertEquals(
            "https://styles.symfonium.app/styles/neon-casette/download",
            CustomThemes.downloadUrl("Check this out https://styles.symfonium.app/styles/neon-casette"),
        )
        assertEquals(
            "https://styles.symfonium.app/styles/golden-2/download",
            CustomThemes.downloadUrl("https://styles.symfonium.app/styles/golden-2/download"),
        )
        assertEquals("https://example.com/my-theme.json", CustomThemes.downloadUrl("https://example.com/my-theme.json"))
        assertEquals("http://192.168.1.10:8080/theme.json", CustomThemes.downloadUrl("http://192.168.1.10:8080/theme.json"))
        assertNull(CustomThemes.downloadUrl("no link here"))
    }
}
