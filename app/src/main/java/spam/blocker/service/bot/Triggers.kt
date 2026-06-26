package spam.blocker.service.bot

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableColumn
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.service.checker.ByContact
import spam.blocker.service.checker.ByRegexRule
import spam.blocker.service.checker.ByRepeatedCall
import spam.blocker.ui.M
import spam.blocker.ui.SizedBox
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.regex.DisableNestedScrolling
import spam.blocker.ui.setting.regex.RegexMode.ModeType
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.CheckBox
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.GreenDot
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.Placeholder
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.RingtonePicker
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SummaryLabel
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.A
import spam.blocker.util.Contacts
import spam.blocker.util.Lambda1
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.RingtoneUtil
import spam.blocker.util.Util
import spam.blocker.util.formatAnnotated
import spam.blocker.util.regexMatches
import spam.blocker.util.regexMatchesNumber
import spam.blocker.util.spf
import java.util.UUID

@Serializable
@SerialName("Manual")
class Manual() : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf()
    }
    override fun isActivated(): Boolean {
        return false
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        return true // do nothing
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.manual)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(2) {
            if (showIcon) {
                SizedBox(18) { Icon() }
            }
            SummaryLabel(Str(R.string.manual))
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_trigger_manual)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_tap)
    }

    @Composable
    override fun Options() {
        NoOptionNeeded()
    }
}


@Serializable
@SerialName("Schedule")
data class Schedule(
    var enabled: Boolean = true,
    var schedule: ISchedule = defaultSchedules[0].clone(), // nullable for historical reason
    var workUUID: String = UUID.randomUUID().toString(), // the schedule UUID tag
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf()
    }
    override fun isActivated(): Boolean {
        return enabled && schedule.isValid()
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        return true // do nothing
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.schedule)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(6) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            RowVCenterSpaced(4) {
                if (showIcon) {
                    SizedBox(18) { Icon() }
                }
                schedule.Summary()
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_trigger_schedule)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_duration)
    }

    @Composable
    private fun EditScheduleDialog(
        trigger: MutableState<Boolean>,
        schedule: MutableState<ISchedule?>,
    ) {
        if (!trigger.value) {
            return
        }
        PopupDialog(
            trigger = trigger,
            onDismiss = {
                // set to itself to refresh the UI
                val clone = schedule.value!!.serialize().parseSchedule()
                schedule.value = clone
            }
        ) {
            schedule.value!!.Options()
        }
    }

    @Composable
    override fun Options() {
        val ctx = LocalContext.current

        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        val scheduleState = rememberSaveableScheduleState(schedule)

        AnimatedVisibleV(enabledState) {
            Column {
                LabeledRow(R.string.type) {
                    val items = defaultSchedules.map {
                        LabelItem(
                            label = it.label(ctx),
                            leadingIcon = { GreyIcon16(it.iconId()) }
                        ) { menuExpanded ->
                            scheduleState.value = it
                            schedule = it
                            menuExpanded.value = false
                        }
                    }
                    val selected = defaultSchedules.indexOfFirst {
                        it::class == scheduleState.value!!::class
                    }
                    ComboBox(items = items, selected = selected)
                }

                val triggerConfigSchedule = rememberSaveable { mutableStateOf(false) }
                EditScheduleDialog(trigger = triggerConfigSchedule, scheduleState)
                LabeledRow(
                    labelId = scheduleState.value!!.optionLabelId()
                ) {
                    GreyButton(label = scheduleState.value!!.summaryLabel(ctx)) {
                        triggerConfigSchedule.value = true
                    }
                }
            }
        }
    }
}

