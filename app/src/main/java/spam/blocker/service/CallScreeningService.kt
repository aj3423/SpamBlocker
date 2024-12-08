package spam.blocker.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.Call.Details
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import spam.blocker.BuildConfig
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.HistoryRecord
import spam.blocker.def.Def
import spam.blocker.def.Def.HISTORY_TTL_DISABLED
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTENT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NON_CONTACT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NUMBER
import spam.blocker.def.Def.RESULT_BLOCKED_BY_STIR
import spam.blocker.service.bot.Delay
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.ReportNumber
import spam.blocker.service.bot.Time
import spam.blocker.service.bot.serialize
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.ICheckResult
import spam.blocker.ui.NotificationTrampolineActivity
import spam.blocker.ui.theme.Salmon
import spam.blocker.util.Contacts
import spam.blocker.util.ILogger
import spam.blocker.util.Notification
import spam.blocker.util.Permissions
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.SharedPref.HistoryOptions
import spam.blocker.util.SharedPref.Temporary
import spam.blocker.util.Util
import spam.blocker.util.logd
import java.util.UUID

fun Details.getRawNumber(): String {
    var rawNumber = ""
    if (handle != null) {
        rawNumber = handle.schemeSpecificPart
    } else if (gatewayInfo?.originalAddress != null) {
        rawNumber = gatewayInfo?.originalAddress?.schemeSpecificPart!!
    } else if (intentExtras != null) {
        var uri = intentExtras.getParcelable<Uri>(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS)
        if (uri == null) {
            uri = intentExtras.getParcelable<Uri>(TelephonyManager.EXTRA_INCOMING_NUMBER);
        }
        if (uri != null) {
            rawNumber = uri.schemeSpecificPart
        }
    }
    return rawNumber
}

class CallScreeningService : CallScreeningService() {

    private fun pass(details: Details) {
        val builder = CallResponse.Builder()
        respondToCall(details, builder.build())
    }

    private fun reject(details: Details) {
        val builder = CallResponse.Builder().apply {
            setSkipCallLog(false)
            setSkipNotification(true)
            setDisallowCall(true)

            setRejectCall(true)
        }
        respondToCall(details, builder.build())
    }

    private fun silence(details: Details) {
        val builder = CallResponse.Builder().apply {
            setSkipCallLog(false)
            setSkipNotification(true)
            setDisallowCall(true)

            setSilenceCall(true)
        }
        respondToCall(details, builder.build())
    }

    private fun answerThenHangUp(rawNumber: String, details: Details) {
        // save number and time to shared pref, it will be read soon in CallStateReceiver
        Temporary(this).setLastCallToBlock(
            Util.clearNumber(rawNumber),
            System.currentTimeMillis()
        )

        // let it ring silently in the background, it will be answered in the CallStateReceiver immediately
        val builder = CallResponse.Builder().apply {
            setSkipCallLog(false)
            setSilenceCall(true)
        }
        respondToCall(details, builder.build())
    }

    override fun onScreenCall(details: Details) {
        if (details.callDirection != Details.DIRECTION_INCOMING)
            return

        if (!Global(this).isGloballyEnabled() || !Global(this).isCallEnabled()) {
            pass(details)
            return
        }

        val rawNumber = details.getRawNumber()

        val r = processCall(this, null, rawNumber, details)

        if (r.shouldBlock()) {
            val blockType = r.getBlockType(this) // reject / silence / answer+hangup

            when (blockType) {
                Def.BLOCK_TYPE_SILENCE -> silence(details)
                Def.BLOCK_TYPE_ANSWER_AND_HANGUP -> answerThenHangUp(rawNumber, details)
                else -> reject(details)
            }
        } else {
            pass(details)
        }
    }

    private fun logToDb(ctx: Context, r: ICheckResult, rawNumber: String) {
        val isDbLogEnabled = HistoryOptions(ctx).getTTL() != HISTORY_TTL_DISABLED
        val recordId = if (isDbLogEnabled) {
            CallTable().addNewRecord(
                ctx, HistoryRecord(
                    peer = rawNumber,
                    time = System.currentTimeMillis(),
                    result = r.type,
                    reason = r.reasonToDb(),
                )
            )
        } else 0

        // broadcast the call to add a new item in history page
        if (isDbLogEnabled) {
            Events.onNewCall.fire(recordId)
        }
    }

    private fun showSpamNotification(ctx: Context, r: ICheckResult, rawNumber: String) {
        // click the notification to launch this app
        val intent = Intent(ctx, NotificationTrampolineActivity::class.java).apply {
            putExtra("type", "call")
            putExtra("blocked", true)
        }.setAction("action_call")

        val toCopy = Checker.Companion.checkQuickCopy(
            ctx, rawNumber, null, true, true
        )

        Notification.show(
            ctx,
            R.drawable.ic_call_blocked,
            title = Contacts.findContactByRawNumber(ctx, rawNumber)?.name ?: rawNumber,
            body = r.resultSummary(ctx),
            importance = r.getSpamImportance(isCall = true),
            color = Salmon,
            intent = intent,
            toCopy = toCopy
        )
    }

    private fun reportNumber(ctx: Context, r: ICheckResult, rawNumber: String, callDetails: Details?) {
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
        val isTesting = callDetails == null
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
                        rawNumber = rawNumber
                    )
                ).serialize(),
                workTag = UUID.randomUUID().toString(),
            )
        }
    }

    fun processCall(
        ctx: Context,
        logger: ILogger?, // for showing detailed steps to logcat or for testing purpose
        rawNumber: String,
        callDetails: Details? = null, // it's null when testing
    ): ICheckResult {
        // 0. check the number with all rules, get the result
        val r = Checker.checkCall(ctx, logger, rawNumber, callDetails)

        // 1. log to db
        logToDb(ctx, r, rawNumber)

        if (r.shouldBlock()) {
            logd(String.format("Reject call %s", rawNumber))

            // 2. Show notification
            showSpamNotification(ctx, r, rawNumber)

            // 3. Report spam number
            reportNumber(ctx, r, rawNumber, callDetails)
        }

        return r
    }
}
