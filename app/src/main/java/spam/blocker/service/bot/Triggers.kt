package spam.blocker.service.bot

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.service.checker.ByRegexRule
import spam.blocker.service.checker.ICheckResult
import spam.blocker.ui.M
import spam.blocker.ui.SizedBox
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.DimGreyText
import spam.blocker.ui.widgets.GreenDot
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.RingtonePicker
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.SummaryLabel
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.A
import spam.blocker.util.Contacts
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.PermissiveJson
import spam.blocker.util.RingtoneUtil
import spam.blocker.util.Util
import spam.blocker.util.formatAnnotated
import spam.blocker.util.hasFlag
import spam.blocker.util.regexMatches
import spam.blocker.util.regexMatchesNumber
import spam.blocker.util.spf
import java.util.UUID

// Continue or terminate the workflow according to ongoing calendar event.
@Serializable
@SerialName("Manual")
class Manual() : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf()
    }
    override fun isActivated(): Boolean {
        return false
    }
    @Composable
    override fun TriggerType(modifier: Modifier) {
        GreyLabel(Str(R.string.manual))
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
                Icon()
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


// Continue or terminate the workflow according to ongoing calendar event.
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
    @Composable
    override fun TriggerType(modifier: Modifier) {
        GreyLabel(Str(R.string.schedule))
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        return true // do nothing
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.schedule)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        val ctx = LocalContext.current
        RowVCenterSpaced(6) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            RowVCenterSpaced(4) {
                if (showIcon) {
                    SizedBox(18) {
                        Icon()
                    }
                }
                SummaryLabel(schedule.summary(ctx))
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
                LabeledRow(R.string.time) {
                    GreyButton(label = scheduleState.value!!.summary(ctx)) {
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
    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(2, modifier = modifier) {
            GreyIcon18(R.drawable.ic_incoming)
            GreyIcon18(R.drawable.ic_calendar)
            GreyLabel(
                text = eventTitle,
                modifier = M.padding(start = 4.dp)
            )
        }
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
                        eventTitle.A(Teal200)
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

    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(2, modifier = modifier) {
            GreyIcon18(R.drawable.ic_sms)
            GreyLabel(
                text = "$content <- $number",
                modifier = M.padding(start = 4.dp)
            )
        }
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
                    "$content <- $number".A(Teal200)
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
            placeholder = { DimGreyText(".*") },
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
            placeholder = { DimGreyText(".*") },
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
    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(2, modifier = modifier) {
            GreyIcon18(R.drawable.ic_incoming)
            GreyLabel(
                text = number,
                modifier = M.padding(start = 4.dp)
            )
        }
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
                    number.A(Teal200)
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
            placeholder = { DimGreyText(".*") },
            showNumberFlags = true,
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
    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(6, modifier = modifier) {
            GreyIcon18(R.drawable.ic_multi_call)

            RowVCenterSpaced(2) {
                if (includingAnswered) {
                    GreyIcon16(R.drawable.ic_call)
                }
                if (includingBlocked) {
                    GreyIcon16(R.drawable.ic_call_blocked)
                }
            }
            GreyLabel(Str(R.string.seconds_template).format(durationSec))
        }
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
    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(6, modifier = modifier) {
            GreyIcon18(R.drawable.ic_multi_sms)

            RowVCenterSpaced(4) {
                GreyLabel("$countLimit/$durationSec")
                GreyLabel(targetRuleDesc)
            }
        }
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

        val forContact = rule.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT)
        val forContactGroup = rule.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT_GROUP)

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
class Features(
    var regex: String?,
    // var repeatedCall: Boolean?,
    // ...
)

@Serializable
@SerialName("Ringtone")
class Ringtone(
    var enabled: Boolean = true, // always enabled, can't be disabled
    var mute: Boolean = false,
    var ringtoneUri: String? = null,
    var bindTo: String = "{ \"regex\": \"\" }",
    var delaySec: Int = 5
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return if (mute) // mute doesn't write to system settings
            listOf()
        else
            listOf(PermissionWrapper(Permission.writeSettings))
    }
    override fun isActivated(): Boolean {
        return enabled &&
                if (mute) true else Permission.writeSettings.isGranted
    }
    private fun labelBindTo(): String {
        try {
            val j = PermissiveJson.decodeFromString<Features>(bindTo)
            if (j.regex != null) {
                return j.regex!!
            }
//        if (j.repeatedCall != null) {
//            return ctx.getString("repeated call")
//        }
        } catch (_: Exception) {
        }
        return ""
    }
    @Composable
    override fun TriggerType(modifier: Modifier) {
        val ctx = LocalContext.current

        RowVCenterSpaced(6, modifier = modifier) {
            GreyIcon18(R.drawable.ic_music)
            GreyLabel(labelBindTo())

            if (mute) {
                GreyIcon18(R.drawable.ic_bell_mute)
            } else {
                val uri = ringtoneUri?.toUri() ?: RingtoneUtil.getCurrent(ctx)
                GreyLabel(RingtoneUtil.getName(ctx, uri))
            }
        }
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (!isActivated())
            return false

        val isTesting = aCtx.lastOutput == null

        if (isTesting) {
            aCtx.logger?.error(ctx.getString(R.string.call_to_test_ringtone))
            return false
        }

        var j: Features
        try {
            j = PermissiveJson.decodeFromString<Features>(bindTo)
        } catch (_: Exception) {
            return false
        }

        val r = aCtx.lastOutput as? ICheckResult

        var anyFeatureMatches = false

        if (j.regex != null && r is ByRegexRule) {
            // If the call was allowed by this regex
            anyFeatureMatches = j.regex!!.regexMatches(r.rule!!.description, Def.DefaultRegexFlags)
        }
//        if (j.repeatedCall != null) { // change ringtone for other features
//            anyFeatureMatches = ...
//        }

        // No feature matches, nothing to do.
        if (!anyFeatureMatches)
            return false

        // Apply the ringtone if any feature matches
        if (!mute) {
            RingtoneUtil.setDefaultUri(ctx, (ringtoneUri ?: "").toUri())

            // Reset the ringtone after N seconds, the ringing should've already started.
            CoroutineScope(IO).launch {
                delay(delaySec.toLong() * 1000)
                resetRingtone(ctx)
            }
        }

        // this will be used in CallScreeningService, ugly workaround..
        aCtx.shouldMute = mute

        return true
    }
    private fun resetRingtone(ctx: Context) {
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
        val ctx = LocalContext.current

        RowVCenterSpaced(6) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            RowVCenterSpaced(4) {
                if (showIcon) {
                    SizedBox(18) { Icon() }
                }

                GreyLabel(labelBindTo())

                if (mute) {
                    GreyIcon18(R.drawable.ic_bell_mute)
                } else {
                    val uri = ringtoneUri?.toUri() ?: RingtoneUtil.getCurrent(ctx)
                    GreyLabel(RingtoneUtil.getName(ctx, uri))
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

    @Composable
    override fun Options() {
        val ctx = LocalContext.current

        var bindToState by remember { mutableStateOf(bindTo) }
        var error by remember(bindToState) {
            val label = labelBindTo()

            mutableStateOf<String?>(
                if (label.isEmpty())
                    ctx.getString(R.string.invalid_config)
                else
                    null
            )
        }
        // Target features
        StrInputBox(
            text = bindToState,
            label = { Text(Str(R.string.set_to)) },
            leadingIconId = R.drawable.ic_link,
            placeholder = { DimGreyText("{\"regex\": \"rule_desc\"}") },
            helpTooltip = Str(R.string.help_set_ringtone_to),
            supportingTextStr = error,
            onValueChange = {
                bindTo = it
                bindToState = it
            }
        )

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
    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(6, modifier = modifier) {
            // Green dot
            GreyIcon18(R.drawable.ic_tile_custom)
            if (tileIndex > 0) {
                GreyLabel("[$tileIndex]")
            }
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
