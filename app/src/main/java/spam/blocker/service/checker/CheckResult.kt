package spam.blocker.service.checker

import android.content.Context
import android.telecom.Connection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import spam.blocker.R
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_DIALED
import spam.blocker.def.Def.RESULT_ALLOWED_BY_EMERGENCY
import spam.blocker.def.Def.RESULT_ALLOWED_BY_API_QUERY
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT_GROUP
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTENT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_DEFAULT
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
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.DrawableImage
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.util.AppInfo
import spam.blocker.util.PermissiveJson
import spam.blocker.util.SharedPref.BlockType
import spam.blocker.util.SharedPref.HistoryOptions


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
fun ExtraInfo(text: String, maxLines: Int) {
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
    fun resultSummary(ctx: Context): String

    // This will be rendered in the history items as the reason.
    @Composable
    fun ResultSummary(expanded: Boolean) {
        GreyLabel(
            text = resultSummary(LocalContext.current),
            fontSize = 16.sp,
            maxLines = if (expanded) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // A detailed information that is displayed in the history card when it's expanded.
    // E.g.:
    //  - the ApiQuery will show full server response
    //  - the SMS rule will show full sms content.
    @Composable
    fun ExtraInfo(expanded: Boolean) {}

    fun shouldBlock(): Boolean {
        return Def.isBlocked(type)
    }

    // Prepare the content to be saved in database, as the `reason` column
    fun reasonToDb(): String {
        return ""
    }

    fun getBlockType(ctx: Context): Int {
        return BlockType(ctx).getType()
    }

    fun getImportance(): Int {
        return Def.DEF_SPAM_IMPORTANCE
    }

}

class ByDefault(
    override val type: Int = RESULT_ALLOWED_BY_DEFAULT,
) : ICheckResult {
    override fun resultSummary(ctx: Context): String {
        return ctx.getString(R.string.passed_by_default)
    }
}

class ByEmergency(
    override val type: Int = RESULT_ALLOWED_BY_EMERGENCY,
) : ICheckResult {
    override fun resultSummary(ctx: Context): String {
        return ctx.getString(R.string.emergency_call)
    }
}

class ByContact(
    override val type: Int,
    private val contactName: String? = null,
) : ICheckResult {
    override fun reasonToDb(): String {
        return contactName ?: ""
    }

    override fun resultSummary(ctx: Context): String {
        return if (type == RESULT_ALLOWED_BY_CONTACT)
            ctx.getString(R.string.contacts)
        else
            ctx.getString(R.string.non_contacts)
    }
}


class ByRecentApp(
    private val pkgName: String,
    override val type: Int = RESULT_ALLOWED_BY_RECENT_APP,
) : ICheckResult {

    override fun reasonToDb(): String {
        return pkgName
    }

    override fun resultSummary(ctx: Context): String {
        return ctx.getString(R.string.recent_apps)
    }

    @Composable
    override fun ResultSummary(expanded: Boolean) {
        RowVCenterSpaced(4) {
            super.ResultSummary(expanded)
            AppIcon(pkgName)
        }
    }
}

class ByMeetingMode(
    private val pkgName: String,
    override val type: Int = RESULT_BLOCKED_BY_MEETING_MODE,
) : ICheckResult {

    override fun reasonToDb(): String {
        return pkgName
    }

    override fun resultSummary(ctx: Context): String {
        return ctx.getString(R.string.in_meeting)
    }

    @Composable
    override fun ResultSummary(expanded: Boolean) {
        RowVCenterSpaced(4) {
            super.ResultSummary(expanded)
            AppIcon(pkgName)
        }
    }
}

class ByRepeatedCall(
    override val type: Int = RESULT_ALLOWED_BY_REPEATED,
) : ICheckResult {
    override fun resultSummary(ctx: Context): String {
        return ctx.getString(R.string.repeated_call)
    }
}

class ByDialedNumber(
    override val type: Int = RESULT_ALLOWED_BY_DIALED,
) : ICheckResult {
    override fun resultSummary(ctx: Context): String {
        return ctx.getString(R.string.dialed_number)
    }
}

class ByOffTime(
    override val type: Int = RESULT_ALLOWED_BY_OFF_TIME,
) : ICheckResult {
    override fun resultSummary(ctx: Context): String {
        return ctx.getString(R.string.off_time)
    }
}

class BySpamDb(
    override val type: Int = RESULT_BLOCKED_BY_SPAM_DB,
) : ICheckResult {
    override fun resultSummary(ctx: Context): String {
        return ctx.getString(R.string.database)
    }
}

class BySTIR(
    override val type: Int,
    private val stirResult: Int,
) : ICheckResult {
    override fun reasonToDb(): String {
        return stirResult.toString()
    }

    override fun resultSummary(ctx: Context): String {
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
data class ApiQueryExtraInfo(
    val apiId: Long,
    val apiSummary: String,
    val category: String?,
    val serverEcho: String,
)

class ByApiQuery(
    override val type: Int,
    val extraInfo: ApiQueryExtraInfo,
) : ICheckResult {
    @Composable
    override fun ExtraInfo(expanded: Boolean) {
        if (expanded) {
            ExtraInfo(
                text = extraInfo.serverEcho,
                maxLines = 20,
            )
        }
    }

    override fun reasonToDb(): String {
        return PermissiveJson.encodeToString(extraInfo)
    }

    override fun resultSummary(ctx: Context): String {
        return ctx.getString(R.string.query) + ": " + extraInfo.apiSummary +
                if (extraInfo.category?.isNotEmpty() == true)
                    " (${extraInfo.category})"
                else
                    ""
    }
}

// This will be serialized to a json and saved as the HistoryTable.reason
@Serializable
class RegexExtraInfo(
    val recordId: Long,
    val smsContent: String? = null,
)

class ByRegexRule(
    override val type: Int,
    private val rule: RegexRule?, // null: the rule is deleted
    val extraInfo: RegexExtraInfo,
) : ICheckResult {
    override fun reasonToDb(): String {
        return PermissiveJson.encodeToString(extraInfo)
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

    override fun getImportance(): Int {
        return if (rule != null)
            rule.importance // per rule setting
        else
            super.getImportance() // fallback to global setting
    }

    @Composable
    override fun ExtraInfo(expanded: Boolean) {
        val ctx = LocalContext.current
        when(type) {
            RESULT_ALLOWED_BY_CONTENT, RESULT_BLOCKED_BY_CONTENT -> {
                if (extraInfo.smsContent != null) {
                    val initialSmsRows = HistoryOptions(ctx).getInitialSmsRowCount()
                    ExtraInfo(
                        text = extraInfo.smsContent,
                        maxLines = if (expanded) Int.MAX_VALUE else initialSmsRows,
                    )
                }
            }
        }
    }

    override fun resultSummary(ctx: Context): String {
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
        RESULT_BLOCKED_BY_SPAM_DB -> BySpamDb(result)
        RESULT_BLOCKED_BY_API_QUERY, RESULT_ALLOWED_BY_API_QUERY -> {
            val extraInfo = PermissiveJson.decodeFromString<ApiQueryExtraInfo>(reason)
            ByApiQuery(result, extraInfo)
        }

        RESULT_ALLOWED_BY_NUMBER, RESULT_BLOCKED_BY_NUMBER,
        RESULT_ALLOWED_BY_CONTACT_GROUP, RESULT_BLOCKED_BY_CONTACT_GROUP,
        RESULT_ALLOWED_BY_CONTACT_REGEX, RESULT_BLOCKED_BY_CONTACT_REGEX -> {
            val resultInfo = PermissiveJson.decodeFromString<RegexExtraInfo>(reason)
            val rule = NumberRuleTable().findRuleById(ctx, resultInfo.recordId)
            ByRegexRule(result, rule, resultInfo)
        }

        RESULT_ALLOWED_BY_CONTENT, RESULT_BLOCKED_BY_CONTENT -> {
            val resultInfo = PermissiveJson.decodeFromString<RegexExtraInfo>(reason)
            val rule = ContentRuleTable().findRuleById(ctx, resultInfo.recordId)
            ByRegexRule(result, rule, resultInfo)
        }

        else -> ByDefault(result)
    }
}
