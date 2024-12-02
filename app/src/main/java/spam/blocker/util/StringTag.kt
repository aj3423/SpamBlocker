package spam.blocker.util

import android.os.Environment
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
            "{Downloads}",
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
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
        .replace("{origin_number}", rawNumber!!)
}