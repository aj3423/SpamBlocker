package spam.blocker.ui.setting.regex

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.util.TimeSchedule
import spam.blocker.util.hasFlag
import spam.blocker.util.setFlag


class RegexState(

    val id: Long,


    // Regex pattern
    val pattern: MutableState<String>,
    val patternFlags: MutableIntState,
    val patternModeType: MutableIntState,
    val patternError: MutableState<Boolean>,

    // For particular number
    val forParticular: MutableState<Boolean>,
    val patternExtra: MutableState<String>,
    val patternExtraFlags: MutableIntState,
    val patternExtraModeType: MutableIntState,
    val patternExtraError: MutableState<Boolean>,

    // Description
    val description: MutableState<String>,

    // Priority
    val priority: MutableIntState,
    val priorityError: MutableState<Boolean>,

    // Apply to Call/SMS
    val applyToCall: MutableState<Boolean>,
    val applyToSms: MutableState<Boolean>,

    // Apply to Notification Title/Body
    val applyToNotifTitle: MutableState<Boolean>,
    val applyToNotifBody: MutableState<Boolean>,

    // Apply to Number/Content
    val applyToNumber: MutableState<Boolean>,
    val applyToContent: MutableState<Boolean>,

    // Apply to Passed/Blocked
    val applyToPassed: MutableState<Boolean>,
    val applyToBlocked: MutableState<Boolean>,

    // Auto copy OPT codes
    val autoCopy: MutableState<Boolean>,

    // Whitelist or Blacklist
    // selected index, 0 == whitelist, 1 == blacklist
    val whiteOrBlack: MutableIntState,

    // Block Type
    val blockType: MutableIntState,
    val blockTypeConfig: MutableState<String>,

    // NotificationType
    val channelId: MutableState<String>,

    // SIM card
    val simSlot: MutableState<Int?>,

    // Schedule
    val schEnabled: MutableState<Boolean>,
    val schWeekdays: SnapshotStateList<Int>,
    val schSHour: MutableIntState,
    val schSMin: MutableIntState,
    val schEHour: MutableIntState,
    val schEMin: MutableIntState,
) {
    fun toRule() : RegexRule {
        val flags = 0
            .setFlag(Def.FLAG_FOR_CALL, applyToCall.value)
            .setFlag(Def.FLAG_FOR_SMS, applyToSms.value)
            .setFlag(Def.FLAG_FOR_NOTIF_TITLE, applyToNotifTitle.value)
            .setFlag(Def.FLAG_FOR_NOTIF_BODY, applyToNotifBody.value)
            .setFlag(Def.FLAG_FOR_NUMBER, applyToNumber.value)
            .setFlag(Def.FLAG_FOR_CONTENT, applyToContent.value)
            .setFlag(Def.FLAG_FOR_PASSED, applyToPassed.value)
            .setFlag(Def.FLAG_FOR_BLOCKED, applyToBlocked.value)
            .setFlag(Def.FLAG_AUTO_COPY, autoCopy.value)

        val schedule = TimeSchedule().apply {
            enabled = schEnabled.value
            startHour = schSHour.intValue
            startMin = schSMin.intValue
            endHour = schEHour.intValue
            endMin = schEMin.intValue
            weekdays = schWeekdays
        }.serializeToStr()

        return RegexRule(
            id = id,
            pattern = pattern.value,
            patternFlags = patternFlags.intValue,
            patternModeType = patternModeType.intValue,
            patternExtra = if (forParticular.value) patternExtra.value else "",
            patternExtraFlags = if (forParticular.value) patternExtraFlags.intValue else Def.DefaultRegexFlags,
            patternExtraModeType = patternExtraModeType.intValue,
            description = description.value,
            priority = priority.intValue,
            isBlacklist = whiteOrBlack.intValue == 1,
            flags = flags,
            channel = channelId.value,
            schedule = schedule,
            blockType = blockType.intValue,
            blockTypeConfig = blockTypeConfig.value,
            simSlot = simSlot.value,
        )
    }
}

fun RegexRule.toState(): RegexState {
    val initRule = this

    val sch = TimeSchedule.parseFromStr(initRule.schedule)

    return RegexState(
        // id
        id = initRule.id,


        // Regex pattern
        pattern = mutableStateOf(initRule.pattern),
        patternFlags = mutableIntStateOf(initRule.patternFlags),
        patternModeType = mutableIntStateOf(initRule.patternModeType),
        patternError = mutableStateOf(false),

        // For particular number
        forParticular = mutableStateOf(initRule.patternExtra != ""),
        patternExtra = mutableStateOf(initRule.patternExtra),
        patternExtraFlags = mutableIntStateOf(initRule.patternExtraFlags),
        patternExtraModeType = mutableIntStateOf(initRule.patternExtraModeType),
        patternExtraError = mutableStateOf(false),

        // Description
        description = mutableStateOf(initRule.description),

        // Priority
        priority = mutableIntStateOf(initRule.priority),
        priorityError = mutableStateOf(false),

        // Apply to Call/SMS
        applyToCall = mutableStateOf(initRule.isForCall()),
        applyToSms = mutableStateOf(initRule.isForSms()),

        // Apply to Notification Title/Body
        applyToNotifTitle = mutableStateOf(initRule.isForNotifTitle()),
        applyToNotifBody = mutableStateOf(initRule.isForNotifBody()),

        // Apply to Number/Content
        applyToNumber = mutableStateOf(initRule.flags.hasFlag(Def.FLAG_FOR_NUMBER)),
        applyToContent = mutableStateOf(initRule.flags.hasFlag(Def.FLAG_FOR_CONTENT)),

        // Apply to Passed/Blocked
        applyToPassed = mutableStateOf(initRule.flags.hasFlag(Def.FLAG_FOR_PASSED)),
        applyToBlocked = mutableStateOf(initRule.flags.hasFlag(Def.FLAG_FOR_BLOCKED)),

        // Auto copy OPT codes
        autoCopy = mutableStateOf(initRule.flags.hasFlag(Def.FLAG_AUTO_COPY)),

        // Whitelist or Blacklist
        // selected index, 0 == whitelist, 1 == blacklist
        whiteOrBlack = mutableIntStateOf(if (initRule.isWhitelist()) 0 else 1),

        // Block Type
        blockType = mutableIntStateOf(initRule.blockType),
        blockTypeConfig = mutableStateOf(initRule.blockTypeConfig),

        // NotificationType
        channelId = mutableStateOf(initRule.channel),

        // SIM card
        simSlot = mutableStateOf(initRule.simSlot),

        // Schedule
        schEnabled = mutableStateOf(sch.enabled),
        schWeekdays = mutableStateListOf(*sch.weekdays.toTypedArray()),
        schSHour = mutableIntStateOf(sch.startHour),
        schSMin = mutableIntStateOf(sch.startMin),
        schEHour = mutableIntStateOf(sch.endHour),
        schEMin = mutableIntStateOf(sch.endMin)
    )
}
