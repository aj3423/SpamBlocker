package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
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
import spam.blocker.util.Now

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

    val autoReportingLog: String? = null, // Actually for both auto/manual reporting

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

abstract class HistoryTable(
    private val tableNameStr: String
) : BasicTable<HistoryRecord>(tableNameStr) {
    fun tableName(): String = tableNameStr

    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): HistoryRecord {
        return HistoryRecord(
            id = cursor.getLong(cursor.getColumnIndex(COLUMN_ID)),
            peer = cursor.getString(cursor.getColumnIndex(COLUMN_PEER)),
            cnap = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_CNAP)),
            time = cursor.getLong(cursor.getColumnIndex(COLUMN_TIME)),
            result = cursor.getInt(cursor.getColumnIndex(COLUMN_RESULT)),
            reason = cursor.getString(cursor.getColumnIndex(COLUMN_REASON)),
            simSlot = cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_SIM_SLOT)),
            read = cursor.getInt(cursor.getColumnIndex(COLUMN_READ)) == 1,
            isTest = cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_IS_TEST)) == 1,
            extraInfo = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_EXTRA_INFO)),
            expanded = cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_EXPANDED)) == 1,
            fullScreeningLog = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_FULL_SCREENING_LOG)),
            autoReportingLog = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_AUTO_REPORTING_LOG)),
            anythingWrongScreening = cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_ANYTHING_WRONG_SCREENING)) == 1,
            anythingWrongReporting = cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_ANYTHING_WRONG_REPORTING)) == 1
        )
    }

    override fun toContentValues(item: HistoryRecord, includeId: Boolean): ContentValues {
        val cv = ContentValues()
        if (includeId) {
            cv.put(COLUMN_ID, item.id)
        }
        cv.put(COLUMN_PEER, item.peer)
        cv.put(COLUMN_CNAP, item.cnap)
        cv.put(COLUMN_TIME, item.time)
        cv.put(COLUMN_RESULT, item.result)
        cv.put(COLUMN_REASON, item.reason)
        cv.put(COLUMN_SIM_SLOT, item.simSlot)
        cv.put(COLUMN_READ, if (item.read) 1 else 0)
        cv.put(COLUMN_IS_TEST, if (item.isTest) 1 else 0)
        cv.put(COLUMN_EXTRA_INFO, item.extraInfo)
        cv.put(COLUMN_EXPANDED, if (item.expanded) 1 else 0)
        cv.put(COLUMN_FULL_SCREENING_LOG, item.fullScreeningLog)
        cv.put(COLUMN_AUTO_REPORTING_LOG, item.autoReportingLog)
        cv.put(COLUMN_ANYTHING_WRONG_SCREENING, if (item.anythingWrongScreening) 1 else 0)
        cv.put(COLUMN_ANYTHING_WRONG_REPORTING, if (item.anythingWrongReporting) 1 else 0)
        return cv
    }

    // for batch insertion
    override fun insertColumns(): List<String> = listOf(
        COLUMN_PEER, COLUMN_CNAP, COLUMN_TIME, COLUMN_RESULT,
        COLUMN_REASON, COLUMN_SIM_SLOT, COLUMN_READ, COLUMN_IS_TEST,
        COLUMN_EXTRA_INFO, COLUMN_EXPANDED, COLUMN_FULL_SCREENING_LOG, COLUMN_AUTO_REPORTING_LOG,
        COLUMN_ANYTHING_WRONG_SCREENING, COLUMN_ANYTHING_WRONG_REPORTING
    )
    // for batch insertion
    override fun bindInsertStatement(stmt: SQLiteStatement, item: HistoryRecord, baseIndex: Int) {
        stmt.bindString(baseIndex, item.peer)
        item.cnap?.let { stmt.bindString(baseIndex + 1, it) } ?: stmt.bindNull(baseIndex + 1)
        stmt.bindLong(baseIndex + 2, item.time)
        stmt.bindLong(baseIndex + 3, item.result.toLong())
        stmt.bindString(baseIndex + 4, item.reason)
        item.simSlot?.let { stmt.bindLong(baseIndex + 5, it.toLong()) } ?: stmt.bindNull(baseIndex + 5)
        stmt.bindLong(baseIndex + 6, if (item.read) 1 else 0)
        stmt.bindLong(baseIndex + 7, if (item.isTest) 1 else 0)
        item.extraInfo?.let { stmt.bindString(baseIndex + 8, it) } ?: stmt.bindNull(baseIndex + 8)
        stmt.bindLong(baseIndex + 9, if (item.expanded) 1 else 0)
        item.fullScreeningLog?.let { stmt.bindString(baseIndex + 10, it) } ?: stmt.bindNull(baseIndex + 10)
        item.autoReportingLog?.let { stmt.bindString(baseIndex + 11, it) } ?: stmt.bindNull(baseIndex + 11)
        stmt.bindLong(baseIndex + 12, if (item.anythingWrongScreening) 1 else 0)
        stmt.bindLong(baseIndex + 13, if (item.anythingWrongReporting) 1 else 0)
    }

    fun listRecords(ctx: Context): List<HistoryRecord> {
        return listAll(ctx, orderBy = "$COLUMN_TIME DESC")
    }

    fun clearRecordsBeforeTimestamp(ctx: Context, timestamp: Long) : Boolean {
        val sql = "DELETE FROM ${tableName()} WHERE $COLUMN_TIME < $timestamp"
        val cursor = Db.getInstance(ctx).writableDatabase.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
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

        return listAll(
            ctx,
            whereClause = "$COLUMN_TIME > ? AND $COLUMN_RESULT BETWEEN 10 AND 99",
            whereParams = arrayOf(xSecondsAgo.toString())
        ).isNotEmpty()
    }

    fun getRecordsWithinSeconds(ctx: Context, durationSeconds: Int) : List<HistoryRecord> {
        val xSecondsAgo = Now.currentMillis() - durationSeconds*1000

        return listAll(
            ctx,
            whereClause = "$COLUMN_TIME > ?",
            whereParams = arrayOf(xSecondsAgo.toString())
        )
    }

    fun getRepeatedRecordsWithinSeconds(ctx: Context, phone: String, durationSeconds: Int) : List<HistoryRecord> {
        val xSecondsAgo = Now.currentMillis() - durationSeconds*1000

        return listAll(
            ctx,
            whereClause = "$COLUMN_TIME > ? AND $COLUMN_PEER = ?",
            whereParams = arrayOf(xSecondsAgo.toString(), phone)
        )
    }
}

open class CallTable : HistoryTable(Db.TABLE_CALL)
open class SmsTable : HistoryTable(Db.TABLE_SMS)
