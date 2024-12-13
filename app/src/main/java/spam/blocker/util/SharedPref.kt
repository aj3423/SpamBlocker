package spam.blocker.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import spam.blocker.def.Def
import spam.blocker.def.Def.DEFAULT_SPAM_DB_TTL
import spam.blocker.def.Def.HISTORY_TTL_NEVER_EXPIRE

class spf { // for namespace only

    open class SharedPref(private val ctx: Context) {
        private val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        companion object {
            private const val PREFS_NAME = "settings"
        }

        fun readString(key: String, defaultValue: String): String {
            return prefs.getString(key, defaultValue) ?: defaultValue
        }
        fun writeString(key: String, value: String) {
            val editor = prefs.edit()
            editor.putString(key, value)
            editor.apply()
        }

        fun readInt(key: String, defaultValue: Int): Int {
            return prefs.getInt(key, defaultValue)
        }
        fun writeInt(key: String, value: Int) {
            val editor = prefs.edit()
            editor.putInt(key, value)
            editor.apply()
        }

        fun readLong(key: String, defaultValue: Long): Long {
            return prefs.getLong(key, defaultValue)
        }
        fun writeLong(key: String, value: Long) {
            val editor = prefs.edit()
            editor.putLong(key, value)
            editor.apply()
        }

        fun readBoolean(key: String, defaultValue: Boolean): Boolean {
            return prefs.getBoolean(key, defaultValue)
        }
        fun writeBoolean(key: String, value: Boolean) {
            val editor = prefs.edit()
            editor.putBoolean(key, value)
            editor.apply()
        }

        fun clear() {
            val editor = prefs.edit()
            editor.clear()
            editor.apply()
        }
//    fun exists(): Boolean {
//        val file = File(ctx.filesDir, "$PREFS_NAME.xml")
//        return file.exists()
//    }
    }

    class Temporary(ctx: Context) : SharedPref(ctx) {
        fun getLastCallToBlock() : Pair<String, Long> {
            return Pair(
                readString(Def.LAST_NUMBER_TO_BLOCK, ""),
                readLong(Def.LAST_CALLED_TIME, 0)
            )
        }
        fun setLastCallToBlock(number: String, timestamp: Long) {
            writeString(Def.LAST_NUMBER_TO_BLOCK, number)
            writeLong(Def.LAST_CALLED_TIME, timestamp)
        }
    }
    class Global(ctx: Context) : SharedPref(ctx) {
        fun isGloballyEnabled(): Boolean { return readBoolean(Def.SETTING_ENABLED, false) }
        fun setGloballyEnabled(enabled: Boolean) { writeBoolean(Def.SETTING_ENABLED, enabled) }
        fun toggleGloballyEnabled() { writeBoolean(Def.SETTING_ENABLED, !isGloballyEnabled()) }
        fun isCallEnabled(): Boolean { return readBoolean(Def.SETTING_CALL_ENABLED, true) }
        fun setCallEnabled(enabled: Boolean) { writeBoolean(Def.SETTING_CALL_ENABLED, enabled) }
        fun isSmsEnabled(): Boolean { return readBoolean(Def.SETTING_SMS_ENABLED, true) }
        fun setSmsEnabled(enabled: Boolean) { writeBoolean(Def.SETTING_SMS_ENABLED, enabled) }

        fun getThemeType(): Int { return readInt(Def.SETTING_THEME_TYPE, 0) }
        fun setThemeType(type: Int) { writeInt(Def.SETTING_THEME_TYPE, type) }

        fun getLanguage(): String { return readString(Def.SETTING_LANGUAGE, "") }
        fun setLanguage(lang: String) { writeString(Def.SETTING_LANGUAGE, lang) }

        // Following settings will not backed up
        fun getActiveTab(): String { return readString(Def.SETTING_ACTIVE_TAB, "setting") }
        fun setActiveTab(tab: String) { writeString(Def.SETTING_ACTIVE_TAB, tab) }

