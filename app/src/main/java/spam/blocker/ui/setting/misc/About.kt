@file:OptIn(ExperimentalMaterial3Api::class)

package spam.blocker.ui.setting.misc

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import spam.blocker.BuildConfig
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.widgets.FileChooser
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.InitFile
import spam.blocker.ui.widgets.MIME_TEXT
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.FileUtils.writeDataToUri

const val REPO = "https://github.com/aj3423/SpamBlocker"


@Composable
fun About() {
    val C = G.palette
    val ctx = LocalContext.current

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

    StrokeButton(
        label = Str(R.string.about),
        color = C.infoBlue,
        onClick = {
            popupTrigger.value = true
        },
        onLongClick = {
            FileChooser.popupWrite(
                init = InitFile(
                    filename = "SpamBlocker.log",
                    mimeType = MIME_TEXT,
                ),
                onResult = { uri ->
                    uri?.let{
                        writeDataToUri(ctx, uri, Logcat.collect().toByteArray())
                    }
                }
            )
        }
    )
}
