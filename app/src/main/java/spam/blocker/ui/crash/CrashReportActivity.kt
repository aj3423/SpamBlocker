package spam.blocker.ui.crash

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import spam.blocker.BuildConfig
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.misc.REPO
import spam.blocker.ui.theme.AppTheme
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Clipboard
import spam.blocker.util.spf

class CrashReportActivity : ComponentActivity() {


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        G.themeType.intValue = spf.Global(this).themeType

        val ctx = this

        setContent {
            AppTheme(
                darkTheme = when (G.themeType.intValue) {
                    1 -> false
                    2 -> true
                    else -> isSystemInDarkTheme()
                }
            ) {
                Scaffold(
                    modifier = M
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) { innerPadding ->
                    Column(
                        modifier = M
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Get the error message from the intent
                        val stackTrace by remember {
                            val stackTrace = intent.getStringExtra("stackTrace")

                            mutableStateOf(
                                "android code: ${Build.VERSION.SDK_INT}\n" +
                                    "app version: ${BuildConfig.VERSION_NAME}\n" +
                                    "$stackTrace"
                            )
                        }

                        var isKnownIssue by remember { mutableStateOf(false) }

                        var promptStr by remember(stackTrace) {
                            val knownIssueLink = when {
                                "layouts are not part of the same hierarchy" in stackTrace -> "$REPO/issues/502"
                                else -> null
                            }
                            isKnownIssue = knownIssueLink != null

                            mutableStateOf(
                                if (isKnownIssue) {
                                    ctx.getString(R.string.known_android_issue).format(knownIssueLink, knownIssueLink)
                                } else {
                                    ctx.getString(R.string.crash_title)
                                }
                            )
                        }

                        HtmlText(
                            html = promptStr,
                            color = Salmon
                        )

                        StrInputBox(
                            text = stackTrace,
                            maxLines = 20,
                            onValueChange = {}
                        )

                        if (!isKnownIssue) {
                            Row(
                                modifier = M.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    space = 10.dp,
                                    alignment = Alignment.End
                                )
                            ) {
                                StrokeButton(label = Str(R.string.copy), color = Teal200) {
                                    Clipboard.copy(ctx, stackTrace)
                                }

                                val uriHandler = LocalUriHandler.current
                                StrokeButton(
                                    label = Str(R.string.report_bug),
                                    color = SkyBlue,
                                ) {
                                    uriHandler.openUri("$REPO/issues")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}