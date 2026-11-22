package app.navimuse.trash

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.navimuse.NaviMuseApp
import app.navimuse.data.TrashQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * While NaviMuse is running (say, playing in the background on the drive home): when the phone
 * joins a Wi-Fi network and songs are waiting, tries moving them right away instead of waiting
 * for the next background check.
 */
object HomeWatch {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false
    private var pending: Job? = null
    @Volatile private var triedOn: Network? = null

    fun start(app: NaviMuseApp) {
        if (started) return
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        started = true
        runCatching {
            cm.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        // Once per network joined: these also come for signal changes.
                        if (network == triedOn || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return
                        if (tryMoving(app)) triedOn = network
                    }

                    override fun onLost(network: Network) {
                        if (network == triedOn) triedOn = null
                    }
                },
            )
        }
    }

    /** Starts a try if songs are waiting; says whether it did. */
    private fun tryMoving(app: NaviMuseApp): Boolean {
        if (TrashQueue.waiting().isEmpty()) return false
        if (pending?.isActive == true) return true
        pending = scope.launch {
            // A moment for the new network to settle (address, name lookups).
            delay(5_000)
            val run = runCatching { TrashMover.runQueue(app, retryProblems = false) }.getOrNull() ?: return@launch
            if (run.moved.isNotEmpty()) TrashQueueWorker.notifyMoved(app, run.moved)
            TrashQueueWorker.sync(app)
        }
        return true
    }
}
