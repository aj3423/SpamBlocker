package spam.blocker.util

import io.mockk.every
import io.mockk.mockkObject
import org.junit.Assert.assertEquals
import org.junit.Test
import spam.blocker.service.bot.Daily
import spam.blocker.service.bot.Time
import spam.blocker.service.bot.Weekly
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Duration
import java.time.LocalDateTime

class ScheduleTest {

    @Test
    fun timeIsValid() {
        assertEquals(Time(0, 0).isValid(), true)
        assertEquals(Time(23, 59).isValid(), true)
        assertEquals(Time(-1, 0).isValid(), false)
        assertEquals(Time(24, 0).isValid(), false)
        assertEquals(Time(0, 60).isValid(), false)
        assertEquals(Time(0, 60).isValid(), false)
    }

    private fun setNow(now: LocalDateTime) {
        every { LocalDateTimeMockk.now() } returns now
    }

    @Test
    fun daily() {
        mockkObject(LocalDateTimeMockk)

        var dur: Duration

        // now: <2000-1-1 0:0>, time: <1:0>  ->  dur: 1 hour
        setNow(LocalDateTime.of(2000, 1, 1, 0, 0, 0))
        dur = Daily(Time(1, 0)).nextOccurrence()!!
        assertEquals("same day", 1 * 3600 * 1000, dur.toMillis())

        // now: <2000-1-1 0:0>, time: <0:0>  -> dur: 24 hours
        setNow(LocalDateTime.of(2000, 1, 1, 0, 0, 0))
        dur = Daily(Time(0, 0)).nextOccurrence()!!
        assertEquals("next day", 24 * 3600 * 1000, dur.toMillis())

        // now: <2023-12-31 10:0>, time: <0:0>  -> dur: 14 hours
        setNow(LocalDateTime.of(2023, 12, 31, 10, 0, 0))
        dur = Daily(Time(0, 0)).nextOccurrence()!!
        assertEquals("cross year", 14 * 3600 * 1000, dur.toMillis())
    }

    @Test
    fun weekly() {
        mockkObject(LocalDateTimeMockk)

        var dur: Duration
        val hour = (1 * 3600 * 1000).toLong()
        val day = (24 * hour).toLong()

        // now: <2024-10-1 Tuesday 0:0:0>, weekdays: [Tuesday], time: <1:0:0>  -> dur: 1 hour
        setNow(LocalDateTime.parse("2024-10-01T00:00:00"))
        dur = Weekly(listOf(TUESDAY), Time(1, 0)).nextOccurrence()!!
        assertEquals("same day", 1 * hour, dur.toMillis())

        // now: <2024-10-1 Tuesday 0:0:0>, weekdays: [Tuesday, Wednesday], time: <0:0:0>  -> dur: 1 day
        setNow(LocalDateTime.parse("2024-10-01T00:00:00"))
        dur = Weekly(listOf(TUESDAY, WEDNESDAY), Time(0, 0)).nextOccurrence()!!
        assertEquals("next day", 1 * day, dur.toMillis())

        // now: <2024-10-1 Tuesday 0:0:0>, weekdays: [Tuesday], time: <0:0:0>  -> dur: 7 days
        setNow(LocalDateTime.parse("2024-10-01T00:00:00"))
        dur = Weekly(listOf(TUESDAY), Time(0, 0)).nextOccurrence()!!
        assertEquals("next week", 7 * day, dur.toMillis())

        // now: <2024-10-31 Thursday 0:0:0>, weekdays: [Thursday], time: <0:0:0>  -> dur: 7 days
        setNow(LocalDateTime.parse("2024-10-31T00:00:00"))
        dur = Weekly(listOf(THURSDAY), Time(0, 0)).nextOccurrence()!!
        assertEquals("cross month", 7 * day, dur.toMillis())

        // now: <2024-12-31 Tuesday 0:0:0>, weekdays: [Tuesday], time: <0:0:0>  -> dur: 7 days
        setNow(LocalDateTime.parse("2024-12-31T00:00:00"))
        dur = Weekly(listOf(TUESDAY), Time(0, 0)).nextOccurrence()!!
        assertEquals("cross year", 7 * day, dur.toMillis())
    }
}