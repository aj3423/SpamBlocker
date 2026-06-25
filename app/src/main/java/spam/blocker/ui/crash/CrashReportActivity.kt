package spam.blocker.ui.crash

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import spam.blocker.db.BotTable
import spam.blocker.db.CallTable
import spam.blocker.db.SmsTable
import spam.blocker.ui.M
import spam.blocker.ui.setting.misc.REPO
import spam.blocker.ui.theme.AppTheme
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.NormalColumnScrollbar
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Clipboard

class CrashReportActivity : ComponentActivity() {


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val ctx = this

        setContent {
            val C = G.palette

            AppTheme {
                Scaffold(
                    modifier = M
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) { innerPadding ->
                    val scrollState = rememberScrollState()

                    NormalColumnScrollbar(scrollState) {
                        Column(
                            modifier = M
                                .padding(innerPadding)
                                .padding(16.dp)
                                .verticalScroll(scrollState),
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
                                color = C.error
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
                                    StrokeButton(label = Str(R.string.copy), color = C.teal200) {
                                        Clipboard.copy(ctx, stackTrace)
                                    }

                                    val uriHandler = LocalUriHandler.current
                                    StrokeButton(
                                        label = Str(R.string.report_bug),
                                        color = C.infoBlue,
                                    ) {
                                        uriHandler.openUri("$REPO/issues")
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 1.dp, color = G.palette.disabled, modifier = M.padding(vertical = 8.dp))

                            // Clear History
                            val clearTrigger = remember { mutableStateOf(false) }
                            PopupDialog(
                                clearTrigger,
                                buttons = {
                                    StrokeButton(Str(R.string.delete), color = C.error) {

                                        // Clear Call/SMS history
                                        CallTable().clearAll(ctx)
                                        SmsTable().clearAll(ctx)

                                        // Clear workflow lastLog
                                        BotTable.clearAllLastLogs(ctx)

                                        clearTrigger.value = false
                                    }
                                }
                            ) {
                                GreyText(Str(R.string.confirm_to_delete))
                            }
                            HtmlText(Str(R.string.tip_clear_history))
                            Row(
                                modifier = M.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                StrokeButton(label = Str(R.string.clear_history), color = C.error) {
                                    clearTrigger.value = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}