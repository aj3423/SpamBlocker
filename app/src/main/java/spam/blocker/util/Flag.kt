package spam.blocker.util

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.def.Def.MAP_REGEX_FLAGS
import spam.blocker.def.Def.MAP_REGEX_MODES
import spam.blocker.def.Def.regexModeIconMap
import spam.blocker.ui.widgets.ResIcon

// check if it has a flag
fun Int.hasFlag(f: Int): Boolean {
    return this and f == f
}

// add or remove a flag
fun Int.setFlag(f: Int, enabled: Boolean): Int {
    return if (enabled) { // add flag
        this or f
    } else { // clear flag
        this and f.inv()
    }
}
fun Int.addFlag(f: Int): Int {
    return setFlag(f, true)
}
fun Int.removeFlag(f: Int): Int {
    return setFlag(f, false)
}



/*
*   Regex flags helper
*/
// List all enabled regex flags from an Int
fun Int.enabledRegexFlags(): List<Int> {
    return MAP_REGEX_FLAGS.keys.filter { flagBit ->
        (this and flagBit) == flagBit
    }
}
// Get the enabled regex mode from an Int
fun Int.enabledRegexMode(): Int? {
    return MAP_REGEX_MODES.keys.firstOrNull() { mode ->
        (this and mode) == mode
    }
}
// Get inline str from mode Int value
fun Int.regexModeInlineId(): String {
    return MAP_REGEX_MODES[this]?: "bug"
}
// Generate string "r" / "i" / "c" from enabled flags
fun List<Int>.toRegexFlagsStr(): String {
    return this.filter {
        MAP_REGEX_FLAGS.contains(it)
    }.joinToString("") { MAP_REGEX_FLAGS[it]?: "bug" }
}
// Generate "imdlc" from an Int
fun Int.enabledRegexFlagsStr(): String {
    return this.enabledRegexFlags().toRegexFlagsStr()
}

fun Int.clearRegexMode(): Int {
    // Combine all flags from the map keys into one mask
    val mask = MAP_REGEX_MODES.keys.reduce { acc, flag -> acc or flag }

    // Clear the entire mask at once
    return this and mask.inv()
}
val regexModeInlineMap = buildMap {
    // 2. Others modes
    MAP_REGEX_MODES.forEach { (key, value) ->
        put(value, InlineTextContent(
            placeholder = Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.TextCenter)
        ) {
            ResIcon(regexModeIconMap[key]!!, color = G.palette.regexFlags)
        })
    }
}

