package spam.blocker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import spam.blocker.ui.main.MainActivity
import spam.blocker.ui.setting.api.PhoneBlock
import spam.blocker.util.spf


fun startAuthLogin(ctx: Context, url: String) {

    val customTabsIntent = CustomTabsIntent.Builder().build()

    try {
        customTabsIntent.launchUrl(ctx, url.toUri())
    } catch (e: Exception) {
        // Very rare fallback
        val fallbackIntent = Intent(Intent.ACTION_VIEW, url.toUri())
        ctx.startActivity(fallbackIntent)
    }
}

class AuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = getIntent()
        val data = intent.data

        if (data != null && "auth" == data.host) {
            val state = data.getQueryParameter("state")
            when (state) {
                PhoneBlock.OAuthState -> {
                    val token = data.getQueryParameter("loginToken")
                    token?.let {
                        spf.OAuth(this).phoneBlockToken = it
                    }
                }
            }
        }

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }
}
