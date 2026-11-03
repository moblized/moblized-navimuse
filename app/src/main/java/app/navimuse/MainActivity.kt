package app.navimuse

import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.IntentCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.navimuse.data.Accent
import app.navimuse.ui.AppRoot
import app.navimuse.ui.components.rememberArtColorOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.isDark
import app.navimuse.ui.icons.IconPacks
import app.navimuse.ui.icons.LocalIconPack
import app.navimuse.ui.theme.AppTheme
import app.navimuse.ui.theme.CustomThemes
import app.navimuse.update.Updater
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** Whether the app is showing its dark theme, so the home-screen icon can match. */
    private var darkTheme: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .crossfade(true)
                .build()
        }

        // Only on a fresh start: after a rotation the same intent comes back and was already handled.
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val themes by CustomThemes.all.collectAsStateWithLifecycle()
            val dark = settings.isDark(isSystemInDarkTheme())
            SideEffect { darkTheme = dark }
            // Status and navigation bar icons follow the app's look, not the phone's.
            LaunchedEffect(dark) {
                val bars = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            val iconPack = remember(settings.iconPack, iconPackRefresh) {
                settings.iconPack?.let { IconPacks.load(this@MainActivity, it) }
            }
            // When the accent follows the song, the theme needs the playing song's art.
            val playingArt by remember {
                vm.playerState.map { st -> st.current?.song?.let { it.coverArt to it.album.ifBlank { it.title } } }.distinctUntilChanged()
            }.collectAsStateWithLifecycle(null)
            val songAccent = if (settings.look.accent == Accent.ALBUM) {
                playingArt?.let { (art, seed) -> rememberArtColorOf(remember(art) { vm.coverUrl(art, 160) }, seed) }
            } else {
                null
            }
            AppTheme(
                dark = dark,
                custom = themes.firstOrNull { it.id == settings.themeId },
                look = settings.look,
                songAccent = songAccent,
            ) {
                CompositionLocalProvider(LocalIconPack provides iconPack) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * "Update ready" notification: install it. A colour theme file opened with (or shared to)
     * NaviMuse: import it.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(Updater.EXTRA_INSTALL, false)) {
            intent.removeExtra(Updater.EXTRA_INSTALL)
            vm.installUpdate()
            return
        }
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { vm.importTheme(it) }
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> vm.importTheme(stream)
                    text != null -> vm.importThemeFromLink(text)
                }
            }
            else -> return
        }
        // Handled once; don't import again when the screen is recreated.
        setIntent(Intent(this, MainActivity::class.java))
    }

    /** Bumped when the app comes back, so an icon pack updated in the meantime is re-read. */
    private var iconPackRefresh by mutableIntStateOf(0)

    override fun onStart() {
        super.onStart()
        iconPackRefresh++
        vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Switch to the dark (or light) app icon once the app is out of sight.
        if (!isChangingConfigurations) darkTheme?.let { AppIcon.sync(this, it) }
    }
}
