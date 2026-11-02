package app.navimuse

import app.navimuse.ui.icons.IconPacks
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser

class IconPacksTest {
    /** Same shape as the map in Symfonium's icon-pack template. */
    private val map = """
        <?xml version="1.0" encoding="utf-8"?>
        <symfonium-icon-pack version="1">
            <pack id="rounded-filled" label="Material Rounded Filled">
                <icon key="playback.play" drawable="@drawable/ic_filled_playback_play" />
                <icon key="navigation.home.selected" drawable="@drawable/ic_filled_navigation_home" />
                <icon key="action.missing" drawable="@drawable/does_not_exist" />
            </pack>
            <pack id="rounded" label="Material Rounded">
                <icon key="playback.play" drawable="@2131230850" />
            </pack>
        </symfonium-icon-pack>
    """.trimIndent()

    private val ids = mapOf("ic_filled_playback_play" to 11, "ic_filled_navigation_home" to 12)

    private fun parse() = IconPacks.parse(KXmlParser().apply { setInput(StringReader(map)) }) { ref ->
        ref.removePrefix("@").toIntOrNull() ?: ids[ref.substringAfter('/')]
    }

    @Test
    fun readsEveryPackInTheMap() {
        val packs = parse()
        assertEquals(listOf("rounded-filled", "rounded"), packs.map { it.id })
        assertEquals("Material Rounded Filled", packs[0].label)
        assertEquals(11, packs[0].icons["playback.play"])
        assertEquals(12, packs[0].icons["navigation.home.selected"])
        assertEquals(2131230850, packs[1].icons["playback.play"])
    }

    @Test
    fun skipsIconsWhoseDrawableIsMissing() {
        assertNull(parse()[0].icons["action.missing"])
    }
}
