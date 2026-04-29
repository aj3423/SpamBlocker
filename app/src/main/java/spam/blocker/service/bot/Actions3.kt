package spam.blocker.service.bot

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONObject
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.BotTable
import spam.blocker.db.RegexRule
import spam.blocker.db.listReportableAPIs
import spam.blocker.db.reScheduleBot
import spam.blocker.def.Def
import spam.blocker.ui.darken
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.api.tagValid
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.Placeholder
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.SummaryLabel
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.A
import spam.blocker.util.AdbLogger
import spam.blocker.util.CountryCode
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.PermissiveJson
import spam.blocker.util.PermissivePrettyJson
import spam.blocker.util.Util
import spam.blocker.util.Xml
import spam.blocker.util.formatAnnotated
import spam.blocker.util.logi
import spam.blocker.util.regexExtract
import spam.blocker.util.regexReplace
import spam.blocker.util.spf
import spam.blocker.util.toMap
import spam.blocker.util.unescapeUnicode


/*
input: None
output: None
 */
@Serializable
@SerialName("EnableWorkflow")
class EnableWorkflow(
    var enable: Boolean = false,
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val workTag = aCtx.workTag

        aCtx.logger?.debug("${label(ctx)}: $workTag")

        // null when Testing, do nothing
        if (workTag == null) {
            aCtx.logger?.debug(ctx.getString(R.string.skip_for_testing))
            return true
        }

        val bot = BotTable.findByWorkUuid(ctx, workTag) // bot is guaranteed to be `Schedule`

        if (bot == null)
            return true

        if (bot.trigger !is Schedule)
            return true

        val newBot = bot.copy(
            trigger = bot.trigger.copy(enabled = enable)
        )

        // 1. enable/disable bot
        BotTable.updateById(ctx, newBot.id, trigger = newBot.trigger)

        // 2. reSchedule
        reScheduleBot(ctx, newBot)

        // 3. fire event to update UI
        Events.botUpdated.fire()

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_enable_workflow)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        SummaryLabel("${Str(R.string.enable)}: ${Str(if (enable) R.string.yes else R.string.no)}")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_enable_workflow)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_workflow)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabled by remember { mutableStateOf(enable) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabled) { on ->
                enabled = on
                enable = on
            }
        }
    }
}

/*
input: None
output: None
 */
@Serializable
@SerialName("EnableApp")
class EnableApp(
    var enable: Boolean = true,
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        spf.Global(ctx).isGloballyEnabled = enable
        G.globallyEnabled.value = enable

        aCtx.logger?.debug("${ctx.getString(R.string.action_enable_app)}: $enable")

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_enable_app)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        SummaryLabel("${Str(R.string.enable)}: ${Str(if (enable) R.string.yes else R.string.no)}")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_enable_app)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_toggle)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabled by remember { mutableStateOf(enable) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabled) { on ->
                enabled = on
                enable = on
            }
        }
    }
}

object ForwardType {
    const val Full = 0
    const val Original = 1
    const val Forwarding = 2
}

