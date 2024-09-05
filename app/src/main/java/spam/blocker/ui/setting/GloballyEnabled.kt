package spam.blocker.ui.setting

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Orange
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.ConfirmDialog
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.ResImage
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionChain
import spam.blocker.util.Permissions
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.Util

@Composable
fun GloballyEnabled() {
    val ctx = LocalContext.current
    val C = LocalPalette.current
    val spf = Global(ctx)

    fun checkCallState(): Boolean {
        return spf.isCallEnabled() && Permissions.isCallScreeningEnabled(ctx)
    }

    fun checkSmsState(): Boolean {
        return spf.isSmsEnabled() && Permissions.isReceiveSmsPermissionGranted(ctx)
    }

    var callState by remember { mutableStateOf(checkCallState()) }
    var smsState by remember { mutableStateOf(checkSmsState()) }

    val doubleSmsWarningTrigger = remember(smsState) { mutableStateOf(false) }
    if (doubleSmsWarningTrigger.value) {
        ConfirmDialog(
            trigger = doubleSmsWarningTrigger,
            icon = { ResIcon(R.drawable.ic_warning, color= Color.Unspecified) },
            negative = {
                StrokeButton(label = Str(R.string.dismiss), color = Orange) {
                    spf.dismissDoubleSMSWarning()
                    doubleSmsWarningTrigger.value = false
                }
            },
            positive = {
                StrokeButton(label = Str(R.string.open_settings), color = Teal200) {
                    Util.openSettingForDefaultSmsApp(ctx)
                }
            }
        ) {
            GreyLabel(ctx.resources.getString(R.string.warning_double_sms))
        }
    }

    // Check double sms issue when `onResume`
    LifecycleResumeEffect(smsState) {
        if (smsState)
            Util.checkDoubleNotifications(ctx, doubleSmsWarningTrigger)
        onPauseOrDispose { }
    }

    val popupTrigger = remember { mutableStateOf(false) }

    PopupDialog(
        trigger = popupTrigger,
        content = {
            Column {
                LabeledRow(labelId = R.string.enabled_for_call) {
                    SwitchBox(checked = callState, onCheckedChange = { isTurningOn ->
                        if (isTurningOn) {
                            Permissions.launcherSetAsCallScreeningApp { granted ->
                                if (granted) {
                                    spf.setCallEnabled(true)
                                    callState = checkCallState()
                                }
                            }
                        } else {
                            spf.setCallEnabled(false)
                            callState = checkCallState()
                        }
                    })
                }

                val permChain = PermissionChain(
                    ctx,
                    listOf(Permission(Manifest.permission.RECEIVE_SMS))
                ).apply { Compose() }

                LabeledRow(labelId = R.string.enabled_for_sms) {
                    SwitchBox(checked = smsState, onCheckedChange = { isTurningOn ->
                        if (isTurningOn) {
                            permChain.ask { granted ->
                                if (granted) {
                                    spf.setSmsEnabled(true)
                                    smsState = checkSmsState()
                                }
                            }
                        } else {
                            spf.setSmsEnabled(false)
                            smsState = checkSmsState()
                        }
                    })
                }
            }
        })

    LabeledRow(
        R.string.globally_enabled,
        paddingHorizontal = 22,
        helpTooltipId = R.string.help_globally_enabled,
        content = {
            if (G.globallyEnabled.value) {

                Box(modifier = M
                    .clickable {
                        popupTrigger.value = true
                    }) {
                    Row {
                        ResImage(
                            R.drawable.ic_call,
                            if (callState) C.enabled else C.disabled,
                            M.padding(end = 4.dp)
                        )
                        ResImage(
                            R.drawable.ic_sms,
                            if (smsState) C.enabled else C.disabled,
                            M.padding(end = 16.dp)
                        )
                    }
                }
            }
            SwitchBox(G.globallyEnabled.value) { enabled ->
                Global(ctx).setGloballyEnabled(enabled)
                G.globallyEnabled.value = enabled
            }
        }
    )
}
