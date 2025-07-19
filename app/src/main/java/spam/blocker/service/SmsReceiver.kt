package spam.blocker.service

import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import spam.blocker.Events
import spam.blocker.db.HistoryRecord
import spam.blocker.db.SmsTable
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.ICheckResult
import spam.blocker.ui.NotificationTrampolineActivity
import spam.blocker.util.Contacts
import spam.blocker.util.ILogger
import spam.blocker.util.Notification
import spam.blocker.util.Notification.IMPORTANCE_HIGH_MUTED
import spam.blocker.util.Notification.Type
import spam.blocker.util.Now
import spam.blocker.util.Util.isDeviceLocked
import spam.blocker.util.Util.isSmsAppInForeground
import spam.blocker.util.logi
import spam.blocker.util.regexMatches
import spam.blocker.util.spf


open class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        logi("Received SMS")

        if (!spf.Global(ctx).isGloballyEnabled() || !spf.Global(ctx).isSmsEnabled()) {
            return
        }
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        // A single long message can be split into multiple parts due to character
        // limitations of SMS messages. These parts are then reassembled by the
        // phone and delivered together.
        val messageBody = messages.fold("") { acc, it -> acc + it.messageBody }
        val rawNumber = messages[0].originatingAddress!!

        processSms(ctx, logger = null, rawNumber, messageBody)
    }

    fun processSms(
        ctx: Context,
        logger: ILogger?,
        rawNumber: String,
        messageBody: String
    ): ICheckResult {

        val r = Checker.checkSms(ctx, logger, rawNumber, messageBody)

        run {
            // 1. log to history db
            val spf = spf.HistoryOptions(ctx)
            val isLogEnabled = spf.isLoggingEnabled()
            val recordId = if (isLogEnabled)
                SmsTable().addNewRecord(
                    ctx, HistoryRecord(
                        peer = rawNumber,
                        time = System.currentTimeMillis(),
                        result = r.type,
                        reason = r.reasonToDb(),
                        extraInfo = if (spf.isLogSmsContentEnabled()) messageBody else null,
                    )
                ) else 0

            // 2. broadcast new sms to add a new item in history page
            if (isLogEnabled) {
                Events.onNewSMS.fire(recordId)
            }
        }

        // 3. update SmsAlert timestamp to SharedPref if it's enabled
        run {
            val spf = spf.SmsAlert(ctx)
            val regex = spf.getRegexStr()
            if (spf.isEnabled()) {
                val flags = spf.getRegexFlags()
                val matches = regex.regexMatches(messageBody, flags)
                if (matches) {
                    spf.setTimestamp(Now.currentMillis())
                }
            }
        }


        // 4. show notification
        val showName = Contacts.findContactByRawNumber(ctx, rawNumber)?.name ?: rawNumber

        if (r.shouldBlock()) {

            val intent = Intent(ctx, NotificationTrampolineActivity::class.java).apply {
                putExtra("type", "sms")
                putExtra("blocked", true)
            }.setAction("action_sms_block")

            val toCopy = Checker.checkQuickCopy(
                ctx, rawNumber, messageBody, false, true
            )

            Notification.show(
                ctx,
                type = Type.SPAM_SMS,
                title = showName,
                body = messageBody,
                importance = r.getSpamImportance(isCall = false),
                intent = intent,
                toCopy = toCopy,
            )

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
                ctx, rawNumber, messageBody, false, false
            )

            Notification.show(
                ctx,
                type = Type.VALID_SMS,
                title = showName,
                body = messageBody,
                importance = if (!isDeviceLocked(ctx) && isSmsAppInForeground(ctx))
                    IMPORTANCE_HIGH_MUTED
                else
                    IMPORTANCE_HIGH,
                intent = intent,
                toCopy = toCopy,
            )
        }

        return r
    }
}