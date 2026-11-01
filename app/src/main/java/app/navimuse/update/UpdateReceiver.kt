package app.navimuse.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/** Hears back from Android's installer: show its confirmation screen, or report a failure. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { Updater.onInstallFinished(false, "Open NaviMuse and tap Install to finish updating.") }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.onInstallFinished(true, null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.restoreReady(context)
            else -> Updater.onInstallFinished(
                false,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The update didn't install (code $status).",
            )
        }
    }
}
