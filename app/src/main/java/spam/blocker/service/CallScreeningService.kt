package spam.blocker.service

import android.content.Context
import android.content.Intent
import android.telecom.Call as TelecomCall
import android.telecom.CallScreeningService
import android.util.Log
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.Record
import spam.blocker.def.Def
import spam.blocker.util.Notification
import spam.blocker.util.SharedPref.BlockType
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.SharedPref.Temporary
import spam.blocker.util.Util

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

        val rawNumber = details.handle.schemeSpecificPart

        Log.d(Def.TAG, String.format("new call from: $rawNumber"))

        if (!Global(this).isGloballyEnabled() || !Global(this).isCallEnabled()) {
            pass(details)
            return
        }

        val r = processCall(this, rawNumber, details)

        if (r.shouldBlock) {
            val blockType = getBlockType(r)

            when(blockType) {
                Def.BLOCK_TYPE_SILENCE -> silence(details)
                Def.BLOCK_TYPE_ANSWER_AND_HANG -> answerThenHangUp(rawNumber, details)
                else -> reject(details)
            }
        } else {
            pass(details)
        }
    }
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
        val call = Record().apply {
            peer = rawNumber
            time = System.currentTimeMillis()
            result = r.result
            reason = r.reason()
        }
        val id = CallTable().addNewRecord(ctx, call)

        // 2. show notification
        if (r.shouldBlock) {

            Log.d(Def.TAG, String.format("Reject call %s", rawNumber))

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
                importance, ctx.resources.getColor(R.color.salmon, null), intent,
                toCopy = toCopy)
        }

        // broadcast new call to update UI(add new item to call log)
        run {
            val intent = Intent(Def.ON_NEW_CALL)
            intent.putExtra("type", "call")
            intent.putExtra("blocked", r.shouldBlock)
            intent.putExtra("record_id", id)

            ctx.sendBroadcast(intent)
        }

        return r
    }

}
