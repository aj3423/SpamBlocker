package spam.blocker.def

import android.app.NotificationManager

object Def {

    // tabs route
    const val CALL_TAB_ROUTE = "call"
    const val SMS_TAB_ROUTE = "sms"
    const val SETTING_TAB_ROUTE = "setting"

    // for block mode: answer + hang-up
    const val LAST_CALLED_TIME = "last_called_time"
    const val LAST_NUMBER_TO_BLOCK = "last_number_to_block"

    const val SETTING_THEME_TYPE = "theme_type"
    const val SETTING_STIR_ENABLED = "stir_enabled"
    const val SETTING_STIR_EXCLUSIVE = "stir_exclusive"
    const val SETTING_STIR_INCLUDE_UNVERIFIED = "stir_include_unverified"
    const val SETTING_LANGUAGE = "language"
    const val SETTING_CONTACT_ENABLED = "contacts_permitted"
    const val SETTING_CONTACTS_EXCLUSIVE = "contacts_exclusive"
    const val SETTING_PERMIT_REPEATED = "permit_repeated"
    const val SETTING_REPEATED_TIMES = "repeated_times"
    const val SETTING_REPEATED_IN_X_MIN = "repeated_in_x_min"
    const val SETTING_RECENT_APP_IN_X_MIN = "recent_app_in_x_min"
    const val SETTING_MEETING_MODE_PRIORITY = "meeting_mode_priority"
    const val SETTING_PERMIT_DIALED = "permit_dialed"
    const val SETTING_DIALED_IN_X_DAY = "dialed_in_x_day"
    const val SETTING_BLOCK_TYPE = "block_type"
    const val SETTING_ENABLE_OFF_TIME = "off_time"
    const val SETTING_OFF_TIME_START_HOUR = "off_time_start_hour"
    const val SETTING_OFF_TIME_START_MIN = "off_time_start_min"
    const val SETTING_OFF_TIME_END_HOUR = "off_time_end_hour"
    const val SETTING_OFF_TIME_END_MIN = "off_time_end_min"
    const val SETTING_RECENT_APPS = "recent_apps"
    const val SETTING_MEETING_APPS = "meeting_apps"
    const val SETTING_ACTIVE_TAB = "active_tab"
    const val SETTING_WARN_RUNNING_IN_WORK_PROFILE_ONCE = "warn_running_in_work_profile_once"
    const val SETTING_WARN_DOUBLE_SMS = "warn_double_sms"
    const val SETTING_ENABLED = "globally_enable"
    const val SETTING_CALL_ENABLED = "call_enable"
    const val SETTING_SMS_ENABLED = "sms_enable"
    const val SETTING_SHOW_PASSED = "show_passed"
    const val SETTING_SHOW_BLOCKED = "show_blocked"
    const val SETTING_HISTORY_TTL = "history_ttl"
    const val SETTING_LOG_SMS_CONTENT = "log_sms_content"
    const val SETTING_INITIAL_SMS_ROW_COUNT = "initial_sms_row_count"
    const val SETTING_SPAM_DB_ENABLED = "spam_db_enabled"
    const val SETTING_SPAM_DB_TTL = "spam_db_ttl"
    const val SETTING_NUMBER_RULE_COLLAPSED = "number_rule_collapsed"
    const val SETTING_CONTENT_RULE_COLLAPSED = "content_rule_collapsed"
    const val SETTING_QUICK_COPY_RULE_COLLAPSED = "quick_copy_rule_collapsed"
    const val SETTING_BOT_LIST_COLLAPSED = "bot_list_collapsed"
    const val SETTING_API_QUERY_LIST_COLLAPSED = "api_query_list_collapsed"
    const val SETTING_API_REPORT_LIST_COLLAPSED = "api_report_list_collapsed"
    const val SETTING_RULE_LIST_MAX_NONE_SCROLL_ROWS = "rule_list_max_none_scroll_rows"
    const val SETTING_RULE_LIST_HEIGHT_PERCENTAGE = "rule_list_height_percentage"
    const val SETTING_RULE_MAX_REGEX_ROWS = "rule_max_regex_rows"
    const val SETTING_RULE_MAX_DESC_ROWS = "rule_max_description_rows"

    const val NUMBER_REPORTING_BUFFER_HOURS = 1L // 1 hour

    const val DEFAULT_SPAM_DB_TTL = 180 // days

    const val HISTORY_TTL_NEVER_EXPIRE = -1
    const val HISTORY_TTL_DISABLED = 0

