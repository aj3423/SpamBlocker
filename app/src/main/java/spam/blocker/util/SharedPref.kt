package spam.blocker.util

import android.content.Context
import android.content.SharedPreferences
import spam.blocker.def.Def

class SharedPref(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "settings"
    }

    fun writeString(key: String, value: String) {
        val editor: SharedPreferences.Editor = prefs.edit()
        editor.putString(key, value)
        editor.apply()
    }
    fun readString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun writeInt(key: String, value: Int) {
        val editor: SharedPreferences.Editor = prefs.edit()
        editor.putInt(key, value)
        editor.apply()
    }
    fun readInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun writeBoolean(key: String, value: Boolean) {
        val editor: SharedPreferences.Editor = prefs.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }
    fun readBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    // ----------------------

    fun isDarkTheme(): Boolean {
        return readBoolean(Def.SETTING_DARK_THEME, false)
    }
    fun setDarkTheme(enabled: Boolean) {
        writeBoolean(Def.SETTING_DARK_THEME, enabled)
    }
    fun toggleDarkTheme() {
        setDarkTheme(!isDarkTheme())
    }
    fun isContactEnabled(): Boolean {
        return readBoolean(Def.SETTING_CONTACT_ENABLED, false)
    }
    fun setContactEnabled(enabled: Boolean) {
        writeBoolean(Def.SETTING_CONTACT_ENABLED, enabled)
    }
    fun isContactExclusive() : Boolean {
        return readBoolean(Def.SETTING_CONTACTS_EXCLUSIVE, false)
    }
    fun setContactExclusive(exclusive: Boolean) {
        writeBoolean(Def.SETTING_CONTACTS_EXCLUSIVE, exclusive)
    }
    fun toggleContactExclusive() {
        setContactExclusive(!isContactExclusive())
    }

    fun setRepeatedCallEnabled(enabled: Boolean) {
        writeBoolean(Def.SETTING_PERMIT_REPEATED, enabled)
    }
    fun isRepeatedCallEnabled(): Boolean {
        return readBoolean(Def.SETTING_PERMIT_REPEATED, false)
    }
    fun getRepeatedConfig(): Pair<Int, Int> {
        val times = readInt(Def.SETTING_REPEATED_TIMES, 1)
        val inXMin = readInt(Def.SETTING_REPEATED_IN_X_MIN, 5)
        return Pair(times, inXMin)
    }
    fun setRepeatedConfig(times: Int, inXMin: Int) {
        writeInt(Def.SETTING_REPEATED_TIMES, times)
        writeInt(Def.SETTING_REPEATED_IN_X_MIN, inXMin)
    }

    fun setDialedEnabled(enabled: Boolean) {
        writeBoolean(Def.SETTING_PERMIT_DIALED, enabled)
    }
    fun isDialedEnabled(): Boolean {
        return readBoolean(Def.SETTING_PERMIT_DIALED, false)
    }
    fun getDialedConfig(): Int {
        return readInt(Def.SETTING_DIALED_IN_X_DAY, 3)
    }
    fun setDialedConfig(inXDay: Int) {
        writeInt(Def.SETTING_DIALED_IN_X_DAY, inXDay)
    }

    fun isOffTimeEnabled(): Boolean {
        return readBoolean(Def.SETTING_ENABLE_OFF_TIME, false)
    }
    fun setOffTimeEnabled(enabled: Boolean) {
        writeBoolean(Def.SETTING_ENABLE_OFF_TIME, enabled)
    }
    fun setOffTimeStart(hour: Int, min: Int) {
        writeInt(Def.SETTING_OFF_TIME_START_HOUR, hour)
        writeInt(Def.SETTING_OFF_TIME_START_MIN, min)
    }
    fun setOffTimeEnd(hour: Int, min: Int) {
        writeInt(Def.SETTING_OFF_TIME_END_HOUR, hour)
        writeInt(Def.SETTING_OFF_TIME_END_MIN, min)
    }
    fun getOffTimeStart(): Pair<Int, Int> {
        return Pair(
            readInt(Def.SETTING_OFF_TIME_START_HOUR, 0),
            readInt(Def.SETTING_OFF_TIME_START_MIN, 0)
        )
    }
    fun getOffTimeEnd(): Pair<Int, Int> {
        return Pair(
            readInt(Def.SETTING_OFF_TIME_END_HOUR, 0),
            readInt(Def.SETTING_OFF_TIME_END_MIN, 0)
        )
    }

    fun setSilenceCallEnabled(enabled: Boolean) {
        writeBoolean(Def.SETTING_ENABLE_SILENCE_CALL, enabled)
    }
    fun isSilenceCallEnabled(): Boolean {
        return readBoolean(Def.SETTING_ENABLE_SILENCE_CALL, false)
    }

    fun getRecentAppList(): List<String> {
        val s = readString(Def.SETTING_RECENT_APPS, "")
        if (s == "") {
            return listOf()
        }
        return s.split(",")
    }
    fun setRecentAppList(list: List<String>) {
        writeString(Def.SETTING_RECENT_APPS, list.joinToString(","))
    }
    fun getRecentAppConfig() : Int {
        return readInt(Def.SETTING_RECENT_APP_IN_X_MIN, 5)
    }
    fun setRecentAppConfig(inXMin : Int) {
        writeInt(Def.SETTING_RECENT_APP_IN_X_MIN, inXMin)
    }

    fun getActiveTab(): String {
        return readString(Def.SETTING_ACTIVE_TAB, "")
    }
    fun setActiveTab(tab: String) {
        writeString(Def.SETTING_ACTIVE_TAB, tab)
    }

    fun hasAskedForAllPermissions(): Boolean {
        return readBoolean(Def.SETTING_REQUIRE_PERMISSION_ONCE, false)
    }
    fun setAskedForAllPermission() {
        writeBoolean(Def.SETTING_REQUIRE_PERMISSION_ONCE, true)
    }

    fun isGloballyEnabled(): Boolean {
        return readBoolean(Def.SETTING_ENABLED, false)
    }
    fun setGloballyEnabled(enabled: Boolean) {
        writeBoolean(Def.SETTING_ENABLED, enabled)
    }
    fun toggleGloballyEnabled() {
        writeBoolean(Def.SETTING_ENABLED, !isGloballyEnabled())
    }

    fun getShowPassed(): Boolean {
        return readBoolean(Def.SETTING_SHOW_PASSED, true)
    }
    fun setShowPassed(enabled: Boolean) {
        writeBoolean(Def.SETTING_SHOW_PASSED, enabled)
    }
    fun getShowBlocked(): Boolean {
        return readBoolean(Def.SETTING_SHOW_BLOCKED, true)
    }
    fun setShowBlocked(enabled: Boolean) {
        writeBoolean(Def.SETTING_SHOW_BLOCKED, enabled)
    }
}