// For "API Query" only, not for Workflows.
// This action parses the incoming number and fill the ActionContext with cc/domestic/number,
//  which can be used in following actions like HttpRequest.
@Serializable
// The historical name, it should be renamed to `InterceptCall`.
// If you know how to rename it with history compatibility, please answer here:
//   https://stackoverflow.com/q/79415650/2219196
@SerialName("ParseIncomingNumber")
class InterceptCall(
    var numberFilter: String = ".*",
    var forwardType: Int = ForwardType.Full,
) : IAction {

    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(
            PermissionWrapper(
                Permission.phoneState,
                prompt = ctx.getString(R.string.auto_detect_cc_permission)
            )
        )
    }

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val C = G.palette

        // The `rawNumber` is set by the workflow caller before it's executed.
        val rawNumber = aCtx.rawNumber!!
        aCtx.logger?.debug("${label(ctx)}: $rawNumber")

        val matchesFilter = numberFilter.toRegex().matches(rawNumber)
        if (!matchesFilter) {
            aCtx.logger?.debug(
                ctx.getString(R.string.number_not_match_filter)
                    .format(rawNumber, numberFilter)
            )
            return false
        }

        var clearedNumber = Util.clearNumber(rawNumber)

        // Handle forwarded number like "111&222"
        if (Util.isAmpersandNumber(clearedNumber)) {
            when (forwardType) {
                ForwardType.Full -> {}
                ForwardType.Forwarding -> {
                    clearedNumber = clearedNumber.split("&").first() // keep "111"
                }
                ForwardType.Original -> {
                    clearedNumber = clearedNumber.split("&").last() // keep "222"
                }
            }
            // Show some log
            when (forwardType) {
                ForwardType.Forwarding, ForwardType.Original -> {
                    aCtx.logger?.warn(
                        ctx.getString(R.string.forwarded_number_modified_to)
                            .formatAnnotated(clearedNumber.A(C.textGrey.darken()))
                    )
                }
            }
        }

        if (rawNumber.startsWith("+")) {
            aCtx.fullNumber = clearedNumber
            val (ok, cc, domestic) = CountryCode.parseCcDomestic(clearedNumber)
            if (ok) { // +1 222 333 4444 mobile format
                aCtx.cc = cc
                aCtx.domestic = domestic
            } else { // unknown error
                aCtx.logger?.error(ctx.getString(R.string.fail_detect_cc) + " " + ctx.getString(R.string.bug_number))
                return false
            }
            return true
        } else { // not start with "+"
            val cc = CountryCode.current(ctx)
            if (cc == null) {
                aCtx.logger?.error(ctx.getString(R.string.fail_detect_cc))
                return false
            }

            // Not sure if it's possible to have a number start with CC but has no leading `+`,
            //  for instance: 33xxxxxxxx
            // Check the part xxxxxxxx, if it's still a valid number for cc==33,
            //   then the xxxxxxxx is the domestic part.
            // Otherwise, the entire 33xxxxxxxx is a domestic number
            if (clearedNumber.startsWith(cc.toString()) && !Util.isAmpersandNumber(clearedNumber)) {
                val rest = clearedNumber.substring(cc.toString().length)
                val pnUtil = PhoneNumberUtil.getInstance()
                val n = Phonenumber.PhoneNumber().apply {
                    countryCode = cc
                    nationalNumber = rest.toLong()
                }
                if (pnUtil.isValidNumber(n)) { // the number start with CC
                    aCtx.cc = cc.toString()
                    aCtx.domestic = rest
                } else { // it's simply a domestic number
                    aCtx.cc = cc.toString()
                    aCtx.domestic = clearedNumber
                }
            } else { // it's simply a domestic number
                aCtx.cc = cc.toString()
                aCtx.domestic = clearedNumber
            }
        }

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_parse_incoming_number)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(4) {
            GreyIcon20(R.drawable.ic_filter)
            SummaryLabel(numberFilter)
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_parse_incoming_number).format(
            ctx.getString(R.string.number_tags)
        )
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon20(iconId = R.drawable.ic_call)
    }

    @Composable
    override fun Options() {
        val ctx = LocalContext.current

        val dummyFlags = remember { mutableIntStateOf(Def.FLAG_REGEX_RAW_NUMBER) }
        RegexInputBox(
            regexStr = numberFilter,
            label = { Text(Str(R.string.number_filter)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_filter) },
            helpTooltipId = R.string.help_number_filter,
            placeholder = { Placeholder(".*") },
            regexFlags = dummyFlags,
            showFlagsIcon = false,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    numberFilter = newVal
                }
            },
            onFlagsChange = { }
        )

        // Forward Type
        var selectedForwardType by remember { mutableIntStateOf(forwardType) }
        val options = remember {
            listOf(
                R.string.raw,
                R.string.original,
                R.string.forwarding,
            ).mapIndexed { index, labelId ->
                LabelItem(label = ctx.getString(labelId)) {
                    forwardType = index
                    selectedForwardType = index
                }
            }
        }
        LabeledRow(
            R.string.forwarding,
            helpTooltip = Str(R.string.help_truncate_forwarded_number).format(
                Str(R.string.explanation_forwarded_call)
            )
        ) {
            ComboBox(options, selectedForwardType)
        }
    }
}


@Serializable
@SerialName("InterceptSms")
class InterceptSms(
) : IPermissiveAction {
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        // Nothing to do
        // It just needs to be there, as the first action in the workflow.
        // Because new SMS will only be checked by such workflows.
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_parse_incoming_sms)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_parse_incoming_sms)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon20(iconId = R.drawable.ic_sms)
    }

    @Composable
    override fun Options() {
        NoOptionNeeded()
    }
}

