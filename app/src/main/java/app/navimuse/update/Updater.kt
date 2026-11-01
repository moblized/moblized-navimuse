package app.navimuse.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.navimuse.BuildConfig
import app.navimuse.MainActivity
import app.navimuse.NaviMuseApp
import app.navimuse.R
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** A version published on the app's GitHub Releases page. */
data class Release(val versionCode: Int, val versionName: String, val apkUrl: String)

/**
 * Keeps the app up to date from its own GitHub Releases. New versions are downloaded in the
 * background and installed with one tap. Android checks they're signed with the same key as
 * the installed app, so only builds from this repository can replace it.
 */
object Updater {
    const val REPO = "moblized/moblized-navimuse"
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 42
    const val EXTRA_INSTALL = "app.navimuse.INSTALL_UPDATE"

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        data class Downloading(val release: Release, val percent: Int) : Status
        /** [note] explains anything you need to do first, such as allowing installs. */
        data class Ready(val release: Release, val apk: File, val note: String? = null) : Status
        data object Installing : Status
        data class Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val lock = Mutex()

    /** Picks the version and APK out of GitHub's "latest release" answer. Tags look like v1.0.42. */
    fun parse(json: String): Release? {
        val o = JSONObject(json)
        val tag = o.optString("tag_name")
        val code = tag.substringAfterLast('.').toIntOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
            ?.takeIf { it.startsWith("https://") }
            ?: return null
        return Release(code, tag.removePrefix("v"), apk)
    }

    fun isNewer(r: Release): Boolean = r.versionCode > BuildConfig.VERSION_CODE

    /**
     * Looks for a newer version and downloads it. Returns it once it's ready to install,
     * or null when this is already the newest.
     */
    suspend fun checkAndDownload(app: NaviMuseApp): Release? = lock.withLock {
        (status.value as? Status.Ready)?.let { if (it.apk.exists()) return it.release }
        _status.value = Status.Checking
        try {
            val release = latest(app)
            if (release == null || !isNewer(release)) {
                _status.value = Status.UpToDate
                return null
            }
            val apk = download(app, release)
            _status.value = Status.Ready(release, apk)
            release
        } catch (e: Exception) {
            _status.value = Status.Failed(e.message ?: "Couldn't reach GitHub.")
            throw e
        }
    }

    private suspend fun latest(app: NaviMuseApp): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        app.http.newCall(request).execute().use { r ->
            if (r.code == 404) return@withContext null
            if (!r.isSuccessful) throw IOException("GitHub answered ${r.code}.")
            parse(r.body?.string().orEmpty())
        }
    }

    private suspend fun download(app: NaviMuseApp, release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "navimuse-${release.versionCode}.apk")
        if (target.exists() && target.length() > 0) return@withContext target
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, target.name + ".part")
        val request = Request.Builder().url(release.apkUrl).build()
        app.http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("The download answered ${r.code}.")
            val body = r.body ?: throw IOException("The download was empty.")
            val total = body.contentLength()
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) _status.value = Status.Downloading(release, (done * 100 / total).toInt())
                    }
                }
            }
        }
        if (!part.renameTo(target)) throw IOException("Couldn't save the update.")
        target
    }

    /**
     * Hands the downloaded APK to Android's installer. The first time, Android may ask you to
     * allow installs from NaviMuse and to confirm; on Android 12 and newer later updates
     * install without a prompt.
     */
    fun install(context: Context) {
        val ready = status.value as? Status.Ready ?: return
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            _status.value = ready.copy(note = "Allow NaviMuse to install updates, then tap Install again.")
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        try {
            val installer = pm.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("navimuse.apk", 0, ready.apk.length()).use { out ->
                    ready.apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, UpdateReceiver::class.java).setPackage(context.packageName),
                    flags,
                )
                _status.value = Status.Installing
                session.commit(callback.intentSender)
            }
        } catch (e: Exception) {
            _status.value = Status.Failed("Couldn't start the install: ${e.message}")
        }
    }

    internal fun onInstallFinished(ok: Boolean, message: String?) {
        if (!ok) _status.value = Status.Failed(message ?: "The update didn't install.")
    }

    /** Puts the "ready" status back after Android's confirmation screen was dismissed. */
    internal fun restoreReady(context: Context) {
        val dir = File(context.cacheDir, "updates")
        val apk = dir.listFiles()?.firstOrNull { it.name.endsWith(".apk") } ?: return
        val code = apk.name.removePrefix("navimuse-").removeSuffix(".apk").toIntOrNull() ?: return
        if (code > BuildConfig.VERSION_CODE) {
            _status.value = Status.Ready(Release(code, "1.0.$code", ""), apk)
        }
    }

    /** "Version 1.0.N is ready", which opens the app and installs it. */
    fun notifyReady(context: Context, release: Release) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.updates_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.updates_channel_description)
            },
        )
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_INSTALL, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("NaviMuse ${release.versionName} is ready")
            .setContentText("Tap to install the update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }
}
