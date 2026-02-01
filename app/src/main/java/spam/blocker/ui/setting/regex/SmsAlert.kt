package spam.blocker.ui.setting.regex

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.spf


@Composable
fun SmsAlert() {
    val ctx = LocalContext.current
    val C = LocalPalette.current
    val spf = spf.SmsAlert(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled && Permission.receiveSMS.isGranted) }
    var duration by remember { mutableIntStateOf(spf.duration) }
    var regexStr by remember { mutableStateOf(spf.regexStr) }
    var regexFlags = remember { mutableIntStateOf(spf.regexFlags) }

    // Edit Duration Dialog
    val editTrigger = rememberSaveable { mutableStateOf(false) }
    PopupDialog(
        trigger = editTrigger,
    ) {
        NumberInputBox(
            intValue = duration,

            labelId = R.string.within_seconds,
            onValueChange = { newVal, hasErr ->
                if (newVal != null) {
                    duration = newVal
                    spf.duration = duration
                }
            },
            leadingIconId = R.drawable.ic_duration,
        )
        RegexInputBox(
            label = { Text(Str(R.string.sms_content_pattern)) },
            regexStr = regexStr,
            regexFlags = regexFlags,
            onRegexStrChange = { newVal, hasErr ->
                if (!hasErr) {
                    regexStr = newVal
                    spf.regexStr = regexStr
                }
            },
            onFlagsChange = {
                regexFlags.intValue = it
                spf.regexFlags = it
            },
            testable = true,
            leadingIcon = { GreyIcon18(R.drawable.ic_open_msg) }
        )
    }

    var collapsed by remember { mutableStateOf(spf.isCollapsed) }

    LabeledRow(
        labelId = R.string.sms_alert,
        isCollapsed = collapsed,
        toggleCollapse = {
            collapsed = !collapsed
            spf.isCollapsed = collapsed
        },
        helpTooltip = Str(R.string.help_sms_alert),
        content = {
            if (isEnabled) {
                StrokeButton(
                    label = "$duration ${Str(R.string.seconds_short)}",
                    color = C.textGrey,
                ) {
                    editTrigger.value = true
                }
            }
            SwitchBox(isEnabled) { isTurningOn ->
                if (isTurningOn) {
                    G.permissionChain.ask(
                        ctx,
                        listOf(
                            PermissionWrapper(Permission.batteryUnRestricted, isOptional = true),
                            PermissionWrapper(Permission.receiveSMS)
                        )
                    ) { granted ->
                        if (granted) {
                            spf.isEnabled = true
                            isEnabled = true
                        }
                    }
                } else {
                    spf.isEnabled = false
                    isEnabled = false
                }
            }
        }
    )

    AnimatedVisibleV (isEnabled && !collapsed) {
        OutlineCard {
            Row(
                modifier = M
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clickable {
                        editTrigger.value = true
                    }
            ) {
                // Regex
                Text(
                    text = regexStr,
                    color = C.textGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = M.padding(top = 2.dp),
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}