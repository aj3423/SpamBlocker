package spam.blocker.util

import org.junit.Assert.assertEquals
import org.junit.Test

private data class TestCase(val formula: String, val expected: Boolean)

class FormulaTest {

    @Test
    fun testcases() {
        val tests = listOf(
            // Basic comparisons
            TestCase("1>2", false),
            TestCase("5 < 10", true),
            TestCase("10 >= 10", true),
            TestCase("3 <= 2", false),

            // AND / OR
            TestCase("1>0 & 2>1", true),
            TestCase("1>0 & 2<1", false),
            TestCase("1>0 | 2<1", true),
            TestCase("1<0 | 2<1", false),

            // Arithmetic + comparisons
            TestCase("(1+2)*3 > 10", false),
            TestCase("10 + 5 > 20", false),
            TestCase("2*3 + 4 <= 10", true),
            TestCase("100 / 4 = 25", true),
            TestCase("100/5< 10", false),

            // Percentages
            TestCase("20% = 0.2", true),
            TestCase("50% < 0.6", true),
            TestCase("120% > 1", true),
            TestCase("2/3 >= 60%", true),
            TestCase("0.5 <= 50%", true),

            // Parentheses & complex
            TestCase("(1>2) | (3<5 & 4>2)", true),
            TestCase("((2+3)*4 >= 20) & (10/2 <= 6)", true),
            TestCase("1>2 & 3<4 | 5>1", true),         // Note: & has higher precedence than |

            // Edge cases
            TestCase("0", false),
            TestCase("0.0", false),
            TestCase("-5 < 0", true),
            TestCase("100%", true),
            TestCase("0%", false),

            // Decimal numbers
            TestCase("2.5 > 2", true),
            TestCase("0.3 < 0.5", true),
            TestCase("1.5 * 2 > 2.9", true),

            // Exception
            TestCase("7/0 = 7", true), // N / 0 -> N, it simply returns the dividend when the divisor is 0
        )

        for (test in tests) {
            val result = Formula.evaluate(test.formula)
            assertEquals(test.formula, test.expected, result)
        }
    }
}