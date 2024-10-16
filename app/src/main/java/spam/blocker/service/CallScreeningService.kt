package spam.blocker.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.HistoryRecord
import spam.blocker.def.Def
import spam.blocker.def.Def.HISTORY_TTL_DISABLED
import spam.blocker.ui.NotificationTrampolineActivity
import spam.blocker.ui.theme.Salmon
import spam.blocker.util.Notification
import spam.blocker.util.SharedPref.BlockType
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.SharedPref.HistoryOptions
import spam.blocker.util.SharedPref.Temporary
import spam.blocker.util.Util
import spam.blocker.util.logd
import android.telecom.Call as TelecomCall

fun TelecomCall.Details.getRawNumber():String {
    var rawNumber = ""
    if (handle != null) {
        rawNumber = handle.schemeSpecificPart
    } else if (gatewayInfo?.originalAddress != null){
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

    private fun pass(details: TelecomCall.Details) {
        val builder = CallResponse.Builder()
        respondToCall(details, builder.build())
    }
    private fun reject(details: TelecomCall.Details) {
        val builder = CallResponse.Builder().apply {
            setSkipCallLog(false)
            setSkipNotification(true)
            setDisallowCall(true)

            setRejectCall(true)
        }
        respondToCall(details, builder.build())
    }
    private fun silence(details: TelecomCall.Details) {
        val builder = CallResponse.Builder().apply {
            setSkipCallLog(false)
            setSkipNotification(true)
            setDisallowCall(true)

            setSilenceCall(true)
        }
        respondToCall(details, builder.build())
    }
    private fun answerThenHangUp(rawNumber: String, details: TelecomCall.Details) {
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

    override fun onScreenCall(details: TelecomCall.Details) {
        if (details.callDirection != TelecomCall.Details.DIRECTION_INCOMING)
            return

        if (!Global(this).isGloballyEnabled() || !Global(this).isCallEnabled()) {
            pass(details)
            return
        }

        val rawNumber = details.getRawNumber()

        val r = processCall(this, rawNumber, details)

        if (r.shouldBlock) {
            val blockType = getBlockType(r) // reject / silence / answer+hangup

            when(blockType) {
                Def.BLOCK_TYPE_SILENCE -> silence(details)
                Def.BLOCK_TYPE_ANSWER_AND_HANGUP -> answerThenHangUp(rawNumber, details)
                else -> reject(details)
            }
        } else {
            pass(details)
        }
    }

    // If it's blocked by regex rule, return `rule.blockType`
    // otherwise return the global setting
    private fun getBlockType(r: CheckResult): Int {
        return if (r.byRule != null) { // per rule setting
            r.byRule!!.blockType
        } else { // global setting
            BlockType(this).getType()
        }
    }

    fun processCall(ctx: Context, rawNumber: String, callDetails: TelecomCall.Details? = null) : CheckResult {
        // 0. check the number with all rules, get the result
        val r = Checker.checkCall(ctx, rawNumber, callDetails)

        // 1. log to db
        val isLogEnabled = HistoryOptions(ctx).getTTL() != HISTORY_TTL_DISABLED
        val recordId = if (isLogEnabled) {
            CallTable().addNewRecord(ctx, HistoryRecord(
                peer = rawNumber,
                time = System.currentTimeMillis(),
                result = r.result,
                reason = r.reason(),
            ))
        } else 0

        // 2. broadcast the call to add a new item in history page
        if (isLogEnabled) {
            Events.onNewCall.fire(recordId)
        }

        // 3. show notification
        if (r.shouldBlock) {

            logd(String.format("Reject call %s", rawNumber))

            val importance = if (r.result == Def.RESULT_BLOCKED_BY_NUMBER)
                r.byRule!!.importance // use per rule notification type
            else
                Def.DEF_SPAM_IMPORTANCE

            // click the notification to launch this app
            val intent = Intent(ctx, NotificationTrampolineActivity::class.java).apply {
                putExtra("type", "call")
                putExtra("blocked", true)
            }.setAction("action_call")

            val toCopy = Checker.checkQuickCopy(
                ctx, rawNumber, null, true, true)
            Notification.show(ctx, R.drawable.ic_call_blocked,
                rawNumber,
                Checker.resultStr(ctx, r.result, r.reason()),
                importance, Salmon, intent,
                toCopy = toCopy)
        }

        return r
    }

}
