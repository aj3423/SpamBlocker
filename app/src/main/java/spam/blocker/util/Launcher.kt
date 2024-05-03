package spam.blocker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import spam.blocker.def.Def
import spam.blocker.ui.main.MainActivity

class Launcher {
    companion object {

        fun launchCallApp(ctx: Context) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://call_log/calls")))
        }

        fun launchSMSApp(ctx: Context) {
            val defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(ctx)
            val intent = ctx.packageManager.getLaunchIntentForPackage(defaultSmsApp)
            intent?.let { ctx.startActivity(it) }
        }

        fun launchThisApp(ctx: Context, startPage: String) {
            // launch SpamBlocker to the SMS page
            val intent = Intent(ctx, MainActivity::class.java)
            intent.putExtra("startPage", startPage)
            ctx.startActivity(intent)
        }

        fun openSMSConversation(ctx: Context, smsto: String?) {

            Log.d(Def.TAG, "smsto: $smsto")

            val smsUri = Uri.parse("smsto:$smsto")
            // val smsIntent = Intent(Intent.ACTION_VIEW, smsUri) // this popups dialog for choosing an app
            val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri) // this doesn't popup that dialog
            smsIntent.addCategory(Intent.CATEGORY_DEFAULT)
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(smsIntent)
        }
    }
}