        fun hasPromptedForRunningInWorkProfile(): Boolean {
            return readBoolean(Def.SETTING_WARN_RUNNING_IN_WORK_PROFILE_ONCE, false)
        }
        fun setPromptedForRunningInWorkProfile() {
            writeBoolean(Def.SETTING_WARN_RUNNING_IN_WORK_PROFILE_ONCE, true)
        }
        fun isDoubleSMSWarningDismissed(): Boolean {
            return readBoolean(Def.SETTING_WARN_DOUBLE_SMS, false)
        }
        fun dismissDoubleSMSWarning() {
            writeBoolean(Def.SETTING_WARN_DOUBLE_SMS, true)
        }
    }

    class RegexOptions(ctx: Context) : SharedPref(ctx) {
        fun isNumberCollapsed(): Boolean { return readBoolean(Def.SETTING_NUMBER_RULE_COLLAPSED, false) }
        fun setNumberCollapsed(enabled: Boolean) { writeBoolean(Def.SETTING_NUMBER_RULE_COLLAPSED, enabled) }
        fun isContentCollapsed(): Boolean { return readBoolean(Def.SETTING_CONTENT_RULE_COLLAPSED, false) }
        fun setContentCollapsed(enabled: Boolean) { writeBoolean(Def.SETTING_CONTENT_RULE_COLLAPSED, enabled) }
        fun isQuickCopyCollapsed(): Boolean { return readBoolean(Def.SETTING_QUICK_COPY_RULE_COLLAPSED, false) }
        fun setQuickCopyCollapsed(enabled: Boolean) { writeBoolean(Def.SETTING_QUICK_COPY_RULE_COLLAPSED, enabled) }

        fun getMaxNoneScrollRows(): Int { return readInt(Def.SETTING_RULE_LIST_MAX_NONE_SCROLL_ROWS, 10) }
        fun setMaxNoneScrollRows(rows: Int) { writeInt(Def.SETTING_RULE_LIST_MAX_NONE_SCROLL_ROWS, rows) }
        fun getMaxRegexRows(): Int { return readInt(Def.SETTING_RULE_MAX_REGEX_ROWS, 3) }
        fun setMaxRegexRows(rows: Int) { writeInt(Def.SETTING_RULE_MAX_REGEX_ROWS, rows) }
        fun getMaxDescRows(): Int { return readInt(Def.SETTING_RULE_MAX_DESC_ROWS, 2) }
        fun setMaxDescRows(rows: Int) { writeInt(Def.SETTING_RULE_MAX_DESC_ROWS, rows) }
        fun getRuleListHeightPercentage(): Int { return readInt(Def.SETTING_RULE_LIST_HEIGHT_PERCENTAGE, 60) }
        fun setRuleListHeightPercentage(percentage: Int) { writeInt(Def.SETTING_RULE_LIST_HEIGHT_PERCENTAGE, percentage) }
    }
    class BotOptions(ctx: Context) : SharedPref(ctx) {
        fun isListCollapsed(): Boolean { return readBoolean(Def.SETTING_BOT_LIST_COLLAPSED, false) }
        fun setListCollapsed(enabled: Boolean) { writeBoolean(Def.SETTING_BOT_LIST_COLLAPSED, enabled) }
    }
    class ApiQueryOptions(ctx: Context) : SharedPref(ctx) {
        fun isListCollapsed(): Boolean { return readBoolean(Def.SETTING_API_QUERY_LIST_COLLAPSED, false) }
        fun setListCollapsed(enabled: Boolean) { writeBoolean(Def.SETTING_API_QUERY_LIST_COLLAPSED, enabled) }
    }
    class ApiReportOptions(ctx: Context) : SharedPref(ctx) {
        fun isListCollapsed(): Boolean { return readBoolean(Def.SETTING_API_REPORT_LIST_COLLAPSED, false) }
        fun setListCollapsed(enabled: Boolean) { writeBoolean(Def.SETTING_API_REPORT_LIST_COLLAPSED, enabled) }
    }