// The return value of the Action ParseQueryResult,
//  and it will be saved along with other information in HistoryTable.
@Serializable
@SerialName("ApiQueryResult")
data class ApiQueryResult(
    val determined: Boolean = false,
    // These values are only useful when determined == true
    val isSpam: Boolean = false,
    val category: String? = null,
    val comment: String? = null,
    val serverEcho: String? = null,
)

@Serializable
@SerialName("ParseQueryResult")
class ParseQueryResult(
    var negativeSig: String = "", // positive signature
    var negativeFlags: Int = Def.DefaultRegexFlags,

    var positiveSig: String = "",
    var positiveFlags: Int = Def.DefaultRegexFlags,

    var categorySig: String = "",
    var categoryFlags: Int = Def.DefaultRegexFlags,

    var commentSig: String = "",
    var commentFlags: Int = Def.DefaultRegexFlags,

    var categoryMapping: String = "",
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = if (aCtx.lastOutput is ByteArray) {
            aCtx.lastOutput as ByteArray
        } else {
            aCtx.lastParsedQueryData!!
        }
        aCtx.lastParsedQueryData = input // Save for following `ParseQueryResult` actions

        val html = String(input).unescapeUnicode()

        aCtx.logger?.debug(label(ctx))

        // 1. negative
        val negativeOpts = Util.flagsToRegexOptions(negativeFlags)
        val isNegative = if (negativeSig.isEmpty())
            null
        else
            negativeSig.toRegex(negativeOpts).containsMatchIn(html)

        // 2. positive
        val positiveOpts = Util.flagsToRegexOptions(positiveFlags)
        val isPositive = if (positiveSig.isEmpty())
            null
        else
            positiveSig.toRegex(positiveOpts).containsMatchIn(html)

        var category: String? = null
        var comment: String? = null

        val determined = isNegative == true || isPositive == true

        // 3. extract category
        if (determined) {
            if (categorySig.trim().isNotEmpty()) {
                val mapping = if (categoryMapping.isNotEmpty()) {
                    JSONObject(categoryMapping).toMap()
                } else {
                    mapOf()
                }

                val opts = Util.flagsToRegexOptions(categoryFlags)
                category = categorySig.trim().toRegex(opts).findAll(html)
                    .map {
                        it.groups
                            .drop(1)
                            .filterNotNull()
                            .firstOrNull()?.value
                    }
                    .filterNot { it.isNullOrEmpty() }
                    .map {
                        // Map "1", "2" to human readable "Fraud", "Political" if it's configured
                        //  in the CategoryMapping, otherwise keep it as is.
                        mapping.getOrDefault(it, it) as String
                    }
                    .joinToString(" | ")
            }
        }

        // 4. extract comments
        if (determined) {
            if (commentSig.trim().isNotEmpty()) {
                comment = commentSig.regexExtract(html, commentFlags)
            }
        }

        // show log
        if (isNegative == true) {
            aCtx.logger?.error(
                ctx.getString(R.string.identified_as_spam)
                    .format(category ?: "", comment ?: "")
            )
        } else if (isPositive == true) {
            aCtx.logger?.success(
                ctx.getString(R.string.identified_as_valid)
                    .format(category ?: "", comment ?: "")
            )
        } else {
            aCtx.logger?.debug(ctx.getString(R.string.unidentified_number))
        }

        // Only update the racingResult when it's the first `ParseQueryResult` or determined.
        if (determined || aCtx.racingResult == null) {
            val result = ApiQueryResult(
                determined = determined,
                isSpam = isNegative == true,
                category = category,
                comment = comment,
                serverEcho = html,
            )
            aCtx.lastOutput = result
            aCtx.racingResult = result
        }

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_parse_query_result)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(4) {
            if (negativeSig.isNotEmpty()) {
                RowVCenter {
                    GreyIcon16(R.drawable.ic_no)
                    SummaryLabel(negativeSig)
                }
            }

            if (positiveSig.isNotEmpty()) {
                RowVCenter {
                    GreyIcon16(R.drawable.ic_yes)
                    SummaryLabel(positiveSig)
                }
            }

            if (categorySig.isNotEmpty()) {
                RowVCenter {
                    GreyIcon16(R.drawable.ic_category)
                    SummaryLabel(categorySig)
                }
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_parse_query_result)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray, ParamType.InstantQueryResult)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None, ParamType.InstantQueryResult)
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_find_check)
    }

    @Composable
    override fun Options() {
        val negativeFlagsCopy = remember { mutableIntStateOf(negativeFlags) }
        RegexInputBox(
            regexStr = negativeSig,
            label = { Text(Str(R.string.negative_identifier)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_no) },
            helpTooltipId = R.string.help_negative_identifier,
            placeholder = { Placeholder(Str(R.string.hint_negative_identifier)) },
            regexFlags = negativeFlagsCopy,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    negativeSig = newVal
                }
            },
            onFlagsChange = {
                negativeFlagsCopy.intValue = it
                negativeFlags = it
            }
        )
        val positiveFlagsCopy = remember { mutableIntStateOf(positiveFlags) }
        RegexInputBox(
            regexStr = positiveSig,
            label = { Text(Str(R.string.positive_identifier)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_yes) },
            helpTooltipId = R.string.help_positive_identifier,
            placeholder = { Placeholder(Str(R.string.hint_positive_identifier)) },
            regexFlags = positiveFlagsCopy,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    positiveSig = newVal
                }
            },
            onFlagsChange = {
                positiveFlagsCopy.intValue = it
                positiveFlags = it
            }
        )
        val reasonFlagsCopy = remember { mutableIntStateOf(categoryFlags) }
        RegexInputBox(
            regexStr = categorySig,
            label = { Text(Str(R.string.category_identifier)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_category) },
            helpTooltipId = R.string.help_category_identifier,
            placeholder = { Placeholder(Str(R.string.hint_category_identifier)) },
            regexFlags = reasonFlagsCopy,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    categorySig = newVal
                }
            },
            onFlagsChange = {
                reasonFlagsCopy.intValue = it
                categoryFlags = it
            }
        )

        var jsonStr by remember { mutableStateOf(categoryMapping) }
        StrInputBox(
            text = jsonStr,
            label = { Text(Str(R.string.category_mapping)) },
            leadingIconId = R.drawable.ic_category,
            placeholder = { Placeholder(Str(R.string.category_mapping_placeholder)) },
            helpTooltip = Str(R.string.help_category_mapping),
            onValueChange = { newVal ->
                categoryMapping = newVal
            }
        )

        val commentFlagsCopy = remember { mutableIntStateOf(commentFlags) }
        RegexInputBox(
            regexStr = commentSig,
            label = { Text(Str(R.string.comment_identifier)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_note) },
            helpTooltipId = R.string.help_comment_identifier,
            placeholder = { Placeholder(Str(R.string.hint_comment_identifier)) },
            regexFlags = commentFlagsCopy,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    commentSig = newVal
                }
            },
            onFlagsChange = {
                commentFlagsCopy.intValue = it
                commentFlags = it
            }
        )
    }
}

