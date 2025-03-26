package spam.blocker.util

import android.os.Environment
import spam.blocker.util.Algorithm.b64Encode
import spam.blocker.util.Algorithm.sha1
import java.time.LocalDateTime

fun String.resolveTimeTags(): String {
    val now = LocalDateTime.now()
    return this
        .replace("{year}", now.year.toString())

        .replace("{month}", now.monthValue.toString().padStart(2, '0'))
        .replace("{day}", now.dayOfMonth.toString().padStart(2, '0'))
        .replace("{hour}", now.hour.toString().padStart(2, '0'))
        .replace("{minute}", now.minute.toString().padStart(2, '0'))
        .replace("{second}", now.second.toString().padStart(2, '0'))

        .replace("{month_index}", now.monthValue.toString())
        .replace("{day_index}", now.dayOfMonth.toString())
        .replace("{hour_index}", now.hour.toString())
        .replace("{minute_index}", now.minute.toString())
        .replace("{second_index}", now.second.toString())
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

// Only return:
//   Bearer API_TOKEN
// Not including the string "Authorization: "
fun String.resolveBearerTag(): String {
    var ret = this

    val tpl ="\\{bearer_auth\\((.+?)\\)\\}" // the tpl
    val result = tpl.toRegex().find(ret)

    result?.groups?.size?.let {
        if (it == 2) {
            val g0 = result.groups[0]!!.value
            val g1 = result.groups[1]!!.value

            ret = ret.replace(g0, "Bearer $g1")
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