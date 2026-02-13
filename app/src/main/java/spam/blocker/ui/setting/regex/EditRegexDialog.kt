package spam.blocker.ui.setting.regex

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Notification.CHANNEL_HIGH
import spam.blocker.db.Notification.CHANNEL_LOW
import spam.blocker.db.RegexRule
import spam.blocker.db.newRegexRule
import spam.blocker.def.Def
import spam.blocker.def.Def.ANDROID_12
import spam.blocker.def.Def.DEFAULT_HANG_UP_DELAY
import spam.blocker.def.Def.FLAG_REGEX_FOR_CNAP
import spam.blocker.def.Def.FLAG_REGEX_FOR_CONTACT
import spam.blocker.def.Def.FLAG_REGEX_FOR_CONTACT_GROUP
import spam.blocker.def.Def.FLAG_REGEX_FOR_GEO_LOCATION
import spam.blocker.def.Def.ForNumber
import spam.blocker.def.Def.ForSms
import spam.blocker.def.Def.MAP_REGEX_FLAGS
import spam.blocker.ui.LaunchedEffectOnlyOnChange
import spam.blocker.ui.M
import spam.blocker.ui.rememberSaveableMutableStateListOf
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.SettingRow
import spam.blocker.ui.setting.quick.ChannelPicker
import spam.blocker.ui.setting.quick.ConfigHangUp
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.CheckBox
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SimPicker
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.ui.widgets.TimeRangePicker
import spam.blocker.ui.widgets.WeekdayPicker1
import spam.blocker.util.Lambda1
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.TimeSchedule
import spam.blocker.util.TimeUtils.timeRangeStr
import spam.blocker.util.addFlag
import spam.blocker.util.hasFlag
import spam.blocker.util.removeFlag
import spam.blocker.util.setFlag

fun Int.clearAllRegexFlags(): Int {
    return this.removeFlag(FLAG_REGEX_FOR_CONTACT_GROUP)
        .removeFlag(FLAG_REGEX_FOR_CONTACT)
        .removeFlag(FLAG_REGEX_FOR_CNAP)
        .removeFlag(FLAG_REGEX_FOR_GEO_LOCATION)
}

