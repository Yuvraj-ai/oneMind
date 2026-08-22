package com.onemind.app.domain.processing

import com.onemind.app.domain.model.Memory

/**
 * A size-limited, representative view of a Memory, built for a language model.
 *
 * A Memory can hold a great deal: fifteen screenshots, thirty links, thousands of
 * words of pasted text. Handing all of that to a model is wasteful, frequently
 * exceeds its context, and does not produce a better answer. This constructs a
 * bounded stand-in instead.
 *
 * Two properties matter beyond the size caps:
 *
 * - It **reuses already-derived data**. OCR text and image descriptions were
 *   produced by earlier stages, so images are represented by what was read out of
 *   them rather than being reprocessed.
 * - It **never modifies the Memory**. This is a representation for one model call.
 *   The original content stays authoritative and complete.
 */
data class BoundedAnalysisInput(
    val text: String,
    /** How many images the Memory had, against how many are represented here. */
    val imagesConsidered: Int,
    val imagesTotal: Int,
    val urlsConsidered: Int,
    val urlsTotal: Int
) {
    /** True when anything was left out, which a prompt may want to acknowledge. */
    val wasTruncated: Boolean
        get() = imagesConsidered < imagesTotal || urlsConsidered < urlsTotal

    val isEmpty: Boolean get() = text.isBlank()

    companion object {

        /**
         * Cap on characters of prose.
         *
         * Roughly 1000 tokens of English, which leaves room for the prompt itself
         * inside a small model's context.
         */
        const val MAX_TEXT_CHARS = 4_000

        /**
         * Images represented, drawn from the front.
         *
         * The locked product decisions name five or six. A Memory of twenty
         * screenshots is usually about one subject, so the first few establish it.
         */
        const val MAX_IMAGES = 6

        /** Links represented, drawn from the front. Same reasoning as images. */
        const val MAX_URLS = 6

        /**
         * Build the bounded view.
         *
         * Sections are ordered and labelled so the model can tell what it is
         * looking at: what the user wrote, what was read off their screenshots,
         * what was seen in their photos, and where the links point. Order is fixed,
         * so the same Memory always produces the same input.
         */
        fun from(memory: Memory): BoundedAnalysisInput {
            val derived = memory.derived
            val allImages = memory.imageBlocks()
            val allUrls = derived.urls

            val sections = mutableListOf<String>()

            val typed = memory.userText().trim()
            if (typed.isNotEmpty()) {
                sections.add("The user wrote:\n$typed")
            }

            // Images are represented by what earlier stages read out of them.
            val consideredBlockIds = allImages.take(MAX_IMAGES).map { it.id }.toSet()

            val ocrText = derived.ocrResults
                .filter { it.contentBlockId in consideredBlockIds }
                .map { it.extractedText.trim() }
                .filter { it.isNotEmpty() }
            if (ocrText.isNotEmpty()) {
                sections.add("Text read from the images:\n" + ocrText.joinToString("\n"))
            }

            val descriptions = derived.visionResults
                .filter { it.contentBlockId in consideredBlockIds }
                .map { it.description.trim() }
                .filter { it.isNotEmpty() }
            if (descriptions.isNotEmpty()) {
                sections.add("The images show:\n" + descriptions.joinToString("\n"))
            }

            val urls = allUrls.take(MAX_URLS)
            if (urls.isNotEmpty()) {
                sections.add("Links:\n" + urls.joinToString("\n") { it.rawUrl })
            }

            return BoundedAnalysisInput(
                text = sections.joinToString("\n\n").take(MAX_TEXT_CHARS),
                imagesConsidered = minOf(allImages.size, MAX_IMAGES),
                imagesTotal = allImages.size,
                urlsConsidered = urls.size,
                urlsTotal = allUrls.size
            )
        }

        /**
         * Bound a plain string, for callers that have text rather than a Memory.
         *
         * This is the case [stages.MetadataExtractionStage] needs: it runs before
         * links are stored, so it cannot build a full view yet, but it should still
         * respect the same limit rather than keeping a private one that can drift.
         */
        fun boundText(text: String): String = text.take(MAX_TEXT_CHARS)
    }
}
