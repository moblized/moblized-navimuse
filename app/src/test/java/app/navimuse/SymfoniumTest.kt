package app.navimuse

import app.navimuse.ui.symfonium.HomeSource
import app.navimuse.ui.symfonium.NpElement
import app.navimuse.ui.symfonium.Shortcut
import app.navimuse.ui.symfonium.SymfoniumStyles
import app.navimuse.ui.symfonium.Template
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymfoniumTest {
    private val song = mapOf(
        "title" to "Archangel", "artist" to "Burial", "album" to "Untrue", "year" to "2007",
        "genre" to "Electronic", "player.paused" to "false", "format.codec" to "FLAC",
    )
    private val fields: (String) -> String? = { song[it] }
    private fun plain(t: String) = Template.plain(t, fields)

    @Test
    fun placeholdersAndBlocks() {
        assertEquals("Archangel by Burial", plain("%title%{ by %artist%}"))
        assertEquals("Archangel", plain("%title%{ (%composer%)}"))
        assertEquals("Untrue (2007)", plain("%album%{ (%year%)}"))
    }

    @Test
    fun conditions() {
        assertEquals("2007", plain("%year|year>\"2000\"%"))
        assertEquals("", plain("%year|year<\"2000\"%"))
        assertEquals("Lossless", plain("{%|format.codec==\"flac\"%Lossless}"))
        assertEquals("Electronic", plain("%genre|genre+=\"tron\" && (artist==\"Burial\" || year==empty)%"))
        assertEquals("", plain("%genre|genre-=\"tron\"%"))
        assertEquals("ok", plain("{%|bpm==empty%ok}"))
        assertEquals("in range", plain("{%|year[]\"2000\"..\"2010\"%in range}"))
    }

    @Test
    fun symfoniumCreditTrickIsHidden() {
        // Styles hide an author credit behind an always-false condition.
        assertEquals("Archangel", plain("{⠀String by Celorien⠀%|!=%}%title%"))
    }

    @Test
    fun markupAndSegments() {
        val r = Template.render("**%title%** //%artist%//\\t%year%", fields)
        assertEquals(1, r.lines.size)
        assertEquals(2, r.lines[0].size)
        assertEquals("Archangel Burial", r.lines[0][0].text)
        assertTrue(r.lines[0][0].spanStyles.isNotEmpty())
        assertEquals("2007", r.lines[0][1].text)
        assertEquals("ARCHANGEL", plain("^^%title%^^"))
        assertEquals("Archangel\nUntrue", plain("%title%%lb%%album%"))
    }

    /** Shaped like a Symfonium "nowplaying" export: the settings are a JSON string. */
    private fun nowPlayingExport(): String {
        val compact = JSONObject()
            .put("floating", true).put("playerCornerRadius", 50).put("swipeToSkip", true)
            .put("playerMargins", JSONObject().put("horizontal", 8).put("bottom", 4))
            .put("thumbnailRoundedCorners", 50)
            .put("title", JSONObject().put("text", 100).put("customText", "%title%").put("size", 1.1))
            .put("subtitle", JSONObject().put("text", 0))
        val portrait = JSONObject()
            .put("backgroundBlurLevel", 48).put("coverArtRoundedCornerPercent", 4).put("coverArtNoCrop", true)
            .put("coverArtRotation", 0).put("sliderSize", 1.2).put("sliderShowTimes", 1)
            .put("controlsOrder", org.json.JSONArray(listOf(50, 40, 10, 11, 20, 0, 30, 12)))
            .put("controlText1", JSONObject().put("text", 100).put("customText", "**%title%**").put("style", 2).put("size", 1.5).put("lineCount", -2))
            .put("controlText2", JSONObject().put("text", 100).put("customText", "%artist%").put("color", 1))
            .put("controlText3", JSONObject().put("text", 100).put("customText", "%album%{ (%year%)}"))
            .put("topBarTitle", JSONObject().put("text", 100).put("customText", "Up next: %next.title%"))
        val style = JSONObject().put("compactConfiguration", compact).put("portraitConfiguration", portrait)
        return JSONObject().put("name", "Test Combo").put("type", "nowplaying").put("version", 1)
            .put("style", style.toString()).toString()
    }

    @Test
    fun readsNowPlayingStyles() {
        val json = nowPlayingExport()
        assertEquals("nowplaying", SymfoniumStyles.typeOf(json))
        val s = SymfoniumStyles.parseNowPlaying(json)
        assertEquals("Test Combo", s.name)
        assertTrue(s.miniFloating)
        assertEquals(50, s.miniCornerPercent)
        assertEquals("%title%", s.miniTitle?.template)
        assertNull(s.miniSubtitle)
        assertEquals(48, s.backgroundBlur)
        assertEquals(4, s.coverCornerPercent)
        assertTrue(s.coverNoCrop)
        assertFalse(s.coverRotates)
        val els = s.elements!!
        // Text 1, text 2, seek bar, buttons, text 3, in the style's order.
        assertEquals(5, els.size)
        assertEquals("**%title%**", (els[0] as NpElement.Text).line.template)
        assertTrue((els[0] as NpElement.Text).line.bold)
        assertEquals(2, (els[0] as NpElement.Text).line.maxLines)
        assertTrue((els[1] as NpElement.Text).line.accent)
        assertEquals(NpElement.Slider, els[2])
        assertEquals(NpElement.Buttons, els[3])
        assertEquals("Up next: %next.title%", s.topTitle?.template)
    }

    @Test
    fun readsAppStyles() {
        val i = JSONObject()
            .put("home.rows", "s:icon;s:shortcut;q:lastplayedsongs;q:favoritealbums;q:randomsongs;q:5starsongs;q:recentalbums")
            .put("home.rows.hidden", "q:randomsongs")
            .put("home.shortcuts", "library;search;settings;syncmanager")
            .put("library.shortcuts", "songs;playlists;artists;realalbums;genres")
            .put("library.page.configuration", JSONObject().put("gridColumns", 3).toString())
            .put("overview.row.q:lastplayedsongs", JSONObject().put("headerVisible", true).put("headerCustomText", "Jump back in").put("dataItemCount", 90).put("dataItemGridRow", 3).put("dataItemGridColumn", 3).toString())
            .put("now.playing.page.configuration", JSONObject().put("portraitConfiguration", JSONObject().put("backgroundBlurLevel", 20)).toString())
        val json = JSONObject().put("name", "Samsung-ish").put("type", "application")
            .put("style", JSONObject().put("i", i).toString()).toString()
        val a = SymfoniumStyles.parseApp(json)
        assertEquals(listOf(HomeSource.SHORTCUTS, HomeSource.RECENTLY_PLAYED, HomeSource.FAVOURITE_ALBUMS, HomeSource.RECENTLY_ADDED), a.homeRows.map { it.source })
        val jump = a.homeRows[1]
        assertEquals("Jump back in", jump.title)
        assertEquals(90, jump.count)
        assertEquals(3, jump.gridRows)
        assertEquals(listOf(Shortcut.LIBRARY, Shortcut.SEARCH, Shortcut.SETTINGS), a.shortcuts[0])
        assertEquals(listOf(Shortcut.PLAYLISTS, Shortcut.ARTISTS, Shortcut.ALBUMS, Shortcut.GENRES), a.library)
        assertEquals(3, a.gridColumns)
        assertEquals(20, a.nowPlaying?.backgroundBlur)
    }

    @Test
    fun coloursThemesAreNotStyles() {
        assertNull(SymfoniumStyles.typeOf("""{"name":"x","schemes":{"dark":{}}}"""))
    }
}
