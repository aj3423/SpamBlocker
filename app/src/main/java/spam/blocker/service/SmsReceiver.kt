package spam.blocker.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import spam.blocker.R
import spam.blocker.db.ContentFilterTable
import spam.blocker.db.Db
import spam.blocker.db.NumberFilterTable
import spam.blocker.db.PatternFilter
import spam.blocker.db.Record
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.ui.main.MainActivity
import spam.blocker.util.Notification
import spam.blocker.util.SharedPref
import spam.blocker.util.Util

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context?, intent: Intent?) {
        Log.d(Def.TAG, "onReceive in SmsReceiver")
        if (!SharedPref(ctx!!).isGloballyEnabled()) {
            return
        }
        val action = intent?.action
        if (action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val msg = messages[0]
            val peer = msg.originatingAddress!!
            val messageBody = msg.messageBody;
            Log.d(Def.TAG, "onReceive sms from $peer: $messageBody")

            val r = shouldBlock(ctx, peer, messageBody)

            // 1. log to db
            val rec = Record()
            rec.peer = peer
            rec.time = System.currentTimeMillis()
            rec.result = r.result
            rec.reason = r.reason()
            val id = SmsTable().addNewRecord(ctx, rec)

            if (r.shouldBlock) {
                var importance = NotificationManager.IMPORTANCE_LOW // default: LOW

                if (r.result == Db.RESULT_BLOCKED_BLACKLIST || r.result == Db.RESULT_BLOCKED_BY_CONTENT) {
                    importance = r.byFilter!!.importance
                }

                // click the notification to launch this app
                val callbackIntent = Intent(ctx, MainActivity::class.java)
                Notification.show(
                    ctx,
                    1,
                    ctx.resources.getString(R.string.spam_sms_blocked),
                    peer,
                    importance,
                    callbackIntent
                )
            } else {
                // different notification id generates different dropdown items
                val notificationId = System.currentTimeMillis().toInt()
                // click to launch the default sms app and navigate to this number
                val smsUri = Uri.parse("smsto:$peer")
                val callbackIntent = Intent(Intent.ACTION_SENDTO, smsUri)

                val contact = Util.findContact(ctx, peer)

                Notification.show(
                    ctx,
                    notificationId,
                    ctx.resources.getString(R.string.new_sms_received),
                    "${contact?.name ?: peer}: $messageBody",
                    NotificationManager.IMPORTANCE_HIGH,
                    callbackIntent
                )
            }
            broadcastNewSms(ctx, r.shouldBlock, id)
        }
    }


    private fun broadcastNewSms(ctx: Context, blocked: Boolean, id: Long) {
        val intent = Intent(Def.ON_NEW_SMS)
        intent.putExtra("type", "sms")
        intent.putExtra("blocked", blocked)
        intent.putExtra("record_id", id)

        ctx.sendBroadcast(intent)
    }

    // returns <should_block, result, reason>
    private fun shouldBlock(
        ctx: Context,
        phone: String,
        messageBody: String
    ): CheckResult {

        val spf = SharedPref(ctx)

        // 1. check contacts
        if (spf.isContactsAllowed()) {
            val contact = Util.findContact(ctx, phone)
            if (contact != null) {
                Log.i(Def.TAG, "is contact")
                return CheckResult(false, Db.RESULT_ALLOWED_AS_CONTACT)
                    .setContactName(contact.name)
            }
        }


        class Wrapper(val filter: PatternFilter, val isNumberFilter: Boolean) {
        }

        // 2. check number/content filters
        run {
            val numberFilters = NumberFilterTable().listFilters(ctx, Db.FLAG_FOR_SMS, Db.FLAG_BOTH_WHITE_BLACKLIST)
                .map { Wrapper(it, true) }
            val contentFilters = ContentFilterTable().listFilters(
                    ctx, 0/* doesn't care */, Db.FLAG_BOTH_WHITE_BLACKLIST)
                .map { Wrapper(it, false) }

            // join them together and sort by priority desc
            val all = (numberFilters + contentFilters).sortedByDescending {
                it.filter.priority
            }

            for (wrapper in all) {
                val f = wrapper.filter

                val matches = if (wrapper.isNumberFilter) {
                    f.pattern.toRegex().matches(phone)
                } else { // sms content filter
                    if (f.patternExtra != "") {
                        f.pattern.toRegex().matches(messageBody) && f.patternExtra.toRegex().matches(phone)
                    } else {
                        f.pattern.toRegex().matches(messageBody)
                    }
                }

                if (matches) {
                    Log.i(Def.TAG, "filter matches: $f")

                    val block = f.isBlacklist
                    val result = if (wrapper.isNumberFilter) {
                        if (block) Db.RESULT_BLOCKED_BLACKLIST else Db.RESULT_ALLOWED_WHITELIST
                    } else {
                        if (block) Db.RESULT_BLOCKED_BY_CONTENT else Db.RESULT_ALLOWED_BY_CONTENT
                    }

                    return CheckResult(block, result).setFilter(f)
                }
            }
        }


        // 4. pass by default
        return CheckResult(false, Db.RESULT_ALLOWED_BY_DEFAULT)
    }
}