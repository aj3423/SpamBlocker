package spam.blocker.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import spam.blocker.util.SaveableLogger
import spam.blocker.util.loge
import spam.blocker.util.logi
import java.util.concurrent.Executors

object Protocol {
    const val action = "sms.screening.provider.PublicSMSScreeningService"

    const val smsScreening = 1
    const val smsScreeningResult = 2

    // request
    const val keyNumber = "number"
    const val keySmsContent = "smsContent"
    const val keySimSlot = "simSlot"
    // response
    const val keyShouldBlock = "shouldBlock"
    const val keyReason = "reason"
}

class PublicSMSScreeningService : Service() {

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sms-screening").apply { isDaemon = true }
    }

    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                Protocol.smsScreening -> {
                    handleQuery(message)
                    true
                }

                else -> false
            }
        }
    )

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun handleQuery(message: Message) {
        val ctx = this

        val requestData = message.data ?: Bundle.EMPTY
        val number = requestData.takeIf { it.containsKey(Protocol.keyNumber) }?.getString(Protocol.keyNumber)
        val smsContent = requestData.takeIf { it.containsKey(Protocol.keySmsContent) }?.getString(Protocol.keySmsContent)
        val simSlot = requestData.takeIf { it.containsKey(Protocol.keySimSlot) }?.getInt(Protocol.keySimSlot)

        val replyMessenger = message.replyTo ?: run {
            loge("Ignoring screening query without reply messenger.")
            return
        }

        worker.execute {
            try {
                val r = SmsReceiver.processSms(
                    ctx = ctx,
                    logger = SaveableLogger(),
                    rawNumber = number ?: "",
                    messageBody = smsContent ?: "",
                    simSlot = simSlot,
                    isTest = false,
                    showNotification = false,
                )
                logi("sms screening result: ${r.shouldBlock()}, ${r.resultReasonStr(ctx)}")
                sendResult(replyMessenger, r.shouldBlock(), r.resultReasonStr(ctx))
            } catch (t: Throwable) {
                loge("sms screening failed: $t")
                sendResult(replyMessenger, false, t.message ?: t.toString())
            }
        }
    }

    private fun sendResult(replyMessenger: Messenger, shouldBlock: Boolean, reason: String) {
        val response = Message.obtain(null, Protocol.smsScreeningResult).apply {
            data = Bundle().apply {
                putBoolean(Protocol.keyShouldBlock, shouldBlock)
                putString(Protocol.keyReason, reason)
            }
        }
        try {
            replyMessenger.send(response)
        } catch (_: RemoteException) {
            loge("Failed to deliver screening result to caller.")
        }
    }
}
