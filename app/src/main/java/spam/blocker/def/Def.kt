package spam.blocker.def

import spam.blocker.db.Notification.CHANNEL_LOW

object Def {

    // tabs route
    const val CALL_TAB_ROUTE = "call"
    const val SMS_TAB_ROUTE = "sms"
    const val SETTING_TAB_ROUTE = "setting"

    // for block mode: answer + hang-up
    const val DEFAULT_HANG_UP_DELAY = 1 // second

    const val NUMBER_REPORTING_BUFFER_HOURS = 1L // 1 hour

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