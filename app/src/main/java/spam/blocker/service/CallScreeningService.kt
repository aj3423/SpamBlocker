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
import spam.blocker.util.SharedPref.BlockType
import spam.blocker.util.SharedPref.Global

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
    private fun pick_then_hang(details: TelecomCall.Details) {
    }
    override fun onScreenCall(details: TelecomCall.Details) {

        if (details.callDirection != TelecomCall.Details.DIRECTION_INCOMING)
            return

        val rawNumber = details.handle.schemeSpecificPart

        Log.d(Def.TAG, String.format("new call from: $rawNumber"))

        if (!Global(this).isGloballyEnabled()) {
            pass(details)
            return
        }

        val r = processCall(this, rawNumber, details)

        if (r.shouldBlock) {
            val blockType = getBlockType(r)

            when(blockType) {
                Def.BLOCK_TYPE_SILENCE -> silence(details)
                Def.BLOCK_TYPE_ANSWER_AND_HANG -> {
                    Global(this).writeLong(Def.LAST_CALLED_TIME, System.currentTimeMillis())
                    pass(details) // let it ring, it will be handled in CallStateReceiver
                }
                else -> reject(details)
            }
        } else {
            pass(details)
        }
    }
    private fun getBlockType(r: CheckResult): Int {
        return if (r.byFilter != null) { // per rule setting
            r.byFilter!!.blockType
        } else { // global setting
            BlockType(this).getType()
        }
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
