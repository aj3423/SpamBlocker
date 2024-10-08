package spam.blocker.ui.setting.bot

import android.content.Context
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
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.reScheduleBot
import spam.blocker.service.bot.CleanupSpamDB
import spam.blocker.service.bot.Daily
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.allChainable
import spam.blocker.service.bot.clone
import spam.blocker.service.bot.defaultSchedules
import spam.blocker.service.bot.rememberSaveableActionList
import spam.blocker.service.bot.rememberSaveableScheduleState
import spam.blocker.service.bot.serialize
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.GreyButton
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
import spam.blocker.util.SharedPref.SpamDB
import java.util.UUID


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

    val schedule = rememberSaveableScheduleState(initial.schedule)
    val actions = rememberSaveableActionList(initial.actions)

    // Description
    var description by rememberSaveable { mutableStateOf(initial.desc) }

    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var workUUID by rememberSaveable { mutableStateOf(initial.workUUID) }

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
                    trigger.value = false

                    val newBot = Bot(
                        id = initial.id,
                        desc = description,
                        schedule = schedule.value,
                        actions = actions,
                        enabled = enabled,
                        workUUID = workUUID,
                    )

                    reScheduleBot(ctx, newBot)

                    onSave(newBot)
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
                        LabeledRow(R.string.enabled) {
                            SwitchBox(checked = enabled, onCheckedChange = { isTurningOn ->
                                if (isTurningOn && schedule.value == null) {
                                    schedule.value = defaultSchedules[0].clone()
                                }
                                enabled = isTurningOn
                            })
                        }
                        if (enabled) {
                            LabeledRow(R.string.type) {
                                val items = defaultSchedules.map {
                                    LabelItem(label = it.label(ctx)) { menuExpanded ->
                                        schedule.value = it
                                        menuExpanded.value = false
                                    }
                                }
                                val selected = defaultSchedules.indexOfFirst {
                                    it.type() == schedule.value!!.type()
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

                // Actions
                Section(
                    title = Str(R.string.workflow),
                    bgColor = C.dialogBg
                ) {
                    Column(modifier = M.fillMaxWidth()) {
                        // Action Header
                        ActionHeader(actions = actions)

                        // Action List
                        ActionList(actions = actions)
                    }
                }
            }
        }
    )
}