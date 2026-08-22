package com.onemind.app.domain.processing.stages

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.MemorySummary
import com.onemind.app.domain.processing.*
import com.onemind.app.domain.repository.DerivedDataRepository
import javax.inject.Inject

/**
 * Says what a Memory is generally about.
 *
 * The point is a sentence the user can scan in a feed, not an inventory. A Memory
 * of fifteen screenshots and thirty links should read as "a collection of
 * resources about running AI models on Android", because that is what makes it
 * recognisable months later. Listing its contents would just reproduce the
 * problem the app exists to solve.
 *
 * Runs last, so it can draw on everything the earlier stages produced: text read
 * off screenshots, descriptions of photos, extracted links. It reuses that derived
 * data rather than reprocessing anything, and works from a
 * [BoundedAnalysisInput] so a large Memory cannot overrun the model's context.
 */
class SummarizationStage @Inject constructor(
    private val textGenerator: TextGenerator,
    private val derivedDataRepository: DerivedDataRepository
) : ProcessingStage {

    override val id = StageId.SUMMARIZATION

    override suspend fun process(memory: Memory): StageResult {
        val input = BoundedAnalysisInput.from(memory)
        if (input.isEmpty) return StageResult.Skipped

        if (!textGenerator.isAvailable()) {
            // Recorded rather than left blank, so the feed can distinguish "no
            // provider configured" from "not processed yet".
            derivedDataRepository.saveSummary(
                MemorySummary(
                    memoryId = memory.id,
                    summaryText = "",
                    status = StageStatus.NOT_SUPPORTED,
                    providerModel = null
                )
            )
            return StageResult.NotSupported
        }

        val generated = textGenerator.generate(
            prompt = buildPrompt(input),
            maxTokens = MAX_RESPONSE_TOKENS
        )

        val summary = generated.getOrElse { error ->
            derivedDataRepository.saveSummary(
                MemorySummary(
                    memoryId = memory.id,
                    summaryText = "",
                    status = StageStatus.FAILED,
                    providerModel = null
                )
            )
            return StageResult.Failed(error.message ?: "Summarization failed", error)
        }

        val cleaned = clean(summary)
        if (cleaned.isEmpty()) {
            derivedDataRepository.saveSummary(
                MemorySummary(
                    memoryId = memory.id,
                    summaryText = "",
                    status = StageStatus.EMPTY,
                    providerModel = textGenerator.modelIdentifier()
                )
            )
            return StageResult.Empty
        }

        derivedDataRepository.saveSummary(
            MemorySummary(
                memoryId = memory.id,
                summaryText = cleaned,
                status = StageStatus.SUCCESS,
                providerModel = textGenerator.modelIdentifier()
            )
        )

        return StageResult.Success
    }

    /**
     * Strip reasoning, chain-of-thought, and conversational wrapping.
     *
     * Many models — especially "reasoning" models like Nemotron, DeepSeek-R1, and
     * QwQ — return their entire chain of thought before the answer, even when
     * instructed not to. The output looks like:
     *
     *     We need to produce one or two sentences... The text appears to be...
     *     So it's a screenshot of... We must not infer... Let's craft:
     *     "A screenshot of a Realme phone's quick settings panel."
     *
     * If we take that at face value, the user sees the model thinking out loud on
     * their feed card, which is both useless and bizarre. The actual summary is the
     * last one or two sentences — everything above is process, not product.
     *
     * Returns empty when the response was *only* reasoning or preamble, which the
     * caller records as EMPTY.
     */
    private fun clean(raw: String): String {
        var text = raw.trim()

        // 1. Strip <think>...</think> blocks (DeepSeek-R1, QwQ style).
        text = THINK_BLOCK.replace(text, "").trim()

        // 2. Strip markdown code fences wrapping the answer.
        text = text.replace("```", "").trim()

        // 3. If there is a clear "reasoning then answer" boundary, take only the
        //    answer. Common boundaries: a line starting with a quote, a line after
        //    "Let's craft:", "My answer:", "Final:", "Output:", or a line that is
        //    clearly the start of the response after a blank line preceded by
        //    reasoning language ("We need to", "So it's", "That suggests").
        val afterBoundary = extractAfterReasoningBoundary(text)
        if (afterBoundary != null) {
            text = afterBoundary
        } else if (looksLikeReasoning(text)) {
            // No clear boundary but the text reads as reasoning. Take the last
            // sentence(s) — usually the actual output after the model finishes
            // thinking. At most two sentences since that is what we asked for.
            text = takeLastSentences(text, maxSentences = 2)
        }

        // 4. Strip leading preamble ("Summary:", "Here is a summary:", etc.)
        text = PREAMBLE.replace(text, "").trim()

        // 5. Strip wrapping quotes.
        if (text.length > 1 && text.startsWith('"') && text.endsWith('"')) {
            text = text.substring(1, text.length - 1)
        }

        return text.trim()
    }

    /**
     * Look for a clear boundary between reasoning and the actual answer.
     *
     * Returns the text after the boundary, or null if none was found.
     */
    private fun extractAfterReasoningBoundary(text: String): String? {
        // Common patterns where models signal "OK here's the actual answer":
        val boundaries = listOf(
            Regex("""(?:let'?s\s+craft|my\s+answer|final\s*(?:answer)?|output)\s*[:\-]\s*""", RegexOption.IGNORE_CASE),
            Regex("""(?:return|reply|respond)\s+(?:with\s+)?(?:just|only)?\s*[:\-]\s*""", RegexOption.IGNORE_CASE)
        )

        for (boundary in boundaries) {
            val match = boundary.find(text)
            if (match != null) {
                val after = text.substring(match.range.last + 1).trim()
                if (after.isNotEmpty()) return after
            }
        }

        // A quoted block after reasoning — the model wrapped its answer in quotes.
        val lastQuoted = Regex("""(?:^|\n)\s*"([^"]{10,})"?\s*$""").find(text)
        if (lastQuoted != null) {
            return lastQuoted.groupValues[1].trim()
        }

        return null
    }

    /**
     * Heuristic: does this text read like a model reasoning out loud?
     *
     * True when it contains phrases models use while thinking, which a real
     * summary never would. Conservative — false means we pass the text through
     * unchanged, which is the safe default.
     */
    private fun looksLikeReasoning(text: String): Boolean {
        val lower = text.lowercase()
        val markers = listOf(
            "we need to", "let's ", "let me ", "i need to",
            "so it's ", "that suggests", "we can say",
            "we must not", "it's okay", "i'll ",
            "the text appears to be", "the text includes",
            "we can infer", "based on this"
        )
        return markers.count { lower.contains(it) } >= 2
    }

    /**
     * Take the last N sentences from a block of text.
     *
     * "Sentence" here means text ending in `.` `!` or `?` — rough, but good enough
     * for extracting a model's final output from its reasoning.
     */
    private fun takeLastSentences(text: String, maxSentences: Int): String {
        val sentences = text.split(Regex("""(?<=[.!?])\s+"""))
            .map { it.trim() }
            .filter { it.length > 10 } // Drop fragments

        if (sentences.size <= maxSentences) return text

        return sentences.takeLast(maxSentences).joinToString(" ")
    }

    private fun buildPrompt(input: BoundedAnalysisInput): String {
        val truncationNote = if (input.wasTruncated) {
            "\nThis is a partial view: the memory holds ${input.imagesTotal} images " +
                "and ${input.urlsTotal} links in total. Describe the whole thing, " +
                "not just the part shown."
        } else {
            ""
        }

        return """
            Below is a saved memory from someone's personal collection.

            Write one or two sentences saying what it is generally about, so they
            can recognise it later in a list.

            Rules:
            - Describe the subject as a whole. Do not list the individual items.
            - Use only what is below. Do not infer or invent.
            - Reply with the sentences only. No preamble, no bullet points, no
              headings.$truncationNote

            Memory:
            ${input.text}
        """.trimIndent()
    }

    companion object {
        /** A couple of sentences, with headroom. Discourages an essay. */
        const val MAX_RESPONSE_TOKENS = 256

        /**
         * Matches `<think>...</think>` blocks, including multiline. Models like
         * DeepSeek-R1 and QwQ wrap their chain-of-thought in these tags.
         */
        private val THINK_BLOCK = Regex(
            """<think>.*?</think>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        /**
         * Matches a leading preamble, but only when the colon or dash makes it
         * unambiguously a label. That is what keeps a real sentence beginning
         * "Summary statistics from..." intact.
         */
        private val PREAMBLE = Regex(
            """^\s*(sure[!,.]?\s*)?(here(?:'s| is)?\s+(?:a\s+)?)?(brief\s+)?summary\s*[:\-]\s*""",
            RegexOption.IGNORE_CASE
        )
    }
}
