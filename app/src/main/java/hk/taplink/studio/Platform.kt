package hk.taplink.studio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Platform {
    const val ACTION_OPEN_WALLET = "hk.taplink.studio.OPEN_WALLET"
    internal const val ALERTS_CHANNEL = "tap_balance_alerts"
    internal const val REMINDERS_CHANNEL = "tap_wallet_reminders"
    private const val JOB_ID = 0x544150
    private val io = Executors.newSingleThreadExecutor()
    private var observedPayments: Set<Long>? = null

    fun lowBalanceMessage(prefs: Prefs, finalCents: Long): String? {
        if (!prefs.alerts || finalCents >= prefs.threshold) return null
        return if (prefs.hidden) "A demo payment left your card below its low-balance threshold."
        else "After your demo payment, the final balance was ${money(finalCents)}, below your ${money(prefs.threshold)} threshold."
    }

    // The controller calls this in publication order, including its first successful load.
    @Synchronized
    fun sync(context: Context, wallet: Wallet) {
        val app = context.applicationContext
        val payments = wallet.entries.filter { it.kind == "Payment" }
        val previous = observedPayments
        observedPayments = payments.mapTo(HashSet()) { it.id }
        TapWalletWidget.sync(app, wallet)
        val newAlerts = if (previous == null || !wallet.prefs.alerts) emptyList() else {
            val newPayments = payments.filter { it.id !in previous }
            newPayments.filter { payment ->
                val reload = wallet.entries.firstOrNull {
                    it.kind == "Reload" && it.cardId == payment.cardId && it.operation == payment.operation
                }
                (reload?.after ?: payment.after) < wallet.prefs.threshold
            }.map { it.id }
        }
        io.execute {
            guarded("schedule maintenance") { ensureJob(app) }
            if (newAlerts.isNotEmpty()) guarded("deliver balance alert") {
                val enabled = Store(app).use { it.load().prefs.alerts }
                if (enabled) newAlerts.forEach { paymentId ->
                    post(app, ALERTS_CHANNEL, "payment/$paymentId", "Demo balance is low",
                        "Open your wallet to review the payment and its final balance.")
                }
            }
        }
    }

    private fun ensureJob(context: Context) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
        if (jobs.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, TapMaintenanceJob::class.java))
            .setPersisted(true)
            .setPeriodic(TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(6))
            .build()
        if (jobs.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            Log.w("TapPlatform", "Android deferred scheduling the maintenance job")
        }
    }

    internal fun dashboardIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.action = ACTION_OPEN_WALLET
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0x5441, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun post(context: Context, channel: String, tag: String, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        val name = if (channel == ALERTS_CHANNEL) "Low-balance alerts" else "Demo reminders"
        manager.createNotificationChannel(NotificationChannel(channel, name, NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.tap_notification_mark)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(dashboardIntent(context))
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        // Permission and channel settings can change while the notification is being built.
        try {
            manager.notify(tag, 1, notification)
        } catch (_: SecurityException) { }
    }

    internal fun guarded(operation: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            Log.w("TapPlatform", "Could not $operation", failure)
        }
    }
}