// Generate a List<RegexRule> for the next ImportToSpamDB
@Serializable
@SerialName("FilterSpamResult")
class FilterSpamResult() : IPermissiveAction {
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (aCtx.lastOutput is ApiQueryResult) { // In an api query workflow
            val input = aCtx.lastOutput as ApiQueryResult

            aCtx.lastOutput = if (input.determined && input.isSpam) {
                listOf(
                    RegexRule(
                        pattern = aCtx.rawNumber!!,
                        isBlacklist = true,
                    )
                )
            } else
                listOf()
        } else { // This is an api report workflow
            aCtx.lastOutput = if (aCtx.tagCategoryValue == tagValid) {
                listOf()
            } else {
                listOf(
                    RegexRule(
                        pattern = aCtx.rawNumber!!,
                        isBlacklist = true,
                    )
                )
            }
        }

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_filter_query_result)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_filter_query_result)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(
            ParamType.None,
//            ParamType.InstantQueryResult, // for api query
//            ParamType.ByteArray, // for reporting, making it chainable after an HttpRequest
        )
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_filter)
    }

    @Composable
    override fun Options() {
        NoOptionNeeded()
    }
}

// Spam category config, for reporting number.
@Serializable
@SerialName("CategoryConfig")
class CategoryConfig(
    // e.g.:
    //  {
    //    "{fraud}" to "G_FRAUD",
    //    "{advertising}" to "E_ADVERTISING",
    //    ...
    //  }
    var map: Map<String, String> = mapOf()
) : IPermissiveAction {

    // It gets the ActionContext.tagCategory tag and fill the ActionContext.realCategory
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val C = G.palette

        // 1. check it in the map config
        val realCategoryStr = map[aCtx.tagCategoryValue]

        if (realCategoryStr == null) {
            aCtx.logger?.warn(
                ctx.getString(R.string.missing_category).formatAnnotated(
                    aCtx.tagCategoryValue!!.A(C.infoBlue),
                    ctx.getString(R.string.action_category_config).A(C.textGrey.darken())
                )
            )
            return false
        }

        // 2. save it in ActionContext, it will be used in the next http Action
        aCtx.realCategoryValue = realCategoryStr
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_category_config)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_category_config)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_category)
    }

    @Composable
    override fun Options() {
        var jsonStr by remember { mutableStateOf(PermissivePrettyJson.encodeToString(map)) }
        StrInputBox(
            text = jsonStr,
            label = { Text(Str(R.string.action_category_config)) },
            leadingIconId = R.drawable.ic_category,
            placeholder = {
                Placeholder(
                    """
                {
                  "{marketing}": "gym",
                  "{other}": "bitcoin",
                  ...
                }
            """.trimIndent()
                )
            },
            helpTooltip = Str(R.string.help_action_category_config),
            onValueChange = { newVal ->
                try {
                    map = PermissiveJson.decodeFromString(newVal)
                    jsonStr = newVal
                } catch (_: Exception) {
                }
            }
        )
    }
}

