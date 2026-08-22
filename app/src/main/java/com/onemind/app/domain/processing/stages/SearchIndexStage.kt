package com.onemind.app.domain.processing.stages

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.processing.ProcessingStage
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.repository.SearchIndexRepository
import com.onemind.app.domain.search.SearchDocument
import javax.inject.Inject

/**
 * Makes a Memory findable.
 *
 * Runs last, and must stay last: it assembles the search document from what every
 * other stage produced, so a stage added after it would be invisible to search.
 *
 * This is a stage rather than a repository detail because its shape is identical
 * to the enrichment stages — read all derived data, write one row — and because
 * the pipeline already owns the problem it solves. The index is a duplicate of
 * text held elsewhere, so it is only correct while it is rebuilt whenever the
 * source changes, and "derived data is stale, rebuild it" is precisely what the
 * pipeline is for. Being a stage also means every capture path added in future is
 * indexed with no further work, and that an edit clears the index for free
 * alongside the rest of the derived data.
 *
 * Unlike the other stages it needs no AI provider. Indexing is pure text
 * assembly, so it never reports NOT_SUPPORTED: search must keep working on a
 * device that has no model configured at all.
 */
class SearchIndexStage @Inject constructor(
    private val searchIndexRepository: SearchIndexRepository
) : ProcessingStage {

    override val id = StageId.INDEXING

    override suspend fun process(memory: Memory): StageResult {
        val document = SearchDocument.build(memory)

        if (document.isBlank()) {
            // Nothing to find. Remove any row from a previous run rather than
            // leaving one that describes content the Memory no longer has.
            searchIndexRepository.remove(memory.id)
            return StageResult.Empty
        }

        searchIndexRepository.index(memory.id, document)
        return StageResult.Success
    }
}
