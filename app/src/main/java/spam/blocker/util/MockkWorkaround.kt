package spam.blocker.util

import java.time.LocalDateTime

// This file contains wrappers for testing, mockk doesn't support hooking
// `System.currentTimeMillis()` or `LocalDateTime.now()`

open class Now {
    companion object {

        fun currentMillis(): Long {
            return System.currentTimeMillis()
        }
    }
}

object LocalDateTimeMockk {
    fun now() : LocalDateTime {
        return LocalDateTime.now()
    }
}

