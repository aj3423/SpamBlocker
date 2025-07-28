package spam.blocker.db

import android.annotation.SuppressLint
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.graphics.toArgb
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.Serializable
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
import spam.blocker.db.Db.Companion.COLUMN_SOUND
import spam.blocker.db.Db.Companion.TABLE_NOTIFICATION_CHANNEL
import spam.blocker.ui.theme.SkyBlue


object Notification {

    // Built-in channel Ids, in system settings.
    const val CHANNEL_NONE = "None"
    const val CHANNEL_ALLOWED = "Allowed"
    const val CHANNEL_BLOCKED = "Blocked"
    const val CHANNEL_ACTIVE_SMS_CHAT = "Active SMS Chat"

    @Serializable
    data class Channel(
        val id: Long = 0,

        val channelId: String = "",
        val importance: Int = IMPORTANCE_HIGH,

        // optional
        val group: String = "", // "" == Auto (depends on channelId and call/sms)
        val mute : Boolean = false, // for active SMS chat
        var sound: String = "", // "" for default sound
        val icon: String = "", // The resource name, e.g. "ic_call_blocked". "" == Auto choose call/sms icon
        val iconColor: Int? = null, // ARGB, Salmon for block, Unspecified for allowed. "" == Auto choose
        val led: Boolean = false,
        val ledColor: Int = SkyBlue.toArgb(),
    ) {

        fun shouldSilent(): Boolean {
            return importance <= IMPORTANCE_LOW
        }
        fun displayName(ctx: Context) : String {
            return when(channelId) {
                CHANNEL_NONE -> ctx.getString(R.string.none)
                CHANNEL_BLOCKED -> ctx.getString(R.string.blocked)
                CHANNEL_ALLOWED -> ctx.getString(R.string.allowed)
                CHANNEL_ACTIVE_SMS_CHAT -> ctx.getString(R.string.sms_chat)
                else -> channelId
            }
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

            return db.update(TABLE_NOTIFICATION_CHANNEL, cv, "$COLUMN_ID = $id", null) >= 0
        }

        // Add a new channel, or replace the channel with the `channelId`
        fun addOrReplace(ctx: Context, ch: Channel) {
            val db = Db.getInstance(ctx).writableDatabase

            db.execSQL(
                """
                    INSERT OR REPLACE INTO $TABLE_NOTIFICATION_CHANNEL 
                    (
                        $COLUMN_CHANNEL_ID, $COLUMN_IMPORTANCE, $COLUMN_MUTE, $COLUMN_SOUND, 
                        $COLUMN_ICON, $COLUMN_ICON_COLOR, $COLUMN_GROUP, $COLUMN_LED, $COLUMN_LED_COLOR
                    )
                    VALUES
                    (
                        '${ch.channelId}', ${ch.importance}, ${if(ch.mute) 1 else 0}, '${ch.sound}',
                        '${ch.icon}', ${ch.iconColor}, '${ch.group}', ${if(ch.led) 1 else 0}, ${ch.ledColor}
                    )
                """.trimIndent()
            )
        }

        @SuppressLint("Range")
        private fun fromCursor(it: Cursor): Channel {
            return Channel(
                id = it.getLong(it.getColumnIndex(COLUMN_ID)),
                channelId = it.getString(it.getColumnIndex(COLUMN_CHANNEL_ID)),
                importance = it.getInt(it.getColumnIndex(COLUMN_IMPORTANCE)),
                mute = it.getIntOrNull(it.getColumnIndex(COLUMN_MUTE)) == 1,
                sound = it.getStringOrNull(it.getColumnIndex(COLUMN_SOUND)) ?: "",
                icon = it.getStringOrNull(it.getColumnIndex(COLUMN_ICON)) ?: "",
                iconColor = it.getIntOrNull(it.getColumnIndex(COLUMN_ICON_COLOR)),
                group = it.getStringOrNull(it.getColumnIndex(COLUMN_GROUP)) ?: "",
                led = it.getIntOrNull(it.getColumnIndex(COLUMN_LED)) == 1,
                ledColor = it.getIntOrNull(it.getColumnIndex(COLUMN_LED_COLOR)) ?: SkyBlue.toArgb()
            )
        }

        fun listAll(
            ctx: Context,
            additionalSql: String? = null
        ): List<Channel> {
            var sql = "SELECT * FROM $TABLE_NOTIFICATION_CHANNEL"

            additionalSql?.let { sql += it }

            val ret: MutableList<Channel> = mutableListOf()

            val db = Db.getInstance(ctx).readableDatabase
            val cursor = db.rawQuery(sql, null)
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
            val found = listAll(ctx, " WHERE $COLUMN_CHANNEL_ID = '$channelId'")
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
