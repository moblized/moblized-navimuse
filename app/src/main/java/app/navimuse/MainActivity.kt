package app.navimuse

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.navimuse.ui.AppRoot
import app.navimuse.ui.AppViewModel
import app.navimuse.ui.isDark
import app.navimuse.ui.theme.AppTheme
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

        handleIntent(intent)

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
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
            AppTheme(dark = dark) {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Update ready" notification: install it. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(Updater.EXTRA_INSTALL, false) == true) {
            intent.removeExtra(Updater.EXTRA_INSTALL)
            vm.installUpdate()
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
