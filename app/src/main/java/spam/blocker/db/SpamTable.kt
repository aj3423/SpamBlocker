package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.Serializable
import spam.blocker.db.Db.Companion.COLUMN_ID
import spam.blocker.db.Db.Companion.COLUMN_PEER
import spam.blocker.db.Db.Companion.COLUMN_REASON
import spam.blocker.db.Db.Companion.COLUMN_REASON_EXTRA
import spam.blocker.db.Db.Companion.COLUMN_TIME
import spam.blocker.db.Db.Companion.TABLE_SPAM
import spam.blocker.util.applyRegexFlags
import spam.blocker.util.regexMatchesNumber

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

object SpamTable : BasicTable<SpamNumber>(TABLE_SPAM) {

    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): SpamNumber {
        return SpamNumber(
            id = cursor.getLong(cursor.getColumnIndex(COLUMN_ID)),
            peer = cursor.getString(cursor.getColumnIndex(COLUMN_PEER)),
            time = cursor.getLong(cursor.getColumnIndex(COLUMN_TIME)),
            importReason = intToImportDbReason(cursor.getIntOrNull(cursor.getColumnIndex(COLUMN_REASON))),
            importReasonExtra = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_REASON_EXTRA)),
        )
    }

    override fun toContentValues(item: SpamNumber, includeId: Boolean): ContentValues {
        val cv = ContentValues()
        if (includeId) {
            cv.put(COLUMN_ID, item.id)
        }
        cv.put(COLUMN_PEER, item.peer)
        cv.put(COLUMN_TIME, item.time)
        cv.put(COLUMN_REASON, item.importReason.ordinal)
        cv.put(COLUMN_REASON_EXTRA, item.importReasonExtra)
        return cv
    }

    // for batch insertion
    override fun insertColumns(): List<String> = listOf(
        COLUMN_PEER, COLUMN_TIME, COLUMN_REASON, COLUMN_REASON_EXTRA
    )

    // for batch insertion
    override fun bindInsertStatement(stmt: SQLiteStatement, item: SpamNumber, baseIndex: Int) {
        stmt.bindString(baseIndex, item.peer)
        stmt.bindLong(baseIndex + 1, item.time)
        stmt.bindLong(baseIndex + 2, item.importReason.ordinal.toLong())
        item.importReasonExtra?.let { stmt.bindString(baseIndex + 3, it) }
            ?: stmt.bindNull(baseIndex + 3)
    }

    fun add(ctx: Context, rawNumber: String) {
        addNew(ctx, SpamNumber(peer = rawNumber, time = System.currentTimeMillis()))
    }

    fun search(
        ctx: Context,
        pattern: String,
        limit: Int = 10,
    ): List<SpamNumber> {
        return listAll(
            ctx,
            whereClause = "$COLUMN_PEER LIKE ?",
            whereParams = arrayOf("%$pattern%"),
            orderBy = "$COLUMN_TIME DESC",
            limit = limit
        )
    }

    fun findByNumberPrefix(
        ctx: Context,
        rawNumber: String, // This function assumes the `incomingNumber` matches the `pattern`
        pattern: String,
        patternFlags: Int,
    ): List<SpamNumber> {
        val tolerance = pattern.takeLastWhile { it == '.' }.length

        // 1. Process `Ignore Country Code` and `Raw Number` first
        val number = rawNumber.applyRegexFlags(patternFlags)

        if (number.length <= tolerance) {
            return listOf()
        }

        val prefixLength = number.length - tolerance
        val prefix = number.substring(0, prefixLength)

        // Query like "SELECT ... LIKE 123456%"
        return listAll(
            ctx,
            whereClause = "$COLUMN_PEER LIKE ?",
            whereParams = arrayOf("$prefix%")
        ).filter {
            // The number must match the regex
            if(!pattern.regexMatchesNumber(it.peer, patternFlags))
                return@filter false

            // Check if the contact number starts with the prefix AND has 'tolerance' more digits
            it.peer.startsWith(prefix) &&
                    it.peer.length == prefix.length + tolerance
        }
    }

    fun findByNumber(ctx: Context, number: String): SpamNumber? {
        return findFirst(
            ctx,
            whereClause = "$COLUMN_PEER = ?",
            whereParams = arrayOf(number)
        )
    }

    // Delete expired records before this timestamp
    fun deleteBeforeTimestamp(ctx: Context, timestamp: Long): Int {
        val args = arrayOf(timestamp.toString())
        val deletedCount = Db.getInstance(ctx).writableDatabase
            .delete(TABLE_SPAM, "$COLUMN_TIME < ?", args)
        return deletedCount
    }
}
