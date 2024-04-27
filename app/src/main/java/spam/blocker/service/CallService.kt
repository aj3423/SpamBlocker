package spam.blocker.service

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_NO_CREATE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.Call as TelecomCall
import android.telecom.CallScreeningService
import android.util.Log
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.Record
import spam.blocker.def.Def
import spam.blocker.ui.main.MainActivity
import spam.blocker.util.Notification
import spam.blocker.util.Notification.Companion.GROUP_SPAM_CALL
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
                val intent = Intent(ctx, NotificationOnClickReceiver::class.java).apply {
                    putExtra("type", "call")
                    putExtra("blocked", true)
                }.setAction("action_call")

                val pendingIntent = PendingIntent.getBroadcast(
                    ctx, 0, intent, FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val notificationId = System.currentTimeMillis().toInt()
                Notification.show(ctx, notificationId, ctx.resources.getString(R.string.spam_call_blocked), phone, importance, pendingIntent)
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
