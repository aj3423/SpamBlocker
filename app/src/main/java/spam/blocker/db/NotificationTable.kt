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

    object ChannelTable {

        fun add(ctx: Context, ch: Channel, db: SQLiteDatabase? = null): Long {
            val wdb = db ?: Db.getInstance(ctx).writableDatabase

            val cv = ContentValues()
            cv.put(COLUMN_CHANNEL_ID, ch.channelId)
            cv.put(COLUMN_IMPORTANCE, ch.importance)
            cv.put(COLUMN_MUTE, ch.mute)
            cv.put(COLUMN_SOUND, ch.sound)
            cv.put(COLUMN_ICON, ch.icon)
            cv.put(COLUMN_ICON_COLOR, ch.iconColor)
            cv.put(COLUMN_GROUP, ch.group)
            cv.put(COLUMN_LED, ch.led)
            cv.put(COLUMN_LED_COLOR, ch.ledColor)
            cv.put(COLUMN_REPEAT, ch.repeat)
            cv.put(COLUMN_REPEAT_INTERVAL, ch.repeatInterval)

            return wdb.insert(TABLE_NOTIFICATION_CHANNEL, null, cv)
        }

        fun updateById(ctx: Context, id: Long, ch: Channel): Boolean {
            val db = Db.getInstance(ctx).writableDatabase
            val cv = ContentValues()

            cv.put(COLUMN_CHANNEL_ID, ch.channelId)
            cv.put(COLUMN_IMPORTANCE, ch.importance)
            cv.put(COLUMN_MUTE, ch.mute)
            cv.put(COLUMN_SOUND, ch.sound)
            cv.put(COLUMN_ICON, ch.icon)
            cv.put(COLUMN_ICON_COLOR, ch.iconColor)
            cv.put(COLUMN_GROUP, ch.group)
            cv.put(COLUMN_LED, ch.led)
            cv.put(COLUMN_LED_COLOR, ch.ledColor)
            cv.put(COLUMN_REPEAT, ch.repeat)
            cv.put(COLUMN_REPEAT_INTERVAL, ch.repeatInterval)

            return db.update(TABLE_NOTIFICATION_CHANNEL, cv, "$COLUMN_ID = $id", null) >= 0
        }

        @SuppressLint("Range")
        private fun fromCursor(it: Cursor): Channel {
            return Channel(
                id = it.getLong(it.getColumnIndex(COLUMN_ID)),
                channelId = it.getString(it.getColumnIndex(COLUMN_CHANNEL_ID)),
                importance = it.getInt(it.getColumnIndex(COLUMN_IMPORTANCE)),
                mute = it.getIntOrNull(it.getColumnIndex(COLUMN_MUTE)) == 1,
                sound = it.getStringOrNull(it.getColumnIndex(COLUMN_SOUND)) ?: "",
                icon = it.getBlobOrNull(it.getColumnIndex(COLUMN_ICON)),
                iconColor = it.getIntOrNull(it.getColumnIndex(COLUMN_ICON_COLOR)),
                group = it.getStringOrNull(it.getColumnIndex(COLUMN_GROUP)) ?: "",
                led = it.getIntOrNull(it.getColumnIndex(COLUMN_LED)) == 1,
                ledColor = it.getIntOrNull(it.getColumnIndex(COLUMN_LED_COLOR)) ?: G.palette.infoBlue.toArgb(),
                repeat = it.getIntOrNull(it.getColumnIndex(COLUMN_REPEAT)) == 1,
                repeatInterval = it.getIntOrNull(it.getColumnIndex(COLUMN_REPEAT_INTERVAL)),
            )
        }

        fun listAll(
            ctx: Context,
            whereClause: String? = null,
            whereParams: Array<String>? = null,
        ): List<Channel> {
            var sql = "SELECT * FROM $TABLE_NOTIFICATION_CHANNEL"

            whereClause?.let { sql += it }

            val ret: MutableList<Channel> = mutableListOf()

            val db = Db.getInstance(ctx).readableDatabase
            val cursor = db.rawQuery(sql, whereParams)
            cursor.use {
                if (it.moveToFirst()) {
                    do {
                        ret += fromCursor(it)
                    } while (it.moveToNext())
                }
                return ret
            }
        }
        fun findByChannelId(ctx: Context, channelId: String) : Channel? {
            val found = listAll(ctx, " WHERE $COLUMN_CHANNEL_ID = ?", arrayOf(channelId))
            return found.getOrNull(0)
        }

        fun clearAll(ctx: Context) {
            val db = Db.getInstance(ctx).writableDatabase
            val sql = "DELETE FROM $TABLE_NOTIFICATION_CHANNEL"
            db.execSQL(sql)
        }

        fun deleteById(ctx: Context, id: Long): Int {
            val args = arrayOf(id.toString())
            val deletedCount = Db.getInstance(ctx).writableDatabase
                .delete(TABLE_NOTIFICATION_CHANNEL, "$COLUMN_ID = ?", args)
            return deletedCount
        }
        fun deleteByChannelId(ctx: Context, channelId: String): Int {
            val args = arrayOf(channelId)
            val deletedCount = Db.getInstance(ctx).writableDatabase
                .delete(TABLE_NOTIFICATION_CHANNEL, "$COLUMN_CHANNEL_ID = ?", args)
            return deletedCount
        }
    }
}
