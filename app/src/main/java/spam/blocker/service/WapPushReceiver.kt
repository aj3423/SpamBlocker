package spam.blocker.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
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
    // The MMS database location:
    //   /data/data/com.android.providers.telephony/databases/mmssms.db
    val mmsUri = "content://mms/".toUri()
    val partUri = "content://mms/part".toUri()

    override fun onReceive(ctx: Context, intent: Intent) {
        logi("Received WapPush")

        val spf = spf.Global(ctx)
        if (!spf.isGloballyEnabled || !spf.isSmsEnabled || !spf.isMmsEnabled) {
            return
        }

        val action = intent.action
        if (action != WAP_PUSH_RECEIVED_ACTION)
            return

        CoroutineScope(IO).launch { // Do it in a coroutine to not block the process
            process(ctx, intent)
        }
    }

    private fun process(ctx: Context, intent: Intent) {
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
        val contentLocation = String(pdu.contentLocation)
//        logi("tr_id: $transactionId, ct_l: $contentLocation")

        // This notification only indicates there is an MMS message, Android will send another
        // request to download the actual MMS media(Text, Image, ...). At this moment, the media
        // doesn't exist in the database yet, check it every 1 second.
        for (i in 1..20) { // retry for 20 sec
            Thread.sleep(1000)

            val map = retrieveMediaMap(ctx, transactionId, contentLocation)

            if (map != null) {
                val messageBody = map.getOrDefault(MimeTypes.TEXT_PLAIN, "")
                val simSlot = getSimSlotFromSmsIntent(ctx, intent)

                processSms(ctx, rawNumber, messageBody, simSlot, isTest = false)

                break
            }
        }
    }


    @SuppressLint("Range")
    fun retrieveMediaMap(
        ctx: Context,
        transactionId: String,
        contentLocation: String
    ): Map<String, String>? {
        // Matching by `tr_id` works for most SMS apps, but there are exceptions:
        //  - "Google Messages"
        //    - `tr_id` is wrapped with protobuf, e.g.: "proto:xxxxxxxx..."
        //    - `ct_l` works
        //  - "Textra"
        //    - `tr_id` is logged as "Txtr313" which isn't the raw transaction id
        //    - `ct_l` is empty, the contentLocation is saved in the `m_id` column

        val mmsId = findMmsId(ctx, "tr_id", transactionId) // Most SMS apps
            ?: findMmsId(ctx, "ct_l", contentLocation) // for GoogleMessages
            ?: findMmsId(ctx, "m_id", contentLocation) // for app Textra

        return if (mmsId != null) {
            queryPartTable(ctx, mmsId)
        } else {
            null
        }
    }

    // Query the MMS table to find the pdu record with `tr_id == transactionId`, or `m_id == transactionId`
    private fun findMmsId(ctx: Context, colName: String, colValue: String): Long? {
        val contentResolver = ctx.contentResolver

        val mmsCursor = contentResolver.query(
            mmsUri,
            arrayOf("_id"),
            "$colName='$colValue'", // selection
            null,
            null
        )

        return mmsCursor?.use { mmsIt ->
            // The `_id` of the pdu record
            if (mmsIt.moveToNext()) {
                mmsIt.getLongOrNull(0)
            } else {
                null
            }
        }
    }

    private fun queryPartTable(ctx: Context, mmsId: Long): Map<String, String>? {
        val partCursor = ctx.contentResolver.query(
            partUri,
            arrayOf("ct", "text"),
            "mid=$mmsId", // selection
            null,
            null
        )
        // When it goes here, the pda content should've been downloaded
        val map = mutableMapOf<String, String>()

        partCursor?.use { partIt ->
            while (partIt.moveToNext()) {
                val mime = partIt.getStringOrNull(0) ?: ""
                val text = partIt.getStringOrNull(1) ?: ""

                map[mime] = text
            }
        }
        return map.ifEmpty { null }
    }
}