    const val BLOCK_TYPE_REJECT = 0
    const val BLOCK_TYPE_SILENCE = 1
    const val BLOCK_TYPE_ANSWER_AND_HANGUP = 2

    const val DEF_SPAM_IMPORTANCE = NotificationManager.IMPORTANCE_LOW
    const val DEF_BLOCK_TYPE = BLOCK_TYPE_REJECT

    // allowed (1~9, 100+)
    const val RESULT_ALLOWED_BY_DEFAULT = 1
    const val RESULT_ALLOWED_BY_NUMBER = 2
    const val RESULT_ALLOWED_BY_CONTACT = 3
    const val RESULT_ALLOWED_BY_RECENT_APP = 4
    const val RESULT_ALLOWED_BY_REPEATED = 5
    const val RESULT_ALLOWED_BY_CONTENT = 6
    const val RESULT_ALLOWED_BY_DIALED = 7
    const val RESULT_ALLOWED_BY_OFF_TIME = 8
    const val RESULT_ALLOWED_BY_EMERGENCY = 9
    const val RESULT_ALLOWED_BY_STIR = 100 // not expected to have that many features...
    const val RESULT_ALLOWED_BY_CONTACT_GROUP = 101
    const val RESULT_ALLOWED_BY_CONTACT_REGEX = 102
    const val RESULT_ALLOWED_BY_API_QUERY = 103


    // blocked (10~99)
    const val RESULT_BLOCKED_BY_NUMBER = 10
    const val RESULT_BLOCKED_BY_CONTENT = 11
    const val RESULT_BLOCKED_BY_NON_CONTACT = 12
    const val RESULT_BLOCKED_BY_STIR = 13
    const val RESULT_BLOCKED_BY_CONTACT_GROUP = 14
    const val RESULT_BLOCKED_BY_CONTACT_REGEX = 15
    const val RESULT_BLOCKED_BY_SPAM_DB = 16
    const val RESULT_BLOCKED_BY_MEETING_MODE = 17
    const val RESULT_BLOCKED_BY_API_QUERY = 18

    fun isBlocked(result: Int): Boolean {
        return result in 10..99
    }

    fun isNotBlocked(result: Int): Boolean {
        return !isBlocked(result)
    }

    // flags
    const val FLAG_FOR_CALL = 1 shl 0
    const val FLAG_FOR_SMS = 1 shl 1
    const val FLAG_FOR_NUMBER = 1 shl 2
    const val FLAG_FOR_CONTENT = 1 shl 3
    const val FLAG_FOR_PASSED = 1 shl 4
    const val FLAG_FOR_BLOCKED = 1 shl 5

    // regexFlags
    const val FLAG_REGEX_IGNORE_CASE = 1 shl 0
    const val FLAG_REGEX_MULTILINE = 1 shl 1
    const val FLAG_REGEX_DOT_MATCH_ALL = 1 shl 2
    const val FLAG_REGEX_LITERAL = 1 shl 3
    const val FLAG_REGEX_RAW_NUMBER = 1 shl 10 // max: 1 shl 30
    const val FLAG_REGEX_FOR_CONTACT_GROUP = 1 shl 11
    const val FLAG_REGEX_FOR_CONTACT = 1 shl 12

    const val DefaultRegexFlags =  FLAG_REGEX_IGNORE_CASE or FLAG_REGEX_DOT_MATCH_ALL

    val MAP_REGEX_FLAGS = mapOf(
        FLAG_REGEX_IGNORE_CASE to "i",
        FLAG_REGEX_MULTILINE to "m",
        FLAG_REGEX_DOT_MATCH_ALL to "d",
        FLAG_REGEX_LITERAL to "l",
        FLAG_REGEX_RAW_NUMBER to "r",
        FLAG_REGEX_FOR_CONTACT_GROUP to "g",
        FLAG_REGEX_FOR_CONTACT to "c",
    )

    // inverse means it won't show labels for these flags when they are set
    // only show labels when they are not set
    val LIST_REGEX_FLAG_INVERSE = listOf(
        FLAG_REGEX_IGNORE_CASE,
        FLAG_REGEX_DOT_MATCH_ALL
    )


    const val ForNumber = 0
    const val ForSms = 1
    const val ForQuickCopy = 2

    const val ForApiQuery = 0
    const val ForApiReport = 1

    const val DIRECTION_INCOMING = 1
    const val DIRECTION_OUTGOING = 2

    const val ANDROID_10 = 29
    const val ANDROID_11 = 30
    const val ANDROID_12 = 31
    const val ANDROID_13 = 33
    const val ANDROID_14 = 34
    const val ANDROID_15 = 35
}