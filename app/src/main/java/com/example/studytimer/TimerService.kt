package com.example.studytimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/**
 * 前台计时服务 — 保持进程存活，防止系统杀掉计时
 * 通知栏显示科目标签和已用时间
 */
class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_IS_COUNTDOWN = "is_countdown"
        const val EXTRA_TOTAL_MINUTES = "total_minutes"

        fun startService(context: Context, subject: String, isCountdown: Boolean, totalMinutes: Int = 0) {
            val intent = Intent(context, TimerService::class.java).apply {
                putExtra(EXTRA_SUBJECT, subject)
                putExtra(EXTRA_IS_COUNTDOWN, isCountdown)
                putExtra(EXTRA_TOTAL_MINUTES, totalMinutes)
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }

    private var startElapsed = 0L
    private var subject = ""
    private var isCountdown = false
    private var totalMinutes = 0
    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        subject = intent?.getStringExtra(EXTRA_SUBJECT) ?: "计时中"
        isCountdown = intent?.getBooleanExtra(EXTRA_IS_COUNTDOWN, false) ?: false
        totalMinutes = intent?.getIntExtra(EXTRA_TOTAL_MINUTES, 0) ?: 0
        startElapsed = SystemClock.elapsedRealtime()

        startForeground(NOTIFICATION_ID, buildNotification("00:00:00"))
        handler.post(updateRunnable)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "计时器",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示正在进行的计时"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(timeText: String): Notification {
        // 点击通知打开主界面
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (isCountdown) "⏳ 倒计时 · $subject" else "⏱ 计时 · $subject"
        val content = if (isCountdown) "已用 $timeText / ${totalMinutes}分钟" else timeText

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun updateNotification() {
        val elapsed = SystemClock.elapsedRealtime() - startElapsed
        val totalSeconds = elapsed / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val timeText = String.format("%02d:%02d:%02d", h, m, s)

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(timeText))
    }
}
