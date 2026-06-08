package com.example.studytimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 定时开始学习提醒 — 用户设定每天固定时间提醒开始学习
 */
class ScheduleStartReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "schedule_start_channel"
        const val NOTIFICATION_ID = 3001

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, "学习计划提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "定时提醒开始学习"
                setShowBadge(true)
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        createChannel(context)
        val openIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📚 该学习啦！")
            .setContentText("到了设定的学习时间，开启今天的专注吧！")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }
}
