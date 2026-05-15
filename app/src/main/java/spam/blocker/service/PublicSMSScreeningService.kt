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
import spam.blocker.service.checker.Checker
import spam.blocker.util.SaveableLogger
import spam.blocker.util.loge
import spam.blocker.util.logi

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

    private val mainHandler = Handler(Looper.getMainLooper())

    private val messenger = Messenger(
        Handler(mainHandler.looper) { message ->
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

        Runnable {
            val r = SmsReceiver.processSms(
                ctx = ctx,
                logger = SaveableLogger(),
                rawNumber = number ?: "",
                messageBody = smsContent ?: "",
                simSlot = simSlot,
                isTest = false,
                showNotification = false, // disable for sms-screening-mode
            )
            logi("sms screening result: ${r.shouldBlock()}, ${r.resultReasonStr(ctx)}")

            val response = Message.obtain(
                null,
                Protocol.smsScreeningResult,
            ).apply {
                data = Bundle().apply {
                    putBoolean(Protocol.keyShouldBlock, r.shouldBlock())
                    putString(Protocol.keyReason, r.resultReasonStr(ctx))
                }
            }

            try {
                replyMessenger.send(response)
            } catch (_: RemoteException) {
                loge("Failed to deliver screening result to caller.")
            }
        }.run()
    }
}
