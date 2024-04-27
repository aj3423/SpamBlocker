package spam.blocker.util


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import spam.blocker.R



class Notification {
    companion object {
        val GROUP_SPAM_SMS = "sms_spam"
        val GROUP_PASS_SMS = "sms_pass"
        val GROUP_SPAM_CALL = "call_spam"

        fun channelId(importance: Int): String {
            return "SB_CHANNEL_$importance"
        }

        private var created = false
        /*
        from: https://developer.android.com/develop/ui/views/notifications/channels

            IMPORTANCE_HIGH:
                Makes a sound and appears as a heads-up notification.
            IMPORTANCE_DEFAULT:
                Makes a sound.
            IMPORTANCE_LOW:
                Makes no sound.
            IMPORTANCE_MIN:
                Makes no sound and doesn't appear in the status bar.
            IMPORTANCE_NONE:
                Makes no sound and doesn't appear in the status bar or shade.
         */
        @Synchronized
        fun createChannelsOnce(ctx: Context) {
            if (!created) {
                val manager = ctx.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
                fun create(importance: Int) {
                    val chId = channelId(importance)
                    val channel = NotificationChannel(chId, chId, importance)
                    manager.createNotificationChannel(channel)
                }

                create(NotificationManager.IMPORTANCE_HIGH)
                create(NotificationManager.IMPORTANCE_DEFAULT)
                create(NotificationManager.IMPORTANCE_LOW)
                create(NotificationManager.IMPORTANCE_MIN)
                create(NotificationManager.IMPORTANCE_NONE)

                created = true
            }
        }
        private fun shouldSilent(importance: Int) : Boolean {
            return importance <= NotificationManager.IMPORTANCE_LOW
        }
        // different notification id generates different dropdown items
        fun show(ctx: Context, notificationId: Int, title: String, body: String, importance: Int, pendingIntent: PendingIntent) {
            createChannelsOnce(ctx)

            val chId = channelId(importance) // 5 importance level <-> 5 channel id
            val builder = NotificationCompat.Builder(ctx, chId)

            builder
                .setAutoCancel(true)
                .setChannelId(chId)
                .setSmallIcon(R.drawable.bell_filter)
                .setContentTitle(title)
                .setContentText(body)
                .setSilent(shouldSilent(importance))
                .setContentIntent(pendingIntent)

            val notification = builder.build()

            val manager = ctx.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, notification)
        }
        fun cancelAll(ctx: Context) {
            val manager = ctx.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancelAll()
        }
    }
}