package spam.blocker.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import spam.blocker.ui.main.MainActivity
import androidx.core.net.toUri

object Launcher {

    fun launchCallApp(ctx: Context) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, "content://call_log/calls".toUri()))
    }

    fun launchSMSApp(ctx: Context) {
        val defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(ctx)
        val intent = ctx.packageManager.getLaunchIntentForPackage(defaultSmsApp)
        intent?.let { ctx.startActivity(it) }
    }

    fun launchThisApp(ctx: Context) {
        val intent = Intent(ctx, MainActivity::class.java)
        ctx.startActivity(intent)
    }

    fun openCallConversation(ctx: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = "tel:$phoneNumber".toUri()
        }
        if (intent.resolveActivity(ctx.packageManager) != null) {
            ctx.startActivity(intent)
        }
    }

    fun openSMSConversation(ctx: Context, smsto: String?) {

        val smsUri = "smsto:$smsto".toUri()
        // val smsIntent = Intent(Intent.ACTION_VIEW, smsUri) // this popups dialog for choosing an app
        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri) // this doesn't popup that dialog
        smsIntent.addCategory(Intent.CATEGORY_DEFAULT)
        smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(smsIntent)
    }

    fun selfRestart(ctx: Context) {
        val intent = Intent(ctx, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)

        // without killing itself, notifications will stop working after a backup restore
//        if (ctx is Activity) {
//            ctx.finish()
//        }
//        Runtime.getRuntime().exit(0)
    }
}