    class HistoryOptions(ctx: Context) : SharedPref(ctx) {
        fun getShowPassed(): Boolean { return readBoolean(Def.SETTING_SHOW_PASSED, true) }
        fun setShowPassed(enabled: Boolean) { writeBoolean(Def.SETTING_SHOW_PASSED, enabled) }
        fun getShowBlocked(): Boolean { return readBoolean(Def.SETTING_SHOW_BLOCKED, true) }
        fun setShowBlocked(enabled: Boolean) { writeBoolean(Def.SETTING_SHOW_BLOCKED, enabled) }

        // TimeToLive for history records:
        // -1: history records never expire, will not be auto deleted
        // 0: history logging is completely disabled
        // > 0: records will be deleted after x days
        fun getTTL(): Int { return readInt(Def.SETTING_HISTORY_TTL, HISTORY_TTL_NEVER_EXPIRE) } // -1: never expire
        fun setTTL(days: Int) { writeInt(Def.SETTING_HISTORY_TTL, days) }

        fun isLogSmsContentEnabled(): Boolean { return readBoolean(Def.SETTING_LOG_SMS_CONTENT, false) }
        fun setLogSmsContentEnabled(enabled: Boolean) { writeBoolean(Def.SETTING_LOG_SMS_CONTENT, enabled) }
        fun getInitialSmsRowCount(): Int { return readInt(Def.SETTING_INITIAL_SMS_ROW_COUNT, 1) }
        fun setInitialSmsRowCount(rows: Int) { writeInt(Def.SETTING_INITIAL_SMS_ROW_COUNT, rows) }
    }


    class Stir(ctx: Context) : SharedPref(ctx) {
        fun isEnabled(): Boolean {
            return readBoolean(Def.SETTING_STIR_ENABLED, false)
        }
        fun setEnabled(enabled: Boolean) {
            writeBoolean(Def.SETTING_STIR_ENABLED, enabled)
        }
        fun isExclusive() : Boolean {
            return readBoolean(Def.SETTING_STIR_EXCLUSIVE, false)
        }
        fun setExclusive(exclusive: Boolean) {
            writeBoolean(Def.SETTING_STIR_EXCLUSIVE, exclusive)
        }
        fun isIncludeUnverified() : Boolean {
            return readBoolean(Def.SETTING_STIR_INCLUDE_UNVERIFIED, false)
        }
        fun setIncludeUnverified(include: Boolean) {
            writeBoolean(Def.SETTING_STIR_INCLUDE_UNVERIFIED, include)
        }
    }
    class SpamDB(ctx: Context) : SharedPref(ctx) {
        fun isEnabled(): Boolean {
            return readBoolean(Def.SETTING_SPAM_DB_ENABLED, false)
        }
        fun setEnabled(enabled: Boolean) {
            writeBoolean(Def.SETTING_SPAM_DB_ENABLED, enabled)
        }
        fun getTTL(): Int { return readInt(Def.SETTING_SPAM_DB_TTL, DEFAULT_SPAM_DB_TTL) } // 90 days
        fun setTTL(days: Int) { writeInt(Def.SETTING_SPAM_DB_TTL, days) }
    }
    class Contact(ctx: Context) : SharedPref(ctx) {
        fun isEnabled(): Boolean {
            return readBoolean(Def.SETTING_CONTACT_ENABLED, false)
        }
        fun setEnabled(enabled: Boolean) {
            writeBoolean(Def.SETTING_CONTACT_ENABLED, enabled)
        }
        fun isExclusive() : Boolean {
            return readBoolean(Def.SETTING_CONTACTS_EXCLUSIVE, false)
        }
        fun setExclusive(exclusive: Boolean) {
            writeBoolean(Def.SETTING_CONTACTS_EXCLUSIVE, exclusive)
        }
        fun toggleExclusive() {
            setExclusive(!isExclusive())
        }
    }
    class RepeatedCall(ctx: Context) : SharedPref(ctx) {
        fun setEnabled(enabled: Boolean) {
            writeBoolean(Def.SETTING_PERMIT_REPEATED, enabled)
        }
        fun isEnabled(): Boolean {
            return readBoolean(Def.SETTING_PERMIT_REPEATED, false)
        }
        fun getTimes(): Int {
            return readInt(Def.SETTING_REPEATED_TIMES, 1)
        }
        fun setTimes(times: Int ) {
            writeInt(Def.SETTING_REPEATED_TIMES, times)
        }
        fun getInXMin(): Int {
            return readInt(Def.SETTING_REPEATED_IN_X_MIN, 5)
        }
        fun setInXMin(inXMin: Int) {
            writeInt(Def.SETTING_REPEATED_IN_X_MIN, inXMin)
        }
    }

