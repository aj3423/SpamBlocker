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
import spam.blocker.ui.main.MainActivity
import spam.blocker.util.Notification
import spam.blocker.util.SharedPref

class CallService : CallScreeningService() {
    override fun onScreenCall(callDetails: TelecomCall.Details) {
        if (
            callDetails.callDirection != TelecomCall.Details.DIRECTION_INCOMING
        ) return

        val phone = callDetails.handle.schemeSpecificPart

        Log.d(Def.TAG, String.format("new call from: $phone"))

        if (!SharedPref(this).isGloballyEnabled()) {
            return
        }

        val builder = CallResponse.Builder()

        val r = processCall(this, phone)

        if (r.shouldBlock) {
            builder.apply {
                setRejectCall(true)
                setDisallowCall(true)
                setSkipCallLog(false)
            }
        }
        respondToCall(callDetails, builder.build())
    }

    fun processCall(ctx: Context, phone: String) : CheckResult {
        var r = CheckResult(false, Def.RESULT_ALLOWED_BY_DEFAULT)
        try {
            r = SpamChecker.checkCall(ctx, phone)

            // 1. log to db
            val call = Record()
            call.peer = phone
            call.time = System.currentTimeMillis()
            call.result = r.result
            call.reason = r.reason()
            val id = CallTable().addNewRecord(ctx, call)

            // 2. block
            if (r.shouldBlock) {

                Log.d(Def.TAG, String.format("Reject call %s", phone))

                val importance = if (r.result == Def.RESULT_BLOCKED_BLACKLIST)
                    r.byFilter!!.importance
                else
                    Def.DEF_SPAM_IMPORTANCE

                // click the notification to launch this app
                val intent = Intent(ctx, MainActivity::class.java)
                Notification.show(ctx, 0, ctx.resources.getString(R.string.spam_call_blocked), phone, importance, intent)
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
