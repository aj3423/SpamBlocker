package spam.blocker.util

// just a wrapper for testing, mockk doesn't support hooking `System.currentTimeMillis` directly
open class Time {
    companion object {
        fun getCurrentTimeMilliss(): Long {
            return System.currentTimeMillis()
        }
    }
}
