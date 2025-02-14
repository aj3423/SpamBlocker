package spam.blocker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import spam.blocker.util.Launcher
import spam.blocker.util.Notification

class NotificationTrampolineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type = intent!!.getStringExtra("type")
        val blocked = intent.getBooleanExtra("blocked", false)

//        val action = intent.action
//        logi("notification clicked, type: $type, blocked: $blocked, action: $action")

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