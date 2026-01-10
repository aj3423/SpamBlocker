package spam.blocker.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.LocaleManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.os.PowerManager
import android.os.UserManager
import android.provider.CalendarContract
import android.provider.CallLog.Calls
import android.provider.OpenableColumns
import android.provider.Settings
import android.provider.Telephony
import android.provider.Telephony.Sms
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.database.getStringOrNull
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.def.Def.ANDROID_13
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
import java.util.regex.Pattern

typealias Lambda = () -> Unit
typealias Lambda1<A> = (A) -> Unit
typealias Lambda2<A, B> = (A, B) -> Unit
typealias Lambda3<A, B, C> = (A, B, C) -> Unit
typealias Lambda4<A, B, C, D> = (A, B, C, D) -> Unit



fun String.escape(): String {
    return this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
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

// For matching phone number only, it handles all regex flags like: RawMode/IgnoreCC
fun String.regexMatchesNumber(rawNumber: String, regexFlags: Int): Boolean {
    val toMatch = if(regexFlags.hasFlag(Def.FLAG_REGEX_RAW_NUMBER)) {
        rawNumber
    } else if (regexFlags.hasFlag(Def.FLAG_REGEX_IGNORE_CC)) {
        val intn = Util.parseInternationalNumber(rawNumber) // it returns Pair<CC, Phone>?
        if (intn != null) {
            intn.second
        } else {
            Util.clearNumber(rawNumber)
        }
    } else {
        Util.clearNumber(rawNumber)
    }

    val opts = Util.flagsToRegexOptions(regexFlags)
    return try {
        this.toRegex(opts).matches(toMatch)
    } catch (_: Exception) {
        false
    }
}
// For matching anything other than phone number, it won't raise exception.
fun String.regexMatches(targetStr: String, regexFlags: Int): Boolean {
    val opts = Util.flagsToRegexOptions(regexFlags)
    return this.toRegex(opts).matches(targetStr)
}

fun String.regexReplace(
    from: String,
    to: String,
    regexFlags: Int
): String {
    val opts = Util.flagsToRegexOptions(regexFlags)
    return from.toRegex(opts).replace(this, to)
}

object Util {

    fun <T>inRange(index: Int, list: List<T>) : Boolean {
        return index >= 0 && index < list.size
    }
    fun isFreshInstall(ctx: Context) : Boolean {
        return with(ctx.packageManager.getPackageInfo(ctx.packageName, 0)) {
            firstInstallTime == lastUpdateTime
        }
    }

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

    fun formatTime(ctx: Context, timestamp: Long): String {
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

    fun isInternationalNumber(number: String): Boolean {
        return "^\\+\\d+$".toRegex().matches(number)
    }

    fun parseInternationalNumber(rawNumber: String): Pair<String, String>? {
        if (!isInternationalNumber(rawNumber))
            return null
        val number = rawNumber.substring(1)

        val matcher =
            Pattern.compile("^([17]|2[07]|3[0123469]|4[013456789]|5[12345678]|6[0123456]|8[1246]|9[0123458]|\\d{3})\\d*?(\\d{4,6})$")
                .matcher(number);
        if (!matcher.find()) {
            return null
        }
        val cc = matcher.group(1) ?: return null

        val phone = number.substring(cc.length)

        return Pair(cc, phone)
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
    // Numbers like "123&456" are forwarded numbers.
    //  https://github.com/aj3423/SpamBlocker/issues/488
    fun isAmpersandNumber(rawNumber: String): Boolean {
        val regex = Regex("""^\d+&\d+$""")
        return regex.matches(rawNumber)
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
            ?.firstOrNull()?.value

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

    // The user is using wildcard `*` instead of `.*`, it could be a typo
    fun regexWildcardNotSupported(
        regexStr: String,
    ): Boolean {
        val regex = Regex("[0-9a-zA-Z]\\*")
        return regex.containsMatchIn(regexStr)
    }

    // It returns first found error
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

        if (!flags.hasFlag(Def.FLAG_REGEX_CASE_SENSITIVE)) {
            opts.add(RegexOption.IGNORE_CASE)
        }

        if (flags.hasFlag(Def.FLAG_REGEX_MULTILINE)) {
            opts.add(RegexOption.MULTILINE)
        }

        opts.add(RegexOption.DOT_MATCHES_ALL)

//        if (flags.hasFlag(Def.FLAG_REGEX_LITERAL)) {
//            opts.add(RegexOption.LITERAL)
//        }
        return opts
    }

    private var cacheAppList: List<AppInfo>? = null
    private val lock_1 = Any()

    @SuppressLint("UseCompatLoadingForDrawables")
    fun listApps(ctx: Context): List<AppInfo> {
        synchronized(lock_1) {
            if (cacheAppList == null) {
                val pm = ctx.packageManager

                val packageInfos = getPackagesHoldingPermissions(
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

    fun openDefaultSmsAppNotificationSetting(ctx: Context) {
        try {
            val defSmsPkg = Telephony.Sms.getDefaultSmsPackage(ctx)

            val intent = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, defSmsPkg)
            }
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

    // Get the geo-location from the number, using libphonenumber's geo database
    fun numberGeoLocation(ctx: Context, rawNumber: String) : String? {
        if (!Permission.phoneState.isGranted) {
            return null
        }
        return try {
            val phoneUtil = PhoneNumberUtil.getInstance()
            val geocoder = PhoneNumberOfflineGeocoder.getInstance()

            val ccAlpha2 = CountryCode.localeAlpha2(ctx) // "US"
            val phoneNumber = phoneUtil.parse(rawNumber, ccAlpha2)

            if (!phoneUtil.isValidNumber(phoneNumber)) {
                return null
            }
            val location = geocoder.getDescriptionForNumber(phoneNumber, Locale.getDefault())
            location
        } catch (_: Exception) {
            null
        }
    }

    fun isEmergencyNumber(ctx: Context, rawNumber: String) : Boolean {
        val telephonyManager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return telephonyManager.isEmergencyNumber(rawNumber)
    }
    fun setLocale(ctx: Context, languageCode: String) {
        if (Build.VERSION.SDK_INT >= ANDROID_13) {
            // Convert pt-rBR -> pt-BR, which is the format used by Android API.
            val normalizedCode = languageCode.replace("-r", "-")
            val localeList = LocaleList.forLanguageTags(normalizedCode)
            ctx.getSystemService(LocaleManager::class.java)?.applicationLocales = localeList
        } else {
            setLocale_Android_12(ctx, languageCode)
        }
    }

    // `updateConfiguration` only works on app start
    // Do NOT use `AppCompatDelegate.setApplicationLocales()`, that library costs 400k bytes.
    private fun setLocale_Android_12(ctx: Context, languageCode: String) {
        val locale = if (languageCode == "")
            Locale.getDefault()
        else {
            if (languageCode.contains("-")) { // e.g.: pt-rBR
                val parts = languageCode.split("-")
                val p0 = parts[0]
                val p1 = parts[1]
                if (p1.startsWith("r")) {
                    Locale(p0, p1.substring(1)) // remove leading "r" from "rBR"
                } else {
                    Locale(p0, p1)
                }
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
        return runBlocking {
            withContext(IO) {
                try {
                    // Use coroutine to avoid accessing network on main thread,
                    //   for importing backup directly from cloud file.
                    ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.buffered().readBytes()
                    }
                } catch (_: IOException) {
                    null
                }
            }
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

    // Device is considered in lockscreen mode if:
    // 1. Screen is off
    // 2. Screen is on but requires PIN/passcode/... to unlock
    fun isDeviceLocked(ctx: Context): Boolean {
        val keyguardManager = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager

        // Check if the screen is off
        val isScreenOff = !powerManager.isInteractive

        // Check if the device is locked (requires authentication)
        val isDeviceLocked = keyguardManager.isDeviceLocked

        return isScreenOff || isDeviceLocked
    }


    fun listUsedAppWithinXSecond(ctx: Context, sec: Int): List<String> {
        val mapApps = mutableMapOf<String, Boolean>()

        val usageStatsManager =
            ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = Now.currentMillis()
        val events = usageStatsManager.queryEvents(currentTime - sec * 1000, currentTime)

        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
            ) {
                mapApps[event.packageName] = true
            }
        }

        return mapApps.keys.toList()
    }

    // Get all events of a list of apps.
    // Returns a Map<pkgName, List<Event>>
    fun getAppsEvents(
        ctx: Context,
        pkgNames: Set<String>,
        withinMillis: Long = 24 * 3600 * 1000, // last 24 hours
    ): Map<String, List<UsageEvents.Event>> {
        val ret = mutableMapOf<String, MutableList<UsageEvents.Event>>()

        val usageStatsManager =
            ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = Now.currentMillis()
        val events = usageStatsManager.queryEvents(currentTime - withinMillis, currentTime)

        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)

            val pkg = event.packageName
            if (pkgNames.contains(pkg)) {
                ret.getOrPut(pkg) { mutableListOf() }.add(event)
            }
        }
        return ret
    }

    fun isAppInForeground(ctx: Context, targetPackageName: String): Boolean {
        val usageStatsManager = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?
            ?: return false // Usage stats service not available

        val now = System.currentTimeMillis()
        // Query stats for a short period (e.g., last 10 seconds)
        // Adjust the interval as needed, but keep it short for efficiency.
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 1000 * 10, // 10 seconds ago
            now
        )

        if (usageStatsList == null || usageStatsList.isEmpty()) {
            return false
        }

        // Sort stats by last time used in descending order
        usageStatsList.sortByDescending { it.lastTimeUsed }

        // The first element in the sorted list is the most recently used app
        val mostRecentApp = usageStatsList[0]

        // Check if the most recently used app's package name matches the target
        return mostRecentApp.packageName == targetPackageName
    }

    fun isSmsAppInForeground(ctx: Context) : Boolean {
        val defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(ctx)
        return isAppInForeground(ctx, defaultSmsApp)
    }

    // List all foreground service names that have started but not stopped yet.
    fun listRunningForegroundServiceNames(
        appEvents: List<UsageEvents.Event>?,
    ): List<String> {

        val startedServices = mutableMapOf<String, Boolean>()
        appEvents
            ?.filter {
                it.eventType == UsageEvents.Event.FOREGROUND_SERVICE_START
                        || it.eventType == UsageEvents.Event.FOREGROUND_SERVICE_STOP
            }
            ?.forEach {
                // Set to `true` if it's START, set to `false` if it's STOP
                startedServices[it.className] =
                    it.eventType == UsageEvents.Event.FOREGROUND_SERVICE_START
            }

        return startedServices.filterValues { it  }.keys.toList()
    }

    fun getPackagesHoldingPermissions(
        pm: PackageManager,
        permissions: Array<String>
    ): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Def.ANDROID_14) {
            pm.getPackagesHoldingPermissions(
                permissions,
                PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            pm.getPackagesHoldingPermissions(permissions, 0)
        }
    }
    class CallInfo(
        val rawNumber: String,
        val type: Int, // answered, missed, ...
        val duration: Long, // in seconds
    )
    fun getHistoryCalls(
        ctx: Context,
        direction: Int, // Def.DIRECTION_INCOMING, Def.DIRECTION_OUTGOING
        withinMillis: Long
    ): List<CallInfo> {
        if (!Permission.callLog.isGranted) { // required for querying content resolver Calls.CONTENT_URI
            return listOf()
        }

        val selection = mutableListOf(
            "${Calls.DATE} >= ${System.currentTimeMillis() - withinMillis}"
        )

        if (direction == Def.DIRECTION_INCOMING) {
            selection.add(
                "${Calls.TYPE} IN (${Calls.INCOMING_TYPE}, ${Calls.MISSED_TYPE}, ${Calls.VOICEMAIL_TYPE}, ${Calls.REJECTED_TYPE}, ${Calls.BLOCKED_TYPE}, ${Calls.ANSWERED_EXTERNALLY_TYPE})"
            )
        } else {
            selection.add(
                "${Calls.TYPE} = ${Calls.OUTGOING_TYPE}"
            )
        }

        val ret = mutableListOf<CallInfo>()
        ctx.contentResolver.query(
            Calls.CONTENT_URI,
            arrayOf(
                Calls.NUMBER,
                Calls.TYPE,
                Calls.DURATION,
            ),
            selection.joinToString(" AND "),
            null,
            null
        )?.use {
            if (it.moveToFirst()) {
                do {
                    ret += CallInfo(
                        rawNumber = it.getStringOrNull(0) ?: "",
                        type = it.getInt(1),
                        duration = it.getLong(2)
                    )
                } while (it.moveToNext())
            }
        }
        return ret
    }
    fun getHistoryCallsByNumber(
        ctx: Context,
        phoneNumber: PhoneNumber,
        direction: Int, // Def.DIRECTION_INCOMING, Def.DIRECTION_OUTGOING
        withinMillis: Long
    ): List<CallInfo> {
        return getHistoryCalls(ctx, direction, withinMillis)
            .filter {
                phoneNumber.isSame(it.rawNumber)
            }
    }
    fun recentCalls(
        ctx: Context,
        withinMillis: Long,

        includingBlocked: Boolean,
        includingAnswered: Boolean,
        minCallDurationSec: Int,
    ): List<CallInfo> {
        return getHistoryCalls(ctx, Def.DIRECTION_INCOMING, withinMillis)
            .filter {
                when (it.type) {
                    Calls.REJECTED_TYPE, Calls.BLOCKED_TYPE -> includingBlocked
                    else -> {
                        if (includingAnswered) {
                            // > 0 means it's answered
                            it.duration > 0 && it.duration < minCallDurationSec.toLong() * 1000
                        } else {
                            false
                        }
                    }
                }
            }
    }

    class SmsInfo(
        val rawNumber: String,
//        val type: Int, // sent, ...
//        val content: String,
    )
    fun getHistorySMSes(
        ctx: Context,
        direction: Int, // Def.DIRECTION_INCOMING, Def.DIRECTION_OUTGOING
        withinMillis: Long
    ): List<SmsInfo> {
        val selection = mutableListOf(
            "${Sms.DATE} >= ${Now.currentMillis() - withinMillis}"
        )

        if (direction == Def.DIRECTION_INCOMING) {
            selection.add(
                "${Sms.TYPE} = ${Sms.MESSAGE_TYPE_INBOX}"
            )
        } else {
            selection.add(
                "${Sms.TYPE} IN (${Sms.MESSAGE_TYPE_SENT}, ${Sms.MESSAGE_TYPE_OUTBOX}, ${Sms.MESSAGE_TYPE_FAILED})"
            )
        }

        val ret = mutableListOf<SmsInfo>()
        try {
            ctx.contentResolver.query(
                Sms.CONTENT_URI,
                arrayOf(Sms.ADDRESS),
                selection.joinToString(" AND "),
                null,
                null
            )?.use {
                if (it.moveToFirst()) {
                    do {
                        val messagedNumber = it.getStringOrNull(0) ?: ""
                        ret += SmsInfo(rawNumber = messagedNumber)

                    } while (it.moveToNext())
                }
            }
        } catch (ignore: Exception) {
        }
        return ret
    }
    fun countHistorySMSByNumber(
        ctx: Context,
        phoneNumber: PhoneNumber,
        direction: Int, // Def.DIRECTION_INCOMING, Def.DIRECTION_OUTGOING
        withinMillis: Long
    ): Int {
        return getHistorySMSes(ctx, direction, withinMillis).filter {
            phoneNumber.isSame(it.rawNumber)
        }.size
    }

    fun ongoingCalendarEvents(ctx: Context) : List<String> {
        if (!Permission.calendar.isGranted)
            return listOf()
        val contentResolver = ctx.contentResolver
        val currentTime = System.currentTimeMillis()
        val timeBuffer = 24 * 60 * 60 * 1000 // 24 hours in milliseconds

        // Define the projection (columns to retrieve)
        val projection = arrayOf(
//            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )

        // Query events within a time range (e.g., from now to 24 hours ago/forward)
        val selection = "${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DTEND} >= ?"
        val selectionArgs = arrayOf(
            (currentTime + timeBuffer).toString(),
            (currentTime - timeBuffer).toString()
        )

        val cursor: Cursor? = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        // List to store ongoing events
        val ongoingEvents = mutableListOf<String>()

        cursor?.use {
            while (it.moveToNext()) {
//                val eventId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events._ID))
                val title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.TITLE))
                val startTime = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                val endTime = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTEND))

                // Check if current time is between startTime and endTime
                if (currentTime in startTime..endTime) {
                    ongoingEvents.add(title)
                }
            }
        }
        return ongoingEvents
    }

    fun isInCall(ctx: Context): Boolean {
        if (!Permission.phoneState.isGranted) {
            return false
        }

        val manager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        return manager.callState == TelephonyManager.CALL_STATE_OFFHOOK
    }
}