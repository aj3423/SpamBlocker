package spam.blocker.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger


// Run multiple blocking functions simultaneously, it returns immediately
//  when any competitor return a non-null value, other threads will be canceled.
// return:
//   Pair(
//     the winner, one of the competitors
//     the first non-null value
//   )
@Suppress("UNCHECKED_CAST")
private suspend fun <C, R> racing(
    competitors: List<C>,
    // runner takes a competitor as param and generate a executable function to run
    runner: (C) -> ((CoroutineScope)->R?),
    timeoutMillis: Long,
): Pair<C?, R?> = coroutineScope {
    if (competitors.isEmpty()) {
        return@coroutineScope Pair(null, null)
    }

    val scope = CoroutineScope(IO)

    val resultChannel = Channel<Pair<C?,R?>>()

    val finishedCount = AtomicInteger(0)

    val jobs = competitors.map { competitor ->
        scope.launch {
            val result = runner(competitor)(this)

            // set the channel if it's not null
            if (result != null) {
                resultChannel.send(
                    Pair(competitor, result as R?)
                )
            }

            yield()

            // All threads are done, but no one has crossed the finish line, return anyway.
            if(finishedCount.addAndGet(1) >= competitors.size) {
                resultChannel.send(
                    Pair(null, null)
                )
            }
        }
    }

    val firstNonNullResult = scope.async {
        withTimeoutOrNull(timeoutMillis) {
            resultChannel.receiveCatching()
        }
    }.await()

    // once there is a result, stop all other jobs
    scope.cancel()
    jobs.forEach { job ->
        job.cancel()
    }

    return@coroutineScope Pair(
        firstNonNullResult?.getOrNull()?.first, // the competitor
        firstNonNullResult?.getOrNull()?.second, // the result
    )
}

fun <C, R> race(
    competitors: List<C>,
    // runner takes a competitor as param and generate a executable function to run
    runner: (C) -> ((CoroutineScope)->R),
    timeoutMillis: Long,
):  Pair<C?, R?>  {
    return runBlocking {
        racing(competitors, runner, timeoutMillis)
    }
}
