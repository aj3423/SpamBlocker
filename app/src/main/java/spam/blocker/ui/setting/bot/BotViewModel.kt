package spam.blocker.ui.setting.bot

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.def.Def
import spam.blocker.util.SharedPref.BotOptions
import spam.blocker.util.SharedPref.RegexOptions

class BotViewModel {
    val list = mutableStateListOf<Bot>()
    val listCollapsed = mutableStateOf(false)


    fun reload(ctx: Context) {
        list.clear()

        val all = BotTable.listAll(ctx)
        list.addAll(all)

        listCollapsed.value = BotOptions(ctx).isListCollapsed()
    }

    fun toggleCollapse(ctx: Context) {
        listCollapsed.value = !listCollapsed.value
        BotOptions(ctx).setListCollapsed(listCollapsed.value)
    }
}
