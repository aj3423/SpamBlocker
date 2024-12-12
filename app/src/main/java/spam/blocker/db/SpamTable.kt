package spam.blocker.db

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.Serializable
import spam.blocker.db.Db.Companion.COLUMN_ID
import spam.blocker.db.Db.Companion.COLUMN_PEER
import spam.blocker.db.Db.Companion.COLUMN_REASON
import spam.blocker.db.Db.Companion.COLUMN_REASON_EXTRA
import spam.blocker.db.Db.Companion.COLUMN_TIME
import spam.blocker.db.Db.Companion.TABLE_SPAM

enum class ImportDbReason {
    Manually,
    ByAPI
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
    // when importReason is
    // - ByAPI, this value is the domain name
    val importReasonExtra: String? = null,
)

object SpamTable {

    fun addAll(
        ctx: Context,
        numbers: List<SpamNumber>,
    ): String? {
        val db = Db.getInstance(ctx).writableDatabase

        db.beginTransaction()
        return try {
            for (number in numbers) {
                db.execSQL(
                    "INSERT INTO $TABLE_SPAM ($COLUMN_PEER, $COLUMN_TIME, $COLUMN_REASON, $COLUMN_REASON_EXTRA)" +
                            " VALUES ('${number.peer}', ${number.time}, ${number.importReason.ordinal}, '${number.importReasonExtra}')" +
                            " ON CONFLICT($COLUMN_PEER) DO UPDATE SET" +
                            " $COLUMN_TIME = ${number.time}, " +
                            " $COLUMN_REASON = ${number.importReason.ordinal}," +
                            " $COLUMN_REASON_EXTRA = '${number.importReasonExtra}'"
                )
            }
            db.setTransactionSuccessful()
            null
        } catch (e: Exception) {
            e.toString()
        } finally {
            db.endTransaction()
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
        additionalSql: String? = null
    ): List<SpamNumber> {
        var sql = "SELECT * FROM $TABLE_SPAM"

        additionalSql?.let { sql += it }

        val ret: MutableList<SpamNumber> = mutableListOf()

        val db = Db.getInstance(ctx).readableDatabase
        val cursor = db.rawQuery(sql, null)
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
            additionalSql = " WHERE $COLUMN_PEER LIKE '%$pattern%' LIMIT $limit"
        )
    }

    fun findByNumber(ctx: Context, number: String): SpamNumber? {
        val records = listAll(
            ctx,
            additionalSql = " WHERE $COLUMN_PEER = '$number'"
        )
        return if (records.isEmpty()) {
            null
        } else {
            records[0]
        }
    }

    fun count(ctx: Context): Int {
        val db = Db.getInstance(ctx).readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${Db.TABLE_SPAM}", null)

        return cursor.use {
            it.moveToFirst()
            it.getInt(0)
        }
    }

    fun clearAll(ctx: Context) {
        val db = Db.getInstance(ctx).writableDatabase
        val sql = "DELETE FROM ${Db.TABLE_SPAM}"
        db.execSQL(sql)
    }

    fun deleteById(ctx: Context, id: Long): Int {
        val args = arrayOf(id.toString())
        val deletedCount = Db.getInstance(ctx).writableDatabase
            .delete(Db.TABLE_SPAM, "${Db.COLUMN_ID} = ?", args)
        return deletedCount
    }

    // Delete expired records before this timestamp
    fun deleteBeforeTimestamp(ctx: Context, timestamp: Long): Int {
        val args = arrayOf(timestamp.toString())
        val deletedCount = Db.getInstance(ctx).writableDatabase
            .delete(Db.TABLE_SPAM, "${Db.COLUMN_TIME} < ?", args)
        return deletedCount
    }
}
