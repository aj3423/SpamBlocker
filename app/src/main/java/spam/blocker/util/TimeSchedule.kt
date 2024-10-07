package spam.blocker.util

import android.annotation.SuppressLint
import android.content.Context
import spam.blocker.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Serialized format: "weekdays,time range,enabled"
// e.g. "12345,00:00-01:02,true"
class TimeSchedule {
    var enabled = false
    var startHour = 0
    var startMin = 0
    var endHour = 0
    var endMin = 0
    var weekdays = mutableListOf<Int>()

    fun serializeToStr(): String {
        val part0 = weekdays.fold("") { acc, item ->
            acc + item.toString()
        }
        val part1 = timeRangeStr()
        val part2 = enabled.toString()

        return "$part0,$part1,$part2"
    }

    // for serialization only
    @SuppressLint("DefaultLocale")
    private fun timeRangeStr(): String {
        return String.format("%02d:%02d-%02d:%02d", startHour, startMin, endHour, endMin)
    }

    fun toDisplayStr(ctx: Context): String {
        val labels = ctx.resources.getStringArray(R.array.short_weekdays)
        val days = weekdays.sorted()

        val rangeStr = Util.timeRangeStr(ctx, startHour, startMin, endHour, endMin)
        if (isEveryday()) // every day, ignore the day string
            return rangeStr
        if (days == listOf(2, 3, 4, 5, 6))
            return ctx.getString(R.string.workday) + "  " + rangeStr
        if (days == listOf(1, 7))
            return ctx.getString(R.string.weekend) + "  " + rangeStr

        // [1,3,5] -> "Sun,Tue,Thur"
        val daysStr = days.joinToString(",") { labels[it - 1] }

        return "$daysStr  $rangeStr"
    }

    // all weekdays
    private fun isEveryday(): Boolean {
        return weekdays.isEmpty() || weekdays.size == 7
    }

    // all hours
    private fun isEntireDay(): Boolean {
        return startHour == 0 && startMin == 0 && endHour == 0 && endMin == 0
    }

    private fun containsDay(day: Int): Boolean {
        return (isEveryday() || (day in weekdays))
    }

    // The time can span multiple days like: 21:00-05:00
    fun satisfyTime(timeMillis: Long): Boolean {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneId.systemDefault())
        var d = t.dayOfWeek.ordinal + 2
        if (d == 8) d = 1 // d: 1~7

        var pd = d - 1 // previous day
        if (pd == 0) pd = 7

        val start = startHour * 60 + startMin
        val end = endHour * 60 + endMin
        val tm = t.hour * 60 + t.minute

        return if (start < end) { // 05:00-10:00
            containsDay(d) && tm in start..end
        } else { // start >= end, like: 21:00-07:00, or 12:00-12:00, or 00-00:00:00(Entire Day)
            ((tm >= start) && containsDay(d))
                    ||
                    ((tm <= end) && containsDay(pd))
        }
    }

    companion object {
        private val regexRange = """(\d{1,2}):(\d{1,2})-(\d{1,2}):(\d{1,2})""".toRegex()

        fun parseFromStr(str: String): TimeSchedule {
            val ret = TimeSchedule()

            if (str.isEmpty())
                return TimeSchedule()

            val parts = str.split(",")
            if (parts.size != 3)
                return TimeSchedule()

            // 1. weekdays, e.g.: "23456"
            // Calendar.SUNDAY ~ Calendar.SATURDAY (1~7)
            parts[0].forEach {
                ret.weekdays.add(it.digitToInt())
            }

            // 2. time range, e.g.: "01:00-02:30"
            val match = regexRange.matchEntire(parts[1]) ?: return TimeSchedule()

            val (startHour, startMinute, endHour, endMinute) = match.destructured
            ret.startHour = startHour.toInt()
            ret.startMin = startMinute.toInt()
            ret.endHour = endHour.toInt()
            ret.endMin = endMinute.toInt()

            // 3. enabled
            ret.enabled = parts[2].toBoolean()

            return ret
        }

        // If it's enabled and not match the current time
        fun dissatisfyNow(scheduleString: String): Boolean {
            val sch = parseFromStr(scheduleString)
            if (sch.enabled) {
                val now = Now.currentMillis()
                if (!sch.satisfyTime(now)) {
                    return true
                }
            }
            return false
        }
    }
}
