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

// List all enabled regex flags from an Int
fun Int.enabledRegexFlags(): List<Int> {
    return MAP_REGEX_FLAGS.keys.filter { flagBit ->
        (this and flagBit) == flagBit
    }
}
// Get the enabled regex mode from an Int
fun Int.enabledRegexMode(): Int {
    return MAP_REGEX_MODES.keys.first { mode ->
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

val regexModeInlineMap = MAP_REGEX_MODES.map { (key, value) ->
    value to InlineTextContent(
        placeholder = Placeholder(
            width = 16.sp,
            height = 16.sp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
        )
    ) {
        ResIcon(
            regexModeIconMap[key]!!,
            color = G.palette.regexFlags
        )
    }
}.toMap()