// Continue or terminate the workflow according to ongoing calendar event.
@Serializable
@SerialName("CalendarEvent")
class CalendarEvent(
    var enabled: Boolean = true,
    var eventTitle: String = "",
    var eventTitleFlags: Int = Def.DefaultRegexFlags,
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(PermissionWrapper(Permission.calendar))
    }
    override fun isActivated(): Boolean {
        return enabled && Permission.calendar.isGranted
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (!isActivated())
            return false

        val ongoingEvents = Util.ongoingCalendarEvents(ctx)
        val triggered = ongoingEvents.any { it ->
            eventTitle.regexMatches(it, eventTitleFlags)
        }
        if (triggered) {
            aCtx.logger?.warn(
                ctx.getString(R.string.calendar_event_is_triggered)
                    .formatAnnotated(
                        eventTitle.A(G.palette.teal200)
                    )
            )
        }
        // Calendar Events modifies rules temporarily.
        aCtx.isInMemory = true

        return triggered
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.calendar_event)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(6) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            RowVCenterSpaced(4) {
                if (showIcon) {
                    SizedBox(18) { Icon() }
                }
                SummaryLabel(eventTitle)
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_calendar_event)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_calendar)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        val flags = remember { mutableIntStateOf(eventTitleFlags) }
        RegexInputBox(
            regexStr = eventTitle,
            label = { Text(Str(R.string.event_title)) },
            regexFlags = flags,
            helpTooltipId = R.string.help_calendar_event_title,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    eventTitle = newVal
                }
            },
            onFlagsChange = {
                flags.intValue = it
                eventTitleFlags = it
            }
        )
    }
}

// This will be triggered on receiving SMS messages
@Serializable
@SerialName("SmsEvent")
class SmsEvent(
    var enabled: Boolean = true,
    var number: String = ".*",
    var numberFlags: Int = Def.DefaultRegexFlags,
    var content: String = ".*",
    var contentFlags: Int = Def.DefaultRegexFlags,
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(
            PermissionWrapper(Permission.receiveSMS),
            PermissionWrapper(Permission.batteryUnRestricted, isOptional = true),
        )
    }

    override fun isActivated(): Boolean {
        return enabled && Permission.receiveSMS.isGranted
    }

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val rawNumber = aCtx.rawNumber
        val smsContent = aCtx.smsContent

        // It's testing in the workflow dialog
        if (rawNumber == null || smsContent == null) {
            aCtx.logger?.error(ctx.getString(R.string.use_global_testing_instead))
            return false
        }

        if (!Permission.receiveSMS.isGranted || !enabled)
            return false

        if (!number.regexMatchesNumber(rawNumber, numberFlags)) {
            return false
        }
        if (!content.regexMatches(smsContent, contentFlags)) {
            return false
        }

        aCtx.logger?.warn(
            ctx.getString(R.string.sms_event_triggered)
                .formatAnnotated(
                    "$content <- $number".A(G.palette.teal200)
                )
        )

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.sms_event)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(6) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            RowVCenterSpaced(4) {
                if (showIcon) {
                    SizedBox(18) { Icon() }
                }
                SummaryLabel("$content <- $number")
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_sms_event)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon20(R.drawable.ic_sms)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        val flagsNumber = remember { mutableIntStateOf(numberFlags) }
        RegexInputBox(
            regexStr = number,
            label = { Text(Str(R.string.phone_number)) },
            regexFlags = flagsNumber,
            placeholder = { Placeholder(".*") },
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    number = newVal
                }
            },
            onFlagsChange = {
                flagsNumber.intValue = it
                numberFlags = it
            }
        )
        val flagsContent = remember { mutableIntStateOf(contentFlags) }
        RegexInputBox(
            regexStr = content,
            label = { Text(Str(R.string.sms_content)) },
            regexFlags = flagsContent,
            placeholder = { Placeholder(".*") },
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    content = newVal
                }
            },
            onFlagsChange = {
                flagsContent.intValue = it
                contentFlags = it
            }
        )
    }
}


