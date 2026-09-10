package spam.blocker.util

class FuzzyFilter(
    filter: String
) {
    private val regex: String = filter.replace(" ", ".*").let { ".*$it.*" }

    fun matches(text: String) : Boolean {
        return regex.regexMatches(text)
    }
}