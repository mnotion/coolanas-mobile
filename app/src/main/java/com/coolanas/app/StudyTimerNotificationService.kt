package com.coolanas.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

class StudyTimerNotificationService : Service() {
    companion object {
        @Volatile
        var isRunningForeground: Boolean = false
            private set

        private const val ACTION_START = "com.coolanas.app.action.START_TIMER_NOTIFICATION"
        private const val EXTRA_ELAPSED_MS = "elapsed_ms"
        private const val EXTRA_SUBJECT = "subject"

        fun show(context: Context, elapsedMs: Long, subject: String) {
            if (!CoolanasNotifications.notificationsAllowed(context)) return
            val intent = Intent(context, StudyTimerNotificationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ELAPSED_MS, elapsedMs.coerceAtLeast(0L))
                putExtra(EXTRA_SUBJECT, subject)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            isRunningForeground = false
            context.stopService(Intent(context, StudyTimerNotificationService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CoolanasNotifications.createChannels(this)
        if (intent?.action != ACTION_START || !CoolanasNotifications.notificationsAllowed(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isRunningForeground = false
            stopSelf()
            return START_NOT_STICKY
        }

        val elapsed = intent.getLongExtra(EXTRA_ELAPSED_MS, 0L).coerceAtLeast(0L)
        val subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()
        startForeground(
            CoolanasNotifications.TIMER_NOTIFICATION_ID,
            buildTimerNotification(elapsed, subject)
        )
        isRunningForeground = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunningForeground = false
        super.onDestroy()
    }

    private fun buildTimerNotification(elapsedMs: Long, subject: String): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            CoolanasNotifications.TIMER_NOTIFICATION_ID,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val whenMillis = System.currentTimeMillis() - elapsedMs
        return Notification.Builder(this, CoolanasNotifications.TIMER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Coolanas · Study timer")
            .setContentText(if (subject.isBlank()) "Studying" else "Studying · $subject")
            .setWhen(whenMillis)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setColor(0xFF9B82C7.toInt())
            .build()
    }
}