// Report number to all api endpoints.
// (For internal app usage only.)
@Serializable
@SerialName("ReportNumber")
class ScheduledAutoReportNumber(
    val rawNumber: String,
    val asTagCategory: String,
    val blockReason: Int?,
    val domainFilter: List<String>? = null // only report to APIs that matches these domains
) : IAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(
            PermissionWrapper(
                Permission.callLog,
                prompt = ctx.getString(R.string.report_number_require_call_log_permission)
            ),
        )
    }

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val apis = listReportableAPIs(
            ctx = ctx, rawNumber = rawNumber, domainFilter = domainFilter, blockReason = blockReason
        )

        // Report
        val scope = CoroutineScope(IO)
        apis.forEach { api ->
            scope.launch {
                val aCtx = ActionContext(
                    scope = scope,
                    logger = AdbLogger(),
                    rawNumber = rawNumber,
                    tagCategoryValue = asTagCategory,
                )
                val success = api.actions.executeAll(ctx, aCtx)
                logi("report number $rawNumber to ${api.summary()}, success: $success")
            }
            true
        }

        return true
    }

    override fun label(ctx: Context): String {
        return "Report Number" // it will not be displayed on UI
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
    }

    override fun tooltip(ctx: Context): String {
        return ""
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
    }

    @Composable
    override fun Options() {
    }
}


// Modify the incoming number before screening.
// Use case: Some MNO provide services that allow a secondary number for the same SIM card. For number
//  3377777777, the secondary number can be "1113377777777" (with an extra prefix 111),
//  the prefix 111 needs to be removed.
@Serializable
@SerialName("ModifyNumber")
class ModifyNumber(
    var from: String = "",
    var fromFlags: Int = Def.DefaultRegexFlags,
    var to: String = "",
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val C = G.palette

        val cCtx = aCtx.cCtx!!

        val before = cCtx.rawNumber
        val after = before.regexReplace(from, to, fromFlags)

        if (before != after) {
            cCtx.rawNumber = after

            aCtx.logger?.warn(
                ctx.getString(R.string.modify_number_template)
                    .formatAnnotated(
                        before.A(C.textGrey.darken()),
                        after.A(C.textGrey.darken())
                    )
            )
        }

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_modify_number)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(8) {
            SummaryLabel("$from -> $to")
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_modify_number)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_replace)
    }

    @Composable
    override fun Options() {
        val fromFlagsState = remember { mutableIntStateOf(fromFlags) }
        RegexInputBox(
            regexStr = from,
            label = { Text(Str(R.string.replace_from)) },
            regexFlags = fromFlagsState,
            showNumberFlags = true,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    from = newVal
                }
            },
            onFlagsChange = {
                fromFlagsState.intValue = it
                fromFlags = it
            }
        )
        StrInputBox(
            text = to,
            label = { Text(Str(R.string.replace_to)) },
            onValueChange = {
                to = it
            }
        )
    }
}

