package spam.blocker.ui.setting

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.service.CallScreeningService
import spam.blocker.service.SmsReceiver
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.SimPicker
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.JetpackTextLogger


class TestingViewModel {
    val selectedType = mutableIntStateOf(0)
    val phone = mutableStateOf("")
    val sms = mutableStateOf("")
    val simSlot = mutableStateOf<Int?>(null)
}


@Composable
fun PopupTesting(
    trigger: MutableState<Boolean>,
) {
    val ctx = LocalContext.current
    val C = LocalPalette.current

    val coroutine = rememberCoroutineScope()

    val items = remember {
        listOf(
            RadioItem(text = ctx.getString(R.string.call), color = C.textGrey),
            RadioItem(text = ctx.getString(R.string.sms), color = C.textGrey),
        )
    }

    var logStr = remember { mutableStateOf(buildAnnotatedString {}) }

    // Log output dialog
    val logTrigger = rememberSaveable { mutableStateOf(false) }
    PopupDialog(
        trigger = logTrigger,
        popupSize = PopupSize(percentage = 0.7f, minWidth = 320, maxWidth = 600),
    ) {
        Text(
            text = logStr.value,
            color = C.textGrey, // the default text color
        )
    }


    fun clearPreviousResult() {
        logStr.value = buildAnnotatedString {  }
    }

    val vm = G.testingVM

    PopupDialog(
        trigger = trigger,
        popupSize = PopupSize(percentage = 0.8f, minWidth = 340, maxWidth = 600),
        title = {
            RowVCenter {
                GreyLabel(Str(R.string.title_rule_testing))
                BalloonQuestionMark(Str(R.string.help_test_rules))
            }
        },
        buttons = {

            val isForCall by remember {
                derivedStateOf {
                    vm.selectedType.intValue == 0
                }
            }

            // "Please enable call/sms first"
            val warningTrigger = remember { mutableStateOf(false) }
            PopupDialog(
                trigger = warningTrigger
            ) {
                Text(
                    text = Str(
                        if (isForCall)
                            R.string.enable_call_screening_first
                        else
                            R.string.enable_sms_screening_first
                    ),
                    color = DarkOrange,
                )
                GreyText(Str(R.string.it_is_at_top))
            }

            // Test Button
            StrokeButton(label = Str(R.string.test), color = Teal200) {
                // Prompt to "enable the call/sms option first"
                if ((isForCall && !G.callEnabled.value) || (!isForCall && !G.smsEnabled.value)) {
                    warningTrigger.value = true
                    return@StrokeButton
                }

                clearPreviousResult()
                logTrigger.value = true

                val textLogger = JetpackTextLogger(logStr, C)

                coroutine.launch(IO) {
                    if (isForCall)
                        CallScreeningService().processCall(ctx, textLogger, vm.phone.value, simSlot = vm.simSlot.value)
                    else
                        SmsReceiver().processSms(ctx, textLogger, vm.phone.value, vm.sms.value, simSlot = vm.simSlot.value)
                }
            }
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Type   [Call, SMS]
                LabeledRow(labelId = R.string.type) {
                    RadioGroup(items = items, selectedIndex = vm.selectedType.intValue) { newSel ->
                        vm.selectedType.intValue = newSel
                    }
                }

                // SIM
                LabeledRow(
                    labelId = R.string.sim_card,
                    color = if(Build.VERSION.SDK_INT < 12) C.disabled else null,
                    helpTooltip = Str(R.string.help_test_sim_card)
                ) {
                    SimPicker(vm.simSlot)
                }

                // Phone number
                StrInputBox(
                    text = vm.phone.value,
                    label = { GreyLabel(Str(R.string.phone_number)) },
                    leadingIconId = R.drawable.ic_call,
                    onValueChange = {
                        vm.phone.value = it
                        clearPreviousResult()
                    },
                )
                // SMS content
                AnimatedVisibleV(vm.selectedType.intValue != Def.ForNumber) {
                    StrInputBox(
                        text = vm.sms.value,
                        label = { GreyLabel(Str(R.string.sms_content)) },
                        leadingIconId = R.drawable.ic_sms,
                        onValueChange = {
                            vm.sms.value = it
                            clearPreviousResult()
                        }
                    )
                }
            }
        }
    )
}
