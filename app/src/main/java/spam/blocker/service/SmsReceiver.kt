package spam.blocker.service

import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import spam.blocker.R
import spam.blocker.db.HistoryRecord
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.ui.theme.Salmon
import spam.blocker.util.Contacts
import spam.blocker.util.Notification
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.logd

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context?, intent: Intent?) {
        logd("onReceive in SmsReceiver")
        if (!Global(ctx!!).isGloballyEnabled() || !Global(ctx).isSmsEnabled()) {
            return
        }
        val action = intent?.action
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        // A single long message can be split into multiple parts due to character
        // limitations of SMS messages. These parts are then reassembled by the
        // phone and delivered together.
        val messageBody = messages.fold("") { acc, it -> acc + it.messageBody }
        val rawNumber = messages[0].originatingAddress!!
        logd("onReceive sms from $rawNumber: $messageBody")

        processSms(ctx, rawNumber, messageBody)
    }

    fun processSms(ctx: Context, rawNumber: String, messageBody: String) : CheckResult {
        val spf = Global(ctx)

        val r = Checker.checkSms(ctx, rawNumber, messageBody)

        // 1. log to db
        val rec = HistoryRecord(
            peer = rawNumber,
            time = System.currentTimeMillis(),
            result = r.result,
            reason = r.reason(),
            smsContent = if (spf.isLogSmsContentEnabled()) messageBody else null
        )
        val id = SmsTable().addNewRecord(ctx, rec)

        val showName = Contacts.findByRawNumber(ctx, rawNumber)?.name ?: rawNumber

        if (r.shouldBlock) {
            var importance = NotificationManager.IMPORTANCE_LOW // default: LOW

            if (r.result == Def.RESULT_BLOCKED_BY_NUMBER || r.result == Def.RESULT_BLOCKED_BY_CONTENT) {
                importance = r.byRule!!.importance // use per rule notification type
            }

            val intent = Intent(ctx, NotificationTrampolineActivity::class.java).apply {
                putExtra("type", "sms")
                putExtra("blocked", true)
            }.setAction("action_sms_block")

            val toCopy = Checker.checkQuickCopy(
                ctx, rawNumber, messageBody, false, true)

            Notification.show(ctx, R.drawable.ic_sms_blocked,
                showName, messageBody,
                importance, Salmon, intent,
                toCopy = toCopy)

        } else { // passed

            // handle clicking of the notification body:
            //  - launch sms app, open conversation with that number
            //  - cancel all notifications
            val intent = Intent(ctx, NotificationTrampolineActivity::class.java).apply {
                putExtra("type", "sms")
                putExtra("blocked", false)
                putExtra("rawNumber", rawNumber)
            }.setAction("action_sms_non_block")

            val toCopy = Checker.checkQuickCopy(
                ctx, rawNumber, messageBody, false, false)

            Notification.show(ctx, R.drawable.ic_sms_pass,
                showName, messageBody,
                IMPORTANCE_HIGH, null, intent,
                toCopy = toCopy
            )
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