@Serializable
@SerialName("GenerateTag")
class GenerateTag(
    var tagName: String = "",

    var parseType: Int = 0, // 0: regex, 1: xpath, 2: json path (in the future)

    // Regex
    var regex: String = "",
    var regexFlags: Int = Def.DefaultRegexFlags,

    // XPath
    var xpath: String = "",

    // Json path (there's no built-in support, use regex instead)
//    var jsonPath: String = "",
) : IPermissiveAction {
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val C = G.palette

        val input = aCtx.lastOutput as ByteArray

        val text = input.toString(Charsets.UTF_8)

        var tagValue: String? = null
        try {
            when(parseType) {
                0 -> { // regex
                    val opts = Util.flagsToRegexOptions(regexFlags)
                    val reg = regex.toRegex(opts)
                    tagValue = Util.extractString(reg, text)
                }
                1 -> { // xpath
                    tagValue = Xml.parseString(input, xpath)
                }
            }

            aCtx.customTags[tagName] = tagValue ?: ""

            aCtx.logger?.info(ctx.getString(R.string.tag_generated)
                .formatAnnotated(
                    tagName.A(C.textGrey.darken()),
                    (tagValue ?: "").A(C.teal200),
                )
            )
        } catch (e: Exception) {
            aCtx.logger?.error(ctx.getString(R.string.failed_to_parse_tag))
            aCtx.logger?.error(e.message ?: "")
        }

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.generate_tag)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        when(parseType) {
            0 -> {
                SummaryLabel("{${tagName}} = $regex")
            }
            1 -> {
                SummaryLabel("{${tagName}} = $xpath")
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_generate_tag)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None, ParamType.ByteArray)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_tag_add)
    }

    @Composable
    override fun Options() {
        Column {
            // Tag Name
            var tagNameState by remember { mutableStateOf(tagName) }
            StrInputBox(
                text = tagNameState,
                label = { Text(Str(R.string.tag_name)) },
                leadingIconId = R.drawable.ic_tag_edit,
                onValueChange = {
                    tagName = it
                    tagNameState = it
                },
                supportingTextStr = if (tagNameState.trim().startsWith("{")) {
                    Str(R.string.invalid_tag_name)
                } else null
            )
            // Parse Type
            var selectedParseType by remember { mutableIntStateOf(parseType) }
            val options = remember {
                listOf("RegEx", "XPath").mapIndexed { index, label ->
                    LabelItem(label = label) {
                        parseType = index
                        selectedParseType = index
                    }
                }
            }
            LabeledRow(R.string.parse_type) {
                ComboBox(options, selectedParseType)
            }

            // Regex / XPath
            when (selectedParseType) {
                0 -> {
                    val flags = remember { mutableIntStateOf(regexFlags) }
                    RegexInputBox(
                        regexStr = regex,
                        label = { Text(Str(R.string.regex_pattern)) },
                        regexFlags = flags,
                        leadingIcon = { GreyIcon(R.drawable.ic_regex) },
                        placeholder = { Placeholder("code: (\\d+)") },
                        onRegexStrChange = { newVal, hasError ->
                            if (!hasError) {
                                regex = newVal
                            }
                        },
                        onFlagsChange = {
                            flags.intValue = it
                            regexFlags = it
                        }
                    )
                }
                1 -> {
                    StrInputBox(
                        text = xpath,
                        label = { Text("XPath") },
                        leadingIconId = R.drawable.ic_xpath,
                        onValueChange = {
                            xpath = it
                        },
                    )
                }
            }
        }
    }
}

@Serializable
@SerialName("LoadBotTag")
class LoadBotTag(
    var tagName: String = "",
    var defaultValue: String = "",
) : IPermissiveAction {
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val C = G.palette

        val logger = aCtx.logger

        val botId = aCtx.botId
        if (botId == null) {
            logger?.error(ctx.getString(R.string.workflow_not_saved))
            return false
        }

        val bot = BotTable.findById(ctx, botId)
        val value = bot?.customTags?.get(tagName) ?: defaultValue
        aCtx.customTags[tagName] = value

        logger?.debug(ctx.getString(R.string.tag_is_set_to).formatAnnotated(
            tagName.A(C.textGrey.darken()), value.A(C.teal200)
        ))

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.load_tag)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        SummaryLabel("{${tagName}} ?= $defaultValue")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_load_tag)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_tag_up)
    }

    @Composable
    override fun Options() {
        Column {
            // Tag Name
            var tagNameState by remember { mutableStateOf(tagName) }
            StrInputBox(
                text = tagNameState,
                label = { Text(Str(R.string.tag_name)) },
                leadingIconId = R.drawable.ic_tag_edit,
                onValueChange = {
                    tagName = it
                    tagNameState = it
                },
                supportingTextStr = if (tagNameState.trim().startsWith("{")) {
                    Str(R.string.invalid_tag_name)
                } else null
            )

            // Default Value
            var defValState by remember { mutableStateOf(defaultValue) }
            StrInputBox(
                text = defValState,
                label = { Text(Str(R.string.default_value)) },
                leadingIconId = R.drawable.ic_note,
                onValueChange = {
                    defaultValue = it
                    defValState = it
                },
            )
        }
    }
}

