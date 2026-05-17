package spam.blocker.util

import spam.blocker.def.Def.MAP_REGEX_FLAGS

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


