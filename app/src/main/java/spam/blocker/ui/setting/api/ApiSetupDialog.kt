package spam.blocker.ui.setting.api

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.startAuthLogin
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Lambda1
import spam.blocker.util.spf

// -------- base class ------------
abstract class ApiSetupDialog {
    @Composable
    abstract fun Compose(trigger: MutableState<Boolean>)
}

/* --------- wiki API dialog -------------
  The dialog simply shows a warning and leads users to the wiki page.
  E.g., the API preset "Groq SMS"
 */
//class WikiSetupDialog(
//    val warningTooltipId: Int,
//) : ApiSetupDialog() {
//    @Composable
//    override fun Compose(trigger: MutableState<Boolean>) {
//        PopupDialog(trigger = trigger) {
//            HtmlText(Str(warningTooltipId))
//        }
//    }
//}
/* --------- Oauth API dialog -------------
  The dialog shows the existing OAuth token if it exists.
  When no existing token is found, it shows a button "Get a Token" that opens a browser activity for completing an oauth flow.
  E.g., the API preset "PhoneBlock"
 */
class OAuthSetupDialog(
    val spfTokenKey: String,
    val oauthUrl: String,
    val doAdd: Lambda1<Context>,
) : ApiSetupDialog() {
    @Composable
    override fun Compose(trigger: MutableState<Boolean>) {
        PopupDialog(trigger = trigger) {
            val ctx = LocalContext.current
            val coroutineScope = rememberCoroutineScope()

            var tokenInSpf by retain { mutableStateOf<String?>(null) }
            DisposableEffect(true) {
                val job = coroutineScope.launch {
                    withContext(IO) {
                        while (true) {
                            // Read from shared pref
                            tokenInSpf = spf.SharedPref(ctx).prefs.getString(spfTokenKey, "")
                                ?.takeIf { it.isNotEmpty() }

                            delay(500) // Delay for 0.5 second
                        }
                    }
                }
                onDispose {
                    job.cancel()
                }
            }

            if (tokenInSpf != null) {
                HtmlText(Str(R.string.found_existing_auth_token))
                HtmlText("<sample>${tokenInSpf!!.take(16)}...</sample>")

                StrokeButton(Str(R.string.use_this_token), color = G.palette.success) {
                    doAdd(ctx)
                    trigger.value = false
                }

                HorizontalDivider(modifier = M.padding(vertical = 10.dp))
            }

            if (tokenInSpf == null) {
                GreyText(Str(R.string.login_and_get_a_token))
            }
            StrokeButton(
                Str(R.string.get_a_new_token),
                color = if (tokenInSpf == null) G.palette.infoBlue else G.palette.textGrey
            ) {
                startAuthLogin(ctx, oauthUrl)
            }
        }
    }
}
