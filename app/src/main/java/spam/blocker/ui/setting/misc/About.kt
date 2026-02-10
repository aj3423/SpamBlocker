package spam.blocker.ui.setting.misc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import spam.blocker.BuildConfig
import spam.blocker.R
import spam.blocker.ui.setting.SettingRow
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.FileWriteChooser
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton

const val REPO = "https://github.com/aj3423/SpamBlocker"

@Composable
fun About() {
    val ctx = LocalContext.current

    SettingRow {
        val popupTrigger = rememberSaveable { mutableStateOf(false) }

        if (popupTrigger.value) {
            PopupDialog(
                trigger = popupTrigger,
                content = {

                    val msg =
                        "${ctx.resources.getString(R.string.version)}:<br>&emsp;${BuildConfig.VERSION_NAME}<br><br>" +
                                "${ctx.resources.getString(R.string.source_code)}:<br>&emsp;<a href=\"$REPO\">$REPO</a><br>"

                    HtmlText(html = msg)
                }
            )
        }

        val trigger = remember { mutableStateOf(false) }
        var filename by remember { mutableStateOf("") }
        var bytes by remember { mutableStateOf("".toByteArray()) }

        FileWriteChooser(
            trigger = trigger,
            filename = filename,
            bytes = bytes,
        )

        StrokeButton(
            label = Str(R.string.about),
            color = SkyBlue,
            onClick = {
                popupTrigger.value = true
            },
            onLongClick = {
                filename = "SpamBlocker.log"
                bytes = Logcat.collect().toByteArray()

                trigger.value = true
            }
        )
    }
}