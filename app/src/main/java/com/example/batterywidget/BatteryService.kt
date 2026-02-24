package com.example.batterywidget

import android.app.*
import android.content.*
import android.os.*
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class BatteryService : Service() {
    companion object {
        const val NOTIFICATION_ID = 888
        const val CHANNEL_ID = "battery_status_channel"
    }

    private var isReceiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                updateNotification(level, isCharging)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        startForeground(NOTIFICATION_ID, buildNotification(0, false))
        if (!isReceiverRegistered) {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            isReceiverRegistered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshCurrentBattery()

        return START_STICKY
    }

    private fun refreshCurrentBattery() {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        updateNotification(level, status == BatteryManager.BATTERY_STATUS_CHARGING)
    }

    private fun buildNotification(percent: Int, isCharging: Boolean): Notification {
        val remoteViews = RemoteViews(packageName, R.layout.notification_battery)
        val symbol = if (isCharging) "⚡ " else ""
        val displayText = if (percent >= 0) "$symbol$percent%" else "获取中..."
        remoteViews.setTextViewText(R.id.tv_notification_content, displayText)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notification = builder.build()

        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

        return notification
    }

    private fun updateNotification(percent: Int, isCharging: Boolean) {
        val notification = buildNotification(percent, isCharging)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "电量实时监控",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null) // silent notification
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // visible on lock screen
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    override fun onDestroy() {
        if (isReceiverRegistered) {
            unregisterReceiver(batteryReceiver)
            isReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}