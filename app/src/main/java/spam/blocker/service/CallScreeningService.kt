package spam.blocker.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.Call.Details
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.HistoryRecord
import spam.blocker.def.Def
import spam.blocker.def.Def.HISTORY_TTL_DISABLED
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.ICheckResult
import spam.blocker.service.reporting.reportSpam
import spam.blocker.ui.NotificationTrampolineActivity
import spam.blocker.ui.theme.Salmon
import spam.blocker.util.Contacts
import spam.blocker.util.ILogger
import spam.blocker.util.Notification
import spam.blocker.util.Util
import spam.blocker.util.logd
import spam.blocker.util.spf

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
        spf.Temporary(this).setLastCallToBlock(
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

        if (!spf.Global(this).isGloballyEnabled() || !spf.Global(this).isCallEnabled()) {
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
        val isDbLogEnabled = spf.HistoryOptions(ctx).getTTL() != HISTORY_TTL_DISABLED
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

        val toCopy = Checker.checkQuickCopy(
            ctx, rawNumber, null, true, true
        )

        Notification.show(
            ctx,
            R.drawable.ic_call_blocked,
            title = Contacts.findContactByRawNumber(ctx, rawNumber)?.name ?: rawNumber,
            body = r.resultReasonStr(ctx),
            importance = r.getSpamImportance(isCall = true),
            color = Salmon,
            intent = intent,
            toCopy = toCopy
        )
    }

    fun processCall(
        ctx: Context,
        logger: ILogger?, // for showing detailed steps to logcat or for testing purpose
        rawNumber: String,
        callDetails: Details? = null, // it's null when testing
    ): ICheckResult {
        // 0. check the number with all rules, get the result
        val r = Checker.checkCall(ctx, logger, rawNumber, callDetails)

        // 1. log result to db
        logToDb(ctx, r, rawNumber)

        if (r.shouldBlock()) {
            logd(String.format("Reject call %s", rawNumber))

            CoroutineScope(IO).launch {

                // 2. Show notification
                showSpamNotification(ctx, r, rawNumber)

                // 3. Report spam number
                reportSpam(ctx, r, rawNumber, isTesting = callDetails == null)
            }
        }

        return r
    }
}
