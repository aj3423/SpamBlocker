package spam.blocker.service.checker

import android.content.Context
import android.telecom.Connection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.HistoryRecord
import spam.blocker.db.Notification.Channel
import spam.blocker.db.Notification.ChannelTable
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.def.Def.DEFAULT_HANG_UP_DELAY
import spam.blocker.def.Def.RESULT_ALLOWED_BY_ANSWERED
import spam.blocker.def.Def.RESULT_ALLOWED_BY_API_QUERY
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CNAP_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTENT_RULE
import spam.blocker.def.Def.RESULT_ALLOWED_BY_DEFAULT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_DIALED
import spam.blocker.def.Def.RESULT_ALLOWED_BY_EMERGENCY_CALL
import spam.blocker.def.Def.RESULT_ALLOWED_BY_EMERGENCY_SITUATION
import spam.blocker.def.Def.RESULT_ALLOWED_BY_GEO_LOCATION_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_NUMBER_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_OFF_TIME
import spam.blocker.def.Def.RESULT_ALLOWED_BY_PUSH_ALERT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_RECENT_APP
import spam.blocker.def.Def.RESULT_ALLOWED_BY_REPEATED
import spam.blocker.def.Def.RESULT_ALLOWED_BY_SMS_ALERT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_STIR
import spam.blocker.def.Def.RESULT_BLOCKED_BY_API_QUERY
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CNAP_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTACT_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTENT_RULE
import spam.blocker.def.Def.RESULT_BLOCKED_BY_GEO_LOCATION_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_MEETING_MODE
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NON_CONTACT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NUMBER_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_SMS_BOMB
import spam.blocker.def.Def.RESULT_BLOCKED_BY_SPAM_DB
import spam.blocker.def.Def.RESULT_BLOCKED_BY_STIR
import spam.blocker.service.bot.ApiQueryResult
import spam.blocker.ui.M
import spam.blocker.ui.history.ReportSpamDialog
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.A
import spam.blocker.util.AppIcon
import spam.blocker.util.Notification.ShowType
import spam.blocker.util.Notification.missingChannel
import spam.blocker.util.PermissiveJson
import spam.blocker.util.PermissivePrettyJson
import spam.blocker.util.Util.highlightMatchedText
import spam.blocker.util.spf


@Composable
fun ExtraInfoWithDivider(text: AnnotatedString, maxLines: Int) {
    val C = G.palette

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
        GreyText(
            text = resultReasonStr(LocalContext.current),
            fontSize = 16.sp,
            maxLines = if (expanded) 3 else 1,
        )
    }

    // It will be displayed in the history card when expanded.
    //  - show "Report Number" for call
    //  - show origin sms content for sms record.
    @Composable
    fun ExpandedContent(forType: Int, record: HistoryRecord) {
        val ctx = LocalContext.current
        val C = G.palette

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
                            color = C.warning,
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
                    ExtraInfoWithDivider(
                        text = smsContent.A(),
                        maxLines = if (record.expanded) Int.MAX_VALUE else spf.HistoryOptions(ctx).initialSmsRowCount,
                    )
                }
            }
        }
    }

    fun shouldBlock(): Boolean {
        return Def.isBlocked(type)
    }

    // For "Answer + Hang up", returns the delay before "Hang Up"
    fun hangUpDelay(ctx: Context): Int {
        return spf.BlockType(ctx).delay.toIntOrNull() ?: DEFAULT_HANG_UP_DELAY
    }

    // Prepare the content to be saved in database, as the `HistoryTable.reason` column
    fun reasonToDb(): String {
        return ""
    }

    // The default block type when it's not overridden by per rule block type.
    fun getBlockType(ctx: Context): Int {
        return spf.BlockType(ctx).type
    }

    // The default notification channel for default call/sms, when it's not overridden by per rule type
    fun getNotificationChannel(ctx: Context, showType: ShowType): Channel {

        val spf = spf.Notification(ctx)

        val channelId = when (showType) {
            ShowType.SPAM_CALL -> spf.spamCallChannelId
            ShowType.SPAM_SMS -> spf.spamSmsChannelId
            ShowType.VALID_SMS -> spf.validSmsChannelId
        }

        val channel = ChannelTable.findByChannelId(ctx, channelId)
            ?: missingChannel()

        return channel
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

// allowed by emergency incoming call
class ByEmergencyCall(
    override val type: Int = RESULT_ALLOWED_BY_EMERGENCY_CALL,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.emergency_call)
    }
}

