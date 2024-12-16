package spam.blocker.service.checker

import android.content.Context
import android.telecom.Connection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.HistoryRecord
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.def.Def.RESULT_ALLOWED_BY_API_QUERY
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT_GROUP
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTENT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_DEFAULT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_DIALED
import spam.blocker.def.Def.RESULT_ALLOWED_BY_EMERGENCY
import spam.blocker.def.Def.RESULT_ALLOWED_BY_NUMBER
import spam.blocker.def.Def.RESULT_ALLOWED_BY_OFF_TIME
import spam.blocker.def.Def.RESULT_ALLOWED_BY_RECENT_APP
import spam.blocker.def.Def.RESULT_ALLOWED_BY_REPEATED
import spam.blocker.def.Def.RESULT_ALLOWED_BY_STIR
import spam.blocker.def.Def.RESULT_BLOCKED_BY_API_QUERY
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTACT_GROUP
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTACT_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTENT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_MEETING_MODE
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NON_CONTACT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NUMBER
import spam.blocker.def.Def.RESULT_BLOCKED_BY_SPAM_DB
import spam.blocker.def.Def.RESULT_BLOCKED_BY_STIR
import spam.blocker.service.bot.ApiQueryResult
import spam.blocker.ui.M
import spam.blocker.ui.history.ReportSpamDialog
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.DrawableImage
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.AppInfo
import spam.blocker.util.Notification
import spam.blocker.util.PermissiveJson
import spam.blocker.util.PermissivePrettyJson
import spam.blocker.util.spf


@Composable
fun AppIcon(pkgName: String) {
    val ctx = LocalContext.current

    DrawableImage(
        AppInfo.fromPackage(ctx, pkgName).icon,
        modifier = M
            .size(24.dp)
            .padding(start = 2.dp)
    )
}
@Composable
fun ExtraInfoWithDivider(text: String, maxLines: Int) {
    val C = LocalPalette.current

    if(text.isNotEmpty() && maxLines > 0) {
        Column {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = C.disabled,
                modifier = M.padding(vertical = 4.dp)
            )
            Text(
                text = text,
                color = C.textGrey,
                fontSize = 16.sp,
                lineHeight = 16.sp,
                overflow = TextOverflow.Ellipsis,
                maxLines = maxLines
            )
        }
    }
}

interface ICheckResult {
    val type: Int

    // Used in the notification
    fun resultReasonStr(ctx: Context): String

    // This will be rendered in the history items as the reason.(2nd row)
    @Composable
    fun ResultReason(expanded: Boolean) {
        GreyLabel(
            text = resultReasonStr(LocalContext.current),
            fontSize = 16.sp,
            maxLines = if (expanded) 3 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    // It will be displayed in the history card when expanded.
    //  - show API server response for API query
    //  - show origin sms content for sms record.
    @Composable
    fun ExpandedContent(forType: Int, record: HistoryRecord) {
        val ctx = LocalContext.current

        when(forType) {
            // "Report Number" button
            Def.ForNumber -> {
                if (record.expanded) {
                    val enabledReportApis = remember {
                        G.apiReportVM.table.listAll(ctx).filter { it.enabled }
                    }
                    if (enabledReportApis.isNotEmpty()) {
                        Spacer(modifier = M.padding(vertical = 4.dp))

                        val reportTrigger = remember { mutableStateOf(false) }
                        ReportSpamDialog(trigger = reportTrigger, rawNumber = record.peer)
                        StrokeButton(
                            label = Str(R.string.report_number),
                            color = DarkOrange,
                            modifier = M.padding(bottom = 4.dp)
                        ) {
                            reportTrigger.value = true
                        }
                    }
                }
            }
            Def.ForSms -> {
                val smsContent = record.extraInfo
                if (smsContent != null) {
                    val initialSmsRows = spf.HistoryOptions(ctx).getInitialSmsRowCount()
                    ExtraInfoWithDivider(
                        text = smsContent,
                        maxLines = if (record.expanded) Int.MAX_VALUE else initialSmsRows,
                    )
                }
            }
        }
    }

    fun shouldBlock(): Boolean {
        return Def.isBlocked(type)
    }

    // Prepare the content to be saved in database, as the `HistoryTable.reason` column
    fun reasonToDb(): String {
        return ""
    }

    // The default block type when it's not overridden by per rule block type.
    fun getBlockType(ctx: Context): Int {
        return spf.BlockType(ctx).getType()
    }

    // The default notification channel for default call/sms, when it's not overridden by per rule type
    fun getSpamImportance(isCall: Boolean): Int {
        return if (isCall)
            Notification.defaultSpamCallImportance
        else
            Notification.defaultSpamSMSImportance
    }
}

// passed by default
class ByDefault(
    override val type: Int = RESULT_ALLOWED_BY_DEFAULT,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.passed_by_default)
    }
}

