package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import androidx.core.database.sqlite.transaction
import kotlinx.serialization.Serializable
import spam.blocker.db.Db.Companion.COLUMN_ID
import spam.blocker.db.Db.Companion.COLUMN_PEER
import spam.blocker.db.Db.Companion.COLUMN_REASON
import spam.blocker.db.Db.Companion.COLUMN_REASON_EXTRA
import spam.blocker.db.Db.Companion.COLUMN_TIME
import spam.blocker.db.Db.Companion.TABLE_SPAM
import spam.blocker.util.loge

enum class ImportDbReason {
    Manually,
    ByAPI, // Only used by presets
}
fun intToImportDbReason(i: Int?): ImportDbReason {
    return when (i) {
        1 -> ImportDbReason.ByAPI
        else -> ImportDbReason.Manually
    }
}

@Serializable
data class SpamNumber(
    val id: Long = 0,
    val peer: String = "",
    val time: Long = 0,
    val importReason: ImportDbReason = ImportDbReason.Manually,
    // when importReason is ByAPI, this value is the domain name
    val importReasonExtra: String? = null,
)

object SpamTable {

    fun addAll(
        ctx: Context,
        numbers: List<SpamNumber>,
    ): String? {
        val db = Db.getInstance(ctx).writableDatabase

        return db.transaction() {
            try {
                for (number in numbers) {
                    insertWithOnConflict(TABLE_SPAM, null, ContentValues().apply {
                        put(COLUMN_PEER, number.peer)
                        put(COLUMN_TIME, number.time)
                        put(COLUMN_REASON, number.importReason.ordinal)
                        put(COLUMN_REASON_EXTRA, number.importReasonExtra)
                    }, CONFLICT_REPLACE)
                }
                null
            } catch (e: Exception) {
                loge(e.toString())
                e.toString()
            }
        }
    }

    fun add(ctx: Context, rawNumber: String) {
        addAll(ctx, listOf(SpamNumber(peer = rawNumber, time = System.currentTimeMillis())))
    }

    @SuppressLint("Range")
    private fun ruleFromCursor(it: Cursor): SpamNumber {
        return SpamNumber(
            id = it.getLong(it.getColumnIndex(COLUMN_ID)),
            peer = it.getString(it.getColumnIndex(COLUMN_PEER)),
            time = it.getLong(it.getColumnIndex(COLUMN_TIME)),
            importReason = intToImportDbReason(it.getIntOrNull(it.getColumnIndex(COLUMN_REASON))),
            importReasonExtra = it.getStringOrNull(it.getColumnIndex(COLUMN_REASON_EXTRA)) ,
        )
    }

    fun listAll(
        ctx: Context,
        whereClause: String? = null,
        whereParams: Array<String>? = null,
    ): List<SpamNumber> {
        var sql = "SELECT * FROM $TABLE_SPAM "

        whereClause?.let { sql += it }

        val ret: MutableList<SpamNumber> = mutableListOf()

        val db = Db.getInstance(ctx).readableDatabase
        val cursor = db.rawQuery(sql, whereParams)
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    ret += ruleFromCursor(it)
                } while (it.moveToNext())
            }
            return ret
        }
    }

    fun search(
        ctx: Context,
        pattern: String,
        limit: Int = 10,
    ): List<SpamNumber> {
        return listAll(
            ctx,
            whereClause = " WHERE $COLUMN_PEER LIKE ? LIMIT ?",
            whereParams = arrayOf("%$pattern%", limit.toString())
        )
    }

    fun findByNumber(ctx: Context, number: String): SpamNumber? {
        val records = listAll(
            ctx,
            whereClause = " WHERE $COLUMN_PEER = ?",
            whereParams = arrayOf(number)
        )
        return if (records.isEmpty()) {
            null
        } else {
            records[0]
        }
    }

    fun count(ctx: Context): Int {
        val db = Db.getInstance(ctx).readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_SPAM", null)

        return cursor.use {
            it.moveToFirst()
            it.getInt(0)
        }
    }

    fun clearAll(ctx: Context) {
        val db = Db.getInstance(ctx).writableDatabase
        val sql = "DELETE FROM $TABLE_SPAM"
        db.execSQL(sql)
    }

    fun deleteById(ctx: Context, id: Long): Int {
        val args = arrayOf(id.toString())
        val deletedCount = Db.getInstance(ctx).writableDatabase
            .delete(TABLE_SPAM, "$COLUMN_ID = ?", args)
        return deletedCount
    }

    // Delete expired records before this timestamp
    fun deleteBeforeTimestamp(ctx: Context, timestamp: Long): Int {
        val args = arrayOf(timestamp.toString())
        val deletedCount = Db.getInstance(ctx).writableDatabase
            .delete(TABLE_SPAM, "$COLUMN_TIME < ?", args)
        return deletedCount
    }
}
