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
import spam.blocker.util.SharedPref.Temporary
import spam.blocker.util.Util

class CallStateReceiver : BroadcastReceiver() {

    private fun shouldBlock(ctx: Context, currNumber: String): Boolean {

        // `numToBlock` and `lastCalledTime` are set in the CallScreeningService,
        //   they are only set when the call should be blocked by "answer + hang up"
        val (numToBlock, lastCalledTime) = Temporary(ctx).getLastCallToBlock()

        // if the time since the `lastCalledTime` is less than 1 second,
        //   answer the call and hang up
        val now = System.currentTimeMillis()
        val tolerance = 5000 // 5 second
        return (now - lastCalledTime) < tolerance && numToBlock == Util.clearNumber(currNumber)
    }

    private fun extractNumber(intent: Intent) : String? {
        return intent.extras?.getString("incoming_number")
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == "android.intent.action.PHONE_STATE") {

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

            when (state) {
                // ringing
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    val currNumber = extractNumber(intent) ?: return
                    Log.e(Def.TAG, "RINGING, num: $currNumber")

                    if (shouldBlock(ctx, currNumber))
                        answerCall(ctx)
                }

                // call is active(in call)
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    val currNumber = extractNumber(intent) ?: return
                    Log.e(Def.TAG, "IN CALL, num: $currNumber")

                    if (shouldBlock(ctx, currNumber))
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