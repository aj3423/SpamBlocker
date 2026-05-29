package spam.blocker.ui.setting.regex

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Notification.CHANNEL_HIGH
import spam.blocker.db.Notification.CHANNEL_LOW
import spam.blocker.def.Def
import spam.blocker.def.Def.ANDROID_12
import spam.blocker.def.Def.DEFAULT_HANG_UP_DELAY
import spam.blocker.ui.LaunchedEffectOnlyOnChange
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.SettingRow
import spam.blocker.ui.setting.quick.ChannelPicker
import spam.blocker.ui.setting.quick.ConfigHangUp
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.CheckBox
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.Placeholder
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.ResIcon16
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SimPicker
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.ui.widgets.TimeRangePicker
import spam.blocker.ui.widgets.WeekdayPicker1
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.TimeUtils.timeRangeStr
import spam.blocker.util.spf

// To add a new regex mode:
//  1. implement the class
//  2. add to `enum Mode`, `regexModeByType`, `allNumberModes`
//  3. add to Def and CheckResult.kt
//  4. add to Checker.Content.particularMatches
//  5. add to Checker.numberRuleToChecker
//  6. add to HistoryIndicator
object RegexMode {

    object ModeType {
        const val PhoneNumber = 0
        const val SmsContent = 1
        const val QuickCopy = 2

        const val ContactName = 3
        const val ContactGroup = 4
        const val ContactPrefix = 5
        const val Geolocation = 6
        const val Carrier = 7
        const val CallerName = 8
        const val DatabasePrefix = 9
    }

    fun regexModeByType(modeType: Int): Base {
        return when(modeType) {
            ModeType.PhoneNumber -> PhoneNumber()
            ModeType.SmsContent -> SmsContent()
            ModeType.QuickCopy -> QuickCopy()

            ModeType.ContactName -> ContactName()
            ModeType.ContactGroup -> ContactGroup()
            ModeType.ContactPrefix -> ContactPrefix()
            ModeType.Geolocation -> Geolocation()
            ModeType.Carrier -> Carrier()
            ModeType.CallerName -> CallerName()
            ModeType.DatabasePrefix -> DatabasePrefix()

            else -> throw IllegalArgumentException("Unexpected ModeType key: $modeType")
        }
    }

    // The dropdown list on UI
    fun allNumberModes(): List<NumberMode> {
        return listOf(
            PhoneNumber(),
            ContactName(),
            ContactGroup(),
            ContactPrefix(),
            DatabasePrefix(),
            Geolocation(),
            Carrier(),
            CallerName(),
        )
    }

