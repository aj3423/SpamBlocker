package spam.blocker.ui.setting.bot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import spam.blocker.db.Bot
import spam.blocker.db.reScheduleBot
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.ITriggerAction
import spam.blocker.service.bot.allChainable
import spam.blocker.service.bot.botTriggers
import spam.blocker.service.bot.clone
import spam.blocker.service.bot.rememberSaveableActionList
import spam.blocker.service.bot.rememberSaveableTriggerState
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.MenuButton
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Lambda
import spam.blocker.util.Lambda1
import spam.blocker.util.logi


@Composable
fun EditBotDialog(
    popupTrigger: MutableState<Boolean>,
    onSave: Lambda1<Bot>,
    onDismiss: Lambda,
    initial: Bot,
) {
    if (!popupTrigger.value) {
        return
    }

    val C = LocalPalette.current
    val ctx = LocalContext.current

    var description by rememberSaveable { mutableStateOf(initial.desc) }
    val trigger = rememberSaveableTriggerState(initial.trigger)
    val actions = rememberSaveableActionList(initial.actions)


    // if any error, disable the Save button
    val anyError = !actions.allChainable()

    PopupDialog(
        trigger = popupTrigger,
        popupSize = PopupSize(percentage = 0.9f, minWidth = 340, maxWidth = 600),
        onDismiss = onDismiss,
        buttons = {
            StrokeButton(
                label = Str(R.string.save),
                color = if (anyError) C.disabled else Teal200,
                enabled = !anyError,
                onClick = {
                    // Gather all required permissions for "trigger + all actions"
                    val requiredPermissions = (actions + trigger.value).map {
                        it.requiredPermissions(ctx)
                    }.flatten()

                    G.permissionChain.ask(ctx, requiredPermissions) { isGranted ->
                        if (isGranted) {
                            popupTrigger.value = false

                            val newBot = initial.copy(
                                desc = description,
                                trigger = trigger.value,
                                actions = actions,
                            )

                            reScheduleBot(ctx, newBot)

                            onSave(newBot)
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

                // Trigger
                Section(
                    title = null,
                    bgColor = C.dialogBg
                ) {
                    Column {
                        // Trigger Type
                        LabeledRow(R.string.trigger) {
                            val triggerItems = remember {
                                botTriggers.mapIndexed { i, trig ->
                                    LabelItem(
                                        label = trig.label(ctx),
                                        leadingIcon = { trig.Icon() },
                                        tooltip = trig.tooltip(ctx)
                                    ) {
                                        trigger.value = trig.clone() as ITriggerAction
                                    }
                                }
                            }
                            MenuButton(
                                label = Str(R.string.choose),
                                items = triggerItems,
                            )
                        }

                        // Trigger Card
                        val editTrigger = remember { mutableStateOf(false) }
                        EditActionDialog(trigger = editTrigger, initial = trigger.value) {
                            trigger.value = it as ITriggerAction
                        }
                        ActionCard(
                            action = trigger.value,
                            modifier = M.clickable {
                                editTrigger.value = true
                            }
                        )
                    }
                }

                // Actions
                Section(
                    title = Str(R.string.workflow),
                    bgColor = C.dialogBg
                ) {
                    Column(modifier = M.fillMaxWidth()) {
                        // Action Header
                        ActionHeader(currentActions = actions)

                        // Action List
                        ActionList(actions = actions)
                    }
                }
            }
        }
    )
}