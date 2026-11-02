package app.navimuse.ui.theme

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * A colour theme imported from Symfonium (Symfonium Styles, the Symfonium forum, Catppuccin…)
 * or exported from Material Theme Builder. Both use the same file: a JSON object whose
 * "schemes" hold a "light" and a "dark" set of Material 3 colour roles, such as
 * {"name": "Neon Cassette", "schemes": {"dark": {"primary": "#FF2E92", "surface": "#150F1C", …}}}.
 *
 * [light] and [dark] map role names ("primary", "surfaceContainerHigh"…) to ARGB colours.
 */
data class CustomTheme(val id: String, val name: String, val light: Map<String, Long>, val dark: Map<String, Long>) {
    fun roles(dark: Boolean): Map<String, Long> = if (dark) this.dark else light
}

class ThemeFormatException(message: String) : Exception(message)

object CustomThemes {
    /** Colour roles the app uses. Others in the file (fixed colours, shadow…) are ignored. */
    val roles = listOf(
        "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
        "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
        "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
        "error", "onError", "errorContainer", "onErrorContainer",
        "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
        "surfaceTint", "inverseSurface", "inverseOnSurface", "outline", "outlineVariant", "scrim",
        "surfaceBright", "surfaceDim", "surfaceContainerLowest", "surfaceContainerLow",
        "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
    )

    /**
     * Reads a theme file. Accepts Symfonium's export and Material Theme Builder's
     * "Material Theme (JSON)" export; a file with only one of light or dark uses it for both.
     */
    fun parse(json: String, fallbackName: String = "Imported theme"): CustomTheme {
        val root = try {
            JSONObject(json.trim().removePrefix("﻿"))
        } catch (e: Exception) {
            throw ThemeFormatException("That file isn't a theme (it isn't JSON).")
        }
        val schemes = root.optJSONObject("schemes")
            ?: throw ThemeFormatException("That file has no colours in it. Use a colour theme from Symfonium Styles or Material Theme Builder.")
        val light = schemes.optJSONObject("light")?.let(::readRoles).orEmpty()
        val dark = schemes.optJSONObject("dark")?.let(::readRoles).orEmpty()
        if (!usable(light) && !usable(dark)) {
            throw ThemeFormatException("That theme is missing its main colours (primary and surface).")
        }
        val name = root.optString("name").trim().ifEmpty { null }
            ?: fallbackName.substringBeforeLast('.').replace(Regex("-\\d{8}_\\d{6}$"), "").replace('_', ' ').trim().ifEmpty { "Imported theme" }
        val l = if (usable(light)) light else dark
        val d = if (usable(dark)) dark else light
        val id = slug(root.optString("id").ifEmpty { name }) + "-" + Integer.toHexString((l.hashCode() * 31 + d.hashCode()) and 0xfffff)
        return CustomTheme(id, name, l, d)
    }

    private fun usable(roles: Map<String, Long>) =
        "primary" in roles && ("surface" in roles || "background" in roles)

    private fun readRoles(o: JSONObject): Map<String, Long> =
        roles.mapNotNull { role -> parseColor(o.optString(role))?.let { role to it } }.toMap()

    /** "#RRGGBB" or "#RRGGBBAA" (with or without #) to 0xAARRGGBB. */
    fun parseColor(text: String?): Long? {
        val hex = text?.trim()?.removePrefix("#") ?: return null
        if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return when (hex.length) {
            6 -> 0xFF000000L or hex.toLong(16)
            8 -> {
                val rgba = hex.toLong(16)
                ((rgba and 0xFF) shl 24) or (rgba ushr 8)
            }
            else -> null
        }
    }

    private fun slug(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "theme" }

    /** The link to fetch for a theme: a Symfonium Styles page gets "/download" added. */
    fun downloadUrl(link: String): String? {
        val url = Regex("https?://\\S+").find(link.trim())?.value?.trimEnd('.', ',', ')', '"', '\'') ?: return null
        val styles = Regex("^https://styles\\.symfonium\\.app/styles/([A-Za-z0-9_-]+)").find(url)
        return when {
            styles != null -> "https://styles.symfonium.app/styles/${styles.groupValues[1]}/download"
            else -> url
        }
    }

    // --- saved themes ------------------------------------------------------------------------

    private var prefs: SharedPreferences? = null
    private val _all = MutableStateFlow<List<CustomTheme>>(emptyList())
    val all: StateFlow<List<CustomTheme>> = _all.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("navimuse_themes", Context.MODE_PRIVATE)
        prefs = p
        _all.value = p.all.values.mapNotNull { v -> (v as? String)?.let { runCatching { fromStored(it) }.getOrNull() } }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun find(id: String?): CustomTheme? = id?.let { wanted -> _all.value.firstOrNull { it.id == wanted } }

    fun save(theme: CustomTheme) {
        prefs?.edit()?.putString(theme.id, toStored(theme))?.apply()
        _all.value = (_all.value.filter { it.id != theme.id } + theme).sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun delete(id: String) {
        prefs?.edit()?.remove(id)?.apply()
        _all.value = _all.value.filter { it.id != id }
    }

    private fun toStored(t: CustomTheme): String = JSONObject()
        .put("id", t.id)
        .put("name", t.name)
        .put("light", JSONObject(t.light.mapValues { it.value }))
        .put("dark", JSONObject(t.dark.mapValues { it.value }))
        .toString()

    private fun fromStored(s: String): CustomTheme {
        val o = JSONObject(s)
        fun map(key: String): Map<String, Long> {
            val m = o.getJSONObject(key)
            return m.keys().asSequence().associateWith { m.getLong(it) }
        }
        return CustomTheme(o.getString("id"), o.getString("name"), map("light"), map("dark"))
    }
}
