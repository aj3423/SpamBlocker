package spam.blocker.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.net.toUri
import spam.blocker.db.Notification.DefaultRepeatInterval
import spam.blocker.util.Notification
import spam.blocker.util.Permission
import spam.blocker.util.logi

class NotificationRepeatReceiver : BroadcastReceiver() {
    @SuppressLint("ScheduleExactAlarm")
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!Permission.scheduleAlarm.isGranted)
            return

        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId == -1) return

        val mgr = Notification.manager(ctx)
        val isAlive = mgr.activeNotifications.any { it.id == notificationId }
        if (!isAlive) {
            cancelRepeatAlarm(ctx, notificationId)
            return
        }

        val soundUriStr = intent.getStringExtra("soundUri")
        val soundUri = if (soundUriStr.isNullOrEmpty())
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else
            soundUriStr.toUri()

        if (soundUri != null) {
            try {
                val ringtone = RingtoneManager.getRingtone(ctx, soundUri)
                ringtone?.play()
            } catch (e: Exception) {
                logi("failed to play repeat notification sound: ${e.message}")
            }
        }

        // Reschedule
        val intervalMin = intent.getIntExtra("intervalMin", DefaultRepeatInterval)
        val nextIntent = Intent(ctx, NotificationRepeatReceiver::class.java).apply {
            putExtra("notificationId", notificationId)
            putExtra("soundUri", soundUriStr)
            putExtra("intervalMin", intervalMin)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            notificationId,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + intervalMin * 60 * 1000L,
            pendingIntent
        )
    }

    companion object {
        fun cancelRepeatAlarm(ctx: Context, notificationId: Int) {
            val intent = Intent(ctx, NotificationRepeatReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                ctx,
                notificationId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
