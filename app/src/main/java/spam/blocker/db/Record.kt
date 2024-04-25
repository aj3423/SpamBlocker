package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.util.Log
import spam.blocker.def.Def



class Record {
    var id: Long = 0

    var peer: String = ""
    var time: Long = 0
    var result: Int = 0
    var reason: String = ""
    var read: Boolean = false

    override fun toString(): String {
        return "record: $id, $peer, $time, $result, $reason, $read"
    }
    fun isBlocked(): Boolean {
        return !isNotBlocked()
    }
    fun isNotBlocked(): Boolean {
        return (result == Db.RESULT_ALLOWED_WHITELIST) or
                (result == Db.RESULT_ALLOWED_BY_DEFAULT) or
                (result == Db.RESULT_ALLOWED_BY_RECENT_APP) or
                (result == Db.RESULT_ALLOWED_BY_REPEATED) or
                (result == Db.RESULT_ALLOWED_BY_CONTENT) or
                (result == Db.RESULT_ALLOWED_AS_CONTACT)
    }
}

abstract class RecordTable {
    abstract fun tableName(): String

    @SuppressLint("Range")
    fun _listRecordsByFilter(ctx: Context, filterSql: String): List<Record> {

        val ret: MutableList<Record> = mutableListOf()

        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(filterSql, null)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val rec = Record()

                    rec.id = it.getLong(it.getColumnIndex(Db.COLUMN_ID))
                    rec.peer = it.getString(it.getColumnIndex(Db.COLUMN_PEER))
                    rec.time = it.getLong(it.getColumnIndex(Db.COLUMN_TIME))
                    rec.result = it.getInt(it.getColumnIndex(Db.COLUMN_RESULT))
                    rec.reason = it.getString(it.getColumnIndex(Db.COLUMN_REASON))
                    rec.read = it.getInt(it.getColumnIndex(Db.COLUMN_READ)) == 1

                    ret += rec
                } while (it.moveToNext())
            }
            return ret
        }
    }

    fun listRecords(ctx: Context): List<Record> {
        val sql = "SELECT * FROM ${tableName()} ORDER BY time DESC"

        Log.d(Def.TAG, sql)
        return _listRecordsByFilter(ctx, sql)
    }

    fun addNewRecord(ctx: Context, r: Record): Long {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_PEER, r.peer)
        cv.put(Db.COLUMN_TIME, r.time)
        cv.put(Db.COLUMN_RESULT, r.result)
        cv.put(Db.COLUMN_REASON, r.reason)
        cv.put(Db.COLUMN_READ, if (r.read) 1 else 0)
        return db.insert(tableName(), null, cv)
    }
    fun addRecordWithId(ctx: Context, r: Record) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_ID, r.id)
        cv.put(Db.COLUMN_PEER, r.peer)
        cv.put(Db.COLUMN_TIME, r.time)
        cv.put(Db.COLUMN_RESULT, r.result)
        cv.put(Db.COLUMN_REASON, r.reason)
        cv.put(Db.COLUMN_READ, if (r.read) 1 else 0)
        db.insert(tableName(), null, cv)
    }
    fun deleteRecord(ctx: Context, id: Long): Boolean {
        val sql = "DELETE FROM ${tableName()} WHERE ${Db.COLUMN_ID} = $id"
        val cursor = Db.getInstance(ctx).writableDatabase.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
    }
    fun deleteAll(ctx: Context) : Boolean {
        val sql = "DELETE FROM ${tableName()} "
        val cursor = Db.getInstance(ctx).writableDatabase.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
    }
    @SuppressLint("Range")
    fun findRecordById(ctx: Context, id: Long): Record? {
        val sql = "SELECT * FROM ${tableName()} WHERE id = $id"

        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(sql, null)

        cursor.use {
            if (it.moveToFirst()) {
                val rec = Record()
                rec.id = it.getLong(it.getColumnIndex(Db.COLUMN_ID))
                rec.peer = it.getString(it.getColumnIndex(Db.COLUMN_PEER))
                rec.time = it.getLong(it.getColumnIndex(Db.COLUMN_TIME))
                rec.result = it.getInt(it.getColumnIndex(Db.COLUMN_RESULT))
                rec.reason = it.getString(it.getColumnIndex(Db.COLUMN_REASON))
                rec.read = it.getInt(it.getColumnIndex(Db.COLUMN_READ)) == 1

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

    // returns: updated rows
//    fun markAllRecordsAsRead(ctx: Context) : Int {
//        val cv = ContentValues()
//        cv.put(Db.COLUMN_READ, 1)
//        return Db.getInstance(ctx).writableDatabase.update(tableName(), cv, null, null)
//    }

    fun countRepeatedRecordsWithin(ctx: Context, phone: String, durationSeconds: Int) : Int {
        val xSecondsAgo = System.currentTimeMillis() - durationSeconds

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
open class CallTable : RecordTable() {
    override fun tableName(): String {
        return Db.TABLE_CALL
    }
}
open class SmsTable : RecordTable() {
    override fun tableName(): String {
        return Db.TABLE_SMS
    }
}

