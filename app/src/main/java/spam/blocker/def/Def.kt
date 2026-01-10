package spam.blocker.def

import spam.blocker.db.Notification.CHANNEL_LOW

object Def {

    // tabs route
    const val CALL_TAB_ROUTE = "call"
    const val SMS_TAB_ROUTE = "sms"
    const val SETTING_TAB_ROUTE = "setting"

    // for block mode: answer + hang-up
    const val LAST_CALLED_TIME = "last_called_time"
    const val LAST_NUMBER_TO_BLOCK = "last_number_to_block"
    const val LAST_NUMBER_TO_BLOCK_DELAY = "last_number_to_block_delay"
    const val DEFAULT_HANG_UP_DELAY = 1 // second
    const val RINGTONE = "ringtone"

    const val SETTING_THEME_TYPE = "theme_type"
    const val SETTING_CHANNEL_SPAM_CALL = "channel_spam_call"
    const val SETTING_CHANNEL_SPAM_SMS = "channel_spam_sms"
    const val SETTING_CHANNEL_VALID_SMS = "channel_valid_sms"
    const val SETTING_CHANNEL_ACTIVE_SMS_CHAT = "channel_active_sms_chat"
    const val SETTING_EMERGENCY_ENABLED = "emergency_enabled"
    const val SETTING_EMERGENCY_COLLAPSED = "emergency_collapsed"
    const val SETTING_EMERGENCY_STIR_ENABLED = "emergency_stir_enabled"
    const val SETTING_EMERGENCY_EXTRA_NUMBERS = "emergency_extra_numbers"
    const val SETTING_EMERGENCY_DURATION = "emergency_duration"
    const val SETTING_EMERGENCY_LAST_TIMESTAMP = "emergency_last_timestamp"
    const val SETTING_STIR_ENABLED = "stir_enabled"
    const val SETTING_STIR_STRICT_OLD = "stir_exclusive"
    const val SETTING_STIR_INCLUDE_UNVERIFIED = "stir_include_unverified"
    const val SETTING_STIR_LENIENT_PRIORITY = "stir_lenient_priority"
    const val SETTING_STIR_PRIORITY = "stir_strict_priority"
    const val SETTING_LANGUAGE = "language"
    const val SETTING_CONTACT_ENABLED = "contacts_permitted"
    const val SETTING_CONTACTS_LENIENT_PRIORITY = "contacts_lenient_priority"
    const val SETTING_CONTACTS_STRICT_PRIORITY = "contacts_strict_priority"
    const val SETTING_CONTACTS_STRICT = "contacts_exclusive"
    const val SETTING_PERMIT_REPEATED = "permit_repeated"
    const val SETTING_PERMIT_REPEATED_BY_SMS = "permit_repeated_by_sms"
    const val SETTING_REPEATED_TIMES = "repeated_times"
    const val SETTING_REPEATED_IN_X_MIN = "repeated_in_x_min"
    const val SETTING_RECENT_APP_IN_X_MIN = "recent_app_in_x_min"
    const val SETTING_MEETING_MODE_PRIORITY = "meeting_mode_priority"
    const val SETTING_PERMIT_DIALED = "permit_dialed"
    const val SETTING_PERMIT_DIALED_BY_SMS = "permit_dialed_by_sms"
    const val SETTING_DIALED_IN_X_DAY = "dialed_in_x_day"
    const val SETTING_PERMIT_ANSWERED = "permit_answered"
    const val SETTING_ANSWERED_MIN_DURATION = "answered_min_duration"
    const val SETTING_ANSWERED_IN_X_DAY = "answered_in_x_day"
    const val SETTING_ANSWERED_WARNING_ACKNOWLEDGED = "answered_warning_acknowledged"
    const val SETTING_BLOCK_TYPE = "block_type"
    const val SETTING_BLOCK_TYPE_DELAY = "block_type_config" // old name
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
    const val SETTING_TESTING_ICON_CLICKED = "testing_icon_clicked"
    const val SETTING_ENABLED = "globally_enable"
    const val SETTING_GLOBAL_ENABLED_COLLAPSED = "global_enable_collapsed"
    const val SETTING_CALL_ENABLED = "call_enable"
    const val SETTING_SMS_ENABLED = "sms_enable"
    const val SETTING_MMS_ENABLED = "mms_enable"
    const val SETTING_SHOW_PASSED = "show_passed"
    const val SETTING_SHOW_BLOCKED = "show_blocked"
    const val SETTING_HISTORY_LOGGING_ENABLED = "history_logging_enabled"
    const val SETTING_HISTORY_EXPIRY_ENABLED = "history_expiry_enabled"
    const val SETTING_SHOW_INDICATOR = "show_indicator"
    const val SETTING_SHOW_GEO_LOCATION = "show_geo_location"
    const val SETTING_FORCE_SHOW_SIM = "force_show_sim"
    const val SETTING_HISTORY_TTL_DAYS = "history_ttl_days"
    const val SETTING_LOG_SMS_CONTENT = "log_sms_content"
    const val SETTING_INITIAL_SMS_ROW_COUNT = "initial_sms_row_count"
    const val SETTING_SPAM_DB_ENABLED = "spam_db_enabled"
    const val SETTING_SPAM_DB_EXPIRY_ENABLED = "spam_db_expiry_enabled"
    const val SETTING_SPAM_DB_TTL = "spam_db_ttl" // for history compatibility only
    const val SETTING_SPAM_DB_PRIORITY = "spam_db_priority"
    const val SETTING_SPAM_DB_TTL_DAYS = "spam_db_ttl_days"
    const val SETTING_NUMBER_RULE_COLLAPSED = "number_rule_collapsed"
    const val SETTING_CONTENT_RULE_COLLAPSED = "content_rule_collapsed"
    const val SETTING_QUICK_COPY_RULE_COLLAPSED = "quick_copy_rule_collapsed"
    const val SETTING_BOT_LIST_COLLAPSED = "bot_list_collapsed"
    const val SETTING_CUSTOM_TILE_ENABLED = "custom_tile_enabled"
    const val SETTING_API_QUERY_LIST_COLLAPSED = "api_query_list_collapsed"
    const val SETTING_API_QUERY_PRIORITY = "api_query_priority"
    const val SETTING_API_REPORT_LIST_COLLAPSED = "api_report_list_collapsed"
    const val SETTING_RULE_LIST_MAX_NONE_SCROLL_ROWS = "rule_list_max_none_scroll_rows"
    const val SETTING_RULE_LIST_HEIGHT_PERCENTAGE = "rule_list_height_percentage"
    const val SETTING_RULE_MAX_REGEX_ROWS = "rule_max_regex_rows"
    const val SETTING_RULE_MAX_DESC_ROWS = "rule_max_description_rows"
    const val SETTING_SMS_ALERT_ENABLED = "call_alert_enabled"
    const val SETTING_SMS_ALERT_COLLAPSED = "call_alert_collapsed"
    const val SETTING_SMS_ALERT_DURATION = "call_alert_duration"
    const val SETTING_SMS_ALERT_REGEX_STR = "call_alert_regex_str"
    const val SETTING_SMS_ALERT_REGEX_FLAGS = "call_alert_regex_flags"
    const val SETTING_SMS_ALERT_TIMESTAMP = "call_alert_timestamp"
    const val SETTING_PUSH_ALERT_COLLAPSED = "push_alert_collapsed"
    const val SETTING_PUSH_ALERT_EXPIRE_TIME = "push_alert_expire_time"
    const val SETTING_PUSH_ALERT_PKG_NAME = "push_alert_pkg_name"
    const val SETTING_PUSH_ALERT_BODY = "push_alert_body"
    const val SETTING_SMS_BOMB_ENABLED = "sms_bomb_enabled"
    const val SETTING_SMS_BOMB_COLLAPSED = "sms_bomb_collapsed"
    const val SETTING_SMS_BOMB_INTERVAL = "sms_bomb_interval"
    const val SETTING_SMS_BOMB_REGEX_STR = "sms_bomb_regex_str"
    const val SETTING_SMS_BOMB_REGEX_FLAGS = "sms_bomb_regex_flags"
    const val SETTING_SMS_BOMB_TIMESTAMP = "sms_bomb_timestamp"
    const val SETTING_SMS_BOMB_LOCKSCREEN_PROTECTION_ENABLED = "sms_bomb_lockscreen_protection_enabled"

