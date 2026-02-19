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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.Db
import spam.blocker.db.NumberRegexTable
import spam.blocker.def.Def
import spam.blocker.def.Def.ANDROID_12
import spam.blocker.def.Def.FLAG_REGEX_FOR_CNAP
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
import spam.blocker.util.MultiLogger
import spam.blocker.util.SaveableLogger
import spam.blocker.util.Util


class TestingViewModel {
    val selectedType = mutableIntStateOf(0)
    val phone = mutableStateOf("")
    val callerName = mutableStateOf("")
    val sms = mutableStateOf("")
    val simSlot = mutableStateOf<Int?>(null)
}


@Composable
fun TestDialog(
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

    val logStr = remember { mutableStateOf(buildAnnotatedString {}) }

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

                val multiLogger = MultiLogger(listOf(
                    JetpackTextLogger(logStr, C),
                    SaveableLogger()
                ))

                coroutine.launch(IO) {
                    if (isForCall)
                        CallScreeningService().processCall(
                            ctx, rawNumber = vm.phone.value, simSlot = vm.simSlot.value,
                            callDetails = null, cnap = vm.callerName.value.ifEmpty { null },
                            isTest = true, logger = multiLogger,
                        )
                    else
                        SmsReceiver().processSms(
                            ctx, vm.phone.value, vm.sms.value, simSlot = vm.simSlot.value, isTest = true, logger = multiLogger)
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
                    color = if(Build.VERSION.SDK_INT < ANDROID_12) C.disabled else null,
                    helpTooltip = Str(R.string.help_test_sim_card)
                ) {
                    SimPicker(vm.simSlot)
                }

                val geoLocation = remember(vm.phone.value) {
                    Util.numberGeoLocation(ctx, vm.phone.value)
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
                    supportingTextStr = geoLocation?.let {
                        // Geo Location
                        Str(R.string.label_value_pair).format(
                            Str(R.string.geo_location), geoLocation
                        )
                    },
                    supportingTextColor = C.textGrey,
                )

                // Only show the Caller Name field when there's at least 1 CNAP rule configured
                var hasCnapRule by remember(G.NumberRuleVM.rules, G.ContentRuleVM.rules) {
                    // either `patterFlags` or `patternExtraFlags` has CNAP flag
                    val foundNumberRule = NumberRegexTable().findByFilter(ctx,
                        " WHERE (${Db.COLUMN_PATTERN_FLAGS} & ${FLAG_REGEX_FOR_CNAP}) = $FLAG_REGEX_FOR_CNAP LIMIT 1"
                    ).isNotEmpty()

                    val foundContentRule = ContentRegexTable().findByFilter(ctx,
                        " WHERE (${Db.COLUMN_PATTERN_EXTRA_FLAGS} & ${FLAG_REGEX_FOR_CNAP}) = $FLAG_REGEX_FOR_CNAP LIMIT 1"
                    ).isNotEmpty()

                    mutableStateOf(foundNumberRule || foundContentRule)
                }

                // Caller Name
                AnimatedVisibleV(vm.selectedType.intValue == Def.ForNumber && hasCnapRule) {
                    StrInputBox(
                        text = vm.callerName.value,
                        label = { GreyLabel(Str(R.string.caller_name)) },
                        leadingIconId = R.drawable.ic_id_card,
                        onValueChange = {
                            vm.callerName.value = it
                            clearPreviousResult()
                        },
                    )
                }
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
