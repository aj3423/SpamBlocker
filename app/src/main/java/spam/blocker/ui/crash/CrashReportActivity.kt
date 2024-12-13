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
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import spam.blocker.BuildConfig
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.theme.AppTheme
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.Teal200
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

        G.themeType.intValue = spf.Global(this).getThemeType()

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
                        Text(
                            text = Str(R.string.crash_title),
                            fontWeight = FontWeight.SemiBold,
                            color = Salmon
                        )

                        // Get the error message from the intent
                        val toReport = remember {
                            val stackTrace = intent.getStringExtra("stackTrace")

                            "android version: ${Build.VERSION.SDK_INT}\n" +
                                    "app version: ${BuildConfig.VERSION_NAME}\n" +
                                    "$stackTrace"
                        }

                        StrInputBox(
                            text = toReport,
                            maxLines = 20,
                            onValueChange = {}
                        )

                        Row(
                            modifier = M.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            StrokeButton(label = Str(R.string.copy), color = Teal200) {
                                Clipboard.copy(ctx, toReport)
                            }
                        }
                    }
                }
            }
        }
    }
}