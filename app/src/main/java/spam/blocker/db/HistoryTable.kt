package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import androidx.core.database.sqlite.transaction
import kotlinx.serialization.Serializable
import spam.blocker.db.Db.Companion.COLUMN_ANYTHING_WRONG_REPORTING
import spam.blocker.db.Db.Companion.COLUMN_ANYTHING_WRONG_SCREENING
import spam.blocker.db.Db.Companion.COLUMN_AUTO_REPORTING_LOG
import spam.blocker.db.Db.Companion.COLUMN_CNAP
import spam.blocker.db.Db.Companion.COLUMN_EXPANDED
import spam.blocker.db.Db.Companion.COLUMN_EXTRA_INFO
import spam.blocker.db.Db.Companion.COLUMN_FULL_SCREENING_LOG
import spam.blocker.db.Db.Companion.COLUMN_ID
import spam.blocker.db.Db.Companion.COLUMN_IS_TEST
import spam.blocker.db.Db.Companion.COLUMN_PEER
import spam.blocker.db.Db.Companion.COLUMN_READ
import spam.blocker.db.Db.Companion.COLUMN_REASON
import spam.blocker.db.Db.Companion.COLUMN_RESULT
import spam.blocker.db.Db.Companion.COLUMN_SIM_SLOT
import spam.blocker.db.Db.Companion.COLUMN_TIME
import spam.blocker.def.Def
import spam.blocker.def.Def.ANDROID_14
import spam.blocker.util.Now
import spam.blocker.util.loge