// allowed by the Emergency in quick settings
class ByEmergencySituation(
    override val type: Int = RESULT_ALLOWED_BY_EMERGENCY_SITUATION,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.emergency_situation)
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
            ctx.getString(R.string.contact)
        else
            ctx.getString(R.string.non_contact)
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

// allowed by answered number
class ByAnsweredNumber(
    override val type: Int = RESULT_ALLOWED_BY_ANSWERED,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.answered_number)
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
    // Because it checks both rawNumber and clearNumber(rawNumber), this value stores the matched one,
    //  for later reporting purpose.
    val matchedNumber: String,
    override val type: Int = RESULT_BLOCKED_BY_SPAM_DB,
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
    val apiSummary: String, // apiSummary and category will be displayed on history card(2nd row)
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
        // "Report Number" button / show top line of SMS content
        super.ExpandedContent(forType, record)

        if (!record.expanded) {
            return
        }

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
                text = prettyEcho.A(),
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
    val rule: RegexRule?, // null: the rule is deleted
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
        return rule?.blockType // per rule setting
            ?: super.getBlockType(ctx) // fallback to global setting
    }

    override fun hangUpDelay(ctx: Context): Int {
        return rule?.blockTypeConfig?.toIntOrNull() ?: DEFAULT_HANG_UP_DELAY
    }

    override fun getNotificationChannel(ctx: Context, showType: ShowType): Channel {
        if (rule == null) { // rule deleted?
            return super.getNotificationChannel(ctx, showType) // fallback to global setting
        }

        return ChannelTable.findByChannelId(ctx, rule.channel)
            ?: missingChannel()
    }

    // Highlight keywords that blocked the SMS (if it's blocked by content regex rule)
    @Composable
    override fun ExpandedContent(forType: Int, record: HistoryRecord) {
        val ctx = LocalContext.current
        val C = G.palette

        when(forType) {
            Def.ForSms -> {
                val smsContent = record.extraInfo
                val isBySmsRule = type in listOf(
                    RESULT_ALLOWED_BY_CONTENT_RULE,
                    RESULT_BLOCKED_BY_CONTENT_RULE
                )

                if (smsContent != null && isBySmsRule && rule != null) {
                    ExtraInfoWithDivider(
                        text = highlightMatchedText(
                            text = smsContent,
                            regexStr = rule.pattern,
                            regexFlags = rule.patternFlags,
                            wildcardColor = if(rule.isBlacklist) C.error else C.success,
                            textColor = C.textGrey
                        ),
                        maxLines = if (record.expanded) Int.MAX_VALUE else spf.HistoryOptions(ctx).initialSmsRowCount,
                    )
                } else {
                    super.ExpandedContent(forType, record)
                }
            }
            else -> super.ExpandedContent(forType, record)
        }
    }

    override fun resultReasonStr(ctx: Context): String {
        val summary = ruleSummary(ctx)

        return when (type) {
            RESULT_ALLOWED_BY_NUMBER_REGEX, RESULT_BLOCKED_BY_NUMBER_REGEX -> ctx.getString(R.string.regex_pattern) + ": $summary"
            RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX, RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX -> {
                ctx.getString(R.string.contact_group) + ": $summary"
            }

            RESULT_ALLOWED_BY_CONTACT_REGEX, RESULT_BLOCKED_BY_CONTACT_REGEX -> {
                ctx.getString(R.string.contact_rule) + ": $summary"
            }

            RESULT_ALLOWED_BY_CONTENT_RULE, RESULT_BLOCKED_BY_CONTENT_RULE -> {
                ctx.getString(R.string.content) + ": $summary"
            }

            RESULT_ALLOWED_BY_CNAP_REGEX, RESULT_BLOCKED_BY_CNAP_REGEX -> {
                ctx.getString(R.string.caller_name) + ": $summary"
            }

            RESULT_ALLOWED_BY_GEO_LOCATION_REGEX, RESULT_BLOCKED_BY_GEO_LOCATION_REGEX -> {
                ctx.getString(R.string.geo_location) + ": $summary"
            }

            else -> "bug, please report"
        }
    }
}

