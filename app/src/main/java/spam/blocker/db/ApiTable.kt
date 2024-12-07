package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.Serializable
import spam.blocker.service.bot.HttpDownload
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.parseActions
import spam.blocker.service.bot.serialize
import spam.blocker.util.Util

@Serializable
data class Api(
    val id: Long = 0,
    val desc: String = "",
    val actions: List<IAction> = listOf(),
    val enabled: Boolean = false,
) {

    // Use the api.desc if it's not empty, otherwise, use the Http domain
    fun summary(): String {
        if(desc.isNotEmpty()) {
            return desc
        }
        // If the `desc` is not set, show the domain name instead

        // Find the HttpDownload action
        val httpAction = actions.firstOrNull {
            it is HttpDownload
        }
        // show
        return if (httpAction != null) {
            val http = httpAction as HttpDownload
            Util.extractDomainName(http.url) ?: ""
        } else {
            ""
        }
    }
}

class ApiTable(
    val tableName: String
) {
    @SuppressLint("Range")
    fun listAll(ctx: Context, where: String = ""): List<Api> {

        val sql = "SELECT * FROM $tableName $where ORDER BY ${Db.COLUMN_DESC}"

        val ret: MutableList<Api> = mutableListOf()

        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(sql, null)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val actionsConfig =
                        it.getStringOrNull(it.getColumnIndex(Db.COLUMN_ACTIONS)) ?: ""
                    val actions = actionsConfig.parseActions()

                    val rec = Api(
                        id = it.getLong(it.getColumnIndex(Db.COLUMN_ID)),
                        desc = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_DESC)) ?: "",
                        actions = actions,
                        enabled = it.getIntOrNull(it.getColumnIndex(Db.COLUMN_ENABLED)) == 1,
                    )

                    ret += rec
                } while (it.moveToNext())
            }
            return ret
        }
    }

    fun addNewRecord(ctx: Context, r: Api): Long {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)
        return db.insert(tableName, null, cv)
    }

    fun addRecordWithId(ctx: Context, r: Api) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_ID, r.id)
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)
        db.insert(tableName, null, cv)
    }

    fun updateById(ctx: Context, id: Long, r: Api): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)

        return db.update(tableName, cv, "${Db.COLUMN_ID} = $id", null) >= 0
    }

    fun deleteById(ctx: Context, id: Long): Boolean {
        val sql = "DELETE FROM $tableName WHERE ${Db.COLUMN_ID} = $id"
        val cursor = Db.getInstance(ctx).writableDatabase.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
    }

    fun clearAll(ctx: Context) {
        val db = Db.getInstance(ctx).writableDatabase
        val sql = "DELETE FROM $tableName"
        db.execSQL(sql)
    }

}
