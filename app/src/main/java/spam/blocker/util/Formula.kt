package spam.blocker.util

import kotlin.math.abs

object Formula {
    fun evaluate(formula: String): Boolean {
        return try {
            Parser(formula).parse() != 0.0
        } catch (_: Exception) {
            false
        }
    }

    private class Parser(private val input: String) {
        private var index = 0

        fun parse(): Double {
            val result = parseOr()
            skipSpaces()
            if (index != input.length) {
                throw IllegalArgumentException("Unexpected character '${input[index]}' at position $index")
            }
            return result
        }

        private fun parseOr(): Double {
            var left = parseAnd()
            while (true) {
                skipSpaces()
                if (!match('|')) return left
                val right = parseAnd()
                left = if (isTrue(left) || isTrue(right)) 1.0 else 0.0
            }
        }

        private fun parseAnd(): Double {
            var left = parseComparison()
            while (true) {
                skipSpaces()
                if (!match('&')) return left
                val right = parseComparison()
                left = if (isTrue(left) && isTrue(right)) 1.0 else 0.0
            }
        }

        private fun parseComparison(): Double {
            var left = parseAddSub()
            while (true) {
                skipSpaces()
                left = when {
                    match(">=") -> if (left >= parseAddSub()) 1.0 else 0.0
                    match("<=") -> if (left <= parseAddSub()) 1.0 else 0.0
                    match('>') -> if (left > parseAddSub()) 1.0 else 0.0
                    match('<') -> if (left < parseAddSub()) 1.0 else 0.0
                    match('=') -> if (abs(left - parseAddSub()) < EPSILON) 1.0 else 0.0
                    else -> return left
                }
            }
        }

        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (true) {
                skipSpaces()
                left = when {
                    match('+') -> left + parseMulDiv()
                    match('-') -> left - parseMulDiv()
                    else -> return left
                }
            }
        }

        private fun parseMulDiv(): Double {
            var left = parseUnary()
            while (true) {
                skipSpaces()
                left = when {
                    match('*') -> left * parseUnary()
                    match('/') -> { // make sure N/0 -> N
                        val right = parseUnary()
                        if (right == 0.0) left else left / right
                    }
                    else -> return left
                }
            }
        }

        private fun parseUnary(): Double {
            skipSpaces()
            return when {
                match('+') -> parseUnary()
                match('-') -> -parseUnary()
                else -> parsePercent()
            }
        }

        private fun parsePercent(): Double {
            var value = parsePrimary()
            while (true) {
                skipSpaces()
                if (!match('%')) return value
                value /= 100.0
            }
        }

        private fun parsePrimary(): Double {
            skipSpaces()
            if (match('(')) {
                val value = parseOr()
                skipSpaces()
                if (!match(')')) {
                    throw IllegalArgumentException("Expected ')' at position $index")
                }
                return value
            }
            return parseNumber()
        }

        private fun parseNumber(): Double {
            skipSpaces()
            val start = index
            var hasDigit = false
            var hasDot = false

            while (index < input.length) {
                val char = input[index]
                when {
                    char.isDigit() -> {
                        hasDigit = true
                        index++
                    }
                    char == '.' && !hasDot -> {
                        hasDot = true
                        index++
                    }
                    else -> break
                }
            }

            if (!hasDigit) {
                throw IllegalArgumentException("Expected number at position $start")
            }

            return input.substring(start, index).toDouble()
        }

        private fun match(char: Char): Boolean {
            skipSpaces()
            if (index >= input.length || input[index] != char) return false
            index++
            return true
        }

        private fun match(text: String): Boolean {
            skipSpaces()
            if (!input.startsWith(text, index)) return false
            index += text.length
            return true
        }

        private fun skipSpaces() {
            while (index < input.length && input[index].isWhitespace()) {
                index++
            }
        }

        private fun isTrue(value: Double): Boolean = value != 0.0
    }

    private const val EPSILON = 1e-9
}
