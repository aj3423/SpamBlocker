package spam.blocker.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.Call.Details
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.Events
import spam.blocker.db.BotTable
import spam.blocker.db.CallTable
import spam.blocker.db.HistoryRecord
import spam.blocker.def.Def
import spam.blocker.service.bot.ActionContext
import spam.blocker.service.bot.Ringtone
import spam.blocker.service.bot.executeAll
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.ICheckResult
import spam.blocker.service.reporting.autoReportSpam
import spam.blocker.ui.NotificationTrampolineActivity
import spam.blocker.util.Contacts
import spam.blocker.util.ILogger
import spam.blocker.util.Notification
import spam.blocker.util.Notification.ShowType
import spam.blocker.util.RingtoneUtil
import spam.blocker.util.SimUtils
import spam.blocker.util.Util
import spam.blocker.util.logi
import spam.blocker.util.spf

fun Details.getRawNumber(): String {
    var rawNumber = ""
    if (handle != null) {
        rawNumber = handle.schemeSpecificPart
    } else if (gatewayInfo?.originalAddress != null) {
        rawNumber = gatewayInfo?.originalAddress?.schemeSpecificPart!!
    } else if (intentExtras != null) {
        var uri = intentExtras.getParcelable<Uri>(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS)
        if (uri == null) {
            uri = intentExtras.getParcelable<Uri>(TelephonyManager.EXTRA_INCOMING_NUMBER);
        }
        if (uri != null) {
            rawNumber = uri.schemeSpecificPart
        }
    }

    return rawNumber
}

class CallScreeningService : CallScreeningService() {

    companion object {
        // Save the timestamp for the feature "Emergency"
        fun updateOutgoingEmergencyTimestamp(ctx: Context, rawNumber: String) {
            val spf = spf.EmergencySituation(ctx)
            val extraNumbers = spf.getExtraNumbers()
            if (Util.isEmergencyNumber(ctx, rawNumber) || extraNumbers.contains(rawNumber)) {
                spf.timestamp = System.currentTimeMillis()
            }
        }
    }

    private fun pass(details: Details, shouldMute: Boolean = false) {
        logi("allow call")

        val builder = CallResponse.Builder().apply {
            if (shouldMute)
                setSilenceCall(true)
        }
        respondToCall(details, builder.build())
    }

    private fun reject(details: Details) {
        logi("reject call")

        val builder = CallResponse.Builder().apply {
            setSkipCallLog(false)
            setSkipNotification(true)
            setDisallowCall(true)

            setRejectCall(true)
        }
        respondToCall(details, builder.build())
    }

    private fun silence(details: Details) {
        logi("silence call")

        val builder = CallResponse.Builder().apply {
            setSkipCallLog(false)
            setSkipNotification(true)
            setDisallowCall(true)

//            setSilenceCall(true) // no need
        }
        respondToCall(details, builder.build())
    }

    private fun answerThenHangUp(rawNumber: String, r: ICheckResult, details: Details) {
        logi("hang-up call")

        val ctx = this

        // If a call is ongoing, don't hang-up, Reject instead
        if (Util.isInCall(ctx)) {
            // "Reject" or "Silence" makes no difference here, it just keeps ringing on the peer.
            reject(details)
            return
        }

        val now = System.currentTimeMillis()

        // save 'number/current time/hang up delay' to shared pref, they will be read soon in CallStateReceiver
        spf.Temporary(ctx).apply {
            lastCallToBlock = Util.clearNumber(rawNumber)
            lastCallTime = now
            hangUpDelay = r.hangUpDelay(ctx)
        }

        // let it ring silently in the background, it will be answered in the CallStateReceiver immediately
        val builder = CallResponse.Builder().apply {
            setSkipCallLog(false)
            setSilenceCall(true)
        }
        respondToCall(details, builder.build())
    }

    override fun onScreenCall(details: Details) {
        logi("onScreenCall() invoked by Android")

        // With this coroutine, this function returns immediately without blocking the whole process.
        // So in doze mode, other services will be woken up and get executed simultaneously.
        // Feature "Push Alert" relies on this, see "NotificationListenerService.kt" for details.
        CoroutineScope(IO).launch {
            doScreenCall(details)
        }
    }