    class Dialed(ctx: Context) : SharedPref(ctx) {
        fun setEnabled(enabled: Boolean) {
            writeBoolean(Def.SETTING_PERMIT_DIALED, enabled)
        }
        fun isEnabled(): Boolean {
            return readBoolean(Def.SETTING_PERMIT_DIALED, false)
        }
        fun getDays(): Int {
            return readInt(Def.SETTING_DIALED_IN_X_DAY, 3)
        }
        fun setDays(inXDay: Int) {
            writeInt(Def.SETTING_DIALED_IN_X_DAY, inXDay)
        }
    }

    class OffTime(ctx: Context) : SharedPref(ctx) {
        fun isEnabled(): Boolean {
            return readBoolean(Def.SETTING_ENABLE_OFF_TIME, false)
        }
        fun setEnabled(enabled: Boolean) {
            writeBoolean(Def.SETTING_ENABLE_OFF_TIME, enabled)
        }
        fun setStartHour(hour: Int) {
            writeInt(Def.SETTING_OFF_TIME_START_HOUR, hour)
        }
        fun setStartMin(min: Int) {
            writeInt(Def.SETTING_OFF_TIME_START_MIN, min)
        }
        fun setEndHour(hour: Int) {
            writeInt(Def.SETTING_OFF_TIME_END_HOUR, hour)
        }
        fun setEndMin(min: Int) {
            writeInt(Def.SETTING_OFF_TIME_END_MIN, min)
        }
        fun getStartHour(): Int {
            return readInt(Def.SETTING_OFF_TIME_START_HOUR, 0)
        }
        fun getStartMin(): Int {
            return readInt(Def.SETTING_OFF_TIME_START_MIN, 0)
        }
        fun getEndHour(): Int {
            return readInt(Def.SETTING_OFF_TIME_END_HOUR, 0)
        }
        fun getEndMin(): Int {
            return readInt(Def.SETTING_OFF_TIME_END_MIN, 0)
        }
    }
    class BlockType(ctx: Context) : SharedPref(ctx) {
        fun setType(type: Int) {
            writeInt(Def.SETTING_BLOCK_TYPE, type)
        }
        fun getType(): Int {
            return readInt(Def.SETTING_BLOCK_TYPE, Def.DEF_BLOCK_TYPE)
        }
    }
    @Serializable
    data class RecentAppInfo(
        val pkgName: String,
        val duration: Int? = null
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
        fun getList(): List<RecentAppInfo> {
            // pkg.a,pkg.b@20,pkg.c
            val s = readString(Def.SETTING_RECENT_APPS, "")

            if (s == "")
                return listOf()

            return s.split(",").map {
                RecentAppInfo.fromString(it)
            }
        }
        fun setList(list: List<RecentAppInfo>) {
            writeString(Def.SETTING_RECENT_APPS, list.joinToString(",") {
                it.toString()
            })
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
        fun getDefaultMin() : Int {
            return readInt(Def.SETTING_RECENT_APP_IN_X_MIN, 5)
        }
        fun setDefaultMin(inXMin : Int) {
            writeInt(Def.SETTING_RECENT_APP_IN_X_MIN, inXMin)
        }
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
        val pkgName: String,
        val exclusions: List<String> = listOf()
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
        fun getList(): List<MeetingAppInfo> {
            val s = readString(Def.SETTING_MEETING_APPS, "")

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
            writeString(Def.SETTING_MEETING_APPS, x)
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
        fun getPriority() : Int {
            return readInt(Def.SETTING_MEETING_MODE_PRIORITY, 20)
        }
        fun setPriority(priority : Int) {
            writeInt(Def.SETTING_MEETING_MODE_PRIORITY, priority)
        }
    }

}