    fun regexModeInlineMap() = buildMap {
        allNumberModes().forEach { mode ->
            put(
                mode.textPlaceholder,
                InlineTextContent(
                    placeholder = androidx.compose.ui.text.Placeholder(
                        (16 + 4).sp, // add 4.sp as the icon's paddingEnd
                        16.sp,
                        PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    mode.Icon()
                }
            )
        }
    }


    fun Int.isForNumberRegexMode(): Boolean {
        return this != ModeType.SmsContent && this != ModeType.QuickCopy
    }
    fun Int.isForSmsContentRegexMode(): Boolean {
        return this == ModeType.SmsContent
    }
    fun Int.isForQuickCopyRegexMode(): Boolean {
        return this == ModeType.QuickCopy
    }


    abstract class Base {
        abstract val modeType: Int

        // Mode label / icon
        abstract val labelId: Int
        @Composable
        abstract fun Icon(
            color: Color = if(modeType == ModeType.PhoneNumber)
                G.palette.textGrey else G.palette.infoBlue
        )

        @Composable
        open fun Compose(state: RegexState) {
            DoCompose(state)
        }

        @Composable
        fun DoCompose(
            state: RegexState,

            showAnsweredSpamDuration: Boolean = false, // for block mode "Answered Spam Prefix"
            showParticular: Boolean = false, // Particular number (for sms mode)

            modeSwitchable: Boolean = true,
        ) {
            val ctx = LocalContext.current
            val C = G.palette

            // Pattern field
            RegexInputBox(
                label = { Text(Str(labelId)) },
                regexStr = state.pattern.value,
                regexFlags = state.patternFlags,
                maxTextLength = spf.RegexOptions(ctx).textboxLimit,
                onRegexStrChange = { newVal, hasErr ->
                    state.pattern.value = newVal
                    state.patternError.value = hasErr
                },
                onFlagsChange = {
                    state.patternFlags.intValue = it
                },
                enableNumberFlags = true,
                testable = true,
                leadingIcon = {
                    if (modeSwitchable) {
                        RegexLeadingDropdownIcon(this, state.patternModeType)
                    } else {
                        Icon()
                    }
                },
            )


            // Answered Spam Prefix - Duration
//            if (showAnsweredSpamDuration) {}

            // Particular
            if (showParticular) {
                LabeledRow(
                    labelId = R.string.for_particular_number,
                    helpTooltip = Str(R.string.help_for_particular_number),
                ) {
                    SwitchBox(state.forParticular.value) {
                        state.forParticular.value = it
                    }
                }
                AnimatedVisibleV(state.forParticular.value) {
                    RegexInputBox(
                        // This must be SMS mode
                        label = { Text(Str(regexModeByType(state.patternModeType.intValue).labelId)) },
                        regexStr = state.patternExtra.value,
                        regexFlags = state.patternExtraFlags,
                        maxTextLength = spf.RegexOptions(ctx).textboxLimit,
                        onRegexStrChange = { newVal, hasErr ->
                            state.patternExtra.value = newVal
                            state.patternExtraError.value = hasErr
                        },
                        onFlagsChange = {
                            state.patternExtraFlags.intValue = it
                        },
                        enableNumberFlags = true,
                        testable = true,
                        leadingIcon = { RegexLeadingDropdownIcon(this, state.patternExtraModeType) },
                    )
                }
            }

            // Description
            StrInputBox(
                text = state.description.value,
                label = {
                    Text(
                        Str(R.string.description),
                        color = Color.Unspecified
                    )
                },
                placeholder = { Placeholder(Str(R.string.optional)) },
                onValueChange = { state.description.value = it },
                leadingIconId = R.drawable.ic_note,
                maxLines = 10,
            )

            // Priority
            // Auto change the priority to 1 or 0 when user select `whitelist/blacklist`
            // Don't change if it's other values.
            LaunchedEffectOnlyOnChange(state.whiteOrBlack.intValue) {
                if (state.whiteOrBlack.intValue == 0 && state.priority.intValue == 0) {
                    state.priority.intValue = 1
                }
                if (state.whiteOrBlack.intValue == 1 && state.priority.intValue == 1) {
                    state.priority.intValue = 0
                }
            }
            PriorityBox(state.priority.intValue) { newVal, hasErr ->
                state.priorityError.value = hasErr
                if (newVal != null)
                    state.priority.intValue = newVal
            }

            val forCNAP = modeType.isForNumberRegexMode() && state.patternModeType.intValue == ModeType.CallerName

            // For Call/SMS
            LabeledRow(
                labelId = R.string.apply_to,
                helpTooltip = Str(R.string.help_apply_to_call_sms),
            ) {
                FlowRowSpaced(10) {
                    if (modeType != ModeType.SmsContent) { // not editing SMS Content Rule
                        CheckBox(
                            checked = state.applyToCall.value,
                            label = { GreyLabel(Str(R.string.call)) },
                            onCheckChange = { state.applyToCall.value = it },
                        )
                    }
                    AnimatedVisibility(!forCNAP) {
                        CheckBox(checked = state.applyToSms.value,
                            label = { GreyLabel(Str(R.string.sms)) },
                            onCheckChange = { state.applyToSms.value = it })
                    }
                }
            }


            // For Number/Content
            if (modeType.isForQuickCopyRegexMode()) {
                LabeledRow(
                    labelId = R.string.apply_to,
                    helpTooltip = Str(R.string.help_apply_to_number_and_message),
                ) {
                    FlowRowSpaced(10) {
                        CheckBox(
                            checked = state.applyToNumber.value,
                            label = { GreyLabel(Str(R.string.phone_number_abbrev)) },
                            onCheckChange = { state.applyToNumber.value = it })
                        CheckBox(
                            checked = state.applyToContent.value,
                            label = { GreyLabel(Str(R.string.content)) },
                            onCheckChange = { state.applyToContent.value = it })
                    }
                }
            }


            // For Passed/Blocked
            if (modeType.isForQuickCopyRegexMode()) {
                LabeledRow(
                    labelId = R.string.apply_to,
                    helpTooltip = Str(R.string.help_apply_to_passed_and_blocked),
                ) {
                    FlowRowSpaced(10) {
                        CheckBox(
                            checked = state.applyToPassed.value,
                            label = { Text(Str(R.string.allowed), color = C.success) },
                            onCheckChange = { state.applyToPassed.value = it },
                        )
                        CheckBox(
                            checked = state.applyToBlocked.value,
                            label = { Text(Str(R.string.blocked), color = C.error) },
                            onCheckChange = { state.applyToBlocked.value = it }
                        )
                    }
                }
            }

            // For Auto Copy
            if (modeType.isForQuickCopyRegexMode()) {
                LabeledRow(
                    labelId = R.string.auto_copy,
                    helpTooltip = Str(R.string.help_auto_copy),
                ) {
                    SwitchBox(checked = state.autoCopy.value) { state.autoCopy.value = it }
                }
            }

            // Allow / Block
            if (!modeType.isForQuickCopyRegexMode()) {
                LabeledRow(
                    labelId = R.string.type,
                ) {
                    val items = listOf(
                        RadioItem(Str(R.string.allow), C.success),
                        RadioItem(Str(R.string.block), C.error),
                    )
                    RadioGroup(items = items, state.whiteOrBlack.intValue) {
                        state.whiteOrBlack.intValue = it
                    }
                }
            }

            // Block Type
            AnimatedVisibleV(
                visible = modeType.isForNumberRegexMode() && state.whiteOrBlack.intValue == 1 && state.applyToCall.value
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
                                        val perms = when(index) {
                                            // Answer-HangUp
                                            2 -> listOf(
                                                PermissionWrapper(Permission.phoneState),
                                                PermissionWrapper(Permission.callLog),
                                                PermissionWrapper(Permission.answerCalls)
                                            )

                                            // Reject/Silence, no permission needed
                                            else -> listOf()
                                        }
                                        G.permissionChain.ask(ctx, perms) { granted ->
                                            if (granted) {
                                                state.blockType.intValue = index
                                            }
                                        }
                                    }
                                )
                            }
                    }
                    RowVCenterSpaced(4) {
                        if (state.blockType.intValue == Def.BLOCK_TYPE_ANSWER_AND_HANGUP) {
                            val delay = retain {
                                mutableIntStateOf(state.blockTypeConfig.value.toIntOrNull() ?: DEFAULT_HANG_UP_DELAY)
                            }
                            LaunchedEffect(delay.intValue) {
                                state.blockTypeConfig.value = delay.intValue.toString()
                            }

                            val popupTrigger = retain { mutableStateOf(false) }
                            ConfigHangUp(popupTrigger, delay)

                            StrokeButton(
                                label = "${delay.intValue} ${Str(R.string.seconds_short)}",
                                color = C.textGrey,
                            ) {
                                popupTrigger.value = true
                            }
                        }
                        ComboBox(blockTypeLabels, state.blockType.intValue)
                    }
                }
            }

            // Notification Type
            AnimatedVisibleV(
                visible = when (modeType) {
                    ModeType.SmsContent -> true
                    ModeType.QuickCopy     -> false
                    else -> state.whiteOrBlack.intValue != 0 || (!forCNAP && state.applyToSms.value)
                }
            ) {
                // Auto change the current channelId to "Allow" or "Block" when user select `whitelist/blacklist`
                // Do not change if it's a custom channel.
                LaunchedEffectOnlyOnChange(state.whiteOrBlack.intValue) {
                    if (state.whiteOrBlack.intValue == 0 && state.channelId.value == CHANNEL_LOW) {
                        state.channelId.value = CHANNEL_HIGH
                    }
                    if (state.whiteOrBlack.intValue == 1 && state.channelId.value == CHANNEL_HIGH) {
                        state.channelId.value = CHANNEL_LOW
                    }
                }

                LabeledRow(
                    labelId = R.string.notification,
//                        helpTooltip = Str(R.string.help_notification),
                ) {
                    ChannelPicker(state.channelId.value) { index, ch ->
                        state.channelId.value = ch.channelId
                    }
                }
            }

            // SIM
            if (!modeType.isForQuickCopyRegexMode()) {
                LabeledRow(
                    labelId = R.string.sim_card,
                    color = if(Build.VERSION.SDK_INT < ANDROID_12) C.disabled else null,
                    helpTooltip = Str(R.string.help_sim_card)
                ) {
                    SimPicker(state.simSlot)
                }
            }


            // Schedule
            if (!modeType.isForQuickCopyRegexMode()) {
                val popupTrigger = retain { mutableStateOf(false) }

                if (popupTrigger.value) {
                    TimeRangePicker(
                        popupTrigger, state.schSHour.intValue, state.schSMin.intValue, state.schEHour.intValue, state.schEMin.intValue,
                    ) { sH, sM, eH, eM ->
                        state.schSHour.intValue = sH
                        state.schSMin.intValue = sM
                        state.schEHour.intValue = eH
                        state.schEMin.intValue = eM
                    }
                }
                LabeledRow(labelId = R.string.schedule) {
                    FlowRowSpaced(8) {
                        if (state.schEnabled.value) {
                            GreyButton(
                                label = timeRangeStr(
                                    ctx, state.schSHour.intValue, state.schSMin.intValue, state.schEHour.intValue, state.schEMin.intValue
                                ),
                            ) {
                                popupTrigger.value = true
                            }
                        }

                        SwitchBox(state.schEnabled.value) { state.schEnabled.value = it }
                    }
                }
                if (state.schEnabled.value) {
                    SettingRow {
                        WeekdayPicker1(selectedDays = state.schWeekdays)
                    }
                }
            }
        }
    }

    // All regex modes in the Number Rules' dropdown menu are `NumberMode`
    abstract class NumberMode: Base() {
        abstract val helpTooltipId: Int
        abstract val textPlaceholder: String // a placeholder for rendering the mode icon in Text()
        open val requiredPermissions: List<PermissionWrapper> = listOf()
    }

    class PhoneNumber : NumberMode() {
        override val modeType = ModeType.PhoneNumber
        override val textPlaceholder = ""
        override val labelId = R.string.phone_number
        @Composable
        override fun Icon(color: Color) {
            ResIcon16(R.drawable.ic_number_sign, color = color)
        }
        override val helpTooltipId = R.string.help_regex_mode_phone_number
    }

    abstract class NeedContactPermission : NumberMode() {
        override val requiredPermissions = listOf(
            PermissionWrapper(Permission.contacts)
        )
    }

    class ContactName : NeedContactPermission() {
        override val modeType = ModeType.ContactName
        override val textPlaceholder = "[contact_name]"
        override val labelId = R.string.contact_name
        @Composable
        override fun Icon(color: Color) {
            ResIcon16(R.drawable.ic_contact_square, color = color)
        }
        override val helpTooltipId = R.string.help_regex_mode_contact
    }

    class ContactGroup : NeedContactPermission() {
        override val modeType = ModeType.ContactGroup
        override val textPlaceholder = "[contact_group]"
        override val labelId = R.string.contact_group
        @Composable
        override fun Icon(color: Color) {
            ResIcon16(R.drawable.ic_contact_group, color = color)
        }
        override val helpTooltipId = R.string.help_regex_mode_contact_group
    }

    class ContactPrefix : NeedContactPermission() {
        override val modeType = ModeType.ContactPrefix
        override val textPlaceholder = "[contact_prefix]"
        override val labelId = R.string.contact_prefix
        @Composable
        override fun Icon(color: Color) {
            ResIcon16(R.drawable.ic_contact_eq, color = color)
        }
        override val helpTooltipId = R.string.help_regex_mode_contact_prefix
    }
    class DatabasePrefix : NumberMode() {
        override val modeType = ModeType.DatabasePrefix
        override val textPlaceholder = "[database_prefix]"
        override val labelId = R.string.database_prefix
        @Composable
        override fun Icon(color: Color) {
            ResIcon16(R.drawable.ic_db_share, color = color)
        }
        override val helpTooltipId = R.string.help_regex_mode_database_prefix
    }
    class Geolocation : NumberMode() {
        override val modeType = ModeType.Geolocation
        override val textPlaceholder = "[geoloc]"
        override val labelId = R.string.geolocation
        @Composable
        override fun Icon(color: Color) {
            ResIcon16(R.drawable.ic_location, color = color)
        }
        override val helpTooltipId = R.string.help_regex_mode_geolocation
    }
    class Carrier : NumberMode() {
        override val modeType = ModeType.Carrier
        override val textPlaceholder = "[carrier]"
        override val labelId = R.string.carrier
        @Composable
        override fun Icon(color: Color) {
            ResIcon16(R.drawable.ic_cellular_network, color = color)
        }
        override val helpTooltipId = R.string.help_regex_mode_carrier
    }
    class CallerName : NumberMode() {
        override val modeType = ModeType.CallerName
        override val textPlaceholder = "[caller_name]"
        override val labelId = R.string.caller_name
        @Composable
        override fun Icon(color: Color) {
            ResIcon16(R.drawable.ic_id_card, color = color)
        }
        override val helpTooltipId = R.string.help_regex_mode_caller_name
    }
    class SmsContent : Base() {
        override val modeType = ModeType.SmsContent
        override val labelId = R.string.sms_content
        @Composable
        override fun Icon(color: Color) {
            ResIcon16(R.drawable.ic_open_msg)
        }

        @Composable
        override fun Compose(state: RegexState) {
            DoCompose(
                state,
                modeSwitchable = false,
                showParticular = true,
            )
        }
    }

    class QuickCopy : Base() {
        override val modeType = ModeType.QuickCopy
        override val labelId = R.string.quick_copy
        @Composable
        override fun Icon(color: Color) {
            ResIcon16(R.drawable.ic_open_msg)
        }

        @Composable
        override fun Compose(state: RegexState) {
            DoCompose(
                state,
                modeSwitchable = false,
            )
        }
    }
}