    private fun doScreenCall(details: Details) {
        logi("doScreenCall()")

        // Outgoing
        if (details.callDirection == Details.DIRECTION_OUTGOING) {
            updateOutgoingEmergencyTimestamp(this, details.getRawNumber())
        }

        // Incoming
        if (details.callDirection != Details.DIRECTION_INCOMING)
            return

        if (!spf.Global(this).isGloballyEnabled || !spf.Global(this).isCallEnabled) {
            pass(details)
            return
        }

        val rawNumber = details.getRawNumber()
        val ringingSimSlot = SimUtils.getRingingSimSlot(this)
        val cnap = details.callerDisplayName

        val r = processCall(
            ctx = this, logger = null, rawNumber = rawNumber, cnap = cnap, callDetails = details, simSlot = ringingSimSlot, isTest = false
        )
        logi("processCall() result: $r")

        if (r.shouldBlock()) {
            val blockType = r.getBlockType(this) // reject / silence / answer+hangup

            when (blockType) {
                Def.BLOCK_TYPE_SILENCE -> silence(details)
                Def.BLOCK_TYPE_ANSWER_AND_HANGUP -> answerThenHangUp(rawNumber, r, details)
                else -> reject(details)
            }
        } else {
            val shouldMute = setRingtone(this, r)
            pass(details, shouldMute)
        }
        logi("doScreenCall() finished")
    }

    // Return value: should mute or not
    //  true -> mute the ringtone
    //  false -> play the ringtone
    private fun setRingtone(ctx: Context, r: ICheckResult) : Boolean {
        if (r.shouldBlock()) // not allowed call, no ringtone, no need to check
            return false

        // 1. Get all workflows that are linked to this regex rule
        val bots = BotTable.listAll(ctx).filter {
            it.trigger is Ringtone
        }
        if (bots.isEmpty())
            return false

        // 2. Save the current ringtone to shared prefs
        val current = RingtoneUtil.getCurrent(ctx)
        spf.Temporary(ctx).ringtone = current.toString()

        // 3. Change the system default ringtone, it will be reset after 2 seconds
        var shouldMute = false
        bots.forEach { bot ->
            val aCtx = ActionContext(lastOutput = r, botId = bot.id)
            listOf(bot.trigger).executeAll(ctx, aCtx)
            shouldMute = aCtx.shouldMute
        }

        return shouldMute
    }

    private fun logToHistoryDb(ctx: Context, r: ICheckResult, rawNumber: String, cnap: String?, simSlot: Int?, isTest: Boolean) {
        val isDbLogEnabled = spf.HistoryOptions(ctx).isLoggingEnabled
        if (!isDbLogEnabled)
            return

        val recordId = CallTable().addNewRecord(
            ctx, HistoryRecord(
                peer = rawNumber,
                cnap = cnap,
                time = System.currentTimeMillis(),
                result = r.type,
                reason = r.reasonToDb(),
                simSlot = simSlot,
                isTest = isTest
            )
        )
        // broadcast the call to add a new item in history page
        Events.onNewCall.fire(recordId)
    }

    private fun showSpamNotification(ctx: Context, r: ICheckResult, rawNumber: String) {
        // click the notification to launch this app
        val intent = Intent(ctx, NotificationTrampolineActivity::class.java).apply {
            putExtra("type", "call")
            putExtra("blocked", true)
        }.setAction("action_call")

        val toCopy = Checker.checkQuickCopy(
            ctx, rawNumber, null, true, true
        )


        Notification.show(
            ctx,
            showType =  ShowType.SPAM_CALL,
            channel = r.getNotificationChannel(ctx, showType = ShowType.SPAM_CALL),
            title = Contacts.findContactByRawNumber(ctx, rawNumber)?.name ?: rawNumber,
            body = r.resultReasonStr(ctx),
            intent = intent,
            toCopy = toCopy
        )
    }

    fun processCall(
        ctx: Context,
        rawNumber: String,
        cnap: String?,
        callDetails: Details?, // it's null when testing
        simSlot: Int?,
        isTest: Boolean,
        logger: ILogger? = null, // for showing detailed steps to logcat or for testing purpose
    ): ICheckResult {
        logi("processCall()")

        // 0. check the number with all rules, get the result
        val r = Checker.checkCall(
            ctx, rawNumber = rawNumber, cnap = cnap, callDetails = callDetails, simSlot = simSlot, logger = logger)

        // 1. log result to history db
        logToHistoryDb(ctx, r, rawNumber, cnap, simSlot, isTest)

        if (r.shouldBlock()) {
            CoroutineScope(IO).launch {

                // 2. Show notification
                showSpamNotification(ctx, r, rawNumber)

                // 3. Report spam number
                autoReportSpam(ctx, r, rawNumber, isTest)
            }
        }

        return r
    }
}
