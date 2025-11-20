package spam.blocker.db

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.os.Build
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
import spam.blocker.def.Def.ANDROID_10
import spam.blocker.def.Def.ANDROID_11
import spam.blocker.def.Def.ANDROID_14
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

    // Returns error string, return null for success
    fun addAll(
        ctx: Context,
        numbers: List<SpamNumber>,
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

                numbers.chunked(batchSize).forEach { batch ->
                    // Build placeholders: (?, ?, ?, ?) repeated for batch size
                    val placeholders = (1..batch.size).joinToString(", ") { "(?, ?, ?, ?)" }
                    val stmt = compileStatement(
                        """
                            INSERT OR REPLACE INTO $TABLE_SPAM 
                            ($COLUMN_PEER, $COLUMN_TIME, $COLUMN_REASON, $COLUMN_REASON_EXTRA)
                            VALUES $placeholders
                        """.trimIndent()
                    )

                    // Bind all params in sequence (4 per row)
                    batch.forEachIndexed { index, number ->
                        val base = index * 4 + 1 // 1-based indexing
                        stmt.bindString(base, number.peer)
                        stmt.bindLong(base + 1, number.time)
                        stmt.bindLong(base + 2, number.importReason.ordinal.toLong())
                        // Nullable handling
                        number.importReasonExtra?.let { stmt.bindString(base + 3, it) }
                            ?: stmt.bindNull(base + 3)
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
