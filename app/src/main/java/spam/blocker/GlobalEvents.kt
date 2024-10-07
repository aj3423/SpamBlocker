package spam.blocker

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
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

            reScheduleHistoryCleanup(ctx)

            reScheduleSpamDBCleanup(ctx)
        }
    }
}
