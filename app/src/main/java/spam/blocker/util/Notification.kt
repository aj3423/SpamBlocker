package spam.blocker.util


import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.NotificationManager.IMPORTANCE_NONE
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.media.AudioAttributes
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Db
import spam.blocker.db.Notification.CHANNEL_ACTIVE_SMS_CHAT
import spam.blocker.db.Notification.CHANNEL_ALLOWED
import spam.blocker.db.Notification.CHANNEL_BLOCKED
import spam.blocker.db.Notification.CHANNEL_NONE
import spam.blocker.db.Notification.Channel
import spam.blocker.db.Notification.ChannelTable
import spam.blocker.service.CopyToClipboardReceiver
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.Salmon
import kotlin.random.Random


object Notification {
    enum class ShowType {
        VALID_SMS, SPAM_SMS, SPAM_CALL
    }

    fun refreshChannels(ctx: Context) {
        G.notificationChannels.apply {
            clear()
            addAll(ChannelTable.listAll(ctx))
        }
    }
    fun deleteAllChannels(ctx: Context) {
        val mgr = manager(ctx)
        mgr.getNotificationChannels().forEach {
            mgr.deleteNotificationChannel(it.id)
        }
    }
    fun createChannel(ctx: Context, channel: Channel) {
        // 1. delete it first
        deleteChannel(ctx, channel.channelId)

        // 2. create
        val c = NotificationChannel(
            channel.channelId, channel.channelId, channel.importance
        ).apply {
            if (channel.mute) {
                setSound(null, null)
            } else if (channel.sound.isNotEmpty()) {
                val attr = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                logi("set sound: ${channel.sound.toUri()}")
                setSound(channel.sound.toUri(), attr)
            }
        }
        manager(ctx).createNotificationChannel(c)
    }
    fun deleteChannel(ctx: Context, channelId: String) {
        val mgr = manager(ctx)
        mgr.deleteNotificationChannel(channelId)
    }

    /*
        0 IMPORTANCE_NONE   -> "None"
        2 IMPORTANCE_LOW    -> "StatusBar+Shade"
        4 IMPORTANCE_HIGH   -> "Heads-up+Sound+StatusBar+Shade"
    */
    fun builtInChannels() : List<Channel> {
        return listOf(
            Channel(channelId = CHANNEL_NONE, importance = IMPORTANCE_NONE),
            Channel(channelId = CHANNEL_BLOCKED, importance = IMPORTANCE_LOW),
            Channel(channelId = CHANNEL_ALLOWED, importance = IMPORTANCE_HIGH),
            Channel(channelId = CHANNEL_ACTIVE_SMS_CHAT, importance = IMPORTANCE_HIGH, mute = true),
        )
    }
    fun isBuiltInChannel(channelId: String) : Boolean {
        return listOf(
            CHANNEL_NONE, CHANNEL_ALLOWED, CHANNEL_BLOCKED, CHANNEL_ACTIVE_SMS_CHAT
        )
            .contains(channelId)
    }
    // Init the table at first launch, or upgrade 4.14 -> 4.15
    fun ensureBuiltInChannels(
        ctx: Context,
        db: SQLiteDatabase? = Db.getInstance(ctx).writableDatabase
    ) {
        val existing = manager(ctx).notificationChannels.map { it.id }
        builtInChannels().forEach { ch ->
            val alreadyExist = existing.contains(ch.channelId)
            if (!alreadyExist) {
                // 1. add to db
                ChannelTable.add(ctx, ch, db)
                // 2. create the notification channel
                createChannel(ctx, ch)
            }
        }
    }
    // Show orange warning when the channel is missing, could've been deleted.
    fun missingChannel(ctx: Context, channelId: String) : Channel {
        return Channel(
            channelId = CHANNEL_ALLOWED,
            importance = IMPORTANCE_HIGH,
            iconColor = DarkOrange.toArgb(),
        )
    }

    fun manager(ctx: Context) : NotificationManager {
        return ctx.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun cancelById(ctx: Context, notificationId: Int) {
        manager(ctx).cancel(notificationId)
    }

    fun isChannelDisabled(ctx: Context, channelId: String) : Boolean {
        val channel = manager(ctx).getNotificationChannel(channelId)
        return channel?.importance == IMPORTANCE_NONE
    }
    fun cancelAll(ctx: Context) {
        manager(ctx).cancelAll()
    }

    fun autoIcon(ctx: Context, channel: Channel, showType: ShowType) : Int {
        return if (channel.icon.isNotEmpty())
            ctx.resources.getIdentifier(channel.icon, "drawable", ctx.packageName)
        else
            when(showType) {
                ShowType.SPAM_SMS -> R.drawable.ic_sms_blocked
                ShowType.SPAM_CALL -> R.drawable.ic_call_blocked
                ShowType.VALID_SMS -> R.drawable.ic_sms_pass
            }
    }

    fun autoGroup(channel: Channel, showType: ShowType) : String {
        return if (channel.group.isNotEmpty()) {
            // when `group` is specified, use it
            channel.group
        } else {
            // when `group` is not specified, auto group by showType
            showType.toString()
        }
    }
    fun autoColor(channel: Channel, showType: ShowType) : Int {
        return channel.iconColor
            ?: when(showType) {
                ShowType.SPAM_CALL -> Salmon.toArgb()
                ShowType.SPAM_SMS -> Salmon.toArgb()
                ShowType.VALID_SMS -> Color.Unspecified.toArgb()
            }
    }
    fun autoSound(channel: Channel) : Uri? {
        return if (channel.sound.isEmpty())
            null
        else
            channel.sound.toUri()
    }

    fun show(
        ctx: Context,
        showType: ShowType,
        channel: Channel,
        title: String,
        body: String,
        intent: Intent, // notification clicking handler
        toCopy: List<String> = listOf(),
    ) {

        val chId = channel.channelId // 5 importance level <-> 5 channel id
        val notificationId = System.currentTimeMillis().toInt()
        val builder = NotificationCompat.Builder(ctx, chId)

        // Use different requestCode for every pendingIntent, otherwise the
        //   previous pendingIntent will be canceled by FLAG_CANCEL_CURRENT, which causes
        //   its action button disabled
        val requestCode = Random.nextInt()

        val shouldSilent = channel.shouldSilent()
        val icon = autoIcon(ctx, channel, showType)
        val group = autoGroup(channel, showType)
        val iconColor = autoColor(channel, showType)
        val sound = autoSound(channel)

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
            .setContentTitle(title)
            .setContentText(body)
            .setSilent(shouldSilent)
            .setContentIntent(pendingIntent)
            .setGroup(group)
            .setSmallIcon(icon)
            .setColorized(true)
            .setColor(iconColor)
            .setSound(sound)


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

        val mgr = manager(ctx)
        mgr.notify(notificationId, notification)

        // group
        run {
            val groupBuilder = NotificationCompat.Builder(ctx, chId)
                .setChannelId(chId)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(body)
                .setSilent(shouldSilent)
                .setGroup(group)
                .setGroupSummary(true)
                .setColorized(true)
                .setColor(iconColor)
                .setSound(sound)

            // Use the same id for a group, otherwise, when swiping left to
            // remove the notification group, previous notifications will appear again.
            val id = group.hashCode()
            mgr.notify(id, groupBuilder.build())
        }
    }
}