package spam.blocker.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.TELECOM_SERVICE
import android.content.Intent
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import spam.blocker.def.Def
import spam.blocker.util.SharedPref.Global

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == "android.intent.action.PHONE_STATE") {

            // The `lastCalledTime` is set in the CallScreeningService,
            //   it's only set when the call should be blocked.
            val spf = Global(ctx)
            val lastCalledTime = spf.readLong(Def.LAST_CALLED_TIME, 0)

            // if the time since the `lastCalledTime` is less than 1 second,
            //   answer the call and hang up
            val now = System.currentTimeMillis()
            val tolerance = 1000 // 1 second
            val shouldBlock = (now - lastCalledTime) < tolerance

            when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                // ringing
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (shouldBlock)
                        answerCall(ctx)
                }

                // call is active(in call)
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    if (shouldBlock)
                        endCall(ctx)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun answerCall(ctx: Context) {
        val telMgr = ctx.getSystemService(TELECOM_SERVICE) as TelecomManager
        telMgr.acceptRingingCall()
        Log.e(Def.TAG, "answer call")
    }
    @SuppressLint("MissingPermission")
    private fun endCall(ctx: Context) {
        val telMgr = ctx.getSystemService(TELECOM_SERVICE) as TelecomManager
        telMgr.endCall()
        Log.e(Def.TAG, "end call")
    }
}