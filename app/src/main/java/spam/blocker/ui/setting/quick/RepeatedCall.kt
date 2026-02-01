package spam.blocker.ui.setting.quick

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.spf

@Composable
fun RepeatedCall() {
    val ctx = LocalContext.current
    val C = LocalPalette.current
    val spf = spf.RepeatedCall(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled && Permission.callLog.isGranted) }
    var smsEnabled by remember(Permission.readSMS.isGranted) { mutableStateOf(spf.isSmsEnabled && Permission.readSMS.isGranted) }
    var times by remember { mutableStateOf<Int?>(spf.times) }
    var inXMin by remember { mutableStateOf<Int?>(spf.inXMin) }

    val popupTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = popupTrigger,
        content = {
            Column(modifier = M.widthIn(max = 280.dp)) {
                NumberInputBox(
                    intValue = times,
                    onValueChange = { newValue, hasError ->
                        if (!hasError) {
                            times = newValue
                            spf.times = newValue!!
                        }
                    },
                    labelId = R.string.times,
                    leadingIconId = R.drawable.ic_repeat,
                )

                Spacer(modifier = M.height(10.dp))

                NumberInputBox(
                    intValue = inXMin,
                    onValueChange = { newValue, hasError ->
                        if (!hasError) {
                            inXMin = newValue!!
                            spf.inXMin = newValue
                        }
                    },
                    labelId = R.string.within_minutes,
                    leadingIconId = R.drawable.ic_duration,
                )
                LabeledRow(
                    R.string.include_sms,
                    content = {
                        SwitchBox(smsEnabled) { isTurningOn ->
                            if (isTurningOn) {
                                G.permissionChain.ask(
                                    ctx,
                                    listOf(PermissionWrapper(Permission.readSMS))
                                ) { granted ->
                                    if (granted) {
                                        spf.isSmsEnabled = true
                                        smsEnabled = true
                                    }
                                }
                            } else {
                                spf.isSmsEnabled = false
                                smsEnabled = false
                            }
                        }
                    }
                )
            }
        }
    )

    LabeledRow(
        R.string.repeated_call,
        helpTooltip = Str(R.string.help_repeated_call),
        content = {
            if (isEnabled && Permission.callLog.isGranted) {
                val label = if (times == 1) {
                    "$inXMin ${Str(R.string.min)}"
                } else {
                    "$times / $inXMin ${Str(R.string.min)}"
                }
                StrokeButton(
                    label = label,
                    color = C.textGrey,
                ) {
                    popupTrigger.value = true
                }
            }
            SwitchBox(isEnabled) { isTurningOn ->
                if (isTurningOn) {
                    G.permissionChain.ask(
                        ctx,
                        listOf(
                            PermissionWrapper(Permission.callLog),
                            // For matching different SIM country codes when using multiple SIM cards,
                            //  for frequent international travellers.
                            PermissionWrapper(Permission.phoneState, isOptional = true),
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
}