// allowed by emergency call
class ByEmergency(
    override val type: Int = RESULT_ALLOWED_BY_EMERGENCY,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.emergency_call)
    }
}

// allowed by contact, or blocked by non-contact
class ByContact(
    override val type: Int,
    private val contactName: String? = null,
) : ICheckResult {
    override fun reasonToDb(): String {
        return contactName ?: ""
    }

    override fun resultReasonStr(ctx: Context): String {
        return if (type == RESULT_ALLOWED_BY_CONTACT)
            ctx.getString(R.string.contacts)
        else
            ctx.getString(R.string.non_contacts)
    }
}


// allowed by recent apps
class ByRecentApp(
    private val pkgName: String,
    override val type: Int = RESULT_ALLOWED_BY_RECENT_APP,
) : ICheckResult {

    override fun reasonToDb(): String {
        return pkgName
    }

    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.recent_apps)
    }

    @Composable
    override fun ResultReason(expanded: Boolean) {
        RowVCenterSpaced(4) {
            super.ResultReason(expanded)
            AppIcon(pkgName)
        }
    }
}

// blocked by meeting mode
class ByMeetingMode(
    private val pkgName: String,
    override val type: Int = RESULT_BLOCKED_BY_MEETING_MODE,
) : ICheckResult {

    override fun reasonToDb(): String {
        return pkgName
    }

    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.in_meeting)
    }

    @Composable
    override fun ResultReason(expanded: Boolean) {
        RowVCenterSpaced(4) {
            super.ResultReason(expanded)
            AppIcon(pkgName)
        }
    }
}

// allowed by repeated call
class ByRepeatedCall(
    override val type: Int = RESULT_ALLOWED_BY_REPEATED,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.repeated_call)
    }
}

// allowed by dialed number
class ByDialedNumber(
    override val type: Int = RESULT_ALLOWED_BY_DIALED,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.dialed_number)
    }
}

// allowed by off time
class ByOffTime(
    override val type: Int = RESULT_ALLOWED_BY_OFF_TIME,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.off_time)
    }
}

// blocked by spam db
class BySpamDb(
    override val type: Int = RESULT_BLOCKED_BY_SPAM_DB,
    // Because it checks both rawNumber and clearNumber(rawNumber), this value stores the matched one,
    //  for later reporting purpose.
    val matchedNumber: String,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.database)
    }
    override fun reasonToDb(): String {
        return matchedNumber
    }
}

// allowed/blocked by stir/shaken
class BySTIR(
    override val type: Int,
    private val stirResult: Int,
) : ICheckResult {
    override fun reasonToDb(): String {
        return stirResult.toString()
    }

    override fun resultReasonStr(ctx: Context): String {
        return when (stirResult) {
            Connection.VERIFICATION_STATUS_NOT_VERIFIED -> "${ctx.getString(R.string.stir_attestation)} ${
                ctx.getString(
                    R.string.unverified
                )
            }"

            Connection.VERIFICATION_STATUS_PASSED -> "${ctx.getString(R.string.stir_attestation)} ${
                ctx.getString(
                    R.string.valid
                )
            }"

            Connection.VERIFICATION_STATUS_FAILED -> "${ctx.getString(R.string.stir_attestation)} ${
                ctx.getString(
                    R.string.spoof
                )
            }"

            else -> ctx.getString(R.string.stir_attestation)
        }
    }
}

// This will be serialized to a json and saved as the HistoryTable.reason
@Serializable
@SerialName("ApiQueryResultDetail")
data class ApiQueryResultDetail(
    val apiSummary: String, // apiSummary and category will be displayed on history card(2st row)
    val apiDomain: String, // for later reporting

    val queryResult: ApiQueryResult, // the result of Action ApiQuery
)

// allowed/blocked by api query
class ByApiQuery(
    override val type: Int,
    val detail: ApiQueryResultDetail,
) : ICheckResult {
    @Composable
    override fun ExpandedContent(forType: Int, record: HistoryRecord) {
        if (!record.expanded) {
            return
        }

        // "Report Number" button
        super.ExpandedContent(forType, record)

        // Server Echo
        val echo = detail.queryResult.serverEcho
        if (echo != null) {
            // pretty format the json
            val prettyEcho = try {
                PermissivePrettyJson.encodeToString(PermissiveJson.decodeFromString<JsonObject>(echo))
            } catch (_: Exception) {
                echo
            }
            ExtraInfoWithDivider(
                text = prettyEcho,
                maxLines = 20,
            )
        }
    }

    override fun reasonToDb(): String {
        return PermissiveJson.encodeToString(detail)
    }

    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.query) + ": " + detail.apiSummary +
                if (detail.queryResult.category?.isNotEmpty() == true)
                    " (${detail.queryResult.category})"
                else
                    ""
    }
}