    const val NUMBER_REPORTING_BUFFER_HOURS = 1L // 1 hour

    const val DEFAULT_SPAM_DB_TTL = 180 // days

    const val BLOCK_TYPE_REJECT = 0
    const val BLOCK_TYPE_SILENCE = 1
    const val BLOCK_TYPE_ANSWER_AND_HANGUP = 2

//    const val DEF_SPAM_IMPORTANCE = NotificationManager.IMPORTANCE_LOW
    const val DEF_SPAM_CHANNEL = CHANNEL_LOW

    const val DEF_BLOCK_TYPE = BLOCK_TYPE_REJECT

    // allowed (1~9, 100+)
    const val RESULT_ALLOWED_BY_DEFAULT = 1
    const val RESULT_ALLOWED_BY_NUMBER_REGEX = 2
    const val RESULT_ALLOWED_BY_CONTACT = 3
    const val RESULT_ALLOWED_BY_RECENT_APP = 4
    const val RESULT_ALLOWED_BY_REPEATED = 5
    const val RESULT_ALLOWED_BY_CONTENT_RULE = 6
    const val RESULT_ALLOWED_BY_DIALED = 7
    const val RESULT_ALLOWED_BY_OFF_TIME = 8
    const val RESULT_ALLOWED_BY_EMERGENCY_CALL = 9
    const val RESULT_ALLOWED_BY_STIR = 100 // not expected to have that many features...
    const val RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX = 101
    const val RESULT_ALLOWED_BY_CONTACT_REGEX = 102
    const val RESULT_ALLOWED_BY_API_QUERY = 103
    const val RESULT_ALLOWED_BY_SMS_ALERT = 104
    const val RESULT_ALLOWED_BY_EMERGENCY_SITUATION = 105
    const val RESULT_ALLOWED_BY_PUSH_ALERT = 106
    const val RESULT_ALLOWED_BY_ANSWERED = 107
    const val RESULT_ALLOWED_BY_CNAP_REGEX = 108
    const val RESULT_ALLOWED_BY_GEO_LOCATION_REGEX = 109



