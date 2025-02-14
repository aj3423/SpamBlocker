package spam.blocker.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import spam.blocker.util.logi
import spam.blocker.util.pdu.pdu.NotificationInd
import spam.blocker.util.pdu.pdu.PduParser
import spam.blocker.util.spf

object MimeTypes {
//    const val APPLICATION_SMIL = "application/smil"
//    const val AUDIO_MP4 = "audio/mp4"

    const val TEXT_PLAIN = "text/plain"
}

class WapPushReceiver : SmsReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        logi("WapPush received...")

        val spf = spf.Global(ctx)
        if (!spf.isGloballyEnabled() || !spf.isSmsEnabled() || !spf.isMmsEnabled()) {
            return
        }

        val action = intent.action
        if (action != WAP_PUSH_RECEIVED_ACTION)
            return

        // doc: https://developer.android.com/reference/kotlin/android/provider/Telephony.Sms.Intents#wap_push_received_action

        // extras contain:
        //		android.telephony.extra.SUBSCRIPTION_INDEX=2,
        //		header=[97, 112, 112, 108, 105, 99, 97, 116, 105, 111, 110, 47, 118, 110, 100, 46, 119, 97, 112, 46, 109, 109, 115, 45, 109, 101, 115, 115, 97, 103, 101, 0, -76, -121, -81, -124],
        //			00000000  61 70 70 6c 69 63 61 74  69 6f 6e 2f 76 6e 64 2e  |application/vnd.|
        //			00000010  77 61 70 2e 6d 6d 73 2d  6d 65 73 73 61 67 65 00  |wap.mms-message.|
        //			00000020  b4 87 af 84
        //		android.telephony.extra.SLOT_INDEX=0,
        //		pduType=6,
        //		data=[-116, -126, -104, 49, 103, 81, 4...],
        //		phone=0,
        //		subscription=2,
        //		transactionId=41
        val extras = intent.extras

        val data = extras!!.getByteArray("data")
        if (data == null)
            return

        val pdu = PduParser(data, true).parse()
        if (pdu !is NotificationInd)
            return

        val rawNumber = pdu.from.string

        // query SMS/MMS log by transactionId, it looks like: "MCP0001oP9g0",
        //  not some simple integer like in the `intent.extras`,
        val transactionId = String(pdu.transactionId)

        // This notification only indicates there is an MMS message, Android will send another
        // request to download the actual MMS media. At this moment, the media
        // doesn't exist in the database yet, use a loop to check it every n seconds.
        (0 until 20).any { // retry for 1 minute
            Thread.sleep(3000)

            val messageBody = retrieveText(ctx, transactionId)

            messageBody?.let {
                processSms(ctx, logger = null, rawNumber, messageBody)
            }
            messageBody != null
        }
    }

    @SuppressLint("Range")
    private fun retrieveText(ctx: Context, transactionId: String) : String? {
        val contentResolver = ctx.contentResolver

        // The actual database location:
        //   /data/data/com.android.providers.telephony/databases/mmssms.db
        val mmsUri = Uri.parse("content://mms/")
        val partUri = Uri.parse("content://mms/part")

        // Query the MMS table to get the message with the specific transaction ID
        val mmsCursor = contentResolver.query(
            mmsUri,
            arrayOf("_id"),
            "tr_id='$transactionId'", // selection
            null,
            null
        )

        mmsCursor?.use {
            if (it.moveToFirst()) {
                // Get the ID of the MMS message
                val mmsId = it.getLongOrNull(0)

                val partCursor = contentResolver.query(
                    partUri,
                    arrayOf("ct", "text"),
                    "mid=$mmsId", // selection
                    null,
                    null
                )
                partCursor?.use {
                    while (it.moveToNext()) {
                        val ct = it.getStringOrNull(0)
                        val text = it.getStringOrNull(1)
                        if (ct == MimeTypes.TEXT_PLAIN) {
                            return text ?: ""
                        }
                    }
                }
            }
        }
        return null
    }
}
