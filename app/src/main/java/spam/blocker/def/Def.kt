package spam.blocker.def

import android.app.NotificationManager

object Def {
    const val TAG = "1111111111111111"

    const val SETTING_DARK_THEME = "dark_theme"
    const val SETTING_CONTACT_ENABLED = "contacts_permitted"
    const val SETTING_CONTACTS_EXCLUSIVE = "contacts_exclusive"
    const val SETTING_PERMIT_REPEATED = "permit_repeated"
    const val SETTING_REPEATED_TIMES = "repeated_times"
    const val SETTING_REPEATED_IN_X_MIN = "repeated_in_x_min"
    const val SETTING_RECENT_APP_IN_X_MIN = "recent_app_in_x_min"


    const val SETTING_RECENT_APPS = "recent_apps"
    const val SETTING_ACTIVE_TAB = "active_tab"
    const val SETTING_REQUIRE_PERMISSION_ONCE = "require_permission_once"
    const val SETTING_ENABLED = "globally_enable"
    const val SETTING_SHOW_PASSED = "show_passed"
    const val SETTING_SHOW_BLOCKED = "show_blocked"

    const val ON_NEW_CALL = "on_new_call"
    const val ON_NEW_SMS = "on_new_sms"

    const val DEF_SPAM_IMPORTANCE = NotificationManager.IMPORTANCE_LOW


    // allowed
    const val RESULT_ALLOWED_BY_DEFAULT = 1
    const val RESULT_ALLOWED_BY_NUMBER = 2
    const val RESULT_ALLOWED_BY_CONTACT = 3
    const val RESULT_ALLOWED_BY_RECENT_APP = 4
    const val RESULT_ALLOWED_BY_REPEATED = 5
    const val RESULT_ALLOWED_BY_CONTENT = 6

    // blocked
    const val RESULT_BLOCKED_BY_NUMBER = 10
    const val RESULT_BLOCKED_BY_CONTENT = 11
    const val RESULT_BLOCKED_BY_NON_CONTACT = 12

    fun isBlocked(result: Int): Boolean {
        return !isNotBlocked(result)
    }
    fun isNotBlocked(result: Int): Boolean {
        return (result == RESULT_ALLOWED_BY_NUMBER) or
                (result == RESULT_ALLOWED_BY_DEFAULT) or
                (result == RESULT_ALLOWED_BY_RECENT_APP) or
                (result == RESULT_ALLOWED_BY_REPEATED) or
                (result == RESULT_ALLOWED_BY_CONTENT) or
                (result == RESULT_ALLOWED_BY_CONTACT)
    }
}