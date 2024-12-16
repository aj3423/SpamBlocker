package spam.blocker.service

import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.serialization.Serializable
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.db.HistoryRecord
import spam.blocker.db.SmsTable
import spam.blocker.def.Def.HISTORY_TTL_DISABLED
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.ICheckResult
import spam.blocker.ui.NotificationTrampolineActivity
import spam.blocker.ui.theme.Salmon
import spam.blocker.util.Contacts
import spam.blocker.util.ILogger
import spam.blocker.util.Notification
import spam.blocker.util.spf

// This will be serialized to a json and saved as the HistoryTable.reason
@Serializable
class SmsRecordInfo(
    val recordId: Long,
    val smsContent: String? = null,
)

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context?, intent: Intent?) {
        if (!spf.Global(ctx!!).isGloballyEnabled() || !spf.Global(ctx).isSmsEnabled()) {
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
        // logd("onReceive sms from $rawNumber: $messageBody")

        processSms(ctx, logger = null, rawNumber, messageBody)
    }

    fun processSms(
        ctx: Context,
        logger: ILogger?,
        rawNumber: String,
        messageBody: String
    ): ICheckResult {
        val spf = spf.HistoryOptions(ctx)

        val r = Checker.checkSms(ctx, logger, rawNumber, messageBody)

        // 1. log to db
        val isLogEnabled = spf.getTTL() != HISTORY_TTL_DISABLED
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
                ctx, R.drawable.ic_sms_blocked,
                title = showName,
                body = messageBody,
                importance = r.getSpamImportance(isCall = false),
                color = Salmon,
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
                ctx, R.drawable.ic_sms_pass,
                title = showName,
                body = messageBody,
                importance = IMPORTANCE_HIGH,
                color = null,
                intent = intent,
                toCopy = toCopy,
            )
        }

        return r
    }
}