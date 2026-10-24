package app.navimuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.navimuse.ui.AppRoot
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.isDark
import app.navimuse.ui.theme.AppTheme
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

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = settings.isDark(isSystemInDarkTheme())
            SideEffect { darkTheme = dark }
            AppTheme(
                dark = dark,
                dynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlack && dark,
            ) {
                AppRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Switch to the dark (or light) app icon once the app is out of sight.
        if (!isChangingConfigurations) darkTheme?.let { AppIcon.sync(this, it) }
    }
}