// This will be serialized to a json and saved as the HistoryTable.reason
@Serializable
@SerialName("PushAlertDetail")
data class PushAlertDetail(
    val pkgName: String,
    val body: String,
)
// allowed by push alert
class ByPushAlert(
    override val type: Int = RESULT_ALLOWED_BY_PUSH_ALERT,
    val detail: PushAlertDetail,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.push_alert)
    }
    override fun reasonToDb(): String {
        return PermissiveJson.encodeToString(detail)
    }
    @Composable
    override fun ResultReason(expanded: Boolean) {
        RowVCenterSpaced(4) {
            super.ResultReason(expanded)
            AppIcon(detail.pkgName)
        }
    }
    @Composable
    override fun ExpandedContent(forType: Int, record: HistoryRecord) {
        Column {
            super.ExpandedContent(forType, record)
            if (record.expanded)
                ExtraInfoWithDivider(
                    text = detail.body.A(),
                    maxLines = 20,
                )
        }
    }
}

// allowed by sms alert
class BySmsAlert(
    override val type: Int = RESULT_ALLOWED_BY_SMS_ALERT,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.sms_alert)
    }
}

// blocked by sms bomb
class BySmsBomb(
    override val type: Int = RESULT_BLOCKED_BY_SMS_BOMB,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.sms_bomb)
    }
}

fun parseCheckResultFromDb(ctx: Context, result: Int, reason: String): ICheckResult {
    return when (result) {
        RESULT_ALLOWED_BY_EMERGENCY_CALL -> ByEmergencyCall()
        RESULT_ALLOWED_BY_EMERGENCY_SITUATION -> ByEmergencySituation()

        RESULT_ALLOWED_BY_CONTACT, RESULT_BLOCKED_BY_NON_CONTACT -> ByContact(
            result, reason
        )

        RESULT_ALLOWED_BY_STIR, RESULT_BLOCKED_BY_STIR -> BySTIR(result, reason.toInt())
        RESULT_ALLOWED_BY_RECENT_APP -> ByRecentApp(reason)
        RESULT_BLOCKED_BY_MEETING_MODE -> ByMeetingMode(reason)
        RESULT_ALLOWED_BY_REPEATED -> ByRepeatedCall()
        RESULT_ALLOWED_BY_DIALED -> ByDialedNumber()
        RESULT_ALLOWED_BY_ANSWERED -> ByAnsweredNumber()
        RESULT_ALLOWED_BY_OFF_TIME -> ByOffTime()
        RESULT_BLOCKED_BY_SPAM_DB -> BySpamDb(matchedNumber = reason)
        RESULT_BLOCKED_BY_API_QUERY, RESULT_ALLOWED_BY_API_QUERY -> {
            val extraInfo = PermissiveJson.decodeFromString<ApiQueryResultDetail>(reason)
            ByApiQuery(result, extraInfo)
        }

        RESULT_ALLOWED_BY_NUMBER_REGEX, RESULT_BLOCKED_BY_NUMBER_REGEX,
        RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX, RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX,
        RESULT_ALLOWED_BY_CONTACT_REGEX, RESULT_BLOCKED_BY_CONTACT_REGEX,
        RESULT_ALLOWED_BY_CNAP_REGEX, RESULT_BLOCKED_BY_CNAP_REGEX,
        RESULT_ALLOWED_BY_GEO_LOCATION_REGEX, RESULT_BLOCKED_BY_GEO_LOCATION_REGEX -> {
            val rule = NumberRegexTable().findRuleById(ctx, reason.toLong())
            ByRegexRule(result, rule)
        }

        RESULT_ALLOWED_BY_CONTENT_RULE, RESULT_BLOCKED_BY_CONTENT_RULE -> {
            val rule = ContentRegexTable().findRuleById(ctx, reason.toLong())
            ByRegexRule(result, rule)
        }
        RESULT_ALLOWED_BY_PUSH_ALERT -> {
            val detail = PermissiveJson.decodeFromString<PushAlertDetail>(reason)
            ByPushAlert(detail = detail)
        }
        RESULT_ALLOWED_BY_SMS_ALERT -> BySmsAlert()
        RESULT_BLOCKED_BY_SMS_BOMB -> BySmsBomb()

        else -> ByDefault(result)
    }
}
