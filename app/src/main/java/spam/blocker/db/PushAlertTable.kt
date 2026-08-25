package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import kotlinx.serialization.Serializable
import spam.blocker.db.Db.Companion.COLUMN_BODY
import spam.blocker.db.Db.Companion.COLUMN_BODY_FLAGS
import spam.blocker.db.Db.Companion.COLUMN_DURATION
import spam.blocker.db.Db.Companion.COLUMN_ENABLED
import spam.blocker.db.Db.Companion.COLUMN_ID
import spam.blocker.db.Db.Companion.COLUMN_PKG_NAME
import spam.blocker.db.Db.Companion.TABLE_PUSH_ALERT
import spam.blocker.def.Def


@Serializable
data class PushAlertRecord(
    val id: Long = 0,
    val enabled: Boolean = true,
    val pkgName: String = "",
    val body: String = "",
    var bodyFlags: Int = Def.DefaultRegexFlags,
    val duration: Int = 1,
) {
    fun isValid(): Boolean {
        return pkgName != "" && body != "" && duration > 0
    }
}

object PushAlertTable : BasicTable<PushAlertRecord>(TABLE_PUSH_ALERT) {

    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): PushAlertRecord {
        return PushAlertRecord(
            id = cursor.getLong(cursor.getColumnIndex(COLUMN_ID)),
            enabled = cursor.getInt(cursor.getColumnIndex(COLUMN_ENABLED)) == 1,
            pkgName = cursor.getString(cursor.getColumnIndex(COLUMN_PKG_NAME)),
            body = cursor.getString(cursor.getColumnIndex(COLUMN_BODY)),
            bodyFlags = cursor.getInt(cursor.getColumnIndex(COLUMN_BODY_FLAGS)),
            duration = cursor.getInt(cursor.getColumnIndex(COLUMN_DURATION)),
        )
    }

    override fun toContentValues(item: PushAlertRecord, includeId: Boolean): ContentValues {
        val cv = ContentValues()
        if (includeId) {
            cv.put(COLUMN_ID, item.id)
        }
        cv.put(COLUMN_ENABLED, item.enabled)
        cv.put(COLUMN_PKG_NAME, item.pkgName)
        cv.put(COLUMN_BODY, item.body)
        cv.put(COLUMN_BODY_FLAGS, item.bodyFlags)
        cv.put(COLUMN_DURATION, item.duration)
        return cv
    }
}
