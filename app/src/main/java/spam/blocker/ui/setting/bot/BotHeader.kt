package spam.blocker.ui.setting.bot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.MenuButton
import spam.blocker.ui.widgets.Str
import spam.blocker.util.loge

// The row:
//   "Number Rule"       [Add] [Test]
@Composable
fun BotHeader() {
    val ctx = LocalContext.current

    val addTrigger = rememberSaveable { mutableStateOf(false) }

    var initial = remember { mutableStateOf(Bot()) }

    if (addTrigger.value) {
        EditBotDialog(
            trigger = addTrigger,
            initial = initial.value,
            onSave = { newBot ->
                // 1. add to db
                BotTable.addNewRecord(ctx, newBot)

                // 2. reload UI
                G.BotVM.reload(ctx)
            }
        )
    }

    val dropdownItems = remember {
        val ret = mutableListOf(
            LabelItem(label = ctx.getString(R.string.customize)) {
                initial.value = Bot()
                addTrigger.value = true
            },
            DividerItem(),
        )
        ret += BotPresets.map { preset ->
            val bot = preset.newInstance(ctx)
            LabelItem(
                label = bot.desc,
                tooltip = ctx.getString(preset.tooltipId)
            ) {
                initial.value = bot

                addTrigger.value = true
            }
        }
        ret
    }

    LabeledRow(
        labelId = R.string.workflows,
        helpTooltipId = R.string.help_workflows,
    ) {
        MenuButton(
            label = Str(R.string.new_),
            color = SkyBlue,
            items = dropdownItems,
        )
    }
}