// allowed/blocked by regex rule
class ByRegexRule(
    override val type: Int,
    private val rule: RegexRule?, // null: the rule is deleted
) : ICheckResult {
    override fun reasonToDb(): String {
        return (rule?.id ?: 0).toString()
    }

    private fun ruleSummary(ctx: Context): String {
        val reasonStr = if (rule != null) {
            if (rule.description != "") rule.description else rule.patternStr()
        } else {
            ctx.resources.getString(R.string.deleted_filter)
        }
        return reasonStr
    }

    override fun getBlockType(ctx: Context): Int {
        return if (rule != null)
            rule.blockType // per rule setting
        else
            super.getBlockType(ctx) // fallback to global setting
    }

    override fun getSpamImportance(isCall: Boolean): Int {
        return if (rule != null)
            rule.importance // per rule setting
        else
            super.getSpamImportance(isCall) // fallback to global setting
    }

    override fun resultReasonStr(ctx: Context): String {
        val summary = ruleSummary(ctx)

        return when (type) {
            RESULT_ALLOWED_BY_NUMBER -> ctx.getString(R.string.whitelist) + ": $summary"
            RESULT_BLOCKED_BY_NUMBER -> ctx.getString(R.string.blacklist) + ": $summary"
            RESULT_ALLOWED_BY_CONTACT_GROUP, RESULT_BLOCKED_BY_CONTACT_GROUP -> {
                ctx.getString(R.string.contact_group) + ": $summary"
            }

            RESULT_ALLOWED_BY_CONTACT_REGEX, RESULT_BLOCKED_BY_CONTACT_REGEX -> {
                ctx.getString(R.string.contact_rule) + ": $summary"
            }

            RESULT_ALLOWED_BY_CONTENT, RESULT_BLOCKED_BY_CONTENT -> {
                ctx.getString(R.string.content) + ": $summary"
            }

            else -> "bug"
        }
    }
}


fun parseCheckResultFromDb(ctx: Context, result: Int, reason: String): ICheckResult {
    return when (result) {
        RESULT_ALLOWED_BY_EMERGENCY -> ByEmergency(result)
        RESULT_ALLOWED_BY_CONTACT, RESULT_BLOCKED_BY_NON_CONTACT -> ByContact(
            result, reason
        )

        RESULT_ALLOWED_BY_STIR, RESULT_BLOCKED_BY_STIR -> BySTIR(result, reason.toInt())
        RESULT_ALLOWED_BY_RECENT_APP -> ByRecentApp(reason)
        RESULT_BLOCKED_BY_MEETING_MODE -> ByMeetingMode(reason)
        RESULT_ALLOWED_BY_REPEATED -> ByRepeatedCall(result)
        RESULT_ALLOWED_BY_DIALED -> ByDialedNumber(result)
        RESULT_ALLOWED_BY_OFF_TIME -> ByOffTime(result)
        RESULT_BLOCKED_BY_SPAM_DB -> BySpamDb(result, matchedNumber = reason)
        RESULT_BLOCKED_BY_API_QUERY, RESULT_ALLOWED_BY_API_QUERY -> {
            val extraInfo = PermissiveJson.decodeFromString<ApiQueryResultDetail>(reason)
            ByApiQuery(result, extraInfo)
        }

        RESULT_ALLOWED_BY_NUMBER, RESULT_BLOCKED_BY_NUMBER,
        RESULT_ALLOWED_BY_CONTACT_GROUP, RESULT_BLOCKED_BY_CONTACT_GROUP,
        RESULT_ALLOWED_BY_CONTACT_REGEX, RESULT_BLOCKED_BY_CONTACT_REGEX -> {
            val rule = NumberRuleTable().findRuleById(ctx, reason.toLong())
            ByRegexRule(result, rule)
        }

        RESULT_ALLOWED_BY_CONTENT, RESULT_BLOCKED_BY_CONTENT -> {
            val rule = ContentRuleTable().findRuleById(ctx, reason.toLong())
            ByRegexRule(result, rule)
        }

        else -> ByDefault(result)
    }
}
