package spam.blocker.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class Clipboard {
    companion object {
        fun copy(ctx: Context, toCopy: String?) {
            val clipboardManager = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("", toCopy)
            clipboardManager.setPrimaryClip(clipData)
        }
    }
}