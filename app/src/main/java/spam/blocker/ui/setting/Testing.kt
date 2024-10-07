package spam.blocker.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.service.CallScreeningService
import spam.blocker.service.Checker
import spam.blocker.service.SmsReceiver
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.DrawableImage
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.AppInfo


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

    val items = remember {
        listOf(
            RadioItem(text = ctx.getString(R.string.call), color = C.textGrey),
            RadioItem(text = ctx.getString(R.string.sms), color = C.textGrey),
        )
    }

    var passOrBlock by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var recentAppPkg by remember { mutableStateOf("") }

    fun clearResult() {
        result = ""
        recentAppPkg = ""
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
                clearResult()

                val r = if (vm.selectedType.intValue == 0/* for call */)
                    CallScreeningService().processCall(ctx, vm.phone.value)
                else
                    SmsReceiver().processSms(ctx, vm.phone.value, vm.sms.value)

                // set result text color
                result = Checker.resultStr(ctx, r.result, r.reason())
                passOrBlock = !r.shouldBlock

                if (r.result == Def.RESULT_ALLOWED_BY_RECENT_APP) {
                    recentAppPkg = r.reason()
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
//                // SMS content
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
                RowVCenter(
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RowVCenter {
                        if (result != "") {

                            Text(
                                text = result,
                                color = if (passOrBlock) C.pass else C.block,
                            )
                            if (recentAppPkg != "") {
                                DrawableImage(
                                    AppInfo.fromPackage(ctx, recentAppPkg).icon,
                                    modifier = M
                                        .size(24.dp)
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
