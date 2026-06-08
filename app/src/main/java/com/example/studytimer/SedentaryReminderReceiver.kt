package com.example.studytimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 久坐提醒广播接收器
 * 由 AlarmManager 定时触发，发送通知提醒用户起身活动
 */
class SedentaryReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "sedentary_reminder_channel"
        const val NOTIFICATION_ID = 2001

        /** 创建通知渠道（应用启动时调用） */
        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "久坐提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "定时提醒起身活动"
                setShowBadge(true)
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        createChannel(context)

        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🧘 该休息一下啦")
            .setContentText("已经学习很久了，起来活动活动身体吧！")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }
}
