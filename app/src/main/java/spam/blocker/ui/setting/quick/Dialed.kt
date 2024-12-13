package spam.blocker.ui.setting.quick

import android.Manifest
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PluralStr
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.NormalPermission
import spam.blocker.util.Permissions.isCallLogPermissionGranted
import spam.blocker.util.spf

@Composable
fun Dialed() {
    val ctx = LocalContext.current
    val spf = spf.Dialed(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled() && isCallLogPermissionGranted(ctx)) }
    var inXDay by remember { mutableStateOf<Int?>(spf.getDays()) }

    // popup
    val popupTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = popupTrigger,
        content = {
            NumberInputBox(
                intValue = inXDay,
                onValueChange = { newValue, hasError ->
                    if (!hasError) {
                        inXDay = newValue
                        spf.setDays(newValue!!)
                    }
                },
                label = { Text(Str(R.string.within_days)) },
                leadingIconId = R.drawable.ic_duration,
            )
        })

    LabeledRow(
        R.string.dialed_number,
        helpTooltipId = R.string.help_dialed,
        content = {
            if (isEnabled && isCallLogPermissionGranted(ctx)) {
                GreyButton(
                    label = PluralStr(inXDay!!, R.plurals.days),
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