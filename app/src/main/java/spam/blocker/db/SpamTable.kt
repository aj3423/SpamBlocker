package spam.blocker.db

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import kotlinx.serialization.Serializable
import spam.blocker.db.Db.Companion.COLUMN_PEER
import spam.blocker.db.Db.Companion.COLUMN_TIME
import spam.blocker.db.Db.Companion.TABLE_SPAM

@Serializable
data class SpamNumber(
    val id: Long = 0,
    val peer: String = "",
    val time: Long = 0,
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
                    "INSERT INTO $TABLE_SPAM ($COLUMN_PEER, $COLUMN_TIME)" +
                            " VALUES ('${number.peer}', ${number.time})" +
                            " ON CONFLICT($COLUMN_PEER) DO UPDATE SET $COLUMN_TIME = ${number.time}"
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

    @SuppressLint("Range")
    private fun ruleFromCursor(it: Cursor): SpamNumber {
        return SpamNumber(
            id = it.getLong(it.getColumnIndex(Db.COLUMN_ID)),
            peer = it.getString(it.getColumnIndex(Db.COLUMN_PEER)),
            time = it.getLong(it.getColumnIndex(Db.COLUMN_TIME)),
        )
    }

    fun listAll(
        ctx: Context,
    ): List<SpamNumber> {
        val sql = "SELECT * FROM ${Db.TABLE_SPAM}"

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


    fun numberExists(ctx: Context, number: String): Boolean {
        val db = Db.getInstance(ctx).readableDatabase

        val cursor =
            db.rawQuery("SELECT * FROM ${Db.TABLE_SPAM} WHERE ${Db.COLUMN_PEER} = '$number'", null)

        return cursor.use {
            it.moveToFirst()
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

    // Delete expired records before this timestamp
    fun deleteBeforeTimestamp(ctx: Context, timestamp: Long): Boolean {
        val sql = "DELETE FROM ${Db.TABLE_SPAM} WHERE ${Db.COLUMN_TIME} < $timestamp"
        val cursor = Db.getInstance(ctx).writableDatabase.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
    }
}
