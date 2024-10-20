package spam.blocker.ui.setting.bot

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.util.SharedPref.BotOptions

class BotViewModel {
    val bots = mutableStateListOf<Bot>()
    val listCollapsed = mutableStateOf(false)


    fun reload(ctx: Context) {
        bots.clear()

        val all = BotTable.listAll(ctx)
        bots.addAll(all)

        listCollapsed.value = BotOptions(ctx).isListCollapsed()
    }

    fun toggleCollapse(ctx: Context) {
        listCollapsed.value = !listCollapsed.value
        BotOptions(ctx).setListCollapsed(listCollapsed.value)
    }
}
