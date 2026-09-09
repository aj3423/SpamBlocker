package spam.blocker.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

@Serializable
data class CnbModelData(
    val weights: Map<Boolean, Map<String, Double>>
)

class ComplementNaiveBayes(
    private val alpha: Double = 1.0, // smoothing parameter that solves the Zero-Frequency problem
    private val maxFeatures: Int = 50_000 // vocabulary size limit, a 50_000 limit keeps the model file small (1~2 mb)
) {
    // class → (token → L1-normalized weight)   weights are negative
    private val weights = mutableMapOf<Boolean, Map<String, Double>>()

    // Raw frequency state maintained for incremental updates
    private val classCounts = mutableMapOf<Boolean, MutableMap<String, Int>>()
    private val docFreq = mutableMapOf<String, Int>()
    private val globalFreq = mutableMapOf<String, Int>()
    private var totalSamples = 0

    fun train(samples: List<Pair<String, Boolean>>) {
        weights.clear()
        classCounts.clear()
        docFreq.clear()
        globalFreq.clear()
        totalSamples = 0

        if (samples.isEmpty()) return

        totalSamples = samples.size
        for ((content, isSpam) in samples) {
            val tokens = tokenize(content)
            val uniqueTokensInDoc = tokens.toSet()
            for (t in uniqueTokensInDoc) {
                docFreq[t] = docFreq.getOrDefault(t, 0) + 1
            }

            val map = classCounts.getOrPut(isSpam) { mutableMapOf() }
            for (t in tokens) {
                map[t] = map.getOrDefault(t, 0) + 1
                globalFreq[t] = globalFreq.getOrDefault(t, 0) + 1
            }
        }

        recomputeWeights()
    }

    /** Add a single sample incrementally and recompute weights. */
    fun addSample(content: String, isSpam: Boolean) {
        val tokens = tokenize(content)
        val uniqueTokens = tokens.toSet()

        for (t in uniqueTokens) {
            docFreq[t] = docFreq.getOrDefault(t, 0) + 1
        }

        val map = classCounts.getOrPut(isSpam) { mutableMapOf() }
        for (t in tokens) {
            map[t] = map.getOrDefault(t, 0) + 1
            globalFreq[t] = globalFreq.getOrDefault(t, 0) + 1
        }
        totalSamples++

        recomputeWeights()
    }

    /** Remove a single sample incrementally and recompute weights. */
    fun removeSample(content: String, isSpam: Boolean) {
        val tokens = tokenize(content)
        val uniqueTokens = tokens.toSet()

        val map = classCounts[isSpam]
        for (t in tokens) {
            if (map != null) {
                val count = map.getOrDefault(t, 0) - 1
                if (count > 0) map[t] = count else map.remove(t)
            }
            val gCount = globalFreq.getOrDefault(t, 0) - 1
            if (gCount > 0) globalFreq[t] = gCount else globalFreq.remove(t)
        }

        for (t in uniqueTokens) {
            val df = docFreq.getOrDefault(t, 0) - 1
            if (df > 0) docFreq[t] = df else docFreq.remove(t)
        }

        if (totalSamples > 0) totalSamples--
        recomputeWeights()
    }

    /** Change a sample's category (e.g. Spam → Ham or Ham → Spam) and recompute weights. */
    fun changeCategory(content: String, oldIsSpam: Boolean, newIsSpam: Boolean) {
        if (oldIsSpam == newIsSpam) return
        removeSample(content, oldIsSpam)
        addSample(content, newIsSpam)
    }

    private fun recomputeWeights() {
        weights.clear()
        if (totalSamples == 0) return

        // Filter out hapax legomena (DF < 2) if dataset has >= 10 samples
        val minDf = if (totalSamples >= 10) 2 else 1
        val filteredFreq = globalFreq.filterKeys { (docFreq[it] ?: 0) >= minDf }

        // Keep top features
        val kept = if (filteredFreq.size > maxFeatures) {
            filteredFreq.entries
                .sortedByDescending { it.value }
                .take(maxFeatures)
                .map { it.key }
                .toSet()
        } else {
            filteredFreq.keys
        }
        val vocabSize = kept.size
        if (vocabSize == 0) return

        // Complement counts
        val allClasses = classCounts.keys
        if (allClasses.size < 2) return

        val complementCounts = mutableMapOf<Boolean, MutableMap<String, Int>>()
        val complementTotals = mutableMapOf<Boolean, Int>()

        for (c in allClasses) {
            val comp = mutableMapOf<String, Int>()
            var total = 0
            for (other in allClasses) {
                if (other == c) continue
                classCounts[other]?.forEach { (token, cnt) ->
                    if (token in kept) {
                        comp[token] = comp.getOrDefault(token, 0) + cnt
                        total += cnt
                    }
                }
            }
            complementCounts[c] = comp
            complementTotals[c] = total
        }

        // log θ + L1 normalisation (weights become negative)
        for (c in allClasses) {
            val comp = complementCounts[c] ?: continue
            val total = complementTotals[c] ?: 0
            val raw = mutableMapOf<String, Double>()

            for (t in kept) {
                val count = comp[t] ?: 0
                val p = (count + alpha) / (total + alpha * vocabSize)
                raw[t] = ln(p)                     // ln θ ≤ 0
            }

            val l1 = raw.values.sumOf { abs(it) }
            val norm = if (l1 > 0.0) l1 else 1.0
            weights[c] = raw.mapValues { it.value / norm }
        }
    }

    /**
     * Note:
     *  Serialize / Deserialize are only for classifying (`spamProbability()`)
     *  DO NOT do incremental updates after `deserialize()`
     * */
    fun serialize(): String {
        val model = CnbModelData(weights)
        return Json.encodeToString(CnbModelData.serializer(), model)
    }
    fun deserialize(json: String) {
        val model = Json.decodeFromString<CnbModelData>(json)
        weights.clear()
        weights.putAll(model.weights)
    }

    /**
     * Returns P(spam) ∈ [0.01, 0.99]
     */
    fun spamProbability(content: String): Double {
        if (weights.isEmpty()) return 0.0
        val tokens = tokenize(content)
        if (tokens.isEmpty()) return 0.0

        val wSpam = weights[true] ?: return 0.0
        val wHam = weights[false] ?: return 0.0

        var scoreSpam = 0.0
        var scoreHam = 0.0
        for (t in tokens) {
            // Unseen tokens contribute 0 (Standard Complement Naive Bayes)
            val ws = wSpam[t]
            if (ws != null) scoreSpam += -ws

            val wh = wHam[t]
            if (wh != null) scoreHam += -wh
        }

        val maxS = maxOf(scoreSpam, scoreHam)
        val expSpam = exp(scoreSpam - maxS)
        val expHam = exp(scoreHam - maxS)
        val sum = expSpam + expHam
        if (sum == 0.0) return 0.5
        val prob = expSpam / sum
        return prob.coerceIn(0.01, 0.99)
    }

    // ---------- Tokenization (Words for space-delimited, 1-gram + 2-gram for CJK & Hangul) ----------
    fun tokenize(text: String): List<String> {
        val result = mutableListOf<String>()

        // Replace URLs with a unified token
        val normalizedText = Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace(URL_REGEX, " __url__ ")

        val sb = StringBuilder()
        val cjkBuffer = mutableListOf<String>()

        fun flushWord() {
            if (sb.isNotEmpty()) {
                val tok = sb.toString().lowercase()
                if (tok.isNotEmpty()) {
                    // Replace pure digit tokens (OTP codes, random numbers) with their length.
                    if (tok.all { it.isDigit() }) {
                        result.add("__num_${tok.length}__")
                    } else if (tok.length >= 2) {
                        // Keep alphabetic/latin words only if length >= 2 to filter single-letter noise
                        result.add(tok)
                    }
                }
                sb.clear()
            }
        }

        fun flushCjk() {
            if (cjkBuffer.isNotEmpty()) {
                // 1-grams
                result.addAll(cjkBuffer)
                // 2-grams (bigrams)
                for (k in 0 until cjkBuffer.size - 1) {
                    result.add(cjkBuffer[k] + cjkBuffer[k + 1])
                }
                cjkBuffer.clear()
            }
        }

        var i = 0
        while (i < normalizedText.length) {
            val code = normalizedText.codePointAt(i)
            val script = Character.UnicodeScript.of(code)

            val isCjkOrHangul = script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL

            val isCurrencyOrSymbol = code in CURRENCY_AND_SYMBOLS
            val isMark = when (Character.getType(code)) {
                Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt() -> true
                else -> false
            }
                val isWordJoiner = code == ZERO_WIDTH_JOINER
                    || code == ZERO_WIDTH_NON_JOINER
                    || code == CATALAN_MIDDLE_DOT

            when {
                isWordJoiner -> Unit
                isCjkOrHangul -> {
                    flushWord()
                    cjkBuffer.add(String(Character.toChars(code)))
                }
                Character.isLetterOrDigit(code) || isMark -> {
                    flushCjk()
                    sb.appendCodePoint(code)
                }
                isCurrencyOrSymbol -> {
                    flushWord()
                    flushCjk()
                    result.add(String(Character.toChars(code)))
                }
                else -> {
                    flushWord()
                    flushCjk()
                }
            }
            i += Character.charCount(code)
        }
        flushWord()
        flushCjk()
        return result
    }

    companion object {
        private val URL_REGEX = Regex("""(?i)\b(?:https?://|www\.)\S+|(?:[a-z0-9-]+\.)+(?:com|net|org|io|me|co|cc|info|biz|link|xyz|top|site|app|live|online|tk|ml|ga|cf|gq)\b\S*""")
        private val CURRENCY_AND_SYMBOLS = setOf(
            '$'.code, '€'.code, '£'.code, '¥'.code, '₩'.code, '₹'.code, '%'.code, '@'.code
        )
        private const val ZERO_WIDTH_JOINER = 0x200D
        private const val ZERO_WIDTH_NON_JOINER = 0x200C
        private const val CATALAN_MIDDLE_DOT = 0x00B7
    }
}