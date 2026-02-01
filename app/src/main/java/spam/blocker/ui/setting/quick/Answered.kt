package spam.blocker.ui.setting.quick

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PluralStr
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.spf

@Composable
fun Answered() {
    val ctx = LocalContext.current
    val spf = spf.Answered(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled && Permission.callLog.isGranted) }
    var minDuration by remember { mutableIntStateOf(spf.minDuration) }
    var inXDay by remember { mutableIntStateOf(spf.days) }

    // config popup
    val configTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = configTrigger,
        content = {
            NumberInputBox(
                intValue = inXDay,
                onValueChange = { newValue, hasError ->
                    if (!hasError) {
                        inXDay = newValue!!
                        spf.days = newValue
                    }
                },
                labelId = R.string.within_days,
                leadingIconId = R.drawable.ic_duration,
            )
            NumberInputBox(
                intValue = minDuration,
                onValueChange = { newValue, hasError ->
                    if (!hasError) {
                        minDuration = newValue!!
                        spf.minDuration = newValue
                    }
                },
                labelId = R.string.minimal_duration,
                leadingIconId = R.drawable.ic_duration,
            )
        }
    )

    fun askForPermission() {
        G.permissionChain.ask(
            ctx,
            listOf(
                PermissionWrapper(Permission.callLog),
                // For matching different SIM country codes when using multiple SIM cards,
                //  for frequent international travellers.
                PermissionWrapper(Permission.phoneState, isOptional =  true),
            )
        ) { granted ->
            if (granted) {
                spf.isEnabled = true
                isEnabled = true
            }
        }
    }

    // config popup
    val warningTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = warningTrigger,
        buttons = {
            StrokeButton(
                label = Str(R.string.acknowledged),
                color = Teal200
            ) {
                spf.isWarningAcknowledged = true
                warningTrigger.value = false
                askForPermission()
            }
        },
        title = {
            Text(
                text = Str(R.string.warning),
                color = DarkOrange,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        content = {
            Text(Str(R.string.answered_warning))
        }
    )

    // Balloon text
    val balloonTooltip by remember {
        derivedStateOf {
            ctx.getString(R.string.help_answered)
                .format(
                    ctx.getString(R.string.answered_warning),
                    inXDay,
                    minDuration
                )
        }
    }

    LabeledRow(
        R.string.answered_number,
        helpTooltip = balloonTooltip,
        content = {
            if (isEnabled && Permission.callLog.isGranted) {
                GreyButton(
                    label = PluralStr(inXDay, R.plurals.days),
                ) {
                    configTrigger.value = true
                }
            }
            SwitchBox(isEnabled) { isTurningOn ->
                if (isTurningOn) {
                    val acknowledged = spf.isWarningAcknowledged
                    if (acknowledged) {
                        askForPermission()
                    } else {
                        warningTrigger.value = true
                    }
                } else {
                    spf.isEnabled = false
                    isEnabled = false
                }
            }
        }
    )
}