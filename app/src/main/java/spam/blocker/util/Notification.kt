package spam.blocker.util


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import spam.blocker.R


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
class Notification {
    companion object {
        fun channelId(importance: Int): String {
            return "SB_CHANNEL_$importance"
        }

        private var inited = false

        @Synchronized
        fun createChannelsOnce(ctx: Context) {
            if (!inited) {
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

                inited = true
            }
        }
        fun shouldSilent(importance: Int) : Boolean {
            return importance <= NotificationManager.IMPORTANCE_LOW
        }
        // different notification id generates different dropdown items
        fun show(ctx: Context, notificationId: Int, title: String, body: String, importance: Int, callbackIntent: Intent) {
            createChannelsOnce(ctx)

            val chId = channelId(importance)
            val builder = NotificationCompat.Builder(ctx, chId)

            if (shouldSilent(importance)) {
                builder.setSilent(true) // disable the notification sound
            }
//            builder.setColor(Color.GREEN)
//            builder.setColorized(true)
//            channel.lightColor = Color.CYAN

            val manager = ctx.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager

            builder.setChannelId(chId).
            setContentTitle(title).
            setSmallIcon(R.drawable.bell_filter).
            setContentText(body)

            val pendingIntent = PendingIntent.getActivity(ctx, 0, callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(pendingIntent)

            val notification = builder.build()
            manager.notify(notificationId, notification)
        }
        fun cancelAll(ctx: Context) {
            val manager = ctx.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancelAll()
        }
    }
}