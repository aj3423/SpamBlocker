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
import spam.blocker.ui.history.HistoryOptions.showHistoryBlocked
import spam.blocker.ui.history.HistoryOptions.showHistoryPassed
import spam.blocker.util.Contacts
import spam.blocker.util.regexMatches

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

        val showPassed = showHistoryPassed.value
        val showBlocked = showHistoryBlocked.value

        // Fuzzy search
        // `aaa bbb` -> `.*aaa.*bbb.*`
        val filterRegex = filter.value.replace(" ", ".*").let { ".*$it.*" }

        records.addAll(table.listRecords(ctx).filter {
            // 1. show or not
            val show = (showPassed && it.isNotBlocked()) || (showBlocked && it.isBlocked())

            // 2. fuzzy filter by keywords
            val filtered = if(!searchEnabled.value) {
                true
            } else {
                val contactName = Contacts.cache.findContactByRawNumber(ctx, it.peer)?.name ?: ""
                val allText = it.peer + contactName + (it.extraInfo ?: "") + it.reason
                filterRegex.regexMatches(allText, Def.DefaultRegexFlags)
            }

            show && filtered
        })
    }
    fun markAllAsRead(ctx: Context) {
        val read = records.map { it.copy(read = true) }
        records.apply {
            clear()
            addAll(read)
        }
        table.markAllAsRead(ctx)
    }
}

class CallViewModel : HistoryViewModel(Def.ForNumber, CallTable())

class SmsViewModel : HistoryViewModel(Def.ForSms, SmsTable())
