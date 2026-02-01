package spam.blocker.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.TELECOM_SERVICE
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import spam.blocker.util.Util
import spam.blocker.util.logi
import spam.blocker.util.spf

class CallStateReceiver : BroadcastReceiver() {
    private var handler: Handler = Handler(Looper.getMainLooper())

    // Don't block calls that are not marked to block in CallScreenService.
    private fun shouldBlock(ctx: Context, currNumber: String): Boolean {
        // `numToBlock` and `lastCalledTime` are set in CallScreeningService,
        //   they are only set when the call is blocked by "answer + hang up"
        val spf = spf.Temporary(ctx)

        // 1. Check if the number matches
        val numToBlock = spf.lastCallToBlock
        if (numToBlock != Util.clearNumber(currNumber)) {
            return false
        }

        // 2. Check if the time since the `lastCalledTime` is less than 5 seconds,
        //   answer the call and hang up
        val lastCalledTime = spf.lastCallTime
        val now = System.currentTimeMillis()
        val tolerance = 5000 // 5 seconds
        return (now - lastCalledTime) < tolerance
    }

    private fun extractNumber(intent: Intent) : String {
        return intent.extras?.getString("incoming_number") ?: ""
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val spf = spf.Temporary(ctx)

        if (intent.action == "android.intent.action.PHONE_STATE") {

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

            when (state) {
                // This is triggered when it starts ringing
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    logi("RINGING")

                    // 1. Blocked by "Answer+HangUp"?
                    val currNumber = extractNumber(intent)

                    val block = shouldBlock(ctx, currNumber)
                    if (block)
                        answerCall(ctx)
                }

                // This is triggered when a call is answered(in call)
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    logi("IN CALL")

                    val currNumber = extractNumber(intent)

                    val block = shouldBlock(ctx, currNumber)

                    if (block) {
                        // Schedule a delayed hang-up task.
                        val delay = spf.hangUpDelay.toLong() * 1000

                        handler.postDelayed({
                            endCall(ctx)
                        }, delay)
                    }
                }

                // This is triggered when any call ends
                TelephonyManager.EXTRA_STATE_IDLE -> {
//                    val currNumber = extractNumber(intent)
                    logi("IDLE")

                    // When the call ends before the hang-up delay, kill the hang-up task,
                    //  to prevent killing any following calls by mistake.
                    handler.removeCallbacksAndMessages(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun answerCall(ctx: Context) {
        val telMgr = ctx.getSystemService(TELECOM_SERVICE) as TelecomManager
        telMgr.acceptRingingCall()
        logi("answer call")
    }
    @SuppressLint("MissingPermission")
    private fun endCall(ctx: Context) {
        val telMgr = ctx.getSystemService(TELECOM_SERVICE) as TelecomManager
        telMgr.endCall()
        logi("end call")
    }
}