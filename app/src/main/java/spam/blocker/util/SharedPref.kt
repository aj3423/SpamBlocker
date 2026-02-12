package spam.blocker.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import spam.blocker.db.Notification.CHANNEL_HIGH
import spam.blocker.db.Notification.CHANNEL_HIGH_MUTED
import spam.blocker.db.Notification.CHANNEL_LOW
import spam.blocker.def.Def
import spam.blocker.def.Def.DEFAULT_HANG_UP_DELAY
import spam.blocker.ui.theme.Emerald
import spam.blocker.ui.theme.MayaBlue
import spam.blocker.util.TimeUtils.FreshnessColor
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class spf { // for namespace only

    open class SharedPref(ctx: Context) {
        val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        companion object {
            private const val PREFS_NAME = "settings"
        }

        fun str(
            key: String,
            defaultValue: String = ""
        ) : ReadWriteProperty<Any?, String> = object : ReadWriteProperty<Any?, String> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): String {
                return prefs.getString(key, defaultValue) ?: defaultValue
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
                prefs.edit { putString(key, value) }
            }
        }

        fun int(
            key: String,
            defaultValue: Int = 0
        ) : ReadWriteProperty<Any?, Int> = object : ReadWriteProperty<Any?, Int> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
                return prefs.getInt(key, defaultValue)
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                prefs.edit { putInt(key, value) }
            }
        }

        fun long(
            key: String,
            defaultValue: Long = 0
        ) : ReadWriteProperty<Any?, Long> = object : ReadWriteProperty<Any?, Long> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Long {
                return prefs.getLong(key, defaultValue)
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
                prefs.edit { putLong(key, value) }
            }
        }

        fun bool(
            key: String,
            defaultValue: Boolean = false
        ) : ReadWriteProperty<Any?, Boolean> = object : ReadWriteProperty<Any?, Boolean> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
                return prefs.getBoolean(key, defaultValue)
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
                prefs.edit { putBoolean(key, value) }
            }
        }

        // Write nothing, wait for all async operations to complete.
        // Use this before restarting the app process.
        fun flush() {
            // Simulate flush by calling commit() on a new editor
            prefs.edit(commit = true) {
            }
        }

        fun clear() {
            prefs.edit() {
                clear()
            }
        }
    }

    class Temporary(ctx: Context) : SharedPref(ctx) {
        // For answer + hang up
        var lastCallToBlock by str("last_number_to_block")
        var lastCallTime by long("last_called_time")
        var hangUpDelay by int("last_number_to_block_delay", DEFAULT_HANG_UP_DELAY)
        var ringtone by str("ringtone")
    }
    class Global(ctx: Context) : SharedPref(ctx) {
        var isGloballyEnabled by bool("globally_enable")
        var isCollapsed by bool("global_enable_collapsed", true)
        var isCallEnabled by bool("call_enable")
        var isSmsEnabled by bool("sms_enable")
        var isMmsEnabled by bool("mms_enable")

        var themeType by int("theme_type")
        var language by str("language")

        var isTestIconClicked by bool("testing_icon_clicked")

        // Settings below will not be backed up
        var activeTab by str("active_tab", "setting")

        var hasPromptedForRunningInWorkProfile by bool("warn_running_in_work_profile_once")
        var isDoubleSMSWarningDismissed by bool("warn_double_sms")
    }

    class EmergencySituation(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("emergency_enabled")
        var isCollapsed by bool("emergency_collapsed")
        var isStirEnabled by bool("emergency_stir_enabled")
        var duration by int("emergency_duration", 120/*Min*/)

        var extraNumbers by str("emergency_extra_numbers")
        fun getExtraNumbers() : List<String> {
            return extraNumbers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        fun setExtraNumbers(numbers: List<String>) {
            extraNumbers = numbers.joinToString(",")
        }

        var timestamp by long("emergency_last_timestamp")
    }

    class RegexOptions(ctx: Context) : SharedPref(ctx) {
        var isNumberCollapsed by bool("number_rule_collapsed")
        var isContentCollapsed by bool("content_rule_collapsed")
        var isQuickCopyCollapsed by bool("quick_copy_rule_collapsed")

        var maxNoneScrollRows by int("rule_list_max_none_scroll_rows", 10)
        var maxRegexRows by int("rule_max_regex_rows", 3)
        var maxDescRows by int("rule_max_description_rows", 2)
        var ruleListHeightPercentage by int("rule_list_height_percentage", 60)
    }

    class BotOptions(ctx: Context) : SharedPref(ctx) {
        var isListCollapsed by bool("bot_list_collapsed")

        fun key(tileIndex: Int): String {
            return "custom_tile_enabled_$tileIndex"
        }
        fun isDynamicTileEnabled(tileIndex: Int): Boolean {
            return prefs.getBoolean(key(tileIndex), false)
        }
        fun setDynamicTileEnabled(tileIndex: Int, enabled: Boolean) {
            prefs.edit { putBoolean(key(tileIndex), enabled) }
        }
    }
    class ApiQueryOptions(ctx: Context) : SharedPref(ctx) {
        var isListCollapsed by bool("api_query_list_collapsed")
        var priority by int("api_query_priority", -1)
    }
    class ApiReportOptions(ctx: Context) : SharedPref(ctx) {
        var isListCollapsed by bool("api_report_list_collapsed")
    }

    class HistoryOptions(ctx: Context) : SharedPref(ctx) {
        var showPassed by bool("show_passed", true)
        var showBlocked by bool("show_blocked", true)
        var showIndicator by bool("show_indicator")
        var showGeoLocation by bool("show_geo_location", true)
        var forceShowSim by bool("force_show_sim")

        var isLoggingEnabled by bool("history_logging_enabled", true)
        var isExpiryEnabled by bool("history_expiry_enabled")
        var ttl by int("history_ttl_days", 14) // 14 days

        var isLogSmsContentEnabled by bool("log_sms_content")
        var initialSmsRowCount by int("initial_sms_row_count", 1)

        var showTimeColor by bool("show_time_color", false)
        var timeColors by str(
            "time_colors",
            Json.encodeToString(listOf(
                FreshnessColor("10min", Emerald.toArgb()),
                FreshnessColor("today", MayaBlue.toArgb()),
            ))
        )
        fun loadTimeColors() : List<FreshnessColor> {
            return try {
                Json.decodeFromString<List<FreshnessColor>>(timeColors)
            } catch (_: Exception) {
                listOf()
            }
        }
        fun saveTimeColors(list: List<FreshnessColor>) {
            val sorted = list.sorted()
            timeColors = Json.encodeToString(sorted)
        }
    }

    class Stir(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("stir_enabled")
        var isIncludeUnverified by bool("stir_include_unverified")
        var priority by int("stir_strict_priority")
    }

    class SpamDB(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("spam_db_enabled")
        var isExpiryEnabled by bool("spam_db_expiry_enabled")
        var priority by int("spam_db_priority")
        var ttl by int("spam_db_ttl_days", 180) // 180 days
    }

    class Contact(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("contacts_permitted")
        var isStrict by bool("contacts_exclusive")
        var lenientPriority by int("contacts_lenient_priority", 10)
        var strictPriority by int("contacts_strict_priority")
    }
    class RepeatedCall(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("permit_repeated")
        var isSmsEnabled by bool("permit_repeated_by_sms", true)

        var times by int("repeated_times", 1)
        var inXMin by int("repeated_in_x_min", 5)
    }

    class Dialed(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("permit_dialed")
        var isSmsEnabled by bool("permit_dialed_by_sms", true)
        var days by int("dialed_in_x_day", 7)
    }

    class Answered(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("permit_answered")
        var isWarningAcknowledged by bool("answered_warning_acknowledged")
        var minDuration by int("answered_min_duration", 15)
        var days by int("answered_in_x_day", 3)
    }

    class OffTime(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("off_time")

        var startHour by int("off_time_start_hour")
        var endHour by int("off_time_end_hour")

        var startMin by int("off_time_start_min")
        var endMin by int("off_time_end_min")
    }

    class BlockType(ctx: Context) : SharedPref(ctx) {
        var type by int("block_type", Def.DEF_BLOCK_TYPE)
        var delay by str("block_type_config")
    }

    class Notification(ctx: Context) : SharedPref(ctx) {
        var spamCallChannelId by str("channel_spam_call", CHANNEL_LOW)
        var spamSmsChannelId by str("channel_spam_sms", CHANNEL_LOW)
        var validSmsChannelId by str("channel_valid_sms", CHANNEL_HIGH)
        var smsChatChannelId by str("channel_active_sms_chat", CHANNEL_HIGH_MUTED)
    }

    @Serializable
    data class RecentAppInfo(
        var pkgName: String,
        var duration: Int? = null
    ) {
        override fun toString(): String {
            return if (duration == null) {
                pkgName
            } else {
                "$pkgName@$duration"
            }
        }
        companion object {
            fun fromString(str: String) : RecentAppInfo {
                val parts = str.split("@")
                return RecentAppInfo(
                    pkgName = parts[0],
                    duration = parts.getOrNull(1)?.toIntOrNull()
                )
            }
        }
    }
    class RecentApps(ctx: Context) : SharedPref(ctx) {
        var appList by str("recent_apps")

        fun getList(): List<RecentAppInfo> {
            // pkg.a,pkg.b@20,pkg.c
            val s = appList

            if (s == "")
                return listOf()

            return s.split(",").map {
                RecentAppInfo.fromString(it)
            }
        }
        fun setList(list: List<RecentAppInfo>) {
            appList = list.joinToString(",") {
                it.toString()
            }
        }
        fun addPackage(pkgToAdd: String) {
            val l = getList().toMutableList()
            l.add(RecentAppInfo(pkgToAdd, null))
            setList(l)
        }
        fun removePackage(pkgToRemove: String) {
            val l = getList().toMutableList()
            val index = l.indexOfFirst {
                it.pkgName == pkgToRemove
            }
            if (index != -1) {
                l.removeAt(index)
            }
            setList(l)
        }

        var inXMin by int("recent_app_in_x_min", 5)
    }

    /*
    Each item is: package name followed by a list of banned foreground service names, which
     is separated by a ;
    E.g.:
     com.pkg.A
     com.pkg.B@fgServiceName1;fgServiceName2

    They will be concatenated into a string with separator, and saved in the shared pref.
    E.g.:
     com.pkg.A,com.pkg.B@fgServiceName1;fgServiceName2
     */
    @Serializable
    data class MeetingAppInfo(
        var pkgName: String,
        var exclusions: List<String> = listOf()
    ) {
        override fun toString(): String {
            return if (exclusions.isEmpty()) {
                pkgName
            } else {
                "$pkgName@${exclusions.joinToString(";")}"
            }
        }
        companion object {
            fun fromString(str: String) : MeetingAppInfo {
                return if (!str.contains("@"))
                    MeetingAppInfo(
                        pkgName = str,
                    )
                else
                    MeetingAppInfo(
                        pkgName = str.substringBefore("@"),
                        exclusions = str.substringAfter("@").split(";").filter { it.isNotEmpty() }
                    )
            }
        }
    }
    class MeetingMode(ctx: Context) : SharedPref(ctx) {
        var appList by str("meeting_apps")

        fun getList(): List<MeetingAppInfo> {
            val s = appList

            if (s == "")
                return listOf()

            val x = s.split(",").map {
                MeetingAppInfo.fromString(it)
            }
            return x
        }
        fun setList(list: List<MeetingAppInfo>) {
            val x = list.joinToString(",") {
                it.toString()
            }
            appList = x
        }
        fun addPackage(pkgToAdd: String) {
            val l = getList().toMutableList()
            l.add(MeetingAppInfo(pkgToAdd))
            setList(l)
        }
        fun removePackage(pkgToRemove: String) {
            val l = getList().toMutableList()
            val index = l.indexOfFirst {
                it.pkgName == pkgToRemove
            }
            if (index != -1) {
                l.removeAt(index)
            }
            setList(l)
        }

        var priority by int("meeting_mode_priority", 20)
    }

    class SmsAlert(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("call_alert_enabled")
        var isCollapsed by bool("call_alert_collapsed")
        var duration by int("call_alert_duration", 90)
        var regexStr by str("call_alert_regex_str")
        var regexFlags by int("call_alert_regex_flags", Def.DefaultRegexFlags)
        var timestamp by long("call_alert_timestamp", 0)
    }

    class PushAlert(ctx: Context) : SharedPref(ctx) {
        var isCollapsed by bool("push_alert_collapsed")
        var pkgName by str("push_alert_pkg_name")
        var body by str("push_alert_body")
        var expireTime by long("push_alert_expire_time")
    }

    class SmsBomb(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("sms_bomb_enabled")
        var isCollapsed by bool("sms_bomb_collapsed")
        var interval by int("sms_bomb_interval", 30)
        var regexStr by str("sms_bomb_regex_str")
        var regexFlags by int("sms_bomb_regex_flags", Def.DefaultRegexFlags)
        var timestamp by long("sms_bomb_timestamp")
        var isLockScreenProtectionEnabled by bool("sms_bomb_lockscreen_protection_enabled", true)
    }
}
