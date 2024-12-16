package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.provider.CallLog.Calls
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.Serializable
import spam.blocker.G
import spam.blocker.def.Def
import spam.blocker.service.bot.HttpDownload
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.parseActions
import spam.blocker.service.bot.serialize
import spam.blocker.util.Permissions
import spam.blocker.util.PhoneNumber
import spam.blocker.util.Util.domainFromUrl
import spam.blocker.util.logi


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
        return domain() ?: ""
    }
    fun domain(): String? {
        return domainFromUrl(url())
    }
    fun url(): String? {
        val httpAction = actions.find { it is HttpDownload }
        if (httpAction == null)
            return null
        return (httpAction as HttpDownload).url
    }
}

private fun isNumberAllowedLater(ctx: Context, rawNumber: String) : Boolean {
    val phoneNumber = PhoneNumber(ctx, rawNumber)
    val incoming = Permissions.getHistoryCallsByNumber(
        ctx, phoneNumber, Def.DIRECTION_INCOMING, Def.NUMBER_REPORTING_BUFFER_HOURS * 3600 * 1000
    )
    val outgoing = Permissions.getHistoryCallsByNumber(
        ctx, phoneNumber, Def.DIRECTION_OUTGOING, Def.NUMBER_REPORTING_BUFFER_HOURS * 3600 * 1000
    )
    val isAllowedLater = (incoming + outgoing).any {
        listOf(
            Calls.INCOMING_TYPE,
            Calls.OUTGOING_TYPE,
            Calls.MISSED_TYPE,
        ).contains(it)
    }
    return isAllowedLater
}

// TODO move this to service/Report.kt
// It checks:
// 0. if the number has repeated later
// 1. if the api is enabled
// 2. remove duplicated apis
// 3. filter by domain(for reporting to a specific api after query or blocked by SpamDB)
fun listReportableAPIs(
    ctx: Context,
    rawNumber: String,
    domainFilter: List<String>?,
    isManualReport: Boolean = false,
): List<Api> {
    if (!isManualReport) {
        // 1. check if the number is repeated or dialed
        //  (DO NOT put this to any other places, it must be checked HERE before further execution)
        val canReadCallLog = Permissions.isCallLogPermissionGranted(ctx)
        if (!canReadCallLog)
            return listOf()

        if (isNumberAllowedLater(ctx, rawNumber)) {
            logi("skip reporting repeated/dialed number: $rawNumber")
            return listOf()
        }
    }

    // 2. List all enabled APIs
    var apis = G.apiReportVM.table.listAll(ctx)
        .filter { it.enabled }
        .filter { // it must contain 1 and only 1 HttpDownload
            val https = it.actions.filter { it is HttpDownload }
            https.size == 1
        }

    // 3. Remove duplicated APIs that have same domain name
    //  (user might have added multiple instances)
    apis = apis.distinctBy {
        val http = it.actions.find { it is HttpDownload }
        val url = (http as HttpDownload).url
        val domain = domainFromUrl(url)
        domain
    }

    // 4. Remove api that doesn't match the domain filter
    if (domainFilter != null) {
        apis = apis.filter {
            val http = it.actions.find { it is HttpDownload }
            val url = (http as HttpDownload).url
            val domain = domainFromUrl(url)
            domainFilter.contains(domain)
        }
    }

    return apis
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