@Serializable
@SerialName("SaveBotTag")
class SaveBotTag(
    var tagName: String = "",
) : IPermissiveAction {
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val C = G.palette

        val logger = aCtx.logger

        val botId = aCtx.botId
        if (botId == null) {
            logger?.error(ctx.getString(R.string.workflow_not_saved))
            return false
        }

        // 1. load from db
        val tags = BotTable.findById(ctx, botId)?.customTags ?: mutableMapOf()
        // 2. update value
        val value = aCtx.customTags[tagName] ?: ""
        tags[tagName] = value
        // 3. save to db
        BotTable.updateById(ctx, botId, customTags = tags)

        logger?.debug(ctx.getString(R.string.tag_is_saved_as).formatAnnotated(
            tagName.A(C.textGrey.darken()), value.A(C.teal200)
        ))

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.save_tag)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        SummaryLabel("{${tagName}}")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_save_tag)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_tag_down)
    }

    @Composable
    override fun Options() {
        Column {
            // Tag Name
            var tagNameState by remember { mutableStateOf(tagName) }
            StrInputBox(
                text = tagNameState,
                label = { Text(Str(R.string.tag_name)) },
                leadingIconId = R.drawable.ic_tag_edit,
                onValueChange = {
                    tagName = it
                    tagNameState = it
                },
                supportingTextStr = if (tagNameState.trim().startsWith("{")) {
                    Str(R.string.invalid_tag_name)
                } else null
            )
        }
    }
}

@Serializable
@SerialName("SetTag")
class SetTag(
    var tagName: String = "",
    var tagValue: String = "",
) : IPermissiveAction {
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val C = G.palette

        aCtx.customTags[tagName] = tagValue

        aCtx.logger?.debug(ctx.getString(R.string.tag_is_set_to).formatAnnotated(
            tagName.A(C.textGrey.darken()), tagValue.A(C.teal200)
        ))

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.set_tag)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        SummaryLabel("{$tagName} = $tagValue")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_set_tag)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_tag_edit)
    }

    @Composable
    override fun Options() {
        Column {
            // Tag Name
            var tagNameState by remember { mutableStateOf(tagName) }
            StrInputBox(
                text = tagNameState,
                label = { Text(Str(R.string.tag_name)) },
                leadingIconId = R.drawable.ic_tag_edit,
                onValueChange = {
                    tagName = it
                    tagNameState = it
                },
                supportingTextStr = if (tagNameState.trim().startsWith("{")) {
                    Str(R.string.invalid_tag_name)
                } else null
            )

            // Tag Value
            var valueState by remember { mutableStateOf(tagValue) }
            StrInputBox(
                text = valueState,
                label = { Text(Str(R.string.tag_value)) },
                leadingIconId = R.drawable.ic_note,
                onValueChange = {
                    tagValue = it
                    valueState = it
                },
            )
        }
    }
}


@Serializable
@SerialName("Wait")
class Wait(
    var seconds: Int = 0,
) : IPermissiveAction {
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        aCtx.logger?.debug(ctx.getString(R.string.wait) + " " + ctx.getString(R.string.seconds_template).format(seconds))
        Thread.sleep((seconds * 1000).toLong())

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.wait)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        SummaryLabel(Str(R.string.seconds_template).format(seconds))
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_wait)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_delay)
    }

    @Composable
    override fun Options() {
        Column {
            NumberInputBox(
                intValue = seconds,
                label = { Text(Str(R.string.duration_in_seconds)) },
                onValueChange = { newVal, hasError ->
                    if (!hasError) {
                        seconds = newVal!!
                    }
                }
            )
        }
    }
}