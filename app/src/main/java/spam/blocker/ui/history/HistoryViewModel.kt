package spam.blocker.ui.history

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import spam.blocker.G
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

        // Fuzzy search
        val filterRegex = fuzzifyFilter()

        records.addAll(table.listRecords(ctx).filter {
            isVisible(ctx, it, filterRegex)
        })
    }

    // `aaa bbb` -> `.*aaa.*bbb.*`
    fun fuzzifyFilter() : String {
        return filter.value.replace(" ", ".*").let { ".*$it.*" }
    }
    fun isVisible(
        ctx: Context,
        record: HistoryRecord,

        // provide this param when calling this function repetitively (for better performance)
        filterRegex: String = fuzzifyFilter()
    ) : Boolean {
        // 1. show or not
        val show = (showHistoryPassed.value && record.isNotBlocked()) || (showHistoryBlocked.value && record.isBlocked())
        if (!show)
            return false

        // 2. fuzzy filter by keywords
        return if(!searchEnabled.value) { // not filtering
            true
        } else {
            val contactName = Contacts.cache.findContactByRawNumber(ctx, record.peer)?.name ?: ""
            val allText = record.peer + contactName + (record.extraInfo ?: "") + record.reason
            filterRegex.regexMatches(allText, Def.DefaultRegexFlags)
        }
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
