package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CallLog.Calls
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.Serializable
import spam.blocker.G
import spam.blocker.def.Def
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NON_CONTACT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NUMBER_RULE
import spam.blocker.def.Def.RESULT_BLOCKED_BY_STIR
import spam.blocker.service.bot.HttpRequest
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.parseActions
import spam.blocker.service.bot.serialize
import spam.blocker.util.Permission
import spam.blocker.util.PhoneNumber
import spam.blocker.util.Util.domainFromUrl
import spam.blocker.util.Util.getHistoryCallsByNumber
import spam.blocker.util.hasFlag
import spam.blocker.util.logi

object AutoReportTypes {
    const val NonContact = 1 shl 0
    const val STIR = 1 shl 1
    const val NumberRegex = 1 shl 2

    const val DefaultTypes = NonContact or STIR or NumberRegex
}

@Serializable
abstract class IApi() {
    abstract val id: Long
    abstract val desc: String
    abstract val actions: List<IAction>
    abstract val enabled: Boolean

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
        val httpAction = actions.find { it is HttpRequest }
        if (httpAction == null)
            return null
        return (httpAction as HttpRequest).url
    }
}

@Serializable
data class QueryApi(
    override val id: Long = 0,
    override val desc: String = "",
    override val actions: List<IAction> = listOf(),
    override val enabled: Boolean = true,
) : IApi()

@Serializable
data class ReportApi(
    override val id: Long = 0,
    override val desc: String = "",
    override val actions: List<IAction> = listOf(),
    override val enabled: Boolean = true,

    val autoReportTypes: Int = AutoReportTypes.DefaultTypes
) : IApi() {
    fun enabledForBlockReason(blockReason: Int): Boolean {
        return when (blockReason) {
            RESULT_BLOCKED_BY_NON_CONTACT -> autoReportTypes.hasFlag(AutoReportTypes.NonContact) // Contacts(Strict)
            RESULT_BLOCKED_BY_STIR -> autoReportTypes.hasFlag(AutoReportTypes.STIR) // STIR
            RESULT_BLOCKED_BY_NUMBER_RULE -> autoReportTypes.hasFlag(AutoReportTypes.NumberRegex) // Number regex
            else -> false
        }
    }
}


private fun isNumberAllowedLater(ctx: Context, rawNumber: String) : Boolean {
    val phoneNumber = PhoneNumber(ctx, rawNumber)
    val incoming = getHistoryCallsByNumber(
        ctx, phoneNumber, Def.DIRECTION_INCOMING, Def.NUMBER_REPORTING_BUFFER_HOURS * 3600 * 1000
    )
    val outgoing = getHistoryCallsByNumber(
        ctx, phoneNumber, Def.DIRECTION_OUTGOING, Def.NUMBER_REPORTING_BUFFER_HOURS * 3600 * 1000
    )
    val isAllowedLater = (incoming + outgoing).any {
        listOf(
            Calls.INCOMING_TYPE,
            Calls.OUTGOING_TYPE,
            Calls.MISSED_TYPE,
        ).contains(it.type)
    }
    return isAllowedLater
}

// TODO move this function to some better place
// It checks:
// 0. if the number has repeated later
// 1. if the api is enabled
// 2. remove duplicated apis
// 3. filter by domain(for reporting to a specific api after query or blocked by SpamDB)
fun listReportableAPIs(
    ctx: Context,
    rawNumber: String,
    domainFilter: List<String>?,
    blockReason: Int?, // null for
    isManualReport: Boolean = false,
    isDbApi: Boolean = false, // if the number is blocked by DB and was auto added by API query
): List<IApi> {
    if (!isManualReport) {
        // 1. check if the number is repeated or dialed
        //  (DO NOT put this to any other places, it must be checked HERE before further execution)
        val canReadCallLog = Permission.callLog.isGranted
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
            val https = it.actions.filter { it is HttpRequest }
            https.isNotEmpty()
        }

    // 3. When auto-reporting, remove APIs that disabled this blockReason.
    if (!isManualReport) {
        if (!isDbApi) { // if isDbApi, no need to filter by blockReason
            apis = apis.filter {
                (it as ReportApi).enabledForBlockReason(blockReason!!)
            }
        }
    }

    // 4. Remove duplicated APIs that have same domain name
    //  (user might have added multiple instances)
    apis = apis.distinctBy {
        val http = it.actions.find { it is HttpRequest }
        val url = (http as HttpRequest).url
        val domain = domainFromUrl(url)
        domain
    }

    // 5. Remove api that doesn't match the domain filter
    if (domainFilter != null) {
        apis = apis.filter {
            val http = it.actions.find { it is HttpRequest }
            val url = (http as HttpRequest).url
            val domain = domainFromUrl(url)
            domainFilter.contains(domain)
        }
    }

    return apis
}


