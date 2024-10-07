package spam.blocker.ui.setting.bot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import spam.blocker.service.bot.ISchedule
import spam.blocker.service.bot.parseSchedule
import spam.blocker.service.bot.serialize
import spam.blocker.ui.widgets.PopupDialog

@Composable
fun EditScheduleDialog(
    trigger: MutableState<Boolean>,
    schedule: MutableState<ISchedule?>,
) {
    if (!trigger.value) {
        return
    }
    PopupDialog(
        trigger = trigger,
        onDismiss = {
            // set to itself to refresh the UI
            val clone = schedule.value!!.serialize().parseSchedule()
            schedule.value = clone
        }
    ) {
        schedule.value!!.Options()
    }
}