// Workflows that contain this preprocessor will be executed before checking the number.
@Serializable
@SerialName("CallEvent")
class CallEvent(
    var enabled : Boolean = true,
    var number: String = ".*",
    var numberFlags: Int = Def.DefaultRegexFlags,
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(
            PermissionWrapper(Permission.callScreening),
        )
    }
    override fun isActivated(): Boolean {
        return enabled && Permission.callScreening.isGranted
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val rawNumber = aCtx.rawNumber

        // It's testing in the workflow dialog
        if (rawNumber == null) {
            aCtx.logger?.error(ctx.getString(R.string.use_global_testing_instead))
            return false
        }
        if (!enabled) {
            return false
        }

        if (!number.regexMatchesNumber(rawNumber, numberFlags)) {
            return false
        }

        aCtx.logger?.warn(
            ctx.getString(R.string.call_event_is_triggered)
                .formatAnnotated(
                    number.A(G.palette.teal200)
                )
        )
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.call_event)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(6) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            RowVCenterSpaced(4) {
                if (showIcon) {
                    SizedBox(18) { Icon() }
                }
                SummaryLabel(number)
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_call_event)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_incoming)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        val flagsNumber = remember { mutableIntStateOf(numberFlags) }
        RegexInputBox(
            regexStr = number,
            label = { Text(Str(R.string.phone_number)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_filter) },
            regexFlags = flagsNumber,
            placeholder = { Placeholder(".*") },
            enableNumberFlags = true,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    number = newVal
                }
            },
            onFlagsChange = {
                flagsNumber.intValue = it
                numberFlags = it
            }
        )
    }
}


@Serializable
@SerialName("CallThrottling")
class CallThrottling(
    var enabled: Boolean = true,
    var durationSec: Int = 30, // 30 seconds
    var includingBlocked: Boolean = true,
    var includingAnswered: Boolean = false,
    var minCallDurationSec: Int = 15, // 15 seconds
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(PermissionWrapper(Permission.callLog))
    }
    override fun isActivated(): Boolean {
        return enabled && Permission.callLog.isGranted
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (aCtx.cCtx?.rawNumber == null) {
            aCtx.logger?.error(ctx.getString(R.string.use_global_testing_instead))
            return false
        }

        if (!isActivated())
            return false

        // Check real call history
        val hasBlockedRealCalls = Util.recentCalls(
            ctx,
            withinMillis = durationSec.toLong() * 1000,
            includingBlocked = includingBlocked,
            includingAnswered = includingAnswered,
            minCallDurationSec = minCallDurationSec,
        ).isNotEmpty()


        // Check testing calls in local db
        var hasBlockedTestingCalls = aCtx.cCtx?.callDetails == null && // Is testing
                includingBlocked &&
                CallTable().hasBlockedRecordsWithinSeconds(ctx, durationSeconds = durationSec)

        if (!hasBlockedRealCalls && !hasBlockedTestingCalls) {
            return false // no recently blocked calls, nothing to do
        }

        aCtx.logger?.warn(ctx.getString(R.string.call_throttling_triggered))

        // Throttling Event modifies rules temporarily.
        aCtx.isInMemory = true

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.call_throttling)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(6) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            RowVCenterSpaced(4) {
                if (showIcon) {
                    SizedBox(18) { Icon() }
                }
                if (includingAnswered) {
                    GreyIcon18(R.drawable.ic_call)
                }
                if (includingBlocked) {
                    GreyIcon18(R.drawable.ic_call_blocked)
                }
                GreyLabel("$durationSec ${Str(R.string.seconds_short)}")
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_call_throttling)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_multi_call)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        // Enabled
        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        // Duration
        NumberInputBox(
            intValue = durationSec,
            label = { Text(Str(R.string.duration_in_seconds)) },
            onValueChange = { newVal, hasError ->
                if (!hasError) {
                    durationSec = newVal!!
                }
            }
        )

        // Include Blocked
        // Must use a state, otherwise the switch doesn't change on click
        var includeBlocked by remember { mutableStateOf(includingBlocked) }

        LabeledRow(labelId = R.string.including_blocked) {
            SwitchBox(includeBlocked) { on ->
                includeBlocked = on
                includingBlocked = on
            }
        }

        // Include Answered
        // Must use a state, otherwise the switch doesn't change on click
        var includeAnswered by remember { mutableStateOf(includingAnswered) }

        LabeledRow(labelId = R.string.including_answered) {
            SwitchBox(includeAnswered) { on ->
                includeAnswered = on
                includingAnswered = on
            }
        }

        // Minimal allowed call duration
        AnimatedVisibleV(includeAnswered) {
            NumberInputBox(
                intValue = minCallDurationSec,
                label = { Text(Str(R.string.minimal_call_duration)) },
                onValueChange = { newVal, hasError ->
                    if (!hasError) {
                        minCallDurationSec = newVal!!
                    }
                }
            )
        }
    }
}


