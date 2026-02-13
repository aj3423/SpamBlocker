package spam.blocker.ui.setting.api

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.AutoReportTypes
import spam.blocker.db.IApi
import spam.blocker.db.QueryApi
import spam.blocker.db.ReportApi
import spam.blocker.service.bot.allChainable
import spam.blocker.service.bot.apiActions
import spam.blocker.service.bot.rememberSaveableActionList
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.bot.ActionHeader
import spam.blocker.ui.setting.bot.ActionList
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.Button
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.ResImage
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Lambda1
import spam.blocker.util.hasFlag
import spam.blocker.util.setFlag

private val autoReportFlags = listOf(
    AutoReportTypes.NonContact,
    AutoReportTypes.STIR,
    AutoReportTypes.NumberRegex
)
private val autoReportIcons = listOf(
    R.drawable.ic_contact_square,
    R.drawable.ic_telco,
    R.drawable.ic_regex
)
private val autoReportLabelIds = listOf(
    R.string.non_contact,
    R.string.stir_attestation,
    R.string.regex_pattern
)

@Composable
fun PopupEditAutoReport(
    trigger: MutableState<Boolean>,
    flags: MutableState<Int?>
) {
    PopupDialog(trigger) {
        Column {
            for (i in 0..2) {
                LabeledRow(
                    autoReportLabelIds[i],
                ) {
                    SwitchBox(
                        checked = flags.value!!.hasFlag(autoReportFlags[i]),
                        onCheckedChange = { isTurningOn ->
                            flags.value = flags.value!!.setFlag(autoReportFlags[i], isTurningOn)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AutoReportIcons(
    autoReportTypes: Int
) {
    val C = LocalPalette.current

    RowVCenterSpaced(4, modifier = M.padding(start = 12.dp)) {
        for (i in 0..2) {
            val hasFlag = autoReportTypes.hasFlag(autoReportFlags[i]) == true
            ResImage(
                autoReportIcons[i],
                if(hasFlag) C.enabled else C.disabled,
                M.size(18.dp)
            )
        }
    }
}
@Composable
fun AutoReportTypesButton(
    autoReportTypes: MutableState<Int?>
) {
    val editTrigger = remember { mutableStateOf(false) }
    PopupEditAutoReport(editTrigger, autoReportTypes)

    Button(
        content = {
            AutoReportIcons(autoReportTypes.value!!)
        }
    ) {
        editTrigger.value = true
    }
}

@Composable
fun EditApiDialog(
    trigger: MutableState<Boolean>,
    onSave: Lambda1<IApi>,
    initial: IApi,
) {
    if (!trigger.value) {
        return
    }

    val C = LocalPalette.current
    val ctx = LocalContext.current

    val isReportApi = rememberSaveable { initial is ReportApi}
    var description by rememberSaveable { mutableStateOf(initial.desc) }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    val actions = rememberSaveableActionList(initial.actions)
    // for ReportApi
    val autoReportTypes = rememberSaveable {
        mutableStateOf<Int?>(
            if (isReportApi) (initial as ReportApi).autoReportTypes else null
        )
    }


    // if any error, disable the Save button
    val anyError = !actions.allChainable()

    PopupDialog(
        trigger = trigger,
        popupSize = PopupSize(percentage = 0.9f, minWidth = 340, maxWidth = 600),
        buttons = {
            StrokeButton(
                label = Str(R.string.save),
                color = if (anyError) C.disabled else Teal200,
                enabled = !anyError,
                onClick = {
                    // Gather all required permissions for all actions
                    val requiredPermissions = actions.map { it.requiredPermissions(ctx) }.flatten()

                    G.permissionChain.ask(ctx, requiredPermissions) { isGranted ->
                        if (isGranted) {
                            trigger.value = false

                            val newApi = if(initial is QueryApi) {
                                initial.copy( // copy `id` field to new object
                                    desc = description,
                                    enabled = enabled,
                                    actions = actions,
                                )
                            } else {
                                (initial as ReportApi).copy(
                                    desc = description,
                                    enabled = enabled,
                                    actions = actions,
                                    autoReportTypes = autoReportTypes.value!!
                                )
                            }

                            onSave(newApi)
                        }
                    }
                }
            )
        },
        content = {
            Column {
                // Description
                StrInputBox(
                    text = description,
                    label = { Text(Str(R.string.description), color = Color.Unspecified) },
                    onValueChange = { description = it },
                    leadingIconId = R.drawable.ic_note,
                    maxLines = 1,
                )
                Spacer(modifier = M.height(8.dp))

                // Enabled
                LabeledRow(R.string.enable) {
                    SwitchBox(checked = enabled, onCheckedChange = { isTurningOn ->
                        enabled = isTurningOn
                    })
                }

                // AutoReport
                AnimatedVisibleV(enabled && isReportApi) {
                    LabeledRow(
                        labelId = R.string.auto_report,
                        helpTooltip = Str(R.string.help_auto_report_type)
                    ) {
                        AutoReportTypesButton(autoReportTypes!!)
                    }
                }

                // Actions
                AnimatedVisibleV(enabled) {
                    Section(
                        title = Str(R.string.workflow),
                        bgColor = C.dialogBg
                    ) {
                        Column(modifier = M.fillMaxWidth()) {
                            // Action Header
                            ActionHeader(
                                currentActions = actions,
                                availableActions = apiActions,
                                testingRequireNumber = true,
                            )

                            // Action List
                            ActionList(actions = actions)
                        }
                    }
                }
            }
        }
    )
}