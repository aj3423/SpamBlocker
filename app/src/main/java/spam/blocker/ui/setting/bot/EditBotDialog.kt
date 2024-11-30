package spam.blocker.ui.setting.bot

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
import spam.blocker.db.Bot
import spam.blocker.db.reScheduleBot
import spam.blocker.service.bot.allChainable
import spam.blocker.service.bot.clone
import spam.blocker.service.bot.defaultSchedules
import spam.blocker.service.bot.rememberSaveableActionList
import spam.blocker.service.bot.rememberSaveableScheduleState
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Spinner
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Lambda1


@Composable
fun EditBotDialog(
    trigger: MutableState<Boolean>,
    onSave: Lambda1<Bot>,
    initial: Bot,
) {
    if (!trigger.value) {
        return
    }

    val C = LocalPalette.current
    val ctx = LocalContext.current

    var description by rememberSaveable { mutableStateOf(initial.desc) }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    val schedule = rememberSaveableScheduleState(initial.schedule)
    val actions = rememberSaveableActionList(initial.actions)


    // if any error, disable the Save button
    val anyError = !actions.allChainable() || !(schedule.value?.isValid() ?: false)

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

                            val newBot = initial.copy(
                                desc = description,
                                enabled = enabled,
                                schedule = schedule.value,
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

                // Schedule
                Section(
                    title = Str(R.string.schedule),
                    bgColor = C.dialogBg
                ) {
                    Column {
                        // Enabled switch box
                        LabeledRow(R.string.enable) {
                            SwitchBox(checked = enabled, onCheckedChange = { isTurningOn ->
                                if (isTurningOn && schedule.value == null) {
                                    schedule.value = defaultSchedules[0].clone()
                                }
                                enabled = isTurningOn
                            })
                        }
                        AnimatedVisibleV(enabled) {
                            Column {
                                LabeledRow(R.string.type) {
                                    val items = defaultSchedules.map {
                                        LabelItem(
                                            label = it.label(ctx),
                                            icon = { GreyIcon16(it.iconId()) }
                                        ) { menuExpanded ->
                                            schedule.value = it
                                            menuExpanded.value = false
                                        }
                                    }
                                    val selected = defaultSchedules.indexOfFirst {
                                        it::class == schedule.value!!::class
                                    }
                                    Spinner(items = items, selected = selected)
                                }

                                val triggerConfigSchedule = rememberSaveable { mutableStateOf(false) }
                                EditScheduleDialog(trigger = triggerConfigSchedule, schedule)
                                LabeledRow(R.string.time) {
                                    GreyButton(label = schedule.value!!.summary(ctx)) {
                                        triggerConfigSchedule.value = true
                                    }
                                }
                            }
                        }
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