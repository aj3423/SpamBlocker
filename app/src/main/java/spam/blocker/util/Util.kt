package spam.blocker.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.provider.Telephony
import androidx.annotation.RequiresApi
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import spam.blocker.R
import spam.blocker.def.Def
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

typealias Lambda = () -> Unit
typealias Lambda1<A> = (A) -> Unit
typealias Lambda2<A, B> = (A, B) -> Unit
typealias Lambda3<A, B, C> = (A, B, C) -> Unit
typealias Lambda4<A, B, C, D> = (A, B, C, D) -> Unit


// parse json -> map
private fun toValue(element: Any) = when (element) {
    JSONObject.NULL -> null
    is JSONObject -> element.toMap()
    is JSONArray -> element.toList()
    else -> element
}

fun JSONArray.toList(): List<Any?> =
    (0 until length()).map { index -> toValue(get(index)) }

fun JSONObject.toMap(): Map<String, Any?> =
    keys().asSequence().associateWith { key -> toValue(get(key)) }

fun JSONObject.toStringMap(): Map<String, String> =
    keys().asSequence().associateWith { key -> toValue(get(key)) as String }

// A json serializer that allows unknown attributes
val PermissiveJson =  Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
val PermissivePrettyJson =  Json {
    prettyPrint = true

    encodeDefaults = true
    ignoreUnknownKeys = true
}


object Util {

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

