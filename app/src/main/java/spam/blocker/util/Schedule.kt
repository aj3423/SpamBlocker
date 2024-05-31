package spam.blocker.util

import android.content.Context
import spam.blocker.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Serialized format: "weekdays,time range,enabled"
// e.g. "12345,00:00-01:02,true"
class Schedule {
    var enabled = false
    var startHour = 0
    var startMin = 0
    var endHour = 0
    var endMin = 0
    var weekdays = arrayListOf<Int>()

    fun serializeToStr(): String {
        val part0 = weekdays.fold("") { acc, item ->
            acc + item.toString()
        }
        val part1 = timeRangeStr()
        val part2 = enabled.toString()

        return "$part0,$part1,$part2"
    }
    private fun timeRangeStr(): String {
        return String.format("%02d:%02d-%02d:%02d", startHour, startMin, endHour, endMin)
    }
    fun timeRangeDisplayStr(ctx: Context): String {
        if (startHour == 0 && startMin == 0 && endHour == 0 && endMin == 0)
            return ctx.getString(R.string.entire_day)
        return String.format("%02d:%02d - %02d:%02d", startHour, startMin, endHour, endMin)
    }
    fun toDisplayStr(ctx: Context): String {
        val labels = ctx.resources.getStringArray(R.array.week)
        val days = weekdays.sorted()

        if (days == listOf(1,2,3,4,5,6,7)) // every day, ignore the day string
            return timeRangeDisplayStr(ctx)
        if (days == listOf(2,3,4,5,6))
            return ctx.getString(R.string.workday) + " " + timeRangeDisplayStr(ctx)
        if (days == listOf(1, 7))
            return ctx.getString(R.string.weekend) + " " + timeRangeDisplayStr(ctx)

        // [1,3,5] -> "Sun,Tue,Thur"
        val daysStr = days.joinToString(",") { labels[it-1] }

        return daysStr + " " + timeRangeDisplayStr(ctx)
    }
    fun satisfyTime(timeMillis: Long): Boolean {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneId.systemDefault())
        var d = t.dayOfWeek.ordinal + 2
        if (d == 8) d = 1
        if (d !in weekdays)
            return false

        // all 0s means entire day, always satisfies
        if (startHour == 0 && startMin == 0 && endHour == 0 && endMin == 0)
            return true

        val min = startHour * 60 + startMin
        val max = endHour * 60 + endMin
        val v = t.hour * 60 + t.minute
        return v in min..max
    }
    
    companion object {
        private val regexRange = """(\d{1,2}):(\d{1,2})-(\d{1,2}):(\d{1,2})""".toRegex()

        fun parseFromStr(str: String) : Schedule {
            val ret = Schedule()

            if (str.isEmpty())
                return Schedule()

            val parts = str.split(",")
            if (parts.size != 3)
                return Schedule()

            // 1. weekdays, e.g.: "23456"
            // Calendar.SUNDAY ~ Calendar.SATURDAY (1~7)
            parts[0].forEach {
                ret.weekdays.add(it.digitToInt())
            }

            // 2. time range, e.g.: "01:00-02:30"
            val match = regexRange.matchEntire(parts[1]) ?: return Schedule()

            val (startHour, startMinute, endHour, endMinute) = match.destructured
            ret.startHour = startHour.toInt()
            ret.startMin = startMinute.toInt()
            ret.endHour = endHour.toInt()
            ret.endMin = endMinute.toInt()

            // 3. enabled
            ret.enabled = parts[2].toBoolean()

            return ret
        }

    }
}
