package spam.blocker.ui.setting.bot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.parseAction
import spam.blocker.service.bot.serialize
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.util.Lambda1

// when this dialog closes, the actions[i] will be updated
@Composable
fun EditActionDialog(
    trigger: MutableState<Boolean>,
    initial: IAction,
    callback: Lambda1<IAction>,
) {
    val edited = remember(initial) { mutableStateOf(initial) }

    PopupDialog(
        trigger = trigger,
        onDismiss = {
            // set to itself to refresh the UI
            val clone = edited.value.serialize().parseAction()
            callback(clone)
        }
    ) {
        edited.value.Options()
    }
}
