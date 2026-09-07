package spam.blocker.util

import spam.blocker.db.BayesianSample
import kotlin.collections.iterator
import kotlin.math.ln
import kotlin.math.abs
import kotlin.math.exp
import kotlin.text.iterator

/**
 * Complement Naive Bayes for SMS spam filtering.
 *
 * Convention in this project:
 *   BayesianSample.category == true  → Spam
 *   BayesianSample.category == false → Ham
 *
 * Returns only the spam probability (0.0 … 1.0).
 */
class ComplementNaiveBayes(
    private val alpha: Double = 1.0,
    private val maxFeatures: Int = 50_000
) {
    // class → (token → L1-normalized weight)   weights are negative
    private val weights = mutableMapOf<Boolean, Map<String, Double>>()
    private var isTrained = false

    fun train(samples: List<BayesianSample>) {
        weights.clear()
        isTrained = false
        if (samples.isEmpty()) return

        // 1. Raw term counts
        val classCounts = mutableMapOf<Boolean, MutableMap<String, Int>>()
        val classTotals = mutableMapOf<Boolean, Int>()
        val globalFreq = mutableMapOf<String, Int>()

        for (s in samples) {
            val tokens = tokenize(s.content)
            val map = classCounts.getOrPut(s.category) { mutableMapOf() }
            var total = classTotals.getOrDefault(s.category, 0)
            for (t in tokens) {
                map[t] = map.getOrDefault(t, 0) + 1
                total++
                globalFreq[t] = globalFreq.getOrDefault(t, 0) + 1
            }
            classTotals[s.category] = total
        }

        // Keep top features
        val kept = if (globalFreq.size > maxFeatures) {
            globalFreq.entries
                .sortedByDescending { it.value }
                .take(maxFeatures)
                .map { it.key }
                .toSet()
        } else {
            globalFreq.keys
        }
        val vocabSize = kept.size
        if (vocabSize == 0) return

        // 2. Complement counts
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

        // 3. log θ + L1 normalisation (weights become negative)
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
        isTrained = true
    }

    /**
     * Returns P(spam) ∈ [0.0, 1.0]
     */
    fun spamProbability(content: String): Double {
        if (!isTrained) return 0.0
        val tokens = tokenize(content)
        if (tokens.isEmpty()) return 0.0

        val wSpam = weights[true] ?: return 0.0
        val wHam = weights[false] ?: return 0.0

        var scoreSpam = 0.0
        var scoreHam = 0.0
        val defaultWeight = -1e-6
        for (t in tokens) {
            scoreSpam += -(wSpam[t] ?: defaultWeight)
            scoreHam += -(wHam[t] ?: defaultWeight)
        }

        val maxS = maxOf(scoreSpam, scoreHam)
        val expSpam = exp(scoreSpam - maxS)
        val expHam = exp(scoreHam - maxS)
        val sum = expSpam + expHam
        return if (sum == 0.0) 0.5 else expSpam / sum
    }

    // ---------- Tokenization (CJK + Hangul + extensions) ----------
    private fun tokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()

        fun flush() {
            if (sb.isNotEmpty()) {
                val tok = sb.toString().lowercase()
                if (tok.isNotEmpty()) result.add(tok)
                sb.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val code = text.codePointAt(i)
            val script = Character.UnicodeScript.of(code)
            val block = Character.UnicodeBlock.of(code)

            val isCjkOrHangul = script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || block == Character.UnicodeBlock.HANGUL_JAMO
                    || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO

            when {
                isCjkOrHangul -> {
                    flush()
                    result.add(String(Character.toChars(code)))
                }
                Character.isLetterOrDigit(code) || Character.getType(code) == Character.NON_SPACING_MARK.toInt() -> {
                    sb.appendCodePoint(code)
                }
                else -> flush()
            }
            i += Character.charCount(code)
        }
        flush()
        return result
    }
}