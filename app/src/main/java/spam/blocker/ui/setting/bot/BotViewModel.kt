package spam.blocker.ui.setting.bot

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.util.spf

class BotViewModel {
    val bots = mutableStateListOf<Bot>()
    val listCollapsed = mutableStateOf(false)


    fun reload(ctx: Context) {
        bots.clear()

        val all = BotTable.listAll(ctx)
        bots.addAll(all)

        listCollapsed.value = spf.BotOptions(ctx).isListCollapsed()
    }

    fun toggleCollapse(ctx: Context) {
        // don't collapse if it's empty
        if (bots.isEmpty() && !listCollapsed.value) {
            return
        }

        listCollapsed.value = !listCollapsed.value
        spf.BotOptions(ctx).setListCollapsed(listCollapsed.value)
    }
}
