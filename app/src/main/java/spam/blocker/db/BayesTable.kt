package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import kotlinx.serialization.Serializable
import spam.blocker.db.Db.Companion.COLUMN_CONTENT
import spam.blocker.db.Db.Companion.COLUMN_HASH
import spam.blocker.db.Db.Companion.COLUMN_ID
import spam.blocker.db.Db.Companion.COLUMN_IS_SPAM
import spam.blocker.db.Db.Companion.TABLE_BAYESIAN_FILTER


@Serializable
data class BayesSample(
    val id: Long = 0,
    var isSpam: Boolean,
    val content: String,
    val hash: Int,
)
fun bayesSampleHash(timestamp: Long, content: String): Int {
    return "${timestamp}_$content".hashCode()
}

object BayesTable : BasicTable<BayesSample>(TABLE_BAYESIAN_FILTER) {

    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): BayesSample {
        return BayesSample(
            id = cursor.getLong(cursor.getColumnIndex(COLUMN_ID)),
            hash = cursor.getInt(cursor.getColumnIndex(COLUMN_HASH)),
            content = cursor.getString(cursor.getColumnIndex(COLUMN_CONTENT)),
            isSpam = cursor.getInt(cursor.getColumnIndex(COLUMN_IS_SPAM)) == 1,
        )
    }

    override fun toContentValues(item: BayesSample, includeId: Boolean): ContentValues {
        val cv = ContentValues()
        if (includeId) {
            cv.put(COLUMN_ID, item.id)
        }
        cv.put(COLUMN_HASH, item.hash)
        cv.put(COLUMN_CONTENT, item.content)
        cv.put(COLUMN_IS_SPAM, item.isSpam)
        return cv
    }

    override fun insertColumns(): List<String> = listOf(
        COLUMN_HASH, COLUMN_CONTENT, COLUMN_IS_SPAM
    )

    override fun bindInsertStatement(stmt: SQLiteStatement, item: BayesSample, baseIndex: Int) {
        stmt.bindLong(baseIndex, item.hash.toLong())
        stmt.bindString(baseIndex + 1, item.content)
        stmt.bindLong(baseIndex + 2, if (item.isSpam) 1 else 0)
    }

    fun findByHash(ctx: Context, hash: Int): BayesSample? {
        return findFirst(ctx, "$COLUMN_HASH = ?", arrayOf("$hash"))
    }
    fun delByHash(ctx: Context, hash: Int) {
        findByHash(ctx, hash)?.let {
            deleteById(ctx, it.id)
        }
    }
    fun count(ctx: Context, isSpam: Boolean): Int {
        return count(
            ctx,
            "$COLUMN_IS_SPAM = ?",
            arrayOf(
                if(isSpam) "1" else "0"
            )
        )
    }
}
