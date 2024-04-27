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

class NotificationOnClickReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context?, intent: Intent?) {

        Notification.cancelAll(ctx!!)

        val type = intent!!.getStringExtra("type")
        val blocked = intent.getBooleanExtra("blocked", false)

        // The action is not used, but it must exist in the Intent,
        // otherwise the intent will be overridden by the next notification
        val action = intent.action

        Log.d(Def.TAG, "notification clicked, type: $type, blocked: $blocked, action: $action")

        Notification.cancelAll(ctx)

        // Launch the default SMS app, open the conversation with the specific number
        if (type == "sms" && !blocked) {
            val phone = intent.getStringExtra("phone")
            Log.d(Def.TAG, "phone: $phone")

            val smsUri = Uri.parse("smsto:$phone")
            val smsIntent = Intent(Intent.ACTION_VIEW, smsUri)
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(smsIntent)
        } else {
            // Just launch SpamBlocker
            val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchIntent.putExtra("startPage", type)
            ctx.startActivity(launchIntent)
        }
    }
}