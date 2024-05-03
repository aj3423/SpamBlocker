package spam.blocker.service

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import spam.blocker.def.Def
import spam.blocker.util.Launcher
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

        when (type) {
            "sms" -> {
                if (blocked) { // launch the default SMS app
                    Launcher.launchSMSApp(this)
                } else { // open the conversation in SMS app
                    val smsto = intent.getStringExtra("phone")
                    Launcher.openSMSConversation(this, smsto)
                }
            }
            "call" -> { // only blocked calls have notification
                Launcher.launchCallApp(this)
            }
            else -> { // this should never happen
                Launcher.launchThisApp(this, "call")
            }
        }

        Notification.cancelAll(this)

        finish();
    }
}