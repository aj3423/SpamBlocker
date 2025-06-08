package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
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
    val duration: Int = 0,
) {
    fun isValid(): Boolean {
        return pkgName != "" && body != "" && duration > 0
    }
}

object PushAlertTable {

    fun add(ctx: Context, rec: PushAlertRecord): Long {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(COLUMN_ENABLED, rec.enabled)
        cv.put(COLUMN_PKG_NAME, rec.pkgName)
        cv.put(COLUMN_BODY, rec.body)
        cv.put(COLUMN_BODY_FLAGS, rec.bodyFlags)
        cv.put(COLUMN_DURATION, rec.duration)

        return db.insert(TABLE_PUSH_ALERT, null, cv)
    }

    fun addWithId(ctx: Context, rec: PushAlertRecord) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(COLUMN_ID, rec.id)
        cv.put(COLUMN_ENABLED, rec.enabled)
        cv.put(COLUMN_PKG_NAME, rec.pkgName)
        cv.put(COLUMN_BODY, rec.body)
        cv.put(COLUMN_BODY_FLAGS, rec.bodyFlags)
        cv.put(COLUMN_DURATION, rec.duration)

        db.insert(TABLE_PUSH_ALERT, null, cv)
    }

    fun updateById(ctx: Context, id: Long, rec: PushAlertRecord): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(COLUMN_ENABLED, rec.enabled)
        cv.put(COLUMN_PKG_NAME, rec.pkgName)
        cv.put(COLUMN_BODY, rec.body)
        cv.put(COLUMN_BODY_FLAGS, rec.bodyFlags)
        cv.put(COLUMN_DURATION, rec.duration)
        return db.update(TABLE_PUSH_ALERT, cv, "$COLUMN_ID = $id", null) >= 0
    }

    @SuppressLint("Range")
    private fun fromCursor(it: Cursor): PushAlertRecord {
        return PushAlertRecord(
            id = it.getLong(it.getColumnIndex(COLUMN_ID)),
            enabled = it.getInt(it.getColumnIndex(COLUMN_ENABLED)) == 1,
            pkgName = it.getString(it.getColumnIndex(COLUMN_PKG_NAME)),
            body = it.getString(it.getColumnIndex(COLUMN_BODY)),
            bodyFlags = it.getInt(it.getColumnIndex(COLUMN_BODY_FLAGS)),
            duration = it.getInt(it.getColumnIndex(COLUMN_DURATION)),
        )
    }

    fun listAll(
        ctx: Context,
        additionalSql: String? = null
    ): List<PushAlertRecord> {
        var sql = "SELECT * FROM $TABLE_PUSH_ALERT"

        additionalSql?.let { sql += it }

        val ret: MutableList<PushAlertRecord> = mutableListOf()

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
    fun listForPackage(
        ctx: Context,
        pkgName: String,
    ): List<PushAlertRecord> {
        return listAll(ctx, " WHERE $COLUMN_PKG_NAME = '$pkgName' AND $COLUMN_ENABLED = 1")
    }

    fun clearAll(ctx: Context) {
        val db = Db.getInstance(ctx).writableDatabase
        val sql = "DELETE FROM $TABLE_PUSH_ALERT"
        db.execSQL(sql)
    }

    fun deleteById(ctx: Context, id: Long): Int {
        val args = arrayOf(id.toString())
        val deletedCount = Db.getInstance(ctx).writableDatabase
            .delete(TABLE_PUSH_ALERT, "$COLUMN_ID = ?", args)
        return deletedCount
    }
}
