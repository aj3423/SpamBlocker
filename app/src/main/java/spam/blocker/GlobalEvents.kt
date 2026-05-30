package spam.blocker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import spam.blocker.util.Lambda1

class Event {
    private val liveData = MutableLiveData<Any?>()

    // DO NOT call this repeatedly, for example in MainActivity.
    // This should be only used in non-@Composable functions.
    fun listen(callback: Lambda1<Any?>) {
        liveData.observeForever {
            callback(liveData.value)
        }
    }

    // This is used in @Composable functions.
    // It registers a listener on compose and unregister that listener on decompose.
    @Composable
    fun Listen(callback: Lambda1<Any?>) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val latestCallback = rememberUpdatedState(callback)

        DisposableEffect(lifecycleOwner) {
            val observer: Observer<Any?> = Observer { latestCallback.value(it) }
            liveData.observe(lifecycleOwner, observer)

            onDispose {
                liveData.removeObserver(observer)
            }
        }
    }

    fun fire(parameter: Any? = null) {
        liveData.postValue(parameter)
    }
}


object Events {
    // Update Call/SMS tab on incoming call/msg, insert a new record at the top of the list.
    val onNewCall = Event()
    val onNewSMS = Event()

    // An event triggered when history records get updated, maybe triggered by Workflow
    val historyUpdated = Event()

    // An event triggered when spam db is updated, maybe triggered by Workflow
    val spamDbUpdated = Event()

    // An event triggered when regex rule list is updated, maybe triggered by Workflow
    val regexRuleUpdated = Event()

    // An event triggered when one or multiple Bot is updated, maybe triggered by Workflow
    val botUpdated = Event()

    // An event for notifying the configuration has changed,
    // observers should restart, such as:
    //  - history cleanup task
    //  - spam db cleanup task
    //  - all bots
    val configImported = Event()
}
