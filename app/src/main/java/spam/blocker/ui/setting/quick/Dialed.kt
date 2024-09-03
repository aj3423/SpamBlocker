package spam.blocker.ui.setting.quick

import android.Manifest
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.util.M
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PluralStr
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionChain
import spam.blocker.util.Permissions.Companion.isCallLogPermissionGranted
import spam.blocker.util.SharedPref.Dialed

@Composable
fun Dialed() {
    val ctx = LocalContext.current
    val spf = Dialed(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled() && isCallLogPermissionGranted(ctx)) }
    var inXDay by remember { mutableStateOf<Int?>(spf.getDays()) }

    // popup
    val popupTrigger = remember { mutableStateOf(false) }

    PopupDialog(
        trigger = popupTrigger,
        content = {
//            OutlinedTextField(value = "aa", onValueChange = {}, label = {Text("AAA")} )
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

    val permChain = remember {
        PermissionChain(
            ctx,
            listOf(
                Permission(Manifest.permission.READ_CALL_LOG),
                Permission(Manifest.permission.READ_SMS, true)
            )
        )
    }
    permChain.Compose()

    LabeledRow(
        R.string.dialed,
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
                    permChain.ask { granted ->
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