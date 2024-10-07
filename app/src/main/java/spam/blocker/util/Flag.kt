package spam.blocker.util

import spam.blocker.def.Def

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

// Generate string "imdlc" from flags
// params:
//   attrMap - mapOf(IgnoreCase -> "i", DotMatchAll -> "d", ...)
//   inverse - invert the showing behavior of some flags,
//     by default it's: show when set
//     if it's inverted: show when not set
fun Int.toFlagStr(
    attrMap: Map<Int, String> = Def.MAP_REGEX_FLAGS,
    inverse: List<Int> = Def.LIST_REGEX_FLAG_INVERSE,
): String {
    var ret = ""
    attrMap.forEach { (k, v) ->
        if (inverse.contains(k)) {
            if (!hasFlag(k))
                ret += v
        } else {
            if (hasFlag(k))
                ret += v
        }
    }
    return ret
}

