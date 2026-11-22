package app.navimuse.trash

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.navimuse.MainActivity
import app.navimuse.NaviMuseApp
import app.navimuse.R
import app.navimuse.data.TrashQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * While songs wait to move to the Trash: every 15 minutes on Wi-Fi (Android may wait longer when
 * the phone is idle), checks whether the NAS answers, and if it does, moves them, with a
 * notification saying so. It stops itself once nothing is waiting.
 */
class TrashQueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NaviMuseApp
        try {
            val run = TrashMover.runQueue(app, retryProblems = false)
            if (run.moved.isNotEmpty()) notifyMoved(app, run.moved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Tried again at the next check.
        }
        if (TrashQueue.waiting().isEmpty()) cancel(app)
        return Result.success()
    }

    companion object {
        private const val NAME = "trash-queue"
        private const val CHANNEL_ID = "trash"
        private const val NOTIFICATION_ID = 43
        private const val PUT_BACK = "You deleted it away from home; it's in _Trash on your NAS now. Put back from Settings → Playback → Trash."

        /** Keeps the check going while songs wait, and stops it once none do. */
        fun sync(context: Context) {
            if (TrashQueue.waiting().isEmpty()) {
                cancel(context)
                return
            }
            val request = PeriodicWorkRequestBuilder<TrashQueueWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        private fun notifyMoved(context: Context, moved: List<TrashQueue.Entry>) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.trash_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.trash_channel_description)
                },
            )
            val open = PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = if (moved.size == 1) "Moved “${moved[0].title}” to the Trash" else "Moved ${moved.size} songs to the Trash"
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(PUT_BACK)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        moved.joinToString("\n") { "${it.title} — ${it.artist}" } + "\n\n" + PUT_BACK,
                    ),
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID, n)
        }
    }
}
