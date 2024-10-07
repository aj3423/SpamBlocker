package spam.blocker.ui.setting.bot

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.ui.M
import spam.blocker.ui.setting.regex.DisableNestedScrolling
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.SwipeInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BotList() {
    val ctx = LocalContext.current
    val vm = G.BotVM
    val coroutineScope = rememberCoroutineScope()

    val editTrigger = rememberSaveable { mutableStateOf(false) }

    var clickedIndex by rememberSaveable { mutableIntStateOf(0) }

    if (editTrigger.value) {
        EditBotDialog(
            trigger = editTrigger,
            initial = vm.list[clickedIndex],
            onSave = { updatedBot ->
                // 1. update in db
                BotTable.updateById(ctx, updatedBot.id, updatedBot)

                // 2. reload UI
                vm.reload(ctx)
            }
        )
    }
    Column(
        modifier = M.nestedScroll(DisableNestedScrolling()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        vm.list.forEachIndexed { index, bot ->
            key(bot.id) {
                LeftDeleteSwipeWrapper(
                    left = SwipeInfo(
                        onSwipe = {
                            // 1. delete from db
                            BotTable.deleteById(ctx, bot.id)

                            // 2. remove from ArrayList
                            vm.list.removeAt(index)

                            // 3. show snackbar
                            SnackBar.show(
                                coroutineScope,
                                bot.desc,
                                ctx.getString(R.string.undelete),
                            ) {
                                BotTable.addRecordWithId(ctx, bot)
                                vm.list.add(index, bot)
                            }
                        }
                    )
                ) {
                    BotCard(
                        bot,
                        modifier = M.combinedClickable(
                            onClick = {
                                clickedIndex = index
                                editTrigger.value = true
                            },
                        )
                    )
                }
            }
        }
    }
}
