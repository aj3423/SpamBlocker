package spam.blocker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BayesTokenizerTest {

    // URLs are reduced to __url__
    @Test
    fun testUrlHandling() {
        assertEquals(
            listOf("__url__"),
            BayesTokenizer.tokenize("https://example.com")
        )
        assertEquals(
            listOf("__url__"),
            BayesTokenizer.tokenize("http://foo.bar/path?query=1#frag")
        )
        assertEquals(
            listOf("__url__"),
            BayesTokenizer.tokenize("www.google.com")
        )
        assertEquals(
            listOf("visit", "__url__", "now"),
            BayesTokenizer.tokenize("visit spam.xyz now")
        )
        assertEquals(
            listOf("click", "__url__"),
            BayesTokenizer.tokenize("click bit.ly/abc")
        )
        assertEquals(
            listOf("click", "__url__"),
            BayesTokenizer.tokenize("click bit.co/abc")
        )
        assertEquals(
            listOf("check", "__url__", "free", "gift"),
            BayesTokenizer.tokenize("check discount.top/offer?id=123 free gift")
        )
        assertEquals(
            listOf("visit", "__url__", "now"),
            BayesTokenizer.tokenize("visit HTTPS://EXAMPLE.COM/path?q=1 now")
        )
        assertEquals(
            listOf("__url__"),
            BayesTokenizer.tokenize("https://example.com/path?query=one,two!")
        )
        assertEquals(
            listOf("visit", "__url__", "today"),
            BayesTokenizer.tokenize("visit deals.cn today")
        )
        assertEquals(
            listOf("visit", "__url__", "today"),
            BayesTokenizer.tokenize("visit shop.co.uk today")
        )
        assertEquals(
            listOf("visit", "__url__", "today"),
            BayesTokenizer.tokenize("visit xn--bcher-kva.de today")
        )
    }

    // Pure numeric chunks are represented by length(__num_X__), alphanumeric words retain.
    @Test
    fun testNumberChunks() {
        assertEquals(
            listOf("code", "__num_6__"),
            BayesTokenizer.tokenize("code 123456")
        )
        assertEquals(
            listOf("otp", "__num_4__"),
            BayesTokenizer.tokenize("otp 0421")
        )
        assertEquals(
            listOf("__num_1__"),
            BayesTokenizer.tokenize("7")
        )
        assertEquals(
            listOf("__num_10__"),
            BayesTokenizer.tokenize("1234567890")
        )
        assertEquals(
            listOf("ref12abc", "__num_2__", "__num_2__"),
            BayesTokenizer.tokenize("ref12abc 12-34")
        )
        assertEquals(
            listOf("__num_3__"),
            BayesTokenizer.tokenize("１２３")
        )
    }

    // One-character words are noise, two-character and longer words are retained.
    @Test
    fun testShortSentencesAndLengthFiltering() {
        assertEquals(
            emptyList<String>(),
            BayesTokenizer.tokenize("a b c")
        )
        assertEquals(
            listOf("hi"),
            BayesTokenizer.tokenize("hi")
        )
        assertEquals(
            listOf("go", "to"),
            BayesTokenizer.tokenize("I go to a")
        )
        assertEquals(
            listOf("hello", "world"),
            BayesTokenizer.tokenize("hello world")
        )
    }

    // Currency symbols
    @Test
    fun testCurrencyAndSymbols() {
        assertEquals(
            listOf("__num_3__"),
            BayesTokenizer.tokenize("$5,00")
        )
        assertEquals(
            listOf("__money_2__"),
            BayesTokenizer.tokenize("$50")
        )
        assertEquals(
            listOf("__money_4__"),
            BayesTokenizer.tokenize("$5,000")
        )
        assertEquals(
            listOf("__money_3_2__"),
            BayesTokenizer.tokenize("$123.45")
        )
        assertEquals(
            listOf("__money_7_2__"),
            BayesTokenizer.tokenize("€1,234,567.89")
        )
        assertEquals(
            listOf("win", "__money_3__", "bonus"),
            BayesTokenizer.tokenize("win €100 bonus")
        )
        assertEquals(
            listOf("price", "__money_2__", "__money_4__", "__money_5__", "__money_3__"),
            BayesTokenizer.tokenize("price £20 ¥1000 ₩50000 ₹500")
        )
        assertEquals(
            listOf("__num_2__", "%", "discount"),
            BayesTokenizer.tokenize("50% discount")
        )
        assertEquals(
            listOf("contact", "@", "support"),
            BayesTokenizer.tokenize("contact @ support")
        )
    }

    // CJK and Hangul runs produce unigram and bigram features around ordinary words.
    @Test
    fun testCjkAndHangul() {
        assertEquals(
            listOf("中", "奖", "了", "中奖", "奖了"),
            BayesTokenizer.tokenize("中奖了")
        )
        assertEquals(
            listOf("無", "料", "無料"),
            BayesTokenizer.tokenize("無料")
        )
        assertEquals(
            listOf("セ", "ー", "ル", "セー", "ール"),
            BayesTokenizer.tokenize("セール")
        )
        assertEquals(
            listOf("당", "첨", "당첨"),
            BayesTokenizer.tokenize("당첨")
        )
        assertEquals(
            listOf("恭", "喜", "恭喜", "win", "無", "料", "無料"),
            BayesTokenizer.tokenize("恭喜 win 無料")
        )
    }

    // Word joiners are ignored and Unicode text is NFC-normalized before tokenization.
    @Test
    fun testWordJoinersAndDiacritics() {
        assertEquals(
            listOf("spam"),
            BayesTokenizer.tokenize("s\u200Dp\u200Ca\u00B7m")
        )
        assertEquals(
            listOf("café"),
            BayesTokenizer.tokenize("café")
        )
        assertEquals(
            listOf("über"),
            BayesTokenizer.tokenize("über")
        )
        assertEquals(
            listOf("café"),
            BayesTokenizer.tokenize("cafe\u0301")
        )
    }

    // Punctuation separates words, and script transitions flush CJK feature sequences.
    @Test
    fun testWordAndCjkBoundaries() {
        assertEquals(
            listOf("hello", "world", "__num_2__"),
            BayesTokenizer.tokenize("Hello,world!42")
        )
        assertEquals(
            listOf("中", "文", "中文", "sale", "당", "첨", "당첨"),
            BayesTokenizer.tokenize("中文sale당첨")
        )
        assertEquals(
            emptyList<String>(),
            BayesTokenizer.tokenize("   ...\n\t")
        )
    }

    // A realistic spam message retains its useful lexical, numeric, symbol, and URL features.
    @Test
    fun testMixedContentAndPunctuation() {
        assertEquals(
            listOf("urgent", "call", "__num_10__", "to", "claim", "__money_3__", "prize", "at", "__url__"),
            BayesTokenizer.tokenize("URGENT: Call 1234567890 to claim $500 prize at https://win.top/1!")
        )
    }
}