@Composable
fun RegexModeIcon(mode: Int) {
    Text(
        MAP_REGEX_FLAGS[mode]!!,
        color = Color.Magenta,
        modifier = M.widthIn(16.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
fun RegexLeadingDropdownIcon(
    forType: Int,
    regexFlags: MutableIntState,
) {
    val ctx = LocalContext.current
    val C = LocalPalette.current

    val dropdownItems = remember(Unit) {
        val tooltipIds = mutableListOf(
            R.string.help_regex_mode_phone_number,
            R.string.help_regex_mode_contact,
            R.string.help_regex_mode_contact_group,
            R.string.help_regex_mode_geo_location,
        )
        if (forType == Def.ForNumber) {
            tooltipIds += R.string.help_regex_mode_caller_name
        }
        val items: MutableList<IMenuItem> = mutableListOf(
            LabelItem(
                label = ctx.getString(R.string.switch_mode),
                tooltip = tooltipIds.joinToString("<br>") { ctx.getString(it) }
            ),
            DividerItem(thickness = 1),
        )
        val labelIds = mutableListOf(
            R.string.phone_number,
            R.string.contact,
            R.string.contact_group,
            R.string.geo_location,
        )
        if (forType == Def.ForNumber) {
            labelIds += R.string.caller_name
        }
        val iconIds = mutableListOf<@Composable ()-> Unit>(
            { GreyIcon16(R.drawable.ic_number_sign) },
            { RegexModeIcon(FLAG_REGEX_FOR_CONTACT) },
            { RegexModeIcon(FLAG_REGEX_FOR_CONTACT_GROUP) },
            { RegexModeIcon(FLAG_REGEX_FOR_GEO_LOCATION) },
        )
        if (forType == Def.ForNumber) {
            iconIds += { RegexModeIcon(FLAG_REGEX_FOR_CNAP)}
        }
        items += labelIds.mapIndexed { index, labelId ->
            LabelItem(
                label = ctx.getString(labelId),
                leadingIcon = iconIds[index],
                dismissOnClick = index != 1 && index != 2
            ) { menuExpanded ->
                when (index) {
                    0 -> { // Number Mode
                        regexFlags.intValue = regexFlags.intValue.clearAllRegexFlags()
                    }

                    1, 2 -> { // Contact Mode, Contact Group Mode
                        G.permissionChain.ask(
                            ctx,
                            listOf(PermissionWrapper(Permission.contacts))
                        ) { granted ->
                            if (granted) {
                                when (index) {
                                    1 -> { // Contact Mode
                                        regexFlags.intValue = regexFlags.intValue
                                            .clearAllRegexFlags()
                                            .addFlag(FLAG_REGEX_FOR_CONTACT)
                                    }

                                    2 -> { // Contact Group Mode
                                        regexFlags.intValue = regexFlags.intValue
                                            .clearAllRegexFlags()
                                            .addFlag(FLAG_REGEX_FOR_CONTACT_GROUP)
                                    }
                                }
                                menuExpanded.value = false
                            }
                        }
                    }
                    3 -> { // Geo Location
                        regexFlags.intValue = regexFlags.intValue
                            .clearAllRegexFlags()
                            .addFlag(FLAG_REGEX_FOR_GEO_LOCATION)
                    }
                    4 -> { // CNAP
                        regexFlags.intValue = regexFlags.intValue
                            .clearAllRegexFlags()
                            .addFlag(FLAG_REGEX_FOR_CNAP)
                    }
                }
            }
        }
        items
    }

    DropdownWrapper(
        items = dropdownItems,
    ) { expanded ->
        val forContactGroup = regexFlags.intValue.hasFlag(FLAG_REGEX_FOR_CONTACT_GROUP)
        val forContact = regexFlags.intValue.hasFlag(FLAG_REGEX_FOR_CONTACT)
        val forCNAP = regexFlags.intValue.hasFlag(FLAG_REGEX_FOR_CNAP)
        val forGeoLocation= regexFlags.intValue.hasFlag(FLAG_REGEX_FOR_GEO_LOCATION)

        Box {
            if (forContact) {
                RegexModeIcon(FLAG_REGEX_FOR_CONTACT)
            } else if (forContactGroup) {
                RegexModeIcon(FLAG_REGEX_FOR_CONTACT_GROUP)
            } else if (forCNAP) {
                RegexModeIcon(FLAG_REGEX_FOR_CNAP)
            } else if (forGeoLocation) {
                RegexModeIcon(FLAG_REGEX_FOR_GEO_LOCATION)
            } else {
                ResIcon(
                    iconId = R.drawable.ic_number_sign,
                    modifier = M.size(16.dp)
                )
            }

            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_dropdown_footer),
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
fun RegexFieldLabel(
    forType: Int,
    flags: Int,
) {
    Text(
        Str(
            when (forType) {
                Def.ForNumber -> {
                    if (flags.hasFlag(FLAG_REGEX_FOR_CONTACT_GROUP))
                        R.string.contact_group
                    else if (flags.hasFlag(FLAG_REGEX_FOR_CONTACT))
                        R.string.contact
                    else if (flags.hasFlag(FLAG_REGEX_FOR_CNAP))
                        R.string.caller_name
                    else if (flags.hasFlag(FLAG_REGEX_FOR_GEO_LOCATION))
                        R.string.geo_location
                    else
                        R.string.phone_number
                }

                Def.ForSms -> R.string.sms_content_pattern
                else -> R.string.quick_copy
            }
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditRegexDialog(
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
    val forCNAP by rememberSaveable(patternFlags.intValue) {
        mutableStateOf(
            forType == Def.ForNumber &&
                    patternFlags.intValue.hasFlag(FLAG_REGEX_FOR_CNAP)
        )
    }

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

    // Auto copy OPT codes
    var autoCopy by rememberSaveable { mutableStateOf(initRule.flags.hasFlag(Def.FLAG_AUTO_COPY)) }

    // Whitelist or Blacklist
    // selected index, 0 == whitelist, 1 == blacklist
    var whiteOrBlack by rememberSaveable { mutableIntStateOf(if (initRule.isWhitelist()) 0 else 1) }

    // Block Type
    var blockType by rememberSaveable { mutableIntStateOf(initRule.blockType) }
    var blockTypeConfig by rememberSaveable { mutableStateOf(initRule.blockTypeConfig) }

    // NotificationType
    var channelId by rememberSaveable { mutableStateOf(initRule.channel) }

    // SIM card
    val simSlot = rememberSaveable { mutableStateOf(initRule.simSlot) }

    // Schedule
    val sch = remember { TimeSchedule.parseFromStr(initRule.schedule) }
    var schEnabled by rememberSaveable { mutableStateOf(sch.enabled) }
    val schWeekdays = rememberSaveableMutableStateListOf(*sch.weekdays.toTypedArray())
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
                    flags = flags.setFlag(Def.FLAG_AUTO_COPY, autoCopy)

                    val schedule = TimeSchedule().apply {
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
                            whiteOrBlack == 1,
                            flags,
                            channelId,
                            schedule,
                            blockType,
                            blockTypeConfig,
                            simSlot.value,
                        )
                    )

                    // fire event to update the UI
                    Events.regexRuleUpdated.fire()
                }
            )
        },
        content = {
            Column {
                // Pattern
                RegexInputBox(
                    label = {
                        RegexFieldLabel(forType = forType, flags = patternFlags.intValue)
                    },
                    regexStr = pattern,
                    regexFlags = patternFlags,
                    onRegexStrChange = { newVal, hasErr ->
                        patternError = hasErr
                        pattern = newVal
                    },
                    onFlagsChange = {
                        patternFlags.intValue = it
                    },
                    showNumberFlags = forType == Def.ForNumber,
                    testable = true,
                    leadingIcon = if (forType == Def.ForNumber) {
                        { RegexLeadingDropdownIcon(Def.ForNumber, patternFlags) }
                    } else {
                        { ResIcon(iconId = R.drawable.ic_open_msg, modifier = M.size(18.dp)) }
                    }
                )

                // For particular number
                if (forType == Def.ForSms) {
                    LabeledRow(
                        labelId = R.string.for_particular_number,
                        helpTooltip = Str(R.string.help_for_particular_number),
                    ) {
                        SwitchBox(checked = forParticular) { forParticular = it }
                    }

                    AnimatedVisibleV(visible = forParticular) {
                        RegexInputBox(
                            label = { RegexFieldLabel(forType = Def.ForNumber, flags = patternExtraFlags.intValue) },
                            regexStr = patternExtra,
                            regexFlags = patternExtraFlags,
                            showNumberFlags = true,
                            onRegexStrChange = { newValue, hasErr ->
                                patternExtraError = hasErr
                                patternExtra = newValue
                            },
                            onFlagsChange = {
                                patternExtraFlags.intValue = it
                            },
                            leadingIcon = { RegexLeadingDropdownIcon(Def.ForSms, patternExtraFlags) }
                        )
                    }
                }

                // Description
                StrInputBox(
                    text = description,
                    label = {
                        Text(
                            Str(R.string.description) + " " + Str(R.string.optional),
                            color = Color.Unspecified
                        )
                    },
                    onValueChange = { description = it },
                    leadingIconId = R.drawable.ic_note,
                    maxLines = 10,
                )

                // Priority
                // Auto change the priority to 1 or 0 when user select `whitelist/blacklist`
                // Don't change if it's other values.
                LaunchedEffectOnlyOnChange(whiteOrBlack) {
                    if (whiteOrBlack == 0 && priority == 0) {
                        priority = 1
                    }
                    if (whiteOrBlack == 1 && priority == 1) {
                        priority = 0
                    }
                }
                PriorityBox(priority) { newVal, hasErr ->
                    priorityError = hasErr
                    if (newVal != null)
                        priority = newVal
                }

                // For Call/SMS
                LabeledRow(
                    labelId = R.string.apply_to,
                    helpTooltip = Str(R.string.help_apply_to_call_sms),
                ) {
                    FlowRowSpaced(10) {
                        if (forType != Def.ForSms) {
                            CheckBox(
                                checked = applyToCall,
                                label = { GreyLabel(Str(R.string.call)) },
                                onCheckChange = { applyToCall = it },
                            )
                        }
                        AnimatedVisibility(!forCNAP) {
                            CheckBox(checked = applyToSms,
                                label = { GreyLabel(Str(R.string.sms)) },
                                onCheckChange = { applyToSms = it })
                        }
                    }
                }

                // For Number/Content
                if (forType == Def.ForQuickCopy) {
                    LabeledRow(
                        labelId = R.string.apply_to,
                        helpTooltip = Str(R.string.help_apply_to_number_and_message),
                    ) {
                        FlowRowSpaced(10) {
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
                        helpTooltip = Str(R.string.help_apply_to_passed_and_blocked),
                    ) {
                        FlowRowSpaced(10) {
                            CheckBox(
                                checked = applyToPassed,
                                label = { Text(Str(R.string.allowed), color = C.pass) },
                                onCheckChange = { applyToPassed = it },
                            )
                            CheckBox(
                                checked = applyToBlocked,
                                label = { Text(Str(R.string.blocked), color = C.block) },
                                onCheckChange = { applyToBlocked = it })
                        }
                    }
                }

                // For Auto Copy
                if (forType == Def.ForQuickCopy) {
                    LabeledRow(
                        labelId = R.string.auto_copy,
                        helpTooltip = Str(R.string.help_auto_copy),
                    ) {
                        SwitchBox(checked = autoCopy) { autoCopy = it }
                    }
                }

                // for Whitelist / Blacklist
                if (forType != Def.ForQuickCopy) {
                    LabeledRow(
                        labelId = R.string.type,
                    ) {
                        val items = listOf(
                            RadioItem(Str(R.string.allow), C.pass),
                            RadioItem(Str(R.string.block), C.block),
                        )
                        RadioGroup(items = items, selectedIndex = whiteOrBlack) {
                            whiteOrBlack = it
                        }
                    }
                }

                // Block Type
                AnimatedVisibleV(
                    visible = forType == Def.ForNumber && whiteOrBlack == 1 && applyToCall
                ) {
                    LabeledRow(labelId = R.string.block_type) {
                        val icons = remember {
                            listOf<@Composable () -> Unit>(
                                // list.map{} doesn't support returning @Composable...
                                { GreyIcon20(iconId = R.drawable.ic_call_blocked) },
                                { GreyIcon20(iconId = R.drawable.ic_call_miss) },
                                { GreyIcon20(iconId = R.drawable.ic_hang) },
                            )
                        }
                        val blockTypeLabels = remember {
                            ctx.resources.getStringArray(R.array.block_type_list)
                                .mapIndexed { index, label ->
                                    LabelItem(
                                        label = label,
                                        leadingIcon = icons[index],
                                        onClick = {
                                            when (index) {
                                                0, 1 -> { // Reject, Silence
                                                    blockType = index
                                                }

                                                2 -> { // Answer-HangUp
                                                    G.permissionChain.ask(
                                                        ctx,
                                                        listOf(
                                                            PermissionWrapper(Permission.phoneState),
                                                            PermissionWrapper(Permission.callLog),
                                                            PermissionWrapper(Permission.answerCalls)
                                                        )
                                                    ) { granted ->
                                                        if (granted) {
                                                            blockType = index
                                                        }
                                                    }
                                                }
                                            }
                                            true
                                        }
                                    )
                                }
                        }
                        RowVCenterSpaced(4) {
                            if (blockType == Def.BLOCK_TYPE_ANSWER_AND_HANGUP) {
                                val delay = remember {
                                    mutableIntStateOf(blockTypeConfig.toIntOrNull() ?: DEFAULT_HANG_UP_DELAY)
                                }
                                LaunchedEffect(delay.intValue) {
                                    blockTypeConfig = delay.intValue.toString()
                                }

                                val popupTrigger = rememberSaveable { mutableStateOf(false) }
                                ConfigHangUp(popupTrigger, delay)

                                StrokeButton(
                                    label = "${delay.intValue} ${Str(R.string.seconds_short)}",
                                    color = C.textGrey,
                                ) {
                                    popupTrigger.value = true
                                }
                            }
                            ComboBox(blockTypeLabels, blockType)
                        }
                    }
                }

                // Notification Type
                AnimatedVisibleV(
                    visible = when (forType) {
                        ForNumber -> whiteOrBlack != 0 || (!forCNAP && applyToSms)
                        ForSms   -> true
                        else     -> false
                    }
                ) {
                    // Auto change the current channelId to "Allow" or "Block" when user select `whitelist/blacklist`
                    // Don't change if it's a custom channel.
                    LaunchedEffectOnlyOnChange(whiteOrBlack) {
                        if (whiteOrBlack == 0 && channelId == CHANNEL_LOW) {
                            channelId = CHANNEL_HIGH
                        }
                        if (whiteOrBlack == 1 && channelId == CHANNEL_HIGH) {
                            channelId = CHANNEL_LOW
                        }
                    }

                    LabeledRow(
                        labelId = R.string.notification,
//                        helpTooltip = Str(R.string.help_notification),
                    ) {
                        ChannelPicker(
                            channelId
                        ) { index, ch ->
                            channelId = ch.channelId
                        }
                    }
                }

                // SIM
                if (forType != Def.ForQuickCopy) {
                    LabeledRow(
                        labelId = R.string.sim_card,
                        color = if(Build.VERSION.SDK_INT < ANDROID_12) C.disabled else null,
                        helpTooltip = Str(R.string.help_sim_card)
                    ) {
                        SimPicker(simSlot)
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
                        FlowRowSpaced(8) {
                            if (schEnabled) {
                                GreyButton(
                                    label = timeRangeStr(
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
                            WeekdayPicker1(selectedDays = schWeekdays)
                        }
                    }
                }
            }
        }
    )
}