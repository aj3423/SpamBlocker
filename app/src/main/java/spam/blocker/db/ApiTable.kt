package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTENT_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NON_CONTACT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NUMBER_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_STIR
import spam.blocker.service.bot.HttpRequest
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.parseActions
import spam.blocker.service.bot.serialize
import spam.blocker.service.checker.ByRegexRule
import spam.blocker.service.checker.ICheckResult
import spam.blocker.util.Util.domainFromUrl
import spam.blocker.util.hasFlag
import spam.blocker.util.regexMatches

object AutoReportTypes {
    const val NonContact = 1 shl 0
    const val STIR = 1 shl 1
    const val Regex = 1 shl 2

    const val DefaultTypes = NonContact or STIR or Regex
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
@SerialName("QueryApi")
data class QueryApi(
    override val id: Long = 0,
    override val desc: String = "",
    override val actions: List<IAction> = listOf(),
    override val enabled: Boolean = true,
) : IApi()

@Serializable
@SerialName("ReportApi")
data class ReportApi(
    override val id: Long = 0,
    override val desc: String = "",
    override val actions: List<IAction> = listOf(),
    override val enabled: Boolean = true,

    val autoReportTypes: Int = AutoReportTypes.DefaultTypes,
    val autoReportRegexFilter: String? = null,
) : IApi() {
    fun enabledForBlockReason(r: ICheckResult): Boolean {
        return when (r.byType) {
            RESULT_BLOCKED_BY_NON_CONTACT -> autoReportTypes.hasFlag(AutoReportTypes.NonContact) // Contacts(Strict)
            RESULT_BLOCKED_BY_STIR -> autoReportTypes.hasFlag(AutoReportTypes.STIR) // STIR
            RESULT_BLOCKED_BY_NUMBER_REGEX, RESULT_BLOCKED_BY_CONTENT_REGEX -> { // regex
                val rule = (r as ByRegexRule).rule!!

                autoReportTypes.hasFlag(AutoReportTypes.Regex)
                        // the rule.desc matches the `autoReportRegexFilter`
                        && (autoReportRegexFilter ?: ".*").regexMatches(rule.description)
            }

            else -> false
        }
    }
}

abstract class ApiTable(
    tableName: String
) : BasicTable<IApi>(tableName) {
    abstract override fun fromCursor(cursor: Cursor): IApi
    fun listAll(ctx: Context): List<IApi> {
        return listAll(ctx, orderBy = Db.COLUMN_DESC)
    }
}

class QueryApiTable : ApiTable(Db.TABLE_API_QUERY) {
    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): IApi {
        val actionsConfig =
            cursor.getStringOrNull(cursor.getColumnIndex(Db.COLUMN_ACTIONS)) ?: ""
        val actions = actionsConfig.parseActions()

        return QueryApi(
            id = cursor.getLong(cursor.getColumnIndex(Db.COLUMN_ID)),
            desc = cursor.getStringOrNull(cursor.getColumnIndex(Db.COLUMN_DESC)) ?: "",
            actions = actions,
            enabled = cursor.getIntOrNull(cursor.getColumnIndex(Db.COLUMN_ENABLED)) == 1,
        )
    }

    override fun toContentValues(item: IApi, includeId: Boolean): ContentValues {
        val cv = ContentValues()
        if (includeId) {
            cv.put(Db.COLUMN_ID, item.id)
        }
        cv.put(Db.COLUMN_DESC, item.desc)
        cv.put(Db.COLUMN_ACTIONS, item.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (item.enabled) 1 else 0)
        return cv
    }
}

class ReportApiTable : ApiTable(Db.TABLE_API_REPORT) {
    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): IApi {
        val actionsConfig =
            cursor.getStringOrNull(cursor.getColumnIndex(Db.COLUMN_ACTIONS)) ?: ""
        val actions = actionsConfig.parseActions()

        return ReportApi(
            id = cursor.getLong(cursor.getColumnIndex(Db.COLUMN_ID)),
            desc = cursor.getStringOrNull(cursor.getColumnIndex(Db.COLUMN_DESC)) ?: "",
            actions = actions,
            enabled = cursor.getIntOrNull(cursor.getColumnIndex(Db.COLUMN_ENABLED)) == 1,
            // `true` if `1` or `null`(old version doesn't have this field)
            autoReportTypes = cursor.getIntOrNull(cursor.getColumnIndex(Db.COLUMN_AUTO_REPORT_TYPES)) ?: AutoReportTypes.DefaultTypes,
            autoReportRegexFilter = cursor.getStringOrNull(cursor.getColumnIndex(Db.COLUMN_AUTO_REPORT_REGEX_FILTER))
        )
    }

    override fun toContentValues(item: IApi, includeId: Boolean): ContentValues {
        val cv = ContentValues()
        if (includeId) {
            cv.put(Db.COLUMN_ID, item.id)
        }
        cv.put(Db.COLUMN_DESC, item.desc)
        cv.put(Db.COLUMN_ACTIONS, item.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (item.enabled) 1 else 0)
        val rr = item as ReportApi
        cv.put(Db.COLUMN_AUTO_REPORT_TYPES, rr.autoReportTypes)
        cv.put(Db.COLUMN_AUTO_REPORT_REGEX_FILTER, rr.autoReportRegexFilter)
        return cv
    }
}
