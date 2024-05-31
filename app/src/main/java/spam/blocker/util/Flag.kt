package spam.blocker.util

import kotlinx.serialization.Serializable

@Serializable
class Flag(var value: Int) {

    // check if it has a flag
    fun Has(f: Int): Boolean {
        return value and f == f
    }

    // add or remove a flag
    fun set(f: Int, enabled: Boolean) {
        value = if (enabled) { // add flag
            value or f
        } else { // clear flag
            value and f.inv()
        }
    }

    // Generate string "imdlc" from flags
    // params:
    //   attrMap - mapOf(IgnoreCase -> "i", DotMatchAll -> "d", ...)
    //   inverse - invert the showing behavior of some flags,
    //     by default it's: show when set
    //     if it's inverted: show when not set
    fun toStr(attrMap: Map<Int, String>, inverse: List<Int>): String {
        var ret = ""
        attrMap.forEach { (k, v) ->
            if (inverse.contains(k)) {
                if (!Has(k))
                    ret += v
            } else {
                if (Has(k))
                    ret += v
            }
        }
        return ret
    }
}
