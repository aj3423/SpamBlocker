package spam.blocker.service.reporting

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.BuildConfig
import spam.blocker.G
import spam.blocker.db.ImportDbReason
import spam.blocker.db.SpamTable
import spam.blocker.db.listReportableAPIs
import spam.blocker.def.Def
import spam.blocker.def.Def.RESULT_BLOCKED_BY_API_QUERY
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTENT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NON_CONTACT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NUMBER
import spam.blocker.def.Def.RESULT_BLOCKED_BY_SPAM_DB
import spam.blocker.def.Def.RESULT_BLOCKED_BY_STIR
import spam.blocker.service.bot.ActionContext
import spam.blocker.service.bot.Delay
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.ReportNumber
import spam.blocker.service.bot.Time
import spam.blocker.service.bot.executeAll
import spam.blocker.service.bot.serialize
import spam.blocker.service.checker.ByApiQuery
import spam.blocker.service.checker.BySpamDb
import spam.blocker.service.checker.ICheckResult
import spam.blocker.ui.setting.api.tagOther
import spam.blocker.util.AdbLogger
import spam.blocker.util.Permissions
import java.util.UUID

fun reportSpam(
    ctx: Context,
    r: ICheckResult,
    rawNumber: String,
    isTesting: Boolean,
) {
    if (shouldReportImmediately(r)) {
        reportImmediately(ctx, r, rawNumber)
    } else {
        scheduleReporting(ctx, r, rawNumber, isTesting)
    }
}

private fun shouldReportImmediately(
    r: ICheckResult,
) : Boolean {
    return r.type in listOf(RESULT_BLOCKED_BY_API_QUERY, RESULT_BLOCKED_BY_SPAM_DB)
}

//  Report immediately if it's blocked by API or SPAM db that originally blocked by API
private fun reportImmediately(
    ctx: Context,
    r: ICheckResult,
    rawNumber: String,
) {
    // 0. if blocked by API or spam db that originally blocked by API
    val domain: String? = when(r.type) {
        RESULT_BLOCKED_BY_API_QUERY -> {
            (r as ByApiQuery).detail.apiDomain
        }
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
            ctx, rawNumber = rawNumber, domainFilter = listOf(domain)
        )
        apis.forEach { api ->
            scope.launch {
                val aCtx = ActionContext(
                    scope = scope,
                    logger = AdbLogger(),
                    rawNumber = rawNumber,
                    tagCategory = tagOther,
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
    val anyApiEnabled = G.apiReportVM.table.listAll(ctx).any { it.enabled }
    if (!anyApiEnabled)
        return

    // 2. Skip if it isn't blocked by local filters
    val isBlockedByLocalFilter = listOf(
        RESULT_BLOCKED_BY_NON_CONTACT, RESULT_BLOCKED_BY_STIR,
        RESULT_BLOCKED_BY_NUMBER, RESULT_BLOCKED_BY_CONTENT,
    ).contains(r.type)
    if (!isBlockedByLocalFilter)
        return

    // 3. Skip if call log permission is disabled, it's necessary for checking
    //  if the call is repeated or allowed later.
    val canReadCalls = Permissions.isCallLogPermissionGranted(ctx)
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
                ReportNumber(
                    rawNumber = rawNumber,
                    asTagCategory = tagOther,
                )
            ).serialize(),
            workTag = UUID.randomUUID().toString(),
        )
    }
}
