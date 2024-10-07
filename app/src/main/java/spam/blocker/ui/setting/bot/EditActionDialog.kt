package spam.blocker.ui.setting.bot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.parseAction
import spam.blocker.service.bot.serialize
import spam.blocker.ui.widgets.PopupDialog

// when this dialog closes, the actions[i] will be updated
@Composable
fun EditActionDialog(
    trigger: MutableState<Boolean>,
    actions: SnapshotStateList<IAction>,
    index: Int,
) {
    if (!trigger.value) {
        return
    }
    PopupDialog(
        trigger = trigger,
        onDismiss = {
            // set to itself to refresh the UI
            val clone = actions[index].serialize().parseAction()
            actions[index] = clone
        }
    ) {
        actions[index].Options()
    }
}
