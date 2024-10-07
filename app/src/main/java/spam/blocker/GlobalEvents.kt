package spam.blocker

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import spam.blocker.db.BotTable
import spam.blocker.db.reScheduleBot
import spam.blocker.ui.history.reScheduleHistoryCleanup
import spam.blocker.ui.setting.quick.reScheduleSpamDBCleanup

@Immutable
object Events {

    // An event for notifying the configuration has changed,
    // observers should restart, such as:
    //  - history cleanup schedule
    //  - spam db cleanup schedule
    val configImported = MutableLiveData<Int>()

    fun setup(ctx: Context) {
        configImported.observeForever  {
            // cancel all
            WorkManager.getInstance(ctx).cancelAllWork()

            // Re-schedule history cleanup task
            reScheduleHistoryCleanup(ctx)

            // Re-schedule spam db cleanup task
            reScheduleSpamDBCleanup(ctx)

            // Re-schedule all bots
            val bots = BotTable.listAll(ctx)
            bots.forEach {
                reScheduleBot(ctx, it)
            }
        }
    }
}
