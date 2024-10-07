package spam.blocker.ui.setting.bot

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import spam.blocker.db.Bot
import spam.blocker.db.BotTable

class BotViewModel {
    val list = mutableStateListOf<Bot>()

    fun reload(ctx: Context) {
        list.clear()

        val all = BotTable.listAll(ctx)
        list.addAll(all)
    }
}
