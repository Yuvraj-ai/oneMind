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
     * Strip the conversational wrapping models like to add.
     *
     * A model asked for one sentence will often answer "Sure! Here's a summary:
     * ...". The preamble is not part of the summary and would be shown on a feed
     * card, so it goes.
     *
     * Returns empty when the response was *only* preamble, which the caller
     * records as EMPTY rather than displaying "Summary:" as though it were the
     * summary.
     */
    private fun clean(raw: String): String {
        var text = raw.trim()

        // Drop a leading "Summary:" or "Here is a summary:" style preamble. If
        // nothing follows it, the caller treats the result as empty — which is the
        // honest reading of a response that said nothing.
        text = PREAMBLE.replace(text, "").trim()

        // Models sometimes wrap prose in quotes when asked for a single sentence.
        if (text.length > 1 && text.startsWith('"') && text.endsWith('"')) {
            text = text.substring(1, text.length - 1)
        }

        return text.trim()
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