@Serializable
data class HistoryRecord(
    val id: Long = 0,

    val peer: String = "",
    val cnap: String? = null,

    val time: Long = 0,

    val result: Int = 0, // e.g.: RESULT_ALLOWED_BY_RECENT_APPS
    // An extra information for the `result`
    //  e.g.: pkgName for RecentApps, or API server echo for API query
    val reason: String = "",

    val simSlot: Int? = null,

    // Generic extra information that not limited to any particular `result` type
    //  e.g. SMS content
    val extraInfo: String? = null,

    val isTest: Boolean = false, // is it test number or real call
    val read: Boolean = false,
    val expanded: Boolean = false,

    val fullScreeningLog: String? = null,

    val autoReportingLog: String? = null,

    // if anything went wrong during the screening, e.g. api query timed out
    val anythingWrongScreening: Boolean = false,
    // if anything went wrong when reporting, e.g. timed out
    val anythingWrongReporting: Boolean = false,
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
    private fun _listRecordsByFilter(
        ctx: Context,
        whereClause: String? = null,
        whereParams: Array<String>? = null,
    ): List<HistoryRecord> {

        val ret: MutableList<HistoryRecord> = mutableListOf()

        var sql = "SELECT * FROM ${tableName()} "

        whereClause?.let { sql += it }

        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(sql, whereParams)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val rec = HistoryRecord(
                        id = it.getLong(it.getColumnIndex(COLUMN_ID)),
                        peer = it.getString(it.getColumnIndex(COLUMN_PEER)),
                        cnap = it.getStringOrNull(it.getColumnIndex(COLUMN_CNAP)),
                        time = it.getLong(it.getColumnIndex(COLUMN_TIME)),
                        result = it.getInt(it.getColumnIndex(COLUMN_RESULT)),
                        reason = it.getString(it.getColumnIndex(COLUMN_REASON)),
                        simSlot = it.getIntOrNull(it.getColumnIndex(COLUMN_SIM_SLOT)),
                        read = it.getInt(it.getColumnIndex(COLUMN_READ)) == 1,
                        isTest = it.getIntOrNull(it.getColumnIndex(COLUMN_IS_TEST)) == 1,
                        extraInfo = it.getStringOrNull(it.getColumnIndex(COLUMN_EXTRA_INFO)),
                        expanded = it.getIntOrNull(it.getColumnIndex(COLUMN_EXPANDED)) == 1,
                        fullScreeningLog = it.getStringOrNull(it.getColumnIndex(COLUMN_FULL_SCREENING_LOG)),
                        autoReportingLog = it.getStringOrNull(it.getColumnIndex(COLUMN_AUTO_REPORTING_LOG)),
                        anythingWrongScreening = it.getIntOrNull(it.getColumnIndex(COLUMN_ANYTHING_WRONG_SCREENING)) == 1,
                        anythingWrongReporting = it.getIntOrNull(it.getColumnIndex(COLUMN_ANYTHING_WRONG_REPORTING)) == 1
                    )

                    ret += rec
                } while (it.moveToNext())
            }
            return ret
        }
    }

    fun listRecords(ctx: Context): List<HistoryRecord> {
        val additional = " ORDER BY time DESC"

        return _listRecordsByFilter(ctx, additional)
    }


    fun addAll(
        ctx: Context,
        records: List<HistoryRecord>,
    ): String? {
        val db = Db.getInstance(ctx).writableDatabase

        // Tested with 200k numbers in debug mode.
        // - Batch insertion: 3 seconds
        // - Single insertion: 9 seconds
        db.transaction() {
            return try {
                val batchSize = if(Build.VERSION.SDK_INT >= ANDROID_14)
                    1000
                else
                    200 // 250+ on android 10/11 would cause "Too many SQL variables"

                records.chunked(batchSize).forEach { batch ->
                    // Build placeholders: (?, ?, ?, ?) repeated for batch size
                    val placeholders = (1..batch.size).joinToString(", ") { "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" }
                    val stmt = compileStatement(
                        """
                            INSERT OR REPLACE INTO ${tableName()} 
                            ($COLUMN_PEER, $COLUMN_CNAP, $COLUMN_TIME, $COLUMN_RESULT,
                            $COLUMN_REASON, $COLUMN_SIM_SLOT, $COLUMN_READ, $COLUMN_IS_TEST,
                            $COLUMN_EXTRA_INFO, $COLUMN_EXPANDED, $COLUMN_FULL_SCREENING_LOG, $COLUMN_AUTO_REPORTING_LOG,
                            $COLUMN_ANYTHING_WRONG_SCREENING, $COLUMN_ANYTHING_WRONG_REPORTING)
                            VALUES $placeholders
                        """.trimIndent()
                    )

                    // Bind all params in sequence (14 per row)
                    batch.forEachIndexed { index, rec ->
                        val base = index * 14 + 1 // 1-based indexing
                        stmt.bindString(base, rec.peer)
                        rec.cnap?.let { stmt.bindString(base + 1, it) } ?: stmt.bindNull(base + 1)
                        stmt.bindLong(base + 2, rec.time)
                        stmt.bindLong(base + 3, rec.result.toLong())
                        stmt.bindString(base + 4, rec.reason)
                        rec.simSlot?.let { stmt.bindLong(base + 5, it.toLong()) } ?: stmt.bindNull(base + 5)
                        stmt.bindLong(base + 6, if (rec.read) 1 else 0)
                        stmt.bindLong(base + 7, if (rec.isTest) 1 else 0)
                        rec.extraInfo?.let { stmt.bindString(base + 8, it) } ?: stmt.bindNull(base + 8)
                        stmt.bindLong(base + 9, if (rec.expanded) 1 else 0)
                        rec.fullScreeningLog?.let { stmt.bindString(base + 10, it) } ?: stmt.bindNull(base + 10)
                        rec.autoReportingLog?.let { stmt.bindString(base + 11, it) } ?: stmt.bindNull(base + 11)
                        stmt.bindLong(base + 12, if (rec.anythingWrongScreening) 1 else 0)
                        stmt.bindLong(base + 13, if (rec.anythingWrongReporting) 1 else 0)
                    }

                    stmt.execute() // No return value for multi-row
                    stmt.close()
                }
                db.setTransactionSuccessful()

                null
            } catch (e: Exception) {
                loge(e.toString())
                e.toString()
            }
        }
    }

    fun addNewRecord(ctx: Context, r: HistoryRecord): Long {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(COLUMN_PEER, r.peer)
        cv.put(COLUMN_CNAP, r.cnap)
        cv.put(COLUMN_TIME, r.time)
        cv.put(COLUMN_RESULT, r.result)
        cv.put(COLUMN_REASON, r.reason)
        cv.put(COLUMN_SIM_SLOT, r.simSlot)
        cv.put(COLUMN_READ, if (r.read) 1 else 0)
        cv.put(COLUMN_IS_TEST, if (r.isTest) 1 else 0)
        cv.put(COLUMN_EXTRA_INFO, r.extraInfo)
        cv.put(COLUMN_EXPANDED, if(r.expanded) 1 else 0)
        cv.put(COLUMN_FULL_SCREENING_LOG, r.fullScreeningLog)
        cv.put(COLUMN_AUTO_REPORTING_LOG, r.autoReportingLog)
        cv.put(COLUMN_ANYTHING_WRONG_SCREENING, if(r.anythingWrongScreening) 1 else 0)
        cv.put(COLUMN_ANYTHING_WRONG_REPORTING, if(r.anythingWrongReporting) 1 else 0)

        return db.insert(tableName(), null, cv)
    }

    fun addRecordWithId(ctx: Context, r: HistoryRecord) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(COLUMN_ID, r.id)
        cv.put(COLUMN_PEER, r.peer)
        cv.put(COLUMN_CNAP, r.cnap)
        cv.put(COLUMN_TIME, r.time)
        cv.put(COLUMN_RESULT, r.result)
        cv.put(COLUMN_REASON, r.reason)
        cv.put(COLUMN_SIM_SLOT, r.simSlot)
        cv.put(COLUMN_READ, if (r.read) 1 else 0)
        cv.put(COLUMN_IS_TEST, if (r.isTest) 1 else 0)
        cv.put(COLUMN_EXTRA_INFO, r.extraInfo)
        cv.put(COLUMN_EXPANDED, if(r.expanded) 1 else 0)
        cv.put(COLUMN_FULL_SCREENING_LOG, r.fullScreeningLog)
        cv.put(COLUMN_AUTO_REPORTING_LOG, r.autoReportingLog)
        cv.put(COLUMN_ANYTHING_WRONG_SCREENING, if(r.anythingWrongScreening) 1 else 0)
        cv.put(COLUMN_ANYTHING_WRONG_REPORTING, if(r.anythingWrongReporting) 1 else 0)

        db.insert(tableName(), null, cv)
    }

    fun clearRecordsBeforeTimestamp(ctx: Context, timestamp: Long) : Boolean {
        val sql = "DELETE FROM ${tableName()} WHERE $COLUMN_TIME < $timestamp"
        val cursor = Db.getInstance(ctx).writableDatabase.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
    }

    fun delById(ctx: Context, id: Long): Boolean {
        val sql = "DELETE FROM ${tableName()} WHERE $COLUMN_ID = $id"
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
        val whereClause = " WHERE id = $id"

        return _listRecordsByFilter(ctx, whereClause)
            .firstOrNull()
    }
    fun markAsRead(ctx: Context, id: Long): Boolean {
        val cv = ContentValues()
        cv.put(COLUMN_READ, 1)
        return Db.getInstance(ctx).writableDatabase.update(tableName(), cv, "$COLUMN_ID = $id", null) == 1
    }
    fun markAllAsRead(ctx: Context): Boolean {
        val cv = ContentValues()
        cv.put(COLUMN_READ, 1)
        return Db.getInstance(ctx).writableDatabase.update(tableName(), cv, null, null) == 1
    }
    fun setExpanded(ctx: Context, id: Long, expanded: Boolean): Boolean {
        val cv = ContentValues()
        cv.put(COLUMN_EXPANDED, if(expanded) 1 else 0)
        return Db.getInstance(ctx).writableDatabase.update(tableName(), cv, "$COLUMN_ID = $id", null) == 1
    }
    fun setAutoReportLog(ctx: Context, recordId: Long, log: String, anythingWrong: Boolean): Boolean {
        val cv = ContentValues()
        cv.put(COLUMN_AUTO_REPORTING_LOG, log)
        cv.put(COLUMN_ANYTHING_WRONG_REPORTING, if(anythingWrong) 1 else 0)

        return Db.getInstance(ctx).writableDatabase.update(tableName(), cv, "$COLUMN_ID = $recordId", null) == 1
    }
    fun hasBlockedRecordsWithinSeconds(ctx: Context, durationSeconds: Int) : Boolean {
        val xSecondsAgo = Now.currentMillis() - durationSeconds*1000

        val whereClause = " WHERE $COLUMN_TIME > ? AND $COLUMN_RESULT BETWEEN 10 AND 99"

        return _listRecordsByFilter(ctx, whereClause, arrayOf(
            xSecondsAgo.toString(),

        )).isNotEmpty()
    }
    fun getRecordsWithinSeconds(ctx: Context, durationSeconds: Int) : List<HistoryRecord> {
        val xSecondsAgo = Now.currentMillis() - durationSeconds*1000

        return _listRecordsByFilter(
            ctx,
            " WHERE $COLUMN_TIME > ?",
            arrayOf(xSecondsAgo.toString()),
        )
    }
    fun getRepeatedRecordsWithinSeconds(ctx: Context, phone: String, durationSeconds: Int) : List<HistoryRecord> {
        val xSecondsAgo = Now.currentMillis() - durationSeconds*1000

        return _listRecordsByFilter(
            ctx,
            "WHERE $COLUMN_TIME > ? AND $COLUMN_PEER = ?",
            arrayOf(xSecondsAgo.toString(), phone),
        )
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