abstract class ApiTable(
    val tableName: String
) {
    abstract fun fromCursor(it: Cursor): IApi

    @SuppressLint("Range")
    fun listAll(ctx: Context, where: String = ""): List<IApi> {

        val sql = "SELECT * FROM $tableName $where ORDER BY ${Db.COLUMN_DESC}"

        val ret: MutableList<IApi> = mutableListOf()

        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(sql, null)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    ret += fromCursor(it)
                } while (it.moveToNext())
            }
            return ret
        }
    }

    abstract fun addNewRecord(ctx: Context, r: IApi): Long
    abstract fun addRecordWithId(ctx: Context, r: IApi)
    abstract fun updateById(ctx: Context, id: Long, r: IApi): Boolean

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

class QueryApiTable : ApiTable(Db.TABLE_API_QUERY) {
    @SuppressLint("Range")
    override fun fromCursor(it: Cursor): IApi {
        val actionsConfig =
            it.getStringOrNull(it.getColumnIndex(Db.COLUMN_ACTIONS)) ?: ""
        val actions = actionsConfig.parseActions()

        return QueryApi(
            id = it.getLong(it.getColumnIndex(Db.COLUMN_ID)),
            desc = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_DESC)) ?: "",
            actions = actions,
            enabled = it.getIntOrNull(it.getColumnIndex(Db.COLUMN_ENABLED)) == 1,
        )
    }

    override fun addNewRecord(ctx: Context, r: IApi): Long {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)
        return db.insert(tableName, null, cv)
    }

    override fun addRecordWithId(ctx: Context, r: IApi) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_ID, r.id)
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)
        db.insert(tableName, null, cv)
    }

    override fun updateById(ctx: Context, id: Long, r: IApi): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)

        return db.update(tableName, cv, "${Db.COLUMN_ID} = $id", null) >= 0
    }
}

class ReportApiTable : ApiTable(Db.TABLE_API_REPORT) {
    @SuppressLint("Range")
    override fun fromCursor(it: Cursor): IApi {
        val actionsConfig =
            it.getStringOrNull(it.getColumnIndex(Db.COLUMN_ACTIONS)) ?: ""
        val actions = actionsConfig.parseActions()

        return ReportApi(
            id = it.getLong(it.getColumnIndex(Db.COLUMN_ID)),
            desc = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_DESC)) ?: "",
            actions = actions,
            enabled = it.getIntOrNull(it.getColumnIndex(Db.COLUMN_ENABLED)) == 1,
            // `true` if `1` or `null`(old version doesn't have this field)
            autoReportTypes = it.getIntOrNull(it.getColumnIndex(Db.COLUMN_AUTO_REPORT_TYPES)) ?: AutoReportTypes.DefaultTypes
        )
    }

    override fun addNewRecord(ctx: Context, r: IApi): Long {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)
        val rr = r as ReportApi
        cv.put(Db.COLUMN_AUTO_REPORT_TYPES, rr.autoReportTypes)
        return db.insert(tableName, null, cv)
    }

    override fun addRecordWithId(ctx: Context, r: IApi) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_ID, r.id)
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)
        val rr = r as ReportApi
        cv.put(Db.COLUMN_AUTO_REPORT_TYPES, rr.autoReportTypes)
        db.insert(tableName, null, cv)
    }

    override fun updateById(ctx: Context, id: Long, r: IApi): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)
        val rr = r as ReportApi
        cv.put(Db.COLUMN_AUTO_REPORT_TYPES, rr.autoReportTypes)
        return db.update(tableName, cv, "${Db.COLUMN_ID} = $id", null) >= 0
    }
}