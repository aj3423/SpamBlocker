package spam.blocker.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import android.os.Build
import androidx.core.database.sqlite.transaction
import spam.blocker.db.Db.Companion.COLUMN_ID
import spam.blocker.def.Def.ANDROID_14
import spam.blocker.util.loge

interface TableAdapter<T> {
    fun fromCursor(cursor: Cursor): T
    fun toContentValues(item: T, includeId: Boolean = false): ContentValues
}

abstract class BasicTable<T>(
    private val tableName: String,
) : TableAdapter<T> {
    // for batch insertion
    protected open fun insertColumns(): List<String> = emptyList()
    protected open fun bindInsertStatement(stmt: SQLiteStatement, item: T, baseIndex: Int) {}

    // Batch insert, way faster than inserting one by one.
    fun addAll(
        ctx: Context,
        items: List<T>,
    ): String? {
        val db = Db.getInstance(ctx).writableDatabase

        db.transaction() {
            return try {
                val batchSize = if (Build.VERSION.SDK_INT >= ANDROID_14)
                    1000
                else
                    200

                val columns = insertColumns()
                val colCount = columns.size
                val colListStr = columns.joinToString(", ")
                val rowPlaceholder = "(" + (1..colCount).joinToString(", ") { "?" } + ")"

                items.chunked(batchSize).forEach { batch ->
                    val placeholders = (1..batch.size).joinToString(", ") { rowPlaceholder }
                    val stmt = compileStatement(
                        """
                            INSERT OR REPLACE INTO $tableName 
                            ($colListStr)
                            VALUES $placeholders
                        """.trimIndent()
                    )

                    batch.forEachIndexed { index, item ->
                        val base = index * colCount + 1
                        bindInsertStatement(stmt, item, base)
                    }

                    stmt.execute()
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
    fun addNew(ctx: Context, item: T): Long {
        val db = Db.getInstance(ctx).writableDatabase
        return db.insert(tableName, null, toContentValues(item, includeId = false))
    }

    fun addWithId(ctx: Context, item: T) {
        val db = Db.getInstance(ctx).writableDatabase
        db.insert(tableName, null, toContentValues(item, includeId = true))
    }

    fun updateById(ctx: Context, id: Long, item: T): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        return db.update(tableName, toContentValues(item), "$COLUMN_ID = ?", arrayOf(id.toString())) > 0
    }

    fun listAll(
        ctx: Context,
        whereClause: String? = null,
        whereParams: Array<String>? = null,
        groupBy: String? = null,
        orderBy: String? = null,
        limit: Int? = null
    ): List<T> {
        var sql = "SELECT * FROM $tableName"

        whereClause?.let { sql += " WHERE $it" }
        groupBy?.let { sql += " GROUP BY $it" }
        orderBy?.let { sql += " ORDER BY $it" }
        limit?.let { sql += " LIMIT $it" }

        val ret = mutableListOf<T>()
        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(sql, whereParams)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    ret += fromCursor(it)
                } while (it.moveToNext())
            }
        }
        return ret
    }

    fun findFirst(
        ctx: Context,
        whereClause: String,
        whereParams: Array<String>? = null,
    ): T? {
        return listAll(
            ctx,
            whereClause = whereClause,
            whereParams = whereParams,
            limit = 1
        ).firstOrNull()
    }

    fun findById(
        ctx: Context,
        id: Long
    ): T? {
        return findFirst(ctx, "$COLUMN_ID = ?", arrayOf("$id"))
    }

    fun count(ctx: Context): Int {
        val db = Db.getInstance(ctx).readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $tableName", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun clearAll(ctx: Context) {
        Db.getInstance(ctx).writableDatabase.execSQL("DELETE FROM $tableName")
    }

    open fun deleteById(ctx: Context, id: Long): Int {
        return Db.getInstance(ctx).writableDatabase
            .delete(tableName, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun deleteByIds(ctx: Context, ids: List<Long>): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        val sql = "DELETE FROM $tableName WHERE $COLUMN_ID IN (${ids.joinToString(",")})"
        val cursor = db.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
    }
}
