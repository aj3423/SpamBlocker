package spam.blocker.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.widget.Toast
import spam.blocker.R
import spam.blocker.util.Notification

class CopyToClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {

        // dismiss this notification, others remain
        val notifId = intent.getIntExtra("notificationId", 0)
        Notification.cancelById(ctx, notifId)

        // copy to clipboard
        val toCopy = intent.getStringExtra("toCopy")
        val clipboardManager = ctx.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("", toCopy)
        clipboardManager.setPrimaryClip(clipData)

//        val copied = ctx.getString(R.string.copied_to_clipboard)
//        Toast.makeText(ctx, copied.format(toCopy), Toast.LENGTH_SHORT).show();
    }
}