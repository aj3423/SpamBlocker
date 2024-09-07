package spam.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import spam.blocker.util.Clipboard
import spam.blocker.util.Notification

class CopyToClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {

        // dismiss this notification, others remain
        val notifId = intent.getIntExtra("notificationId", 0)
        Notification.cancelById(ctx, notifId)

        // copy to clipboard
        val toCopy = intent.getStringExtra("toCopy")
        Clipboard.copy(ctx, toCopy)

//        val copied = ctx.getString(R.string.copied_to_clipboard)
//        Toast.makeText(ctx, copied.format(toCopy), Toast.LENGTH_SHORT).show();
    }
}