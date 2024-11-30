package spam.blocker.service

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.Connection
import spam.blocker.R
import spam.blocker.db.ApiTable
import spam.blocker.db.CallTable
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.db.RuleTable
import spam.blocker.db.SmsTable
import spam.blocker.db.SpamTable
import spam.blocker.def.Def
import spam.blocker.service.bot.ActionContext
import spam.blocker.service.bot.QueryResult
import spam.blocker.service.bot.executeAll
import spam.blocker.util.Contacts
import spam.blocker.util.ILogger
import spam.blocker.util.Permissions
import spam.blocker.util.PhoneNumber
import spam.blocker.util.SharedPref.ApiOptions
import spam.blocker.util.SharedPref.Contact
import spam.blocker.util.SharedPref.Dialed
import spam.blocker.util.SharedPref.RecentApps
import spam.blocker.util.SharedPref.RepeatedCall
import spam.blocker.util.SharedPref.SpamDB
import spam.blocker.util.SharedPref.Stir
import spam.blocker.util.TimeSchedule
import spam.blocker.util.Util
import spam.blocker.util.hasFlag
import spam.blocker.util.race

class CheckResult(
    val shouldBlock: Boolean,
    val result: Int,
) {
    var byContact: String? = null // allowed by contact
    var byRule: RegexRule? = null // allowed or blocked by this filter rule
    var byApp: String? = null // allowed by recent app, or blocked by meeting mode
    var stirResult: Int? = null
    var byInstantQuerySummary: String? = null

    // This `reason` will be saved to database as a string
    fun reason(): String {
        if (byContact != null) return byContact!!
        if (byRule != null) return byRule!!.id.toString()
        if (byApp != null) return byApp!!
        if (stirResult != null) return stirResult.toString()
        if (byInstantQuerySummary != null) return byInstantQuerySummary.toString()
        return ""
    }
}

interface IChecker {
    fun priority(): Int
    fun check(): CheckResult?
}

class Checker { // for namespace only