@Serializable
@SerialName("SmsThrottling")
class SmsThrottling(
    var enabled: Boolean = true,
    var durationSec: Int = 60, // 60 seconds
    var countLimit: Int = 3,
    var targetRuleDesc: String = "",
    var targetRuleDescFlags: Int = Def.DefaultRegexFlags,
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(PermissionWrapper(Permission.readSMS))
    }
    override fun isActivated(): Boolean {
        return enabled && Permission.readSMS.isGranted
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (aCtx.cCtx?.rawNumber == null || aCtx.cCtx?.smsContent == null) {
            aCtx.logger?.error(ctx.getString(R.string.use_global_testing_instead))
            return false
        }

        if (!isActivated())
            return false

        val rules = NumberRegexTable().findRuleByDesc(ctx, targetRuleDesc, targetRuleDescFlags)
        if (rules.isEmpty())
            return false
        val rule = rules[0]

        val forContact = rule.patternModeType == ModeType.ContactName
        val forContactGroup = rule.patternModeType == ModeType.ContactGroup

        val matches = fun(rawNumber: String) : Boolean {
            return if (forContact) {
                val contactInfo = Contacts.findContactByRawNumber(ctx, rawNumber)
                contactInfo != null && rule.matches(contactInfo.name)
            } else if (forContactGroup) {
                Contacts.findGroupsContainNumber(ctx, rawNumber)
                    .find { groupName ->
                        rule.matches(groupName)
                    } != null
            } else {
                rule.pattern.regexMatchesNumber(rawNumber, rule.patternFlags)
            }
        }

        // Get from system SMS history
        var smses = Util.getHistorySMSes(ctx, Def.DIRECTION_INCOMING, durationSec.toLong()*1000)
            .filter {
                matches(it.rawNumber) // by sender category
                // it.rawNumber == aCtx.rawNumber // by sender
            }
            .map {it.rawNumber}

        if (smses.isEmpty()) {
            // Get from local db for testing
            smses = SmsTable().getRecordsWithinSeconds(ctx, durationSec)
                .filter {
                    matches(it.peer) // by sender category
                    // it.peer == aCtx.rawNumber // by sender
                }
                .map { it.peer }
            if (smses.isEmpty())
                return false
        }

        val matchCount = smses.size

        if (matchCount < countLimit) {
            return false
        }

        aCtx.logger?.warn(ctx.getString(R.string.sms_throttling_triggered))

        // Throttling Event modifies rules temporarily.
        aCtx.isInMemory = true

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.sms_throttling)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(6) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            RowVCenterSpaced(4) {
                if (showIcon) {
                    SizedBox(18) { Icon() }
                }
                GreyLabel("$countLimit/$durationSec")
                GreyLabel(targetRuleDesc)
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_sms_throttling)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_multi_sms)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        // Enabled
        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        // Duration
        NumberInputBox(
            intValue = durationSec,
            label = { Text(Str(R.string.duration_in_seconds)) },
            onValueChange = { newVal, hasError ->
                if (!hasError) {
                    durationSec = newVal!!
                }
            }
        )

        // Count Limit
        NumberInputBox(
            intValue = countLimit,
            label = { Text(Str(R.string.count_limit)) },
            onValueChange = { newVal, hasError ->
                if (!hasError) {
                    countLimit = newVal!!
                }
            }
        )

        // Target rule desc
        val flagsState = remember { mutableIntStateOf(targetRuleDescFlags) }
        RegexInputBox(
            regexStr = targetRuleDesc,
            label = { Text(Str(R.string.target_rule_desc)) },
            regexFlags = flagsState,
            helpTooltipId = R.string.help_target_rule_desc,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    targetRuleDesc = newVal
                }
            },
            onFlagsChange = {
                flagsState.intValue = it
                targetRuleDescFlags = it
            }
        )
    }
}


