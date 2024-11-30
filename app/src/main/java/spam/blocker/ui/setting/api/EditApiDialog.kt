package spam.blocker.ui.setting.api

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Api
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
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Lambda1


@Composable
fun EditApiDialog(
    trigger: MutableState<Boolean>,
    onSave: Lambda1<Api>,
    initial: Api,
) {
    if (!trigger.value) {
        return
    }

    val C = LocalPalette.current
    val ctx = LocalContext.current

    var description by rememberSaveable { mutableStateOf(initial.desc) }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    val actions = rememberSaveableActionList(initial.actions)


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
                    val missingPermissions = actions.map { it.missingPermissions(ctx) }.flatten()

                    G.permissionChain.ask(ctx, missingPermissions) { isGranted ->
                        if (isGranted) {
                            trigger.value = false

                            val newApi = initial.copy(
                                desc = description,
                                enabled = enabled,
                                actions = actions,
                            )

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