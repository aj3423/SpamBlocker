package spam.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import spam.blocker.Events
import spam.blocker.db.HistoryRecord
import spam.blocker.db.Notification.ChannelTable
import spam.blocker.db.SmsTable
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.ICheckResult
import spam.blocker.ui.NotificationTrampolineActivity
import spam.blocker.util.Contacts
import spam.blocker.util.ILogger
import spam.blocker.util.Notification
import spam.blocker.util.Notification.ShowType
import spam.blocker.util.Notification.missingChannel
import spam.blocker.util.Now
import spam.blocker.util.SaveableLogger
import spam.blocker.util.Util.isDeviceLocked
import spam.blocker.util.Util.isSmsAppInForeground
import spam.blocker.util.logi
import spam.blocker.util.regexMatches
import spam.blocker.util.spf


fun getSimSlotFromSmsIntent(ctx: Context, intent: Intent?) : Int? {
    // https://stackoverflow.com/questions/35968766/how-to-figure-out-which-sim-received-sms-in-dual-sim-android-device
    try {
        val bundle = intent?.extras

        var slot: Int? = null
        listOf("slot", "simSlot", "simId", "slot_id", "simnum", "phone", "slotId", "slotIdx", "android.telephony.extra.SLOT_INDEX")
            .first {
                slot = bundle?.getInt(it, -1)
                slot != -1
            }

        return slot
    } catch (e: SecurityException) { // when permission not granted
        return null
    }
}

open class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        logi("Received SMS")

        // Only handle incoming messages
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val spf = spf.Global(ctx)

        if (!spf.isGloballyEnabled) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        // A single long message can be split into multiple parts due to character
        // limitations of SMS messages. These parts are then reassembled by the
        // phone and delivered together.
        val messageBody = messages.fold("") { acc, it -> acc + it.messageBody }

        // When SMS screening is not enabled, only check if it matches SMS Alert
        if (!spf.isSmsEnabled) {
            this.checkSmsAlert(ctx, messageBody)
            return
        }

        val rawNumber = messages[0].originatingAddress!!

        val simSlot = getSimSlotFromSmsIntent(ctx, intent)

        processSms(ctx, rawNumber = rawNumber, messageBody = messageBody, simSlot = simSlot,
            isTest = false, logger = SaveableLogger())
    }

    // Update SmsAlert timestamp in SharedPref if it's enabled
    fun checkSmsAlert(ctx: Context, messageBody: String) {
        val spf = spf.SmsAlert(ctx)
        val regex = spf.regexStr
        if (spf.isEnabled) {
            val flags = spf.regexFlags
            val matches = regex.regexMatches(messageBody, flags)
            if (matches) {
                spf.timestamp = Now.currentMillis()
            }
        }
    }

    fun processSms(
        ctx: Context,
        rawNumber: String,
        messageBody: String,
        simSlot: Int?,
        isTest: Boolean,
        logger: ILogger? = null,
    ): ICheckResult {
        logi("process Sms")

        val (r, fullScreeningLog, anythingWrong) = Checker.checkSms(
            ctx, rawNumber = rawNumber, messageBody = messageBody, simSlot = simSlot, logger = logger)

        run {
            // 1. log to history db
            val spf = spf.HistoryOptions(ctx)
            val isLogEnabled = spf.isLoggingEnabled
            if (isLogEnabled) {
                val recordId = SmsTable().addNewRecord(
                    ctx, HistoryRecord(
                        peer = rawNumber,
                        time = System.currentTimeMillis(),
                        result = r.type,
                        reason = r.reasonToDb(),
                        simSlot = simSlot,
                        extraInfo = if (spf.isLogSmsContentEnabled) messageBody else null,
                        isTest = isTest,
                        fullScreeningLog = fullScreeningLog,
                        anythingWrong = anythingWrong
                    )
                )

                // 2. broadcast new sms to add a new item in history page
                Events.onNewSMS.fire(recordId)
            }
        }

        // 3. Update SmsAlert timestamp, it's necessary to check it here, so testing would also work.
        this.checkSmsAlert(ctx, messageBody)

        // 4. show notification
        val showName = Contacts.findContactByRawNumber(ctx, rawNumber)?.name ?: rawNumber

        if (r.shouldBlock()) {

            val intent = Intent(ctx, NotificationTrampolineActivity::class.java).apply {
                putExtra("type", "sms")
                putExtra("blocked", true)
            }.setAction("action_sms_block")

            val toCopy = Checker.checkQuickCopy(
                ctx, rawNumber, messageBody, isCall = false, isBlocked = true
            )

            Notification.show(
                ctx,
                showType = ShowType.SPAM_SMS,
                channel = r.getNotificationChannel(ctx, showType = ShowType.SPAM_SMS),
                title = showName,
                body = messageBody,
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


            // silence it when actively SMS chat
            val isActiveSmsChat = !isDeviceLocked(ctx) && isSmsAppInForeground(ctx)

            Notification.show(
                ctx,
                showType = ShowType.VALID_SMS,
                channel = if (isActiveSmsChat) {
                    val activeChannelId = spf.Notification(ctx).smsChatChannelId

                    ChannelTable.findByChannelId(ctx, activeChannelId)
                        ?: missingChannel()
                } else {
                    r.getNotificationChannel(ctx, ShowType.VALID_SMS)
                },
                title = showName,
                body = messageBody,
                intent = intent,
                toCopy = toCopy,
            )
        }

        return r
    }
}