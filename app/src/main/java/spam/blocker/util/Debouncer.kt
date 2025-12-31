package spam.blocker.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
    Usage:
        val debouncer = Debouncer()

        debouncer.debounce {
            // This runs only after 300ms of inactivity
            searchRepository.search(query)
        }
 */
class LockedDebouncer(
    private val scope: CoroutineScope = CoroutineScope(IO),
    private val waitMs: Long = 500L
) {
    private var job: Job? = null
    private val mutex = Mutex()  // Protects the action execution

    fun debounce(action: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(waitMs)
            mutex.withLock {
                action()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
