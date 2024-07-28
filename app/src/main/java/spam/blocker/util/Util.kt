package spam.blocker.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
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
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.SharedPref.SharedPref
import java.io.IOException
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

        fun extractString(regex: Regex, haystack: String) : String? {
            /*
                lookbehind: has `value`, no `group(1)`
                capturing group: has both, should use group(1) only

                so the logic is:
                    if has `value` && no `group(1)`
                        use `value`
                    else if has both
                        use `group1`
             */
            val result = regex.find(haystack)
            val v = result?.value
            val g1 = result?.groupValues?.getOrNull(1)

            if (v != null && g1 == null) {
                return v
            } else if (v != null && g1 != null) {
                return g1
            }

            return null
        }

        fun truncate(str: String, limit: Int = 300, showEllipsis: Boolean = true) : String {
            return if (str.length >= limit)
                str.substring(0, limit) + if (showEllipsis) "..." else ""
            else
                str
        }

        fun formatTimeRange(
//            ctx: Context,
            stHour: Int, stMin: Int, etHour: Int, etMin: Int
        ): String {
            // not use fmt12h, "xx:xx AM - yy:yy PM" is too wide
//            val fmt24h = DateFormat.is24HourFormat(ctx)
//            if (fmt24h) {
                val startTime = String.format("%02d:%02d", stHour, stMin)
                val endTime = String.format("%02d:%02d", etHour, etMin)
                return "$startTime - $endTime"
//            } else {
//                val startTime = String.format(
//                    "%02d:%02d %s",
//                    if (stHour == 0 || stHour == 12) 12 else stHour % 12,
//                    stMin,
//                    if (stHour < 12) "AM" else "PM"
//                )
//                val endTime = String.format(
//                    "%02d:%02d %s",
//                    if (etHour == 0 || etHour == 12) 12 else etHour % 12,
//                    etMin,
//                    if (etHour < 12) "AM" else "PM"
//                )
//                return "$startTime - $endTime"
//            }
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

        fun validateRegex(ctx: Context, regexStr: String, disableNumberOptimization: Boolean) : String? {
            var s = regexStr
            if (s.isNotEmpty() && s.trim() != s)
                return ctx.getString(R.string.pattern_contain_invisible_characters)

            if (!isRegexValid(s))
                return ctx.getString(R.string.invalid_regex_pattern)

            if (s.startsWith("^"))
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

        fun flagsToRegexOptions(flags: Flag) : Set<RegexOption> {
            val opts = mutableSetOf<RegexOption>()
            if (flags.has(Def.FLAG_REGEX_IGNORE_CASE)) {
                opts.add(RegexOption.IGNORE_CASE)
            }
            if (flags.has(Def.FLAG_REGEX_MULTILINE)) {
                opts.add(RegexOption.MULTILINE)
            }
            if (flags.has(Def.FLAG_REGEX_DOT_MATCH_ALL)) {
                opts.add(RegexOption.DOT_MATCHES_ALL)
            }
            if (flags.has(Def.FLAG_REGEX_LITERAL)) {
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
                    val pm = ctx.packageManager

                    val packageInfos = Permissions.getPackagesHoldingPermissions(pm, arrayOf(Manifest.permission.INTERNET))

                    cacheAppList = packageInfos.filter {
                        (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    }.map {
                        val appInfo = it.applicationInfo

                        AppInfo().apply {
                            pkgName = it.packageName
                            label = appInfo.loadLabel(pm).toString()
                            icon = appInfo.loadIcon(pm)
                        }
                    }
                }
            }

            return cacheAppList!!
        }
        fun clearAppsCache() {
            synchronized(lock_1) {
                cacheAppList = null
            }
        }

        // android<13 will always return `false`
        @RequiresApi(Def.ANDROID_13)
        private fun isDefaultSmsAppNotificationEnabled(ctx: Context) : Boolean {
            val defSmsPkg = Telephony.Sms.getDefaultSmsPackage(ctx)

            val pm = ctx.packageManager

            val result = pm.checkPermission(Manifest.permission.POST_NOTIFICATIONS, defSmsPkg)

            return result == PERMISSION_GRANTED
        }

        private var dlgDebouncer = false
        fun checkDoubleNotifications(ctx: Context) {

            if (Build.VERSION.SDK_INT >= Def.ANDROID_13) {
                val spf = Global(ctx)
                if (isDefaultSmsAppNotificationEnabled(ctx) && spf.isGloballyEnabled()&& spf.isSmsEnabled()) {
                    if (!spf.isDoubleSMSWarningDismissed()) {

                        if (dlgDebouncer)
                            return
                        dlgDebouncer = true

                        AlertDialog.Builder(ctx).apply {
                            setTitle(" ")
                            setIcon(R.drawable.ic_warning)
                            setMessage(ctx.resources.getString(R.string.warning_double_sms))
                            setPositiveButton(R.string.open_settings) { _,_ ->
                                val defSmsPkg = Telephony.Sms.getDefaultSmsPackage(ctx)
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.fromParts("package", defSmsPkg, null)
                                context.startActivity(intent)
                            }
                            setNegativeButton(R.string.ignore) { _,_ ->
                                spf.dismissDoubleSMSWarning()
                            }
                            setOnDismissListener {
                                dlgDebouncer = false
                            }
                        }.create().show()
                    }
                }
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
            else
                Locale(languageCode)

            Locale.setDefault(locale)

            val resources = ctx.resources
            val configuration = resources.configuration
            configuration.setLocale(locale)

            resources.updateConfiguration(configuration, resources.displayMetrics)
        }

        fun getScreenHeight(ctx: Context) : Int {
            val windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            display.getMetrics(metrics)
            return metrics.heightPixels
        }

        fun isRunningInWorkProfile(ctx: Context) : Boolean {
            val um = ctx.getSystemService(Context.USER_SERVICE) as UserManager

            return if (Build.VERSION.SDK_INT >= Def.ANDROID_11) { // android 10 ignored
                um.isManagedProfile
            } else
                false
        }

        fun writeDataToUri(ctx: Context, uri: Uri, dataToWrite: ByteArray) : Boolean {
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
        fun basename(fn: String) : String {
            val lastDotIndex = fn.lastIndexOf('.')
            return if (lastDotIndex > 0) {
                fn.substring(0, lastDotIndex)
            } else {
                fn
            }
        }

        // returns `true` if it's the first time
        fun doOnce(ctx: Context, tag: String, doSomething: ()->Unit) : Boolean {
            val spf = SharedPref(ctx)
            val alreadyExist = spf.readBoolean(tag, false)
            if (!alreadyExist) {
                spf.writeBoolean(tag, true)
                doSomething()
                return true
            }
            return false
        }
    }
}