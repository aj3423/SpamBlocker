package spam.blocker.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.main.MainActivity
import spam.blocker.util.Notification

class NotificationTrampolineActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type = intent!!.getStringExtra("type")
        val blocked = intent.getBooleanExtra("blocked", false)

        // The action is not used, but it must exist in the Intent,
        // otherwise the intent will be overridden by the next notification
        val action = intent.action

        Log.d(Def.TAG, "notification clicked, type: $type, blocked: $blocked, action: $action")

        // Launch the default SMS app, open the conversation with the specific number
        if (type == "sms" && !blocked) {
            val phone = intent.getStringExtra("phone")
            Log.d(Def.TAG, "phone: $phone")

            val smsUri = Uri.parse("smsto:$phone")
//            val smsIntent = Intent(Intent.ACTION_VIEW, smsUri) // this popups dialog for choosing an app
            val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri) // this doesn't popup that dialog
            smsIntent.addCategory(Intent.CATEGORY_DEFAULT)
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(smsIntent)

        } else {
            // launch SpamBlocker to the SMS page
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("startPage", type)
            startActivity(intent)
        }

        Notification.cancelAll(this)

        finish();
    }
}