    // blocked (10~99)
    const val RESULT_BLOCKED_BY_NUMBER_REGEX = 10
    const val RESULT_BLOCKED_BY_CONTENT_RULE = 11
    const val RESULT_BLOCKED_BY_NON_CONTACT = 12
    const val RESULT_BLOCKED_BY_STIR = 13
    const val RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX = 14
    const val RESULT_BLOCKED_BY_CONTACT_REGEX = 15
    const val RESULT_BLOCKED_BY_SPAM_DB = 16
    const val RESULT_BLOCKED_BY_MEETING_MODE = 17
    const val RESULT_BLOCKED_BY_API_QUERY = 18
    const val RESULT_BLOCKED_BY_SMS_BOMB = 19
    const val RESULT_BLOCKED_BY_CNAP_REGEX = 20
    const val RESULT_BLOCKED_BY_GEO_LOCATION_REGEX = 21


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
    const val FLAG_AUTO_COPY = 1 shl 6


    // regexFlags, max: 1 shl 30
    const val FLAG_REGEX_IGNORE_CASE = 1 shl 0 // remove this after 2027-01-01, replaced by FLAG_REGEX_CASE_SENSITIVE
    const val FLAG_REGEX_MULTILINE = 1 shl 1
//    const val FLAG_REGEX_DOT_MATCH_ALL = 1 shl 2
//    const val FLAG_REGEX_LITERAL = 1 shl 3
    const val FLAG_REGEX_CASE_SENSITIVE = 1 shl 4

    const val FLAG_REGEX_RAW_NUMBER = 1 shl 10
    const val FLAG_REGEX_FOR_CONTACT_GROUP = 1 shl 11
    const val FLAG_REGEX_FOR_CONTACT = 1 shl 12
    const val FLAG_REGEX_IGNORE_CC = 1 shl 13
    const val FLAG_REGEX_FOR_CNAP = 1 shl 14
    const val FLAG_REGEX_FOR_GEO_LOCATION = 1 shl 15



    const val DefaultRegexFlags =  0

    val MAP_REGEX_FLAGS = mapOf(
        FLAG_REGEX_CASE_SENSITIVE to "I",
//        FLAG_REGEX_MULTILINE to "m",
//        FLAG_REGEX_DOT_MATCH_ALL to "d",
//        FLAG_REGEX_LITERAL to "l",
        FLAG_REGEX_RAW_NUMBER to "®", // r
        FLAG_REGEX_FOR_CONTACT_GROUP to "g",
        FLAG_REGEX_FOR_CONTACT to "c",
        FLAG_REGEX_IGNORE_CC to "🌐",
        FLAG_REGEX_FOR_CNAP to "☑",
        FLAG_REGEX_FOR_GEO_LOCATION to "⚲"
    )
    const val REGEX_FLAGS_RIC = FLAG_REGEX_RAW_NUMBER or FLAG_REGEX_IGNORE_CC or FLAG_REGEX_CASE_SENSITIVE

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