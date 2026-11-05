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
import spam.blocker.util.FuzzyFilter

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
    val multiSelect = mutableStateOf(false)
    val selectedIds = mutableStateListOf<Long>()

    fun toggleSelected(id: Long) {
        if (!selectedIds.remove(id)) selectedIds.add(id)
    }

    fun clearSelection() {
        selectedIds.clear()
        multiSelect.value = false
    }

    fun deleteSelected(ctx: Context) {
        val ids = selectedIds.toSet()
        ids.forEach { table.deleteById(ctx, it) }
        records.removeAll { it.id in ids }
        clearSelection()
    }

    fun reload(ctx: Context) {
        records.clear()

        val all = table.listRecords(ctx)
        val filtered = if (filter.value.isNotEmpty()) {
            // Fuzzy search
            val filter = FuzzyFilter(filter.value)

            all.filter { isVisible(ctx, it, filter) }
        } else {
            all
        }
        records.addAll(filtered)
        selectedIds.retainAll(records.map { it.id }.toSet())
    }

    fun isVisible(
        ctx: Context,
        record: HistoryRecord,

        // provide this param when calling this function repetitively (for better performance)
        filter: FuzzyFilter ?= null
    ) : Boolean {
        // 1. show or not
        val show = (showHistoryPassed.value && record.isNotBlocked()) || (showHistoryBlocked.value && record.isBlocked())
        if (!show)
            return false

        // 2. fuzzy filter by keywords
        return if(!searchEnabled.value) { // not filtering
            true
        } else {
            if (filter == null) {
                true
            } else {
                val contactName = Contacts.cache.findContactByRawNumber(ctx, record.peer)?.name ?: ""
                val allText = record.peer + contactName + (record.extraInfo ?: "") + record.reason
                filter.matches(text = allText)
            }
        }
    }
    fun updateRecord(recordId: Long, changes: HistoryRecord.() -> HistoryRecord) {
        val index = records.indexOfFirst { it.id == recordId }
        if (index != -1) {
            records[index] = records[index].let(changes)
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
