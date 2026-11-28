package com.coolanas.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock

class StudyTimerNotificationService : Service() {
    companion object {
        @Volatile
        var isRunningForeground: Boolean = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CoolanasNotifications.createChannels(this)
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isRunningForeground = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val elapsed = intent.getLongExtra(EXTRA_ELAPSED_MS, 0L).coerceAtLeast(0L)
                val subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()
                startForeground(
                    CoolanasNotifications.TIMER_NOTIFICATION_ID,
                    buildTimerNotification(elapsed, subject)
                )
                isRunningForeground = true
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
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
            4101,
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
            .setCategory("stopwatch")
            .setColor(0xFF9B82C7.toInt())
            .build()
    }

    companion object {
        private const val ACTION_START = "com.coolanas.app.action.START_TIMER_NOTIFICATION"
        private const val ACTION_STOP = "com.coolanas.app.action.STOP_TIMER_NOTIFICATION"
        private const val EXTRA_ELAPSED_MS = "elapsed_ms"
        private const val EXTRA_SUBJECT = "subject"

        fun show(context: android.content.Context, elapsedMs: Long, subject: String) {
            val intent = Intent(context, StudyTimerNotificationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ELAPSED_MS, elapsedMs)
                putExtra(EXTRA_SUBJECT, subject)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun hide(context: android.content.Context) {
            isRunningForeground = false
            context.stopService(Intent(context, StudyTimerNotificationService::class.java))
        }
    }
}
