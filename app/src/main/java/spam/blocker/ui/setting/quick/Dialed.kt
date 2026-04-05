package spam.blocker.ui.setting.quick

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.DurationButton
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.PluralStr
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.spf

@Composable
fun Dialed() {
    val ctx = LocalContext.current
    val spf = spf.Dialed(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled && Permission.callLog.isGranted) }
    var smsEnabled by remember(Permission.readSMS.isGranted) { mutableStateOf(spf.isSmsEnabled && Permission.readSMS.isGranted) }
    var always by remember { mutableStateOf(spf.always) }
    var inXDay by remember { mutableIntStateOf(spf.days) }

    // popup
    val popupTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = popupTrigger,
        content = {
            // Duration
            LabeledRow(R.string.time_range) {
                DurationButton(
                    alwaysEnabled = always,
                    alwaysLabelId = R.string.all_time,
                    onEnableChange = { isOn ->
                        spf.always = isOn
                        always = isOn
                    },
                    duration = inXDay,
                    onDurationChange = { newValue, hasError ->
                        if (!hasError) {
                            inXDay = newValue!!
                            spf.days = newValue
                        }
                    },
                    durationLabelId = R.string.within_days,
                )
            }

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
        })

    // Balloon text
    val balloonTooltip by remember {
        derivedStateOf {
            ctx.getString(R.string.help_dialed)
                .format(inXDay)
        }
    }

    LabeledRow(
        R.string.dialed_number,
        helpTooltip = balloonTooltip,
        content = {
            if (isEnabled && Permission.callLog.isGranted) {
                GreyButton(
                    label = if (!always) {
                        PluralStr(inXDay, R.plurals.days)
                    } else {
                        Str(R.string.all_time)
                    }
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
                            //  for frequent international travelers.
                            PermissionWrapper(Permission.phoneState, isOptional =  true),
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