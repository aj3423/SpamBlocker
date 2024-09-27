package spam.blocker.ui.setting.regex

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import spam.blocker.R
import spam.blocker.db.RegexRule
import spam.blocker.db.newRegexRule
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.rememberMutableStateListOf
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.SettingRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.CheckBox
import spam.blocker.ui.widgets.CustomItem
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.ShowAnimated
import spam.blocker.ui.widgets.Spinner
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.ui.widgets.TimeRangePicker
import spam.blocker.ui.widgets.WeekdayPicker
import spam.blocker.util.Clipboard
import spam.blocker.util.Lambda1
import spam.blocker.util.Permission
import spam.blocker.util.PermissionChain
import spam.blocker.util.Schedule
import spam.blocker.util.Util
import spam.blocker.util.hasFlag
import spam.blocker.util.setFlag

@Composable
fun LeadingDropdownIcon(regexFlags: MutableState<Int>) {
    val ctx = LocalContext.current
    val C = LocalPalette.current

    val dropdownOffset = remember {
        mutableStateOf(Offset.Zero)
    }

    val dropdownItems = remember(Unit) {
        val items: MutableList<IMenuItem> = mutableListOf(
            CustomItem {
                RowVCenter(
                    modifier = M.padding(horizontal = 10.dp)
                ) {
                    GreyLabel(Str(R.string.switch_mode))
                    BalloonQuestionMark(
                        helpTooltipId = R.string.help_number_mode,
                        dropdownOffset.value.round()
                    )
                }
            },
            DividerItem(thickness = 1),
        )
        // Number Mode
        items += LabelItem(
            label = ctx.getString(R.string.phone_number),
            icon = { GreyIcon(R.drawable.ic_number_sign, modifier = M.size(16.dp)) }
        ) {
            regexFlags.value = regexFlags.value.setFlag(Def.FLAG_REGEX_FOR_CONTACT_GROUP, false)
        }
        // Contact Group Mode
        items += LabelItem(
            label = ctx.getString(R.string.contact_group),
            icon = { GreyIcon(R.drawable.ic_account_circle, modifier = M.size(16.dp)) }
        ) {
            regexFlags.value = regexFlags.value.setFlag(Def.FLAG_REGEX_FOR_CONTACT_GROUP, true)
        }
        items
    }

    DropdownWrapper(
        items = dropdownItems,
        modifier = M.onGloballyPositioned {
            dropdownOffset.value = it.positionOnScreen()
        }
    ) { expanded ->
        val forContactGroup = regexFlags.value.hasFlag(Def.FLAG_REGEX_FOR_CONTACT_GROUP)

        Box {
            ResIcon(
                iconId = if (forContactGroup) {
                    R.drawable.ic_account_circle
                } else {
                    R.drawable.ic_number_sign
                },
                modifier = M.size(18.dp)
            )
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.spinner_arrow),
                contentDescription = "",
                tint = C.textGrey,
                modifier = Modifier
                    .size(6.dp)
                    .align(Alignment.BottomEnd)
                    .offset((4).dp, (4).dp)
                    .clickable { expanded.value = true }
            )
        }
    }
}

