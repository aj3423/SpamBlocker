package spam.blocker

import androidx.compose.runtime.Immutable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import spam.blocker.util.Lambda1
import java.util.UUID

@Immutable
class EventData(
    val parameter: Any?,
) {
    // Make sure each event data is different
    val uuid: UUID = UUID.randomUUID()
}

class Event {
    private val liveData = MutableLiveData<EventData>()

    fun listen(callback: Lambda1<Any?>) {
        liveData.observeForever {
            callback(liveData.value?.parameter)
        }
    }

    fun listen(lifecycleOwner: LifecycleOwner, callback: Lambda1<Any?>) {
        liveData.observe(lifecycleOwner) {
            callback(liveData.value?.parameter)
        }
    }

    fun fire(parameter: Any? = null) {
        liveData.postValue(
            EventData(parameter)
        )
    }
}

object Events {

    // An event triggered when spam db is updated, maybe triggered by Workflow
    val spamDbUpdated = Event()

    // An event triggered when regex rule list is updated, maybe triggered by Workflow
    val regexRuleUpdated = Event()

    // An event for notifying the configuration has changed,
    // observers should restart, such as:
    //  - history cleanup task
    //  - spam db cleanup task
    //  - all bots
    val configImported = Event()
}
