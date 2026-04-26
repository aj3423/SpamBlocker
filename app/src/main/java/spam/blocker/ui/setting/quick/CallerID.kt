package spam.blocker.ui.setting.quick

import android.content.Context
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.service.checker.ICheckResult
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.ColorPickerButton
import spam.blocker.ui.widgets.FloatingWindow
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.spf

const val DefaultCallerIdBgColor = 0x10808080
const val DefaultCallerIdTemplate =
"""<font color="#50c878"><big><big><big>{reason}</big></big></big></font>
<br>
<font color="#00bfff"><big>{geolocation}</big></font>
<br>
<font color="#ea86ff"><big>{carrier}</big></font>
"""

const val FullHtmlTagList = "https://github.com/aj3423/SpamBlocker/issues/555"

fun callerIdView(ctx: Context, template: String) : View {
    return TextView(ctx).apply {
        text = Html.fromHtml(template, Html.FROM_HTML_MODE_LEGACY)
        gravity = Gravity.CENTER
    }
}

fun showCallerIdWindow(
    ctx: Context,
    geoLocation: String? = null,
    carrier: String? = null,
    r: ICheckResult? = null
) {
    val spf = spf.CallerID(ctx)

    // Resolve tags: {reason} {api_result} {geolocation}
    var content = spf.template
    if (r != null) {
        content = content.replace("{reason}", r.resultReasonStr(ctx))
    }
    if (geoLocation != null) {
        content = content.replace("{geolocation}", geoLocation)
    }
    if (carrier != null) {
        content = content.replace("{carrier}", carrier)
    }

    FloatingWindow.apply {
        update(ctx, bgColor = spf.bgColor, x = spf.x, y = spf.y)
        onDrag = { newX, newY ->
            spf.x = newX
            spf.y = newY
        }
        show(ctx, callerIdView(ctx, content))
    }
}

@Composable
private fun ConfigDialog(
    trigger: MutableState<Boolean>
) {
    val ctx = LocalContext.current
    val spf = spf.CallerID(ctx)

    var bgColor by remember { mutableStateOf(Color(spf.bgColor)) }
    var template by remember { mutableStateOf(spf.template) }

    // Destroy the floating window when the app is killed
    DisposableEffect(true) {
        onDispose {
            FloatingWindow.hide(ctx)
        }
    }
    PopupDialog(
        trigger,
        popupSize = PopupSize(),
        onDismiss = {
            FloatingWindow.hide(ctx)
        },
        buttons = {
            val C = G.palette

            val confirmTrigger = remember { mutableStateOf(false) }
            PopupDialog(
                trigger = confirmTrigger,
                buttons = {
                    // Reset
                    StrokeButton(Str(R.string.reset), color = C.error) {
                        spf.bgColor = DefaultCallerIdBgColor
                        spf.template = DefaultCallerIdTemplate
                        bgColor = Color(DefaultCallerIdBgColor)
                        template = DefaultCallerIdTemplate
                        showCallerIdWindow(ctx)
                        confirmTrigger.value = false
                    }
                }
            ) {
                GreyText(Str(R.string.confirm_to_reset))
            }

            // Reset button
            StrokeButton(Str(R.string.reset), color = G.palette.error) {
                confirmTrigger.value = true
            }
        }
    ) {
        // BG Color
        LabeledRow(R.string.background_color) {
            ColorPickerButton(
                color = bgColor,
                onSelect = {
                    if (it != null) {
                        bgColor = it
                        spf.bgColor = it.toArgb()
                        FloatingWindow.update(ctx, bgColor = spf.bgColor)
                    }
                }
            )
        }

        // 2. Template
        StrInputBox(
            text = template,
            label = { Text(Str(R.string.content_template)) },
            helpTooltip = Str(R.string.help_caller_id_template).format(FullHtmlTagList),
            onValueChange = {
                template = it
                spf.template = it

                showCallerIdWindow(ctx)
            }
        )
    }
}

@Composable
fun CallerID() {
    val ctx = LocalContext.current
    val spf = spf.CallerID(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled) }

    LabeledRow(
        R.string.caller_id,
        helpTooltip = Str(R.string.help_caller_id),
        content = {
            val C = G.palette
            RowVCenterSpaced(4) {
                val trigger = remember { mutableStateOf(false) }
                ConfigDialog(trigger)

                if (isEnabled) {

                    // Setting button
                    StrokeButton(
                        label = Str(R.string.preview),
                        color = C.textGrey,
                    ) {
                        showCallerIdWindow(ctx)

                        trigger.value = true
                    }
                }

                SwitchBox(isEnabled) { isTurningOn ->
                    if (isTurningOn) {
                        G.permissionChain.ask(
                            ctx,
                            listOf(
                                PermissionWrapper(Permission.showOverlay),
                                PermissionWrapper(Permission.phoneState),
                            )
                        ) { granted ->
                            if (granted) {
                                isEnabled = true
                                spf.isEnabled = true
                            }
                        }
                    } else {
                        isEnabled = false
                        spf.isEnabled = false
                    }
                }
            }
        }
    )
}