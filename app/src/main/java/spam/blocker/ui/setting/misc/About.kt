package spam.blocker.ui.setting.misc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import spam.blocker.BuildConfig
import spam.blocker.R
import spam.blocker.ui.setting.SettingRow
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton

const val repo = "https://github.com/aj3423/SpamBlocker"

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
                                "${ctx.resources.getString(R.string.source_code)}:<br>&emsp;<a href=\"$repo\">$repo</a><br>"

                    HtmlText(html = msg)
                }
            )
        }

        StrokeButton(
            label = Str(R.string.about),
            color = SkyBlue,
        ) {
            popupTrigger.value = true
        }
    }
}