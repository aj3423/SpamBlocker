package spam.blocker.util.SharedPref

import android.content.Context
import android.content.SharedPreferences
import spam.blocker.def.Def

open class SharedPref(private val ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "settings"
    }

    fun writeString(key: String, value: String) {
        val editor = prefs.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun readString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun writeInt(key: String, value: Int) {
        val editor = prefs.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    fun readInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun writeBoolean(key: String, value: Boolean) {
        val editor = prefs.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun readBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
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

class Global(ctx: Context) : SharedPref(ctx) {
    fun isGloballyEnabled(): Boolean { return readBoolean(Def.SETTING_ENABLED, false) }
    fun setGloballyEnabled(enabled: Boolean) { writeBoolean(Def.SETTING_ENABLED, enabled) }
    fun toggleGloballyEnabled() { writeBoolean(Def.SETTING_ENABLED, !isGloballyEnabled()) }

    fun isDarkTheme(): Boolean { return readBoolean(Def.SETTING_DARK_THEME, false) }
    fun setDarkTheme(enabled: Boolean) { writeBoolean(Def.SETTING_DARK_THEME, enabled) }
    fun toggleDarkTheme() { setDarkTheme(!isDarkTheme()) }

    fun getLanguage(): String { return readString(Def.SETTING_LANGUAGE, "en") }
    fun setLanguage(lang: String) { writeString(Def.SETTING_LANGUAGE, lang) }

    fun getActiveTab(): String { return readString(Def.SETTING_ACTIVE_TAB, "") }
    fun setActiveTab(tab: String) { writeString(Def.SETTING_ACTIVE_TAB, tab) }

    fun hasAskedForAllPermissions(): Boolean {
        return readBoolean(Def.SETTING_REQUIRE_PERMISSION_ONCE, false)
    }
    fun setAskedForAllPermission() {
        writeBoolean(Def.SETTING_REQUIRE_PERMISSION_ONCE, true)
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


class Stir(ctx: Context) : SharedPref(ctx) {
    fun isEnabled(): Boolean {
        return readBoolean(Def.SETTING_STIR_ENABLED, false)
    }
    fun setEnabled(enabled: Boolean) {
        writeBoolean(Def.SETTING_STIR_ENABLED, enabled)
    }
    fun toggleEnabled() {
        setEnabled(!isEnabled())
    }
    fun isExclusive() : Boolean {
        return readBoolean(Def.SETTING_STIR_EXCLUSIVE, false)
    }
    fun setExclusive(exclusive: Boolean) {
        writeBoolean(Def.SETTING_STIR_EXCLUSIVE, exclusive)
    }
    fun toggleExclusive() {
        setExclusive(!isExclusive())
    }
    fun isIncludeUnverified() : Boolean {
        return readBoolean(Def.SETTING_STIR_INCLUDE_UNVERIFIED, false)
    }
    fun setIncludeUnverified(include: Boolean) {
        writeBoolean(Def.SETTING_STIR_INCLUDE_UNVERIFIED, include)
    }
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
    fun getConfig(): Pair<Int, Int> {
        val times = readInt(Def.SETTING_REPEATED_TIMES, 1)
        val inXMin = readInt(Def.SETTING_REPEATED_IN_X_MIN, 5)
        return Pair(times, inXMin)
    }
    fun setConfig(times: Int, inXMin: Int) {
        writeInt(Def.SETTING_REPEATED_TIMES, times)
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
    fun getConfig(): Int {
        return readInt(Def.SETTING_DIALED_IN_X_DAY, 3)
    }
    fun setConfig(inXDay: Int) {
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
    fun setStart(hour: Int, min: Int) {
        writeInt(Def.SETTING_OFF_TIME_START_HOUR, hour)
        writeInt(Def.SETTING_OFF_TIME_START_MIN, min)
    }
    fun setEnd(hour: Int, min: Int) {
        writeInt(Def.SETTING_OFF_TIME_END_HOUR, hour)
        writeInt(Def.SETTING_OFF_TIME_END_MIN, min)
    }
    fun getStart(): Pair<Int, Int> {
        return Pair(
            readInt(Def.SETTING_OFF_TIME_START_HOUR, 0),
            readInt(Def.SETTING_OFF_TIME_START_MIN, 0)
        )
    }
    fun getEnd(): Pair<Int, Int> {
        return Pair(
            readInt(Def.SETTING_OFF_TIME_END_HOUR, 0),
            readInt(Def.SETTING_OFF_TIME_END_MIN, 0)
        )
    }
}
class Silence(ctx: Context) : SharedPref(ctx) {
    fun setEnabled(enabled: Boolean) {
        writeBoolean(Def.SETTING_ENABLE_SILENCE_CALL, enabled)
    }
    fun isEnabled(): Boolean {
        return readBoolean(Def.SETTING_ENABLE_SILENCE_CALL, false)
    }
}
class RecentApps(ctx: Context) : SharedPref(ctx) {
    fun getList(): List<String> {
        val s = readString(Def.SETTING_RECENT_APPS, "")
        if (s == "") {
            return listOf()
        }
        return s.split(",")
    }
    fun setList(list: List<String>) {
        writeString(Def.SETTING_RECENT_APPS, list.joinToString(","))
    }
    fun getConfig() : Int {
        return readInt(Def.SETTING_RECENT_APP_IN_X_MIN, 5)
    }
    fun setConfig(inXMin : Int) {
        writeInt(Def.SETTING_RECENT_APP_IN_X_MIN, inXMin)
    }
}

