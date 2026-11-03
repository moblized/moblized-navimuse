package app.navimuse

import app.navimuse.data.Accent
import app.navimuse.data.CollectionEntry
import app.navimuse.data.Corners
import app.navimuse.data.FontChoice
import app.navimuse.data.HomeSection
import app.navimuse.data.Look
import app.navimuse.data.MiniStyle
import app.navimuse.data.PlayerArt
import app.navimuse.data.TabChoice
import app.navimuse.data.TabLabels
import app.navimuse.data.TextSize
import app.navimuse.ui.symfonium.Shortcut
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookTest {
    @Test
    fun everyChoiceSurvivesSaving() {
        val look = Look(
            accent = Accent.PINK,
            font = FontChoice.SERIF,
            textSize = TextSize.LARGER,
            corners = Corners.ROUND,
            gridColumns = 3,
            tabs = listOf(TabChoice.COLLECTION, TabChoice.HOME),
            tabLabels = TabLabels.SELECTED,
            startTab = TabChoice.COLLECTION,
            home = listOf(HomeSection.PLAYLISTS, HomeSection.MIXES),
            collection = listOf(CollectionEntry.DOWNLOADS, CollectionEntry.ALBUMS),
            collectionRecent = false,
            playerArt = PlayerArt.RECORD,
            showShare = true,
            keepScreenOn = true,
            miniStyle = MiniStyle.FLOATING,
            miniPrevious = true,
        )
        assertEquals(look, Look.fromJson(look.toJson()))
    }

    @Test
    fun nothingSavedMeansNaviMusesOwnLook() {
        assertEquals(Look(), Look.fromJson(null))
        assertEquals(Look(), Look.fromJson(""))
        assertEquals(Look(), Look.fromJson("not json"))
        assertEquals(Look(), Look.fromJson("{}"))
    }

    @Test
    fun unknownValuesKeepTheirDefaults() {
        val look = Look.fromJson("""{"accent":"PLAID","font":"SERIF","home":["MIXES","SOMETHING_NEW","DISCOVER"]}""")
        assertEquals(Accent.THEME, look.accent)
        assertEquals(FontChoice.SERIF, look.font)
        assertEquals(listOf(HomeSection.MIXES, HomeSection.DISCOVER), look.home)
    }

    @Test
    fun noShareButtonUnlessSwitchedOn() {
        assertEquals(false, Look().showShare)
        assertEquals(false, Look.fromJson("{}").showShare)
    }

    @Test
    fun anEmptyHomeStaysEmpty() {
        val look = Look.fromJson(Look(home = emptyList()).toJson())
        assertTrue(look.home.isEmpty())
    }

    @Test
    fun settingsAlwaysStayReachable() {
        // Hiding both Home and Collection would leave no way into Settings.
        val look = Look(tabs = listOf(TabChoice.SEARCH), startTab = TabChoice.HOME).sanitized()
        assertTrue(TabChoice.COLLECTION in look.tabs)
        assertTrue(look.startTab in look.tabs)
        val saved = JSONObject(Look().toJson()).put("tabs", org.json.JSONArray(listOf("MIXES"))).toString()
        assertTrue(TabChoice.COLLECTION in Look.fromJson(saved).tabs)
    }

    @Test
    fun startTabMovesWhenItsTabIsHidden() {
        val look = Look(tabs = listOf(TabChoice.MIXES, TabChoice.COLLECTION), startTab = TabChoice.HOME).sanitized()
        assertEquals(TabChoice.MIXES, look.startTab)
    }

    @Test
    fun everyCollectionEntryOpensSomething() {
        CollectionEntry.entries.forEach { Shortcut.valueOf(it.name) }
    }

    @Test
    fun presetAccentsHaveBothShades() {
        Accent.entries.filter { it.isPreset }.forEach { a ->
            assertTrue("${a.name} light", a.light != 0L)
        }
        assertTrue(!Accent.THEME.isPreset && !Accent.ALBUM.isPreset && !Accent.PHONE.isPreset)
    }
}
