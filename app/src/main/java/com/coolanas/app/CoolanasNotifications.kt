package com.coolanas.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager

object CoolanasNotifications {
    const val TIMER_CHANNEL = "coolanas_timer"
    const val MESSAGE_CHANNEL = "coolanas_messages"
    const val TIMER_NOTIFICATION_ID = 4101

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                TIMER_CHANNEL,
                "Study timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the elapsed time while your Coolanas stopwatch is running."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGE_CHANNEL,
                "Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new Coolanas direct messages."
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    fun notificationsAllowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyMessage(context: Context, senderName: String, body: String, senderId: String) {
        createChannels(context)
        if (!notificationsAllowed(context)) return
        val id = 5000 + Math.floorMod(senderId.hashCode(), 100000)
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_MESSAGES, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = body.trim().take(180).ifBlank { "You have a new message." }
        val notification = Notification.Builder(context, MESSAGE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(senderName.ifBlank { "New Coolanas message" })
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setColor(0xFF9B82C7.toInt())
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
