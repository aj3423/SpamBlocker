package spam.blocker.ui.setting

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Orange
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.ResImage
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.Util
import spam.blocker.util.Util.isDefaultSmsAppNotificationEnabled
import spam.blocker.util.spf

@Composable
fun GloballyEnabled() {
    val ctx = LocalContext.current
    val C = LocalPalette.current
    val spf = spf.Global(ctx)

    fun checkCallState(): Boolean {
        return spf.isCallEnabled() && Permission.callScreening.isGranted
    }

    fun checkSmsState(): Boolean {
        return spf.isSmsEnabled() &&
                Permission.receiveSMS.isGranted
    }

    fun checkMmsState(): Boolean {
        return checkSmsState()
                && spf.isMmsEnabled()
                && Permission.receiveMMS.isGranted
                && Permission.readSMS.isGranted
    }

    var callEnabled by remember { mutableStateOf(checkCallState()) }
    var smsEnabled by remember { mutableStateOf(checkSmsState()) }
    var mmsEnabled by remember { mutableStateOf(checkMmsState()) }


    val doubleSmsWarningTrigger = remember(smsEnabled) { mutableStateOf(false) }
    if (doubleSmsWarningTrigger.value) {
        PopupDialog(
            trigger = doubleSmsWarningTrigger,
            icon = { ResIcon(R.drawable.ic_warning, color = Color.Unspecified) },
            buttons = {
                StrokeButton(label = Str(R.string.dismiss), color = Orange) {
                    spf.dismissDoubleSMSWarning()
                    doubleSmsWarningTrigger.value = false
                }
                Spacer(modifier = M.width(10.dp))
                StrokeButton(label = Str(R.string.open_settings), color = Teal200) {
                    doubleSmsWarningTrigger.value = false
                    Util.openSettingForDefaultSmsApp(ctx)
                }
            },
        ) {
            RowVCenter {
                GreyLabel(ctx.getString(R.string.warning_double_sms))

                BalloonQuestionMark(ctx.getString(R.string.help_rcs_message))
            }
        }
    }
    // Show warnings `onResume`
    // - double sms notification
    LifecycleResumeEffect(smsEnabled) {
        if (smsEnabled) {
            if (Build.VERSION.SDK_INT >= Def.ANDROID_13) {
                if (isDefaultSmsAppNotificationEnabled(ctx) && spf.isGloballyEnabled() && spf.isSmsEnabled()) {
                    if (!spf.isDoubleSMSWarningDismissed()) {
                        doubleSmsWarningTrigger.value = true
                    }
                }
            }
        }
        onPauseOrDispose { }
    }

    val popupTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = popupTrigger,
        content = {
            Column {
                LabeledRow(labelId = R.string.enabled_for_call) {
                    SwitchBox(checked = callEnabled, onCheckedChange = { isTurningOn ->
                        if (isTurningOn) {
                            G.permissionChain.ask(
                                ctx,
                                listOf(
                                    PermissionWrapper(Permission.callScreening),
                                )
                            ) { granted ->
                                if (granted) {
                                    spf.setCallEnabled(true)
                                    callEnabled = checkCallState()
                                }
                            }
                        } else {
                            spf.setCallEnabled(false)
                            callEnabled = checkCallState()
                        }
                    })
                }

                LabeledRow(labelId = R.string.enabled_for_sms) {
                    SwitchBox(checked = smsEnabled, onCheckedChange = { isTurningOn ->
                        if (isTurningOn) {
                            G.permissionChain.ask(
                                ctx,
                                listOf(
                                    PermissionWrapper(Permission.receiveSMS),
                                    // isOptional because some might prefer "Optimized" than "Unrestricted"
                                    PermissionWrapper(Permission.batteryUnRestricted, isOptional = true),
                                )
                            ) { granted ->
                                if (granted) {
                                    spf.setSmsEnabled(true)
                                    smsEnabled = checkSmsState()
                                }
                            }
                        } else {
                            spf.setSmsEnabled(false)
                            smsEnabled = checkSmsState()
                        }
                    })
                }

                AnimatedVisibleV(smsEnabled) {
                    LabeledRow(
                        labelId = R.string.enable_for_mms,
                        helpTooltip = Str(R.string.help_enable_for_mms),
                    ) {
                        SwitchBox(checked = mmsEnabled, onCheckedChange = { isTurningOn ->
                            if (isTurningOn) {
                                G.permissionChain.ask(
                                    ctx,
                                    listOf(
                                        PermissionWrapper(Permission.receiveMMS),
                                        PermissionWrapper(Permission.readSMS),
                                        PermissionWrapper(Permission.batteryUnRestricted, isOptional = true),
                                        )
                                ) { granted ->
                                    if (granted) {
                                        spf.setMmsEnabled(true)
                                        mmsEnabled = checkMmsState()
                                    }
                                }
                            } else {
                                spf.setMmsEnabled(false)
                                mmsEnabled = checkMmsState()
                            }
                        })
                    }
                }
                LabeledRow(
                    labelId = R.string.rcs_message,
                    helpTooltip = Str(R.string.help_rcs_message),
                    color = C.disabled,
                ) {}
            }
        })

    LabeledRow(
        R.string.enable,
        paddingHorizontal = 22,
        helpTooltip = Str(R.string.help_globally_enabled),
        content = {
            if (G.globallyEnabled.value) {

                Box(modifier = M
                    .clickable {
                        popupTrigger.value = true
                    }) {
                    Row {
                        ResImage(
                            R.drawable.ic_call,
                            if (callEnabled) C.enabled else C.disabled,
                            M.padding(end = 4.dp)
                        )
                        ResImage(
                            R.drawable.ic_sms,
                            if (smsEnabled) C.enabled else C.disabled,
                            M.padding(end = 16.dp)
                        )
                    }
                }
            }
            SwitchBox(G.globallyEnabled.value) { enabled ->
                spf.setGloballyEnabled(enabled)
                G.globallyEnabled.value = enabled
            }
        }
    )
}
