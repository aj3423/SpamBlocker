@file:OptIn(ExperimentalMaterial3Api::class)

package spam.blocker.ui.setting.misc

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import spam.blocker.BuildConfig
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.SettingRow
import spam.blocker.ui.theme.ColdGrey
import spam.blocker.ui.theme.Priority
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.BalloonWrapper
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResImage
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.rememberFileWriteChooser

const val REPO = "https://github.com/aj3423/SpamBlocker"

data class FaqBox(
    val label: String,
    val color: Color,
    val helpTooltip: String,
)

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun FAQ() {
    val ctx = LocalContext.current

    val popupTrigger = rememberSaveable { mutableStateOf(false) }

    if (popupTrigger.value) {
        PopupDialog(
            trigger = popupTrigger,
            content = {
                val items = remember {
                    listOf(
                        FaqBox(
                            label = ctx.getString(R.string.priority),
                            color = Priority,
                            helpTooltip = ctx.getString(R.string.faq_priority)
                        )
                    )
                }


                val scope = rememberCoroutineScope()

                FlowRowSpaced(
                    space = 20,
                    vSpace = 30,
                ) {
                    items.forEach { item ->
                        BalloonWrapper(
                            tooltip = item.helpTooltip,
                        ) { tooltipState ->
                            StrokeButton(
                                label = item.label,
                                color = item.color,
                            ) {
                                scope.launch {
                                    tooltipState.show()
                                }
                            }
                        }

                    }
                }
            }
        )
    }

    val fileWriter = rememberFileWriteChooser()
    fileWriter.Compose()

    StrokeButton(
        label = Str(R.string.faq),
        color = SkyBlue,
        onClick = {
            popupTrigger.value = true
        },
        onLongClick = {
            fileWriter.popup(
                filename = "SpamBlocker.log",
                content = Logcat.collect().toByteArray(),
            )
        }
    )
}

@Composable
fun About() {
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

    val fileWriter = rememberFileWriteChooser()
    fileWriter.Compose()

    StrokeButton(
        label = Str(R.string.about),
        color = SkyBlue,
        onClick = {
            popupTrigger.value = true
        },
        onLongClick = {
            fileWriter.popup(
                filename = "SpamBlocker.log",
                content = Logcat.collect().toByteArray(),
            )
        }
    )
}

@Composable
fun About_Faq() {
    SettingRow {
        RowVCenterSpaced(8) {
            FAQ()
            About()
        }
    }
}
