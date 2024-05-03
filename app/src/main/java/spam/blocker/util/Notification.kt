package spam.blocker.util


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import spam.blocker.R



class Notification {
    companion object {

        fun channelId(importance: Int): String {
            return when(importance) {
                0 -> "None"
                1 -> "Shade"
                2 -> "StatusBar+Shade"
                3 -> "Sound+StatusBar+Shade"
                4 -> "Heads-up+Sound+StatusBar+Shade"
                else -> ""
            }
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

//                    channel.enableLights(true)
//                    channel.lightColor = Color.RED

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
        fun show(ctx: Context, title: String, body: String, importance: Int, intent: Intent) {
            createChannelsOnce(ctx)

            val chId = channelId(importance) // 5 importance level <-> 5 channel id
            val builder = NotificationCompat.Builder(ctx, chId)

            val pendingIntent = TaskStackBuilder.create(ctx).run {
                addNextIntentWithParentStack(intent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
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
            val notificationId = System.currentTimeMillis().toInt()
            manager.notify(notificationId, notification)
        }
        fun cancelAll(ctx: Context) {
            val manager = ctx.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancelAll()
        }
    }
}