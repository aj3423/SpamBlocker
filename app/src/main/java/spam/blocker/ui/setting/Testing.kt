package spam.blocker.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import kotlinx.coroutines.withContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.service.CallScreeningService
import spam.blocker.service.SmsReceiver
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.TextLogger


class TestingViewModel {
    val selectedType = mutableIntStateOf(0)
    val phone = mutableStateOf("")
    val sms = mutableStateOf("")
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
    ) {
        Text(text = logStr.value)
    }


    fun clearResult() {
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
        buttons = { // Test Button

            StrokeButton(label = Str(R.string.test), color = Teal200) {
                logTrigger.value = true
                clearResult()

                val textLogger = TextLogger(logStr, C)

                coroutine.launch {
                    withContext(IO) {
                        if (vm.selectedType.intValue == 0/* for call */)
                            CallScreeningService().processCall(ctx, textLogger, vm.phone.value)
                        else
                            SmsReceiver().processSms(ctx, textLogger, vm.phone.value, vm.sms.value)
                    }
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
                // Phone number
                StrInputBox(
                    text = vm.phone.value,
                    label = { GreyLabel(Str(R.string.phone_number)) },
                    leadingIconId = R.drawable.ic_call,
                    onValueChange = {
                        vm.phone.value = it
                        clearResult()
                    },
                )
                // SMS content
                if (vm.selectedType.intValue != Def.ForNumber) {
                    StrInputBox(
                        text = vm.sms.value,
                        label = { GreyLabel(Str(R.string.sms_content)) },
                        leadingIconId = R.drawable.ic_sms,
                        onValueChange = {
                            vm.sms.value = it
                            clearResult()
                        }
                    )
                }
            }
        }
    )
}
