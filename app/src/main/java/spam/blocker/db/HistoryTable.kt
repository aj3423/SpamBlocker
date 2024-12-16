package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import spam.blocker.def.Def
import spam.blocker.util.Now

data class HistoryRecord(
    val id: Long = 0,

    val peer: String = "",
    val time: Long = 0,

    val result: Int = 0, // e.g.: RESULT_ALLOWED_BY_RECENT_APPS
    // An extra information for the `result`
    //  e.g.: pkgName for RecentApps, or API server echo for API query
    val reason: String = "",

    // Generic extra information that not limited to any particular `result` type
    //  e.g. SMS content
    val extraInfo: String? = null,

    val read: Boolean = false,
    val expanded: Boolean = false,
) {
    fun isBlocked(): Boolean {
        return Def.isBlocked(result)
    }
    fun isNotBlocked(): Boolean {
        return Def.isNotBlocked(result)
    }
}

abstract class HistoryTable {
    abstract fun tableName(): String

    @SuppressLint("Range")
    fun _listRecordsByFilter(ctx: Context, filterSql: String): List<HistoryRecord> {

        val ret: MutableList<HistoryRecord> = mutableListOf()

        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(filterSql, null)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val rec = HistoryRecord(
                        id = it.getLong(it.getColumnIndex(Db.COLUMN_ID)),
                        peer = it.getString(it.getColumnIndex(Db.COLUMN_PEER)),
                        time = it.getLong(it.getColumnIndex(Db.COLUMN_TIME)),
                        result = it.getInt(it.getColumnIndex(Db.COLUMN_RESULT)),
                        reason = it.getString(it.getColumnIndex(Db.COLUMN_REASON)),
                        read = it.getInt(it.getColumnIndex(Db.COLUMN_READ)) == 1,
                        extraInfo = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_EXTRA_INFO)),
                        expanded = it.getIntOrNull(it.getColumnIndex(Db.COLUMN_EXPANDED)) == 1,
                    )

                    ret += rec
                } while (it.moveToNext())
            }
            return ret
        }
    }

    fun listRecords(ctx: Context): List<HistoryRecord> {
        val sql = "SELECT * FROM ${tableName()} ORDER BY time DESC"

        return _listRecordsByFilter(ctx, sql)
    }

    fun addNewRecord(ctx: Context, r: HistoryRecord): Long {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_PEER, r.peer)
        cv.put(Db.COLUMN_TIME, r.time)
        cv.put(Db.COLUMN_RESULT, r.result)
        cv.put(Db.COLUMN_REASON, r.reason)
        cv.put(Db.COLUMN_READ, if (r.read) 1 else 0)
        cv.put(Db.COLUMN_EXTRA_INFO, r.extraInfo)
        cv.put(Db.COLUMN_EXPANDED, if(r.expanded) 1 else 0)
        return db.insert(tableName(), null, cv)
    }

    fun addRecordWithId(ctx: Context, r: HistoryRecord) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_ID, r.id)
        cv.put(Db.COLUMN_PEER, r.peer)
        cv.put(Db.COLUMN_TIME, r.time)
        cv.put(Db.COLUMN_RESULT, r.result)
        cv.put(Db.COLUMN_REASON, r.reason)
        cv.put(Db.COLUMN_READ, if (r.read) 1 else 0)
        cv.put(Db.COLUMN_EXTRA_INFO, r.extraInfo)
        cv.put(Db.COLUMN_EXPANDED, if(r.expanded) 1 else 0)
        db.insert(tableName(), null, cv)
    }

    fun clearRecordsBeforeTimestamp(ctx: Context, timestamp: Long) : Boolean {
        val sql = "DELETE FROM ${tableName()} WHERE ${Db.COLUMN_TIME} < $timestamp"
        val cursor = Db.getInstance(ctx).writableDatabase.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
    }

    fun delById(ctx: Context, id: Long): Boolean {
        val sql = "DELETE FROM ${tableName()} WHERE ${Db.COLUMN_ID} = $id"
        val cursor = Db.getInstance(ctx).writableDatabase.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
    }
    fun clearAll(ctx: Context) {
        val db = Db.getInstance(ctx).writableDatabase
        val sql = "DELETE FROM ${tableName()} "
        db.execSQL(sql)
    }
    @SuppressLint("Range")
    fun findRecordById(ctx: Context, id: Long): HistoryRecord? {
        val sql = "SELECT * FROM ${tableName()} WHERE id = $id"

        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(sql, null)

        cursor.use {
            if (it.moveToFirst()) {
                val rec = HistoryRecord(
                    id = it.getLong(it.getColumnIndex(Db.COLUMN_ID)),
                    peer = it.getString(it.getColumnIndex(Db.COLUMN_PEER)),
                    time = it.getLong(it.getColumnIndex(Db.COLUMN_TIME)),
                    result = it.getInt(it.getColumnIndex(Db.COLUMN_RESULT)),
                    reason = it.getString(it.getColumnIndex(Db.COLUMN_REASON)),
                    read = it.getInt(it.getColumnIndex(Db.COLUMN_READ)) == 1,
                    extraInfo = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_EXTRA_INFO)),
                    expanded = it.getIntOrNull(it.getColumnIndex(Db.COLUMN_EXPANDED)) == 1,
                )

                return rec
            } else {
                return null
            }
        }
    }
    fun markAsRead(ctx: Context, id: Long): Boolean {
        val cv = ContentValues()
        cv.put(Db.COLUMN_READ, 1)
        return Db.getInstance(ctx).writableDatabase.update(tableName(), cv, "${Db.COLUMN_ID} = $id", null) == 1
    }
    fun markAllAsRead(ctx: Context): Boolean {
        val cv = ContentValues()
        cv.put(Db.COLUMN_READ, 1)
        return Db.getInstance(ctx).writableDatabase.update(tableName(), cv, null, null) == 1
    }
    fun setExpanded(ctx: Context, id: Long, expanded: Boolean): Boolean {
        val cv = ContentValues()
        cv.put(Db.COLUMN_EXPANDED, if(expanded) 1 else 0)
        return Db.getInstance(ctx).writableDatabase.update(tableName(), cv, "${Db.COLUMN_ID} = $id", null) == 1
    }

    fun countRepeatedRecordsWithinSeconds(ctx: Context, phone: String, durationSeconds: Int) : Int {
        val xSecondsAgo = Now.currentMillis() - durationSeconds*1000

        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${tableName()} " +
                    "WHERE ${Db.COLUMN_TIME} > $xSecondsAgo AND ${Db.COLUMN_PEER} = '$phone'"
            , null)

        cursor.use {
            var count = 0
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0)
            }
            return count
        }
    }
}
open class CallTable : HistoryTable() {
    override fun tableName(): String {
        return Db.TABLE_CALL
    }
}
open class SmsTable : HistoryTable() {
    override fun tableName(): String {
        return Db.TABLE_SMS
    }
}
