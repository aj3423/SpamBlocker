package spam.blocker.db

import android.annotation.SuppressLint
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.graphics.toArgb
import androidx.core.database.getBlobOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.Serializable
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Db.Companion.COLUMN_CHANNEL_ID
import spam.blocker.db.Db.Companion.COLUMN_GROUP
import spam.blocker.db.Db.Companion.COLUMN_ICON
import spam.blocker.db.Db.Companion.COLUMN_ICON_COLOR
import spam.blocker.db.Db.Companion.COLUMN_ID
import spam.blocker.db.Db.Companion.COLUMN_IMPORTANCE
import spam.blocker.db.Db.Companion.COLUMN_LED
import spam.blocker.db.Db.Companion.COLUMN_LED_COLOR
import spam.blocker.db.Db.Companion.COLUMN_MUTE
import spam.blocker.db.Db.Companion.COLUMN_REPEAT
import spam.blocker.db.Db.Companion.COLUMN_REPEAT_INTERVAL
import spam.blocker.db.Db.Companion.COLUMN_SOUND
import spam.blocker.db.Db.Companion.TABLE_NOTIFICATION_CHANNEL
import spam.blocker.ui.theme.SkyBlue


object Notification {
    const val DefaultRepeatInterval = 5 // min

    // Built-in channel Ids, in system settings.
    const val CHANNEL_NONE = "None"
    const val CHANNEL_LOW = "Low"
    const val CHANNEL_MEDIUM = "Medium"
    const val CHANNEL_HIGH = "High"
    const val CHANNEL_HIGH_MUTED = "High Muted"

    @Serializable
    data class Channel(
        val id: Long = 0,

        val channelId: String = "",
        val importance: Int = IMPORTANCE_HIGH,

        // optional
        val group: String = "", // "" == Auto (depends on channelId and call/sms)
        val mute : Boolean = false, // for active SMS chat
        var sound: String = "", // "" for default sound
        val icon: ByteArray? = null, // icon bytes, null == Auto choose call/sms icon
        val iconColor: Int? = null, // ARGB, red for block, Unspecified for allowed. "" == Auto choose
        var led: Boolean = false,
        var ledColor: Int = SkyBlue.toArgb(),
        var repeat: Boolean = false,
        var repeatInterval: Int? = null, // min
    ) {

        fun shouldSilent(): Boolean {
            return importance <= IMPORTANCE_LOW
        }
        fun displayName(ctx: Context) : String {
            return when(channelId) {
                CHANNEL_NONE -> ctx.getString(R.string.none)
                CHANNEL_LOW -> ctx.getString(R.string.low)
                CHANNEL_MEDIUM -> ctx.getString(R.string.medium)
                CHANNEL_HIGH -> ctx.getString(R.string.high)
                CHANNEL_HIGH_MUTED -> ctx.getString(R.string.high_muted)
                else -> channelId
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Channel

            if (id != other.id) return false
            if (importance != other.importance) return false
            if (mute != other.mute) return false
            if (iconColor != other.iconColor) return false
            if (led != other.led) return false
            if (ledColor != other.ledColor) return false
            if (channelId != other.channelId) return false
            if (group != other.group) return false
            if (sound != other.sound) return false
            if (!icon.contentEquals(other.icon)) return false
            if (repeat != other.repeat) return false
            if (repeatInterval != other.repeatInterval) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + importance
            result = 31 * result + mute.hashCode()
            result = 31 * result + (iconColor ?: 0)
            result = 31 * result + led.hashCode()
            result = 31 * result + ledColor
            result = 31 * result + channelId.hashCode()
            result = 31 * result + group.hashCode()
            result = 31 * result + sound.hashCode()
            result = 31 * result + (icon?.contentHashCode() ?: 0)
            result = 31 * result + repeat.hashCode()
            result = 31 * result + repeatInterval.hashCode()

            return result
        }
    }

    object ChannelTable : BasicTable<Channel>(TABLE_NOTIFICATION_CHANNEL) {

        @SuppressLint("Range")
        override fun fromCursor(cursor: Cursor): Channel {
            return Channel(
                id = cursor.getLong(cursor.getColumnIndex(COLUMN_ID)),
                channelId = cursor.getString(cursor.getColumnIndex(COLUMN_CHANNEL_ID)),
                importance = cursor.getInt(cursor.getColumnIndex(COLUMN_IMPORTANCE)),
                mute = cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_MUTE)) == 1,
                sound = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_SOUND)) ?: "",
                icon = cursor.getBlobOrNull(cursor.getColumnIndex(COLUMN_ICON)),
                iconColor = cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_ICON_COLOR)),
                group = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_GROUP)) ?: "",
                led = cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_LED)) == 1,
                ledColor = cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_LED_COLOR)) ?: G.palette.infoBlue.toArgb(),
                repeat = cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_REPEAT)) == 1,
                repeatInterval = cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_REPEAT_INTERVAL)),
            )
        }

        override fun toContentValues(item: Channel, includeId: Boolean): ContentValues {
            val cv = ContentValues()
            if (includeId) {
                cv.put(COLUMN_ID, item.id)
            }
            cv.put(COLUMN_CHANNEL_ID, item.channelId)
            cv.put(COLUMN_IMPORTANCE, item.importance)
            cv.put(COLUMN_MUTE, item.mute)
            cv.put(COLUMN_SOUND, item.sound)
            cv.put(COLUMN_ICON, item.icon)
            cv.put(COLUMN_ICON_COLOR, item.iconColor)
            cv.put(COLUMN_GROUP, item.group)
            cv.put(COLUMN_LED, item.led)
            cv.put(COLUMN_LED_COLOR, item.ledColor)
            cv.put(COLUMN_REPEAT, item.repeat)
            cv.put(COLUMN_REPEAT_INTERVAL, item.repeatInterval)
            return cv
        }

        fun add(ctx: Context, ch: Channel, db: SQLiteDatabase? = null): Long {
            if (db != null) {
                return db.insert(TABLE_NOTIFICATION_CHANNEL, null, toContentValues(ch, includeId = false))
            }
            return addNew(ctx, ch)
        }

        fun findByChannelId(ctx: Context, channelId: String) : Channel? {
            return findFirst(ctx, "$COLUMN_CHANNEL_ID = ?", arrayOf(channelId))
        }

        fun deleteByChannelId(ctx: Context, channelId: String): Int {
            val args = arrayOf(channelId)
            val deletedCount = Db.getInstance(ctx).writableDatabase
                .delete(TABLE_NOTIFICATION_CHANNEL, "$COLUMN_CHANNEL_ID = ?", args)
            return deletedCount
        }
    }
}
