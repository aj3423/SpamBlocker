package spam.blocker.service

import android.content.Context
import android.content.Intent
import android.telecom.Call as TelecomCall
import android.telecom.CallScreeningService
import android.util.Log
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.Record
import spam.blocker.def.Def
import spam.blocker.util.Notification
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.SharedPref.Silence

class CallService : CallScreeningService() {
    override fun onScreenCall(callDetails: TelecomCall.Details) {

        if (callDetails.callDirection != TelecomCall.Details.DIRECTION_INCOMING)
            return

        val rawNumber = callDetails.handle.schemeSpecificPart

        Log.d(Def.TAG, String.format("new call from: $rawNumber"))

        val builder = CallResponse.Builder()

        if (!Global(this).isGloballyEnabled()) {
            respondToCall(callDetails, builder.build())
            return
        }


        val r = processCall(this, rawNumber, callDetails)

        if (r.shouldBlock) {
            val silence = r.byFilter?.blockType == Def.BLOCK_TYPE_SILENCE // per rule
                    || Silence(this).isEnabled() // global

            builder.apply {
                setSkipCallLog(false)
                setSkipNotification(true)
                setDisallowCall(true)

                if (silence) {
                    setSilenceCall(true)
                } else {
                    setRejectCall(true)
                }
            }
        }
        respondToCall(callDetails, builder.build())
    }

    fun processCall(ctx: Context, rawNumber: String, callDetails: TelecomCall.Details? = null) : CheckResult {
        var r = CheckResult(false, Def.RESULT_ALLOWED_BY_DEFAULT)
        try {
            r = Checker.checkCall(ctx, rawNumber, callDetails)

            // 1. log to db
            val call = Record()
            call.peer = rawNumber
            call.time = System.currentTimeMillis()
            call.result = r.result
            call.reason = r.reason()
            val id = CallTable().addNewRecord(ctx, call)

            // 2. block
            if (r.shouldBlock) {

                Log.d(Def.TAG, String.format("Reject call %s", rawNumber))

                val importance = if (r.result == Def.RESULT_BLOCKED_BY_NUMBER)
                    r.byFilter!!.importance
                else
                    Def.DEF_SPAM_IMPORTANCE

                // click the notification to launch this app
                val intent = Intent(ctx, NotificationTrampolineActivity::class.java).apply {
                    putExtra("type", "call")
                    putExtra("blocked", true)
                }.setAction("action_call")

                Notification.show(ctx, R.drawable.ic_call_blocked,
                    rawNumber,
                    Checker.reasonStr(ctx, NumberRuleTable(), r.reason()),
                    importance, ctx.resources.getColor(R.color.salmon, null), intent)
            }

            // broadcast new call
            run {
                val intent = Intent(Def.ON_NEW_CALL)
                intent.putExtra("type", "call")
                intent.putExtra("blocked", r.shouldBlock)
                intent.putExtra("record_id", id)

                ctx.sendBroadcast(intent)
            }

        } catch (t: Throwable) {
            Log.w(Def.TAG, t)
        } finally {
        }
        return r
    }

}
