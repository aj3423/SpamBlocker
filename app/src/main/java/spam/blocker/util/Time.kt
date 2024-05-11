package spam.blocker.util

// just a wrapper for testing, mockk doesn't support hooking `System.currentTimeMillis` directly
open class Time {
    companion object {

        // mocck doesn't work for hooking System.currentTimeMillis, so use a wrapper
        fun currentTimeMillis(): Long {
            return System.currentTimeMillis()
        }
    }
}
