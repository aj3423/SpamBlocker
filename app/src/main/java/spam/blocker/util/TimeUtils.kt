package spam.blocker.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable
import spam.blocker.R
import spam.blocker.ui.theme.SkyBlue
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.Year
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun isSameYearAsNow(timestamp: Long): Boolean {
        return Year.from(
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        ) == Year.now()
    }
    fun fullDateString(timestamp: Long): String {
        val format = if (isSameYearAsNow(timestamp)) {
            "MM-dd\nHH:mm" // don't show the YEAR
        } else {
            "yyyy-MM-dd\nHH:mm"
        }
        val dateFormat = SimpleDateFormat(format, Locale.getDefault())
        val date = Date(timestamp)
        return dateFormat.format(date)
    }

    fun hourMin(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val date = Date(timestamp)
        return dateFormat.format(date)
    }

    fun isToday(timestampMillis: Long): Boolean {
        val now = LocalDateTime.now()

        // Convert the timestamp in milliseconds to a LocalDateTime object
        val then = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestampMillis), ZoneId.systemDefault()
        )

        return now.year == then.year && now.month == then.month && now.dayOfMonth == then.dayOfMonth
    }
    fun isWithinLast(timestampMillis: Long, maxAge: Duration): Boolean {
        val now = Instant.now()
        val eventTime = Instant.ofEpochMilli(timestampMillis)

        // negative duration = in the future
        // positive duration = how long ago it happened
        val age = Duration.between(eventTime, now)

        return !age.isNegative && age <= maxAge
    }

    // Convenience functions
    fun isWithinMinute(timestampMillis: Long, minutes: Long): Boolean =
        isWithinLast(timestampMillis, Duration.ofMinutes(minutes))

    fun isYesterday(timestampMillis: Long): Boolean {
        val now = LocalDateTime.now()

        // Convert the timestamp in milliseconds to a LocalDateTime object
        val then = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestampMillis),
            ZoneId.systemDefault()
        )

        // Check if the difference between now and then is less than 24 hours
        return now.minusDays(1) <= then && then < now
    }

    // For history record time
    fun dayOfWeekString(ctx: Context, timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysArray = ctx.resources.getStringArray(R.array.weekdays_abbrev).asList()
        return daysArray[dayOfWeek - 1]
    }

    // For history record time.
    fun isWithinAWeek(timestamp: Long): Boolean {
        val currentTimeMillis = System.currentTimeMillis()
        val difference = currentTimeMillis - timestamp
        val millisecondsInWeek = 7 * 24 * 60 * 60 * 1000 // 7 days in milliseconds
        return difference <= millisecondsInWeek
    }


    fun formatTime(
        ctx: Context,
        timestamp: Long,
    ): String {
        return if (isToday(timestamp)) {
            hourMin(timestamp)
        } else if (isYesterday(timestamp)) {
            ctx.getString(R.string.yesterday_abbrev) + "\n" + hourMin(timestamp)
        } else if (isWithinAWeek(timestamp)) {
            dayOfWeekString(ctx, timestamp) + "\n" + hourMin(timestamp)
        } else {
            fullDateString(timestamp)
        }
    }

    @Serializable
    data class FreshnessColor (
        val durationMin: String = "10min", // "5min", "today", ...
        val argb: Int = SkyBlue.toArgb()
    ) : Comparable<FreshnessColor> {
        fun isValid(): Boolean {
            return durationMin == "today" || durationMin.matches(Regex("\\d+min"))
        }
        override fun compareTo(other: FreshnessColor): Int {
            return durationValue().compareTo(other.durationValue())
        }

        private fun durationValue(): Int {
            if (durationMin == "today") return Int.MAX_VALUE
            return durationMin.removeSuffix("min").toInt()
        }
    }

    fun timeColor(
        timestamp: Long,
        // Should be a list of:
        //  [
        //    {"1min", red},
        //    {"today", blue}
        //  ]
        sortedColors: List<FreshnessColor>,
    ) : Color? {
        return sortedColors.firstOrNull { fc ->
            when {
                fc.durationMin == "today" -> isToday(timestamp)
                else -> fc.durationMin.removeSuffix("min").toLongOrNull()
                    ?.let { isWithinMinute(timestamp, it) } == true
            }
        }?.argb?.let { Color(it) }
    }

    val MIN: Long = 60
    val HOUR: Long = 60 * MIN
    val DAY: Long = 24 * HOUR

    fun durationString(ctx: Context, dur: Duration): String {
        val parts = mutableListOf<String>()

        val days = dur.seconds / DAY
        val hours = dur.seconds % DAY / HOUR
        val minutes = dur.seconds % HOUR / MIN
        val seconds = dur.seconds % MIN

        if (days > 0) {
            val nDays = ctx.resources.getQuantityString(R.plurals.days, days.toInt(), days)
            parts += "$nDays "
        }
        parts += "%02d:%02d:%02d".format(hours, minutes, seconds)

        return parts.joinToString(" ")
    }

    // for display on Util
    @SuppressLint("DefaultLocale")
    fun timeRangeStr(
        ctx: Context,
        stHour: Int, stMin: Int, etHour: Int, etMin: Int
    ): String {
        if (stHour == 0 && stMin == 0 && etHour == 0 && etMin == 0)
            return ctx.getString(R.string.entire_day)
        return String.format("%02d:%02d - %02d:%02d", stHour, stMin, etHour, etMin)
    }

    fun currentHourMin(): Pair<Int, Int> {
        val calendar = Calendar.getInstance()
        val currHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currMinute = calendar.get(Calendar.MINUTE)
        return Pair(currHour, currMinute)
    }

//    fun currentHourMinSec(): Triple<Int, Int, Int> {
//        val calendar = Calendar.getInstance()
//        val currHour = calendar.get(Calendar.HOUR_OF_DAY)
//        val currMinute = calendar.get(Calendar.MINUTE)
//        val currSecond = calendar.get(Calendar.SECOND)
//        return Triple(currHour, currMinute, currSecond)
//    }

    fun isCurrentTimeWithinRange(stHour: Int, stMin: Int, etHour: Int, etMin: Int): Boolean {
        val (currHour, currMinute) = currentHourMin()
        val curr = currHour * 60 + currMinute

        val rangeStart = stHour * 60 + stMin
        val rangeEnd = etHour * 60 + etMin

        return if (rangeStart <= rangeEnd) {
            curr in rangeStart..rangeEnd
        } else {
            curr >= rangeStart || curr <= rangeEnd
        }
    }
}