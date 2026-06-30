package spam.blocker.service.reporting

import android.content.Context
import android.provider.CallLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.BuildConfig
import spam.blocker.G
import spam.blocker.db.IApi
import spam.blocker.db.ImportDbReason
import spam.blocker.db.ReportApi
import spam.blocker.db.SpamTable
import spam.blocker.def.Def
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NON_CONTACT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NUMBER_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_SPAM_DB
import spam.blocker.def.Def.RESULT_BLOCKED_BY_STIR
import spam.blocker.service.bot.ActionContext
import spam.blocker.service.bot.Delay
import spam.blocker.service.bot.HttpRequest
import spam.blocker.service.bot.InterceptCall
import spam.blocker.service.bot.InterceptSms
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.ScheduledAutoReportNumber
import spam.blocker.service.bot.Time
import spam.blocker.service.bot.executeAll
import spam.blocker.service.bot.serialize
import spam.blocker.service.checker.BySpamDb
import spam.blocker.service.checker.ICheckResult
import spam.blocker.ui.setting.api.tagOther
import spam.blocker.util.AdbLogger
import spam.blocker.util.Contacts
import spam.blocker.util.Permission
import spam.blocker.util.PhoneNumber
import spam.blocker.util.Util.domainFromUrl
import spam.blocker.util.Util.getHistoryCallsByNumber
import spam.blocker.util.logi
import java.util.UUID


// ------------ For SMS --------------
fun reportSMS(
    ctx: Context,
    r: ICheckResult,
    rawNumber: String,
    smsContent: String,
) {
    val scope = CoroutineScope(IO)
    val apis = G.apiReportVM.table.listAll(ctx)
        .filter {
            it.enabled &&
                    it.actions.firstOrNull() is InterceptSms
        }

    apis.forEach { api ->
        scope.launch {
            val aCtx = ActionContext(
                scope = scope,
                logger = AdbLogger(),
                rawNumber = rawNumber,
                smsContent = smsContent,
                tagCategoryValue = tagOther,
            )
            api.actions.executeAll(ctx, aCtx)
        }
    }
}

// ------------ For Call --------------
fun autoReportSpamCall(
    ctx: Context,
    r: ICheckResult,
    rawNumber: String,
    isTest: Boolean,
) {
    if (shouldReportImmediately(r)) {
        reportImmediately(ctx, r, rawNumber)
    } else {
        scheduleReporting(ctx, r, rawNumber, isTest)
    }
}

private fun shouldReportImmediately(
    r: ICheckResult,
) : Boolean {
    return r.type == RESULT_BLOCKED_BY_SPAM_DB
}

//  Report immediately if it's blocked by db and the number is originally blocked by API
private fun reportImmediately(
    ctx: Context,
    r: ICheckResult,
    rawNumber: String,
) {
    // 0. if blocked by API or spam db that originally blocked by API
    val domain: String? = when(r.type) {
        RESULT_BLOCKED_BY_SPAM_DB -> {
            // Only report if it originally blocked by API query, such as:
            //   ApiQuery -> block -> AddToSpamDb
            // When adding to Spam db, the url domain will be saved as the `importReasonExtra`
            val record = SpamTable.findByNumber(ctx, (r as BySpamDb).matchedNumber)
            if (record?.importReason == ImportDbReason.ByAPI) {
                record.importReasonExtra // the url domain
            } else {
                null
            }
        }
        else -> null
    }
    // Report if `domain` exists
    if (domain != null) {
        val scope = CoroutineScope(IO)
        val apis = listReportableAPIs(
            ctx, rawNumber = rawNumber, domainFilter = listOf(domain), blockReason = r.type, isDbApi = true
        )
        apis.forEach { api ->
            scope.launch {
                val aCtx = ActionContext(
                    scope = scope,
                    logger = AdbLogger(),
                    rawNumber = rawNumber,
                    tagCategoryValue = tagOther,
                )
                api.actions.executeAll(ctx, aCtx)
            }
        }
    }
}

