package spam.blocker.service.checker

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.Connection
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.db.CallTable
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.PushAlertTable
import spam.blocker.db.QuickCopyRegexTable
import spam.blocker.db.RegexRule
import spam.blocker.db.SmsTable
import spam.blocker.db.SpamNumber
import spam.blocker.db.SpamTable
import spam.blocker.def.Def
import spam.blocker.service.bot.ActionContext
import spam.blocker.service.bot.CalendarEvent
import spam.blocker.service.bot.CallEvent
import spam.blocker.service.bot.CallThrottling
import spam.blocker.service.bot.InterceptCall
import spam.blocker.service.bot.InterceptSms
import spam.blocker.service.bot.QuickTile
import spam.blocker.service.bot.SmsEvent
import spam.blocker.service.bot.SmsThrottling
import spam.blocker.service.bot.executeAll
import spam.blocker.ui.darken
import spam.blocker.ui.setting.regex.RegexMode.ModeType
import spam.blocker.util.A
import spam.blocker.util.Clipboard
import spam.blocker.util.ContactInfo
import spam.blocker.util.Contacts
import spam.blocker.util.CountryCode
import spam.blocker.util.ILogger
import spam.blocker.util.Now
import spam.blocker.util.Permission
import spam.blocker.util.PhoneNumber
import spam.blocker.util.TimeSchedule
import spam.blocker.util.TimeUtils.isCurrentTimeWithinRange
import spam.blocker.util.Util
import spam.blocker.util.Util.countHistorySMSByNumber
import spam.blocker.util.Util.getAppsEvents
import spam.blocker.util.Util.getHistoryCallsByNumber
import spam.blocker.util.Util.listRunningForegroundServiceNames
import spam.blocker.util.Util.listUsedAppWithinXSecond
import spam.blocker.util.formatAnnotated
import spam.blocker.util.getSaveableOutput
import spam.blocker.util.hasFlag
import spam.blocker.util.race
import spam.blocker.util.regexMatches
import spam.blocker.util.regexMatchesNumber
import spam.blocker.util.spf

class CheckContext(
    var rawNumber: String,
    var cnap: String? = null,
    val callDetails: Call.Details? = null,
    val simSlot: Int?, // on which SIM slot is the call ringing on
    val smsContent: String? = null,
    val logger: ILogger? = null,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val checkers: List<IChecker>,
    var anythingWrong: Boolean = false,
)


interface IChecker {
    fun priority(): Int
    fun check(cCtx: CheckContext): ICheckResult?
    fun desc(): AnnotatedString

    // These functions are only for detecting priority conflicts in the current setup.
    fun listType(): Boolean? = true // true: whitelist, false: blacklist, null: unknown(e.g. API query)
    fun isConfigEnabledForCall(): Boolean
    fun isConfigEnabledForSms(): Boolean

    fun logChecking(ctx: Context, logger: ILogger?) {
        logger?.debug(
            buildAnnotatedString {
                appendInlineContent(id = "priority")
                append(ctx.getString(R.string.checking_template_new)
                    .formatAnnotated(
                        if(priority() == Int.MAX_VALUE) {
                            ctx.getString(R.string.max).A(G.palette.priority)
                        } else {
                            priority().toString().A(G.palette.priority)
                        },
                        desc()
                    ))
            }
        )
    }
}

fun RegexRule.numberRuleToChecker(
    ctx: Context,
): IChecker {
    return when (patternModeType) {
        ModeType.ContactName -> Checker.RegexContact(ctx, this)
        ModeType.ContactGroup -> Checker.ContactGroup(ctx, this)
        ModeType.ContactPrefix -> Checker.ContactPrefix(ctx, this)
        ModeType.CallerName -> Checker.CNAP(ctx, this)
        ModeType.Geolocation -> Checker.Geolocation(ctx, this)
        ModeType.Carrier -> Checker.Carrier(ctx, this)
        ModeType.DatabasePrefix -> Checker.DatabasePrefix(ctx, this)
        else -> Checker.Number(ctx, this)
    }
}

// Find priority conflicts in a List<IChecker>
fun List<IChecker>.findConflicts(): List<IChecker> {
    val grouped = this.groupBy { it.priority() }

    val conflicting = mutableListOf<IChecker>()

    for ((_, group) in grouped) {
        // Separate checkers by their listType
        val trueCheckers = group.filter { it.listType() == true }
        val falseCheckers = group.filter { it.listType() == false }
        // null ones are ignored completely

        // If both true and false exist in same priority → conflict
        if (trueCheckers.isNotEmpty() && falseCheckers.isNotEmpty()) {
            conflicting.addAll(trueCheckers)
            conflicting.addAll(falseCheckers)
        }
    }

    return conflicting
}

// Before the call/SMS is checked by the rules, run all related workflows first.
// Workflows with triggers like `Call Event`, `Calendar Event` will be executed on incoming call,
//   and `SMS Event` on SMS.
object Preprocessors { // for namespace only

    interface IPreProcessor {
        fun preprocess(cCtx: CheckContext)
    }

    // Execute a workflow
    class BotRunner(
        val ctx: Context,
        val bot: Bot,
    ) : IPreProcessor {
        override fun preprocess(cCtx: CheckContext) {
            val aCtx = ActionContext(
                logger = cCtx.logger,
                rawNumber = cCtx.rawNumber,
                smsContent = cCtx.smsContent,
                cCtx = cCtx,
                botId = bot.id,
            )
            // Run Trigger + Actions
            bot.triggerAndActions().executeAll(ctx, aCtx)
        }
    }
    // Collect all call-related workflows.
    fun callBots(ctx: Context): List<BotRunner> {
        return BotTable.listAll(ctx)
            .filter {
                val trigger = it.trigger

                // Is activated ?
                if (!trigger.isActivated())
                    return@filter false

                // Type of these classes ?
                return@filter when(trigger) {
                    is CallEvent, is CallThrottling, is QuickTile, is CalendarEvent -> true
                    else -> false
                }
            }
            .map {
                BotRunner(ctx, it)
            }
    }
    // Collect all SMS-related workflows.
    fun smsBots(ctx: Context): List<BotRunner> {
        return BotTable.listAll(ctx)
            .filter {
                val trigger = it.trigger

                // Is activated ?
                if (!trigger.isActivated())
                    return@filter false

                // one of these types ?
                return@filter when(trigger) {
                    is SmsEvent, is SmsThrottling, is CalendarEvent -> true
                    else -> false
                }
            }
            .map {
                BotRunner(ctx, it)
            }
    }
}
class Checker { // for namespace only

