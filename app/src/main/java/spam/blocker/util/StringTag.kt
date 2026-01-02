package spam.blocker.util

import android.os.Environment
import spam.blocker.util.Algorithm.b64Encode
import spam.blocker.util.Algorithm.sha1
import java.time.LocalDateTime
import java.util.Locale

// Clear time tags with numeric operations, return values:
//   1. the cleared string
//   2. the adjusted time
// E.g.:
//  - `pattern` == "..._FTC_DNC_{year}-{month}-{day-1}.csv"
//  - `dateTime` == 2026-01-01 00:00:00
//  return value ->
//   - adjusted `pattern` == "..._FTC_DNC_{year}-{month}-{day}.csv"   <-- `{day-1}` becomes `{day}`
//   - adjusted `dateTime` == 2025-12-31 00:00:00   <-- 1 day earlier
private fun adjustTime(
    pattern: String,
    dateTime: LocalDateTime,
    locale: Locale = Locale.getDefault()  // Uses user's current locale
): Pair<String, LocalDateTime> {
    val tagRegex = Regex("\\{([^\\}]+)\\}")

    var retTime = dateTime

    val retPattern = tagRegex.replace(pattern) { matchResult ->
        // e.g.: "day-1"
        val expression = matchResult.groupValues[1].trim()

        // Split "day-1" ->
        //   parts == ["day", "-", "1"]
        val parts = expression.split(Regex("(?<=[-+])|(?=[-+])"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (parts.isEmpty()) return@replace "{$expression}"

        val fieldName = parts[0].lowercase(locale)
        var offset = 0L

        // Parse offset if present: +5 or -3
        if (parts.size >= 3 && (parts[1] == "-" || parts[1] == "+")) {
            val amount = parts.last().toIntOrNull() ?: 0
            offset = if (parts[1] == "+") amount.toLong() else -amount.toLong()
        }

        // calc ret time
        retTime = when (fieldName) {
            "year" -> retTime.plusYears(offset)
            "month", "month_index" -> retTime.plusMonths(offset)
            "day", "day_index", "day_of_week", "day_of_week_index" -> retTime.plusDays(offset)
            "hour", "hour_index" -> retTime.plusHours(offset)
            "minute", "minute_index" -> retTime.plusMinutes(offset)
            "second", "second_index" -> retTime.plusSeconds(offset)
            else -> retTime
        }

        // calc ret string
        when (fieldName) {
            "year", "month", "month_index",
            "day", "day_index", "day_of_week", "day_of_week_index",
            "hour", "hour_index", "minute", "minute_index", "second", "second_index"
                -> "{$fieldName}"
            else -> "{$expression}"
        }
    }
    return Pair(retPattern, retTime)
}

fun String.resolveTimeTags(): String {
    val now = LocalDateTime.now()

    val (str, time) = adjustTime(this, now)

    return str
        .replace("{year}", time.year.toString())

        .replace("{month}", time.monthValue.toString().padStart(2, '0'))
        .replace("{day}", time.dayOfMonth.toString().padStart(2, '0'))
        .replace("{hour}", time.hour.toString().padStart(2, '0'))
        .replace("{minute}", time.minute.toString().padStart(2, '0'))
        .replace("{second}", time.second.toString().padStart(2, '0'))

        .replace("{month_index}", time.monthValue.toString())
        .replace("{day_index}", time.dayOfMonth.toString())
        .replace("{hour_index}", time.hour.toString())
        .replace("{minute_index}", time.minute.toString())
        .replace("{second_index}", time.second.toString())

        .replace("{day_of_week}", time.dayOfWeek.toString())
        .replace("{day_of_week_index}", time.dayOfWeek.value.toString())
}

fun String.resolvePathTags(): String {
    return this
        .replace(
            "{Download}",
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        )
        .replace(
            "{Downloads}", // for history compatibility
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        )
        .replace(
            "{Documents}",
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
        )
        .replace(
            "{sdcard}",
            Environment.getExternalStorageDirectory().absolutePath
        )
}

fun String.resolveNumberTag(
    cc: String? = null,
    domestic: String? = null,
    fullNumber: String? = null,
    rawNumber: String? = null,
): String {
    return this
        .replace("{cc}", cc ?: "")
        .replace("{domestic}", domestic ?: "")
        .replace("{number}", fullNumber ?: ((cc?:"") + (domestic?:"")))
        .replace("{origin_number}", rawNumber?.replace(" ", "") ?: "")
}
fun String.resolveSmsTag(
    smsContent: String? = null,
): String {
    return this
        .replace("{sms}", smsContent ?: "")
}

// Deprecated by `resolveBasicAuthTag`, for history compatibility only.
fun String.resolveBase64Tag(): String {
    var ret = this

    val tpl ="\\{base64\\((.*?)\\)\\}"
    val result = tpl.toRegex().find(ret)

    result?.groups?.size?.let {
        if (it > 1) {
            val g0 = result.groups[0]!!.value // the tpl
            val g1 = result.groups[1]!!.value

            ret = ret.replace(g0, b64Encode(g1))
        }
    }
    return ret
}

// Query by the number's sha1 hash for better privacy (if it's supported by the API).
fun String.resolveSHA1Tag(): String {
    var ret = this

    val tpl ="\\{sha1\\((.*?)\\)\\}"
    val result = tpl.toRegex().find(ret)

    result?.groups?.size?.let {
        if (it > 1) {
            val g0 = result.groups[0]!!.value // the tpl
            val g1 = result.groups[1]!!.value

            ret = ret.replace(g0, sha1(g1.toByteArray()).toHexString().uppercase())
        }
    }
    return ret
}

// Authorization: Basic {base64({username}:{password})}
fun String.resolveBasicAuthTag(): String {
    var ret = this

    val tpl ="\\{basic_auth\\((.+?:.+?)\\)\\}" // the tpl
    val result = tpl.toRegex().find(ret)

    result?.groups?.size?.let {
        if (it == 2) {
            val g0 = result.groups[0]!!.value
            val g1 = result.groups[1]!!.value

            ret = ret.replace(g0, "Authorization: Basic ${b64Encode(g1)}")
        }
    }
    return ret
}

// Return:
//   Authorization: Bearer API_TOKEN
fun String.resolveBearerAuthTag(): String {
    var ret = this

    val tpl ="\\{bearer_auth\\((.+?)\\)\\}" // the tpl
    val result = tpl.toRegex().find(ret)

    result?.groups?.size?.let {
        if (it == 2) {
            val g0 = result.groups[0]!!.value
            val g1 = result.groups[1]!!.value

            ret = ret.replace(g0, "Authorization: Bearer $g1")
        }
    }
    return ret
}

fun String.resolveHttpAuthTag(): String {
    return this
        .resolveBasicAuthTag()
        .resolveBearerAuthTag()
}

fun String.resolveCustomTag(
    mapping: Map<String, String>
): String {
    var ret = this
    mapping.forEach { (k, v) ->
        ret = ret.replace("{$k}", v)
    }
    return ret
}