// Schedule a report task if it's blocked by local filters
private fun scheduleReporting(
    ctx: Context,
    r: ICheckResult,
    rawNumber: String,
    isTesting: Boolean,
) {
    // 1. Skip if no reporting api is enabled
    val anyApiEnabled = G.apiReportVM.table.listAll(ctx).any {
        it.enabled && (it as ReportApi).enabledForBlockReason(r.type)
    }
    if (!anyApiEnabled)
        return

    // 2. Skip if it isn't blocked by local filters
    val isBlockedByLocalFilter = when(r.type) {
        RESULT_BLOCKED_BY_NON_CONTACT, RESULT_BLOCKED_BY_STIR, RESULT_BLOCKED_BY_NUMBER_REGEX -> true
        else -> false
    }
    if (!isBlockedByLocalFilter)
        return

    // 3. Skip if call log permission is disabled, it's necessary for checking
    //  if the call is repeated or allowed later.
    val canReadCalls = Permission.callLog.isGranted
    if (!canReadCalls)
        return

    // 4. Skip for global testing, unless developing
    val isDev = BuildConfig.DEBUG // developing
    if (isDev || !isTesting) {
        MyWorkManager.schedule(
            ctx,
            scheduleConfig = Delay(
                if (isDev)
                    Time(min = 1) // debug: 1 min
                else
                    Time(hour = Def.NUMBER_REPORTING_BUFFER_HOURS.toInt()) // release: 1 hour
            ).serialize(),
            actionsConfig = listOf(
                ScheduledAutoReportNumber(
                    rawNumber = rawNumber,
                    asTagCategory = tagOther,
                    blockReason = r.type
                )
            ).serialize(),
            workTag = UUID.randomUUID().toString(),
        )
    }
}


private fun isNumberAllowedLater(ctx: Context, rawNumber: String) : Boolean {
    val phoneNumber = PhoneNumber(ctx, rawNumber)
    val incoming = getHistoryCallsByNumber(
        ctx, phoneNumber, Def.DIRECTION_INCOMING, Def.NUMBER_REPORTING_BUFFER_HOURS * 3600 * 1000
    )
    val outgoing = getHistoryCallsByNumber(
        ctx, phoneNumber, Def.DIRECTION_OUTGOING, Def.NUMBER_REPORTING_BUFFER_HOURS * 3600 * 1000
    )
    val isAllowedLater = (incoming + outgoing).any {
        listOf(
            CallLog.Calls.INCOMING_TYPE,
            CallLog.Calls.OUTGOING_TYPE,
            CallLog.Calls.MISSED_TYPE,
        ).contains(it.type)
    }
    return isAllowedLater
}

// It checks:
// 0. if the number has repeated later
// 1. if the api is enabled
// 2. remove duplicated apis
// 3. filter by domain(for reporting to a specific api after query or blocked by SpamDB)
fun listReportableAPIs(
    ctx: Context,
    rawNumber: String,
    domainFilter: List<String>?,
    blockReason: Int?, // null for
    isManualReport: Boolean = false,
    isDbApi: Boolean = false, // if the number is blocked by DB and was auto added by API query
): List<IApi> {
    if (!isManualReport) {
        // 1. check if the number is repeated or dialed
        //  (DO NOT put this to any other places, it must be checked HERE before further execution)
        val canReadCallLog = Permission.callLog.isGranted
        if (!canReadCallLog)
            return listOf()

        if (isNumberAllowedLater(ctx, rawNumber)) {
            logi("skip reporting repeated/dialed number: $rawNumber")
            return listOf()
        }

        // 2. check if the number has been added to contact
        if (Contacts.findContactByRawNumber(ctx, rawNumber) != null) {
            logi("skip reporting contact number: $rawNumber")
            return listOf()
        }
    }

    // 2. List all enabled APIs
    var apis = G.apiReportVM.table.listAll(ctx)
        .filter { it.enabled }
        .filter { it.actions.firstOrNull() is InterceptCall }
        .filter { // it must contain at least 1 HttpRequest
            val https = it.actions.filterIsInstance<HttpRequest>()
            https.isNotEmpty()
        }

    // 3. When auto-reporting, remove APIs that disabled this blockReason.
    if (!isManualReport) {
        if (!isDbApi) { // if isDbApi, no need to filter by blockReason
            apis = apis.filter {
                (it as ReportApi).enabledForBlockReason(blockReason!!)
            }
        }
    }

    // 4. Remove duplicated APIs that have same domain name
    //  (user might have added multiple instances)
    apis = apis.distinctBy {
        val http = it.actions.find { it is HttpRequest }
        val url = (http as HttpRequest).url
        val domain = domainFromUrl(url)
        domain
    }

    // 5. Remove api that doesn't match the domain filter
    if (domainFilter != null) {
        apis = apis.filter {
            val http = it.actions.find { it is HttpRequest }
            val url = (http as HttpRequest).url
            val domain = domainFromUrl(url)
            domainFilter.contains(domain)
        }
    }

    return apis
}
