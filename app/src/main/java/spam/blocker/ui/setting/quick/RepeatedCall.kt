package spam.blocker.ui.setting.quick

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
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
import spam.blocker.util.NormalPermission
import spam.blocker.util.Permissions.isCallLogPermissionGranted
import spam.blocker.util.spf

@Composable
fun RepeatedCall() {
    val ctx = LocalContext.current
    val C = LocalPalette.current
    val spf = spf.RepeatedCall(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled() && isCallLogPermissionGranted(ctx)) }
    var times by remember { mutableStateOf<Int?>(spf.getTimes()) }
    var inXMin by remember { mutableStateOf<Int?>(spf.getInXMin()) }

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
                            spf.setTimes(newValue!!)
                        }
                    },
                    label = @Composable { Text(Str(R.string.times)) },
                    leadingIconId = R.drawable.ic_repeat,
                )

                Spacer(modifier = M.height(10.dp))

                NumberInputBox(
                    intValue = inXMin,
                    onValueChange = { newValue, hasError ->
                        if (!hasError) {
                            inXMin = newValue!!
                            spf.setInXMin(newValue)
                        }
                    },
                    label = @Composable { Text(Str(R.string.within_minutes)) },
                    leadingIconId = R.drawable.ic_duration,
                )
            }
        })

    LabeledRow(
        R.string.repeated_call,
        helpTooltipId = R.string.help_repeated_call,
        content = {
            if (isEnabled && isCallLogPermissionGranted(ctx)) {
                StrokeButton(
                    label = "$times / $inXMin ${Str(R.string.min)}",
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
                            NormalPermission(Manifest.permission.READ_CALL_LOG),
                            NormalPermission(Manifest.permission.READ_PHONE_STATE, true),
                            NormalPermission(Manifest.permission.READ_SMS, true)
                        )
                    ) { granted ->
                        if (granted) {
                            spf.setEnabled(true)
                            isEnabled = true
                        }
                    }
                } else {
                    spf.setEnabled(false)
                    isEnabled = false
                }
            }
        }
    )
}