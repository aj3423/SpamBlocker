package spam.blocker.ui.history

import androidx.lifecycle.ViewModel
import il.co.theblitz.observablecollections.lists.ObservableArrayList
import spam.blocker.db.Record

// This class is only used for sharing data between MainActivity and CallFragment
abstract class HistoryViewModel : ViewModel() {
    var records = ObservableArrayList<Record>()
}
class CallViewModel : HistoryViewModel() {}
class SmsViewModel : HistoryViewModel() {}