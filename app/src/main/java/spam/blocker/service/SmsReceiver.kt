package spam.blocker.service

import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import spam.blocker.R
import spam.blocker.db.Record
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.util.Contacts
import spam.blocker.util.Notification
import spam.blocker.util.SharedPref

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context?, intent: Intent?) {
        Log.d(Def.TAG, "onReceive in SmsReceiver")
        if (!SharedPref(ctx!!).isGloballyEnabled()) {
            return
        }
        val action = intent?.action
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val msg = messages[0]
        val rawNumber = msg.originatingAddress!!
        val content = msg.messageBody;
        Log.d(Def.TAG, "onReceive sms from $rawNumber: $content")

        processSms(ctx, rawNumber, content)
    }

    fun processSms(ctx: Context, rawNumber: String, content: String) : CheckResult {

        val r = SpamChecker.checkSms(ctx, rawNumber, content)

        // 1. log to db
        val rec = Record()
        rec.peer = rawNumber
        rec.time = System.currentTimeMillis()
        rec.result = r.result
        rec.reason = r.reason()
        val id = SmsTable().addNewRecord(ctx, rec)

        if (r.shouldBlock) {
            var importance = NotificationManager.IMPORTANCE_LOW // default: LOW

            if (r.result == Def.RESULT_BLOCKED_BLACKLIST || r.result == Def.RESULT_BLOCKED_BY_CONTENT) {
                importance = r.byFilter!!.importance
            }

            val intent = Intent(ctx, NotificationTrampolineActivity::class.java).apply {
                putExtra("type", "sms")
                putExtra("blocked", true)
            }.setAction("action_sms_block")

            Notification.show(ctx, ctx.resources.getString(R.string.spam_sms_blocked), rawNumber, importance, intent)
        } else {

            val intent = Intent(ctx, NotificationTrampolineActivity::class.java).apply {
                putExtra("type", "sms")
                putExtra("blocked", false)
                putExtra("phone", rawNumber)
            }.setAction("action_sms_non_block")

            val body = "${Contacts.findByRawNumberAuto(ctx, rawNumber)?.name ?: rawNumber}: $content"

            Notification.show(ctx, ctx.resources.getString(R.string.new_sms_received), body, IMPORTANCE_HIGH, intent)
        }

        // broadcast new sms
        run {
            val intent = Intent(Def.ON_NEW_SMS)
            intent.putExtra("type", "sms")
            intent.putExtra("blocked", r.shouldBlock)
            intent.putExtra("record_id", id)

            ctx.sendBroadcast(intent)
        }
        return r
    }

}