@Composable
fun RuleEditDialog(
    trigger: MutableState<Boolean>,
    forType: Int,
    onSave: Lambda1<RegexRule>,
    initRule: RegexRule = RegexRule(), // use default when "Add" new rule
) {
    if (!trigger.value) {
        return
    }

    val C = LocalPalette.current
    val ctx = LocalContext.current

    // id
    val id by rememberSaveable { mutableLongStateOf(initRule.id) }

    // Regex pattern
    var pattern by rememberSaveable { mutableStateOf(initRule.pattern) }
    val patternFlags = rememberSaveable { mutableIntStateOf(initRule.patternFlags) }
    var patternError by rememberSaveable { mutableStateOf(false) }

    // For particular number
    var forParticular by rememberSaveable { mutableStateOf(initRule.patternExtra != "") }
    var patternExtra by rememberSaveable { mutableStateOf(initRule.patternExtra) }
    val patternExtraFlags = rememberSaveable { mutableIntStateOf(initRule.patternExtraFlags) }
    var patternExtraError by rememberSaveable { mutableStateOf(false) }

    // Description
    var description by rememberSaveable { mutableStateOf(initRule.description) }

    // Priority
    var priority by rememberSaveable { mutableIntStateOf(initRule.priority) }
    var priorityError by rememberSaveable { mutableStateOf(false) }

    // Apply to Call/SMS
    var applyToCall by rememberSaveable { mutableStateOf(initRule.isForCall()) }
    var applyToSms by rememberSaveable { mutableStateOf(initRule.isForSms()) }

    // Apply to Number/Content
    var applyToNumber by rememberSaveable { mutableStateOf(initRule.flags.hasFlag(Def.FLAG_FOR_NUMBER)) }
    var applyToContent by rememberSaveable { mutableStateOf(initRule.flags.hasFlag(Def.FLAG_FOR_CONTENT)) }

    // Apply to Passed/Blocked
    var applyToPassed by rememberSaveable { mutableStateOf(initRule.flags.hasFlag(Def.FLAG_FOR_PASSED)) }
    var applyToBlocked by rememberSaveable { mutableStateOf(initRule.flags.hasFlag(Def.FLAG_FOR_BLOCKED)) }

    // Whitelist or Blacklist
    // selected index
    var applyToWorB by rememberSaveable { mutableIntStateOf(if (initRule.isWhitelist()) 0 else 1) }

    // Block Type
    var blockType by rememberSaveable { mutableIntStateOf(initRule.blockType) }

    // NotificationType
    var notifyType by rememberSaveable { mutableIntStateOf(initRule.importance) }

    // Schedule
    val sch = remember { Schedule.parseFromStr(initRule.schedule) }
    var schEnabled by rememberSaveable { mutableStateOf(sch.enabled) }
    val schWeekdays = rememberMutableStateListOf(*sch.weekdays.toTypedArray())
    var schSHour by rememberSaveable { mutableIntStateOf(sch.startHour) }
    var schSMin by rememberSaveable { mutableIntStateOf(sch.startMin) }
    var schEHour by rememberSaveable { mutableIntStateOf(sch.endHour) }
    var schEMin by rememberSaveable { mutableIntStateOf(sch.endMin) }

    // if any error, disable the Save button
    val anyError = rememberSaveable(patternError, patternExtraError, priorityError) {
        patternError || patternExtraError || priorityError
    }

    PopupDialog(
        trigger = trigger,
        popupSize = PopupSize(percentage = 0.9f, minWidth = 340, maxWidth = 600),
        buttons = {
            StrokeButton(
                label = Str(R.string.save),
                color = if (anyError) C.disabled else Teal200,
                enabled = !anyError,
                onClick = {
                    trigger.value = false

                    var flags = 0
                    flags = flags.setFlag(Def.FLAG_FOR_CALL, applyToCall)
                    flags = flags.setFlag(Def.FLAG_FOR_SMS, applyToSms)
                    flags = flags.setFlag(Def.FLAG_FOR_NUMBER, applyToNumber)
                    flags = flags.setFlag(Def.FLAG_FOR_CONTENT, applyToContent)
                    flags = flags.setFlag(Def.FLAG_FOR_PASSED, applyToPassed)
                    flags = flags.setFlag(Def.FLAG_FOR_BLOCKED, applyToBlocked)

                    val schedule = Schedule().apply {
                        enabled = schEnabled
                        startHour = schSHour
                        startMin = schSMin
                        endHour = schEHour
                        endMin = schEMin
                        weekdays = schWeekdays
                    }.serializeToStr()

                    onSave(
                        newRegexRule(
                            id,
                            pattern,
                            patternExtra,
                            patternFlags.intValue,
                            patternExtraFlags.intValue,
                            description,
                            priority,
                            applyToWorB == 1,
                            flags,
                            notifyType,
                            schedule,
                            blockType,
                        )
                    )
                }
            )
        },
        content = {
            Column {
                // Pattern
                RegexInputBox(
                    label = {
                        Text(
                            Str(
                                when (forType) {
                                    Def.ForNumber -> R.string.number_pattern
                                    Def.ForSms -> R.string.sms_content_pattern
                                    else -> R.string.quick_copy
                                }
                            )
                        )
                    },
                    regexStr = pattern,
                    regexFlags = patternFlags,
                    onRegexStrChange = { newVal, hasErr ->
                        patternError = hasErr
                        pattern = newVal
                    },
                    leadingIcon = if (forType == Def.ForNumber) {
                        { LeadingDropdownIcon(patternFlags) }
                    } else {
                        { ResIcon(iconId = R.drawable.ic_open_msg, modifier = M.size(18.dp)) }
                    }
                )

                // For particular number
                if (forType == Def.ForSms) {
                    LabeledRow(
                        labelId = R.string.for_particular_number,
                        helpTooltipId = R.string.help_for_particular_number,
                    ) {
                        SwitchBox(checked = forParticular) { forParticular = it }
                    }

                    ShowAnimated(visible = forParticular) {
                        RegexInputBox(
                            label = {
                                Text(
                                    Str(R.string.phone_number),
                                    color = Color.Unspecified
                                )
                            },
                            regexStr = patternExtra,
                            regexFlags = patternExtraFlags,
                            onRegexStrChange = { newValue, hasErr ->
                                patternExtraError = hasErr
                                patternExtra = newValue
                            },
                            leadingIcon = { LeadingDropdownIcon(patternExtraFlags) }
                        )
                    }
                }

                // Description
                StrInputBox(
                    text = description,
                    label = { Text(Str(R.string.description), color = Color.Unspecified) },
                    onValueChange = { description = it },
                    leadingIconId = R.drawable.ic_question,
                    maxLines = 1,
                )

                // Priority
                NumberInputBox(
                    intValue = priority,

                    label = { Text(Str(R.string.priority), color = Color.Unspecified) },
                    onValueChange = { newVal, hasErr ->
                        priorityError = hasErr
                        if (newVal != null)
                            priority = newVal
                    },
                    leadingIconId = R.drawable.ic_priority,
                )

                // For Call/SMS
                LabeledRow(
                    labelId = R.string.apply_to,
                    helpTooltipId = R.string.help_apply_to_call_sms,
                ) {
                    RowVCenterSpaced(10) {
                        if (forType != Def.ForSms) {
                            CheckBox(
                                checked = applyToCall,
                                label = { GreyLabel(Str(R.string.call)) },
                                onCheckChange = { applyToCall = it },
                            )
                        }
                        CheckBox(
                            checked = applyToSms,
                            label = { GreyLabel(Str(R.string.sms)) },
                            onCheckChange = { applyToSms = it })
                    }
                }

                // For Number/Content
                if (forType == Def.ForQuickCopy) {
                    LabeledRow(
                        labelId = R.string.apply_to,
                        helpTooltipId = R.string.help_apply_to_number_and_message,
                    ) {
                        RowVCenterSpaced(10) {
                            CheckBox(
                                checked = applyToNumber,
                                label = { GreyLabel(Str(R.string.phone_number_abbrev)) },
                                onCheckChange = { applyToNumber = it })
                            CheckBox(
                                checked = applyToContent,
                                label = { GreyLabel(Str(R.string.content)) },
                                onCheckChange = { applyToContent = it })
                        }
                    }
                }

                // For Passed/Blocked
                if (forType == Def.ForQuickCopy) {
                    LabeledRow(
                        labelId = R.string.apply_to,
                        helpTooltipId = R.string.help_apply_to_passed_and_blocked,
                    ) {
                        RowVCenterSpaced(10) {
                            CheckBox(
                                checked = applyToPassed,
                                label = { Text(Str(R.string.passed), color = C.pass) },
                                onCheckChange = { applyToPassed = it },
                            )
                            CheckBox(
                                checked = applyToBlocked,
                                label = { Text(Str(R.string.blocked), color = C.block) },
                                onCheckChange = { applyToBlocked = it })
                        }
                    }
                }

                // for Whitelist / Blacklist
                if (forType != Def.ForQuickCopy) {
                    LabeledRow(
                        labelId = R.string.type,
                    ) {
                        val items = listOf(
                            RadioItem(Str(R.string.whitelist), C.pass),
                            RadioItem(Str(R.string.blacklist), C.block),
                        )
                        RadioGroup(items = items, selectedIndex = applyToWorB) {
                            applyToWorB = it
                        }
                    }
                }

                // Block Type
                ShowAnimated(visible = forType == Def.ForNumber && applyToWorB == 1) {
                    val permChain = remember {
                        PermissionChain(
                            ctx,
                            listOf(
                                Permission(Manifest.permission.READ_PHONE_STATE),
                                Permission(Manifest.permission.READ_CALL_LOG),
                                Permission(Manifest.permission.ANSWER_PHONE_CALLS)
                            )
                        )
                    }
                    permChain.Compose()

                    LabeledRow(labelId = R.string.block_type) {
                        val icons = remember {
                            listOf<@Composable () -> Unit>(
                                // list.map{} doesn't support returning @Composable...
                                {
                                    GreyIcon(
                                        iconId = R.drawable.ic_call_blocked,
                                        modifier = M.size(16.dp)
                                    )
                                },
                                {
                                    GreyIcon(
                                        iconId = R.drawable.ic_call_miss,
                                        modifier = M.size(16.dp)
                                    )
                                },
                                { GreyIcon(iconId = R.drawable.ic_hang, modifier = M.size(16.dp)) },
                            )
                        }
                        val blockTypeLabels = remember {
                            ctx.resources.getStringArray(R.array.block_type_list)
                                .mapIndexed { index, label ->
                                    LabelItem(
                                        label = label,
                                        icon = icons[index],
                                        onClick = {
                                            when (index) {
                                                0, 1 -> { // Reject, Silence
                                                    blockType = index
                                                }

                                                2 -> { // Answer-HangUp
                                                    permChain.ask { granted ->
                                                        if (granted) {
                                                            blockType = index
                                                        }
                                                    }
                                                }
                                            }
                                        })
                                }
                        }
                        Spinner(blockTypeLabels, blockType)
                    }
                }

                // Notification Type
                ShowAnimated(visible = forType != Def.ForQuickCopy && applyToWorB == 1) {
                    LabeledRow(
                        labelId = R.string.notification,
                        helpTooltipId = R.string.help_importance,
                    ) {
                        val icons = remember {
                            // list.map{} doesn't support returning @Composable...
                            listOf<(@Composable () -> Unit)?>(
                                null,
                                { GreyIcon(R.drawable.ic_shade, modifier = M.size(16.dp)) },
                                {
                                    GreyIcon(
                                        R.drawable.ic_statusbar_shade,
                                        modifier = M.size(16.dp)
                                    )
                                },
                                {
                                    RowVCenterSpaced(2) {
                                        GreyIcon(
                                            R.drawable.ic_bell_ringing,
                                            modifier = M.size(16.dp)
                                        )
                                        GreyIcon(
                                            R.drawable.ic_statusbar_shade,
                                            modifier = M.size(16.dp)
                                        )
                                    }
                                },
                                {
                                    RowVCenterSpaced(2) {
                                        GreyIcon(
                                            R.drawable.ic_bell_ringing,
                                            modifier = M.size(16.dp)
                                        )
                                        GreyIcon(
                                            R.drawable.ic_statusbar_shade,
                                            modifier = M.size(16.dp)
                                        )
                                        GreyIcon(R.drawable.ic_heads_up, modifier = M.size(16.dp))
                                    }
                                },
                            )
                        }

                        val notifyTypeLabels = remember {
                            ctx.resources.getStringArray(R.array.importance_list)
                                .mapIndexed { index, label ->
                                    LabelItem(
                                        label = label,
                                        icon = icons[index],
                                        onClick = {
                                            notifyType = index
                                        }
                                    )
                                }
                        }
                        Spinner(notifyTypeLabels, notifyType)
                    }
                }

                // Schedule
                if (forType != Def.ForQuickCopy) {
                    val popupTrigger = rememberSaveable { mutableStateOf(false) }

                    if (popupTrigger.value) {
                        TimeRangePicker(
                            popupTrigger, schSHour, schSMin, schEHour, schEMin,
                        ) { sH, sM, eH, eM ->
                            schSHour = sH
                            schSMin = sM
                            schEHour = eH
                            schEMin = eM
                        }
                    }
                    LabeledRow(labelId = R.string.schedule) {
                        RowVCenterSpaced(8) {
                            if (schEnabled) {
                                GreyButton(
                                    label = Util.timeRangeStr(
                                        ctx, schSHour, schSMin, schEHour, schEMin
                                    ),
                                ) {
                                    popupTrigger.value = true
                                }
                            }

                            SwitchBox(checked = schEnabled) { schEnabled = it }
                        }
                    }
                    if (schEnabled) {
                        SettingRow {
                            WeekdayPicker(selectedDays = schWeekdays)
                        }
                    }
                }
            }
        }
    )
}