@Serializable
enum class RingtoneRuleType {
    NumberRule,
    RepeatedCall,
    Contacts,
}

@Serializable
@SerialName("RingtoneRule")
data class RingtoneRule(
    val type: RingtoneRuleType = RingtoneRuleType.NumberRule,
    val extraRegex: String? = null, // a regex string

    // ringtone settings
    var mute: Boolean = false,
    var ringtoneUri: String? = null,
    var delaySec: Int = 5,
) {
    fun matches(ctx: Context, aCtx: ActionContext): Boolean {
        return when(type) {
            RingtoneRuleType.NumberRule -> {
                (aCtx.checkResult as? ByRegexRule)?.let {
                    (this.extraRegex ?: "").regexMatches(it.rule!!.description)
                } ?: false
            }
            RingtoneRuleType.RepeatedCall -> {
                aCtx.checkResult is ByRepeatedCall
            }
            RingtoneRuleType.Contacts -> {
                aCtx.checkResult is ByContact
            }
        }
    }

    @Composable
    fun TriggerSummary() {
        RowVCenterSpaced(4) {
            when (type) {
                RingtoneRuleType.NumberRule -> {
                    GreyIcon18(iconId = R.drawable.ic_regex)
                    GreyLabel(extraRegex ?: "")
                }
                RingtoneRuleType.RepeatedCall -> {
                    GreyIcon18(iconId = R.drawable.ic_repeat)
                    GreyLabel(Str(R.string.repeated_call))
                }
                RingtoneRuleType.Contacts -> {
                    GreyIcon18(iconId = R.drawable.ic_contact_circle)
                    GreyLabel(Str(R.string.contacts))
                }
            }
        }
    }

    @Composable
    fun RingtoneSummary() {
        val ctx = LocalContext.current

        if (mute) {
            GreyIcon18(R.drawable.ic_bell_mute)
        } else {
            val uri = ringtoneUri?.toUri() ?: RingtoneUtil.getCurrent(ctx)
            GreyLabel(RingtoneUtil.getName(ctx, uri))
        }
    }

    fun requiredPermissions(): List<PermissionWrapper> {
        return if (mute) // mute doesn't write to system settings
            listOf()
        else
            listOf(PermissionWrapper(Permission.writeSettings))
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ReplyRuleEditDialog(
    trigger: MutableState<Boolean>,
    rule: RingtoneRule,
    onUpdate: Lambda1<RingtoneRule>
) {
    if (trigger.value) {
        var typeIndex by retain { mutableIntStateOf(RingtoneRuleType.entries.indexOf(rule.type)) }
        var extraRegex by retain { mutableStateOf(rule.extraRegex) }
        var mute by retain { mutableStateOf(rule.mute) }
        var ringtoneUri by retain { mutableStateOf(rule.ringtoneUri) }
        var delaySec by retain { mutableStateOf(rule.delaySec) }

        PopupDialog(
            trigger,
            onDismiss = {
                val newRule = RingtoneRule(
                    type = RingtoneRuleType.entries[typeIndex],
                    extraRegex = extraRegex,
                    mute = mute,
                    ringtoneUri = ringtoneUri,
                    delaySec = delaySec,
                )
                if(newRule != rule) {
                    onUpdate(newRule)
                }
            }
        ) {
            val ctx = LocalContext.current
            val C = G.palette

            Section(
                Str(R.string.trigger),
                bgColor = C.dialogBg
            ) {
                Column {
                    LabeledRow(R.string.trigger) {
                        val items = listOf(
                            Str(R.string.number_rule),
                            Str(R.string.repeated_call),
                            Str(R.string.contacts),
                        )
                        val iconIds = listOf(
                            R.drawable.ic_regex,
                            R.drawable.ic_repeat,
                            R.drawable.ic_contact_circle,
                        )
                        ComboBox(
                            items = items.mapIndexed { index, label ->
                                LabelItem(
                                    label = label,
                                    leadingIcon = { GreyIcon18(iconIds[index]) }
                                ) {
                                    typeIndex = index
                                }
                            },
                            selected = typeIndex,
                        )
                    }
                    AnimatedVisibleV(typeIndex == 0) { // Number Rule
                        val dummyFlags = retain { mutableIntStateOf(Def.DefaultRegexFlags) }
                        RegexInputBox(
                            regexStr = extraRegex ?: "",
                            onRegexStrChange = { newVal, hasErr ->
                                if (!hasErr) {
                                    extraRegex = newVal
                                }
                            },
                            label = {
                                Text(Str(R.string.rule_description))
                            },
                            helpTooltipId = R.string.find_regex_rule_by_description,

                            regexFlags = dummyFlags,
                            onFlagsChange = {},
                            showFlagsIcon = false,
                        )
                    }
                }
            }


            Section(
                Str(R.string.ringtone),
                bgColor = C.dialogBg
            ) {
                Column {
// Mute + Sound
                    var sound by remember { mutableStateOf(
                        ringtoneUri?.toUri() ?: RingtoneUtil.getCurrent(ctx)
                    ) }
                    var soundName by remember(sound) {
                        mutableStateOf(RingtoneUtil.getName(ctx, sound))
                    }

                    val soundTrigger = remember { mutableStateOf(false) }
                    RingtonePicker(soundTrigger) { uri, name ->
                        uri?.let {
                            sound = uri.toUri()
                            ringtoneUri = uri
                        }
                    }
                    // Mute
                    var muteState by remember { mutableStateOf(mute) }
                    LabeledRow(R.string.mute) {
                        SwitchBox(muteState) { on ->
                            mute = on
                            muteState = on
                        }
                    }

                    // Ringtone
                    AnimatedVisibleV(!muteState) {
                        LabeledRow(
                            labelId = R.string.ringtone,
                        ) {
                            RowVCenterSpaced(6) {
                                GreyButton(soundName) {
                                    soundTrigger.value = true
                                }
                            }
                        }
                    }

                    // Delay
                    // Balloon text
                    val tooltipText by remember {
                        derivedStateOf {
                            ctx.getString(R.string.help_ringtone_delay)
                                .format(delaySec)
                        }
                    }
                    NumberInputBox(
                        intValue = delaySec,
                        label = { Text(Str(R.string.delay_in_seconds)) },
                        helpTooltip = { BalloonQuestionMark(tooltipText) },
                        onValueChange = { newVal, hasError ->
                            if (!hasError) {
                                delaySec = newVal!!
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RingtoneRuleCard(
    rule: RingtoneRule,
    modifier: Modifier
) {
    OutlineCard(containerBg = G.palette.dialogBg, modifier = modifier) {
        RowVCenterSpaced(
            4,
            modifier = M.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(modifier = M.weight(1f)) {
                // Row 1, Trigger Type
                rule.TriggerSummary()

                // Row 2, Ringtone Summary
                RowVCenterSpaced(4) {
                    GreyIcon18(R.drawable.ic_music)
                    rule.RingtoneSummary()
                }
            }

            // Reorder icon
            GreyIcon16(iconId = R.drawable.ic_reorder)
        }
    }
}

@Serializable
@SerialName("Ringtone")
class Ringtone(
    var enabled: Boolean = true, // always enabled, can't be disabled
    var rules: List<RingtoneRule> = listOf(),
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return rules.flatMap { it.requiredPermissions() }.distinct()
    }
    override fun isActivated(): Boolean {
        if (!enabled)
            return false

        val anyNonMute = rules.any { !it.mute }

        return if (anyNonMute)
            Permission.writeSettings.isGranted
        else
            true
    }

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (!isActivated())
            return false

        val r = aCtx.checkResult

        if (r == null) {
            aCtx.logger?.error(ctx.getString(R.string.call_to_test_ringtone))
            return false
        }

        val rule = rules.firstOrNull {
            it.matches(ctx, aCtx)
        }

        if (rule == null) {
            return false
        } else {
            if (!rule.mute) {
                RingtoneUtil.setDefaultUri(ctx, (rule.ringtoneUri ?: "").toUri())

                // Reset the ringtone after N seconds, the ringing should've already started.
                CoroutineScope(IO).launch {
                    delay(rule.delaySec.toLong() * 1000)
                    resetToPreviousRingtone(ctx)
                }
            }

            // this will be used in CallScreeningService, ugly workaround..
            aCtx.shouldMute = rule.mute

            return true
        }
    }
    private fun resetToPreviousRingtone(ctx: Context) {
        // 1. check if it was set in CallScreeningService
        val spf = spf.Temporary(ctx)
        val previousRingtone = spf.ringtone
        if (previousRingtone.isEmpty())
            return
        spf.ringtone = "" // clear in spf

        // 2. check permission
        if (!Permission.writeSettings.isGranted)
            return

        // 3. restore to the previous ringtone
        RingtoneUtil.setDefaultUri(ctx, previousRingtone.toUri())
    }


    override fun label(ctx: Context): String {
        return ctx.getString(R.string.ringtone)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(6) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            RowVCenterSpaced(4) {
                if (showIcon) {
                    SizedBox(18) { Icon() }
                }
                if (rules.size == 1) {
                    rules.first().RingtoneSummary()
                } else {
                    GreyLabel(
                        rules.take(5).map {
                            when (it.type) {
                                RingtoneRuleType.NumberRule -> it.extraRegex ?: ""
                                RingtoneRuleType.RepeatedCall -> Str(R.string.repeated_call)
                                RingtoneRuleType.Contacts -> Str(R.string.contacts)
                            }
                        }.joinToString(", ")
                    )
                }
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_ringtone)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_music)
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    @Composable
    override fun Options() {
        val C = G.palette

        val rulesState = retain { mutableStateListOf(*rules.toTypedArray()) }

        LabeledRow(R.string.rules, helpTooltip = Str(R.string.priority_in_order)) {
            StrokeButton(Str(R.string.new_), C.infoBlue) {
                rulesState += RingtoneRule()
                rules = rulesState
            }
        }

        ReorderableColumn(
            list = rulesState.toList(),
            modifier = M.nestedScroll(DisableNestedScrolling()),
            onSettle = { fromIndex, toIndex ->
                rulesState.apply {
                    add(toIndex, removeAt(fromIndex))
                }
                rules = rulesState
            },
            onMove = {},
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) { index, rule, isDragging ->
            key(rule.hashCode()) {
                // Swipe <----
                LeftDeleteSwipeWrapper(
                    left = SwipeInfo(
                        onSwipe = {
                            rulesState -= rule
                            rules = rulesState
                        }
                    )
                ) {
                    val editTrigger = retain { mutableStateOf(false) }
                    ReplyRuleEditDialog(editTrigger, rule) { updatedRule ->
                        rulesState[index] = updatedRule
                        rules = rulesState
                    }

                    RingtoneRuleCard(
                        rule = rule,
                        modifier = M
                            .clickable {
                                editTrigger.value = true
                            }
                            .draggableHandle() // make it reorderable
                    )
                }
            }
        }
    }
}

@Serializable
@SerialName("QuickTile")
class QuickTile(
    val tileIndex : Int = 0,
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf()
    }
    override fun isActivated(): Boolean {
        return state().value
    }
    private fun state() : MutableState<Boolean> {
        // Currently only 1 tile, always return this
        return when (tileIndex) {
//            0 -> G.dynamicTileEnabled
            else -> G.dynamicTile0Enabled
        }
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val active = isActivated()
        if (active) {
            aCtx.logger?.warn(ctx.getString(R.string.quick_tile_is_active))
            aCtx.isInMemory = true
        }
        return active
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.quick_tile)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(6) {
            // Green dot
            if (isActivated()) {
                GreenDot()
            }
            RowVCenterSpaced(4) {
                if (showIcon) {
                    SizedBox(18) { Icon() }
                }
                if (tileIndex > 0) {
                    GreyLabel("[$tileIndex]")
                }
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_quick_tile)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_tile_custom)
    }

    @Composable
    override fun Options() {
        NoOptionNeeded()
    }
}


@Serializable
@SerialName("CallScreened")
class CallScreened(
    var enabled: Boolean = true,
    var numberFilter: String = ".*",
    var forAllowed: Boolean = false,
    var forBlocked: Boolean = true,
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context) = listOf(
        PermissionWrapper(Permission.callScreening)
    )

    override fun isActivated(): Boolean {
        return enabled && Permission.callScreening.isGranted
    }

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (!isActivated())
            return false

        val rawNumber = aCtx.rawNumber!!

        // Check number filter
        val matchesFilter = numberFilter.toRegex().matches(rawNumber)
        if (!matchesFilter) {
            aCtx.logger?.debug(
                ctx.getString(R.string.number_not_match_filter)
                    .format(rawNumber, numberFilter)
            )
            return false
        }

        aCtx.logger?.debug("${label(ctx)} triggered: $rawNumber")

        val r = aCtx.checkResult ?: return false

        if (r.shouldBlock()) { // blocked
            if (forBlocked) return true
        } else { // allowed
            if (forAllowed) return true
        }

        return false
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.call_screened)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(6) {
            // Green dot
            if (isActivated()) {
                GreenDot()
            }
            RowVCenterSpaced(4) {
                if (showIcon) {
                    SizedBox(18) { Icon() }
                }
                if (forAllowed) {
                    GreyLabel(Str(R.string.allowed))
                }
                if (forBlocked) {
                    GreyLabel(Str(R.string.blocked))
                }
                if (!forAllowed && !forBlocked) {
                    GreyLabel(Str(R.string.none))
                }
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_call_screened)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_filter)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        // Number Filter
        val dummyFlags = remember { mutableIntStateOf(Def.FLAG_REGEX_RAW_NUMBER) }
        RegexInputBox(
            regexStr = numberFilter,
            label = { Text(Str(R.string.number_filter)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_filter) },
            helpTooltipId = R.string.help_call_screening_number_filter,
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

        // Allow / Block
        LabeledRow(
            labelId = R.string.result,
        ) {
            var forAllowedState by remember { mutableStateOf(forAllowed) }
            var forBlockedState by remember { mutableStateOf(forBlocked) }

            RowVCenterSpaced(10) {
                CheckBox(
                    checked = forAllowedState,
                    label = { GreyLabel(Str(R.string.allowed)) },
                    onCheckChange = {
                        forAllowedState = it
                        forAllowed = it
                    }
                )
                CheckBox(
                    checked = forBlockedState,
                    label = { GreyLabel(Str(R.string.blocked)) },
                    onCheckChange = {
                        forBlockedState = it
                        forBlocked = it
                    }
                )
            }
        }
    }
}