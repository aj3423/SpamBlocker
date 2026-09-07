package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.database.getIntOrNull
import kotlinx.serialization.Serializable
import spam.blocker.db.Db.Companion.COLUMN_BODY
import spam.blocker.db.Db.Companion.COLUMN_BODY_FLAGS
import spam.blocker.db.Db.Companion.COLUMN_CATEGORY
import spam.blocker.db.Db.Companion.COLUMN_CONTENT
import spam.blocker.db.Db.Companion.COLUMN_DURATION
import spam.blocker.db.Db.Companion.COLUMN_ENABLED
import spam.blocker.db.Db.Companion.COLUMN_HASH
import spam.blocker.db.Db.Companion.COLUMN_ID
import spam.blocker.db.Db.Companion.COLUMN_PKG_NAME
import spam.blocker.db.Db.Companion.TABLE_BAYESIAN_FILTER
import spam.blocker.db.Db.Companion.TABLE_PUSH_ALERT
import spam.blocker.util.logi


@Serializable
data class BayesianSample(
    val id: Long = 0,
    val hash: Int,
    var category: Boolean,
    val content: String
)


object BayesianTable : BasicTable<BayesianSample>(TABLE_BAYESIAN_FILTER) {

    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): BayesianSample {
        return BayesianSample(
            id = cursor.getLong(cursor.getColumnIndex(COLUMN_ID)),
            hash = cursor.getInt(cursor.getColumnIndex(COLUMN_HASH)),
            content = cursor.getString(cursor.getColumnIndex(COLUMN_CONTENT)),
            category = cursor.getInt(cursor.getColumnIndex(COLUMN_CATEGORY)) == 1,
        )
    }

    override fun toContentValues(item: BayesianSample, includeId: Boolean): ContentValues {
        val cv = ContentValues()
        if (includeId) {
            cv.put(COLUMN_ID, item.id)
        }
        cv.put(COLUMN_HASH, item.hash)
        cv.put(COLUMN_CONTENT, item.content)
        cv.put(COLUMN_CATEGORY, item.category)
        return cv
    }

    fun findByHash(ctx: Context, hash: Int): BayesianSample? {
        return findFirst(ctx, "$COLUMN_HASH = ?", arrayOf("$hash"))
    }
    fun delByHash(ctx: Context, hash: Int) {
        findByHash(ctx, hash)?.let {
            deleteById(ctx, it.id)
        }
    }
}
