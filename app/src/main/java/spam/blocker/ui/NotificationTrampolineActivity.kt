package spam.blocker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import spam.blocker.util.Launcher
import spam.blocker.util.Notification
import spam.blocker.util.logd

class NotificationTrampolineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type = intent!!.getStringExtra("type")
        val blocked = intent.getBooleanExtra("blocked", false)

        // The action is not used, but it must exist in the Intent,
        // otherwise the intent will be overridden by the next notification
        val action = intent.action

        logd("notification clicked, type: $type, blocked: $blocked, action: $action")

        when (type) {
            "sms" -> {
                if (blocked) { // launch the default SMS app
                    Launcher.launchSMSApp(this)
                } else { // open the conversation in SMS app
                    val smsTo = intent.getStringExtra("rawNumber")
                    Launcher.openSMSConversation(this, smsTo)
                }
            }
            "call" -> { // only blocked calls have notification
                Launcher.launchCallApp(this)
            }
            else -> { // this should never happen
                Launcher.launchThisApp(this)
            }
        }

        Notification.cancelAll(this)

        finish()
    }
}