    class PassedByDefault(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = true
        override fun isConfigEnabledForSms() = true

        override fun desc() =
            ctx.getString(R.string.passed_by_default).A(G.palette.infoBlue)

        override fun priority(): Int {
            return Int.MIN_VALUE
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            cCtx.logger?.success(ctx.getString(R.string.passed_by_default))
            return ByDefault()
        }
    }

    // This checks if the incoming call is from an emergency number.
    // It's always enabled, there is no setting entry for this.
    private class EmergencyCall(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = true
        override fun isConfigEnabledForSms() = false

        override fun desc() =
            ctx.getString(R.string.emergency_call).A(G.palette.infoBlue)

        override fun priority(): Int {
            return Int.MAX_VALUE
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            val C = G.palette

            val logger = cCtx.logger
            val callDetails = cCtx.callDetails

            logChecking(ctx, logger)

            if (callDetails == null) {// there is no callDetail when testing
                logger?.debug(ctx.getString(R.string.skip_for_testing).A(C.disabled))
                return null
            }

            if (callDetails.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)
                || callDetails.hasProperty(Call.Details.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL)
                || Util.isEmergencyNumber(ctx, cCtx.rawNumber)
            ) {
                logger?.success(
                    ctx.getString(R.string.allowed_by)
                        .formatAnnotated(desc())
                )
                return ByEmergencyCall()
            }

            return null
        }
    }

    // This is the `Emergency` in Quick Settings, it allows all calls after calling an emergency number.
    private class EmergencySituation(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = spf.EmergencySituation(ctx).isEnabled
        override fun isConfigEnabledForSms() = false

        override fun desc() =
            ctx.getString(R.string.emergency_situation).A(G.palette.infoBlue)

        override fun priority(): Int {
            return spf.EmergencySituation(ctx).priority
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            val spf = spf.EmergencySituation(ctx)
            if (!spf.isEnabled)
                return null

            val logger = cCtx.logger
            logChecking(ctx, logger)

            // 1. check time
            val lastEccCallTime: Long = spf.timestamp
            val duration: Long = (spf.duration * 60 * 1000).toLong()
            val now = System.currentTimeMillis()
            if (lastEccCallTime + duration < now) {
                return null
            }

            logger?.success(
                ctx.getString(R.string.allowed_by)
                    .formatAnnotated(desc())
            )
            return ByEmergencySituation()
        }
    }

    private class STIR(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = spf.Stir(ctx).isEnabled
        override fun isConfigEnabledForSms() = false
        override fun listType() = false

        override fun desc() =
            ctx.getString(R.string.stir_attestation).A(G.palette.infoBlue)

        override fun priority(): Int {
            return spf.Stir(ctx).priority
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            val C = G.palette
            val logger = cCtx.logger
            val callDetails = cCtx.callDetails

            val spf = spf.Stir(ctx)
            if (!spf.isEnabled)
                return null

            logChecking(ctx, logger)

            // STIR only works >= Android 11
            if (Build.VERSION.SDK_INT < Def.ANDROID_11) {
                logger?.debug(ctx.getString(R.string.android_ver_lower_than_11))
                return null
            }

            // there is no callDetail when testing
            if (callDetails == null) {
                logger?.debug(ctx.getString(R.string.skip_for_testing).A(C.disabled))
                return null
            }

            val includeUnverified = spf.isIncludeUnverified

            val stir = callDetails.callerNumberVerificationStatus

            val unverified = stir == Connection.VERIFICATION_STATUS_NOT_VERIFIED
            val fail = stir == Connection.VERIFICATION_STATUS_FAILED

            if (fail || (includeUnverified && unverified)) {
                val ret = BySTIR(Def.RESULT_BLOCKED_BY_STIR, stir)
                logger?.error(
                    ctx.getString(R.string.blocked_by_template).formatAnnotated(desc())
                            + ret.resultReasonStr(ctx).A()
                )
                return ret
            }

            return null
        }
    }

    // The "Database" in quick settings.
    // It checks whether the number exists in the spam database.
    private class SpamDB(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = spf.SpamDB(ctx).isEnabled
        override fun isConfigEnabledForSms() = isConfigEnabledForCall()
        override fun listType() = false

        override fun desc() =
            ctx.getString(R.string.database).A(G.palette.infoBlue)

        override fun priority(): Int {
            return spf.SpamDB(ctx).priority
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!isConfigEnabledForCall())
                return null

            logChecking(ctx, logger)

            // 1. check rawNumber
            var record = SpamTable.findByNumber(ctx, rawNumber)

            // 2. if not found, clear leading `+` and 0s, then check again
            if (record == null) {
                record = SpamTable.findByNumber(ctx, Util.clearNumber(rawNumber))
            }

            if (record == null) {
                val cc = CountryCode.current(ctx)
                cc?.let {
                    // 3. prepend country code and check again
                    record = SpamTable.findByNumber(ctx, "$cc$rawNumber") // e.g. 331111111

                    // 4. try again with a leading plus: + CC rawNumber
                    if (record == null) {
                        record = SpamTable.findByNumber(ctx, "+$cc$rawNumber") // e.g. +331111111
                    }
                }
            }

            if (record != null) {
                logger?.error(
                    ctx.getString(R.string.blocked_by_template).formatAnnotated(desc())
                )
                return BySpamDb(matchedNumber = record.peer)
            }
            return null
        }
    }

    // The "Contacts" in quick settings.
    // It checks whether the phone number belongs to a contact.
    private class Contact(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = spf.Contact(ctx).isEnabled
        override fun isConfigEnabledForSms() = isConfigEnabledForCall()
        override fun priority(): Int {
            return spf.Contact(ctx).lenientPriority
        }

        override fun desc() =
            ctx.getString(R.string.contact).A(G.palette.infoBlue)

        override fun check(cCtx: CheckContext): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!isConfigEnabledForCall() || !Permission.contacts.isGranted) {
                return null
            }

            logChecking(ctx, logger)

            val contact = Contacts.findContactByRawNumber(ctx, rawNumber)
            if (contact != null) { // is contact
                logger?.success(
                    ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                            + ": ${contact.name}".A()
                )
                return ByContact(Def.RESULT_ALLOWED_BY_CONTACT, contact.name)
            }
            return null
        }
    }
    // The "Contacts" in quick settings.
    // It checks whether the phone number is unknown(not from a contact).
    private class NonContact(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall(): Boolean {
            val spf = spf.Contact(ctx)
            return spf.isEnabled && spf.isStrict
        }
        override fun isConfigEnabledForSms() = isConfigEnabledForCall()
        override fun listType() = false

        override fun desc() =
            ctx.getString(R.string.non_contact).A(G.palette.infoBlue)

        override fun priority(): Int {
            return spf.Contact(ctx).strictPriority
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!Permission.contacts.isGranted) {
                return null
            }
            if (!isConfigEnabledForCall()) {
                return null
            }

            logChecking(ctx, logger)

            val contact = Contacts.findContactByRawNumber(ctx, rawNumber)
            if (contact == null) { // not from contacts
                logger?.error(
                    ctx.getString(R.string.blocked_by_template).formatAnnotated(
                        desc()
                    )
                )
                return ByNonContact()
            }
            return null
        }
    }
    private class RepeatedCall(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = spf.RepeatedCall(ctx).isEnabled
        override fun isConfigEnabledForSms() = false

        override fun priority(): Int {
            return 10
        }

        override fun desc() =
            ctx.getString(R.string.repeated_call).A(G.palette.infoBlue)

        override fun check(cCtx: CheckContext): ICheckResult? {
            val C = G.palette

            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger
            val isTesting = cCtx.callDetails == null

            val canReadCalls = Permission.callLog.isGranted
            val canReadSMSs = Permission.readSMS.isGranted

            val spf = spf.RepeatedCall(ctx)
            if (!spf.isEnabled || (!canReadCalls && !canReadSMSs)) {
                return null
            }

            logChecking(ctx, logger)

            val times = spf.times
            val durationMinutes = spf.inXMin
            val smsEnabled = spf.isSmsEnabled

            val durationMillis = durationMinutes.toLong() * 60 * 1000

            val phoneNumber = PhoneNumber(ctx, rawNumber)

            // count Calls from real call history
            var nCalls = getHistoryCallsByNumber(
                ctx,
                phoneNumber,
                Def.DIRECTION_INCOMING,
                durationMillis
            ).size
            // When testing, there is no real call history, try local db instead
            if (isTesting) {
                val nCallsTesting = CallTable().countRepeatedRecordsWithinSeconds(
                    ctx,
                    rawNumber,
                    durationMinutes * 60
                )
                if (nCalls < nCallsTesting) // use the larger one
                    nCalls = nCallsTesting
            }

            // count SMSs from real SMS history
            var nSMSs = if(smsEnabled)
                countHistorySMSByNumber(
                    ctx,
                    phoneNumber,
                    Def.DIRECTION_INCOMING,
                    durationMillis
                )
            else
                0
            if (isTesting) { // try local db
                val nSMSsTesting = if (smsEnabled)
                    SmsTable().countRepeatedRecordsWithinSeconds(
                        ctx,
                        rawNumber,
                        durationMinutes * 60
                    )
                else
                    0
                if (nSMSs < nSMSsTesting)
                    nSMSs = nSMSsTesting
            }


            logger?.debug("${ctx.getString(R.string.call)}: $nCalls, ${ctx.getString(R.string.sms)}: $nSMSs".A(C.textGrey.darken()))

            // check
            if (nCalls + nSMSs >= times) {
                logger?.success(
                    ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                )
                return ByRepeatedCall()
            }
            return null
        }
    }

    private class Dialed(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = spf.Dialed(ctx).isEnabled
        override fun isConfigEnabledForSms() = false

        override fun priority(): Int {
            return 10
        }

        override fun desc() =
            ctx.getString(R.string.dialed_number).A(G.palette.infoBlue)

        override fun check(cCtx: CheckContext): ICheckResult? {
            val C = G.palette

            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            val spf = spf.Dialed(ctx)
            if (!spf.isEnabled)
                return null

            logChecking(ctx, logger)

            val smsEnabled = spf.isSmsEnabled
            val always = spf.always
            val durationDays = spf.days

            val durationMillis = durationDays.toLong() * 24 * 3600 * 1000

            // repeated count of call/sms, sms also counts
            val phoneNumber = PhoneNumber(ctx, rawNumber)
            val nCalls = getHistoryCallsByNumber(
                ctx,
                phoneNumber,
                Def.DIRECTION_OUTGOING,
                withinMillis = if(always)
                    null
                else
                    durationMillis
            ).size
           val nSMSs = if (smsEnabled)
                countHistorySMSByNumber(
                    ctx,
                    phoneNumber,
                    Def.DIRECTION_OUTGOING,
                    durationMillis
                )
            else
                0
            logger?.debug("${ctx.getString(R.string.call)}: $nCalls, ${ctx.getString(R.string.sms)}: $nSMSs".A(C.textGrey.darken()))

            if (nCalls + nSMSs > 0) {
                logger?.success(
                    ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                )
                return ByDialedNumber()
            }
            return null
        }
    }

    private class Answered(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = spf.Answered(ctx).isEnabled
        override fun isConfigEnabledForSms() = false

        override fun priority(): Int {
            return 10
        }

        override fun desc() =
            ctx.getString(R.string.answered_number).A(G.palette.infoBlue)

        override fun check(cCtx: CheckContext): ICheckResult? {
            val C = G.palette

            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            val spf = spf.Answered(ctx)
            if (!spf.isEnabled)
                return null

            logChecking(ctx, logger)

            val minDuration = spf.minDuration
            val durationDays = spf.days

            val durationMillis = durationDays.toLong() * 24 * 3600 * 1000

            // repeated count of call/sms, sms also counts
            val phoneNumber = PhoneNumber(ctx, rawNumber)
            val nCalls = getHistoryCallsByNumber(
                ctx,
                phoneNumber,
                Def.DIRECTION_INCOMING,
                durationMillis
            ).filter {
                it.duration >= minDuration
            }.size

            logger?.debug("${ctx.getString(R.string.call)}: $nCalls".A(C.textGrey.darken()))

            if (nCalls > 0) {
                logger?.success(
                    ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                )
                return ByAnsweredNumber()
            }
            return null
        }
    }

    private class OffTime(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = spf.OffTime(ctx).isEnabled
        override fun isConfigEnabledForSms() = isConfigEnabledForCall()

        override fun priority(): Int {
            return 10
        }

        override fun desc() =
            ctx.getString(R.string.off_time).A(G.palette.infoBlue)

        override fun check(cCtx: CheckContext): ICheckResult? {

            val logger = cCtx.logger

            val spf = spf.OffTime(ctx)
            if (!spf.isEnabled) {
                return null
            }

            logChecking(ctx, logger)

            val stHour = spf.startHour
            val stMin = spf.startMin
            val etHour = spf.endHour
            val etMin = spf.endMin

            // Entire day
            if (stHour == etHour && stMin == etMin) {
                logger?.debug(ctx.getString(R.string.entire_day))
                logger?.success(
                    ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                )
                return ByOffTime()
            }

            if (isCurrentTimeWithinRange(stHour, stMin, etHour, etMin)) {
                logger?.success(
                    ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                )
                return ByOffTime()
            }

            return null
        }
    }

    private class RecentApp(
        private val ctx: Context,
    ) : IChecker {
        private val enabledApps by lazy { spf.RecentApps(ctx).getList() }
        override fun isConfigEnabledForCall() = enabledApps.isNotEmpty()
        override fun isConfigEnabledForSms() = false

        override fun priority(): Int {
            return 10
        }

        override fun desc() =
            ctx.getString(R.string.recent_apps).A(G.palette.infoBlue)

        override fun check(cCtx: CheckContext): ICheckResult? {
            val logger = cCtx.logger

            if (enabledApps.isEmpty()) {
                return null
            }

            logChecking(ctx, logger)

            val spf = spf.RecentApps(ctx)
            val duration = spf.inXMin // in minutes

            // To avoid querying db for each app, aggregate them by duration, like:
            //  pkg.a,pkg.b@20,pkg.c
            // ->
            //  map {
            //    5  -> [pkg.a,pkg.c], // 5 is the default duration
            //    20 -> [pkg.b],
            //  }
            //  So it only queries db twice: for 5 min and 20 min.
            val aggregation = enabledApps.groupBy {
                it.duration ?: duration
            }.mapValues { (_, values) ->
                values.map { it.pkgName }
            }

            for ((duration, appList) in aggregation) {
                val usedApps = listUsedAppWithinXSecond(ctx, duration * 60)

                val intersection = appList.toList().intersect(usedApps.toSet())

                if (intersection.isNotEmpty()) {
                    logger?.success(
                        ctx.getString(R.string.allowed_by)
                            .formatAnnotated(desc()) + ": ${intersection.first()}".A()
                    )
                    return ByRecentApp(pkgName = intersection.first())
                }
            }
            return null
        }
    }

    private class MeetingMode(
        private val ctx: Context,
    ) : IChecker {
        private val appList by lazy { spf.MeetingMode(ctx).getList() }
        override fun isConfigEnabledForCall() = appList.isNotEmpty()
        override fun isConfigEnabledForSms() = appList.isNotEmpty()

        override fun listType() = false

        override fun desc() =
            ctx.getString(R.string.in_meeting).A(G.palette.infoBlue)

        override fun priority(): Int {
            val spf = spf.MeetingMode(ctx)
            return spf.priority
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            val logger = cCtx.logger

            if (appList.isEmpty())
                return null

            logChecking(ctx, logger)

            val eventsMap = getAppsEvents(ctx, appList.map { it.pkgName }.toSet())

            // Check if any app is running a foreground service
            val appInMeeting = appList.firstOrNull {
                val runningServiceNames = listRunningForegroundServiceNames(
                    appEvents = eventsMap[it.pkgName],
                )
                val exclusions = it.exclusions

                runningServiceNames.any { serviceName ->
                    !exclusions.any { serviceName.contains(it) }
                }
            }

            if (appInMeeting != null) {
                logger?.error(
                    ctx.getString(R.string.blocked_by_template)
                        .formatAnnotated(desc()) + ": $appInMeeting".A()
                )
                return ByMeetingMode(pkgName = appInMeeting.pkgName)
            }
            return null
        }
    }

    private class InstantQuery(
        private val ctx: Context,
        private val forType: Int, // for call or sms
    ) : IChecker {
        override fun isConfigEnabledForCall() = callApis.isNotEmpty()
        override fun isConfigEnabledForSms() = smsApis.isNotEmpty()
        private val allApis by lazy {
            G.apiQueryVM.table.listAll(ctx).filter { it.enabled }
        }
        private val callApis by lazy {
            allApis.filter { it.actions.firstOrNull() is InterceptCall }
        }
        private val smsApis by lazy {
            allApis.filter { it.actions.firstOrNull() is InterceptSms }
        }

        override fun listType() = null

        override fun desc() =
            ctx.getString(R.string.instant_query).A(G.palette.infoBlue)

        override fun priority(): Int {
            return spf.ApiQueryOptions(ctx).priority
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            val apis = if (forType == Def.ForNumber)
                callApis
            else
                smsApis

            if (apis.isEmpty())
                return null

            logChecking(ctx, logger)

            // The call screening time limit is 5 seconds, with a 500ms buffer for
            //  the inaccuracy of System.currentTimeMillis(), the total time limit
            //  is actually 4500ms
            val now = System.currentTimeMillis()
            val alreadyCost = now - cCtx.startTimeMillis
            val buffer = 500
            val timeLeft = 5000 - buffer - alreadyCost
            if (timeLeft <= 0) {
                logger?.warn(
                    ctx.getString(R.string.not_enough_time_left).format(
                        "$buffer", "${5000 - buffer}", "$alreadyCost"
                    )
                )
                return null
            }


            // Run all apis simultaneously, get the first non-null result, which means "determined",
            //  return that result immediately and kill other threads.
            val (winnerApi, result, timedOut) = race(
                competitors = apis,
                timeoutMillis = timeLeft,
                runner = { api ->
                    { scope ->
                        try {
                            val aCtx = ActionContext(
                                cCtx = cCtx,
                                scope = scope,
                                logger = logger,
                                rawNumber = rawNumber,
                                smsContent = cCtx.smsContent,
                            )
                            val success = api.actions.executeAll(ctx, aCtx)

                            val result = aCtx.racingResult
                            if (success && result?.determined == true) {
                                result
                            } else {
                                null
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            )

            if (result?.determined == true) { // either spam or non-spam
                winnerApi!! // If it's determined, the winnerApi must exist

                if (result.isSpam)
                    logger?.error(
                        ctx.getString(R.string.blocked_by_template)
                            .formatAnnotated(desc()) + " <${winnerApi.summary()}>".A()
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by)
                            .formatAnnotated(desc()) + " <${winnerApi.summary()}>".A()
                    )

                return ByApiQuery(
                    type = if (result.isSpam) Def.RESULT_BLOCKED_BY_API_QUERY else Def.RESULT_ALLOWED_BY_API_QUERY,
                    detail = ApiQueryResultDetail(
                        apiSummary = winnerApi.summary(),
                        apiDomain = winnerApi.domain()!!,
                        queryResult = result,
                    )
                )
            }

            if (timedOut) {
                logger?.warn(ctx.getString(R.string.api_query_timeout))
                cCtx.anythingWrong = true
            }

            return null
        }
    }

    abstract class RegexRuleChecker(
        val ctx: Context,
        var rule: RegexRule,
        val labelId: Int
    ) : IChecker {
        override fun isConfigEnabledForCall() = rule.flags.hasFlag(Def.FLAG_FOR_CALL)
        override fun isConfigEnabledForSms() = rule.flags.hasFlag(Def.FLAG_FOR_SMS)
        override fun listType() = rule.isWhitelist()
        override fun priority(): Int {
            return rule.priority
        }

        override fun desc() : AnnotatedString {
            val C = G.palette
            return ctx.getString(R.string.label_value_pair).formatAnnotated(
                ctx.getString(labelId).A(C.infoBlue),
                rule.descOrPattern().A(if (rule.isBlacklist) C.error else C.success)
            )
        }

        open fun isEnabled(cCtx: CheckContext): Boolean {
            // 0. check if the rule is enabled for call/sms
            val isForSMS = cCtx.smsContent != null
            if (isForSMS && !isConfigEnabledForSms()) // for sms
                return false

            if (!isForSMS && !isConfigEnabledForCall()) // for call
                return false

            // 1. check time schedule
            if (TimeSchedule.dissatisfyNow(rule.schedule)) {
//                cCtx.logger?.debug(ctx.getString(R.string.outside_time_schedule))
                return false
            }

            // 2. check sim slot
            if (rule.simSlot != null && cCtx.simSlot != null) { // null == doesn't care, no need to check
                if (!Permission.phoneState.isGranted) {
                    cCtx.logger?.warn(ctx.getString(R.string.missing_permission).formatAnnotated(
                        Permission.phoneState.name.A(G.palette.teal200)
                    ))
                } else if (rule.simSlot != cCtx.simSlot) {
                    return false
                }
            }

            return true
        }
    }

    // Check if a number rule matches the incoming number
    class Number(
        ctx: Context,
        rule: RegexRule,
    ) : RegexRuleChecker(ctx, rule, R.string.number_rule) {
        override fun check(cCtx: CheckContext): ICheckResult? {

            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!isEnabled(cCtx)) {
                return null
            }

            logChecking(ctx, logger)

            // 2. check regex
            if (doCheck(rawNumber)) {
                val block = rule.isBlacklist

                if (block)
                    logger?.error(
                        ctx.getString(R.string.blocked_by_template).formatAnnotated(desc())
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                    )

                return ByRegexRule(
                    type = if (block) Def.RESULT_BLOCKED_BY_NUMBER_REGEX else Def.RESULT_ALLOWED_BY_NUMBER_REGEX,
                    rule = rule,
                )
            }

            return null
        }
        fun doCheck(rawNumber: String): Boolean {
            return rule.pattern.regexMatchesNumber(rawNumber, rule.patternFlags)
        }
    }

    // Check if the regex matches the caller display name
    class CNAP(
        ctx: Context,
        rule: RegexRule,
    ) : RegexRuleChecker(ctx, rule, R.string.caller_name_rule) {
        override fun check(cCtx: CheckContext): ICheckResult? {

            val logger = cCtx.logger

            if (!isEnabled(cCtx)) {
                return null
            }

            logChecking(ctx, logger)

            // 2. check regex
            if (doCheck(cCtx.cnap)) {
                val block = rule.isBlacklist

                if (block)
                    logger?.error(
                        ctx.getString(R.string.blocked_by_template).formatAnnotated(desc())
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                    )

                return ByRegexRule(
                    type = if (block) Def.RESULT_BLOCKED_BY_CNAP_REGEX else Def.RESULT_ALLOWED_BY_CNAP_REGEX,
                    rule = rule,
                )
            }

            return null
        }
        fun doCheck(cnap: String?): Boolean {
            if (cnap == null) {
                return false
            }
            return rule.pattern.regexMatchesNumber(cnap, rule.patternFlags)
        }
    }

    // Check if the regex matches the geolocation of the incoming number
    class Geolocation(
        ctx: Context,
        rule: RegexRule,
    ) : RegexRuleChecker(ctx, rule, R.string.geolocation_rule) {
        override fun check(cCtx: CheckContext): ICheckResult? {

            if (!Permission.phoneState.isGranted) {
                return null
            }
            if (!isEnabled(cCtx)) {
                return null
            }

            val logger = cCtx.logger

            logChecking(ctx, logger)

            if (doCheck(cCtx.rawNumber)) {
                val block = rule.isBlacklist

                if (block)
                    logger?.error(
                        ctx.getString(R.string.blocked_by_template).formatAnnotated(desc())
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                    )

                return ByRegexRule(
                    type = if (block) Def.RESULT_BLOCKED_BY_GEO_LOCATION_REGEX else Def.RESULT_ALLOWED_BY_GEO_LOCATION_REGEX,
                    rule = rule,
                )
            }

            return null
        }
        fun doCheck(rawNumber: String): Boolean {
            val location = Util.numberGeoLocation(ctx, rawNumber) ?: ""

            // check regex
            return rule.pattern.regexMatches(location, rule.patternFlags)
        }
    }

    // Check if the regex matches the carrier of the incoming number
    class Carrier(
        ctx: Context,
        rule: RegexRule,
    ) : RegexRuleChecker(ctx, rule, R.string.carrier_rule) {
        override fun check(cCtx: CheckContext): ICheckResult? {

            if (!isEnabled(cCtx)) {
                return null
            }

            val logger = cCtx.logger

            logChecking(ctx, logger)

            // check regex
            if (doCheck(cCtx.rawNumber)) {
                val block = rule.isBlacklist

                if (block)
                    logger?.error(
                        ctx.getString(R.string.blocked_by_template).formatAnnotated(desc())
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                    )

                return ByRegexRule(
                    type = if (block) Def.RESULT_BLOCKED_BY_CARRIER_REGEX else Def.RESULT_ALLOWED_BY_CARRIER_REGEX,
                    rule = rule,
                )
            }

            return null
        }
        fun doCheck(rawNumber: String): Boolean {
            val carrier = Util.numberCarrier(ctx, rawNumber) ?: ""

            // check regex
            return rule.pattern.regexMatches(carrier, rule.patternFlags)
        }
    }

    // The regex flag `Contact`, it matches the contact name instead of the phone number
    class RegexContact(
        ctx: Context,
        rule: RegexRule
    ) : RegexRuleChecker(ctx, rule, R.string.contact_rule) {
        override fun check(cCtx: CheckContext): ICheckResult? {

            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!Permission.contacts.isGranted) {
                return null
            }

            if (!isEnabled(cCtx)) {
                return null
            }

            logChecking(ctx, logger)

            // 2. check regex
            if (doCheck(rawNumber)) {
                val block = rule.isBlacklist

                if (block)
                    logger?.error(
                        ctx.getString(R.string.blocked_by_template).formatAnnotated(desc())
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                    )

                return ByRegexRule(
                    type = if (block) Def.RESULT_BLOCKED_BY_CONTACT_REGEX else Def.RESULT_ALLOWED_BY_CONTACT_REGEX,
                    rule = rule,
                )
            }
            return null
        }
        fun doCheck(rawNumber: String): Boolean {
            val contactInfo = Contacts.findContactByRawNumber(ctx, rawNumber)
            return contactInfo != null &&
                    rule.matches(contactInfo.name)
        }
    }

    // The regex flag `Contact Group`, it matches the contact group name instead of the phone number
    class ContactGroup(
        ctx: Context,
        rule: RegexRule
    ) : RegexRuleChecker(ctx, rule, R.string.contact_group) {
        override fun check(cCtx: CheckContext): ICheckResult? {

            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!Permission.contacts.isGranted) {
                return null
            }

            if (!isEnabled(cCtx)) {
                return null
            }

            logChecking(ctx, logger)

            // 2. check regex
            if (doCheck(rawNumber)) { // found match
                val block = rule.isBlacklist

                if (block)
                    logger?.error(
                        ctx.getString(R.string.blocked_by_template).formatAnnotated(desc())
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                    )

                return ByRegexRule(
                    type = if (block) Def.RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX else Def.RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX,
                    rule = rule,
                )
            }
            return null
        }
        fun doCheck(rawNumber: String): Boolean {
            return Contacts.findGroupsContainNumber(ctx, rawNumber)
                .any { groupName ->
                    rule.matches(groupName)
                }
        }
    }

    // The regex flag `Contact Prefix`, fuzzy prefix match.
    class ContactPrefix(
        ctx: Context,
        rule: RegexRule
    ) : RegexRuleChecker(ctx, rule, R.string.contact_prefix) {
        override fun check(cCtx: CheckContext): ICheckResult? {

            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!Permission.contacts.isGranted) {
                return null
            }

            if (!isEnabled(cCtx)) {
                return null
            }

            logChecking(ctx, logger)

            // 2. check regex
            val contactInfo = doCheck(rawNumber)
            if (contactInfo != null) {
                val block = rule.isBlacklist

                if (block)
                    logger?.error(
                        ctx.getString(R.string.blocked_by_template)
                            .formatAnnotated(desc()) + " - ${contactInfo.name}".A()
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by)
                            .formatAnnotated(desc()) + " - ${contactInfo.name}".A()
                    )

                return ByRegexRule(
                    type = if (block) Def.RESULT_BLOCKED_BY_CONTACT_PREFIX_REGEX else Def.RESULT_ALLOWED_BY_CONTACT_PREFIX_REGEX,
                    rule = rule,
                    details = contactInfo.name
                )
            }
            return null
        }
        fun doCheck(rawNumber: String): ContactInfo? {
            // Both the incoming number and the contact number should match this regex,
            //   check the incoming number here.
            if (!rule.pattern.regexMatchesNumber(rawNumber, rule.patternFlags)) {
                return null
            }
            return Contacts.findContactByNumberPrefix(ctx, rawNumber, rule.pattern, rule.patternFlags)
        }
    }

    // The regex flag `Database Prefix`, fuzzy prefix match.
    class DatabasePrefix(
        ctx: Context,
        rule: RegexRule
    ) : RegexRuleChecker(ctx, rule, R.string.database_prefix) {
        override fun check(cCtx: CheckContext): ICheckResult? {

            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!isEnabled(cCtx)) {
                return null
            }

            logChecking(ctx, logger)

            // Find all numbers instead of checking if any exists, maybe it will support "min match count" in the future
            val similarNumbers = doCheck(rawNumber)

            if (similarNumbers.isNotEmpty()) {
                val firstNumber = similarNumbers[0]

                val block = rule.isBlacklist

                if (block)
                    logger?.error(
                        ctx.getString(R.string.blocked_by_template)
                            .formatAnnotated(desc()) + " - ${firstNumber.peer}".A()
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by)
                            .formatAnnotated(desc()) + " - ${firstNumber.peer}".A()
                    )

                return ByRegexRule(
                    type = if (block) Def.RESULT_BLOCKED_BY_DATABASE_PREFIX_REGEX else Def.RESULT_ALLOWED_BY_DATABASE_PREFIX_REGEX,
                    rule = rule,
                    details = firstNumber.peer
                )
            }
            return null
        }
        fun doCheck(rawNumber: String) : List<SpamNumber> {
            // Both the incoming number and the database number should match this regex,
            //   check the incoming number here.
            if (!rule.pattern.regexMatchesNumber(rawNumber, rule.patternFlags)) {
                return listOf()
            }

            val similarNumbers = SpamTable.findByNumberPrefix(ctx, rawNumber, rule.pattern, rule.patternFlags)
            return similarNumbers
        }
    }

    /*
        Check if text message body matches the SMS Content rule,
        the number is also checked when "for particular number" is enabled
     */
    class Content(
        ctx: Context,
        rule: RegexRule
    ) : RegexRuleChecker(ctx, rule, R.string.content_rule) {
        override fun priority(): Int {
            return rule.priority
        }

        override fun check(cCtx: CheckContext): ICheckResult? {

            val rawNumber = cCtx.rawNumber
            val smsContent = cCtx.smsContent!!
            val logger = cCtx.logger

            if (!isEnabled(cCtx)) {
                return null
            }

            logChecking(ctx, logger)

            // 1. check regex
            val contentMatches = rule.matches(smsContent)

            // 2. check for particular number
            fun particularMatches(): Boolean {
                if (rule.patternExtra == "") { // "for particular number" is not enabled
                    return true
                }

                val tempRule = rule.apply {
                    pattern = rule.patternExtra
                    patternFlags = rule.patternExtraFlags
                }
                return when(rule.patternExtraModeType) {
                    ModeType.PhoneNumber -> Number(ctx, tempRule).doCheck(rawNumber)
                    ModeType.ContactName -> RegexContact(ctx, tempRule).doCheck(rawNumber)
                    ModeType.ContactGroup -> ContactGroup(ctx, tempRule).doCheck(rawNumber)
                    ModeType.ContactPrefix -> ContactPrefix(ctx, tempRule).doCheck(rawNumber) != null
                    ModeType.DatabasePrefix -> DatabasePrefix(ctx, tempRule).doCheck(rawNumber).isNotEmpty()
                    ModeType.Geolocation -> Geolocation(ctx, tempRule).doCheck(rawNumber)
                    ModeType.Carrier -> Carrier(ctx, tempRule).doCheck(rawNumber)
                    ModeType.CallerName -> CNAP(ctx, tempRule).doCheck(cCtx.cnap)

                    else -> false
                }
            }

            if (contentMatches && particularMatches()) {
                val block = rule.isBlacklist

                if (block)
                    logger?.error(
                        ctx.getString(R.string.blocked_by_template).formatAnnotated(desc())
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                    )

                return ByRegexRule(
                    type = if (block) Def.RESULT_BLOCKED_BY_CONTENT_RULE else Def.RESULT_ALLOWED_BY_CONTENT_RULE,
                    rule = rule,
                )
            }
            return null
        }
    }

    private class PushAlert(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = PushAlertTable.listAll(ctx).any {
            it.enabled && it.isValid()
        }
        override fun isConfigEnabledForSms() = false

        override fun priority(): Int {
            return 10
        }

        override fun desc() = ctx.getString(R.string.push_alert).A(G.palette.infoBlue)

        override fun check(cCtx: CheckContext): ICheckResult? {

            if (!Permission.notificationAccess.isGranted) {
                return null
            }

            // Skip if no valid rule is set.
            val enabled = PushAlertTable.listAll(ctx).any {
                it.enabled && it.isValid()
            }
            if (!enabled) {
                return null
            }

            val logger = cCtx.logger

            logChecking(ctx, logger)

            // Sleep 500ms for the `NotificationListenerService` to process all buffered notifications,
            //  see "NotificationListenerService.kt" for a detailed explanation.
            runBlocking(IO) {
                delay(500)
            }

            val spf = spf.PushAlert(ctx)

            // Following information is updated by NotificationMonitorService when receiving notifications.
            val pkgName = spf.pkgName
            val body = spf.body
            val expireTime: Long = spf.expireTime // millis

            val now = System.currentTimeMillis()

            if (now < expireTime) {
                logger?.success(
                    ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                )
                return ByPushAlert(detail = PushAlertDetail(
                    pkgName = pkgName,
                    body = body,
                ))
            }

            return null
        }
    }

    private class SmsAlert(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = spf.SmsAlert(ctx).isEnabled
        override fun isConfigEnabledForSms() = false

        override fun priority(): Int {
            return 10
        }

        override fun desc() = ctx.getString(R.string.sms_alert).A()

        override fun check(cCtx: CheckContext): ICheckResult? {
            val logger = cCtx.logger

            val spf = spf.SmsAlert(ctx)
            if (!spf.isEnabled) {
                return null
            }

            logChecking(ctx, logger)

            val duration = spf.duration.toLong() * 1000 // second * 1000 -> millis
            val receiveSmsTimestamp = spf.timestamp // the time it received that SMS
            val expire = receiveSmsTimestamp + duration

            val now = Now.currentMillis()

            if (now < expire) {
                logger?.success(
                    ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                )
                return BySmsAlert()
            }

            return null
        }
    }

    private class SmsBomb(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabledForCall() = spf.SmsBomb(ctx).isEnabled
        override fun isConfigEnabledForSms() = false
        override fun listType() = false
        override fun desc() = ctx.getString(R.string.sms_bomb).A(G.palette.infoBlue)

        override fun priority(): Int {
            return 20
        }

        override fun check(cCtx: CheckContext): ICheckResult? {

            val logger = cCtx.logger

            val spf = spf.SmsBomb(ctx)
            if (!spf.isEnabled) {
                return null
            }

            logChecking(ctx, logger)

            // 1. check if regex matches
            val regex = spf.regexStr
            val flags = spf.regexFlags
            val matches = regex.regexMatches(cCtx.smsContent!!, flags)
            if (!matches) {
                return null
            }

            var blockIt = false
            // 2. check if lockscreen protect on
            if (spf.isLockScreenProtectionEnabled && Util.isDeviceLocked(ctx)) {
                blockIt = true
            }

            // 3. check if within interval
            val now = Now.currentMillis()
            val lastBombTime = spf.timestamp
            val interval = spf.interval // in seconds

            if (lastBombTime + interval*1000 > now) {
                blockIt = true
            }

            // 4. save the last bomb time
            spf.timestamp = Now.currentMillis()

            if (blockIt) {
                logger?.error(
                    ctx.getString(R.string.blocked_by_template).formatAnnotated(desc())
                )
                return BySmsBomb()
            }
            return null
        }
    }


    companion object {
        fun defaultCallCheckers(ctx: Context): List<IChecker> {
            val checkers = arrayListOf(
                PassedByDefault(ctx),
                EmergencyCall(ctx),
                EmergencySituation(ctx),
                STIR(ctx),
                SpamDB(ctx),
                Contact(ctx),
                NonContact(ctx),
                RepeatedCall(ctx),
                Dialed(ctx),
                Answered(ctx),
                RecentApp(ctx),
                MeetingMode(ctx),
                OffTime(ctx),
                InstantQuery(ctx, Def.ForNumber),
                PushAlert(ctx),
                SmsAlert(ctx)
            )

            // Add number rules to checkers
            val rules = NumberRegexTable().listAll(ctx)
            checkers += rules.map {
                it.numberRuleToChecker(ctx)
            }
            return checkers
        }

        // Return value: Triple< check_result, full_log, anything_went_wrong >
        fun checkCall(
            ctx: Context,
            rawNumber: String,
            cnap: String? = null,
            callDetails: Call.Details? = null,
            simSlot: Int? = null,
            logger: ILogger? = null,
            checkers: List<IChecker> = defaultCallCheckers(ctx),
        ): Triple<ICheckResult, String?, Boolean> {
            val cCtx = CheckContext(
                rawNumber = rawNumber,
                cnap = cnap,
                callDetails = callDetails,
                logger = logger,
                checkers = checkers,
                simSlot = simSlot,
            )
            // pre-process the checkers, temporarily modify rules
            Preprocessors.callBots(ctx)
                .forEach { it.preprocess(cCtx) }

            // sort by priority desc
            val sortedCheckers = checkers.sortedByDescending {
                it.priority()
            }

            // Try all checkers in order, until a match is found
            // There will definitely be a match, as the last checker is PassedByDefault and it always allows.
            val result = sortedCheckers.firstNotNullOf {
                it.check(cCtx)
            }
            val fullScreeningLog = logger?.getSaveableOutput()?.serialize() ?: ""

            return Triple(
                result, fullScreeningLog, cCtx.anythingWrong
            )
        }

        fun defaultSmsCheckers(
            ctx: Context,
        ): List<IChecker> {
            val checkers = arrayListOf<IChecker>(
                PassedByDefault(ctx),
                Contact(ctx),
                NonContact(ctx),
                SpamDB(ctx),
                MeetingMode(ctx),
                OffTime(ctx),
                SmsBomb(ctx),
                InstantQuery(ctx, Def.ForSms),
            )

            //  add number rules to checkers
            val numberRules = NumberRegexTable().listAll(ctx)
            checkers += numberRules.map {
                it.numberRuleToChecker(ctx)
            }

            //  add sms content rules to checkers
            val contentFilters = ContentRegexTable().listAll(ctx)
            checkers += contentFilters.map {
                Content(ctx, it)
            }

            return checkers
        }
        fun checkSms(
            ctx: Context,
            rawNumber: String,
            messageBody: String,
            simSlot: Int? = null,
            logger: ILogger? = null,
            checkers: List<IChecker> = defaultSmsCheckers(ctx),
        ): Triple<ICheckResult, String?, Boolean> {
            val cCtx = CheckContext(
                rawNumber = rawNumber,
                smsContent = messageBody,
                logger = logger,
                checkers = checkers,
                simSlot = simSlot,
            )

            // pre-process the checkers, temporarily modify rules
            Preprocessors.smsBots(ctx)
                .forEach { it.preprocess(cCtx) }

            // sort by priority desc
            val sortedCheckers = checkers.sortedByDescending {
                it.priority()
            }

            // Try all checkers in order, until a match is found
            // There will definitely be a match, as the last checker is PassedByDefault and it always allows.
            val result = sortedCheckers.firstNotNullOf {
                it.check(cCtx)
            }
            val fullScreeningLog = logger?.getSaveableOutput()?.serialize() ?: ""

            return Triple(
                result, fullScreeningLog, cCtx.anythingWrong
            )
        }

        // It returns a list<String>, all the Strings will be shown as Buttons in the notification.
        fun checkQuickCopy(
            ctx: Context,
            rawNumber: String,
            messageBody: String?,
            isCall: Boolean, // is this called from incoming call or message.
            isBlocked: Boolean,
        ): List<String> {

            return QuickCopyRegexTable().listAll(ctx).filter {
                val c1 = it.flags.hasFlag(if (isCall) Def.FLAG_FOR_CALL else Def.FLAG_FOR_SMS)
                val c2 =
                    it.flags.hasFlag(if (isBlocked) Def.FLAG_FOR_BLOCKED else Def.FLAG_FOR_PASSED)
                c1 && c2
            }.sortedByDescending {
                it.priority
            }.fold(mutableListOf()) { acc, it ->

                fun autoCopy(text: String) {
                    if (it.flags.hasFlag(Def.FLAG_AUTO_COPY)) {
                        CoroutineScope(IO).launch { Clipboard.copy(ctx, text) }
                    }
                }

                val opts = Util.flagsToRegexOptions(it.patternFlags)
                val regex = it.pattern.toRegex(opts)

                val forNumber = it.flags.hasFlag(Def.FLAG_FOR_NUMBER)
                val forContent = it.flags.hasFlag(Def.FLAG_FOR_CONTENT)

                if (forNumber) {
                    val numberToCheck = if (it.patternFlags.hasFlag(Def.FLAG_REGEX_RAW_NUMBER))
                        rawNumber
                    else
                        Util.clearNumber(rawNumber)

                    val extracted = Util.extractString(regex, numberToCheck)
                    if (extracted != null) {
                        acc.add(extracted)
                        autoCopy(extracted)
                    }
                }
                if (forContent && messageBody != null) {
                    val extracted = Util.extractString(regex, messageBody)
                    if (extracted != null) {
                        acc.add(extracted)
                        autoCopy(extracted)
                    }
                }
                acc
            }
        }
    }
}
