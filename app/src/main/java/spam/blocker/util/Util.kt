package spam.blocker.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.text.format.DateFormat
import spam.blocker.R
import spam.blocker.db.Flag
import spam.blocker.def.Def
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Util {
    companion object {
        fun fullDateString(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd\nHH:mm", Locale.getDefault())
            val date = Date(timestamp)
            return dateFormat.format(date)
        }

        fun hourMin(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = Date(timestamp)
            return dateFormat.format(date)
        }

        fun isToday(timestamp: Long): Boolean {
            val calendar = Calendar.getInstance()
            val currentDate = calendar.get(Calendar.DAY_OF_MONTH)
            calendar.timeInMillis = timestamp
            val date = calendar.get(Calendar.DAY_OF_MONTH)
            return currentDate == date
        }

        fun getDayOfWeek(ctx: Context, timestamp: Long): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val daysArray = ctx.resources.getStringArray(R.array.week).asList()
            return daysArray[dayOfWeek - 1]
        }

        fun isWithinAWeek(timestamp: Long): Boolean {
            val currentTimeMillis = System.currentTimeMillis()
            val difference = currentTimeMillis - timestamp
            val millisecondsInWeek = 7 * 24 * 60 * 60 * 1000 // 7 days in milliseconds
            return difference <= millisecondsInWeek
        }

        // check if a string only contains:
        //   digits spaces + - ( )
        val pattern = "^[0-9\\s+\\-()]*\$".toRegex()
        fun clearNumber(number: String): String {
            // check if it contains alphabetical characters like "Microsoft"
            if (!pattern.matches(number)) { // don't clear for enterprise string number
                return number
            }

            return number
                .trimStart('0') // remove leading "0"s
                .replace("-", "")
                .replace("+", "")
                .replace(" ", "")
                .replace("(", "")
                .replace(")", "")
        }

        fun formatTimeRange(
            ctx: Context,
            stHour: Int, stMin: Int, etHour: Int, etMin: Int
        ): String {
            val fmt24h = DateFormat.is24HourFormat(ctx)

            if (fmt24h) {
                val startTime = String.format("%02d:%02d", stHour, stMin)
                val endTime = String.format("%02d:%02d", etHour, etMin)
                return "$startTime - $endTime"
            } else {
                val startTime = String.format(
                    "%02d:%02d %s",
                    if (stHour == 0 || stHour == 12) 12 else stHour % 12,
                    stMin,
                    if (stHour < 12) "AM" else "PM"
                )
                val endTime = String.format(
                    "%02d:%02d %s",
                    if (etHour == 0 || etHour == 12) 12 else etHour % 12,
                    etMin,
                    if (etHour < 12) "AM" else "PM"
                )
                return "$startTime - $endTime"
            }
        }

        fun currentHourMin(): Pair<Int, Int> {
            val calendar = Calendar.getInstance()
            val currHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currMinute = calendar.get(Calendar.MINUTE)
            return Pair(currHour, currMinute)
        }
        fun isCurrentTimeWithinRange(stHour: Int, stMin: Int, etHour: Int, etMin: Int): Boolean {
            val (currHour, currMinute) = currentHourMin()
            val curr = currHour * 60 + currMinute

            val rangeStart = stHour * 60 + stMin
            val rangeEnd = etHour * 60 + etMin

            return if (rangeStart <= rangeEnd) {
                curr in rangeStart.. rangeEnd
            } else {
                curr >= rangeStart || curr <= rangeEnd
            }
        }

        private fun isRegexValid(regex: String): Boolean {
            return try {
                Regex(regex)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun validateRegex(ctx: Context, regexStr: String) : String? {
            var s = regexStr
            if (s.isNotEmpty() && s.trim() != s)
                return ctx.getString(R.string.pattern_contain_invisible_characters)

            if (!isRegexValid(s))
                return ctx.getString(R.string.invalid_regex_pattern)

            if (s.startsWith("^"))
                s = s.substring(1)

            if (s.startsWith("+") || s.startsWith("\\+"))
                return ctx.getString(R.string.pattern_contain_leading_plus) + " " + ctx.getString(R.string.check_balloon_for_explanation)

            if (s.startsWith("0")) {
                return ctx.getString(R.string.pattern_contain_leading_zeroes) + " " + ctx.getString(R.string.check_balloon_for_explanation)
            }

            return null
        }

        fun flagsToRegexOptions(flags: Flag) : Set<RegexOption> {
            val opts = mutableSetOf<RegexOption>()
            if (flags.Has(Def.FLAG_REGEX_IGNORE_CASE)) {
                opts.add(RegexOption.IGNORE_CASE)
            }
            if (flags.Has(Def.FLAG_REGEX_MULTILINE)) {
                opts.add(RegexOption.MULTILINE)
            }
            if (flags.Has(Def.FLAG_REGEX_DOT_MATCH_ALL)) {
                opts.add(RegexOption.DOT_MATCHES_ALL)
            }
            if (flags.Has(Def.FLAG_REGEX_LITERAL)) {
                opts.add(RegexOption.LITERAL)
            }
            return opts

        }


        fun isInt(str: String): Boolean {
            return str.toIntOrNull() != null
        }

        private var cacheAppList : List<AppInfo>? = null
        private val lock_1 = Any()
        @SuppressLint("UseCompatLoadingForDrawables")
        fun listApps(ctx: Context): List<AppInfo> {
            synchronized(lock_1) {
                if (cacheAppList == null) {
                    val packageManager = ctx.packageManager

                    cacheAppList = packageManager.getInstalledApplications(
                        PackageManager.MATCH_DISABLED_COMPONENTS or
                                PackageManager.MATCH_UNINSTALLED_PACKAGES or
                                PackageManager.GET_META_DATA
                    ).filter { appInfo ->
                        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    }.map {
                        val ret = AppInfo()
                        ret.pkgName = it.packageName
                        ret.label = packageManager.getApplicationLabel(it).toString()
                        try {
                            ret.icon = packageManager.getApplicationIcon(it)
                        } catch (e: PackageManager.NameNotFoundException) {
                            ret.icon = ctx.getDrawable(R.drawable.unknown_app_icon)!!
                        }
                        ret
                    }
                }
            }

            return cacheAppList!!
        }

        private var cacheAppMap : Map<String, AppInfo>? = null
        private val lock_2 = Any()
        fun getAppsMap(ctx: Context): Map<String, AppInfo> {
            synchronized(lock_2) {
                if (cacheAppMap == null) {
                    cacheAppMap = listApps(ctx).associateBy { it.pkgName }
                }
            }

            return cacheAppMap!!
        }



//        fun splitCcPhone(str: String): Pair<String, String>? {
//            val matcher = Pattern.compile("^([17]|2[07]|3[0123469]|4[013456789]|5[12345678]|6[0123456]|8[1246]|9[0123458]|\\d{3})\\d*?(\\d{4,6})$").matcher(str);
//            if (!matcher.find()) {
//                return null
//            }
//            val cc = matcher.group(1) ?: return null
//
//            val phone = str.substring(cc.length)
//
////            Log.d(Def.TAG, "cc: $cc, g2: $phone")
//            return Pair(cc, phone)
//        }

        fun isPackageInstalled(ctx: Context, pkgName: String): Boolean {
            val pm = ctx.packageManager
            val flags = 0
            return try {
                pm.getPackageUid(pkgName, flags)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun setLocale(ctx: Context, languageCode: String) {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)

            val resources = ctx.resources
            val configuration = resources.configuration
            configuration.setLocale(locale)

            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
    }
}