    class Emergency(
        private val ctx: Context,
        private val logger: ILogger?,
        private val callDetails: Call.Details?
    ) : IChecker {
        override fun priority(): Int {
            return Int.MAX_VALUE
        }

        override fun check(): CheckResult? {
            logger?.info(
                ctx.getString(R.string.checking_template)
                    .format(ctx.getString(R.string.emergency_call))
            )
            if (callDetails == null) {// there is no callDetail when testing
                logger?.debug(ctx.getString(R.string.skip_for_testing))
                return null
            }

            if (callDetails.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)
                || callDetails.hasProperty(Call.Details.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL)
            ) {
                logger?.success(
                    ctx.getString(R.string.allowed_by)
                        .format(ctx.getString(R.string.emergency_call))
                )
                return CheckResult(false, Def.RESULT_ALLOWED_BY_EMERGENCY)
            }

            return null
        }
    }

    class STIR(
        private val ctx: Context,
        private val logger: ILogger?,
        private val callDetails: Call.Details?
    ) : IChecker {
        override fun priority(): Int {
            val isExclusive = Stir(ctx).isExclusive()
            return if (isExclusive) 0 else 10
        }

        override fun check(): CheckResult? {
            val spf = Stir(ctx)
            if (!spf.isEnabled())
                return null

            logger?.info(
                ctx.getString(R.string.checking_template)
                    .format(ctx.getString(R.string.stir_attestation))
            )

            // STIR only works >= Android 11
            if (Build.VERSION.SDK_INT < Def.ANDROID_11) {
                logger?.debug(ctx.getString(R.string.android_ver_lower_than_11))
                return null
            }

            // there is no callDetail when testing
            if (callDetails == null) {
                logger?.debug(ctx.getString(R.string.skip_for_testing))
                return null
            }

            val exclusive = spf.isExclusive()
            val includeUnverified = spf.isIncludeUnverified()

            val stir = callDetails.callerNumberVerificationStatus

            val pass = stir == Connection.VERIFICATION_STATUS_PASSED
            val unverified = stir == Connection.VERIFICATION_STATUS_NOT_VERIFIED
            val fail = stir == Connection.VERIFICATION_STATUS_FAILED

            if (exclusive) {
                if (fail || (includeUnverified && unverified)) {
                    logger?.error(
                        ctx.getString(R.string.blocked_by)
                            .format(ctx.getString(R.string.stir_attestation))
                                + ": ${resultStr(ctx, Def.RESULT_BLOCKED_BY_STIR, stir.toString())}"
                    )
                    return CheckResult(true, Def.RESULT_BLOCKED_BY_STIR)
                        .apply { stirResult = stir }
                }
            } else {
                if (pass || (includeUnverified && unverified)) {
                    logger?.success(
                        ctx.getString(R.string.allowed_by)
                            .format(ctx.getString(R.string.stir_attestation))
                                + ": ${resultStr(ctx, Def.RESULT_ALLOWED_BY_STIR, stir.toString())}"
                    )
                    return CheckResult(false, Def.RESULT_ALLOWED_BY_STIR)
                        .apply { stirResult = stir }
                }
            }

            return null
        }
    }

    // The "Database" in quick settings.
    // It checks whether the number exists in the spam database.
    class SpamDB(
        private val ctx: Context,
        private val logger: ILogger?,
        private val rawNumber: String,
    ) : IChecker {
        override fun priority(): Int {
            return 0
        }

        override fun check(): CheckResult? {
            val enabled = SpamDB(ctx).isEnabled()
            if (!enabled)
                return null

            logger?.info(
                ctx.getString(R.string.checking_template).format(ctx.getString(R.string.database))
            )

            val exists = SpamTable.numberExists(ctx, rawNumber) ||
                    SpamTable.numberExists(ctx, Util.clearNumber(rawNumber))

            if (exists) {
                logger?.error(
                    ctx.getString(R.string.blocked_by).format(ctx.getString(R.string.database))
                )
                return CheckResult(true, Def.RESULT_BLOCKED_BY_SPAM_DB)
            }
            return null
        }
    }

    // The "Contacts" in quick settings.
    // It checks whether the phone number belongs to a contact.
    class Contact(
        private val ctx: Context,
        private val logger: ILogger?,
        private val rawNumber: String,
    ) : IChecker {
        override fun priority(): Int {
            val isExclusive = Contact(ctx).isExclusive()
            return if (isExclusive) 0 else 10
        }

        override fun check(): CheckResult? {
            val spf = Contact(ctx)

            if (!spf.isEnabled() or !Permissions.isContactsPermissionGranted(ctx)) {
                return null
            }
            logger?.info(
                ctx.getString(R.string.checking_template).format(ctx.getString(R.string.contacts))
            )

            val contact = Contacts.findContactByRawNumber(ctx, rawNumber)
            if (contact != null) { // is contact
                logger?.success(
                    ctx.getString(R.string.allowed_by)
                        .format(ctx.getString(R.string.contacts)) + ": ${contact.name}"
                )
                return CheckResult(false, Def.RESULT_ALLOWED_BY_CONTACT)
                    .apply { byContact = contact.name }
            } else { // not contact
                if (spf.isExclusive()) {
                    logger?.error(
                        ctx.getString(R.string.blocked_by).format(
                            resultStr(ctx, Def.RESULT_BLOCKED_BY_NON_CONTACT, "")
                        )
                    )
                    return CheckResult(true, Def.RESULT_BLOCKED_BY_NON_CONTACT)
                }
            }
            return null
        }
    }

    class RepeatedCall(
        private val ctx: Context,
        private val logger: ILogger?,
        private val rawNumber: String,
        private val isTesting: Boolean = false
    ) : IChecker {
        override fun priority(): Int {
            return 10
        }

        override fun check(): CheckResult? {
            val canReadCalls = Permissions.isCallLogPermissionGranted(ctx)
            val canReadSMSs = Permissions.isReadSmsPermissionGranted(ctx)

            val spf = RepeatedCall(ctx)
            if (!spf.isEnabled() || (!canReadCalls && !canReadSMSs)) {
                return null
            }
            logger?.info(
                ctx.getString(R.string.checking_template)
                    .format(ctx.getString(R.string.repeated_call))
            )

            val times = spf.getTimes()
            val durationMinutes = spf.getInXMin()

            val durationMillis = durationMinutes.toLong() * 60 * 1000

            val phoneNumber = PhoneNumber(ctx, rawNumber)

            // count Calls from real call history
            var nCalls = Permissions.countHistoryCallByNumber(
                ctx,
                phoneNumber,
                Def.DIRECTION_INCOMING,
                durationMillis
            )
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
            var nSMSs = Permissions.countHistorySMSByNumber(
                ctx,
                phoneNumber,
                Def.DIRECTION_INCOMING,
                durationMillis
            )
            if (isTesting) { // try local db
                val nSMSsTesting = SmsTable().countRepeatedRecordsWithinSeconds(
                    ctx,
                    rawNumber,
                    durationMinutes * 60
                )
                if (nSMSs < nSMSsTesting)
                    nSMSs = nSMSsTesting
            }


            logger?.debug("${ctx.getString(R.string.call)}: $nCalls, ${ctx.getString(R.string.sms)}: $nSMSs")

            // check
            if (nCalls + nSMSs >= times) {
                logger?.success(
                    ctx.getString(R.string.allowed_by).format(ctx.getString(R.string.repeated_call))
                )
                return CheckResult(false, Def.RESULT_ALLOWED_BY_REPEATED)
            }
            return null
        }
    }

    class Dialed(
        private val ctx: Context,
        private val logger: ILogger?,
        private val rawNumber: String,
    ) : IChecker {
        override fun priority(): Int {
            return 10
        }

        override fun check(): CheckResult? {
            val spf = Dialed(ctx)
            if (!spf.isEnabled()
                || (!Permissions.isCallLogPermissionGranted(ctx) && !Permissions.isReadSmsPermissionGranted(
                    ctx
                ))
            ) {
                return null
            }

            logger?.info(
                ctx.getString(R.string.checking_template)
                    .format(ctx.getString(R.string.dialed_number))
            )

            val durationDays = spf.getDays()

            val durationMillis = durationDays.toLong() * 24 * 3600 * 1000

            // repeated count of call/sms, sms also counts
            val phoneNumber = PhoneNumber(ctx, rawNumber)
            val nCalls = Permissions.countHistoryCallByNumber(
                ctx,
                phoneNumber,
                Def.DIRECTION_OUTGOING,
                durationMillis
            )
            val nSMSs = Permissions.countHistorySMSByNumber(
                ctx,
                phoneNumber,
                Def.DIRECTION_OUTGOING,
                durationMillis
            )
            logger?.debug("${ctx.getString(R.string.call)}: $nCalls, ${ctx.getString(R.string.sms)}: $nSMSs")

            if (nCalls + nSMSs > 0) {
                logger?.success(
                    ctx.getString(R.string.allowed_by).format(ctx.getString(R.string.dialed_number))
                )
                return CheckResult(false, Def.RESULT_ALLOWED_BY_DIALED)
            }
            return null
        }
    }

    class OffTime(
        private val ctx: Context,
        private val logger: ILogger?,
    ) : IChecker {
        override fun priority(): Int {
            return 10
        }

        override fun check(): CheckResult? {
            val spf = spam.blocker.util.SharedPref.OffTime(ctx)
            if (!spf.isEnabled()) {
                return null
            }
            logger?.info(
                ctx.getString(R.string.checking_template).format(ctx.getString(R.string.off_time))
            )

            val stHour = spf.getStartHour()
            val stMin = spf.getStartMin()
            val etHour = spf.getEndHour()
            val etMin = spf.getEndMin()

            // Entire day
            if (stHour == etHour && stMin == etMin) {
                logger?.debug(ctx.getString(R.string.entire_day))
                logger?.success(
                    ctx.getString(R.string.allowed_by).format(ctx.getString(R.string.off_time))
                )
                return CheckResult(false, Def.RESULT_ALLOWED_BY_OFF_TIME)
            }

            if (Util.isCurrentTimeWithinRange(stHour, stMin, etHour, etMin)) {
                logger?.success(
                    ctx.getString(R.string.allowed_by).format(ctx.getString(R.string.off_time))
                )
                return CheckResult(false, Def.RESULT_ALLOWED_BY_OFF_TIME)
            }

            return null
        }
    }

    class RecentApp(
        private val ctx: Context,
        private val logger: ILogger?,
    ) : IChecker {
        override fun priority(): Int {
            return 10
        }

        override fun check(): CheckResult? {
            logger?.info(
                ctx.getString(R.string.checking_template)
                    .format(ctx.getString(R.string.recent_apps))
            )

            val spf = RecentApps(ctx)

            val defaultDuration = spf.getDefaultMin() // in minutes

            // To avoid querying db for each app, aggregate them by duration, like:
            //  pkg.a,pkg.b@20,pkg.c
            // ->
            //  map {
            //    5  -> [pkg.a,pkg.c], // 5 is the default duration
            //    20 -> [pkg.b],
            //  }
            //  So it only queries db for two times: 5 min and 20 min.
            val aggregation = spf.getList().groupBy {
                it.duration ?: defaultDuration
            }.mapValues { (_, values) ->
                values.map { it.pkgName }
            }

            for ((duration, appList) in aggregation) {
                val usedApps = Permissions.listUsedAppWithinXSecond(ctx, duration * 60)

                val intersection = appList.toList().intersect(usedApps.toSet())

                if (intersection.isNotEmpty()) {
                    logger?.success(
                        ctx.getString(R.string.allowed_by)
                            .format(ctx.getString(R.string.recent_apps)) + ": ${intersection.first()}"
                    )
                    return CheckResult(
                        false,
                        Def.RESULT_ALLOWED_BY_RECENT_APP
                    ).apply { byApp = intersection.first() }
                }
            }
            return null
        }
    }

    class MeetingMode(
        private val ctx: Context,
        private val logger: ILogger?,
    ) : IChecker {
        override fun priority(): Int {
            val spf = spam.blocker.util.SharedPref.MeetingMode(ctx)
            return spf.getPriority()
        }

        override fun check(): CheckResult? {
            logger?.info(
                ctx.getString(R.string.checking_template).format(ctx.getString(R.string.in_meeting))
            )

            val spf = spam.blocker.util.SharedPref.MeetingMode(ctx)


            val apps = spf.getList()

            val eventsMap = Permissions.getAppsEvents(ctx, apps.toSet())

            // Check if any app is running a foreground service
            val appInMeeting = apps.firstOrNull {
                Permissions.isForegroundServiceRunning(eventsMap[it])
            }

            if (appInMeeting != null) {
                logger?.success(
                    ctx.getString(R.string.allowed_by)
                        .format(ctx.getString(R.string.in_meeting)) + ": $appInMeeting"
                )
                return CheckResult(
                    true,
                    Def.RESULT_BLOCKED_BY_MEETING_MODE
                ).apply { byApp = appInMeeting }
            }
            return null
        }
    }

    class InstantQuery(
        private val ctx: Context,
        private val logger: ILogger?,
        private val rawNumber: String,
    ) : IChecker {
        override fun priority(): Int {
            return -1
        }

        override fun check(): CheckResult? {
            val apis = ApiTable.listAll(ctx).filter { it.enabled }

            if (apis.isNotEmpty())
                logger?.info(ctx.getString(R.string.checking_template).format(ctx.getString(R.string.instant_query)))

            val timeout = ApiOptions(ctx).getTimeout().toLong()

            // Run all apis simultaneously
            // get the first non-null return value, and stop all others
            var (winnerApi, result) = race(
                competitors = apis,
                runner = {
                    {
                        try {

                            val aCtx = ActionContext(
                                logger = logger,
                                rawNumber = rawNumber,
                            )
                            val success = it.actions.executeAll(ctx, aCtx)

                            if (!success)
                                null

                            val result = aCtx.lastOutput as QueryResult
                            if (result.isSpam != null) { // null == undetermined
                                result
                            } else {
                                null
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                },
                timeoutMillis = timeout,
            )

            if (result?.isSpam != null) { // either spam or non-spam
                if (result.isSpam)
                    logger?.error(ctx.getString(R.string.blocked_by).format(ctx.getString(R.string.instant_query)))
                else
                    logger?.success(ctx.getString(R.string.allowed_by).format(ctx.getString(R.string.instant_query)))

                return CheckResult(
                    result.isSpam,
                    if (result.isSpam) Def.RESULT_BLOCKED_BY_INSTANT_QUERY else Def.RESULT_ALLOWED_BY_INSTANT_QUERY
                ).apply {
                    byInstantQuerySummary =
                        "${winnerApi?.summary()}${if (result.category != null) " (${result.category})" else ""}"
                }
            }

            return null
        }
    }

    // Check if a number rule matches the incoming number
    class Number(
        private val ctx: Context,
        private val logger: ILogger?,
        private val rawNumber: String,
        private val numberRule: RegexRule,
    ) : IChecker {
        override fun priority(): Int {
            return numberRule.priority
        }

        override fun check(): CheckResult? {
            // 1. check time schedule
            if (TimeSchedule.dissatisfyNow(numberRule.schedule)) {
                logger?.debug(ctx.getString(R.string.outside_time_schedule))
                return null
            }

            logger?.info(ctx.getString(R.string.checking_template).format(ctx.getString(R.string.number_filter)) + ": ${numberRule.summary()}")

            // 2. check regex
            val opts = Util.flagsToRegexOptions(numberRule.patternFlags)

            // check if user enabled the `RawNumber` mode for this regex
            val numberToCheck = if (numberRule.patternFlags.hasFlag(Def.FLAG_REGEX_RAW_NUMBER))
                rawNumber
            else
                Util.clearNumber(rawNumber)

            if (numberRule.pattern.toRegex(opts).matches(numberToCheck)) {
                val block = numberRule.isBlacklist

                if (block)
                    logger?.error(ctx.getString(R.string.blocked_by).format(ctx.getString(R.string.number_filter)) + ": ${numberRule.summary()}")
                else
                    logger?.success(ctx.getString(R.string.allowed_by).format(ctx.getString(R.string.number_filter)) + ": ${numberRule.summary()}")

                return CheckResult(
                    block,
                    if (block) Def.RESULT_BLOCKED_BY_NUMBER else Def.RESULT_ALLOWED_BY_NUMBER
                ).apply {
                    byRule = numberRule
                }
            }

            return null
        }
    }

    // The regex flag `Contact`, it matches the contact name instead of the phone number
    class RegexContact(
        private val ctx: Context,
        private val logger: ILogger?,
        private val rawNumber: String,
        private val rule: RegexRule
    ) : IChecker {
        override fun priority(): Int {
            return rule.priority
        }

        override fun check(): CheckResult? {
            if (!Permissions.isContactsPermissionGranted(ctx)) {
                return null
            }
            logger?.info(ctx.getString(R.string.checking_template).format(ctx.getString(R.string.contact_rule)) + ": ${rule.summary()}")

            // 1. check time schedule
            if (TimeSchedule.dissatisfyNow(rule.schedule)) {
                logger?.debug(ctx.getString(R.string.outside_time_schedule))
                return null
            }

            // 2. check regex
            val contactInfo = Contacts.findContactByRawNumber(ctx, rawNumber)
            if (contactInfo != null) {
                val opts = Util.flagsToRegexOptions(rule.patternFlags)

                if (rule.pattern.toRegex(opts).matches(contactInfo.name)) {
                    val block = rule.isBlacklist

                    if (block)
                        logger?.error(ctx.getString(R.string.blocked_by).format(ctx.getString(R.string.contact_rule)) + ": ${rule.summary()}")
                    else
                        logger?.success(ctx.getString(R.string.allowed_by).format(ctx.getString(R.string.contact_rule)) + ": ${rule.summary()}")

                    return CheckResult(
                        block,
                        if (block) Def.RESULT_BLOCKED_BY_CONTACT_REGEX else Def.RESULT_ALLOWED_BY_CONTACT_REGEX
                    ).apply {
                        byRule = rule
                    }
                }
            }
            return null
        }
    }

    // The regex flag `Contact Group`, it matches the contact group name instead of the phone number
    class ContactGroup(
        private val ctx: Context,
        private val logger: ILogger?,
        private val rawNumber: String,
        private val rule: RegexRule
    ) : IChecker {
        override fun priority(): Int {
            return rule.priority
        }

        override fun check(): CheckResult? {
            if (!Permissions.isContactsPermissionGranted(ctx)) {
                return null
            }

            logger?.info(ctx.getString(R.string.checking_template).format(ctx.getString(R.string.contact_group)) + ": ${rule.summary()}")

            // 1. check time schedule
            if (TimeSchedule.dissatisfyNow(rule.schedule)) {
                logger?.debug(ctx.getString(R.string.outside_time_schedule))
                return null
            }

            // 2. check regex
            val groupNames = Contacts.findGroupsByRawNumber(ctx, rawNumber)
            for (groupName in groupNames) { // is contact
                val opts = Util.flagsToRegexOptions(rule.patternFlags)

                if (rule.pattern.toRegex(opts).matches(groupName)) {
                    val block = rule.isBlacklist

                    if (block)
                        logger?.error(ctx.getString(R.string.blocked_by).format(ctx.getString(R.string.contact_group)) + ": ${rule.summary()}")
                    else
                        logger?.success(ctx.getString(R.string.allowed_by).format(ctx.getString(R.string.contact_group)) + ": ${rule.summary()}")

                    return CheckResult(
                        block,
                        if (block) Def.RESULT_BLOCKED_BY_CONTACT_GROUP else Def.RESULT_ALLOWED_BY_CONTACT_GROUP
                    ).apply {
                        byRule = rule
                    }
                }
            }
            return null
        }
    }

    /*
        Check if text message body matches the SMS Content rule,
        the number is also checked when "for particular number" is enabled
     */
    class Content(
        private val ctx: Context,
        private val logger: ILogger?,
        private val rawNumber: String,
        private val messageBody: String,
        private val rule: RegexRule
    ) : IChecker {
        override fun priority(): Int {
            return rule.priority
        }

        override fun check(): CheckResult? {
            logger?.info(ctx.getString(R.string.checking_template).format(ctx.getString(R.string.content_filter)) + ": ${rule.summary()}")

            // 1. check time schedule
            if (TimeSchedule.dissatisfyNow(rule.schedule)) {
                logger?.debug(ctx.getString(R.string.outside_time_schedule))
                return null
            }

            // 2. check regex
            val opts = Util.flagsToRegexOptions(rule.patternFlags)
            val optsExtra = Util.flagsToRegexOptions(rule.patternExtraFlags)

            val contentMatches = rule.pattern.toRegex(opts).matches(messageBody)
            val numberToCheck = if (rule.patternExtraFlags.hasFlag(Def.FLAG_REGEX_RAW_NUMBER))
                rawNumber
            else
                Util.clearNumber(rawNumber)

            // 3. check for particular number
            val matches = if (rule.patternExtra != "") { // for particular number enabled
                // if this regex is for matching contact group
                val forContactGroup =
                    rule.patternExtraFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT_GROUP)
                if (forContactGroup) {
                    val anyContactGroupMatches = Contacts.findGroupsByRawNumber(ctx, rawNumber)
                        .any { groupName ->
                            rule.patternExtra.toRegex(optsExtra).matches(groupName)
                        }
                    contentMatches && anyContactGroupMatches
                } else {
                    val particularNumberMatches =
                        rule.patternExtra.toRegex(optsExtra).matches(numberToCheck)

                    contentMatches && particularNumberMatches
                }
            } else {
                contentMatches
            }

            if (matches) {
                val block = rule.isBlacklist

                if (block)
                    logger?.error(ctx.getString(R.string.blocked_by).format(ctx.getString(R.string.content_filter)) + ": ${rule.summary()}")
                else
                    logger?.success(ctx.getString(R.string.allowed_by).format(ctx.getString(R.string.content_filter)) + ": ${rule.summary()}")

                return CheckResult(
                    block,
                    if (block) Def.RESULT_BLOCKED_BY_CONTENT else Def.RESULT_ALLOWED_BY_CONTENT
                ).apply { byRule = rule }
            }
            return null
        }
    }

    companion object {

        fun checkCall(
            ctx: Context,
            logger: ILogger?,
            rawNumber: String,
            callDetails: Call.Details? = null
        ): CheckResult {
            val checkers = arrayListOf(
                Checker.Emergency(ctx, logger, callDetails),
                Checker.STIR(ctx, logger, callDetails),
                Checker.SpamDB(ctx, logger, rawNumber),
                Checker.Contact(ctx, logger, rawNumber),
                Checker.RepeatedCall(ctx, logger, rawNumber, isTesting = callDetails == null),
                Checker.Dialed(ctx, logger, rawNumber),
                Checker.RecentApp(ctx, logger),
                Checker.MeetingMode(ctx, logger),
                Checker.OffTime(ctx, logger),
                Checker.InstantQuery(ctx, logger, rawNumber),
            )

            //  add number rules to checkers
            val rules = NumberRuleTable().listRules(ctx, Def.FLAG_FOR_CALL)
            checkers += rules.map {
                val forContact = it.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT)
                val forContactGroup = it.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT_GROUP)
                if (forContact)
                    Checker.RegexContact(ctx, logger, rawNumber, it)
                else if (forContactGroup)
                    Checker.ContactGroup(ctx, logger, rawNumber, it)
                else
                    Checker.Number(ctx, logger, rawNumber, it)
            }

            // sort by priority desc
            checkers.sortByDescending {
                it.priority()
            }

            // try all checkers in order, until a match is found
            var result: CheckResult? = null
            checkers.firstOrNull {
                result = it.check()
                result != null
            }
            // match found
            if (result != null) {
                return result
            }

            logger?.success(ctx.getString(R.string.passed_by_default))
            // pass by default
            return CheckResult(false, Def.RESULT_ALLOWED_BY_DEFAULT)
        }

        fun checkSms(
            ctx: Context,
            logger: ILogger?,
            rawNumber: String,
            messageBody: String
        ): CheckResult {

            val checkers = arrayListOf<IChecker>(
                Checker.Contact(ctx, logger, rawNumber),
                Checker.SpamDB(ctx, logger, rawNumber),
                Checker.MeetingMode(ctx, logger),
                Checker.OffTime(ctx, logger)
            )

            //  add number rules to checkers
            val numberFilters = NumberRuleTable().listRules(ctx, Def.FLAG_FOR_SMS)
            checkers += numberFilters.map {
                val forContact = it.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT)
                val forContactGroup = it.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT_GROUP)
                if (forContact)
                    Checker.RegexContact(ctx, logger, rawNumber, it)
                else if (forContactGroup)
                    Checker.ContactGroup(ctx, logger, rawNumber, it)
                else
                    Checker.Number(ctx, logger, rawNumber, it)
            }

            //  add sms content rules to checkers
            val contentFilters = ContentRuleTable().listRules(ctx, 0/* doesn't care */)
            checkers += contentFilters.map {
                Checker.Content(ctx, logger, rawNumber, messageBody, it)
            }

            // sort by priority desc
            checkers.sortByDescending {
                it.priority()
            }

            // try all checkers in order, until a match is found
            var result: CheckResult? = null
            checkers.firstOrNull {
                result = it.check()
                result != null
            }
            // match found
            if (result != null) {
                return result!!
            }

            // pass by default
            return CheckResult(false, Def.RESULT_ALLOWED_BY_DEFAULT)
        }

        // It returns a list<String>, all the Strings will be shown as Buttons in the notification.
        fun checkQuickCopy(
            ctx: Context,
            rawNumber: String, messageBody: String?,
            isCall: Boolean, // is this called from incoming call or message.
            isBlocked: Boolean,
        ): List<String> {

            return QuickCopyRuleTable().listAll(ctx).filter {
                val c1 = it.flags.hasFlag(if (isCall) Def.FLAG_FOR_CALL else Def.FLAG_FOR_SMS)
                val c2 =
                    it.flags.hasFlag(if (isBlocked) Def.FLAG_FOR_BLOCKED else Def.FLAG_FOR_PASSED)
                c1 && c2
            }.sortedByDescending {
                it.priority
            }.fold(mutableListOf()) { acc, it ->

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
                    if (extracted != null)
                        acc.add(extracted)
                }
                if (forContent && messageBody != null) {
                    val extracted = Util.extractString(regex, messageBody)
                    if (extracted != null)
                        acc.add(extracted)
                }
                acc
            }
        }


        private fun ruleReasonStr(ctx: Context, ruleTable: RuleTable?, reason: String): String {
            val f = ruleTable?.findRuleById(ctx, reason.toLong())

            val reasonStr = if (f != null) {
                if (f.description != "") f.description else f.patternStr()
            } else {
                ctx.resources.getString(R.string.deleted_filter)
            }
            return reasonStr
        }

        fun resultStr(ctx: Context, result: Int, reason: String): String {

            val res = ctx.resources

            return when (result) {
                Def.RESULT_ALLOWED_BY_CONTACT -> res.getString(R.string.contacts)
                Def.RESULT_BLOCKED_BY_NON_CONTACT -> res.getString(R.string.non_contacts)
                Def.RESULT_ALLOWED_BY_STIR, Def.RESULT_BLOCKED_BY_STIR -> {
                    when (reason.toInt()) {
                        Connection.VERIFICATION_STATUS_NOT_VERIFIED -> "${res.getString(R.string.stir_attestation)} ${
                            res.getString(
                                R.string.unverified
                            )
                        }"

                        Connection.VERIFICATION_STATUS_PASSED -> "${res.getString(R.string.stir_attestation)} ${
                            res.getString(
                                R.string.valid
                            )
                        }"

                        Connection.VERIFICATION_STATUS_FAILED -> "${res.getString(R.string.stir_attestation)} ${
                            res.getString(
                                R.string.spoof
                            )
                        }"

                        else -> res.getString(R.string.stir_attestation)
                    }
                }

                Def.RESULT_ALLOWED_BY_EMERGENCY -> res.getString(R.string.emergency_call)
                Def.RESULT_ALLOWED_BY_RECENT_APP -> res.getString(R.string.recent_apps) + " "
                Def.RESULT_BLOCKED_BY_MEETING_MODE -> res.getString(R.string.in_meeting) + " "
                Def.RESULT_BLOCKED_BY_INSTANT_QUERY, Def.RESULT_ALLOWED_BY_INSTANT_QUERY ->
                    res.getString(R.string.instant_query) + ": " + reason

                Def.RESULT_ALLOWED_BY_REPEATED -> res.getString(R.string.repeated_call)
                Def.RESULT_ALLOWED_BY_DIALED -> res.getString(R.string.dialed_number)
                Def.RESULT_ALLOWED_BY_OFF_TIME -> res.getString(R.string.off_time)
                Def.RESULT_ALLOWED_BY_NUMBER -> res.getString(R.string.whitelist) + ": " + ruleReasonStr(
                    ctx, NumberRuleTable(), reason
                )

                Def.RESULT_BLOCKED_BY_NUMBER -> res.getString(R.string.blacklist) + ": " + ruleReasonStr(
                    ctx, NumberRuleTable(), reason
                )

                Def.RESULT_BLOCKED_BY_SPAM_DB -> res.getString(R.string.database)

                Def.RESULT_ALLOWED_BY_CONTACT_GROUP, Def.RESULT_BLOCKED_BY_CONTACT_GROUP -> {
                    res.getString(R.string.contact_group) + ": " +
                            ruleReasonStr(ctx, NumberRuleTable(), reason)
                }

                Def.RESULT_ALLOWED_BY_CONTACT_REGEX, Def.RESULT_BLOCKED_BY_CONTACT_REGEX -> {
                    res.getString(R.string.contact_rule) + ": " +
                            ruleReasonStr(ctx, NumberRuleTable(), reason)
                }

                Def.RESULT_ALLOWED_BY_CONTENT -> res.getString(R.string.content) + ": " + ruleReasonStr(
                    ctx, ContentRuleTable(), reason
                )

                Def.RESULT_BLOCKED_BY_CONTENT -> res.getString(R.string.content) + ": " + ruleReasonStr(
                    ctx, ContentRuleTable(), reason
                )

                else -> res.getString(R.string.passed_by_default)
            }
        }
    }
}
