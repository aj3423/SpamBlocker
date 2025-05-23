package spam.blocker.util


import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.NotificationManager.IMPORTANCE_MIN
import android.app.NotificationManager.IMPORTANCE_NONE
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.service.CopyToClipboardReceiver
import spam.blocker.ui.theme.Salmon
import kotlin.random.Random


object Notification {
    // A workaround for disabling default spam notifications completely by disabling
    //  notification channels in system settings.
    const val IMPORTANCE_DEFAULT_SPAM_CALL = -1
    const val IMPORTANCE_DEFAULT_SPAM_SMS = -2
    // Mute the sound when actively texting(while the default SMS app is in the foreground)
    const val IMPORTANCE_HIGH_MUTED = 100

    const val SPAM_CALL_GROUP = "spam_call"
    const val SPAM_SMS_GROUP = "spam_sms"

    enum class Type {
        VALID_SMS, SPAM_SMS, SPAM_CALL
    }

    fun iconId(type: Type): Int {
        return when (type) {
            Type.VALID_SMS -> R.drawable.ic_sms_pass
            Type.SPAM_SMS -> R.drawable.ic_sms_blocked
            Type.SPAM_CALL -> R.drawable.ic_call_blocked
        }
    }

    fun groupName(type: Type): String? {
        return when (type) {
            Type.VALID_SMS -> null
            Type.SPAM_SMS -> SPAM_SMS_GROUP
            Type.SPAM_CALL -> SPAM_CALL_GROUP
        }
    }

    fun color(type: Type): Color? {
        return when (type) {
            Type.VALID_SMS -> null
            Type.SPAM_SMS -> Salmon
            Type.SPAM_CALL -> Salmon
        }
    }

    fun channelId(importance: Int): String {
        return when (importance) {
            IMPORTANCE_DEFAULT_SPAM_CALL -> "Default spam call"
            IMPORTANCE_DEFAULT_SPAM_SMS -> "Default spam SMS"

            IMPORTANCE_NONE -> "None"
            IMPORTANCE_MIN -> "Shade"
            IMPORTANCE_LOW -> "StatusBar+Shade"
            IMPORTANCE_DEFAULT -> "Sound+StatusBar+Shade"
            IMPORTANCE_HIGH -> "Heads-up+Sound+StatusBar+Shade"

            IMPORTANCE_HIGH_MUTED -> "Heads-up+StatusBar+Shade"

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

            fun create(id: String, importance: Int, mute: Boolean = false) {
                val channel = NotificationChannel(id, id, importance).apply {
                    if (mute) {
                        setSound(null, null)
                    }
                }

                manager.createNotificationChannel(channel)
            }

            create(channelId(IMPORTANCE_HIGH_MUTED), IMPORTANCE_HIGH, mute = true) // 100

            create(channelId(IMPORTANCE_HIGH), IMPORTANCE_HIGH) // 4
            create(channelId(IMPORTANCE_DEFAULT), IMPORTANCE_DEFAULT) // 3
            create(channelId(IMPORTANCE_LOW), IMPORTANCE_LOW) // 2
            create(channelId(IMPORTANCE_MIN), IMPORTANCE_MIN) // 1
            create(channelId(IMPORTANCE_NONE), IMPORTANCE_NONE) // 0

            create(channelId(IMPORTANCE_DEFAULT_SPAM_CALL), Def.DEF_SPAM_IMPORTANCE) // -1
            create(channelId(IMPORTANCE_DEFAULT_SPAM_SMS), Def.DEF_SPAM_IMPORTANCE) // -2

            created = true
        }
    }

    private fun shouldSilent(importance: Int): Boolean {
        return importance <= IMPORTANCE_LOW
    }

    // different notification id generates different dropdown items
    fun show(
        ctx: Context, type: Type, title: String, body: String, importance: Int,
        intent: Intent, // notification clicking handler
        toCopy: List<String> = listOf(),
    ) {
        createChannelsOnce(ctx)

        val chId = channelId(importance) // 5 importance level <-> 5 channel id
        val notificationId = System.currentTimeMillis().toInt()
        val builder = NotificationCompat.Builder(ctx, chId)

        val icon = iconId(type)

        // Use different requestCode for every pendingIntent, otherwise the
        //   previous pendingIntent will be canceled by FLAG_CANCEL_CURRENT, which causes
        //   its action button disabled
        val requestCode = Random.nextInt()

        val pendingIntent = TaskStackBuilder.create(ctx).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        builder
            .setAutoCancel(true)
            .setChannelId(chId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setSilent(shouldSilent(importance))
            .setContentIntent(pendingIntent)
            .apply {
                val group = groupName(type)
                group?.let { setGroup(it) }

                val clr = color(type)
                if (clr != null) {
                    builder.setColorized(true)
                    builder.setColor(clr.toArgb())
                }
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

        // group
        when (type) {
            Type.SPAM_CALL, Type.SPAM_SMS -> {
                val name = groupName(type)
                val group = NotificationCompat.Builder(ctx, chId)
                    .setChannelId(chId)
                    .setSmallIcon(iconId(type))
                    .setContentTitle(title)
                    .setContentText(body)
                    .setColor(Salmon.toArgb())
                    .setSilent(shouldSilent(importance))
                    .setGroup(name)
                    .setGroupSummary(true)
                manager.notify(type.ordinal, group.build())
            }

            else -> {}
        }
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