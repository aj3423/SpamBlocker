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
import spam.blocker.service.reporting.reportSpam
import spam.blocker.ui.NotificationTrampolineActivity
import spam.blocker.util.Contacts
import spam.blocker.util.ILogger
import spam.blocker.util.Notification
import spam.blocker.util.Notification.ShowType
import spam.blocker.util.RingtoneUtil
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
                spf.setTimestamp(System.currentTimeMillis())
            }
        }
    }

    private fun pass(details: Details, shouldMute: Boolean = false) {
        val builder = CallResponse.Builder().apply {
            if (shouldMute)
                setSilenceCall(true)
        }
        respondToCall(details, builder.build())
    }

    private fun reject(details: Details) {
        val builder = CallResponse.Builder().apply {
            setSkipCallLog(false)
            setSkipNotification(true)
            setDisallowCall(true)

            setRejectCall(true)
        }
        respondToCall(details, builder.build())
    }

    private fun silence(details: Details) {
        val builder = CallResponse.Builder().apply {
            setSkipCallLog(false)
            setSkipNotification(true)
            setDisallowCall(true)

//            setSilenceCall(true) // no need
        }
        respondToCall(details, builder.build())
    }

    private fun answerThenHangUp(rawNumber: String, r: ICheckResult, details: Details) {
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
            setLastCallToBlock(Util.clearNumber(rawNumber))
            setLastCallTime(now)
            setHangUpDelay(r.hangUpDelay(ctx))
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
        //   So other services will get executed simultaneously.
        //   Feature "Push Alert" relies on this, see "NotificationListenerService.kt" for details.
        CoroutineScope(IO).launch {
            doScreenCall(details)
        }
    }

    private fun doScreenCall(details: Details) {
        // Outgoing
        if (details.callDirection == Details.DIRECTION_OUTGOING) {
            updateOutgoingEmergencyTimestamp(this, details.getRawNumber())
        }

        // Incoming
        if (details.callDirection != Details.DIRECTION_INCOMING)
            return

        if (!spf.Global(this).isGloballyEnabled() || !spf.Global(this).isCallEnabled()) {
            pass(details)
            return
        }

        val rawNumber = details.getRawNumber()

        val r = processCall(ctx = this, logger = null, rawNumber = rawNumber, callDetails = details)

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
    }

    // Return value: should mute or not
    //  true -> mute the ringtone
    //  false -> play the ringtone
    private fun setRingtone(ctx: Context, r: ICheckResult) : Boolean {
        if (r.shouldBlock()) // not allowed call, no ringtone, no need to check
            return false

        // 1. Get the workflow(s) that is linked to this regex rule
        val bots = BotTable.listAll(ctx).filter {
            it.actions.firstOrNull() is Ringtone
        }
        if (bots.isEmpty())
            return false

        // 2. Save the current ringtone to shared prefs
        val current = RingtoneUtil.getCurrent(ctx)
        spf.Temporary(ctx).setRingtone(current.toString())

        // 3. Change the system default ringtone, it will be reset soon in CallStateReceiver
        var shouldMute = false
        bots.forEach {
            val aCtx = ActionContext(lastOutput = r)
            it.actions.executeAll(ctx, aCtx)
            shouldMute = aCtx.shouldMute
        }

        return shouldMute
    }

    private fun logToHistoryDb(ctx: Context, r: ICheckResult, rawNumber: String) {
        val isDbLogEnabled = spf.HistoryOptions(ctx).isLoggingEnabled()
        val recordId = if (isDbLogEnabled) {
            CallTable().addNewRecord(
                ctx, HistoryRecord(
                    peer = rawNumber,
                    time = System.currentTimeMillis(),
                    result = r.type,
                    reason = r.reasonToDb(),
                )
            )
        } else 0

        // broadcast the call to add a new item in history page
        if (isDbLogEnabled) {
            Events.onNewCall.fire(recordId)
        }
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
        logger: ILogger?, // for showing detailed steps to logcat or for testing purpose
        rawNumber: String,
        callDetails: Details? = null, // it's null when testing
    ): ICheckResult {
        logi("Process incoming call")

        // 0. check the number with all rules, get the result
        val r = Checker.checkCall(ctx, logger, rawNumber, callDetails)

        // 1. log result to history db
        logToHistoryDb(ctx, r, rawNumber)

        if (r.shouldBlock()) {
            CoroutineScope(IO).launch {

                // 2. Show notification
                showSpamNotification(ctx, r, rawNumber)

                // 3. Report spam number
                reportSpam(ctx, r, rawNumber, isTesting = callDetails == null)
            }
        }

        return r
    }
}
