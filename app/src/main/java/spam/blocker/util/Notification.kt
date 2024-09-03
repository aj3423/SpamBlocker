package spam.blocker.util


import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import spam.blocker.R
import spam.blocker.service.CopyToClipboardReceiver
import kotlin.random.Random


object Notification {

    fun channelId(importance: Int): String {
        return when (importance) {
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
            val manager =
                ctx.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager

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

    private fun shouldSilent(importance: Int): Boolean {
        return importance <= NotificationManager.IMPORTANCE_LOW
    }

    // different notification id generates different dropdown items
    fun show(
        ctx: Context, iconId: Int, title: String, body: String, importance: Int, color: Color?,
        intent: Intent, // notification clicking handler

        toCopy: List<String> = listOf()
    ) {
        createChannelsOnce(ctx)

        val chId = channelId(importance) // 5 importance level <-> 5 channel id
        val notificationId = System.currentTimeMillis().toInt()
        val builder = NotificationCompat.Builder(ctx, chId)

        // Use different requestCode for every pendingIntent, otherwise the
        //   previous pendingIntent will be canceled by FLAG_CANCEL_CURRENT, which causes
        //   its action button disabled
        val requestCode = Random.nextInt()

        val pendingIntent = TaskStackBuilder.create(ctx).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )
        }
        builder
            .setAutoCancel(true)
            .setChannelId(chId)
            .setSmallIcon(iconId)
            .setContentTitle(title)
            .setContentText(body)
            .setSilent(shouldSilent(importance))
            .setContentIntent(pendingIntent)

        if (color != null) {
            builder.setColorized(true)
            builder.setColor(color.toArgb())
        }


        // copy buttons
        toCopy.forEach {
            val copyIntent = Intent(ctx, CopyToClipboardReceiver::class.java).apply {
                putExtra("toCopy", it)
                putExtra("notificationId", notificationId)
            }
            val reqCode = Random.nextInt()
            val pending = PendingIntent.getBroadcast(
                ctx, reqCode, copyIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val labelCopy = ctx.getString(R.string.copy)
            builder.addAction(0, "$labelCopy: $it", pending)
        }

        val notification = builder.build()

        val manager =
            ctx.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    fun cancelById(ctx: Context, notificationId: Int) {
        val manager =
            ctx.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    fun cancelAll(ctx: Context) {
        val manager =
            ctx.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }
}