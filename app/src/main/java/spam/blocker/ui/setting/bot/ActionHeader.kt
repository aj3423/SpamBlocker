package spam.blocker.ui.setting.bot

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.R
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.clone
import spam.blocker.service.bot.defaultActions
import spam.blocker.service.bot.executeAll
import spam.blocker.ui.M
import spam.blocker.ui.setting.SettingRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.MenuButton
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton

@Composable
fun TestActionButton(
    actions: SnapshotStateList<IAction>
) {
    val ctx = LocalContext.current
    val C = LocalPalette.current

    val trigger = rememberSaveable { mutableStateOf(false) }

    var finished by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    PopupDialog(
        trigger = trigger,
    ) {
        Text(
            text = if (finished) {
                if (error == null) {
                    Str(R.string.success)
                } else {
                    Str(R.string.failed) + ": " + error
                }
            } else {
                Str(R.string.running)
            },
            color = if (!finished) {
                C.textGrey
            } else {
                if (error == null) C.pass else C.block
            }
        )
    }

    val coroutine = rememberCoroutineScope()
    StrokeButton(label = Str(R.string.test), color = Teal200) {
        trigger.value = true

        finished = false
        error = null

        coroutine.launch {
            withContext(Dispatchers.IO) {
                error = actions.executeAll(ctx)
                finished = true
            }
        }
    }
}

@Composable
fun ActionHeader(
    actions: SnapshotStateList<IAction>
) {
    val ctx = LocalContext.current

    val dropdownActionItems = defaultActions.mapIndexed { index, action ->
        LabelItem(
            label = action.label(ctx),
            icon = { action.Icon() },
            tooltip = action.tooltip(ctx)
        ) {
            val newAction = defaultActions[index].clone()
            actions.add(newAction)
        }
    }

    SettingRow(
        modifier = M.fillMaxWidth()
    ) {
        Spacer(modifier = M.weight(1f)) // align the button at the end

        RowVCenterSpaced(4) {
            // Tooltip
            BalloonQuestionMark(Str(R.string.help_action_header))

            // Test
            TestActionButton(actions = actions)

            // New
            MenuButton(
                label = Str(R.string.new_),
                color = SkyBlue,
                items = dropdownActionItems
            )
        }
    }
}

