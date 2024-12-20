package spam.blocker.service.checker

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.Connection
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.db.SmsTable
import spam.blocker.db.SpamTable
import spam.blocker.def.Def
import spam.blocker.def.Def.RESULT_ALLOWED_BY_STIR
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NON_CONTACT
import spam.blocker.service.bot.ActionContext
import spam.blocker.service.bot.executeAll
import spam.blocker.ui.theme.Emerald
import spam.blocker.ui.theme.LightMagenta
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.util.A
import spam.blocker.util.Contacts
import spam.blocker.util.ILogger
import spam.blocker.util.Permissions
import spam.blocker.util.PhoneNumber
import spam.blocker.util.TimeSchedule
import spam.blocker.util.Util
import spam.blocker.util.formatAnnotated
import spam.blocker.util.hasFlag
import spam.blocker.util.race
import spam.blocker.util.spf

class CheckContext(
    val rawNumber: String,
    val callDetails: Call.Details? = null,
    val logger: ILogger? = null,
    val startTimeMillis: Long = System.currentTimeMillis(),
)


interface IChecker {
    fun priority(): Int
    fun check(): ICheckResult?
}

class Checker { // for namespace only

    private class Emergency(
        private val ctx: Context,
        private val cCtx: CheckContext,
    ) : IChecker {
        override fun priority(): Int {
            return Int.MAX_VALUE
        }

        override fun check(): ICheckResult? {
            val logger = cCtx.logger
            val callDetails = cCtx.callDetails
            logger?.info(
                ctx.getString(R.string.checking_template)
                    .formatAnnotated(
                        ctx.getString(R.string.emergency_call).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
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
                return ByEmergency()
            }

            return null
        }
    }

    private class STIR(
        private val ctx: Context,
        private val cCtx: CheckContext,
    ) : IChecker {
        override fun priority(): Int {
            val isExclusive = spf.Stir(ctx).isExclusive()
            return if (isExclusive) 0 else 10
        }

        override fun check(): ICheckResult? {
            val logger = cCtx.logger
            val callDetails = cCtx.callDetails

            val spf = spf.Stir(ctx)
            if (!spf.isEnabled())
                return null

            logger?.info(
                ctx.getString(R.string.checking_template)
                    .formatAnnotated(
                        ctx.getString(R.string.stir_attestation).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
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
                    val ret = BySTIR(Def.RESULT_BLOCKED_BY_STIR, stir)
                    logger?.error(
                        ctx.getString(R.string.blocked_by)
                            .format(ret.resultReasonStr(ctx))
                    )
                    return ret
                }
            } else {
                if (pass || (includeUnverified && unverified)) {
                    val ret = BySTIR(RESULT_ALLOWED_BY_STIR, stir)
                    logger?.success(
                        ctx.getString(R.string.allowed_by)
                            .format(ret.resultReasonStr(ctx))
                    )
                    return ret
                }
            }

            return null
        }
    }

    // The "Database" in quick settings.
    // It checks whether the number exists in the spam database.
    private class SpamDB(
        private val ctx: Context,
        private val cCtx: CheckContext,
    ) : IChecker {
        override fun priority(): Int {
            return 0
        }

        override fun check(): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            val enabled = spf.SpamDB(ctx).isEnabled()
            if (!enabled)
                return null

            logger?.info(
                ctx.getString(R.string.checking_template)
                    .formatAnnotated(
                        ctx.getString(R.string.database).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
            )

            val record = SpamTable.findByNumber(ctx, rawNumber) ?:
                    SpamTable.findByNumber(ctx, Util.clearNumber(rawNumber))

            if (record != null) {
                logger?.error(
                    ctx.getString(R.string.blocked_by).format(ctx.getString(R.string.database))
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
        private val cCtx: CheckContext,
    ) : IChecker {
        override fun priority(): Int {
            val isExclusive = spf.Contact(ctx).isExclusive()
            return if (isExclusive) 0 else 10
        }

        override fun check(): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            val spf = spf.Contact(ctx)

            if (!spf.isEnabled() or !Permissions.isContactsPermissionGranted(ctx)) {
                return null
            }
            logger?.info(
                ctx.getString(R.string.checking_template)
                    .formatAnnotated(
                        ctx.getString(R.string.contacts).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
            )

            val contact = Contacts.findContactByRawNumber(ctx, rawNumber)
            if (contact != null) { // is contact
                logger?.success(
                    ctx.getString(R.string.allowed_by)
                        .format(ctx.getString(R.string.contacts)) + ": ${contact.name}"
                )
                return ByContact(Def.RESULT_ALLOWED_BY_CONTACT, contact.name)
            } else { // not contact
                if (spf.isExclusive()) {
                    val ret = ByContact(RESULT_BLOCKED_BY_NON_CONTACT)
                    logger?.error(
                        ctx.getString(R.string.blocked_by).format(
                            ret.resultReasonStr(ctx)
                        )
                    )
                    return ret
                }
            }
            return null
        }
    }

    private class RepeatedCall(
        private val ctx: Context,
        private val cCtx: CheckContext,
    ) : IChecker {
        override fun priority(): Int {
            return 10
        }

        override fun check(): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger
            val isTesting = cCtx.callDetails == null

            val canReadCalls = Permissions.isCallLogPermissionGranted(ctx)
            val canReadSMSs = Permissions.isReadSmsPermissionGranted(ctx)

            val spf = spf.RepeatedCall(ctx)
            if (!spf.isEnabled() || (!canReadCalls && !canReadSMSs)) {
                return null
            }
            logger?.info(
                ctx.getString(R.string.checking_template)
                    .formatAnnotated(
                        ctx.getString(R.string.repeated_call).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
            )

            val times = spf.getTimes()
            val durationMinutes = spf.getInXMin()

            val durationMillis = durationMinutes.toLong() * 60 * 1000

            val phoneNumber = PhoneNumber(ctx, rawNumber)

            // count Calls from real call history
            var nCalls = Permissions.getHistoryCallsByNumber(
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
                return ByRepeatedCall()
            }
            return null
        }
    }

    private class Dialed(
        private val ctx: Context,
        private val cCtx: CheckContext,
    ) : IChecker {
        override fun priority(): Int {
            return 10
        }

        override fun check(): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            val spf = spf.Dialed(ctx)
            if (!spf.isEnabled()
                || (!Permissions.isCallLogPermissionGranted(ctx) && !Permissions.isReadSmsPermissionGranted(
                    ctx
                ))
            ) {
                return null
            }

            logger?.info(
                ctx.getString(R.string.checking_template)
                    .formatAnnotated(
                        ctx.getString(R.string.dialed_number).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
            )

            val durationDays = spf.getDays()

            val durationMillis = durationDays.toLong() * 24 * 3600 * 1000

            // repeated count of call/sms, sms also counts
            val phoneNumber = PhoneNumber(ctx, rawNumber)
            val nCalls = Permissions.getHistoryCallsByNumber(
                ctx,
                phoneNumber,
                Def.DIRECTION_OUTGOING,
                durationMillis
            ).size
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
                return ByDialedNumber()
            }
            return null
        }
    }

    private class OffTime(
        private val ctx: Context,
        private val cCtx: CheckContext,
    ) : IChecker {
        override fun priority(): Int {
            return 10
        }

        override fun check(): ICheckResult? {
            val logger = cCtx.logger

            val spf = spf.OffTime(ctx)
            if (!spf.isEnabled()) {
                return null
            }
            logger?.info(
                ctx.getString(R.string.checking_template)
                    .formatAnnotated(
                        ctx.getString(R.string.off_time).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
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
                return ByOffTime()
            }

            if (Util.isCurrentTimeWithinRange(stHour, stMin, etHour, etMin)) {
                logger?.success(
                    ctx.getString(R.string.allowed_by).format(ctx.getString(R.string.off_time))
                )
                return ByOffTime()
            }

            return null
        }
    }

    private class RecentApp(
        private val ctx: Context,
        private val cCtx: CheckContext,
    ) : IChecker {
        override fun priority(): Int {
            return 10
        }

        override fun check(): ICheckResult? {
            val logger = cCtx.logger

            logger?.info(
                ctx.getString(R.string.checking_template)
                    .formatAnnotated(
                        ctx.getString(R.string.recent_apps).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
            )

            val spf = spf.RecentApps(ctx)

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
                    return ByRecentApp(pkgName = intersection.first())
                }
            }
            return null
        }
    }

    private class MeetingMode(
        private val ctx: Context,
        private val cCtx: CheckContext,
    ) : IChecker {
        override fun priority(): Int {
            val spf = spf.MeetingMode(ctx)
            return spf.getPriority()
        }

        override fun check(): ICheckResult? {
            val logger = cCtx.logger

            logger?.info(
                ctx.getString(R.string.checking_template)
                    .formatAnnotated(
                        ctx.getString(R.string.in_meeting).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
            )

            val spf = spf.MeetingMode(ctx)

            val appInfos = spf.getList()

            val eventsMap = Permissions.getAppsEvents(ctx, appInfos.map { it.pkgName }.toSet())

            // Check if any app is running a foreground service
            val appInMeeting = appInfos.firstOrNull {
                val runningServiceNames = Permissions.listRunningForegroundServiceNames(
                    appEvents = eventsMap[it.pkgName],
                )
                val exclusions = it.exclusions

                runningServiceNames.any { serviceName ->
                    !exclusions.any { serviceName.contains(it) }
                }
            }

            if (appInMeeting != null) {
                logger?.error(
                    ctx.getString(R.string.blocked_by)
                        .format(ctx.getString(R.string.in_meeting)) + ": $appInMeeting"
                )
                return ByMeetingMode(pkgName = appInMeeting.pkgName)
            }
            return null
        }
    }

    private class InstantQuery(
        private val ctx: Context,
        private val cCtx: CheckContext,
    ) : IChecker {
        override fun priority(): Int {
            return -1
        }

        override fun check(): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            val apis = G.apiQueryVM.table.listAll(ctx).filter { it.enabled }

            if (apis.isEmpty())
                return null

            logger?.info(
                ctx.getString(R.string.checking_template)
                    .formatAnnotated(
                        ctx.getString(R.string.instant_query).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
            )

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
            //  and return that result immediately and kill other threads.
            var (winnerApi, result) = race(
                competitors = apis,
                timeoutMillis = timeLeft,
                runner = {
                    { scope ->
                        try {
                            val aCtx = ActionContext(
                                scope = scope,
                                logger = logger,
                                rawNumber = rawNumber,
                            )
                            val success = it.actions.executeAll(ctx, aCtx)

                            if (!success) {
                                null
                            }

                            val result = aCtx.racingResult
                            if (result?.determined == true) {
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
                        ctx.getString(R.string.blocked_by)
                            .format(ctx.getString(R.string.instant_query)) + " <${winnerApi.summary()}>"
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by)
                            .format(ctx.getString(R.string.instant_query)) + " <${winnerApi.summary()}>"
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

            return null
        }
    }

    // Check if a number rule matches the incoming number
    private class Number(
        private val ctx: Context,
        private val cCtx: CheckContext,
        private val numberRule: RegexRule,
    ) : IChecker {
        override fun priority(): Int {
            return numberRule.priority
        }

        override fun check(): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            // 1. check time schedule
            if (TimeSchedule.dissatisfyNow(numberRule.schedule)) {
                logger?.debug(ctx.getString(R.string.outside_time_schedule))
                return null
            }

            logger?.info(
                (ctx.getString(R.string.checking_template)+ ": %s")
                    .formatAnnotated(
                        ctx.getString(R.string.number_rule).A(SkyBlue),
                        priority().toString().A(LightMagenta),
                        numberRule.summary().A(if (numberRule.isBlacklist) Salmon else Emerald),
                    )
            )

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
                    logger?.error(
                        ctx.getString(R.string.blocked_by)
                            .format(ctx.getString(R.string.number_rule)) + ": ${numberRule.summary()}"
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by)
                            .format(ctx.getString(R.string.number_rule)) + ": ${numberRule.summary()}"
                    )

                return ByRegexRule(
                    type = if (block) Def.RESULT_BLOCKED_BY_NUMBER else Def.RESULT_ALLOWED_BY_NUMBER,
                    rule = numberRule,
                )
            }

            return null
        }
    }

    // The regex flag `Contact`, it matches the contact name instead of the phone number
    private class RegexContact(
        private val ctx: Context,
        private val cCtx: CheckContext,
        private val rule: RegexRule
    ) : IChecker {
        override fun priority(): Int {
            return rule.priority
        }

        override fun check(): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!Permissions.isContactsPermissionGranted(ctx)) {
                return null
            }
            logger?.info(
                (ctx.getString(R.string.checking_template) + ": ${rule.summary()}")
                    .formatAnnotated(
                        ctx.getString(R.string.contact_rule).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
            )

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
                        logger?.error(
                            ctx.getString(R.string.blocked_by)
                                .format(ctx.getString(R.string.contact_rule)) + ": ${rule.summary()}"
                        )
                    else
                        logger?.success(
                            ctx.getString(R.string.allowed_by)
                                .format(ctx.getString(R.string.contact_rule)) + ": ${rule.summary()}"
                        )

                    return ByRegexRule(
                        type = if (block) Def.RESULT_BLOCKED_BY_CONTACT_REGEX else Def.RESULT_ALLOWED_BY_CONTACT_REGEX,
                        rule = rule,
                    )
                }
            }
            return null
        }
    }

    // The regex flag `Contact Group`, it matches the contact group name instead of the phone number
    private class ContactGroup(
        private val ctx: Context,
        private val cCtx: CheckContext,
        private val rule: RegexRule
    ) : IChecker {
        override fun priority(): Int {
            return rule.priority
        }

        override fun check(): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!Permissions.isContactsPermissionGranted(ctx)) {
                return null
            }

            logger?.info(
                (ctx.getString(R.string.checking_template)+ ": ${rule.summary()}")
                    .formatAnnotated(
                        ctx.getString(R.string.contact_group).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
            )

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
                        logger?.error(
                            ctx.getString(R.string.blocked_by)
                                .format(ctx.getString(R.string.contact_group)) + ": ${rule.summary()}"
                        )
                    else
                        logger?.success(
                            ctx.getString(R.string.allowed_by)
                                .format(ctx.getString(R.string.contact_group)) + ": ${rule.summary()}"
                        )

                    return ByRegexRule(
                        type = if (block) Def.RESULT_BLOCKED_BY_CONTACT_GROUP else Def.RESULT_ALLOWED_BY_CONTACT_GROUP,
                        rule = rule,
                    )
                }
            }
            return null
        }
    }

    /*
        Check if text message body matches the SMS Content rule,
        the number is also checked when "for particular number" is enabled
     */
    private class Content(
        private val ctx: Context,
        private val cCtx: CheckContext,
        private val messageBody: String,
        private val rule: RegexRule
    ) : IChecker {
        override fun priority(): Int {
            return rule.priority
        }

        override fun check(): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            logger?.info(
                (ctx.getString(R.string.checking_template)+ ": ${rule.summary()}")
                    .formatAnnotated(
                        ctx.getString(R.string.content_rule).A(SkyBlue),
                        priority().toString().A(LightMagenta)
                    )
            )

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
                    logger?.error(
                        ctx.getString(R.string.blocked_by)
                            .format(ctx.getString(R.string.content_rule)) + ": ${rule.summary()}"
                    )
                else
                    logger?.success(
                        ctx.getString(R.string.allowed_by)
                            .format(ctx.getString(R.string.content_rule)) + ": ${rule.summary()}"
                    )

                return ByRegexRule(
                    type = if (block) Def.RESULT_BLOCKED_BY_CONTENT else Def.RESULT_ALLOWED_BY_CONTENT,
                    rule = rule,
                )
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
        ): ICheckResult {
            val cCtx = CheckContext(
                rawNumber = rawNumber,
                callDetails = callDetails,
                logger = logger,
            )
            val checkers = arrayListOf(
                Emergency(ctx, cCtx),
                STIR(ctx, cCtx),
                SpamDB(ctx, cCtx),
                Contact(ctx, cCtx),
                RepeatedCall(ctx, cCtx),
                Dialed(ctx, cCtx),
                RecentApp(ctx, cCtx),
                MeetingMode(ctx, cCtx),
                OffTime(ctx, cCtx),
                InstantQuery(ctx, cCtx),
            )

            //  add number rules to checkers
            val rules = NumberRuleTable().listRules(ctx, Def.FLAG_FOR_CALL)
            checkers += rules.map {
                val forContact = it.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT)
                val forContactGroup = it.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT_GROUP)
                if (forContact)
                    RegexContact(ctx, cCtx, it)
                else if (forContactGroup)
                    ContactGroup(ctx, cCtx, it)
                else
                    Number(ctx, cCtx, it)
            }

            // sort by priority desc
            checkers.sortByDescending {
                it.priority()
            }

            // try all checkers in order, until a match is found
            var result: ICheckResult? = null
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
            return ByDefault()
        }

        fun checkSms(
            ctx: Context,
            logger: ILogger?,
            rawNumber: String,
            messageBody: String
        ): ICheckResult {

            val cCtx = CheckContext(
                rawNumber = rawNumber,
                logger = logger,
            )
            val checkers = arrayListOf<IChecker>(
                Contact(ctx, cCtx),
                SpamDB(ctx, cCtx),
                MeetingMode(ctx, cCtx),
                OffTime(ctx, cCtx)
            )

            //  add number rules to checkers
            val numberFilters = NumberRuleTable().listRules(ctx, Def.FLAG_FOR_SMS)
            checkers += numberFilters.map {
                val forContact = it.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT)
                val forContactGroup = it.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT_GROUP)
                if (forContact)
                    RegexContact(ctx, cCtx, it)
                else if (forContactGroup)
                    ContactGroup(ctx, cCtx, it)
                else
                    Number(ctx, cCtx, it)
            }

            //  add sms content rules to checkers
            val contentFilters = ContentRuleTable().listRules(ctx, 0/* doesn't care */)
            checkers += contentFilters.map {
                Content(ctx, cCtx, messageBody, it)
            }

            // sort by priority desc
            checkers.sortByDescending {
                it.priority()
            }

            // try all checkers in order, until a match is found
            var result: ICheckResult? = null
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
            return ByDefault()
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
    }
}
