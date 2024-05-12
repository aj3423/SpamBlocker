package spam.blocker.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.text.format.DateFormat
import spam.blocker.R
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.Flag
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.RuleTable
import spam.blocker.def.Def
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

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

        fun getDayOfWeek(timestamp: Long): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val daysArray = arrayOf(
                "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
            )
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
        private fun hasTrailingSpacesOrNewlines(regexStr: String): Boolean {
            return regexStr.isNotEmpty() && regexStr.trim() != regexStr
        }
        fun validateRegex(ctx: Context, regexStr: String) : Pair<Boolean, String> {
            if (hasTrailingSpacesOrNewlines(regexStr))
                return Pair(false, ctx.getString(R.string.pattern_contain_trailing_space))
            return if (isRegexValid(regexStr))
                Pair(true, "")
            else
                Pair(false, ctx.getString(R.string.invalid_regex_pattern))
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
        @SuppressLint("UseCompatLoadingForDrawables")
        fun listApps(ctx: Context): List<AppInfo> {
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

            return cacheAppList!!
        }

        private var cacheAppMap : Map<String, AppInfo>? = null
        fun getAppsMap(ctx: Context): Map<String, AppInfo> {
            if (cacheAppMap == null) {
                cacheAppMap = listApps(ctx).associateBy { it.pkgName }
            }
            return cacheAppMap!!
        }


        fun reasonStr(ctx: Context, filterTable: RuleTable?, reason: String) : String {
            val f = filterTable?.findPatternRuleById(ctx, reason.toLong())

            val reasonStr = if (f != null) {
                if (f.description != "") f.description else f.patternStr()
            } else {
                ctx.resources.getString(R.string.deleted_filter)
            }
            return reasonStr
        }
        fun reasonTable(result: Int):  RuleTable? {
            return when (result) {
                Def.RESULT_ALLOWED_BY_NUMBER, Def.RESULT_BLOCKED_BY_NUMBER ->  NumberRuleTable()
                Def.RESULT_ALLOWED_BY_CONTENT, Def.RESULT_BLOCKED_BY_CONTENT ->  ContentRuleTable()

                else -> null
            }
        }
        fun resultStr(ctx: Context, result: Int, reason: String): String {
            return when (result) {
                Def.RESULT_ALLOWED_BY_CONTACT ->  ctx.resources.getString(R.string.contact)
                Def.RESULT_BLOCKED_BY_NON_CONTACT ->  ctx.resources.getString(R.string.non_contact)

                Def.RESULT_ALLOWED_BY_RECENT_APP ->  ctx.resources.getString(R.string.recent_app) + ": "
                Def.RESULT_ALLOWED_BY_REPEATED ->  ctx.resources.getString(R.string.repeated_call)
                Def.RESULT_ALLOWED_BY_DIALED ->  ctx.resources.getString(R.string.dialed)
                Def.RESULT_ALLOWED_BY_OFF_TIME ->  ctx.resources.getString(R.string.off_time)
                Def.RESULT_ALLOWED_BY_NUMBER ->  ctx.resources.getString(R.string.whitelist) + ": " + reasonStr(
                    ctx, NumberRuleTable(), reason)
                Def.RESULT_BLOCKED_BY_NUMBER ->  ctx.resources.getString(R.string.blacklist) + ": " + reasonStr(
                    ctx, NumberRuleTable(), reason)
                Def.RESULT_ALLOWED_BY_CONTENT ->  ctx.resources.getString(R.string.content) + ": " + reasonStr(
                    ctx, ContentRuleTable(), reason)
                Def.RESULT_BLOCKED_BY_CONTENT ->  ctx.resources.getString(R.string.content) + ": " + reasonStr(
                    ctx, ContentRuleTable(), reason)

                else -> ctx.resources.getString(R.string.passed_by_default)
            }
        }

        fun splitCcPhone(str: String): Pair<String, String>? {
            val matcher = Pattern.compile("^([17]|2[07]|3[0123469]|4[013456789]|5[12345678]|6[0123456]|8[1246]|9[0123458]|\\d{3})\\d*?(\\d{4,6})$").matcher(str);
            if (!matcher.find()) {
                return null
            }
            val cc = matcher.group(1) ?: return null

            val phone = str.substring(cc.length)

//            Log.d(Def.TAG, "cc: $cc, g2: $phone")
            return Pair(cc, phone)
        }

    }
}