    fun isToday(timestampMillis: Long): Boolean {
        val now = LocalDateTime.now()

        // Convert the timestamp in milliseconds to a LocalDateTime object
        val then = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestampMillis), ZoneId.systemDefault()
        )

        return now.year == then.year && now.month == then.month && now.dayOfMonth == then.dayOfMonth
    }

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
        val daysArray = ctx.resources.getStringArray(R.array.short_weekdays).asList()
        return daysArray[dayOfWeek - 1]
    }

    // For history record time.
    fun isWithinAWeek(timestamp: Long): Boolean {
        val currentTimeMillis = System.currentTimeMillis()
        val difference = currentTimeMillis - timestamp
        val millisecondsInWeek = 7 * 24 * 60 * 60 * 1000 // 7 days in milliseconds
        return difference <= millisecondsInWeek
    }

    fun formatTime(ctx: Context, timestamp: Long): String {
        return if (isToday(timestamp)) {
            hourMin(timestamp)
        } else if (isYesterday(timestamp)) {
            ctx.getString(R.string.yesterday) + "\n" + hourMin(timestamp)
        } else if (isWithinAWeek(timestamp)) {
            dayOfWeekString(ctx, timestamp) + "\n" + hourMin(timestamp)
        } else {
            fullDateString(timestamp)
        }
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

    // Check if it only contains:
    //   0-9 space - ( )
    val nonAlphaPattern = "^[0-9\\s+\\-()]*\$".toRegex()
    fun isAlphaNumber(number: String): Boolean {
        return !nonAlphaPattern.matches(number)
    }
    // check if a string only contains:
    //   digit spaces + - ( )
    // Param:
    //   when `force`==true, numbers like "+123####" will also be cleared.
    fun clearNumber(number: String, force: Boolean = false): String {
        // check if it contains alphabetical characters like "Microsoft"
        if (!force && isAlphaNumber(number)) { // don't clear for enterprise string number
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

    fun extractString(regex: Regex, haystack: String): String? {
        /*
            lookbehind: there is `value`, no `group`
            capturing group: there are both `value` and `group`, should use `group` only

            so the logic is:
                if there is only `value`, no `group`
                    use `value`
                else if both exist
                    use `group`
         */
        val result = regex.find(haystack)

        val v = result?.value
        // https://github.com/aj3423/SpamBlocker/discussions/192#discussioncomment-11328612
        val group = result?.groups
            ?.drop(1) // skip group[0], it's always the entire string
            ?.filterNotNull()
            ?.first()?.value

        if (v != null && group == null) {
            return v
        } else if (v != null && group != null) {
            return group
        }

        return null
    }

    fun truncate(str: String, limit: Int = 300, showEllipsis: Boolean = true): String {
        return if (str.length >= limit)
            str.substring(0, limit) + if (showEllipsis) "…" else ""
        else
            str
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

    fun currentHourMinSec(): Triple<Int, Int, Int> {
        val calendar = Calendar.getInstance()
        val currHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currMinute = calendar.get(Calendar.MINUTE)
        val currSecond = calendar.get(Calendar.SECOND)
        return Triple(currHour, currMinute, currSecond)
    }

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

    private fun isRegexValid(regex: String): Boolean {
        return try {
            Regex(regex)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun validateRegex(
        ctx: Context,
        regexStr: String,
        disableNumberOptimization: Boolean
    ): String? {
        var s = regexStr
        if (s.isNotEmpty() && s.trim() != s)
            return ctx.getString(R.string.pattern_contain_invisible_characters)

        if (!isRegexValid(s))
            return ctx.getString(R.string.invalid_regex_pattern)

        if (s.startsWith("^")) // may be it's `^0` or `^+1`
            s = s.substring(1)

        if ((s.startsWith("+") || s.startsWith("\\+")) && !disableNumberOptimization)
            return listOf(
                ctx.getString(R.string.pattern_contain_leading_plus),
                ctx.getString(R.string.disable_number_optimization),
                ctx.getString(R.string.check_balloon_for_explanation),
            ).joinToString(separator = "\n")

        if (s.startsWith("0") && !disableNumberOptimization) {
            return listOf(
                ctx.getString(R.string.pattern_contain_leading_zeroes),
                ctx.getString(R.string.disable_number_optimization),
                ctx.getString(R.string.check_balloon_for_explanation),
            ).joinToString(separator = "\n")
        }

        return null
    }

    fun flagsToRegexOptions(flags: Int): Set<RegexOption> {
        val opts = mutableSetOf<RegexOption>()
        if (flags.hasFlag(Def.FLAG_REGEX_IGNORE_CASE)) {
            opts.add(RegexOption.IGNORE_CASE)
        }
        if (flags.hasFlag(Def.FLAG_REGEX_MULTILINE)) {
            opts.add(RegexOption.MULTILINE)
        }
        if (flags.hasFlag(Def.FLAG_REGEX_DOT_MATCH_ALL)) {
            opts.add(RegexOption.DOT_MATCHES_ALL)
        }
        if (flags.hasFlag(Def.FLAG_REGEX_LITERAL)) {
            opts.add(RegexOption.LITERAL)
        }
        return opts
    }


    private var cacheAppList: List<AppInfo>? = null
    private val lock_1 = Any()

    @SuppressLint("UseCompatLoadingForDrawables")
    fun listApps(ctx: Context): List<AppInfo> {
        synchronized(lock_1) {
            if (cacheAppList == null) {
                val pm = ctx.packageManager

                val packageInfos = Permissions.getPackagesHoldingPermissions(
                    pm,
                    arrayOf(Manifest.permission.INTERNET)
                )

                cacheAppList = packageInfos.filter {
                    if (it.applicationInfo == null)
                        false
                    else
                        (it.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }.map {
                    val appInfo = it.applicationInfo

                    AppInfo().apply {
                        pkgName = it.packageName
                        label = appInfo!!.loadLabel(pm).toString()
                        icon = appInfo.loadIcon(pm)
                    }
                }
            }
        }

        return cacheAppList!!
    }

    // android<13 will always return `false`
    @RequiresApi(Def.ANDROID_13)
    fun isDefaultSmsAppNotificationEnabled(ctx: Context): Boolean {
        // It can throw exception, ref: https://github.com/aj3423/SpamBlocker/issues/122
        try {
            val defSmsPkg = Telephony.Sms.getDefaultSmsPackage(ctx)

            val pm = ctx.packageManager

            val result = pm.checkPermission(Manifest.permission.POST_NOTIFICATIONS, defSmsPkg)

            return result == PERMISSION_GRANTED
        } catch (e: Exception) {
            loge("exception in isDefaultSmsAppNotificationEnabled: $e")
            return false
        }
    }

    fun openSettingForDefaultSmsApp(ctx: Context) {
        try {
            val defSmsPkg = Telephony.Sms.getDefaultSmsPackage(ctx)
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", defSmsPkg, null)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            loge("exception in openSettingForDefaultSmsApp: $e")
        }
    }

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
        val locale = if (languageCode == "")
            Locale.getDefault()
        else {
            if (languageCode.contains("-")) { // e.g.: pt-rBR
                val parts = languageCode.split("-")
                Locale(parts[0], parts[1])
            } else {
                Locale(languageCode)
            }
        }

        Locale.setDefault(locale)

        val resources = ctx.resources
        val configuration = resources.configuration
        configuration.setLocale(locale)

        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    fun isRunningInWorkProfile(ctx: Context): Boolean {
        val um = ctx.getSystemService(Context.USER_SERVICE) as UserManager

        return if (Build.VERSION.SDK_INT >= Def.ANDROID_11) { // android 10 ignored
            um.isManagedProfile
        } else
            false
    }

    fun writeDataToUri(ctx: Context, uri: Uri, dataToWrite: ByteArray): Boolean {
        return try {
            ctx.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(dataToWrite)
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    fun readDataFromUri(ctx: Context, uri: Uri): ByteArray? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.buffered().readBytes()
            }
        } catch (_: IOException) {
            null
        }
    }

    fun getFilename(ctx: Context, uri: Uri): String? {
        val cursor = ctx.contentResolver.query(uri, null, null, null, null)
        var filename: String? = null

        cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)?.let { nameIndex ->
            cursor.moveToFirst()

            filename = cursor.getString(nameIndex)
            cursor.close()
        }

        return filename
    }

    fun basename(fn: String): String {
        val lastDotIndex = fn.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            fn.substring(0, lastDotIndex)
        } else {
            fn
        }
    }

    fun readFile(dir: String, filename: String): ByteArray {
        val file = File(dir, filename)
        val data = file.readBytes()
        return data
    }

    fun writeFile(dir: String, filename: String, data: ByteArray) {
        val file = File(dir, filename)
        file.writeBytes(data)
    }

    // returns `true` if it's the first time
    fun doOnce(ctx: Context, tag: String, doSomething: () -> Unit): Boolean {
        val spf = spf.SharedPref(ctx)
        val alreadyExist = spf.readBoolean(tag, false)
        if (!alreadyExist) {
            spf.writeBoolean(tag, true)
            doSomething()
            return true
        }
        return false
    }

    fun isUUID(string: String): Boolean {
        return try {
            UUID.fromString(string)
            true
        } catch (exception: IllegalArgumentException) {
            false
        }
    }

    // extract "abc" from "https://www.abc.com/...."
    fun domainFromUrl(url: String?): String? {
        if (url == null)
            return null

        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return null
            val domain = host.split('.').takeLast(2).joinToString(".")
            domain
        } catch (_: Exception) {
            null
        }
    }
}