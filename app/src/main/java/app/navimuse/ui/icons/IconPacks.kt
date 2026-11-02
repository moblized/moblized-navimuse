package app.navimuse.ui.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser

/** An icon pack someone installed for Symfonium, as offered in Settings. */
data class IconPackInfo(
    /** "<package>/<pack id>": stable across updates of the pack. */
    val id: String,
    val label: String,
    val packageName: String,
)

/**
 * Symfonium icon packs (styles.symfonium.app). Each is a small APK with a service that answers
 * the "app.symfonium.intent.action.ICON_PACK" action and points, through the
 * "app.symfonium.iconpack.MAP" meta-data, at an XML map of icon keys to drawables:
 *
 *     <symfonium-icon-pack version="1">
 *         <pack id="rounded" label="Material Rounded">
 *             <icon key="playback.play" drawable="@drawable/ic_play" />
 *         </pack>
 *     </symfonium-icon-pack>
 *
 * NaviMuse reads the same map, so any Symfonium icon pack works here too. Icons a pack doesn't
 * map keep NaviMuse's own.
 */
object IconPacks {
    const val ACTION = "app.symfonium.intent.action.ICON_PACK"
    const val MAP_META = "app.symfonium.iconpack.MAP"

    /** One pack read from the XML map: its id, label and icon key → drawable resource id. */
    data class ParsedPack(val id: String, val label: String, val icons: Map<String, Int>)

    /**
     * Reads a Symfonium icon map. [resolve] turns a drawable reference ("@drawable/name", or a
     * compiled "@2131230850") into a resource id.
     */
    fun parse(parser: XmlPullParser, resolve: (String) -> Int?): List<ParsedPack> {
        val packs = ArrayList<ParsedPack>()
        var id: String? = null
        var label = ""
        var icons = LinkedHashMap<String, Int>()
        while (true) {
            when (parser.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> when (parser.name) {
                    "pack" -> {
                        id = parser.getAttributeValue(null, "id")
                        label = parser.getAttributeValue(null, "label").orEmpty()
                        icons = LinkedHashMap()
                    }
                    "icon" -> {
                        val key = parser.getAttributeValue(null, "key")
                        val ref = parser.getAttributeValue(null, "drawable")
                        if (id != null && !key.isNullOrBlank() && !ref.isNullOrBlank()) {
                            resolve(ref)?.takeIf { it != 0 }?.let { icons[key] = it }
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "pack") {
                    id?.let { packs += ParsedPack(it, label.ifBlank { it }, icons) }
                    id = null
                }
            }
        }
        return packs
    }

    private fun services(context: Context) =
        context.packageManager.queryIntentServices(Intent(ACTION), PackageManager.GET_META_DATA)

    private fun readPacks(context: Context, packageName: String, mapRes: Int): List<ParsedPack> {
        val pm = context.packageManager
        val res = pm.getResourcesForApplication(packageName)
        val parser = res.getXml(mapRes)
        try {
            return parse(parser) { ref -> resolveRef(res, packageName, ref) }
        } finally {
            parser.close()
        }
    }

    private fun resolveRef(res: Resources, packageName: String, ref: String): Int? {
        val v = ref.removePrefix("@")
        v.toIntOrNull()?.let { return it }
        val name = v.substringAfter('/')
        val type = v.substringBefore('/', "drawable").substringAfter(':')
        return res.getIdentifier(name, type, packageName).takeIf { it != 0 }
    }

    /** Every Symfonium icon pack installed on the phone. */
    fun installed(context: Context): List<IconPackInfo> = runCatching {
        services(context).flatMap { ri ->
            val si = ri.serviceInfo ?: return@flatMap emptyList()
            val map = si.metaData?.getInt(MAP_META, 0) ?: 0
            if (map == 0) return@flatMap emptyList()
            val appLabel = si.applicationInfo.loadLabel(context.packageManager).toString()
            runCatching { readPacks(context, si.packageName, map) }.getOrDefault(emptyList()).map { p ->
                IconPackInfo(
                    id = "${si.packageName}/${p.id}",
                    label = if (p.label.equals(appLabel, ignoreCase = true)) p.label else "${p.label} ($appLabel)",
                    packageName = si.packageName,
                )
            }
        }.distinctBy { it.id }.sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

    /** Loads a pack chosen in Settings, or null when it's been uninstalled. */
    fun load(context: Context, id: String): LoadedIconPack? = runCatching {
        val packageName = id.substringBefore('/')
        val packId = id.substringAfter('/')
        val si = services(context).map { it.serviceInfo }.firstOrNull { it?.packageName == packageName } ?: return null
        val map = si.metaData?.getInt(MAP_META, 0)?.takeIf { it != 0 } ?: return null
        val pack = readPacks(context, packageName, map).firstOrNull { it.id == packId } ?: return null
        LoadedIconPack(id, context.packageManager.getResourcesForApplication(packageName), pack.icons)
    }.getOrNull()
}

/** A pack in use: draws its icons on demand and keeps the recent ones. */
class LoadedIconPack(val id: String, private val res: Resources, private val icons: Map<String, Int>) {
    private val cache = LruCache<String, ImageBitmap>(160)
    private val missing = HashSet<String>()

    /**
     * The pack's icon for [key], falling back from a state variant ("….selected", "….on",
     * "….filled") to its plain key. Null when the pack doesn't cover it.
     */
    fun bitmap(key: String): ImageBitmap? {
        cache.get(key)?.let { return it }
        if (key in missing) return null
        val resId = icons[key] ?: fallbackKey(key)?.let { icons[it] }
        val bmp = resId?.let {
            runCatching {
                @Suppress("DEPRECATION")
                res.getDrawable(it, null).toBitmap(SIZE_PX, SIZE_PX).asImageBitmap()
            }.getOrNull()
        }
        if (bmp == null) missing += key else cache.put(key, bmp)
        return bmp
    }

    private fun fallbackKey(key: String): String? =
        listOf(".selected", ".on", ".filled", ".active").firstOrNull { key.endsWith(it) }?.let { key.removeSuffix(it) }

    private companion object {
        /** Drawn once at this size and scaled; sharp up to the big play button. */
        const val SIZE_PX = 144
    }
}
