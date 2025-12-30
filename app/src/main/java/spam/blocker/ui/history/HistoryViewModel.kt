package spam.blocker.ui.history

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import spam.blocker.db.CallTable
import spam.blocker.db.HistoryRecord
import spam.blocker.db.HistoryTable
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.util.spf

/*
  To simplify the code, this view model is used in GlobalVariables instead of viewModel<...>().
 */
open class HistoryViewModel(
    val forType: Int,
    val table: HistoryTable,
) : ViewModel() {
    val records = mutableStateListOf<HistoryRecord>()
    val searchEnabled = mutableStateOf(false)
    val filter = mutableStateOf("")

    fun reload(ctx: Context) {
        records.clear()

        val spf = spf.HistoryOptions(ctx)
        val showPassed = spf.getShowPassed()
        val showBlocked = spf.getShowBlocked()

        records.addAll(table.listRecords(ctx).filter {
            val show = (showPassed && it.isNotBlocked()) || (showBlocked && it.isBlocked())

            val filtered = if(!searchEnabled.value) {
                true
            } else {
                when(forType) {
                    Def.ForNumber -> { // Call
                        it.peer.contains(filter.value)
                    }
                    else -> { // SMS
                        it.peer.contains(filter.value) || it.extraInfo?.contains(filter.value) == true
                    }
                }
            }

            show && filtered
        })
    }
}

class CallViewModel : HistoryViewModel(Def.ForNumber, CallTable())

class SmsViewModel : HistoryViewModel(Def.ForSms, SmsTable())
